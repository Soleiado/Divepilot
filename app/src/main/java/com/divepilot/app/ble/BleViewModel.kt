package com.divepilot.app.ble

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow

class BleViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = BleManager.get(application)

    val state: StateFlow<BleUiState> = manager.state

    fun refreshBondedDevices() = manager.refreshBondedDevices()
    fun startScan() = manager.startScan()
    fun stopScan() = manager.stopScan()
    fun connect(device: ScannedDevice) = manager.connect(device)
    fun disconnect() = manager.disconnect()
    fun clearCommands() = manager.clearCommands()
}
