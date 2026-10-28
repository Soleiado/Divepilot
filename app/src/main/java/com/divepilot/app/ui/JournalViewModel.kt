package com.divepilot.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.divepilot.app.data.DiveLog
import com.divepilot.app.data.DiveRepository
import kotlinx.coroutines.flow.StateFlow

class JournalViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DiveRepository.get(application)
    val logs: StateFlow<List<DiveLog>> = repository.logs
}
