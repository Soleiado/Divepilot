package com.divepilot.app.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formate une profondeur : « 05.0m » en mètres, « 40ft » en pieds (unité commune). */
fun formatDepth(meters: Double, depthUnit: String = "m"): String {
    return if (depthUnit == "ft") {
        String.format(Locale.US, "%02.0fft", meters * 3.28084)
    } else {
        String.format(Locale.US, "%04.1fm", meters)
    }
}

/** Placeholder « aucune valeur » pour une profondeur, selon l'unité. */
fun depthPlaceholder(depthUnit: String = "m"): String =
    if (depthUnit == "ft") "--ft" else "--.-m"

fun formatTemp(celsius: Double, tempUnit: String = "C"): String {
    val value = if (tempUnit == "F") celsius * 1.8 + 32.0 else celsius
    val unit = if (tempUnit == "F") "°F" else "°C"
    return String.format(Locale.US, "%.1f %s", value, unit)
}

fun formatDuration(millis: Long): String {
    val totalSec = millis / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", min, sec)
}

/** Durée en HH:MM (pour l'intervalle de surface, le temps avant avion, etc.). */
fun formatHoursMinutes(millis: Long): String {
    val totalMin = millis / 60000
    return formatMinutesToHours(totalMin.toDouble())
}

/** Minutes décimales en HH:MM (pour le temps avant avion). */
fun formatMinutesToHours(minutes: Double): String {
    val total = minutes.toInt().coerceAtLeast(0)
    val h = total / 60
    val m = total % 60
    return String.format(Locale.US, "%02d:%02d", h, m)
}

fun formatDateTime(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(millis))

/** Coordonnées GPS au format « lat, lng » (null si absentes). */
fun formatGps(latitude: Double?, longitude: Double?): String? =
    if (latitude == null || longitude == null) null
    else String.format(Locale.US, "%.5f, %.5f", latitude, longitude)
