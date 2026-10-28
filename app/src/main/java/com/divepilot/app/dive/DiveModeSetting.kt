package com.divepilot.app.dive

/** Un paramètre réglable : activé (onOff) et une valeur. */
data class ItemSetting(val onOff: Boolean = false, val value: Int = 0)

/** Réglages de plongée (mode SCUBA / Free + alertes). */
data class DiveModeSetting(
    val mode: String = MODE_SCUBA,
    val scubaMaxDepth: ItemSetting = ItemSetting(false, 30),
    val scubaMaxTime: ItemSetting = ItemSetting(false, 60),
    val scubaAscentRate: ItemSetting = ItemSetting(false, 9),
    val freeDepth1: ItemSetting = ItemSetting(false, 10),
    val freeDepth2: ItemSetting = ItemSetting(false, 20),
    val freeDepth3: ItemSetting = ItemSetting(false, 30),
    val freeDepth4: ItemSetting = ItemSetting(false, 40),
    val freeDepth5: ItemSetting = ItemSetting(false, 50),
    val freeSurfaceInterval: ItemSetting = ItemSetting(false, 3),
) {
    companion object {
        const val MODE_SCUBA = "scuba"
        const val MODE_FREE = "free"
    }
}
