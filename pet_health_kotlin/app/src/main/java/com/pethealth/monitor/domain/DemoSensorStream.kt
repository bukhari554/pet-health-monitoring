package com.pethealth.monitor.domain

import com.pethealth.monitor.data.ActivityClass
import com.pethealth.monitor.data.SensorReading
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

class DemoSensorStream(private val petId: String) {
    private val random = Random(System.currentTimeMillis())
    private var movement = 40
    private var laying = 80
    private var standing = 45
    private var sleeping = 320
    private var active = 55
    private var tick = 0

    fun readings(intervalMs: Long = 2000L): Flow<SensorReading> = flow {
        while (true) {
            emit(next())
            delay(intervalMs)
        }
    }

    private fun next(): SensorReading {
        tick++
        val phase = tick % 40
        val activity = when {
            phase < 12 -> ActivityClass.SLEEPING
            phase < 18 -> ActivityClass.LAYING
            phase < 24 -> ActivityClass.STANDING
            phase < 32 -> ActivityClass.WALKING
            else -> ActivityClass.PLAYING
        }

        val forceHighRestingHr = tick % 55 == 20
        val forceFall = tick % 70 == 35
        val forceCollarOff = tick % 90 == 50

        // Build vectors first, then derive motion energy = |‖a‖ − g| (same as ESP).
        var ax = 0.2f + random.nextFloat() * 0.4f
        var ay = 0.1f + random.nextFloat() * 0.3f
        var az = 9.6f + (random.nextFloat() - 0.5f) * 0.4f
        var gx = (random.nextFloat() - 0.5f) * 0.3f
        val gy = (random.nextFloat() - 0.5f) * 0.3f
        val gz = (random.nextFloat() - 0.5f) * 0.3f
        var jerk = 0.5f + random.nextFloat()
        var gyroEnergy = 0.2f + random.nextFloat() * 0.4f

        when (activity) {
            ActivityClass.STANDING -> {
                ay = 9.6f + (random.nextFloat() - 0.5f) * 0.3f
                az = 0.4f + random.nextFloat() * 0.4f
                ax = 0.2f + random.nextFloat() * 0.3f
                gyroEnergy = 0.15f + random.nextFloat() * 0.2f
                standing++
            }
            ActivityClass.LAYING -> {
                az = 9.5f + (random.nextFloat() - 0.5f) * 0.3f
                ax = 0.3f + random.nextFloat() * 0.5f
                ay = 0.2f + random.nextFloat() * 0.3f
                gyroEnergy = 0.2f + random.nextFloat() * 0.25f
                laying++
            }
            ActivityClass.SLEEPING -> {
                az = 9.4f + (random.nextFloat() - 0.5f) * 0.15f
                ax = 0.15f + random.nextFloat() * 0.2f
                ay = 0.1f + random.nextFloat() * 0.2f
                gyroEnergy = 0.05f + random.nextFloat() * 0.1f
                sleeping++
            }
            ActivityClass.WALKING -> {
                ax = 2f + random.nextFloat() * 2f
                ay = 8f + random.nextFloat()
                az = 3f + random.nextFloat()
                gyroEnergy = 1.5f + random.nextFloat()
                jerk = 2f + random.nextFloat() * 2f
                movement += 2
                active++
            }
            ActivityClass.PLAYING -> {
                ax = 4f + random.nextFloat() * 4f
                ay = 6f + random.nextFloat() * 3f
                az = 5f + random.nextFloat() * 3f
                gyroEnergy = 5f + random.nextFloat() * 4f
                jerk = 6f + random.nextFloat() * 5f
                movement += 5
                active += 2
            }
            ActivityClass.UNKNOWN -> Unit
        }

        var accelEnergy = kotlin.math.abs(
            kotlin.math.sqrt(ax * ax + ay * ay + az * az) - 9.81f
        )

        if (forceFall) {
            jerk = 22f + random.nextFloat() * 5f
            ax = 20f
            ay = 5f
            az = 5f
            accelEnergy = kotlin.math.abs(
                kotlin.math.sqrt(ax * ax + ay * ay + az * az) - 9.81f
            )
            gx = 8f
        }

        var quality = 0.75f + random.nextFloat() * 0.2f
        var artifact = false
        if (activity == ActivityClass.PLAYING || activity == ActivityClass.WALKING) {
            if (random.nextFloat() < 0.25f) {
                artifact = true
                quality = 0.25f + random.nextFloat() * 0.2f
            }
        }
        if (forceCollarOff) {
            quality = 0.05f
            artifact = true
            accelEnergy = 0.15f
            gyroEnergy = 0.05f
        }

        val resting = activity == ActivityClass.SLEEPING || activity == ActivityClass.LAYING
        val bpm: Int
        val hrv: Float
        when {
            forceCollarOff || artifact -> {
                bpm = 0
                hrv = 0f
            }
            forceHighRestingHr && resting -> {
                bpm = 135 + random.nextInt(20)
                hrv = 12f + random.nextFloat() * 5f
            }
            resting -> {
                bpm = 70 + random.nextInt(25)
                hrv = 28f + random.nextFloat() * 25f
            }
            activity == ActivityClass.WALKING -> {
                bpm = 100 + random.nextInt(25)
                hrv = 20f + random.nextFloat() * 15f
            }
            else -> {
                bpm = 120 + random.nextInt(40)
                hrv = 15f + random.nextFloat() * 12f
            }
        }

        return SensorReading(
            petId = petId,
            timestampMs = System.currentTimeMillis(),
            accelX = ax,
            accelY = ay,
            accelZ = az,
            gyroX = gx,
            gyroY = gy,
            gyroZ = gz,
            enclosureTempC = 27f + random.nextFloat() * 6f + if (activity == ActivityClass.PLAYING) 2f else 0f,
            accelEnergy = accelEnergy,
            gyroEnergy = gyroEnergy,
            jerk = jerk,
            heartRateBpm = bpm,
            hrvRmssdMs = hrv,
            signalQuality = quality.coerceIn(0f, 1f),
            restingHeartRate = if (resting && bpm > 0) bpm else null,
            artifactRejected = artifact,
            activity = activity,
            movementCount = movement,
            layingMinutes = laying,
            standingMinutes = standing,
            sleepingMinutes = sleeping,
            activeMinutes = active
        )
    }
}
