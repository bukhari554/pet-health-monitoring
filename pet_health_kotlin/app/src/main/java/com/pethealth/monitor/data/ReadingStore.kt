package com.pethealth.monitor.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local rolling record of vitals so Home/Vitals stay filled across restarts. */
class ReadingStore(context: Context) {
    private val prefs = context.getSharedPreferences("pet_health_records", Context.MODE_PRIVATE)

    fun load(petId: String, limit: Int = 120): List<SensorReading> {
        val raw = prefs.getString(key(petId), null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    parse(arr.getJSONObject(i))?.let { add(it) }
                }
            }.sortedBy { it.timestampMs }.takeLast(limit)
        }.getOrDefault(emptyList())
    }

    fun save(petId: String, readings: List<SensorReading>) {
        val trimmed = readings.sortedBy { it.timestampMs }.takeLast(120)
        val arr = JSONArray()
        trimmed.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(key(petId), arr.toString()).apply()
    }

    fun append(petId: String, reading: SensorReading): List<SensorReading> {
        val merged = (load(petId) + reading)
            .distinctBy { it.timestampMs }
            .sortedBy { it.timestampMs }
            .takeLast(120)
        save(petId, merged)
        return merged
    }

    private fun key(petId: String) = "hist_$petId"

    private fun toJson(r: SensorReading): JSONObject = JSONObject().apply {
        put("petId", r.petId)
        put("ts", r.timestampMs)
        put("ax", r.accelX.toDouble())
        put("ay", r.accelY.toDouble())
        put("az", r.accelZ.toDouble())
        put("gx", r.gyroX.toDouble())
        put("gy", r.gyroY.toDouble())
        put("gz", r.gyroZ.toDouble())
        put("temp", r.enclosureTempC.toDouble())
        put("ae", r.accelEnergy.toDouble())
        put("ge", r.gyroEnergy.toDouble())
        put("jerk", r.jerk.toDouble())
        put("bpm", r.heartRateBpm)
        put("hrv", r.hrvRmssdMs.toDouble())
        put("q", r.signalQuality.toDouble())
        put("rest", r.restingHeartRate ?: JSONObject.NULL)
        put("art", r.artifactRejected)
        put("act", r.activity.name)
        put("move", r.movementCount)
        put("lay", r.layingMinutes)
        put("stand", r.standingMinutes)
        put("sleep", r.sleepingMinutes)
        put("active", r.activeMinutes)
    }

    private fun parse(o: JSONObject): SensorReading? = runCatching {
        SensorReading(
            petId = o.getString("petId"),
            timestampMs = o.getLong("ts"),
            accelX = o.getDouble("ax").toFloat(),
            accelY = o.getDouble("ay").toFloat(),
            accelZ = o.getDouble("az").toFloat(),
            gyroX = o.getDouble("gx").toFloat(),
            gyroY = o.getDouble("gy").toFloat(),
            gyroZ = o.getDouble("gz").toFloat(),
            enclosureTempC = o.getDouble("temp").toFloat(),
            accelEnergy = o.getDouble("ae").toFloat(),
            gyroEnergy = o.getDouble("ge").toFloat(),
            jerk = o.getDouble("jerk").toFloat(),
            heartRateBpm = o.getInt("bpm"),
            hrvRmssdMs = o.getDouble("hrv").toFloat(),
            signalQuality = o.getDouble("q").toFloat(),
            restingHeartRate = if (o.isNull("rest")) null else o.getInt("rest"),
            artifactRejected = o.optBoolean("art", false),
            activity = runCatching {
                ActivityClass.valueOf(o.getString("act"))
            }.getOrDefault(ActivityClass.UNKNOWN),
            movementCount = o.optInt("move", 0),
            layingMinutes = o.optInt("lay", 0),
            standingMinutes = o.optInt("stand", 0),
            sleepingMinutes = o.optInt("sleep", 0),
            activeMinutes = o.optInt("active", 0)
        )
    }.getOrNull()
}
