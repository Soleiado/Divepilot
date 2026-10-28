package com.divepilot.app.dive

import com.divepilot.app.R
import com.divepilot.app.data.DepthSample
import com.divepilot.app.data.DiveLog
import com.divepilot.app.deco.DecompressionModel
import com.divepilot.app.deco.TissueState
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class DiveState { START, END }

/** État de plongée courant, affiché dans l'overlay caméra. */
data class DiveUiState(
    val currentDepthMeters: Double = 0.0,
    val currentTemperature: Double = 0.0,
    val batteryPercent: Int = 0,
    val diveState: DiveState = DiveState.END,
    val diveStartTimeMillis: Long = 0L,
    val diveElapsedMillis: Long = 0L,
    val maxDepthMeters: Double = 0.0,
    val minTemperature: Double = 0.0,
    val staySafeState: Int = 0,
    val ndlMinutes: Double = 0.0,
    val ceilingMeters: Double = 0.0,
    val firstStopMeters: Double = 0.0,
    val firstStopMinutes: Int = 0,
    val ttsMinutes: Int = 0,
    val surfaceIntervalMillis: Long = 0L,
    val ppo2: Double = 0.0,
    val cnsPercent: Double = 0.0,
    val otu: Double = 0.0,
    val noFlyMinutes: Double = 0.0,
    val dcEnabled: Boolean = false,
)

/** Événements émis par la machine à états (alertes, fin de plongée, intervalle de surface). */
sealed interface DiveEvent {
    data class Alert(val resId: Int, val args: List<Any> = emptyList()) : DiveEvent
    data class DiveEnded(val log: DiveLog) : DiveEvent
    data class SurfaceInterval(val minutes: Int) : DiveEvent
}

/**
 * Machine à états de plongée, fidèle au comportement de l'application d'origine :
 * - début : profondeur > 1,2 m
 * - fin : profondeur ≤ 0,5 m
 * - palier de sécurité (SCUBA) : 6 m / 10 m
 * - alertes de profondeur, temps, vitesse de remontée (SCUBA) et de profondeur (Free)
 */
class DiveComputer(
    private val getSettings: () -> DiveModeSetting,
    private val getDcSettings: () -> DiveComputerSettings,
    private val getLocation: () -> Pair<Double, Double>? = { null },
) {

    private val decompressionModel = DecompressionModel(
        getFo2 = { getDcSettings().o2Percent / 100.0 },
        getSurfacePressure = { surfacePressureForAltitude(getDcSettings().altitudeMeters) },
        getWaterDensity = { waterDensityMetersPerBar(getDcSettings().freshWater) },
    )

    private val _events = MutableSharedFlow<DiveEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<DiveEvent> = _events.asSharedFlow()

    private var state = DiveUiState()
    private var lastModelTime = 0L
    private var lastDiveEndTimeMillis = 0L

    private var samples = mutableListOf<DepthSample>()
    private var tempSum = 0.0
    private var tempCount = 0
    private var lastSampleTime = 0L

    private var lastSecondDepth = 0.0
    private var lastSecondTime = 0L

    private var lastWarnAscentDepth = 0.0
    private var lastWarnAscentTime = 0L
    private var warnAscentCount = 0

    private var staySafeStart = false

    // Contexte de plongée capturé à l'immersion (position + réglages de l'ordinateur).
    private var diveLatitude: Double? = null
    private var diveLongitude: Double? = null
    private var diveAltitudeMeters: Int = 0
    private var diveFreshWater: Boolean = false

    val uiState: DiveUiState get() = state

    fun tissueState(): TissueState = decompressionModel.saveState()

    /** Restaure l'état tissulaire et le temps de fin de plongée après un redémarrage. */
    fun restore(tissueState: TissueState?, savedAtMillis: Long, lastDiveEndMillis: Long, now: Long) {
        lastDiveEndTimeMillis = lastDiveEndMillis
        if (tissueState != null) {
            decompressionModel.loadState(tissueState)
            val elapsedMs = (now - savedAtMillis).coerceAtLeast(0L)
            if (elapsedMs > 0L) decompressionModel.surfaceOffGas(elapsedMs / 1000.0)
        }
        lastModelTime = now
        refreshDerived(now, getDcSettings())
    }

    fun update(depth: Double, temperature: Double, battery: Int, now: Long) {
        val settings = getSettings()
        val dcSettings = getDcSettings()

        // Mise à jour du modèle de décompression (pas de temps réel).
        // Le modèle ne se charge qu'en plongée SCUBA avec l'ordinateur activé ; sinon
        // (mode Free / apnée, ou ordinateur désactivé) il ne charge pas, mais continue
        // à désaturer en surface (profondeur ≤ 0,5 m).
        val dt = if (lastModelTime == 0L) 0.0 else (now - lastModelTime) / 1000.0
        lastModelTime = now
        if (dt > 0.0) {
            val scubaDive = settings.mode == DiveModeSetting.MODE_SCUBA && dcSettings.enabled
            val atSurface = depth <= 0.5
            if (scubaDive || atSurface) decompressionModel.update(depth, dt)
        }

        state = state.copy(
            currentDepthMeters = depth,
            currentTemperature = temperature,
            batteryPercent = battery,
        )
        refreshDerived(now, dcSettings)

        if (state.diveState == DiveState.END && depth > 1.2) {
            startDive(now, temperature)
        }

        if (state.diveState == DiveState.START && depth <= 0.5) {
            endDive(now, settings)
            return
        }

        if (state.diveState == DiveState.START) {
            trackDive(depth, temperature, now, settings)
        }
    }

    /**
     * Rafraîchissement périodique en surface (appelé par un ticker ~1 Hz) : désature le
     * modèle et met à jour l'intervalle de surface et le temps avant avion même sans
     * télémétrie BLE.
     */
    fun tick(now: Long) {
        if (state.diveState == DiveState.START) return
        val dt = if (lastModelTime == 0L) 0.0 else (now - lastModelTime) / 1000.0
        lastModelTime = now
        if (dt > 0.0) decompressionModel.update(0.0, dt)
        refreshDerived(now, getDcSettings())
    }

    private fun refreshDerived(now: Long, dcSettings: DiveComputerSettings) {
        val dcEnabled = dcSettings.enabled
        val stops = if (dcEnabled) decompressionModel.decoStops() else emptyList()
        state = state.copy(
            surfaceIntervalMillis = if (lastDiveEndTimeMillis > 0L) (now - lastDiveEndTimeMillis).coerceAtLeast(0L) else 0L,
            ndlMinutes = if (dcEnabled) decompressionModel.ndlMinutes() else 0.0,
            ceilingMeters = if (dcEnabled) decompressionModel.ceilingMeters() else 0.0,
            firstStopMeters = if (dcEnabled) decompressionModel.firstStopDepthMeters() else 0.0,
            firstStopMinutes = stops.firstOrNull()?.minutes ?: 0,
            ttsMinutes = stops.sumOf { it.minutes },
            ppo2 = if (dcEnabled) decompressionModel.ppo2() else 0.0,
            cnsPercent = if (dcEnabled) decompressionModel.cnsPercent() else 0.0,
            otu = if (dcEnabled) decompressionModel.otu() else 0.0,
            noFlyMinutes = if (dcEnabled) decompressionModel.noFlyMinutes() else 0.0,
            dcEnabled = dcEnabled,
        )
    }

    private fun startDive(now: Long, temperature: Double) {
        // Capture des dernières coordonnées GPS (avant immersion) et des réglages.
        val location = getLocation()
        diveLatitude = location?.first
        diveLongitude = location?.second
        diveAltitudeMeters = getDcSettings().altitudeMeters
        diveFreshWater = getDcSettings().freshWater

        state = state.copy(
            diveState = DiveState.START,
            diveStartTimeMillis = now,
            diveElapsedMillis = 0L,
            maxDepthMeters = 0.0,
            minTemperature = temperature,
            staySafeState = 0,
        )
        samples.clear()
        tempSum = 0.0
        tempCount = 0
        lastSampleTime = 0L
        lastSecondTime = 0L
        lastSecondDepth = 0.0
        lastWarnAscentDepth = 0.0
        lastWarnAscentTime = 0L
        warnAscentCount = 0
        staySafeStart = false
    }

    private fun trackDive(depth: Double, temperature: Double, now: Long, settings: DiveModeSetting) {
        val elapsed = now - state.diveStartTimeMillis
        state = state.copy(
            diveElapsedMillis = elapsed,
            maxDepthMeters = maxOf(state.maxDepthMeters, depth),
            minTemperature = minOf(state.minTemperature, temperature),
        )

        // Échantillonnage (1 s en Free, 10 s en SCUBA).
        val interval = if (settings.mode == DiveModeSetting.MODE_FREE) 1000L else 10000L
        if (lastSampleTime == 0L || now - lastSampleTime >= interval) {
            samples.add(DepthSample(elapsed / 1000L, depth))
            tempSum += temperature
            tempCount++
            lastSampleTime = now
        }

        val descending = isDown(depth, now)

        if (settings.mode == DiveModeSetting.MODE_SCUBA) {
            scubaSafetyStop(depth)
            scubaAlerts(settings, depth, elapsed, now, descending)
        } else {
            state = state.copy(staySafeState = 0)
            freeAlerts(settings, depth, descending)
        }
    }

    private fun scubaSafetyStop(depth: Double) {
        // Déclenchement : profondeur max ≥ 10 m et remontée à 6 m.
        if (state.maxDepthMeters >= 10.0 && depth <= 6.0 && !staySafeStart) {
            staySafeStart = true
            state = state.copy(staySafeState = 1)
            _events.tryEmit(DiveEvent.Alert(R.string.alert_safety_stop))
        }
        // Zone tampon entre 6 m et 10 m.
        if (staySafeStart && depth > 6.0 && depth < 10.0) {
            state = state.copy(staySafeState = 2)
        }
        // Annulation : au-dessus de 10 m, ou palier terminé (remontée sous 3,5 m).
        if (staySafeStart && (depth > 10.0 || depth < 3.5)) {
            staySafeStart = false
            state = state.copy(staySafeState = 0)
        }
    }

    private fun scubaAlerts(
        settings: DiveModeSetting,
        depth: Double,
        elapsed: Long,
        now: Long,
        descending: Boolean,
    ) {
        if (settings.scubaMaxDepth.onOff && descending &&
            abs(depth - settings.scubaMaxDepth.value) <= 0.1
        ) {
            _events.tryEmit(DiveEvent.Alert(R.string.alert_depth_reached, listOf(settings.scubaMaxDepth.value)))
        }

        if (settings.scubaMaxTime.onOff) {
            val target = settings.scubaMaxTime.value * 60L * 1000L
            if (abs(elapsed - target) < 100) {
                _events.tryEmit(DiveEvent.Alert(R.string.alert_time_reached, listOf(settings.scubaMaxTime.value)))
            }
        }

        if (settings.scubaAscentRate.onOff && now - lastWarnAscentTime > 1000) {
            if (lastWarnAscentDepth - depth > settings.scubaAscentRate.value / 60.0) {
                warnAscentCount++
            } else {
                warnAscentCount = 0
            }
            lastWarnAscentDepth = depth
            lastWarnAscentTime = now
            if (warnAscentCount >= 5) {
                warnAscentCount = 0
                _events.tryEmit(
                    DiveEvent.Alert(R.string.alert_ascent_rate, listOf(settings.scubaAscentRate.value))
                )
            }
        }
    }

    private fun freeAlerts(settings: DiveModeSetting, depth: Double, descending: Boolean) {
        checkFreeDepth(settings.freeDepth1, depth, descending)
        checkFreeDepth(settings.freeDepth2, depth, descending)
        checkFreeDepth(settings.freeDepth3, depth, descending)
        checkFreeDepth(settings.freeDepth4, depth, descending)
        checkFreeDepth(settings.freeDepth5, depth, descending)
    }

    private fun checkFreeDepth(item: ItemSetting, depth: Double, descending: Boolean) {
        if (item.onOff && descending && abs(depth - item.value) <= 0.1) {
            _events.tryEmit(DiveEvent.Alert(R.string.alert_depth_reached, listOf(item.value)))
        }
    }

    private fun endDive(now: Long, settings: DiveModeSetting) {
        val startTime = state.diveStartTimeMillis
        val duration = (now - startTime) / 1000L

        samples.add(DepthSample(duration, state.currentDepthMeters))
        tempSum += state.currentTemperature
        tempCount++

        val avgDepth = if (samples.isNotEmpty()) samples.map { it.depthMeters }.average() else 0.0
        val avgTemp = if (tempCount > 0) tempSum / tempCount else 0.0

        val log = DiveLog(
            diveType = settings.mode,
            startTimeMillis = startTime,
            endTimeMillis = now,
            durationSeconds = duration,
            maxDepthMeters = state.maxDepthMeters,
            minTemperature = state.minTemperature,
            avgDepthMeters = avgDepth,
            avgTemperature = avgTemp,
            samples = samples.toList(),
            latitude = diveLatitude,
            longitude = diveLongitude,
            altitudeMeters = diveAltitudeMeters,
            freshWater = diveFreshWater,
        )
        _events.tryEmit(DiveEvent.DiveEnded(log))

        if (settings.mode == DiveModeSetting.MODE_FREE && settings.freeSurfaceInterval.value > 0) {
            _events.tryEmit(DiveEvent.SurfaceInterval(settings.freeSurfaceInterval.value))
        }

        // L'intervalle de surface et l'azote résiduel ne concernent que le SCUBA.
        if (settings.mode == DiveModeSetting.MODE_SCUBA) {
            lastDiveEndTimeMillis = now
        }
        state = state.copy(diveState = DiveState.END, diveElapsedMillis = 0L, staySafeState = 0)
        staySafeStart = false
    }

    private fun isDown(depth: Double, now: Long): Boolean {
        val z = depth > lastSecondDepth
        if (now - lastSecondTime >= 1000) {
            lastSecondTime = now
            lastSecondDepth = depth
        }
        return z
    }
}
