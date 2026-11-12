package com.pethealth.monitor.data

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class WifiHit(
    val ssid: String,
    val rssi: Int,
    val secured: Boolean
)

class WifiScanner(private val context: Context) {

    private val wifi: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    @SuppressLint("MissingPermission")
    fun snapshot(): List<WifiHit> {
        val mgr = wifi ?: return emptyList()
        return mgr.scanResults
            .mapNotNull { r ->
                val name = r.SSID?.trim().orEmpty()
                if (name.isBlank() || name == "<unknown ssid>") return@mapNotNull null
                val caps = r.capabilities.orEmpty()
                WifiHit(
                    ssid = name,
                    rssi = r.level,
                    secured = caps.contains("WPA") || caps.contains("WEP") || caps.contains("PSK")
                )
            }
            .groupBy { it.ssid }
            .map { (_, hits) -> hits.maxBy { it.rssi } }
            .sortedByDescending { it.rssi }
    }

    @SuppressLint("MissingPermission")
    fun scan(): Flow<List<WifiHit>> = callbackFlow {
        val mgr = wifi
        if (mgr == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val main = Handler(Looper.getMainLooper())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                trySend(snapshot())
            }
        }
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        trySend(snapshot())
        runCatching { mgr.startScan() }

        val refresh = Runnable { trySend(snapshot()) }
        main.postDelayed(refresh, 2_500L)
        main.postDelayed(refresh, 5_000L)

        awaitClose {
            main.removeCallbacks(refresh)
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
}
