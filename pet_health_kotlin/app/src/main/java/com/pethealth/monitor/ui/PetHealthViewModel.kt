package com.pethealth.monitor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pethealth.monitor.ble.BleLinkState
import com.pethealth.monitor.ble.BleProvisioner
import com.pethealth.monitor.data.ApiConfig
import com.pethealth.monitor.data.DailySummary
import com.pethealth.monitor.data.DeviceConnectivity
import com.pethealth.monitor.data.HealthAlert
import com.pethealth.monitor.data.HealthScore
import com.pethealth.monitor.data.Pet
import com.pethealth.monitor.data.PetBaseline
import com.pethealth.monitor.data.PetHealthApi
import com.pethealth.monitor.data.PetHealthApi.NoReadingYetException
import com.pethealth.monitor.data.SensorReading
import com.pethealth.monitor.domain.DemoSensorStream
import com.pethealth.monitor.domain.FusionAlertEngine
import com.pethealth.monitor.domain.HealthScoreService
import com.pethealth.monitor.domain.JournalService
import com.pethealth.monitor.notify.AlertNotifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val ble: BleLinkState = BleLinkState()
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
    private val ble = BleProvisioner(app.applicationContext)
    private val notifiedIds = mutableSetOf<String>()

    private val _state = MutableStateFlow(
        UiState(
            pets = listOf(
                Pet(ApiConfig.BUDDY_ID, "Buddy", "Dog", "Labrador", 3),
                Pet(ApiConfig.MILO_ID, "Milo", "Cat", "Persian", 2)
            )
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var loopJob: Job? = null

    init {
        notifier.ensureChannel()
        viewModelScope.launch {
            ble.state.collect { link ->
                _state.update { it.copy(ble = link) }
            }
        }
        startLiveLoop()
    }

    fun bleScan() = ble.startScan()
    fun bleStopScan() = ble.stopScan()
    fun bleConnect(address: String) = ble.connect(address)
    fun bleDisconnect() = ble.disconnect()
    fun bleProvision(ssid: String, password: String) {
        val brokerHost = _state.value.mqttUrl
            .removePrefix("mqtt://")
            .removePrefix("mqtts://")
            .substringBefore(':')
            .substringBefore('/')
        ble.provision(ssid, password, brokerHost, _state.value.baseUrl)
    }

    override fun onCleared() {
        ble.release()
        super.onCleared()
    }

    fun selectPet(id: String) {
        if (id == _state.value.selectedPetId) return
        notifiedIds.clear()
        _state.update {
            it.copy(
                selectedPetId = id,
                latest = null,
                history = emptyList(),
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
                // Live mode: only flip ack flags — keep API score/journal intact.
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

    fun setBaseUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        api.setBaseUrl(clean)
        _state.update { it.copy(baseUrl = clean, statusMessage = "API URL updated") }
        if (!_state.value.demoMode) startLiveLoop()
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

    private fun startLiveLoop() {
        loopJob?.cancel()
        loopJob = viewModelScope.launch {
            if (_state.value.demoMode) runDemo() else runApiPolling()
        }
    }

    private suspend fun runApiPolling() {
        while (currentCoroutineContext().isActive) {
            val petId = _state.value.selectedPetId
            try {
                if (!api.health()) error("API offline")
                val pets = runCatching { api.listPets() }.getOrDefault(_state.value.pets)
                val history = runCatching { api.history(petId) }.getOrDefault(emptyList())
                val alerts = runCatching { api.alerts(petId) }.getOrDefault(emptyList())
                val week = runCatching { api.journal(petId) }.getOrDefault(emptyList())
                val baseline = runCatching { api.baseline(petId) }.getOrNull()
                val device = runCatching {
                    api.deviceConnectivity(_state.value.deviceSerial)
                }.getOrNull()
                val linkMsg = when {
                    device?.cloud == true -> "CLOUD · MQTT up · live"
                    device?.online == true -> "Device online · MQTT down (HTTP grace)"
                    else -> "Live · Fastify/Postgres"
                }

                try {
                    val (reading, score) = api.latestVitals(petId)
                    maybeNotify(alerts)
                    _state.update {
                        it.copy(
                            pets = if (pets.isNotEmpty()) pets else it.pets,
                            latest = reading,
                            history = history,
                            alerts = alerts,
                            healthScore = score,
                            week = week,
                            baseline = baseline,
                            device = device,
                            apiOnline = true,
                            statusMessage = linkMsg
                        )
                    }
                } catch (_: NoReadingYetException) {
                    _state.update {
                        it.copy(
                            pets = if (pets.isNotEmpty()) pets else it.pets,
                            history = history,
                            alerts = alerts,
                            week = week,
                            baseline = baseline,
                            device = device,
                            apiOnline = true,
                            statusMessage = if (device?.cloud == true) {
                                "CLOUD up · waiting first reading"
                            } else {
                                "API online · waiting for device"
                            }
                        )
                    }
                }
            } catch (_: Exception) {
                // Keep last live reading; do not inject demo vitals over real data.
                _state.update {
                    it.copy(
                        apiOnline = false,
                        statusMessage = "API unreachable · last live reading kept"
                    )
                }
            }
            delay(2000)
        }
    }

    private suspend fun runDemo() {
        DemoSensorStream(_state.value.selectedPetId).readings().collect { reading ->
            applyDemoReading(reading)
            _state.update { it.copy(statusMessage = "Demo stream (offline)") }
        }
    }

    private fun applyDemoReading(reading: SensorReading) {
        _state.update { s ->
            val history = (s.history + reading).takeLast(60)
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
            if (notifiedIds.add(alert.id)) {
                notifier.notifyCritical(alert)
            }
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
