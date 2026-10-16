package com.telat.smartring.r99.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.telat.smartring.r99.data.ble.RingBleManager
import com.telat.smartring.r99.data.healthconnect.HealthConnectBridge
import com.telat.smartring.r99.data.model.BleConnectionState
import kotlinx.coroutines.CompletableDeferred
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

        val syncFinished = CompletableDeferred<Boolean>()
        var lastBpm: Int? = null
        var lastSteps: Int? = null
        var lastCalories: Int? = null
        var lastDistance: Int? = null

        // Escuchar telemetría
        val hrJob = workerScope.launch {
            bleManager.heartRateFlow.collect { sample ->
                lastBpm = sample.bpm
            }
        }

        val stepsJob = workerScope.launch {
            bleManager.consolidatedStepsFlow.collect { sample ->
                lastSteps = sample.steps
                lastCalories = sample.calories
                lastDistance = sample.distanceMeters
                // Si ya tenemos el bloque consolidado y el pulso, podemos dar por terminada la ventana
                syncFinished.complete(true)
            }
        }

        return try {
            bleManager.startScan()

            // Esperar conexión y sincronización con un timeout máximo de 60 segundos
            withTimeoutOrNull(60_000L) {
                // Esperar a que se conecte
                while (bleManager.connectionState.value != BleConnectionState.CONNECTED) {
                    delay(500L)
                }

                // Enviar ping forzado
                bleManager.sendSyncPing()

                // Esperar respuesta o cumplir tiempo de lectura
                syncFinished.await()
            }

            // Escribir en Health Connect si se obtuvieron datos
            val now = Instant.now()
            val thirtyMinutesAgo = now.minus(30, ChronoUnit.MINUTES)

            lastBpm?.let { bpm ->
                healthBridge.writeHeartRateSample(bpm, now)
            }

            lastSteps?.let { steps ->
                if (steps > 0) {
                    healthBridge.writeStepsWindow(steps.toLong(), thirtyMinutesAgo, now)
                }
            }

            lastCalories?.let { cal ->
                if (cal > 0) {
                    healthBridge.writeCalories(cal.toDouble(), thirtyMinutesAgo, now)
                }
            }

            lastDistance?.let { dist ->
                if (dist > 0) {
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
            stepsJob.cancel()
            bleManager.disconnect()
        }
    }
}
