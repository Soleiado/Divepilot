package com.divepilot.app.dive

import android.content.Context
import kotlin.math.exp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Réglages de l'ordinateur de plongée (désactivé par défaut, mode test). */
data class DiveComputerSettings(
    val enabled: Boolean = false,
    val o2Percent: Int = 21,       // 21..40
    val altitudeMeters: Int = 0,   // 0, 1000, 2000, 3000
    val freshWater: Boolean = false, // false = eau salée, true = eau douce
)

/** Singleton gérant la persistance des réglages de l'ordinateur de plongée. */
class DiveComputerSettingsManager private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("divepilot_dc_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<DiveComputerSettings> = _settings.asStateFlow()

    private fun load(): DiveComputerSettings = DiveComputerSettings(
        enabled = prefs.getBoolean("enabled", false),
        o2Percent = prefs.getInt("o2", 21).coerceIn(21, 40),
        altitudeMeters = prefs.getInt("altitude", 0),
        freshWater = prefs.getBoolean("fresh", false),
    )

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("enabled", enabled).apply()
        _settings.value = _settings.value.copy(enabled = enabled)
    }

    fun setO2(percent: Int) {
        val v = percent.coerceIn(21, 40)
        prefs.edit().putInt("o2", v).apply()
        _settings.value = _settings.value.copy(o2Percent = v)
    }

    fun setAltitude(meters: Int) {
        prefs.edit().putInt("altitude", meters).apply()
        _settings.value = _settings.value.copy(altitudeMeters = meters)
    }

    fun setFreshWater(fresh: Boolean) {
        prefs.edit().putBoolean("fresh", fresh).apply()
        _settings.value = _settings.value.copy(freshWater = fresh)
    }

    companion object {
        @Volatile
        private var instance: DiveComputerSettingsManager? = null

        fun get(context: Context): DiveComputerSettingsManager =
            instance ?: synchronized(this) {
                instance ?: DiveComputerSettingsManager(context.applicationContext)
                    .also { instance = it }
            }
    }
}

/** Pression de surface (bar) selon l'altitude (approx. exponentielle). */
fun surfacePressureForAltitude(altitudeMeters: Int): Double =
    exp(-altitudeMeters / 8434.0)

/** Densité de l'eau (m/bar) : 10,0 (salée) ou 10,3 (douce). */
fun waterDensityMetersPerBar(freshWater: Boolean): Double = if (freshWater) 10.3 else 10.0
