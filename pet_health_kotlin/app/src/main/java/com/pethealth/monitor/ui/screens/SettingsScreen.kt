package com.pethealth.monitor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pethealth.monitor.ble.BleProvision
import com.pethealth.monitor.data.PetBaseline
import com.pethealth.monitor.ui.components.SoftCard
import com.pethealth.monitor.ui.theme.LocalPhColors
import com.pethealth.monitor.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    baseline: PetBaseline?,
    bleStatus: String,
    bleLinked: Boolean,
    bleWifiUp: Boolean,
    bleNetworks: List<BleProvision.WifiHit>,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onRefreshBaseline: () -> Unit,
    onBleSearch: () -> Unit,
    onBleRescan: () -> Unit,
    onBleClose: () -> Unit,
    onBleSendWifi: (ssid: String, pass: String) -> Boolean
) {
    val c = LocalPhColors.current
    var ssid by remember { mutableStateOf("") }
    var wifiPass by remember { mutableStateOf("") }
    var showWifiPass by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c.text)

        SoftCard(Modifier.fillMaxWidth()) {
            Text("Appearance", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEach { mode ->
                    val selected = themeMode == mode
                    Text(
                        when (mode) {
                            ThemeMode.SYSTEM -> "System"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        },
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (selected) c.accent else c.surfaceSoft,
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                1.dp,
                                if (selected) c.accent else c.border,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onThemeMode(mode) }
                            .padding(vertical = 12.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) c.accentOn else c.text
                    )
                }
            }
        }

        SoftCard(Modifier.fillMaxWidth()) {
            Text("Set up device Wi‑Fi", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text)
            Spacer(Modifier.height(6.dp))
            Text(bleStatus, fontSize = 12.sp, color = c.muted)
            if (note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(note, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
            }
            Spacer(Modifier.height(12.dp))

            PrimaryBtn(
                label = if (bleLinked) "Scan BLE" else "Scan BLE",
                onClick = {
                    note = ""
                    onBleSearch()
                }
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryBtn(
                    label = "Rescan Wi‑Fi",
                    enabled = bleLinked,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        note = ""
                        onBleRescan()
                    }
                )
                SecondaryBtn(
                    label = "Disconnect",
                    enabled = bleLinked,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onBleClose()
                        note = "BLE closed"
                    }
                )
            }

            if (bleNetworks.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Networks from collar", fontSize = 11.sp, color = c.muted)
                Spacer(Modifier.height(6.dp))
                bleNetworks.forEach { hit ->
                    val selected = ssid == hit.ssid
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .background(if (selected) c.okBg else c.surfaceSoft, RoundedCornerShape(12.dp))
                            .border(
                                1.dp,
                                if (selected) c.accent else c.border,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { ssid = hit.ssid }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(hit.ssid, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = c.text)
                            Text(
                                if (hit.secure) "Secured" else "Open",
                                fontSize = 10.sp,
                                color = c.muted
                            )
                        }
                        Text("${hit.rssi} dBm", fontSize = 11.sp, color = c.muted)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SoftField(ssid, { ssid = it }, "Wi‑Fi name (SSID)")
            Spacer(Modifier.height(10.dp))
            SoftField(
                value = wifiPass,
                onValueChange = { wifiPass = it },
                placeholder = "Wi‑Fi password",
                isPassword = !showWifiPass,
                trailing = {
                    IconButton(onClick = { showWifiPass = !showWifiPass }) {
                        Icon(
                            if (showWifiPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            null,
                            tint = c.muted
                        )
                    }
                }
            )
            Spacer(Modifier.height(12.dp))
            PrimaryBtn(
                label = if (bleLinked) "Send credentials" else "Wait for BLE ready…",
                enabled = bleLinked && ssid.isNotBlank(),
                onClick = {
                    note = if (onBleSendWifi(ssid.trim(), wifiPass)) {
                        if (bleLinked) wifiPass = ""
                        "Sent"
                    } else {
                        "Not linked"
                    }
                }
            )

            if (bleWifiUp) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(c.okBg, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        "Collar is on Wi‑Fi — Home & Vitals updating live",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = c.okFg
                    )
                }
            }
        }

        SoftCard(Modifier.fillMaxWidth()) {
            Text("Personalized baseline", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text)
            Spacer(Modifier.height(12.dp))
            if (baseline == null) {
                Text("No baseline yet — fills from live records", fontSize = 13.sp, color = c.muted)
            } else {
                BaselineRow("Resting HR avg", "%.1f ± %.1f bpm".format(baseline.restingHrAvg, baseline.restingHrStd))
                BaselineRow("High resting threshold", "${baseline.highRestingHrThreshold} bpm")
                BaselineRow("Low HRV threshold", "${baseline.lowHrvThreshold} ms")
                BaselineRow("Samples collected", "${baseline.samples}", last = true)
            }
            Spacer(Modifier.height(12.dp))
            PrimaryBtn(label = "Refresh baseline", onClick = onRefreshBaseline)
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun PrimaryBtn(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalPhColors.current
    Text(
        label,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (enabled) c.accent else c.surfaceSoft, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        textAlign = TextAlign.Center,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = if (enabled) c.accentOn else c.muted
    )
}

@Composable
private fun SecondaryBtn(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val c = LocalPhColors.current
    Text(
        label,
        modifier = modifier
            .background(c.surface, RoundedCornerShape(12.dp))
            .border(1.dp, c.border, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        textAlign = TextAlign.Center,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = if (enabled) c.text else c.muted.copy(alpha = 0.45f)
    )
}

@Composable
private fun SoftField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    trailing: @Composable (() -> Unit)? = null
) {
    val c = LocalPhColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(placeholder, fontSize = 14.sp, color = c.muted) },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (isPassword) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        trailingIcon = trailing,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = c.surface,
            unfocusedContainerColor = c.surfaceSoft,
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.border,
            focusedTextColor = c.text,
            unfocusedTextColor = c.text,
            cursorColor = c.accent
        )
    )
}

@Composable
private fun BaselineRow(label: String, value: String, last: Boolean = false) {
    val c = LocalPhColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.sp, color = c.muted)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.text)
        }
        if (!last) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Spacer(Modifier.height(8.dp))
        }
    }
}
