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
import android.bluetooth.BluetoothStatusCodes
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
    val busy: Boolean = false,
    val ready: Boolean = false
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

    private sealed class GattOp {
        data class WriteChar(val characteristic: BluetoothGattCharacteristic, val value: ByteArray) : GattOp()
        data class WriteDesc(val descriptor: BluetoothGattDescriptor, val value: ByteArray) : GattOp()
        data class ReadChar(val characteristic: BluetoothGattCharacteristic) : GattOp()
    }

    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private val _state = MutableStateFlow(BleLinkState())
    val state: StateFlow<BleLinkState> = _state.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private val found = linkedMapOf<String, BleDeviceHit>()
    private val opQueue = ArrayDeque<GattOp>()
    private var opInFlight = false
    private var mtuReady = false
    private var servicesReady = false

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
                mtuReady = false
                servicesReady = false
                opQueue.clear()
                opInFlight = false
                _state.update {
                    it.copy(
                        connectedAddress = g.device.address,
                        connectedName = g.device.name,
                        ready = false,
                        message = "BLE connected — negotiating MTU…",
                        busy = true
                    )
                }
                // Larger MTU so WiFi password (>20 chars) can be written in one shot.
                if (Build.VERSION.SDK_INT >= 21) {
                    g.requestMtu(517)
                } else {
                    mtuReady = true
                    g.discoverServices()
                }
                runCatching {
                    g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                resetOps()
                _state.update {
                    it.copy(
                        connectedAddress = null,
                        connectedName = null,
                        ready = false,
                        busy = false,
                        message = "BLE disconnected"
                    )
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            mtuReady = true
            _state.update {
                it.copy(message = "MTU $mtu — discovering services…")
            }
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.update { it.copy(busy = false, ready = false, message = "Service discovery failed") }
                return
            }
            val svc = g.getService(SVC)
            if (svc == null) {
                _state.update { it.copy(busy = false, ready = false, message = "Provision service missing") }
                return
            }
            servicesReady = true
            val statusChar = svc.getCharacteristic(STATUS)
            if (statusChar != null) {
                g.setCharacteristicNotification(statusChar, true)
                statusChar.getDescriptor(CCCD)?.let { cccd ->
                    enqueue(GattOp.WriteDesc(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE))
                }
                enqueue(GattOp.ReadChar(statusChar))
            }
            _state.update {
                it.copy(
                    busy = opInFlight || opQueue.isNotEmpty(),
                    ready = true,
                    message = "BLE ready — enter WiFi & Apply"
                )
            }
            drainOps(g)
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
            opInFlight = false
            main.post { drainOps(g) }
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
            opInFlight = false
            main.post { drainOps(g) }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                resetOps()
                _state.update {
                    it.copy(
                        busy = false,
                        message = "BLE write failed (${characteristic.uuid} status=$status)"
                    )
                }
                return
            }
            opInFlight = false
            main.postDelayed({ drainOps(g) }, 40)
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.update { it.copy(message = "Notify enable failed ($status)") }
            }
            opInFlight = false
            main.postDelayed({ drainOps(g) }, 40)
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
        resetOps()
        _state.update { it.copy(busy = true, ready = false, message = "Connecting BLE…") }
        gatt = if (Build.VERSION.SDK_INT >= 23) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    fun disconnect() {
        resetOps()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _state.update {
            it.copy(
                connectedAddress = null,
                connectedName = null,
                ready = false,
                message = "BLE disconnected",
                busy = false
            )
        }
    }

    /**
     * @param apiBase optional REST base (e.g. https://finrado.com) packed with broker host for ESP
     */
    fun provision(ssid: String, password: String, brokerHost: String, apiBase: String? = null) {
        val g = gatt
        val svc = g?.getService(SVC)
        if (g == null || svc == null || !servicesReady) {
            _state.update { it.copy(message = "Connect to device first and wait until BLE ready") }
            return
        }
        if (ssid.isBlank()) {
            _state.update { it.copy(message = "Enter WiFi SSID") }
            return
        }
        if (ssid.toByteArray(Charsets.UTF_8).size > 32) {
            _state.update { it.copy(message = "SSID too long (max 32 bytes)") }
            return
        }
        if (password.toByteArray(Charsets.UTF_8).size > 63) {
            _state.update { it.copy(message = "Password too long (max 63 bytes)") }
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

        val hostPayload = buildString {
            append(brokerHost.trim())
            val api = apiBase?.trim().orEmpty()
            if (api.startsWith("http://") || api.startsWith("https://")) {
                append(';')
                append(api)
            }
        }

        // Clear leftover setup ops, then write SSID → PASS → HOST → APPLY in order.
        opQueue.clear()
        opInFlight = false
        enqueue(GattOp.WriteChar(ssidChar, ssid.toByteArray(Charsets.UTF_8)))
        enqueue(GattOp.WriteChar(passChar, password.toByteArray(Charsets.UTF_8)))
        enqueue(GattOp.WriteChar(hostChar, hostPayload.toByteArray(Charsets.UTF_8)))
        enqueue(GattOp.WriteChar(applyChar, byteArrayOf('1'.code.toByte())))
        _state.update { it.copy(busy = true, message = "Writing WiFi over BLE…") }
        drainOps(g)
    }

    private fun enqueue(op: GattOp) {
        opQueue.addLast(op)
    }

    private fun resetOps() {
        opQueue.clear()
        opInFlight = false
        mtuReady = false
        servicesReady = false
    }

    private fun drainOps(g: BluetoothGatt) {
        if (opInFlight) return
        val next = opQueue.removeFirstOrNull()
        if (next == null) {
            val provisioningDone = _state.value.message.startsWith("Writing WiFi")
            _state.update {
                it.copy(
                    busy = false,
                    message = if (provisioningDone) "Applied — waiting WiFi→MQTT" else it.message
                )
            }
            return
        }
        opInFlight = true
        val started = when (next) {
            is GattOp.WriteChar -> startWriteCharacteristic(g, next.characteristic, next.value)
            is GattOp.WriteDesc -> startWriteDescriptor(g, next.descriptor, next.value)
            is GattOp.ReadChar -> startReadCharacteristic(g, next.characteristic)
        }
        if (!started) {
            opInFlight = false
            resetOps()
            _state.update { it.copy(busy = false, message = "BLE stack rejected write — retry Apply") }
        }
    }

    private fun startWriteCharacteristic(
        g: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            val code = g.writeCharacteristic(
                characteristic,
                value,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
            code == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = value
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            g.writeCharacteristic(characteristic)
        }
    }

    private fun startWriteDescriptor(
        g: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            val code = g.writeDescriptor(descriptor, value)
            code == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            g.writeDescriptor(descriptor)
        }
    }

    private fun startReadCharacteristic(
        g: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): Boolean {
        @Suppress("DEPRECATION")
        return g.readCharacteristic(characteristic)
    }

    fun release() {
        stopScan()
        disconnect()
    }
}
