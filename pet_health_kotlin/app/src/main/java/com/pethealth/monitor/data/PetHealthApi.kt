package com.pethealth.monitor.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class PetHealthApi(private var baseUrl: String = ApiConfig.DEFAULT_BASE_URL) {

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        try {
            val raw = get("/health")
            JSONObject(raw).optBoolean("ok", false)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun listPets(): List<Pet> = withContext(Dispatchers.IO) {
        val arr = JSONArray(get("/pets"))
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    Pet(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        type = o.getString("type"),
                        breed = o.getString("breed"),
                        age = o.getInt("age")
                    )
                )
            }
        }
    }

    class NoReadingYetException : Exception("No readings yet")

    suspend fun latestVitals(petId: String): Pair<SensorReading, HealthScore?> =
        withContext(Dispatchers.IO) {
            val raw = try {
                get("/pets/$petId/vitals/latest")
            } catch (e: IllegalStateException) {
                if (e.message?.contains("HTTP 404") == true) throw NoReadingYetException()
                throw e
            }
            val o = JSONObject(raw)
            val reading = parseReading(o.getJSONObject("reading"))
            val score = o.optJSONObject("healthScore")?.let { parseScore(it) }
            reading to score
        }

    suspend fun history(petId: String, limit: Int = 60): List<SensorReading> =
        withContext(Dispatchers.IO) {
            val arr = JSONArray(get("/pets/$petId/vitals/history?limit=$limit"))
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SensorReading(
                            petId = o.optString("petId", petId),
                            timestampMs = parseTime(o.optString("recordedAt")),
                            accelX = 0f,
                            accelY = 0f,
                            accelZ = 0f,
                            gyroX = 0f,
                            gyroY = 0f,
                            gyroZ = 0f,
                            enclosureTempC = 0f,
                            accelEnergy = o.optDouble("accelEnergy", 0.0).toFloat(),
                            gyroEnergy = 0f,
                            jerk = o.optDouble("jerk", 0.0).toFloat(),
                            heartRateBpm = o.optInt("heartRateBpm", 0),
                            hrvRmssdMs = o.optDouble("hrvRmssdMs", 0.0).toFloat(),
                            signalQuality = o.optDouble("signalQuality", 0.0).toFloat(),
                            restingHeartRate = null,
                            artifactRejected = o.optBoolean("artifactRejected", false),
                            activity = parseActivity(o.optString("activity", "unknown")),
                            movementCount = 0,
                            layingMinutes = 0,
                            standingMinutes = 0,
                            sleepingMinutes = 0,
                            activeMinutes = 0
                        )
                    )
                }
            }
        }

    suspend fun alerts(petId: String): List<HealthAlert> = withContext(Dispatchers.IO) {
        val arr = JSONArray(get("/pets/$petId/alerts"))
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    HealthAlert(
                        id = o.getString("id"),
                        petId = o.getString("petId"),
                        reason = parseReason(o.getString("reason")),
                        severity = parseSeverity(o.getString("severity")),
                        description = o.getString("description"),
                        contextNote = o.optString("contextNote"),
                        timestampMs = parseTime(o.getString("createdAt")),
                        isAcknowledged = o.optBoolean("isAcknowledged", false)
                    )
                )
            }
        }
    }

    suspend fun acknowledge(alertId: String) = withContext(Dispatchers.IO) {
        post("/alerts/$alertId/acknowledge", "{}")
    }

    suspend fun acknowledgeAll(petId: String) = withContext(Dispatchers.IO) {
        post("/pets/$petId/alerts/acknowledge-all", "{}")
    }

    suspend fun deviceConnectivity(serial: String): DeviceConnectivity =
        withContext(Dispatchers.IO) {
            val o = JSONObject(get("/devices/$serial/connectivity"))
            DeviceConnectivity(
                serial = o.getString("serial"),
                online = o.optBoolean("online", false),
                mqttUp = o.optBoolean("mqttUp", false),
                cloud = o.optBoolean("cloud", false),
                lastSeen = if (o.isNull("lastSeen")) null else o.optString("lastSeen"),
                wifiRssi = if (o.isNull("wifiRssi")) null else o.optInt("wifiRssi"),
                firmware = if (o.isNull("firmware")) null else o.optString("firmware"),
                ageMs = if (o.isNull("ageMs")) null else o.optLong("ageMs"),
                graceMs = o.optLong("graceMs", 20_000),
                keepaliveHintSec = o.optInt("keepaliveHintSec", 15),
                mqttKeepaliveSec = o.optInt("mqttKeepaliveSec", 20)
            )
        }

    suspend fun baseline(petId: String): PetBaseline = withContext(Dispatchers.IO) {
        val o = JSONObject(get("/pets/$petId/baseline"))
        PetBaseline(
            petId = o.getString("petId"),
            restingHrAvg = o.optDouble("restingHrAvg", 80.0),
            restingHrStd = o.optDouble("restingHrStd", 12.0),
            sleepMinutesAvg = o.optDouble("sleepMinutesAvg", 420.0),
            hrvAvg = o.optDouble("hrvAvg", 30.0),
            samples = o.optInt("samples", 0),
            highRestingHrThreshold = o.optInt("highRestingHrThreshold", 130),
            lowHrvThreshold = o.optInt("lowHrvThreshold", 18)
        )
    }

    suspend fun refreshBaseline(petId: String): PetBaseline = withContext(Dispatchers.IO) {
        val o = JSONObject(post("/pets/$petId/baseline/refresh", "{}"))
        PetBaseline(
            petId = o.getString("petId"),
            restingHrAvg = o.optDouble("restingHrAvg", 80.0),
            restingHrStd = o.optDouble("restingHrStd", 12.0),
            sleepMinutesAvg = o.optDouble("sleepMinutesAvg", 420.0),
            hrvAvg = o.optDouble("hrvAvg", 30.0),
            samples = o.optInt("samples", 0),
            highRestingHrThreshold = o.optInt("highRestingHrThreshold", 130),
            lowHrvThreshold = o.optInt("lowHrvThreshold", 18)
        )
    }

    suspend fun journal(petId: String): List<DailySummary> = withContext(Dispatchers.IO) {
        val arr = JSONArray(get("/pets/$petId/journal"))
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    DailySummary(
                        dayStartMs = parseDay(o.getString("day")),
                        sleepMinutes = o.optInt("sleepMinutes"),
                        activeMinutes = o.optInt("activeMinutes"),
                        layingMinutes = o.optInt("layingMinutes"),
                        standingMinutes = o.optInt("standingMinutes"),
                        awakenings = o.optInt("awakenings"),
                        restlessMinutes = o.optInt("restlessMinutes"),
                        sleepScore = o.optInt("sleepScore"),
                        avgBpm = o.optDouble("avgBpm", 0.0).toFloat(),
                        avgRestingBpm = o.optDouble("avgRestingBpm", 0.0).toFloat(),
                        avgHrv = o.optDouble("avgHrv", 0.0).toFloat(),
                        movementCount = o.optInt("movementCount"),
                        alertCount = o.optInt("alertCount"),
                        healthScore = o.optInt("healthScore")
                    )
                )
            }
        }
    }

    private fun parseReading(o: JSONObject): SensorReading {
        return SensorReading(
            petId = o.getString("petId"),
            timestampMs = parseTime(o.getString("recordedAt")),
            accelX = o.optDouble("accelX", 0.0).toFloat(),
            accelY = o.optDouble("accelY", 0.0).toFloat(),
            accelZ = o.optDouble("accelZ", 0.0).toFloat(),
            gyroX = o.optDouble("gyroX", 0.0).toFloat(),
            gyroY = o.optDouble("gyroY", 0.0).toFloat(),
            gyroZ = o.optDouble("gyroZ", 0.0).toFloat(),
            enclosureTempC = o.optDouble("enclosureTempC", 0.0).toFloat(),
            accelEnergy = o.optDouble("accelEnergy", 0.0).toFloat(),
            gyroEnergy = o.optDouble("gyroEnergy", 0.0).toFloat(),
            jerk = o.optDouble("jerk", 0.0).toFloat(),
            heartRateBpm = o.optInt("heartRateBpm", 0),
            hrvRmssdMs = o.optDouble("hrvRmssdMs", 0.0).toFloat(),
            signalQuality = o.optDouble("signalQuality", 0.0).toFloat(),
            restingHeartRate = if (o.isNull("restingHeartRate")) null else o.optInt("restingHeartRate"),
            artifactRejected = o.optBoolean("artifactRejected", false),
            activity = parseActivity(o.optString("activity", "unknown")),
            movementCount = o.optInt("movementCount", 0),
            layingMinutes = o.optInt("layingMinutes", 0),
            standingMinutes = o.optInt("standingMinutes", 0),
            sleepingMinutes = o.optInt("sleepingMinutes", 0),
            activeMinutes = o.optInt("activeMinutes", 0)
        )
    }

    private fun parseScore(o: JSONObject): HealthScore {
        val reasonsJson = o.optJSONArray("reasons") ?: JSONArray()
        val reasons = buildList {
            for (i in 0 until reasonsJson.length()) add(reasonsJson.getString(i))
        }
        return HealthScore(
            score = o.getInt("score"),
            label = o.getString("label"),
            reasons = reasons,
            computedAtMs = parseTime(o.optString("computedAt"))
        )
    }

    private fun parseActivity(raw: String): ActivityClass = when (raw.lowercase(Locale.US)) {
        "sleeping" -> ActivityClass.SLEEPING
        "laying" -> ActivityClass.LAYING
        "standing" -> ActivityClass.STANDING
        "walking" -> ActivityClass.WALKING
        "playing" -> ActivityClass.PLAYING
        else -> ActivityClass.UNKNOWN
    }

    private fun parseSeverity(raw: String): AlertSeverity = when (raw.lowercase(Locale.US)) {
        "critical" -> AlertSeverity.CRITICAL
        "warning" -> AlertSeverity.WARNING
        else -> AlertSeverity.INFO
    }

    private fun parseReason(raw: String): AlertReason = when (raw.lowercase(Locale.US)) {
        "high_resting_hr" -> AlertReason.HIGH_RESTING_HR
        "low_hr" -> AlertReason.LOW_HR
        "low_hrv" -> AlertReason.LOW_HRV
        "fall_suspected" -> AlertReason.FALL_SUSPECTED
        "collar_off" -> AlertReason.COLLAR_OFF
        "over_exertion" -> AlertReason.OVER_EXERTION
        "low_signal_quality" -> AlertReason.LOW_SIGNAL_QUALITY
        "heat_stress_proxy" -> AlertReason.HEAT_STRESS_PROXY
        else -> AlertReason.LOW_SIGNAL_QUALITY
    }

    private fun parseTime(raw: String): Long {
        if (raw.isBlank()) return System.currentTimeMillis()
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            fmt.parse(raw)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            try {
                val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                fmt.parse(raw)?.time ?: System.currentTimeMillis()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        }
    }

    private fun parseDay(raw: String): Long {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            fmt.parse(raw.take(10))?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun get(path: String): String {
        val conn = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4000
            readTimeout = 4000
        }
        return read(conn)
    }

    private fun post(path: String, body: String): String {
        val conn = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 4000
            readTimeout = 4000
            setRequestProperty("Content-Type", "application/json")
            outputStream.use { it.write(body.toByteArray()) }
        }
        return read(conn)
    }

    private fun read(conn: HttpURLConnection): String {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code: $text")
        }
        return text
    }
}
