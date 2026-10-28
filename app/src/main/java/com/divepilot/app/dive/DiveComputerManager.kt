package com.divepilot.app.dive

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.divepilot.app.R
import com.divepilot.app.ble.BleManager
import com.divepilot.app.ble.DecodedFrame
import com.divepilot.app.data.DiveRepository
import com.divepilot.app.data.DiveTissueStore
import com.divepilot.app.location.LocationTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Singleton reliant la télémétrie BLE à la machine à états de plongée.
 * Enregistre les plongées et déclenche les vibrations/alertes.
 */
class DiveComputerManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val settingsRepository = SettingsRepository(appContext)
    private val diveRepository = DiveRepository.get(appContext)
    private val dcSettingsManager = DiveComputerSettingsManager.get(appContext)
    private val tissueStore = DiveTissueStore(appContext)
    private val locationTracker = LocationTracker(appContext)
    private val vibrator = appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    private val computer = DiveComputer(
        getSettings = { settingsRepository.load() },
        getDcSettings = { dcSettingsManager.settings.value },
        getLocation = { locationTracker.lastKnownLocation()?.let { it.latitude to it.longitude } },
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(DiveUiState())
    val uiState: StateFlow<DiveUiState> = _uiState.asStateFlow()

    private val _alerts = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val alerts: SharedFlow<String> = _alerts.asSharedFlow()

    init {
        // Restaure l'azote résiduel et le temps de fin de la dernière plongée.
        val stored = tissueStore.load()
        computer.restore(
            tissueState = stored.tissueState,
            savedAtMillis = stored.savedAtMillis,
            lastDiveEndMillis = stored.lastDiveEndMillis,
            now = System.currentTimeMillis(),
        )
        _uiState.value = computer.uiState

        scope.launch {
            computer.events.collect { event ->
                when (event) {
                    is DiveEvent.Alert -> onAlert(event)
                    is DiveEvent.DiveEnded -> {
                        diveRepository.addLog(event.log)
                        if (event.log.diveType == DiveModeSetting.MODE_SCUBA) {
                            tissueStore.save(
                                computer.tissueState(),
                                System.currentTimeMillis(),
                                event.log.endTimeMillis,
                            )
                        }
                    }
                    is DiveEvent.SurfaceInterval -> scheduleSurfaceInterval(event.minutes)
                }
            }
        }
        scope.launch {
            BleManager.get(appContext).frames.collect { frame ->
                if (frame is DecodedFrame.Telemetry) {
                    computer.update(
                        depth = frame.depthMeters,
                        temperature = frame.temperatureCelsius,
                        battery = frame.batteryPercent,
                        now = System.currentTimeMillis(),
                    )
                    _uiState.value = computer.uiState
                }
            }
        }
        // Ticker : désature en surface et rafraîchit intervalle de surface / temps avant avion.
        scope.launch {
            while (true) {
                delay(1000)
                computer.tick(System.currentTimeMillis())
                _uiState.value = computer.uiState
            }
        }
    }

    private fun onAlert(event: DiveEvent.Alert) {
        val message = appContext.getString(event.resId, *event.args.toTypedArray())
        _alerts.tryEmit(message)
        vibrate()
    }

    private fun scheduleSurfaceInterval(minutes: Int) {
        scope.launch {
            delay(minutes * 60_000L)
            val message = appContext.getString(R.string.alert_surface_interval, minutes)
            _alerts.tryEmit(message)
            vibrate()
        }
    }

    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }
        } catch (_: Exception) {
            // Pas de vibreur disponible.
        }
    }

    companion object {
        @Volatile
        private var instance: DiveComputerManager? = null

        fun get(context: Context): DiveComputerManager =
            instance ?: synchronized(this) {
                instance ?: DiveComputerManager(context.applicationContext).also { instance = it }
            }
    }
}
