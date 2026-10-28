package com.divepilot.app.data

/** Échantillon profondeur/temps d'une plongée (pour le graphique). */
data class DepthSample(
    val timeSeconds: Long,
    val depthMeters: Double,
)

/** Une plongée enregistrée dans le journal. */
data class DiveLog(
    val id: Long = 0L,
    val diveType: String = "scuba",
    val startTimeMillis: Long = 0L,
    val endTimeMillis: Long = 0L,
    val durationSeconds: Long = 0L,
    val maxDepthMeters: Double = 0.0,
    val minTemperature: Double = 0.0,
    val avgDepthMeters: Double = 0.0,
    val avgTemperature: Double = 0.0,
    val samples: List<DepthSample> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Int = 0,
    val freshWater: Boolean = false,
)
