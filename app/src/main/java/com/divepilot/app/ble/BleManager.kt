package com.divepilot.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.divepilot.app.R
import com.divepilot.app.service.DiveService

data class ScannedDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val bonded: Boolean = false,
)

data class CommandLog(val timestamp: Long, val hex: String, val label: String)

data class TelemetryData(
    val batteryPercent: Int,
    val temperatureCelsius: Double,
    val depthMeters: Double,
)

data class BleUiState(
    val isScanning: Boolean = false,
    val devices: List<ScannedDevice> = emptyList(),
    val connectedDevice: ScannedDevice? = null,
    val connectionStatus: String = "",
    val commands: List<CommandLog> = emptyList(),
    val latestTelemetry: TelemetryData? = null,
)

/**
 * Gestionnaire BLE : scan, connexion GATT et abonnement aux notifications.
 * Les trames reçues sont décodées puis consignées dans [state].
 */
@SuppressLint("MissingPermission")
class BleManager private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: BleManager? = null

        /** Instance partagée à l'échelle de l'application (une seule connexion GATT). */
        fun get(context: Context): BleManager =
            instance ?: synchronized(this) {
                instance ?: BleManager(context.applicationContext).also { instance = it }
            }
    }

    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _state = MutableStateFlow(
        BleUiState(connectionStatus = appContext.getString(R.string.disconnected))
    )
    val state: StateFlow<BleUiState> = _state.asStateFlow()

    /** Flux de trames décodées, pour réagir aux commandes (caméra, etc.). */
    private val _frames = MutableSharedFlow<DecodedFrame>(extraBufferCapacity = 64)
    val frames: SharedFlow<DecodedFrame> = _frames.asSharedFlow()

    private var gatt: BluetoothGatt? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = safeName(device)
            if (name.isNullOrBlank()) return
            val found = ScannedDevice(device.address, name, result.rssi)
            _state.update { s ->
                val idx = s.devices.indexOfFirst { it.address == found.address }
                val devices = if (idx < 0) {
                    s.devices + found
                } else {
                    s.devices.toMutableList().also { it[idx] = found }
                }
                s.copy(devices = devices)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.update {
                it.copy(isScanning = false, connectionStatus = appContext.getString(R.string.scan_failed, errorCode))
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _state.update { it.copy(connectionStatus = appContext.getString(R.string.connected_discovering)) }
                    DiveService.start(appContext)
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _state.update {
                        it.copy(connectedDevice = null, connectionStatus = appContext.getString(R.string.disconnected))
                    }
                    DiveService.stop(appContext)
                    this@BleManager.gatt?.close()
                    this@BleManager.gatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.update { it.copy(connectionStatus = appContext.getString(R.string.service_discovery_failed)) }
                return
            }
            val characteristic = gatt
                .getService(BleConstants.SERVICE_UUID)
                ?.getCharacteristic(BleConstants.NOTIFY_CHAR_UUID)
            if (characteristic == null) {
                _state.update { it.copy(connectionStatus = appContext.getString(R.string.notify_not_found)) }
                return
            }
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(BleConstants.CCCD_UUID)
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
            _state.update { it.copy(connectionStatus = appContext.getString(R.string.subscribed)) }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val bytes = characteristic.value ?: return
            val decoded = ProtocolDecoder.decode(bytes)
            _frames.tryEmit(decoded)
            when (decoded) {
                is DecodedFrame.Command -> {
                    val label = appContext.getString(decoded.command.labelRes)
                    val entry = CommandLog(System.currentTimeMillis(), decoded.rawHex, label)
                    _state.update { s -> s.copy(commands = (s.commands + entry).takeLast(200)) }
                }
                is DecodedFrame.Telemetry -> {
                    val data = TelemetryData(
                        decoded.batteryPercent,
                        decoded.temperatureCelsius,
                        decoded.depthMeters,
                    )
                    _state.update { s -> s.copy(latestTelemetry = data) }
                }
                is DecodedFrame.Unknown -> {
                    val label = appContext.getString(R.string.unknown_frame, decoded.note)
                    val entry = CommandLog(System.currentTimeMillis(), decoded.rawHex, label)
                    _state.update { s -> s.copy(commands = (s.commands + entry).takeLast(200)) }
                }
            }
        }
    }

    fun startScan() {
        val a = adapter ?: run {
            _state.update { it.copy(connectionStatus = appContext.getString(R.string.bluetooth_unavailable)) }
            return
        }
        val scanner = a.bluetoothLeScanner ?: return
        // On conserve les appareils déjà jumelés et on repart d'une liste de scan propre.
        refreshBondedDevices()
        _state.update { it.copy(isScanning = true, devices = it.devices.filter { d -> d.bonded }) }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, scanCallback)
    }

    /** Liste les appareils déjà jumelés (visibles même sans advertising actif). */
    fun refreshBondedDevices() {
        val a = adapter ?: return
        val bonded = try {
            a.bondedDevices
        } catch (_: SecurityException) {
            emptySet()
        }
        val bondedList = bonded.map { device ->
            ScannedDevice(device.address, safeName(device), 0, bonded = true)
        }
        _state.update { s ->
            val merged = (bondedList + s.devices).distinctBy { it.address }
            s.copy(devices = merged)
        }
    }

    fun stopScan() {
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        _state.update { it.copy(isScanning = false) }
    }

    fun connect(device: ScannedDevice) {
        val a = adapter ?: return
        stopScan()
        _state.update { it.copy(connectionStatus = appContext.getString(R.string.connecting, device.name ?: device.address)) }
        gatt?.close()
        gatt = a.getRemoteDevice(device.address)
            .connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.disconnect()
    }

    fun clearCommands() {
        _state.update { it.copy(commands = emptyList()) }
    }

    private fun safeName(device: BluetoothDevice): String? = try {
        device.name
    } catch (_: SecurityException) {
        null
    }
}
