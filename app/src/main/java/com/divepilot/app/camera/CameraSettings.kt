package com.divepilot.app.camera

import android.content.Context
import com.google.gson.Gson

/** Réglages caméra (balance des blancs, taille image/vidéo, exposition, balance des couleurs). */
data class CameraSettings(
    val whiteBalance: Int = 0,  // index dans les modes de balance des blancs (0..6)
    val imageSize: Int = 0,     // 0 = grande (qualité max), 1 = petite
    val videoQuality: Int = 0,  // index dans les qualités vidéo supportées
    val exposureIndex: Int = 0, // correction d'exposition (EV)
    val redGain: Int = 100,     // gain rouge 50..200 (100 = neutre)
    val greenGain: Int = 100,   // gain vert 50..200
    val blueGain: Int = 100,    // gain bleu 50..200
)

class CameraSettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("divepilot_camera", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun load(): CameraSettings = try {
        gson.fromJson(prefs.getString(KEY, null), CameraSettings::class.java) ?: CameraSettings()
    } catch (_: Exception) {
        CameraSettings()
    }

    fun save(settings: CameraSettings) {
        prefs.edit().putString(KEY, gson.toJson(settings)).apply()
    }

    private companion object {
        const val KEY = "camera_settings"
    }
}
