package com.divepilot.app.deco

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

/** Un palier de décompression (profondeur + durée). */
data class DecoStop(val depthMeters: Double, val minutes: Int)

/** État tissulaire persistable (tensions d'azote + toxicité oxygène). */
data class TissueState(
    val tensions: List<Double>,
    val cnsPercent: Double,
    val otu: Double,
)

/**
 * Modèle de décompression Bühlmann ZHL-16C avec Gradient Factors (GF Low/High).
 * Suit la tension d'azote dissous dans 16 compartiments, supporte air + nitrox,
 * l'altitude, l'eau douce/salée, et la toxicité oxygène (CNS + OTU).
 *
 * Avertissement : outil d'aide en MODE TEST, à valider avant tout usage réel.
 */
class DecompressionModel(
    private val getFo2: () -> Double,
    private val getSurfacePressure: () -> Double = { 1.0 },
    private val getWaterDensity: () -> Double = { 10.0 },
    private val gfLow: Double = 0.30,
    private val gfHigh: Double = 0.70,
) {

    private val tensions = DoubleArray(Zhl16.COMPARTMENTS) { initialN2() }
    private var lastInspired = initialN2()
    private var lastDepth = 0.0

    // Toxicité oxygène (cumul).
    private var cnsPercent = 0.0
    private var otu = 0.0

    private fun initialN2(): Double =
        (getSurfacePressure() - Zhl16.WATER_VAPOR) * (1.0 - getFo2())

    private fun ambient(depth: Double): Double =
        getSurfacePressure() + depth / getWaterDensity()

    private fun inspired(depth: Double): Double =
        (ambient(depth) - Zhl16.WATER_VAPOR) * (1.0 - getFo2())

    fun reset() {
        val p0 = initialN2()
        for (i in tensions.indices) tensions[i] = p0
        lastInspired = p0
        lastDepth = 0.0
        cnsPercent = 0.0
        otu = 0.0
    }

    /** Capture l'état tissulaire courant (pour persistance entre plongées). */
    fun saveState(): TissueState = TissueState(tensions.toList(), cnsPercent, otu)

    /** Restaure un état tissulaire précédemment sauvegardé. */
    fun loadState(state: TissueState) {
        for (i in tensions.indices) {
            tensions[i] = if (i < state.tensions.size) state.tensions[i] else tensions[i]
        }
        cnsPercent = state.cnsPercent
        otu = state.otu
        lastInspired = inspired(0.0)
        lastDepth = 0.0
    }

    /**
     * Désature en surface pendant [dtSeconds] (intervalle de surface entre deux plongées
     * ou après redémarrage de l'application). Met aussi à jour la décroissance CNS/OTU.
     */
    fun surfaceOffGas(dtSeconds: Double) {
        if (dtSeconds <= 0.0) return
        val pInspired = inspired(0.0)
        for (i in tensions.indices) {
            val k = ln(2.0) / (Zhl16.HALF_TIMES_MIN[i] * 60.0)
            tensions[i] = pInspired + (tensions[i] - pInspired) * exp(-k * dtSeconds)
        }
        lastInspired = pInspired
        lastDepth = 0.0
        val decay = 0.5.pow(dtSeconds / (90.0 * 60.0)) // demi-vie CNS ~90 min
        cnsPercent *= decay
        otu *= decay
    }

    /** Avance le modèle d'un pas de [dtSeconds] à la profondeur [depthMeters]. */
    fun update(depthMeters: Double, dtSeconds: Double) {
        if (dtSeconds <= 0.0) return
        val pInspired = inspired(depthMeters)
        val rate = (pInspired - lastInspired) / dtSeconds
        for (i in tensions.indices) {
            val k = ln(2.0) / (Zhl16.HALF_TIMES_MIN[i] * 60.0) // par seconde
            val p0 = tensions[i]
            tensions[i] = if (rate == 0.0) {
                pInspired + (p0 - pInspired) * exp(-k * dtSeconds)
            } else {
                pInspired + rate * (dtSeconds - 1.0 / k) -
                    (pInspired - p0 - rate / k) * exp(-k * dtSeconds)
            }
        }
        lastInspired = pInspired
        lastDepth = depthMeters

        // Toxicité oxygène.
        val ppo2 = getFo2() * ambient(depthMeters)
        if (depthMeters > SURFACE_THRESHOLD) {
            cnsPercent += cnsPerMinute(ppo2) * (dtSeconds / 60.0)
            otu += otuPerMinute(ppo2) * (dtSeconds / 60.0)
        } else {
            val decay = 0.5.pow(dtSeconds / (90.0 * 60.0)) // demi-vie CNS ~90 min
            cnsPercent *= decay
            otu *= decay
        }
    }

    fun ppo2(): Double = getFo2() * ambient(lastDepth)

    fun cnsPercent(): Double = cnsPercent

    fun otu(): Double = otu

    /** Plafond (m) : profondeur minimale autorisée, avec interpolation GF Low→High. */
    fun ceilingMeters(): Double {
        if (ceilingFor(gfHigh) <= 0.0) return 0.0
        val first = firstStopDepthMeters()
        return ceilingFor(interpolatedGf(lastDepth, first))
    }

    /** Profondeur du premier palier (plafond avec GF Low, arrondi à 3 m). */
    fun firstStopDepthMeters(): Double {
        if (ceilingFor(gfHigh) <= 0.0) return 0.0
        return ceil(ceilingFor(gfLow) / STOP_STEP) * STOP_STEP
    }

    /** Plan de décompression : liste des paliers (profondeur + durée). */
    fun decoStops(): List<DecoStop> {
        val first = firstStopDepthMeters()
        if (first <= 0.0) return emptyList()

        val sim = tensions.copyOf()
        val stops = mutableListOf<DecoStop>()

        var depth = first
        while (depth > 0.0) {
            val next = maxOf(depth - STOP_STEP, 0.0)
            var minutes = 0
            while (ceilingForTensions(sim, interpolatedGf(depth, first)) > next &&
                minutes < MAX_STOP_MIN
            ) {
                offGas(sim, depth, 60.0)
                minutes++
            }
            if (minutes > 0) stops.add(DecoStop(depth, minutes))
            depth = next
        }
        return stops
    }

    /** Temps total de décompression (min). */
    fun ttsMinutes(): Int = decoStops().sumOf { it.minutes }

    /**
     * Temps restant avant de pouvoir prendre l'avion (min) : durée nécessaire pour que
     * tous les compartiments se désaturent en surface jusqu'à la limite GF High.
     */
    fun noFlyMinutes(): Double {
        val pInspired = inspired(0.0)
        var worst = 0.0
        for (i in tensions.indices) {
            val limit = surfaceLimit(i)
            val p0 = tensions[i]
            if (p0 <= limit) continue
            val denominator = p0 - pInspired
            val numerator = limit - pInspired
            if (denominator <= 0.0) continue
            if (numerator <= 0.0) continue
            val k = ln(2.0) / (Zhl16.HALF_TIMES_MIN[i] * 60.0)
            val seconds = -ln(numerator / denominator) / k
            val minutes = seconds / 60.0
            if (minutes > worst) worst = minutes
        }
        return worst
    }

    /** Temps restant (min) à la profondeur courante avant décompression. */
    fun ndlMinutes(): Double {
        val pInspired = inspired(lastDepth)
        var best = Double.MAX_VALUE
        for (i in tensions.indices) {
            val limit = surfaceLimit(i)
            if (pInspired <= limit) continue // ce tissu décharge, non limitant
            val p0 = tensions[i]
            if (p0 >= limit) return 0.0
            val k = ln(2.0) / (Zhl16.HALF_TIMES_MIN[i] * 60.0)
            val seconds = -ln((limit - pInspired) / (p0 - pInspired)) / k
            val minutes = seconds / 60.0
            if (minutes < best) best = minutes
        }
        return if (best == Double.MAX_VALUE) MAX_NDL else best
    }

    private fun surfaceLimit(i: Int): Double =
        getSurfacePressure() * (1.0 - gfHigh + gfHigh / Zhl16.N2_B[i]) + Zhl16.N2_A[i] * gfHigh

    private fun ceilingFor(gf: Double): Double = ceilingForTensions(tensions, gf)

    private fun ceilingForTensions(t: DoubleArray, gf: Double): Double {
        val sp = getSurfacePressure()
        val wd = getWaterDensity()
        var ceiling = 0.0
        for (i in t.indices) {
            val denom = 1.0 - gf + gf / Zhl16.N2_B[i]
            val pAmbient = (t[i] - Zhl16.N2_A[i] * gf) / denom
            val depth = (pAmbient - sp) * wd
            if (depth > ceiling) ceiling = depth
        }
        return max(ceiling, 0.0)
    }

    private fun interpolatedGf(depth: Double, firstStop: Double): Double {
        if (firstStop <= 0.0) return gfHigh
        if (depth >= firstStop) return gfLow
        return gfHigh + (gfLow - gfHigh) * (depth / firstStop)
    }

    private fun offGas(t: DoubleArray, depth: Double, dt: Double) {
        val pInspired = inspired(depth)
        for (i in t.indices) {
            val k = ln(2.0) / (Zhl16.HALF_TIMES_MIN[i] * 60.0)
            t[i] = pInspired + (t[i] - pInspired) * exp(-k * dt)
        }
    }

    /** %CNS par minute (table NOAA, interpolation linéaire). */
    private fun cnsPerMinute(ppo2: Double): Double {
        val table = listOf(
            0.6 to 720.0, 0.7 to 570.0, 0.8 to 450.0, 0.9 to 360.0,
            1.0 to 300.0, 1.1 to 240.0, 1.2 to 210.0, 1.3 to 180.0,
            1.4 to 150.0, 1.5 to 120.0, 1.6 to 45.0,
        )
        if (ppo2 <= 0.5) return 0.0
        if (ppo2 >= 1.6) return 100.0 / 45.0
        for (i in 0 until table.size - 1) {
            val (p1, t1) = table[i]
            val (p2, t2) = table[i + 1]
            if (ppo2 in p1..p2) {
                val frac = (ppo2 - p1) / (p2 - p1)
                val time = t1 + (t2 - t1) * frac
                return 100.0 / time
            }
        }
        return 0.0
    }

    /** OTU par minute. */
    private fun otuPerMinute(ppo2: Double): Double {
        if (ppo2 <= 0.5) return 0.0
        return ((ppo2 - 0.5) / 0.5).pow(0.83)
    }

    companion object {
        const val MAX_NDL = 99.0
        const val STOP_STEP = 3.0
        const val MAX_STOP_MIN = 120
        const val SURFACE_THRESHOLD = 0.5
    }
}
