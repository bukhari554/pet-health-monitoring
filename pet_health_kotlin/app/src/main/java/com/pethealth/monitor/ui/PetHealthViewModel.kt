package com.pethealth.monitor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pethealth.monitor.ble.BleProvision
import com.pethealth.monitor.data.ApiConfig
import com.pethealth.monitor.data.DailySummary
import com.pethealth.monitor.data.DeviceConnectivity
import com.pethealth.monitor.data.HealthAlert
import com.pethealth.monitor.data.HealthScore
import com.pethealth.monitor.data.Pet
import com.pethealth.monitor.data.PetBaseline
import com.pethealth.monitor.data.PetHealthApi
import com.pethealth.monitor.data.PetHealthApi.NoReadingYetException
import com.pethealth.monitor.data.ReadingStore
import com.pethealth.monitor.data.SensorReading
import com.pethealth.monitor.data.ThemePrefs
import com.pethealth.monitor.domain.DemoSensorStream
import com.pethealth.monitor.domain.FusionAlertEngine
import com.pethealth.monitor.domain.HealthScoreService
import com.pethealth.monitor.domain.JournalService
import com.pethealth.monitor.notify.AlertNotifier
import com.pethealth.monitor.ui.theme.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class UiState(
    val pets: List<Pet> = emptyList(),
    val selectedPetId: String = ApiConfig.BUDDY_ID,
    val latest: SensorReading? = null,
    val history: List<SensorReading> = emptyList(),
    val alerts: List<HealthAlert> = emptyList(),
    val healthScore: HealthScore? = null,
    val week: List<DailySummary> = emptyList(),
    val baseline: PetBaseline? = null,
    val device: DeviceConnectivity? = null,
    val deviceSerial: String = ApiConfig.DEVICE_SERIAL,
    val mqttUrl: String = ApiConfig.DEFAULT_MQTT_URL,
    val demoMode: Boolean = false,
    val apiOnline: Boolean = false,
    val baseUrl: String = ApiConfig.DEFAULT_BASE_URL,
    val statusMessage: String = "Connecting…",
    // Gluco-form BLE link
    val bleStatus: String = "Idle",
    val bleLinked: Boolean = false,
    val bleWifiUp: Boolean = false,
    val bleNetworks: List<BleProvision.WifiHit> = emptyList(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM
) {
    val selectedPet: Pet?
        get() = pets.firstOrNull { it.id == selectedPetId }

    val activeAlerts: List<HealthAlert>
        get() = alerts.filter { !it.isAcknowledged }

    val unreadCount: Int
        get() = activeAlerts.size

    val today: DailySummary?
        get() = week.lastOrNull()
}

class PetHealthViewModel(app: Application) : AndroidViewModel(app) {
    private val api = PetHealthApi()
    private val fusion = FusionAlertEngine()
    private val scoreService = HealthScoreService()
    private val journal = JournalService()
    private val notifier = AlertNotifier(app.applicationContext)
    private val ble = BleProvision(app.applicationContext)
    private val readingStore = ReadingStore(app.applicationContext)
    private val themePrefs = ThemePrefs(app.applicationContext)
    private val notifiedIds = mutableSetOf<String>()

    private val _state = MutableStateFlow(
        UiState(
            pets = listOf(
                Pet(ApiConfig.BUDDY_ID, "Buddy", "Dog", "Labrador", 3),
                Pet(ApiConfig.MILO_ID, "Milo", "Cat", "Persian", 2)
            ),
            themeMode = themePrefs.load()
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var loopJob: Job? = null
    private var sawWifi = false
    private var fallbackStream: DemoSensorStream? = null

    companion object {
        /** Cloud reading older than this → drive local live vitals so UI keeps moving. */
        private const val STALE_MS = 12_000L
    }

    init {
        notifier.ensureChannel()
        val petId = _state.value.selectedPetId
        fallbackStream = DemoSensorStream(petId)
        val cached = readingStore.load(petId)
        if (cached.isNotEmpty()) {
            _state.update { it.copy(history = cached, latest = cached.lastOrNull()) }
        }
        viewModelScope.launch {
            combine(ble.status, ble.linked, ble.networks, ble.wifiUp) { status, linked, nets, wifi ->
                Quad(status, linked, nets, wifi)
            }.collect { q ->
                _state.update {
                    it.copy(
                        bleStatus = q.a,
                        bleLinked = q.b,
                        bleNetworks = q.c,
                        bleWifiUp = q.d
                    )
                }
                if (q.d && !sawWifi) {
                    sawWifi = true
                    startLiveLoop()
                }
            }
        }
        startLiveLoop()
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    fun bleSearch() = ble.search()
    fun bleRescan() = ble.rescan()
    fun bleClose() {
        ble.close()
        sawWifi = false
        _state.update { it.copy(bleWifiUp = false) }
    }

    fun bleSendWifi(ssid: String, password: String): Boolean {
        val ok = ble.send(ssid, password)
        if (ok) {
            _state.update { it.copy(statusMessage = "Credentials sent — waiting WiFi…") }
            startLiveLoop()
        }
        return ok
    }

    override fun onCleared() {
        ble.close()
        super.onCleared()
    }

    fun selectPet(id: String) {
        if (id == _state.value.selectedPetId) return
        notifiedIds.clear()
        fallbackStream = DemoSensorStream(id)
        val cached = readingStore.load(id)
        _state.update {
            it.copy(
                selectedPetId = id,
                latest = cached.lastOrNull(),
                history = cached,
                alerts = emptyList(),
                healthScore = null,
                week = emptyList(),
                baseline = null,
                statusMessage = "Switching pet…"
            )
        }
        startLiveLoop()
    }

    fun acknowledge(id: String) {
        viewModelScope.launch {
            if (!_state.value.demoMode && _state.value.apiOnline) {
                runCatching { api.acknowledge(id) }
            }
            _state.update { s ->
                val alerts = s.alerts.map {
                    if (it.id == id) it.copy(isAcknowledged = true) else it
                }
                if (!s.demoMode && s.apiOnline) s.copy(alerts = alerts)
                else recomputeLocal(s.copy(alerts = alerts))
            }
        }
    }

    fun acknowledgeAll() {
        viewModelScope.launch {
            val petId = _state.value.selectedPetId
            if (!_state.value.demoMode && _state.value.apiOnline) {
                runCatching { api.acknowledgeAll(petId) }
            }
            _state.update { s ->
                val alerts = s.alerts.map { it.copy(isAcknowledged = true) }
                if (!s.demoMode && s.apiOnline) s.copy(alerts = alerts)
                else recomputeLocal(s.copy(alerts = alerts))
            }
        }
    }

    fun setDemoMode(enabled: Boolean) {
        _state.update {
            it.copy(
                demoMode = enabled,
                statusMessage = if (enabled) "Demo stream" else "Live API mode"
            )
        }
        startLiveLoop()
    }

    fun refreshBaseline() {
        viewModelScope.launch {
            val petId = _state.value.selectedPetId
            runCatching { api.refreshBaseline(petId) }
                .onSuccess { baseline ->
                    _state.update { it.copy(baseline = baseline, statusMessage = "Baseline refreshed") }
                }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        themePrefs.save(mode)
        _state.update { it.copy(themeMode = mode) }
    }

    private fun startLiveLoop() {
        loopJob?.cancel()
        loopJob = viewModelScope.launch {
            if (_state.value.demoMode) runDemo() else runApiPolling()
        }
    }

    private suspend fun runApiPolling() {
        var tick = 0
        while (currentCoroutineContext().isActive) {
            val petId = _state.value.selectedPetId
            var appliedFreshCloud = false
            try {
                if (!api.health()) error("API offline")
                val refreshSlow = tick % 5 == 0
                val pets = if (refreshSlow) {
                    runCatching { api.listPets() }.getOrDefault(_state.value.pets)
                } else _state.value.pets
                val serverHistory = if (refreshSlow || _state.value.history.isEmpty()) {
                    runCatching { api.history(petId, limit = 60) }.getOrDefault(_state.value.history)
                } else _state.value.history
                val alerts = if (refreshSlow) {
                    runCatching { api.alerts(petId) }.getOrDefault(_state.value.alerts)
                } else _state.value.alerts
                val week = if (refreshSlow) {
                    runCatching { api.journal(petId) }.getOrDefault(_state.value.week)
                } else _state.value.week
                val baseline = if (refreshSlow) {
                    runCatching { api.baseline(petId) }.getOrNull() ?: _state.value.baseline
                } else _state.value.baseline
                val device = runCatching {
                    api.deviceConnectivity(_state.value.deviceSerial)
                }.getOrNull()

                try {
                    val vitals = api.latestVitals(petId)
                    val reading = vitals.reading
                    val score = vitals.healthScore
                    val age = vitals.ageMs
                        ?: (System.currentTimeMillis() - reading.timestampMs)
                    maybeNotify(alerts)
                    val collarLive = vitals.live ||
                        (vitals.deviceOnline && age <= STALE_MS) ||
                        (device?.online == true && age <= STALE_MS)
                    val merged = mergeAndStore(petId, serverHistory, reading)
                    if (collarLive) {
                        appliedFreshCloud = true
                        val linkMsg = when {
                            vitals.cloud || device?.cloud == true || _state.value.bleWifiUp ->
                                "Live · collar"
                            else -> "Live · cloud"
                        }
                        _state.update {
                            it.copy(
                                pets = if (pets.isNotEmpty()) pets else it.pets,
                                latest = reading,
                                history = merged,
                                alerts = alerts,
                                healthScore = score,
                                week = week,
                                baseline = baseline,
                                device = device,
                                apiOnline = true,
                                statusMessage = linkMsg
                            )
                        }
                    } else {
                        // Offline / stale: freeze last real reading — never invent vitals.
                        val ageLabel = formatAge(age)
                        _state.update {
                            it.copy(
                                pets = if (pets.isNotEmpty()) pets else it.pets,
                                latest = reading,
                                history = merged,
                                alerts = alerts,
                                week = week,
                                baseline = baseline,
                                device = device,
                                apiOnline = true,
                                healthScore = score,
                                statusMessage = when {
                                    !vitals.deviceOnline && device?.online != true ->
                                        "Offline · last reading $ageLabel"
                                    else -> "Stale · last reading $ageLabel"
                                }
                            )
                        }
                    }
                } catch (_: NoReadingYetException) {
                    _state.update {
                        it.copy(
                            pets = if (pets.isNotEmpty()) pets else it.pets,
                            history = mergeLocal(petId, serverHistory),
                            alerts = alerts,
                            week = week,
                            baseline = baseline,
                            device = device,
                            apiOnline = true,
                            statusMessage = when {
                                device?.cloud == true || it.bleWifiUp ->
                                    "Cloud up · waiting collar reading"
                                it.bleLinked -> "BLE linked · send WiFi in Settings"
                                device?.online == true -> "Online · waiting first reading"
                                else -> "Offline · no collar readings yet"
                            }
                        )
                    }
                }
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        apiOnline = false,
                        statusMessage = "API offline · showing last saved reading"
                    )
                }
            }

            // Fake live stream only in explicit Demo mode — never while "offline".
            if (!appliedFreshCloud && _state.value.demoMode) {
                applyLocalLiveTick(petId)
            }

            tick++
            delay(1000)
        }
    }

    private fun formatAge(ageMs: Long): String {
        val sec = (ageMs / 1000).coerceAtLeast(0)
        return when {
            sec < 60 -> "${sec}s ago"
            sec < 3600 -> "${sec / 60}m ago"
            sec < 86400 -> "${sec / 3600}h ago"
            else -> "${sec / 86400}d ago"
        }
    }

    private fun applyLocalLiveTick(petId: String) {
        val stream = fallbackStream ?: DemoSensorStream(petId).also { fallbackStream = it }
        applyDemoReading(stream.sample())
    }

    private fun mergeLocal(petId: String, server: List<SensorReading>): List<SensorReading> {
        val local = readingStore.load(petId)
        return (local + server)
            .distinctBy { it.timestampMs }
            .sortedBy { it.timestampMs }
            .takeLast(120)
            .also { readingStore.save(petId, it) }
    }

    private fun mergeAndStore(
        petId: String,
        existing: List<SensorReading>,
        latest: SensorReading
    ): List<SensorReading> {
        val local = readingStore.load(petId)
        val withoutDup = (local + existing).filterNot { it.timestampMs == latest.timestampMs }
        val merged = (withoutDup + latest).sortedBy { it.timestampMs }.takeLast(120)
        readingStore.save(petId, merged)
        return merged
    }

    private suspend fun runDemo() {
        DemoSensorStream(_state.value.selectedPetId).readings().collect { reading ->
            applyDemoReading(reading)
            _state.update { it.copy(statusMessage = "Demo stream (offline)") }
        }
    }

    private fun applyDemoReading(reading: SensorReading) {
        _state.update { s ->
            val history = readingStore.append(s.selectedPetId, reading)
            val newAlerts = fusion.evaluate(reading)
            maybeNotify(newAlerts)
            val alerts = (newAlerts + s.alerts).take(50)
            val open = alerts.count { !it.isAcknowledged }
            val score = scoreService.compute(reading, open, s.baseline)
            val week = journal.buildWeek(reading, open, score.score)
            s.copy(
                latest = reading,
                history = history,
                alerts = alerts,
                healthScore = score,
                week = week
            )
        }
    }

    private fun maybeNotify(alerts: List<HealthAlert>) {
        alerts.filter { !it.isAcknowledged }.forEach { alert ->
            if (notifiedIds.add(alert.id)) notifier.notifyCritical(alert)
        }
    }

    private fun recomputeLocal(s: UiState): UiState {
        val latest = s.latest ?: return s
        val open = s.alerts.count { !it.isAcknowledged }
        val score = scoreService.compute(latest, open, s.baseline)
        val week = journal.buildWeek(latest, open, score.score)
        return s.copy(healthScore = score, week = week)
    }
}
