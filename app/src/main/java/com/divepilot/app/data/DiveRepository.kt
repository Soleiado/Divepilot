package com.divepilot.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Stockage du journal de plongée dans un fichier JSON (réactif via StateFlow). */
class DiveRepository private constructor(context: Context) {

    private val file = File(context.filesDir, "dive_logs.json")
    private val gson = Gson()

    private val _logs = MutableStateFlow(load())
    val logs: StateFlow<List<DiveLog>> = _logs.asStateFlow()

    private fun load(): List<DiveLog> {
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<DiveLog>>() {}.type
            gson.fromJson<List<DiveLog>>(file.readText(), type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addLog(log: DiveLog) {
        val withId = log.copy(id = System.currentTimeMillis())
        _logs.value = _logs.value + withId
        persist()
    }

    fun getLog(id: Long): DiveLog? = _logs.value.firstOrNull { it.id == id }

    private fun persist() {
        try {
            file.writeText(gson.toJson(_logs.value))
        } catch (_: Exception) {
            // Échec d'écriture silencieux : le journal restera en mémoire.
        }
    }

    companion object {
        @Volatile
        private var instance: DiveRepository? = null

        fun get(context: Context): DiveRepository =
            instance ?: synchronized(this) {
                instance ?: DiveRepository(context.applicationContext).also { instance = it }
            }
    }
}
