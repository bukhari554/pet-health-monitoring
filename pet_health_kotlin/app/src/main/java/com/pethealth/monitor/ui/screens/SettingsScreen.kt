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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pethealth.monitor.ble.BleLinkState
import com.pethealth.monitor.data.DeviceConnectivity
import com.pethealth.monitor.data.PetBaseline
import com.pethealth.monitor.ui.theme.Amber
import com.pethealth.monitor.ui.theme.Coral
import com.pethealth.monitor.ui.theme.Forest
import com.pethealth.monitor.ui.theme.Ink
import com.pethealth.monitor.ui.theme.Sky

@Composable
fun SettingsScreen(
    demoMode: Boolean,
    apiOnline: Boolean,
    statusMessage: String,
    baseline: PetBaseline?,
    device: DeviceConnectivity?,
    ble: BleLinkState,
    onDemoMode: (Boolean) -> Unit,
    onRefreshBaseline: () -> Unit,
    onBleScan: () -> Unit,
    onBleStopScan: () -> Unit,
    onBleConnect: (String) -> Unit,
    onBleDisconnect: () -> Unit,
    onBleProvision: (ssid: String, pass: String) -> Unit
) {
    var ssid by remember { mutableStateOf("") }
    var wifiPass by remember { mutableStateOf("") }
    var showWifiPass by remember { mutableStateOf(false) }

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
        Text("Connectivity", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "Connect collar over Bluetooth, then share your Wi‑Fi",
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
            Text("Status: ${ble.message}", fontSize = 13.sp)
            Text(
                "Link: ${if (device?.mqttUp == true || ble.mqtt) "cloud up" else if (ble.wifi || device?.online == true) "wifi only" else "waiting"}",
                fontSize = 13.sp
            )
            Text("Signal: ${device?.wifiRssi?.toString() ?: "--"} dBm", fontSize = 13.sp)
            Text("Last seen: ${device?.lastSeen ?: "--"}", fontSize = 13.sp)
        }

        Spacer(Modifier.height(16.dp))
        Text("Set up device Wi‑Fi", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(
            "Scan → tap your collar → wait until ready → Apply",
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
            label = { Text("Wi‑Fi name (SSID)") }
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = wifiPass,
            onValueChange = { wifiPass = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Wi‑Fi password") },
            visualTransformation = if (showWifiPass) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showWifiPass = !showWifiPass }) {
                    Icon(
                        imageVector = if (showWifiPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showWifiPass) "Hide password" else "Show password"
                    )
                }
            }
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { onBleProvision(ssid.trim(), wifiPass) },
            enabled = ble.ready && ssid.isNotBlank() && !ble.busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    ble.busy -> "Provisioning…"
                    !ble.ready -> "Wait for BLE ready…"
                    else -> "Apply over BLE"
                }
            )
        }

        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Demo stream", fontWeight = FontWeight.SemiBold)
                Text("Offline simulation without live backend", fontSize = 12.sp, color = Ink.copy(alpha = 0.6f))
            }
            Switch(checked = demoMode, onCheckedChange = onDemoMode)
        }

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
