package com.telat.smartring.r99.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.telat.smartring.r99.data.ble.RingBleManager
import com.telat.smartring.r99.data.healthconnect.HealthConnectBridge
import com.telat.smartring.r99.data.model.BleConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.temporal.ChronoUnit

class RingSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "RingSyncWorker"
        const val WORK_NAME = "smart_ring_r99_periodic_sync"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Iniciando ciclo periódico de sincronización cada 30 min...")

        val healthBridge = HealthConnectBridge(applicationContext)
        if (!healthBridge.isAvailable || !healthBridge.hasAllPermissions()) {
            Log.w(TAG, "Health Connect no disponible o sin permisos otorgados")
            return Result.failure()
        }

        val workerScope = CoroutineScope(Dispatchers.IO + Job())
        val bleManager = RingBleManager(applicationContext, workerScope)

        var lastBpm: Int? = null
        var lastSpo2: Int? = null
        var lastSteps: Int? = null
        var lastCalories: Int? = null
        var lastDistance: Int? = null

        val hrJob = workerScope.launch {
            bleManager.heartRateFlow.collect { sample ->
                lastBpm = sample.bpm
            }
        }

        val spo2Job = workerScope.launch {
            bleManager.spo2Flow.collect { percent ->
                lastSpo2 = percent
            }
        }

        val stepsJob = workerScope.launch {
            bleManager.consolidatedStepsFlow.collect { sample ->
                lastSteps = sample.steps
                lastCalories = sample.calories
                lastDistance = sample.distanceMeters
            }
        }

        return try {
            bleManager.startScan()

            // Esperar conexión con un timeout máximo de 45 segundos
            val connected = withTimeoutOrNull(45_000L) {
                while (bleManager.connectionState.value != BleConnectionState.CONNECTED) {
                    delay(500L)
                }
                true
            } ?: false

            if (!connected) {
                Log.w(TAG, "Timeout esperando conexión BLE con Smart Ring R99")
                return Result.retry()
            }

            // Descargar todo el historial acumulado en el anillo (YCBT flash memory)
            val history = bleManager.syncAllHistory()
            Log.i(TAG, "Sincronización de historial finalizada: ${history.totalCount} registros totales")

            if (history.sportRecords.isNotEmpty()) {
                healthBridge.writeSportBatch(history.sportRecords)
            }

            for (session in history.sleepSessions) {
                healthBridge.writeSleepSession(session)
            }

            if (history.heartRateRecords.isNotEmpty()) {
                healthBridge.writeHeartRateBatch(history.heartRateRecords)
            }

            if (history.spo2Records.isNotEmpty()) {
                healthBridge.writeSpo2Batch(history.spo2Records)
            }

            // Guardar lecturas puntuales en tiempo real si no vinieron en lotes históricos
            val now = Instant.now()
            val thirtyMinutesAgo = now.minus(30, ChronoUnit.MINUTES)

            lastBpm?.let { bpm ->
                healthBridge.writeHeartRateSample(bpm, now)
            }

            lastSpo2?.let { spo2 ->
                healthBridge.writeOxygenSaturation(spo2, now)
            }

            lastSteps?.let { steps ->
                if (steps > 0 && history.sportRecords.isEmpty()) {
                    healthBridge.writeStepsWindow(steps.toLong(), thirtyMinutesAgo, now)
                }
            }

            lastCalories?.let { cal ->
                if (cal > 0 && history.sportRecords.isEmpty()) {
                    healthBridge.writeCalories(cal.toDouble(), thirtyMinutesAgo, now)
                }
            }

            lastDistance?.let { dist ->
                if (dist > 0 && history.sportRecords.isEmpty()) {
                    healthBridge.writeDistance(dist.toDouble(), thirtyMinutesAgo, now)
                }
            }

            Log.i(TAG, "Sincronización de 30 min completada con éxito. Desconectando BLE para preservar batería.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Excepción durante sincronización de fondo: ${e.message}", e)
            Result.retry()
        } finally {
            hrJob.cancel()
            spo2Job.cancel()
            stepsJob.cancel()
            bleManager.disconnect()
        }
    }
}
