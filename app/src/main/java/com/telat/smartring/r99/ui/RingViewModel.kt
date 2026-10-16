package com.telat.smartring.r99.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.telat.smartring.r99.data.ble.RingBleManager
import com.telat.smartring.r99.data.export.CsvExporter
import com.telat.smartring.r99.data.export.CsvLogEntry
import com.telat.smartring.r99.data.healthconnect.HealthConnectBridge
import com.telat.smartring.r99.data.model.BleConnectionState
import com.telat.smartring.r99.data.model.RingDashboardUiState
import com.telat.smartring.r99.worker.RingSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.concurrent.TimeUnit

class RingViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "RingViewModel"
    }

    private val bleManager = RingBleManager(application, viewModelScope)
    val healthConnectBridge = HealthConnectBridge(application)
    private val csvExporter = CsvExporter(application)
    private val workManager = WorkManager.getInstance(application)

    private val _uiState = MutableStateFlow(RingDashboardUiState())
    val uiState: StateFlow<RingDashboardUiState> = _uiState.asStateFlow()

    private val sessionLogs = Collections.synchronizedList(mutableListOf<CsvLogEntry>())

    init {
        checkHealthConnectStatus()
        observeBleConnection()
        observeTelemetry()
        configurePeriodicSync(true)
    }

    fun checkHealthConnectStatus() {
        val available = healthConnectBridge.isAvailable
        viewModelScope.launch {
            val hasPerms = if (available) healthConnectBridge.hasAllPermissions() else false
            _uiState.update {
                it.copy(
                    isHealthConnectAvailable = available,
                    hasHealthPermissions = hasPerms,
                    statusMessage = if (hasPerms) "Health Connect listo y vinculado" else "Se requieren permisos de Health Connect"
                )
            }
        }
    }

    private fun observeBleConnection() {
        viewModelScope.launch {
            bleManager.connectionState.collect { state ->
                val statusText = when (state) {
                    BleConnectionState.DISCONNECTED -> "Desconectado"
                    BleConnectionState.SCANNING -> "Buscando Smart Ring R99..."
                    BleConnectionState.CONNECTING -> "Estableciendo conexión BLE..."
                    BleConnectionState.CONNECTED -> "Conectado al anillo"
                    BleConnectionState.SYNCING -> "Sincronizando telemetría..."
                    BleConnectionState.ERROR -> "Error en módulo BLE"
                }
                _uiState.update {
                    it.copy(
                        connectionState = state,
                        statusMessage = statusText
                    )
                }
            }
        }

        viewModelScope.launch {
            bleManager.connectedDeviceName.collect { name ->
                _uiState.update { it.copy(deviceName = name) }
            }
        }

        viewModelScope.launch {
            bleManager.connectedDeviceAddress.collect { addr ->
                _uiState.update { it.copy(deviceAddress = addr) }
            }
        }
    }

    private fun observeTelemetry() {
        // 1. Escuchar pulso cardíaco
        viewModelScope.launch {
            bleManager.heartRateFlow.collect { sample ->
                _uiState.update {
                    it.copy(
                        currentHeartRate = sample.bpm,
                        lastHeartRateTime = sample.timestamp
                    )
                }

                sessionLogs.add(
                    CsvLogEntry(
                        timestamp = sample.timestamp,
                        metricType = "HEART_RATE",
                        value1 = "${sample.bpm} BPM"
                    )
                )

                // Enviar inmediatamente a Health Connect si hay permisos
                if (_uiState.value.hasHealthPermissions) {
                    healthConnectBridge.writeHeartRateSample(sample.bpm, Instant.ofEpochMilli(sample.timestamp))
                }
            }
        }

        // 2. Escuchar bloques consolidados de pasos
        viewModelScope.launch {
            bleManager.consolidatedStepsFlow.collect { sample ->
                val now = System.currentTimeMillis()
                _uiState.update {
                    it.copy(
                        totalStepsToday = sample.steps,
                        totalCaloriesToday = sample.calories,
                        totalDistanceMetersToday = sample.distanceMeters,
                        lastSyncTimestamp = now,
                        statusMessage = "Telemetría sincronizada con éxito"
                    )
                }

                sessionLogs.add(
                    CsvLogEntry(
                        timestamp = now,
                        metricType = "STEPS_CONSOLIDATED",
                        value1 = "${sample.steps} steps",
                        value2 = "${sample.calories} kcal",
                        value3 = "${sample.distanceMeters} m"
                    )
                )

                if (_uiState.value.hasHealthPermissions) {
                    val instantNow = Instant.ofEpochMilli(now)
                    val windowStart = instantNow.minus(30, ChronoUnit.MINUTES)
                    healthConnectBridge.writeStepsWindow(sample.steps.toLong(), windowStart, instantNow)
                    healthConnectBridge.writeCalories(sample.calories.toDouble(), windowStart, instantNow)
                    healthConnectBridge.writeDistance(sample.distanceMeters.toDouble(), windowStart, instantNow)
                }
            }
        }

        // 3. Ticks instantáneos de zancada (actualización visual reactiva)
        viewModelScope.launch {
            bleManager.instantStepFlow.collect {
                _uiState.update {
                    it.copy(totalStepsToday = it.totalStepsToday + 1)
                }
            }
        }

        // 4. Estados de actividad y sueño
        viewModelScope.launch {
            bleManager.activityStateFlow.collect { act ->
                _uiState.update { it.copy(isMoving = act.isMoving) }
                sessionLogs.add(
                    CsvLogEntry(
                        timestamp = System.currentTimeMillis(),
                        metricType = "ACTIVITY_STATE",
                        value1 = if (act.isMoving) "MOVING" else "IDLE",
                        notes = act.rawState
                    )
                )
            }
        }

        viewModelScope.launch {
            bleManager.sleepStateFlow.collect { sleep ->
                val desc = if (sleep.eventId != null) "Evento de sueño #${sleep.eventId}" else "Anillo en uso (Wear OK)"
                _uiState.update { it.copy(sleepStatusText = desc) }
                sessionLogs.add(
                    CsvLogEntry(
                        timestamp = System.currentTimeMillis(),
                        metricType = "SLEEP_STATE",
                        value1 = desc
                    )
                )
            }
        }

        viewModelScope.launch {
            bleManager.rawLogsFlow.collect { raw ->
                Log.d(TAG, "RAW BLE LOG: $raw")
            }
        }
    }

    fun startScanningAndConnect() {
        bleManager.startScan()
    }

    fun disconnectRing() {
        bleManager.disconnect()
    }

    fun forceSyncNow() {
        if (_uiState.value.connectionState == BleConnectionState.CONNECTED) {
            _uiState.update { it.copy(statusMessage = "Enviando ping de sincronización forzada...") }
            bleManager.sendSyncPing()
        } else {
            _uiState.update { it.copy(statusMessage = "Conectando al Smart Ring para sincronizar...") }
            bleManager.startScan()
        }
    }

    fun togglePeriodicSync(enable: Boolean) {
        _uiState.update { it.copy(isPeriodicSyncEnabled = enable) }
        configurePeriodicSync(enable)
    }

    private fun configurePeriodicSync(enable: Boolean) {
        if (enable) {
            val syncRequest = PeriodicWorkRequestBuilder<RingSyncWorker>(30, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                RingSyncWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )
            Log.i(TAG, "Sincronización en segundo plano de 30 min programada")
        } else {
            workManager.cancelUniqueWork(RingSyncWorker.WORK_NAME)
            Log.i(TAG, "Sincronización periódica cancelada")
        }
    }

    fun exportSessionCsv(): String? {
        val result = csvExporter.exportSessionToDownloads(sessionLogs.toList())
        if (result != null) {
            _uiState.update { it.copy(statusMessage = "CSV guardado en $result") }
        } else {
            _uiState.update { it.copy(statusMessage = "Error al exportar CSV") }
        }
        return result
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.disconnect()
    }
}
