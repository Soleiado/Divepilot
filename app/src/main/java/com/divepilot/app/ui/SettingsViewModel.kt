package com.divepilot.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.divepilot.app.dive.DiveModeSetting
import com.divepilot.app.dive.SettingsRepository

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)

    var setting by mutableStateOf(repository.load())
        private set

    fun update(transform: (DiveModeSetting) -> DiveModeSetting) {
        setting = transform(setting)
        repository.save(setting)
    }
}
