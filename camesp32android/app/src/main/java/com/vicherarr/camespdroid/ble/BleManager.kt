package com.vicherarr.camespdroid.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class BleState {
    object Idle : BleState()
    object Scanning : BleState()
    data class Found(val deviceName: String, val address: String, val rssi: Int) : BleState()
    object Connecting : BleState()
    object WakeupSent : BleState()
    data class Error(val message: String) : BleState()
}

class BleManager(private val context: Context) {

    private val _bleState = MutableStateFlow<BleState>(BleState.Idle)
    val bleState: StateFlow<BleState> = _bleState.asStateFlow()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        bluetoothManager?.adapter
    }

    private var activeGatt: BluetoothGatt? = null
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("MissingPermission")
    fun triggerWakeup(targetDeviceName: String = "CAM-ACTIVATE") {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _bleState.value = BleState.Error("Bluetooth deshabilitado o no disponible")
            return
        }

        _bleState.value = BleState.Scanning
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _bleState.value = BleState.Error("Escaner BLE no disponible")
            return
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { scanRes ->
                    val device = scanRes.device
                    val foundName = device.name ?: scanRes.scanRecord?.deviceName ?: ""
                    
                    // Match either targetDeviceName ("CAM-ACTIVATE"), "ESP32-CAM-WiFi-Trigger", or any device with "CAM"
                    if (foundName.contains("CAM", ignoreCase = true) || 
                        foundName.contains(targetDeviceName, ignoreCase = true) || 
                        foundName.contains("ESP32", ignoreCase = true)) {
                        
                        _bleState.value = BleState.Found(foundName.ifEmpty { targetDeviceName }, device.address, scanRes.rssi)
                        scanner.stopScan(this)
                        connectAndWakeup(device)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                _bleState.value = BleState.Error("Fallo escaneo BLE: error $errorCode")
            }
        }

        // Start scanning without restrictive hardware filter for maximum Android device compatibility
        scanner.startScan(null, scanSettings, scanCallback)

        // Timeout scan after 12 seconds
        handler.postDelayed({
            if (_bleState.value is BleState.Scanning) {
                scanner.stopScan(scanCallback)
                _bleState.value = BleState.Error("No se encontró el dispositivo BLE '$targetDeviceName'")
            }
        }, 12000)
    }

    @SuppressLint("MissingPermission")
    private fun connectAndWakeup(device: BluetoothDevice) {
        _bleState.value = BleState.Connecting

        activeGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt?.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    _bleState.value = BleState.WakeupSent
                    gatt?.close()
                    activeGatt = null
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                    val service = gatt.services.firstOrNull()
                    val characteristic = service?.characteristics?.firstOrNull()

                    if (characteristic != null) {
                        characteristic.value = byteArrayOf(0x01)
                        gatt.writeCharacteristic(characteristic)
                    }
                    _bleState.value = BleState.WakeupSent
                    handler.postDelayed({ gatt.disconnect() }, 500)
                } else {
                    _bleState.value = BleState.WakeupSent
                    gatt?.disconnect()
                }
            }
        })
    }
}
