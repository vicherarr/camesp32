package com.vicherarr.camespdroid.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Estado del dispositivo leído por BLE: [armed, motion, wifi_on]. */
data class DeviceState(val armed: Boolean, val motion: Boolean, val wifiOn: Boolean)

sealed interface BleStatus {
    data object Idle : BleStatus
    data object Scanning : BleStatus
    data object Connecting : BleStatus
    data class Connected(val state: DeviceState) : BleStatus
    data object Disconnected : BleStatus
    data class Error(val msg: String) : BleStatus
}

/**
 * Canal de control BLE con la cámara (servicio "Alarm Control"). Escanea el dispositivo `CAMSEC`,
 * se conecta, se suscribe al Estado (notify) y envía comandos (armar/desarmar/wifi/hora).
 *
 * La media (en vivo/galería) NO va por aquí: al pedir WiFi, la cámara apaga BLE y levanta su AP;
 * la app se conecta entonces con enlace local (ver WifiLink).
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("a1b2c3d4-1111-4a5b-8c6d-000000000001")
        val STATE_UUID: UUID = UUID.fromString("a1b2c3d4-1111-4a5b-8c6d-000000000002")
        val CMD_UUID: UUID = UUID.fromString("a1b2c3d4-1111-4a5b-8c6d-000000000003")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val DEVICE_NAME = "CAMSEC"

        const val CMD_DISARM: Byte = 0x00
        const val CMD_ARM: Byte = 0x01
        const val CMD_WIFI_ON: Byte = 0x02
        const val CMD_WIFI_OFF: Byte = 0x03
        const val CMD_SET_TIME: Byte = 0x04
    }

    private val _status = MutableStateFlow<BleStatus>(BleStatus.Idle)
    val status: StateFlow<BleStatus> = _status.asStateFlow()

    private val bluetoothManager by lazy { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val adapter by lazy { bluetoothManager.adapter }

    private var gatt: BluetoothGatt? = null
    private var cmdChar: BluetoothGattCharacteristic? = null
    private var scanning = false

    // ---- Escaneo + conexión ----
    fun startScanAndConnect() {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            _status.value = BleStatus.Error("Bluetooth no disponible/desactivado")
            return
        }
        if (scanning) return
        scanning = true
        _status.value = BleStatus.Scanning
        val filters = listOf(ScanFilter.Builder().setDeviceName(DEVICE_NAME).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(filters, settings, scanCallback)
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            if (device.name == DEVICE_NAME || result.scanRecord?.deviceName == DEVICE_NAME) {
                stopScan()
                _status.value = BleStatus.Connecting
                gatt = device.connectGatt(context, false, gattCallback, BluetoothProfile.GATT)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            _status.value = BleStatus.Error("Escaneo BLE falló ($errorCode)")
        }
    }

    fun disconnect() {
        stopScan()
        gatt?.disconnect()
    }

    private fun cleanup() {
        cmdChar = null
        gatt?.close()
        gatt = null
    }

    // ---- Callbacks GATT ----
    private val gattCallback = object : android.bluetooth.BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, statusCode: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    cleanup()
                    _status.value = BleStatus.Disconnected
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, statusCode: Int) {
            val service = g.getService(SERVICE_UUID)
            if (service == null) {
                _status.value = BleStatus.Error("Servicio Alarm Control no encontrado")
                return
            }
            cmdChar = service.getCharacteristic(CMD_UUID)
            val stateChar = service.getCharacteristic(STATE_UUID)
            if (stateChar != null) {
                g.setCharacteristicNotification(stateChar, true)
                stateChar.getDescriptor(CCCD_UUID)?.let { cccd ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    } else {
                        @Suppress("DEPRECATION")
                        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION")
                        g.writeDescriptor(cccd)
                    }
                }
                g.readCharacteristic(stateChar)
            }
        }

        override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray, statusCode: Int) {
            if (ch.uuid == STATE_UUID) parseState(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(g: BluetoothGatt, ch: BluetoothGattCharacteristic, statusCode: Int) {
            if (ch.uuid == STATE_UUID) parseState(@Suppress("DEPRECATION") ch.value)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
            if (ch.uuid == STATE_UUID) parseState(value)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            if (ch.uuid == STATE_UUID) parseState(@Suppress("DEPRECATION") ch.value)
        }
    }

    private fun parseState(value: ByteArray?) {
        if (value == null || value.size < 3) return
        _status.value = BleStatus.Connected(
            DeviceState(
                armed = value[0].toInt() != 0,
                motion = value[1].toInt() != 0,
                wifiOn = value[2].toInt() != 0
            )
        )
    }

    // ---- Envío de comandos ----
    private fun send(bytes: ByteArray) {
        val g = gatt ?: return
        val ch = cmdChar ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            ch.value = bytes
            @Suppress("DEPRECATION")
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            g.writeCharacteristic(ch)
        }
    }

    fun arm() = send(byteArrayOf(CMD_ARM))
    fun disarm() = send(byteArrayOf(CMD_DISARM))
    fun wifiOn() = send(byteArrayOf(CMD_WIFI_ON))
    fun wifiOff() = send(byteArrayOf(CMD_WIFI_OFF))

    /** Envía la hora local como epoch en ms (8 bytes little-endian) para fijar el reloj del RTC. */
    fun setTime(epochMs: Long) {
        val payload = ByteArray(9)
        payload[0] = CMD_SET_TIME
        for (i in 0 until 8) payload[1 + i] = ((epochMs shr (8 * i)) and 0xFF).toByte()
        send(payload)
    }
}
