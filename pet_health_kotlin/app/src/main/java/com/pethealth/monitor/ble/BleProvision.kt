package com.pethealth.monitor.ble

import android.Manifest
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
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * GlucoAgri BLE provision — protocol as-is.
 * Kit scans WiFi; phone sends chosen SSID + password over BLE.
 */
class BleProvision(private val context: Context) {
    data class WifiHit(val ssid: String, val rssi: Int, val secure: Boolean)

    private val serviceId = UUID.fromString("4f9a0001-7c2e-4b1a-9d33-51a6c8e04b10")
    private val cmdId = UUID.fromString("4f9a0002-7c2e-4b1a-9d33-51a6c8e04b10")
    private val statusId = UUID.fromString("4f9a0003-7c2e-4b1a-9d33-51a6c8e04b10")
    private val scanId = UUID.fromString("4f9a0004-7c2e-4b1a-9d33-51a6c8e04b10")
    private val cccd = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status.asStateFlow()
    private val _networks = MutableStateFlow<List<WifiHit>>(emptyList())
    val networks: StateFlow<List<WifiHit>> = _networks.asStateFlow()
    private val _linked = MutableStateFlow(false)
    val linked: StateFlow<Boolean> = _linked.asStateFlow()
    private val _wifiUp = MutableStateFlow(false)
    val wifiUp: StateFlow<Boolean> = _wifiUp.asStateFlow()

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter
    private var gatt: BluetoothGatt? = null
    private var notifyLeft = listOf<BluetoothGattCharacteristic>()
    private var searching = false
    private var pending: BluetoothDevice? = null
    private var attempts = 0
    private val handler = Handler(Looper.getMainLooper())
    private val stopSearch = Runnable {
        if (!searching) return@Runnable
        searching = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        if (!_linked.value) _status.value = "Collar not found — power ESP + retry"
    }

    private fun hasBlePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun search() {
        val radio = adapter
        if (radio == null || !radio.isEnabled) {
            _status.value = "Bluetooth is off"
            return
        }
        if (!hasBlePermission()) {
            _status.value = "Allow Bluetooth permission"
            return
        }
        close()
        searching = true
        _networks.value = emptyList()
        _wifiUp.value = false
        _status.value = "Searching PetHealth…"
        attempts = 0
        radio.bondedDevices?.firstOrNull { isPetDevice(it.name) }?.let {
            connect(it)
            return
        }
        val scanner = radio.bluetoothLeScanner ?: run {
            _status.value = "Bluetooth is off"
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        // Prefer service UUID filter; also run unfiltered pass for name-only ads.
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceId)).build()
        )
        try {
            scanner.startScan(filters, settings, callback)
        } catch (_: Exception) {
            scanner.startScan(null, settings, callback)
        }
        handler.removeCallbacks(stopSearch)
        handler.postDelayed(stopSearch, 20_000L)
        // Second unfiltered sweep after 2s in case UUID wasn't in primary packet
        handler.postDelayed({
            if (!searching || _linked.value) return@postDelayed
            runCatching {
                scanner.stopScan(callback)
                scanner.startScan(null, settings, callback)
                _status.value = "Searching (wide)…"
            }
        }, 2_500L)
    }

    @SuppressLint("MissingPermission")
    fun rescan() {
        val g = gatt ?: return
        val cmd = g.getService(serviceId)?.getCharacteristic(cmdId) ?: return
        _networks.value = emptyList()
        _status.value = "Scanning"
        write(g, cmd, byteArrayOf(1))
    }

    @SuppressLint("MissingPermission")
    fun send(ssid: String, pass: String): Boolean {
        val name = ssid.toByteArray(Charsets.UTF_8)
        val secret = pass.toByteArray(Charsets.UTF_8)
        if (name.isEmpty() || name.size > 32 || secret.size > 64) return false
        val g = gatt ?: return false
        val cmd = g.getService(serviceId)?.getCharacteristic(cmdId) ?: return false
        val body = ByteArray(3 + name.size + secret.size)
        body[0] = 2
        body[1] = name.size.toByte()
        name.copyInto(body, 2)
        body[2 + name.size] = secret.size.toByte()
        secret.copyInto(body, 3 + name.size)
        _status.value = "Sending"
        return write(g, cmd, body)
    }

    @SuppressLint("MissingPermission")
    fun close() {
        searching = false
        handler.removeCallbacks(stopSearch)
        runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        gatt?.close()
        gatt = null
        _linked.value = false
    }

    @SuppressLint("MissingPermission")
    private fun write(g: BluetoothGatt, ch: BluetoothGattCharacteristic, bytes: ByteArray): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(ch, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                BluetoothGatt.GATT_SUCCESS
        } else {
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            ch.value = bytes
            @Suppress("DEPRECATION")
            g.writeCharacteristic(ch)
        }
    }

    private fun isPetDevice(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return name.startsWith("PetHealth", true) ||
            name.equals("GlycoSense", true) ||
            name.equals("GlucoTouch", true) ||
            name.equals("GlucoAgri", true)
    }

    private val callback: ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!searching) return
            val record = result.scanRecord
            val name = record?.deviceName ?: result.device.name
            val service = record?.serviceUuids?.any { it.uuid == serviceId } == true
            if (!isPetDevice(name) && !service) return
            searching = false
            handler.removeCallbacks(stopSearch)
            adapter?.bluetoothLeScanner?.stopScan(this)
            connect(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            searching = false
            handler.removeCallbacks(stopSearch)
            _status.value = "Search failed ($errorCode)"
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            val failed = status != BluetoothGatt.GATT_SUCCESS && newState != BluetoothProfile.STATE_CONNECTED
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                attempts = 0
                _linked.value = true
                _status.value = "Connected"
                g.discoverServices()
                return
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED || failed) {
                _linked.value = false
                val device = pending
                if (device != null && attempts < 3) {
                    attempts++
                    if (status == 133) forgetBond(device)
                    _status.value = "Retrying BLE… ($attempts)"
                    g.close()
                    handler.postDelayed({ connect(device) }, 900)
                } else {
                    _status.value = "Could not connect (status $status)"
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _status.value = "Services failed"
                return
            }
            if (g.getService(serviceId) == null) {
                _status.value = "Wrong device (no PetHealth service)"
                return
            }
            g.requestMtu(185)
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            val service = g.getService(serviceId) ?: return
            notifyLeft = listOfNotNull(
                service.getCharacteristic(statusId),
                service.getCharacteristic(scanId)
            )
            enableNext(g)
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (notifyLeft.isEmpty()) rescan() else enableNext(g)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            incoming(characteristic.uuid, value)
        }

        @Deprecated("Legacy callback")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            incoming(characteristic.uuid, characteristic.value ?: return)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        pending = device
        _status.value = "Connecting…"
        gatt?.close()
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    private fun forgetBond(device: BluetoothDevice) {
        runCatching { device.javaClass.getMethod("removeBond").invoke(device) }
    }

    @SuppressLint("MissingPermission")
    private fun enableNext(g: BluetoothGatt) {
        val next = notifyLeft.firstOrNull() ?: return
        notifyLeft = notifyLeft.drop(1)
        g.setCharacteristicNotification(next, true)
        val descriptor = next.getDescriptor(cccd) ?: run {
            if (notifyLeft.isEmpty()) rescan() else enableNext(g)
            return
        }
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(descriptor, value)
        else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            g.writeDescriptor(descriptor)
        }
    }

    private fun incoming(uuid: UUID, value: ByteArray) {
        if (value.isEmpty()) return
        if (uuid == statusId) {
            val text = value.toString(Charsets.UTF_8)
            _status.value = when (text) {
                "scanning" -> "Scanning"
                "saved" -> "Saved on the Pet Health collar"
                "connected" -> {
                    _wifiUp.value = true
                    "Collar is on WiFi"
                }
                "failed" -> "Collar could not join WiFi"
                "idle" -> if (_linked.value) "Connected" else "Idle"
                else -> text
            }
            return
        }
        if (uuid != scanId) return
        if (value[0] == 0x10.toByte()) {
            if (_status.value == "Scanning") _status.value = "Connected"
            return
        }
        if (value[0] != 0x11.toByte() || value.size < 4) return
        val rssi = value[1].toInt()
        val secure = value[2].toInt() != 0
        val ssid = value.copyOfRange(3, value.size).toString(Charsets.UTF_8)
        if (ssid.isBlank()) return
        _networks.value = (_networks.value.filterNot { it.ssid == ssid } + WifiHit(ssid, rssi, secure))
            .sortedByDescending { it.rssi }
    }
}
