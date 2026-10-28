package com.divepilot.app

import android.app.Application
import com.divepilot.app.ble.BleManager
import com.divepilot.app.dive.DiveComputerManager
import com.divepilot.app.dive.DiveComputerSettingsManager
import com.divepilot.app.dive.UnitManager

class DivePilotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialise les singletons BLE, ordinateur de plongée, unités et réglages.
        BleManager.get(this)
        DiveComputerManager.get(this)
        UnitManager.get(this)
        DiveComputerSettingsManager.get(this)
    }
}
