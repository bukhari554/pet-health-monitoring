package com.pethealth.monitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pethealth.monitor.ble.BleLinkState
import com.pethealth.monitor.data.ApiConfig
import com.pethealth.monitor.data.DeviceConnectivity
import com.pethealth.monitor.data.PetBaseline
import com.pethealth.monitor.ui.theme.Amber
import com.pethealth.monitor.ui.theme.Coral
import com.pethealth.monitor.ui.theme.Forest
import com.pethealth.monitor.ui.theme.Ink
import com.pethealth.monitor.ui.theme.Leaf
import com.pethealth.monitor.ui.theme.Sky

@Composable
fun SettingsScreen(
    demoMode: Boolean,
    apiOnline: Boolean,
    baseUrl: String,
    statusMessage: String,
    baseline: PetBaseline?,
    device: DeviceConnectivity?,
    ble: BleLinkState,
    onDemoMode: (Boolean) -> Unit,
    onSaveBaseUrl: (String) -> Unit,
    onRefreshBaseline: () -> Unit,
    onBleScan: () -> Unit,
    onBleStopScan: () -> Unit,
    onBleConnect: (String) -> Unit,
    onBleDisconnect: () -> Unit,
    onBleProvision: (ssid: String, pass: String, host: String) -> Unit
) {
    var urlDraft by remember(baseUrl) { mutableStateOf(baseUrl) }
    var ssid by remember { mutableStateOf("") }
    var wifiPass by remember { mutableStateOf("") }
    var brokerHost by remember {
        mutableStateOf(
            ApiConfig.DEFAULT_MQTT_URL
                .removePrefix("mqtt://")
                .substringBefore(':')
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(statusMessage, fontSize = 13.sp, color = Ink.copy(alpha = 0.6f))
        Spacer(Modifier.height(8.dp))
        Text(
            if (apiOnline) "Backend online" else "Backend offline",
            color = Forest,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(20.dp))
        Text("Connectivity (GlucoTouch-style)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "Phone BLE → device WiFi creds → MQTT cloud",
            fontSize = 12.sp,
            color = Ink.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(10.dp))

        val cloudColor = when {
            device?.cloud == true || ble.cloud -> Forest
            device?.online == true || ble.wifi -> Amber
            ble.connectedAddress != null -> Sky
            else -> Coral
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Text(
                when {
                    device?.cloud == true || ble.cloud -> "CLOUD"
                    ble.wifi || device?.online == true -> "WIFI"
                    ble.connectedAddress != null -> "BLE"
                    else -> "OFFLINE"
                },
                color = cloudColor,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(Modifier.height(6.dp))
            Text("Path: BLE → WiFi → MQTT", fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
            Text("Serial: ${device?.serial ?: ApiConfig.DEVICE_SERIAL}", fontSize = 13.sp)
            Text("BLE: ${ble.message}", fontSize = 13.sp)
            Text("MQTT: ${if (device?.mqttUp == true || ble.mqtt) "up" else "down"}", fontSize = 13.sp)
            Text("WiFi RSSI: ${device?.wifiRssi?.toString() ?: "--"} dBm", fontSize = 13.sp)
            Text("Firmware: ${device?.firmware ?: "--"}", fontSize = 13.sp)
            Text("Last seen: ${device?.lastSeen ?: "--"}", fontSize = 13.sp)
        }

        Spacer(Modifier.height(16.dp))
        Text("BLE provision device", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            "Scan PetHealth-*, connect, send WiFi + PC broker IP",
            fontSize = 12.sp,
            color = Ink.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Button(
                onClick = if (ble.scanning) onBleStopScan else onBleScan,
                modifier = Modifier.weight(1f),
                enabled = !ble.busy
            ) {
                Text(if (ble.scanning) "Stop scan" else "Scan BLE")
            }
            Spacer(Modifier.padding(6.dp))
            OutlinedButton(
                onClick = onBleDisconnect,
                modifier = Modifier.weight(1f),
                enabled = ble.connectedAddress != null
            ) {
                Text("Disconnect")
            }
        }

        if (ble.devices.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ble.devices.forEach { hit ->
                val selected = hit.address == ble.connectedAddress
                Text(
                    "${hit.name}  ${hit.rssi} dBm${if (selected) " · linked" else ""}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !ble.busy) { onBleConnect(hit.address) }
                        .padding(vertical = 8.dp),
                    color = if (selected) Forest else Ink,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = ssid,
            onValueChange = { ssid = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("WiFi SSID") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = wifiPass,
            onValueChange = { wifiPass = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("WiFi password") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = brokerHost,
            onValueChange = { brokerHost = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Broker / PC IP") },
            supportingText = { Text("e.g. 192.168.1.14  (MQTT :1883, API :3000)") }
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onBleProvision(ssid.trim(), wifiPass, brokerHost.trim()) },
            enabled = ble.connectedAddress != null && ssid.isNotBlank() && !ble.busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (ble.busy) "Provisioning…" else "Apply over BLE")
        }

        Spacer(Modifier.height(12.dp))
        Text("Quick reference", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("REST: $baseUrl", fontSize = 12.sp, color = Sky)
        Text("MQTT TCP: ${ApiConfig.DEFAULT_MQTT_URL}", fontSize = 12.sp, color = Sky)
        Text("Device advertises: PetHealth-${ApiConfig.DEVICE_SERIAL}", fontSize = 12.sp, color = Leaf)

        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Demo stream", fontWeight = FontWeight.SemiBold)
                Text("Offline simulation without Fastify", fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
            }
            Switch(checked = demoMode, onCheckedChange = onDemoMode)
        }

        Spacer(Modifier.height(20.dp))
        Text("API base URL", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = urlDraft,
            onValueChange = { urlDraft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = {
                Text("Production: https://finrado.com")
            }
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onSaveBaseUrl(urlDraft) }) { Text("Save API URL") }

        Spacer(Modifier.height(24.dp))
        Text("Personalized baseline", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (baseline == null) {
            Text("No baseline yet", fontSize = 13.sp, color = Ink.copy(alpha = 0.6f))
        } else {
            Text("Resting HR avg: ${"%.1f".format(baseline.restingHrAvg)} ± ${"%.1f".format(baseline.restingHrStd)}", fontSize = 13.sp)
            Text("High resting threshold: ${baseline.highRestingHrThreshold} bpm", fontSize = 13.sp)
            Text("Low HRV threshold: ${baseline.lowHrvThreshold} ms", fontSize = 13.sp)
            Text("Samples: ${baseline.samples}", fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onRefreshBaseline, enabled = apiOnline && !demoMode) {
            Text("Refresh baseline")
        }
    }
}
