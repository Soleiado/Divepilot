package com.divepilot.app.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/** Gestion de la langue de l'application (français / anglais). */
object LocaleManager {

    private const val PREFS = "divepilot_settings"
    private const val KEY = "app_language"

    fun getLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "fr") ?: "fr"

    fun setLanguage(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, language).apply()
    }

    fun locale(context: Context): Locale =
        if (getLanguage(context) == "en") Locale.ENGLISH else Locale.FRENCH

    /** Enveloppe un contexte pour qu'il utilise la langue sélectionnée. */
    fun wrap(context: Context): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale(context))
        return context.createConfigurationContext(config)
    }
}
