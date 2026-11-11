package com.pethealth.monitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pethealth.monitor.ui.PetHealthViewModel
import com.pethealth.monitor.ui.screens.AlertsScreen
import com.pethealth.monitor.ui.screens.DashboardScreen
import com.pethealth.monitor.ui.screens.JournalScreen
import com.pethealth.monitor.ui.screens.SettingsScreen
import com.pethealth.monitor.ui.screens.VitalsScreen
import com.pethealth.monitor.ui.theme.Ink
import com.pethealth.monitor.ui.theme.PetHealthTheme

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not — alerts still show in-app */ }

    private val blePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Settings BLE scan uses granted set */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()
        enableEdgeToEdge()
        setContent {
            PetHealthTheme {
                PetHealthApp()
            }
        }
    }

    private fun requestRuntimePermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (Build.VERSION.SDK_INT >= 31) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ).forEach { p ->
                if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                    needed += p
                }
            }
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (needed.isNotEmpty()) {
            if (needed.size == 1 && needed[0] == Manifest.permission.POST_NOTIFICATIONS) {
                notificationPermission.launch(needed[0])
            } else {
                blePermissions.launch(needed.toTypedArray())
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun PetHealthApp(vm: PetHealthViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val tabs = listOf(
        Tab("dashboard", "Home", Icons.Filled.Home),
        Tab("vitals", "Vitals", Icons.Filled.Favorite),
        Tab("alerts", "Alerts", Icons.Filled.Notifications),
        Tab("journal", "Journal", Icons.AutoMirrored.Filled.MenuBook),
        Tab("settings", "Settings", Icons.Filled.Settings)
    )
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: "dashboard"

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo("dashboard") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            if (tab.route == "alerts" && state.unreadCount > 0) {
                                BadgedBox(badge = { Badge { Text("${state.unreadCount}") } }) {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.label)
                            }
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Text(
                text = state.statusMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                fontSize = 12.sp,
                color = Ink.copy(alpha = 0.65f),
                textAlign = TextAlign.Center
            )
            NavHost(
                navController = nav,
                startDestination = "dashboard",
                modifier = Modifier.weight(1f)
            ) {
                composable("dashboard") {
                    DashboardScreen(
                        pets = state.pets,
                        selectedPetId = state.selectedPetId,
                        latest = state.latest,
                        score = state.healthScore,
                        baseline = state.baseline,
                        unreadAlerts = state.unreadCount,
                        onSelectPet = vm::selectPet
                    )
                }
                composable("vitals") {
                    VitalsScreen(latest = state.latest, history = state.history)
                }
                composable("alerts") {
                    AlertsScreen(
                        alerts = state.alerts,
                        onAck = vm::acknowledge,
                        onAckAll = vm::acknowledgeAll
                    )
                }
                composable("journal") {
                    JournalScreen(latest = state.latest, week = state.week, today = state.today)
                }
                composable("settings") {
                    SettingsScreen(
                        demoMode = state.demoMode,
                        apiOnline = state.apiOnline,
                        statusMessage = state.statusMessage,
                        baseline = state.baseline,
                        device = state.device,
                        ble = state.ble,
                        onDemoMode = vm::setDemoMode,
                        onRefreshBaseline = vm::refreshBaseline,
                        onBleScan = vm::bleScan,
                        onBleStopScan = vm::bleStopScan,
                        onBleConnect = vm::bleConnect,
                        onBleDisconnect = vm::bleDisconnect,
                        onBleProvision = vm::bleProvision
                    )
                }
            }
        }
    }
}
