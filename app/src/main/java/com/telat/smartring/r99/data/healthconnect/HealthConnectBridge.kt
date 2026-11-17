package com.telat.smartring.r99.data.healthconnect

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Pressure
import com.telat.smartring.r99.data.ycbt.YcbtBloodPressureRecord
import com.telat.smartring.r99.data.ycbt.YcbtHeartRateRecord
import com.telat.smartring.r99.data.ycbt.YcbtHrvRecord
import com.telat.smartring.r99.data.ycbt.YcbtRespiratoryRateRecord
import com.telat.smartring.r99.data.ycbt.YcbtSleepSession
import com.telat.smartring.r99.data.ycbt.YcbtSleepStage
import com.telat.smartring.r99.data.ycbt.YcbtSleepStageType
import com.telat.smartring.r99.data.ycbt.YcbtSpo2Record
import com.telat.smartring.r99.data.ycbt.YcbtSportRecord
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class HealthConnectBridge(
    private val context: Context
) {
    companion object {
        private const val TAG = "HealthConnectBridge"

        val WRITE_PERMISSIONS = setOf(
            HealthPermission.getWritePermission(StepsRecord::class),
            HealthPermission.getWritePermission(HeartRateRecord::class),
            HealthPermission.getWritePermission(DistanceRecord::class),
            HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getWritePermission(SleepSessionRecord::class),
            HealthPermission.getWritePermission(OxygenSaturationRecord::class),
            HealthPermission.getWritePermission(BloodPressureRecord::class),
            HealthPermission.getWritePermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getWritePermission(RespiratoryRateRecord::class)
        )

        val READ_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(BloodPressureRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(RespiratoryRateRecord::class)
        )

        val REQUIRED_PERMISSIONS = WRITE_PERMISSIONS + READ_PERMISSIONS
    }

    val isAvailable: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    val healthConnectClient: HealthConnectClient? by lazy {
        if (isAvailable) HealthConnectClient.getOrCreate(context) else null
    }

    suspend fun getGrantedPermissions(): Set<String> {
        val client = healthConnectClient ?: return emptySet()
        return try {
            client.permissionController.getGrantedPermissions()
        } catch (e: Exception) {
            Log.w(TAG, "Error consultando permisos concedidos: ${e.message}")
            emptySet()
        }
    }

    suspend fun hasWritePermissions(): Boolean {
        val granted = getGrantedPermissions()
        return granted.any { it.contains("WRITE") }
    }

    suspend fun hasAllPermissions(): Boolean {
        val granted = getGrantedPermissions()
        // En Android 14 consideramos listo el puente si los permisos de escritura requeridos están concedidos
        return granted.containsAll(WRITE_PERMISSIONS)
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

    /**
     * Escribe una sesión completa de sueño con sus etapas (Deep, Light, REM, Awake) en Health Connect.
     */
    suspend fun writeSleepSession(session: YcbtSleepSession): Boolean {
        val client = healthConnectClient ?: return false
        if (!session.endTime.isAfter(session.startTime)) return false

        val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(session.startTime)

        val stages = session.stages.mapNotNull { stage ->
            val stageEnd = stage.startTime.plusSeconds(stage.durationSeconds)
            if (stageEnd.isAfter(stage.startTime)) {
                val stageConstant = when (stage.type) {
                    YcbtSleepStageType.DEEP -> SleepSessionRecord.STAGE_TYPE_DEEP
                    YcbtSleepStageType.LIGHT -> SleepSessionRecord.STAGE_TYPE_LIGHT
                    YcbtSleepStageType.REM -> SleepSessionRecord.STAGE_TYPE_REM
                    YcbtSleepStageType.AWAKE -> SleepSessionRecord.STAGE_TYPE_AWAKE
                }
                SleepSessionRecord.Stage(
                    startTime = stage.startTime,
                    endTime = stageEnd,
                    stage = stageConstant
                )
            } else null
        }

        return try {
            val record = SleepSessionRecord(
                startTime = session.startTime,
                startZoneOffset = zoneOffset,
                endTime = session.endTime,
                endZoneOffset = zoneOffset,
                metadata = Metadata(),
                title = "Sueño Smart Ring R99",
                notes = "Sincronizado vía protocolo YCBT",
                stages = stages
            )

            client.insertRecords(listOf(record))
            Log.d(TAG, "SleepSessionRecord insertado con éxito en Health Connect (${stages.size} fases)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error insertando SleepSessionRecord: ${e.message}")
            false
        }
    }

    /**
     * Escribe una medición puntual de saturación de oxígeno (SpO2) en Health Connect.
     */
    suspend fun writeOxygenSaturation(percent: Int, timestamp: Instant = Instant.now()): Boolean {
        val client = healthConnectClient ?: return false
        if (percent !in 70..100) return false

        val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(timestamp)

        return try {
            val record = OxygenSaturationRecord(
                time = timestamp,
                zoneOffset = zoneOffset,
                percentage = Percentage(percent.toDouble()),
                metadata = Metadata()
            )

            client.insertRecords(listOf(record))
            Log.d(TAG, "OxygenSaturationRecord ($percent%) insertado con éxito en Health Connect")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error insertando OxygenSaturationRecord: ${e.message}")
            false
        }
    }

    /**
     * Escribe en lote las lecturas históricas de frecuencia cardíaca recuperadas de la memoria flash del anillo.
     */
    suspend fun writeHeartRateBatch(records: List<YcbtHeartRateRecord>): Int {
        val client = healthConnectClient ?: return 0
        if (records.isEmpty()) return 0

        val hcRecords = records.map { r ->
            val startTime = r.timestamp.minus(1, ChronoUnit.SECONDS)
            val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(r.timestamp)
            HeartRateRecord(
                startTime = startTime,
                startZoneOffset = zoneOffset,
                endTime = r.timestamp,
                endZoneOffset = zoneOffset,
                samples = listOf(
                    HeartRateRecord.Sample(
                        time = r.timestamp,
                        beatsPerMinute = r.bpm.toLong()
                    )
                ),
                metadata = Metadata()
            )
        }

        return try {
            client.insertRecords(hcRecords)
            Log.i(TAG, "Lote de ${hcRecords.size} registros de pulso insertados en Health Connect")
            hcRecords.size
        } catch (e: Exception) {
            Log.e(TAG, "Error insertando lote de pulso: ${e.message}")
            0
        }
    }

    /**
     * Escribe en lote los intervalos históricos de pasos, distancia y calorías recuperados del anillo.
     */
    suspend fun writeSportBatch(records: List<YcbtSportRecord>): Int {
        val client = healthConnectClient ?: return 0
        if (records.isEmpty()) return 0

        var insertedCount = 0
        for (r in records) {
            if (r.endTime.isAfter(r.startTime)) {
                val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(r.startTime)
                val items = mutableListOf<androidx.health.connect.client.records.Record>()

                if (r.steps > 0) {
                    items.add(
                        StepsRecord(
                            count = r.steps.toLong(),
                            startTime = r.startTime,
                            startZoneOffset = zoneOffset,
                            endTime = r.endTime,
                            endZoneOffset = zoneOffset,
                            metadata = Metadata()
                        )
                    )
                }
                if (r.distanceMeters > 0) {
                    items.add(
                        DistanceRecord(
                            distance = Length.meters(r.distanceMeters.toDouble()),
                            startTime = r.startTime,
                            startZoneOffset = zoneOffset,
                            endTime = r.endTime,
                            endZoneOffset = zoneOffset,
                            metadata = Metadata()
                        )
                    )
                }
                if (r.caloriesKcal > 0) {
                    items.add(
                        TotalCaloriesBurnedRecord(
                            energy = Energy.kilocalories(r.caloriesKcal.toDouble()),
                            startTime = r.startTime,
                            startZoneOffset = zoneOffset,
                            endTime = r.endTime,
                            endZoneOffset = zoneOffset,
                            metadata = Metadata()
                        )
                    )
                }

                if (items.isNotEmpty()) {
                    try {
                        client.insertRecords(items)
                        insertedCount++
                    } catch (e: Exception) {
                        Log.w(TAG, "Error insertando intervalo deportivo: ${e.message}")
                    }
                }
            }
        }
        return insertedCount
    }

    /**
     * Escribe en lote las lecturas de SpO2 recuperadas del anillo.
     */
    suspend fun writeSpo2Batch(records: List<YcbtSpo2Record>): Int {
        val client = healthConnectClient ?: return 0
        if (records.isEmpty()) return 0

        val hcRecords = records.filter { it.percent in 70..100 }.map { r ->
            val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(r.timestamp)
            OxygenSaturationRecord(
                time = r.timestamp,
                zoneOffset = zoneOffset,
                percentage = Percentage(r.percent.toDouble()),
                metadata = Metadata()
            )
        }

        return try {
            client.insertRecords(hcRecords)
            Log.i(TAG, "Lote de ${hcRecords.size} registros de SpO2 insertados en Health Connect")
            hcRecords.size
        } catch (e: Exception) {
            Log.e(TAG, "Error insertando lote de SpO2: ${e.message}")
            0
        }
    }

    /**
     * Lee el historial de frecuencia cardíaca de los últimos [days] días desde Health Connect.
     */
    suspend fun readHeartRateHistory(days: Long = 30): List<YcbtHeartRateRecord> {
        val client = healthConnectClient ?: return emptyList()
        val end = Instant.now()
        val start = end.minus(days, ChronoUnit.DAYS)
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            response.records.flatMap { rec ->
                rec.samples.mapNotNull { s ->
                    val bpm = s.beatsPerMinute.toInt()
                    if (bpm in 35..230) YcbtHeartRateRecord(s.time, bpm) else null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo leer historial HR desde Health Connect: ${e.message}")
            emptyList()
        }
    }

    /**
     * Lee el historial de SpO2 de los últimos [days] días desde Health Connect.
     */
    suspend fun readSpo2History(days: Long = 30): List<YcbtSpo2Record> {
        val client = healthConnectClient ?: return emptyList()
        val end = Instant.now()
        val start = end.minus(days, ChronoUnit.DAYS)
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = OxygenSaturationRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            response.records.mapNotNull { rec ->
                val pct = rec.percentage.value.toInt()
                if (pct in 70..100) YcbtSpo2Record(rec.time, pct) else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo leer historial SpO2 desde Health Connect: ${e.message}")
            emptyList()
        }
    }

    /**
     * Lee el historial de sueño de los últimos [days] días desde Health Connect.
     */
    suspend fun readSleepSessionsHistory(days: Long = 30): List<YcbtSleepSession> {
        val client = healthConnectClient ?: return emptyList()
        val end = Instant.now()
        val start = end.minus(days, ChronoUnit.DAYS)
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            response.records.mapNotNull { rec ->
                val stages = rec.stages.map { st ->
                    val type = when (st.stage) {
                        SleepSessionRecord.STAGE_TYPE_DEEP -> YcbtSleepStageType.DEEP
                        SleepSessionRecord.STAGE_TYPE_REM -> YcbtSleepStageType.REM
                        SleepSessionRecord.STAGE_TYPE_AWAKE,
                        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> YcbtSleepStageType.AWAKE
                        else -> YcbtSleepStageType.LIGHT
                    }
                    YcbtSleepStage(
                        startTime = st.startTime,
                        durationSeconds = Duration.between(st.startTime, st.endTime).seconds.coerceAtLeast(60L),
                        type = type
                    )
                }.ifEmpty {
                    val totSec = Duration.between(rec.startTime, rec.endTime).seconds.coerceAtLeast(60L)
                    listOf(YcbtSleepStage(rec.startTime, totSec, YcbtSleepStageType.LIGHT))
                }
                if (rec.endTime.isAfter(rec.startTime)) {
                    YcbtSleepSession(rec.startTime, rec.endTime, stages)
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo leer historial de sueño desde Health Connect: ${e.message}")
            emptyList()
        }
    }

    /**
     * Lee el historial de pasos de los últimos [days] días desde Health Connect.
     */
    suspend fun readStepsHistory(days: Long = 30): List<YcbtSportRecord> {
        val client = healthConnectClient ?: return emptyList()
        val end = Instant.now()
        val start = end.minus(days, ChronoUnit.DAYS)
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            response.records.mapNotNull { rec ->
                val count = rec.count.toInt()
                if (count > 0) {
                    YcbtSportRecord(
                        startTime = rec.startTime,
                        endTime = rec.endTime,
                        steps = count,
                        distanceMeters = (count * 0.72).toInt(),
                        caloriesKcal = (count * 0.04).toInt()
                    )
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo leer historial de pasos desde Health Connect: ${e.message}")
            emptyList()
        }
    }

    /**
     * Escribe una medición puntual de presión arterial en Health Connect.
     */
    suspend fun writeBloodPressure(
        systolicMmHg: Int,
        diastolicMmHg: Int,
        timestamp: Instant = Instant.now()
    ): Boolean {
        val client = healthConnectClient ?: return false
        if (systolicMmHg !in 60..240 || diastolicMmHg !in 35..150) return false
        val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(timestamp)
        return try {
            val record = BloodPressureRecord(
                time = timestamp,
                zoneOffset = zoneOffset,
                systolic = Pressure.millimetersOfMercury(systolicMmHg.toDouble()),
                diastolic = Pressure.millimetersOfMercury(diastolicMmHg.toDouble()),
                bodyPosition = BloodPressureRecord.BODY_POSITION_SITTING_DOWN,
                measurementLocation = BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_UPPER_ARM,
                metadata = Metadata()
            )
            client.insertRecords(listOf(record))
            Log.d(TAG, "BloodPressureRecord ($systolicMmHg/$diastolicMmHg mmHg) insertado en Health Connect")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error insertando BloodPressureRecord: ${e.message}")
            false
        }
    }

    /**
     * Escribe en lote lecturas históricas de presión arterial en Health Connect.
     */
    suspend fun writeBloodPressureBatch(records: List<YcbtBloodPressureRecord>): Int {
        val client = healthConnectClient ?: return 0
        val hcRecords = records.filter { it.systolic in 60..240 && it.diastolic in 35..150 }.map { r ->
            val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(r.timestamp)
            BloodPressureRecord(
                time = r.timestamp,
                zoneOffset = zoneOffset,
                systolic = Pressure.millimetersOfMercury(r.systolic.toDouble()),
                diastolic = Pressure.millimetersOfMercury(r.diastolic.toDouble()),
                bodyPosition = BloodPressureRecord.BODY_POSITION_SITTING_DOWN,
                measurementLocation = BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_UPPER_ARM,
                metadata = Metadata()
            )
        }
        if (hcRecords.isEmpty()) return 0
        return try {
            client.insertRecords(hcRecords)
            Log.i(TAG, "Lote de ${hcRecords.size} registros de presión arterial insertados en Health Connect")
            hcRecords.size
        } catch (e: Exception) {
            Log.e(TAG, "Error insertando lote de presión arterial: ${e.message}")
            0
        }
    }

    /**
     * Escribe una medición puntual de HRV (RMSSD en ms) en Health Connect.
     */
    suspend fun writeHrv(rmssdMs: Int, timestamp: Instant = Instant.now()): Boolean {
        val client = healthConnectClient ?: return false
        if (rmssdMs !in 1..500) return false
        val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(timestamp)
        return try {
            val record = HeartRateVariabilityRmssdRecord(
                time = timestamp,
                zoneOffset = zoneOffset,
                heartRateVariabilityMillis = rmssdMs.toDouble(),
                metadata = Metadata()
            )
            client.insertRecords(listOf(record))
            Log.d(TAG, "HeartRateVariabilityRmssdRecord ($rmssdMs ms) insertado en Health Connect")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error insertando HRV: ${e.message}")
            false
        }
    }

    /**
     * Escribe en lote lecturas históricas de HRV en Health Connect.
     */
    suspend fun writeHrvBatch(records: List<YcbtHrvRecord>): Int {
        val client = healthConnectClient ?: return 0
        val hcRecords = records.filter { it.hrvMs in 1..500 }.map { r ->
            val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(r.timestamp)
            HeartRateVariabilityRmssdRecord(
                time = r.timestamp,
                zoneOffset = zoneOffset,
                heartRateVariabilityMillis = r.hrvMs.toDouble(),
                metadata = Metadata()
            )
        }
        if (hcRecords.isEmpty()) return 0
        return try {
            client.insertRecords(hcRecords)
            Log.i(TAG, "Lote de ${hcRecords.size} registros de HRV insertados en Health Connect")
            hcRecords.size
        } catch (e: Exception) {
            Log.e(TAG, "Error insertando lote de HRV: ${e.message}")
            0
        }
    }

    /**
     * Escribe una medición de frecuencia respiratoria (rpm) en Health Connect.
     */
    suspend fun writeRespiratoryRate(rpm: Int, timestamp: Instant = Instant.now()): Boolean {
        val client = healthConnectClient ?: return false
        if (rpm !in 5..60) return false
        val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(timestamp)
        return try {
            val record = RespiratoryRateRecord(
                time = timestamp,
                zoneOffset = zoneOffset,
                rate = rpm.toDouble(),
                metadata = Metadata()
            )
            client.insertRecords(listOf(record))
            Log.d(TAG, "RespiratoryRateRecord ($rpm rpm) insertado en Health Connect")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error insertando frecuencia respiratoria: ${e.message}")
            false
        }
    }

    /**
     * Escribe en lote lecturas históricas de frecuencia respiratoria en Health Connect.
     */
    suspend fun writeRespiratoryRateBatch(records: List<YcbtRespiratoryRateRecord>): Int {
        val client = healthConnectClient ?: return 0
        val hcRecords = records.filter { it.rpm in 5..60 }.map { r ->
            val zoneOffset = ZoneOffset.systemDefault().rules.getOffset(r.timestamp)
            RespiratoryRateRecord(
                time = r.timestamp,
                zoneOffset = zoneOffset,
                rate = r.rpm.toDouble(),
                metadata = Metadata()
            )
        }
        if (hcRecords.isEmpty()) return 0
        return try {
            client.insertRecords(hcRecords)
            Log.i(TAG, "Lote de ${hcRecords.size} registros de frecuencia respiratoria insertados en Health Connect")
            hcRecords.size
        } catch (e: Exception) {
            Log.e(TAG, "Error insertando lote de frecuencia respiratoria: ${e.message}")
            0
        }
    }

    /**
     * Lee el historial de presión arterial de los últimos [days] días desde Health Connect.
     */
    suspend fun readBloodPressureHistory(days: Long = 30): List<YcbtBloodPressureRecord> {
        val client = healthConnectClient ?: return emptyList()
        val end = Instant.now()
        val start = end.minus(days, ChronoUnit.DAYS)
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = BloodPressureRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            response.records.mapNotNull { rec ->
                val sys = rec.systolic.inMillimetersOfMercury.toInt()
                val dia = rec.diastolic.inMillimetersOfMercury.toInt()
                if (sys in 60..240 && dia in 35..150) YcbtBloodPressureRecord(rec.time, sys, dia) else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo leer historial de presión arterial desde Health Connect: ${e.message}")
            emptyList()
        }
    }

    /**
     * Lee el historial de HRV de los últimos [days] días desde Health Connect.
     */
    suspend fun readHrvHistory(days: Long = 30): List<YcbtHrvRecord> {
        val client = healthConnectClient ?: return emptyList()
        val end = Instant.now()
        val start = end.minus(days, ChronoUnit.DAYS)
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateVariabilityRmssdRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            response.records.mapNotNull { rec ->
                val ms = rec.heartRateVariabilityMillis.toInt()
                if (ms in 1..500) YcbtHrvRecord(rec.time, ms) else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo leer historial HRV desde Health Connect: ${e.message}")
            emptyList()
        }
    }

    /**
     * Lee el historial de frecuencia respiratoria de los últimos [days] días desde Health Connect.
     */
    suspend fun readRespiratoryRateHistory(days: Long = 30): List<YcbtRespiratoryRateRecord> {
        val client = healthConnectClient ?: return emptyList()
        val end = Instant.now()
        val start = end.minus(days, ChronoUnit.DAYS)
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = RespiratoryRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            response.records.mapNotNull { rec ->
                val rpm = rec.rate.toInt()
                if (rpm in 5..60) YcbtRespiratoryRateRecord(rec.time, rpm) else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo leer historial respiratorio desde Health Connect: ${e.message}")
            emptyList()
        }
    }
}
