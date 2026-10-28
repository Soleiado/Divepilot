package com.divepilot.app.deco

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test de non-régression du modèle Bühlmann ZHL-16C.
 * Formule M-value confirmée : P_tol = a + P_amb/b.
 */
class DecompressionModelTest {

    private val goldenAirNdl = mapOf(
        15.0 to 115.5,
        18.0 to 64.2,
        21.0 to 40.4,
        24.0 to 28.5,
        27.0 to 21.3,
        30.0 to 16.3,
        33.0 to 13.5,
        36.0 to 11.5,
        39.0 to 9.6,
        42.0 to 8.1,
    )

    @Test
    fun ndlAirMatchesGolden() {
        for ((depth, expected) in goldenAirNdl) {
            val model = DecompressionModel(getFo2 = { 0.21 }, gfLow = 1.0, gfHigh = 1.0)
            model.update(depth, 0.0001)
            assertEquals("NDL air à ${depth} m", expected, model.ndlMinutes(), 0.5)
        }
    }

    @Test
    fun ndlNitroxMatchesGolden() {
        val ean32 = DecompressionModel(getFo2 = { 0.32 }, gfLow = 1.0, gfHigh = 1.0)
        ean32.update(30.0, 0.0001)
        assertEquals("NDL EAN32 à 30 m", 28.0, ean32.ndlMinutes(), 0.5)

        val ean36 = DecompressionModel(getFo2 = { 0.36 }, gfLow = 1.0, gfHigh = 1.0)
        ean36.update(30.0, 0.0001)
        assertEquals("NDL EAN36 à 30 m", 35.1, ean36.ndlMinutes(), 0.5)
    }

    @Test
    fun decoStopsForDeepDive() {
        // Air à 40 m pendant 30 min (dépasse largement la NDL) — GF 30/70.
        val model = DecompressionModel(getFo2 = { 0.21 }, gfLow = 0.30, gfHigh = 0.70)
        model.update(40.0, 0.0001)
        model.update(40.0, 30.0 * 60.0)

        val stops = model.decoStops()
        val first = model.firstStopDepthMeters()
        val tts = model.ttsMinutes()

        println("40m/30min air (GF30/70) : premier palier=${first}m, TTS=${tts}min, paliers=$stops")

        assertTrue("Il doit y avoir des paliers", stops.isNotEmpty())
        assertTrue("Premier palier > 0", first > 0.0)
        // Les paliers doivent descendre par pas de 3 m.
        for (i in 1 until stops.size) {
            assertEquals(3.0, stops[i - 1].depthMeters - stops[i].depthMeters, 0.001)
        }
    }

    @Test
    fun noDecoStopWithinNdl() {
        // Air à 30 m pendant 5 min (dans la NDL ~9,5 min avec GF 30/70) — aucun palier.
        val model = DecompressionModel(getFo2 = { 0.21 }, gfLow = 0.30, gfHigh = 0.70)
        model.update(30.0, 0.0001)
        model.update(30.0, 5.0 * 60.0)
        assertEquals(0.0, model.firstStopDepthMeters(), 0.001)
        assertTrue(model.decoStops().isEmpty())
    }

    @Test
    fun oxygenToxicityAccumulates() {
        // EAN40 à 30 m : PPO2 = 0,40 × 4,0 = 1,6 bar (limite).
        val model = DecompressionModel(getFo2 = { 0.40 }, gfLow = 0.30, gfHigh = 0.70)
        model.update(30.0, 0.0001)
        model.update(30.0, 30.0 * 60.0) // 30 min à PPO2 1,6
        assertTrue("CNS doit s'accumuler", model.cnsPercent() > 10.0)
        assertTrue("OTU doit s'accumuler", model.otu() > 10.0)
    }

    @Test
    fun altitudeReducesNdl() {
        val sea = DecompressionModel(getFo2 = { 0.21 }, gfLow = 1.0, gfHigh = 1.0)
        sea.update(30.0, 0.0001)
        val ndlSea = sea.ndlMinutes()

        val high = DecompressionModel(
            getFo2 = { 0.21 },
            getSurfacePressure = { 0.70 }, // ~3000 m
            gfLow = 1.0,
            gfHigh = 1.0,
        )
        high.update(30.0, 0.0001)
        val ndlHigh = high.ndlMinutes()

        assertTrue("NDL en altitude doit être plus courte", ndlHigh < ndlSea)
    }

    @Test
    fun noFlyTimeAfterDecoDive() {
        val model = DecompressionModel(getFo2 = { 0.21 }, gfLow = 0.30, gfHigh = 0.70)
        model.update(40.0, 0.0001)
        model.update(40.0, 30.0 * 60.0)
        val noFly = model.noFlyMinutes()
        println("40m/30min air : temps avant avion = ${noFly}min")
        assertTrue("Temps avant avion doit être > 0 après une plongée à décompression", noFly > 0.0)
    }

    @Test
    fun surfaceOffGasReducesNoFly() {
        val model = DecompressionModel(getFo2 = { 0.21 }, gfLow = 0.30, gfHigh = 0.70)
        model.update(40.0, 0.0001)
        model.update(40.0, 30.0 * 60.0)
        val noFlyBefore = model.noFlyMinutes()

        model.surfaceOffGas(120.0 * 60.0) // 2 h en surface

        val noFlyAfter = model.noFlyMinutes()
        println("Temps avant avion : avant=${noFlyBefore}min, après 2h=${noFlyAfter}min")
        assertTrue("Le temps avant avion doit diminuer après désaturation", noFlyAfter < noFlyBefore)
    }

    @Test
    fun saveLoadStateRoundTrip() {
        val model = DecompressionModel(getFo2 = { 0.21 }, gfLow = 0.30, gfHigh = 0.70)
        model.update(40.0, 0.0001)
        model.update(40.0, 30.0 * 60.0)
        val saved = model.saveState()

        val restored = DecompressionModel(getFo2 = { 0.21 }, gfLow = 0.30, gfHigh = 0.70)
        restored.loadState(saved)

        assertEquals(model.noFlyMinutes(), restored.noFlyMinutes(), 0.001)
        assertEquals(model.cnsPercent(), restored.cnsPercent(), 0.001)
        assertEquals(model.otu(), restored.otu(), 0.001)
    }
}
