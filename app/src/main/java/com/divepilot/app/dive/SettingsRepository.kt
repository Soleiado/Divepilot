package com.divepilot.app.dive

import android.content.Context
import com.google.gson.Gson

/** Persistance des réglages de plongée (SharedPreferences + JSON). */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("divepilot_settings", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(): DiveModeSetting {
        val json = prefs.getString(KEY, null) ?: return DiveModeSetting()
        return try {
            gson.fromJson(json, DiveModeSetting::class.java) ?: DiveModeSetting()
        } catch (_: Exception) {
            DiveModeSetting()
        }
    }

    fun save(setting: DiveModeSetting) {
        prefs.edit().putString(KEY, gson.toJson(setting)).apply()
    }

    private companion object {
        const val KEY = "dive_mode_setting"
    }
}
