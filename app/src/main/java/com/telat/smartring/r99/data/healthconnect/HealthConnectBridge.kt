package com.telat.smartring.r99.data.healthconnect

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class HealthConnectBridge(
    private val context: Context
) {
    companion object {
        private const val TAG = "HealthConnectBridge"

        val REQUIRED_PERMISSIONS = setOf(
            HealthPermission.getWritePermission(StepsRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getWritePermission(DistanceRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
        )
    }

    val isAvailable: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    val healthConnectClient: HealthConnectClient? by lazy {
        if (isAvailable) HealthConnectClient.getOrCreate(context) else null
    }

    suspend fun hasAllPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(REQUIRED_PERMISSIONS)
    }

    /**
     * Escribe un registro de frecuencia cardíaca puntual en Health Connect.
     */
    suspend fun writeHeartRateSample(bpm: Int, timestamp: Instant = Instant.now()): Boolean {
        val client = healthConnectClient ?: return false
        val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(timestamp)

        return try {
            val sample = HeartRateRecord.Sample(
                time = timestamp,
                beatsPerMinute = bpm.toLong()
            )

            // Health Connect exige una ventana de tiempo (mínimo 1 segundo de inicio a fin)
            val startTime = timestamp.minus(1, ChronoUnit.SECONDS)
            val record = HeartRateRecord(
                startTime = startTime,
                startZoneOffset = zoneOffset,
                endTime = timestamp,
                endZoneOffset = zoneOffset,
                samples = listOf(sample),
                metadata = Metadata()
            )

            client.insertRecords(listOf(record))
            Log.d(TAG, "HeartRateRecord ($bpm BPM) insertado con éxito en Health Connect")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error insertando HeartRateRecord: ${e.message}")
            false
        }
    }

    /**
     * Escribe un bloque de pasos en una ventana de tiempo delimitada.
     */
    suspend fun writeStepsWindow(
        stepsCount: Long,
        startTime: Instant,
        endTime: Instant = Instant.now()
    ): Boolean {
        val client = healthConnectClient ?: return false
        if (stepsCount <= 0 || !endTime.isAfter(startTime)) return false

        val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(startTime)

        return try {
            val record = StepsRecord(
                count = stepsCount,
                startTime = startTime,
                startZoneOffset = zoneOffset,
                endTime = endTime,
                endZoneOffset = zoneOffset,
                metadata = Metadata()
            )

            client.insertRecords(listOf(record))
            Log.d(TAG, "StepsRecord ($stepsCount pasos) insertado con éxito en Health Connect")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error insertando StepsRecord: ${e.message}")
            false
        }
    }

    /**
     * Escribe la distancia recorrida en metros.
     */
    suspend fun writeDistance(
        distanceMeters: Double,
        startTime: Instant,
        endTime: Instant = Instant.now()
    ): Boolean {
        val client = healthConnectClient ?: return false
        if (distanceMeters <= 0.0 || !endTime.isAfter(startTime)) return false

        val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(startTime)

        return try {
            val record = DistanceRecord(
                distance = Length.meters(distanceMeters),
                startTime = startTime,
                startZoneOffset = zoneOffset,
                endTime = endTime,
                endZoneOffset = zoneOffset,
                metadata = Metadata()
            )

            client.insertRecords(listOf(record))
            Log.d(TAG, "DistanceRecord ($distanceMeters m) insertado con éxito en Health Connect")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error insertando DistanceRecord: ${e.message}")
            false
        }
    }

    /**
     * Escribe las calorías quemadas estimadas.
     */
    suspend fun writeCalories(
        caloriesKcal: Double,
        startTime: Instant,
        endTime: Instant = Instant.now()
    ): Boolean {
        val client = healthConnectClient ?: return false
        if (caloriesKcal <= 0.0 || !endTime.isAfter(startTime)) return false

        val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(startTime)

        return try {
            val record = TotalCaloriesBurnedRecord(
                energy = Energy.kilocalories(caloriesKcal),
                startTime = startTime,
                startZoneOffset = zoneOffset,
                endTime = endTime,
                endZoneOffset = zoneOffset,
                metadata = Metadata()
            )

            client.insertRecords(listOf(record))
            Log.d(TAG, "TotalCaloriesBurnedRecord ($caloriesKcal kcal) insertado con éxito en Health Connect")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error insertando TotalCaloriesBurnedRecord: ${e.message}")
            false
        }
    }
}
