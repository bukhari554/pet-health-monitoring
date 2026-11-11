package com.pethealth.monitor.ble

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
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class BleDeviceHit(
    val name: String,
    val address: String,
    val rssi: Int
)

data class BleLinkState(
    val scanning: Boolean = false,
    val devices: List<BleDeviceHit> = emptyList(),
    val connectedAddress: String? = null,
    val connectedName: String? = null,
    val deviceStatusJson: String? = null,
    val wifi: Boolean = false,
    val mqtt: Boolean = false,
    val cloud: Boolean = false,
    val message: String = "BLE idle",
    val busy: Boolean = false
)

@SuppressLint("MissingPermission")
class BleProvisioner(private val context: Context) {

    companion object {
        val SVC: UUID = UUID.fromString("7a5f0001-4d3e-4b2a-9c1d-8e7f6a5b4c3d")
        val SSID: UUID = UUID.fromString("7a5f0002-4d3e-4b2a-9c1d-8e7f6a5b4c3d")
        val PASS: UUID = UUID.fromString("7a5f0003-4d3e-4b2a-9c1d-8e7f6a5b4c3d")
        val HOST: UUID = UUID.fromString("7a5f0004-4d3e-4b2a-9c1d-8e7f6a5b4c3d")
        val APPLY: UUID = UUID.fromString("7a5f0005-4d3e-4b2a-9c1d-8e7f6a5b4c3d")
        val STATUS: UUID = UUID.fromString("7a5f0006-4d3e-4b2a-9c1d-8e7f6a5b4c3d")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val _state = MutableStateFlow(BleLinkState())
    val state: StateFlow<BleLinkState> = _state.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private val found = linkedMapOf<String, BleDeviceHit>()
    private val writeQueue = ArrayDeque<Pair<BluetoothGattCharacteristic, ByteArray>>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: result.scanRecord?.deviceName ?: return
            if (!name.startsWith("PetHealth-", ignoreCase = true)) return
            found[result.device.address] = BleDeviceHit(name, result.device.address, result.rssi)
            _state.update {
                it.copy(
                    devices = found.values.sortedByDescending { d -> d.rssi },
                    message = "Found ${found.size} device(s)"
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.update { it.copy(scanning = false, message = "Scan failed ($errorCode)") }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _state.update {
                    it.copy(
                        connectedAddress = g.device.address,
                        connectedName = g.device.name,
                        message = "BLE connected — discovering…",
                        busy = true
                    )
                }
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                writeQueue.clear()
                _state.update {
                    it.copy(
                        connectedAddress = null,
                        connectedName = null,
                        busy = false,
                        message = "BLE disconnected"
                    )
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.getService(SVC)
            if (svc == null) {
                _state.update { it.copy(busy = false, message = "Provision service missing") }
                return
            }
            val statusChar = svc.getCharacteristic(STATUS)
            if (statusChar != null) {
                g.setCharacteristicNotification(statusChar, true)
                statusChar.getDescriptor(CCCD)?.let { cccd ->
                    writeDescriptor(g, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                }
                main.postDelayed({ g.readCharacteristic(statusChar) }, 300)
            }
            _state.update { it.copy(busy = false, message = "BLE ready — enter WiFi & Apply") }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == STATUS) parseStatus(value.toString(Charsets.UTF_8))
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == STATUS) {
                @Suppress("DEPRECATION")
                parseStatus(characteristic.value?.toString(Charsets.UTF_8) ?: "")
            }
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (characteristic.uuid == STATUS && status == BluetoothGatt.GATT_SUCCESS) {
                parseStatus(value.toString(Charsets.UTF_8))
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid == STATUS && status == BluetoothGatt.GATT_SUCCESS) {
                @Suppress("DEPRECATION")
                parseStatus(characteristic.value?.toString(Charsets.UTF_8) ?: "")
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                writeQueue.clear()
                _state.update { it.copy(busy = false, message = "BLE write failed") }
                return
            }
            main.postDelayed({ drainWrites(g) }, 80)
        }
    }

    private fun parseStatus(json: String) {
        val wifi = Regex("\"wifi\"\\s*:\\s*(true|1)").containsMatchIn(json)
        val mqtt = Regex("\"mqtt\"\\s*:\\s*(true|1)").containsMatchIn(json)
        val cloud = Regex("\"cloud\"\\s*:\\s*(true|1)").containsMatchIn(json) || (wifi && mqtt)
        _state.update {
            it.copy(
                deviceStatusJson = json,
                wifi = wifi,
                mqtt = mqtt,
                cloud = cloud,
                message = when {
                    cloud -> "CLOUD via MQTT"
                    wifi -> "WiFi up — waiting MQTT"
                    else -> "BLE linked — WiFi down"
                }
            )
        }
    }

    fun startScan() {
        val a = adapter
        if (a == null || !a.isEnabled) {
            _state.update { it.copy(message = "Turn on Bluetooth") }
            return
        }
        found.clear()
        _state.update { it.copy(scanning = true, devices = emptyList(), message = "Scanning PetHealth…") }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        a.bluetoothLeScanner?.startScan(null, settings, scanCallback)
        main.postDelayed({ stopScan() }, 12_000)
    }

    fun stopScan() {
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        _state.update { it.copy(scanning = false) }
    }

    fun connect(address: String) {
        stopScan()
        val device = adapter?.getRemoteDevice(address) ?: run {
            _state.update { it.copy(message = "Device not found") }
            return
        }
        gatt?.close()
        writeQueue.clear()
        _state.update { it.copy(busy = true, message = "Connecting BLE…") }
        gatt = if (Build.VERSION.SDK_INT >= 23) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    fun disconnect() {
        writeQueue.clear()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _state.update {
            it.copy(
                connectedAddress = null,
                connectedName = null,
                message = "BLE disconnected",
                busy = false
            )
        }
    }

    fun provision(ssid: String, password: String, brokerHost: String) {
        val g = gatt
        val svc = g?.getService(SVC)
        if (g == null || svc == null) {
            _state.update { it.copy(message = "Connect to device first") }
            return
        }
        val ssidChar = svc.getCharacteristic(SSID)
        val passChar = svc.getCharacteristic(PASS)
        val hostChar = svc.getCharacteristic(HOST)
        val applyChar = svc.getCharacteristic(APPLY)
        if (ssidChar == null || passChar == null || hostChar == null || applyChar == null) {
            _state.update { it.copy(busy = false, message = "Missing GATT chars") }
            return
        }
        writeQueue.clear()
        writeQueue += ssidChar to ssid.toByteArray(Charsets.UTF_8)
        writeQueue += passChar to password.toByteArray(Charsets.UTF_8)
        writeQueue += hostChar to brokerHost.trim().toByteArray(Charsets.UTF_8)
        writeQueue += applyChar to "1".toByteArray(Charsets.UTF_8)
        _state.update { it.copy(busy = true, message = "Writing credentials over BLE…") }
        drainWrites(g)
    }

    private fun drainWrites(g: BluetoothGatt) {
        val next = writeQueue.removeFirstOrNull()
        if (next == null) {
            _state.update { it.copy(busy = false, message = "Applied — waiting WiFi→MQTT") }
            return
        }
        writeCharacteristic(g, next.first, next.second)
    }

    private fun writeCharacteristic(
        g: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = value
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            g.writeCharacteristic(characteristic)
        }
    }

    private fun writeDescriptor(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray) {
        if (Build.VERSION.SDK_INT >= 33) {
            g.writeDescriptor(descriptor, value)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            g.writeDescriptor(descriptor)
        }
    }

    fun release() {
        stopScan()
        disconnect()
    }
}
