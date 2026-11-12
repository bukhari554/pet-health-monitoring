package com.pethealth.monitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
import com.pethealth.monitor.ui.theme.LocalPhColors
import com.pethealth.monitor.ui.theme.PetHealthTheme

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val blePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()
        enableEdgeToEdge()
        setContent {
            val vm: PetHealthViewModel = viewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            PetHealthTheme(themeMode = state.themeMode) {
                PetHealthApp(vm)
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
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).forEach { p ->
                if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                    needed += p
                }
            }
        } else {
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).forEach { p ->
                if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                    needed += p
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.NEARBY_WIFI_DEVICES
            }
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

private data class Tab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
private fun PetHealthApp(vm: PetHealthViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val context = LocalContext.current
    val c = LocalPhColors.current
    val tabs = listOf(
        Tab("dashboard", "Home", Icons.Filled.Home, Icons.Outlined.Home),
        Tab("vitals", "Vitals", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
        Tab("alerts", "Alerts", Icons.Filled.Notifications, Icons.Outlined.Notifications),
        Tab("journal", "Journal", Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
        Tab("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    )
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: "dashboard"

    Scaffold(
        containerColor = c.background,
        bottomBar = {
            BottomNavBar(
                tabs = tabs,
                current = current,
                alertCount = state.unreadCount,
                onSelect = { route ->
                    nav.navigate(route) {
                        popUpTo("dashboard") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "dashboard",
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(padding)
        ) {
            composable("dashboard") {
                DashboardScreen(
                    pets = state.pets,
                    selectedPetId = state.selectedPetId,
                    latest = state.latest,
                    score = state.healthScore,
                    baseline = state.baseline,
                    unreadAlerts = state.unreadCount,
                    onSelectPet = vm::selectPet,
                    onOpenSettings = {
                        nav.navigate("settings") {
                            popUpTo("dashboard") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenAlerts = {
                        nav.navigate("alerts") {
                            popUpTo("dashboard") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
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
                JournalScreen(
                    latest = state.latest,
                    week = state.week,
                    today = state.today,
                    onVetExport = {
                        Toast.makeText(context, "Vet-ready PDF snapshot exported!", Toast.LENGTH_SHORT).show()
                    }
                )
            }
            composable("settings") {
                SettingsScreen(
                    baseline = state.baseline,
                    bleStatus = state.bleStatus,
                    bleLinked = state.bleLinked,
                    bleWifiUp = state.bleWifiUp,
                    bleNetworks = state.bleNetworks,
                    themeMode = state.themeMode,
                    onThemeMode = vm::setThemeMode,
                    onRefreshBaseline = vm::refreshBaseline,
                    onBleSearch = vm::bleSearch,
                    onBleRescan = vm::bleRescan,
                    onBleClose = vm::bleClose,
                    onBleSendWifi = vm::bleSendWifi
                )
            }
        }
    }
}

@Composable
private fun BottomNavBar(
    tabs: List<Tab>,
    current: String,
    alertCount: Int,
    onSelect: (String) -> Unit
) {
    val c = LocalPhColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.navBg)
            .border(width = 1.dp, color = c.border)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val selected = current == tab.route
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clickable { onSelect(tab.route) },
                verticalArrangement = Arrangement.Center
            ) {
                Box {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label,
                        tint = if (selected) c.accent else c.muted,
                        modifier = Modifier.size(24.dp)
                    )
                    if (tab.route == "alerts" && alertCount > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 0.dp, end = 0.dp)
                                .background(c.coral, RoundedCornerShape(9.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                "$alertCount",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = c.accentOn
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    tab.label,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) c.accent else c.muted
                )
            }
        }
    }
}
