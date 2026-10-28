package com.divepilot.app.data

import android.content.Context
import com.divepilot.app.deco.TissueState
import com.google.gson.Gson

/** État de plongée persisté (tensions tissulaires + temps de fin de plongée). */
data class StoredDiveState(
    val tissueState: TissueState?,
    val savedAtMillis: Long,
    val lastDiveEndMillis: Long,
)

/**
 * Persiste l'état tissulaire (azote résiduel + CNS/OTU) et le temps de fin de la dernière
 * plongée, afin de conserver l'intervalle de surface et le temps avant avion après un
 * redémarrage de l'application.
 */
class DiveTissueStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("divepilot_tissue", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun save(tissueState: TissueState, savedAtMillis: Long, lastDiveEndMillis: Long) {
        prefs.edit()
            .putString("tissue", gson.toJson(tissueState))
            .putLong("savedAt", savedAtMillis)
            .putLong("lastDiveEnd", lastDiveEndMillis)
            .apply()
    }

    fun load(): StoredDiveState {
        val tissueJson = prefs.getString("tissue", null)
        val tissue = try {
            if (tissueJson != null) gson.fromJson(tissueJson, TissueState::class.java) else null
        } catch (_: Exception) {
            null
        }
        return StoredDiveState(
            tissueState = tissue,
            savedAtMillis = prefs.getLong("savedAt", 0L),
            lastDiveEndMillis = prefs.getLong("lastDiveEnd", 0L),
        )
    }
}
