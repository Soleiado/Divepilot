package com.divepilot.app.deco

/**
 * Constantes du modèle Bühlmann ZHL-16C (azote uniquement).
 * Demi-vies en minutes, coefficients a/b publiés (Tauchmedizin, Bühlmann).
 */
object Zhl16 {
    const val COMPARTMENTS = 16
    const val WATER_VAPOR = 0.0627 // pression partielle de vapeur d'eau alvéolaire (bar)

    val HALF_TIMES_MIN = doubleArrayOf(
        4.0, 8.0, 12.5, 18.5, 27.0, 38.3, 54.3, 77.0,
        109.0, 146.0, 187.0, 239.0, 305.0, 390.0, 498.0, 635.0,
    )

    val N2_A = doubleArrayOf(
        1.2599, 1.0000, 0.8618, 0.7562, 0.6667, 0.5933, 0.5282, 0.4701,
        0.4187, 0.3798, 0.3497, 0.3223, 0.2971, 0.2737, 0.2523, 0.2327,
    )

    val N2_B = doubleArrayOf(
        0.5050, 0.6514, 0.7222, 0.7825, 0.8126, 0.8434, 0.8693, 0.8910,
        0.9092, 0.9222, 0.9319, 0.9403, 0.9477, 0.9544, 0.9602, 0.9653,
    )
}
