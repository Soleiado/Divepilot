package com.divepilot.app.dive

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Unités d'affichage (profondeur m/ft, température °C/°F). */
data class UnitSettings(
    val depthUnit: String = UNIT_M,
    val tempUnit: String = UNIT_C,
) {
    companion object {
        const val UNIT_M = "m"
        const val UNIT_FT = "ft"
        const val UNIT_C = "C"
        const val UNIT_F = "F"
    }
}

/** Singleton gérant la persistance et l'exposition des unités d'affichage. */
class UnitManager private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("divepilot_units", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<UnitSettings> = _settings.asStateFlow()

    private fun load(): UnitSettings = UnitSettings(
        depthUnit = prefs.getString("depth", UnitSettings.UNIT_M) ?: UnitSettings.UNIT_M,
        tempUnit = prefs.getString("temp", UnitSettings.UNIT_C) ?: UnitSettings.UNIT_C,
    )

    fun setDepthUnit(unit: String) {
        prefs.edit().putString("depth", unit).apply()
        _settings.value = _settings.value.copy(depthUnit = unit)
    }

    fun setTempUnit(unit: String) {
        prefs.edit().putString("temp", unit).apply()
        _settings.value = _settings.value.copy(tempUnit = unit)
    }

    companion object {
        @Volatile
        private var instance: UnitManager? = null

        fun get(context: Context): UnitManager =
            instance ?: synchronized(this) {
                instance ?: UnitManager(context.applicationContext).also { instance = it }
            }
    }
}
