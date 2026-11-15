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
import com.telat.smartring.r99.data.ycbt.YcbtConstants
import com.telat.smartring.r99.data.ycbt.YcbtSleepStageType
import com.telat.smartring.r99.worker.RingSyncWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.Locale
import java.util.concurrent.TimeUnit

private data class SleepSummaryMetrics(
    val totalMinutes: Int,
    val deepMinutes: Int,
    val lightMinutes: Int,
    val remMinutes: Int,
    val awakeMinutes: Int,
    val dateRangeStr: String
)

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
            val granted = if (available) healthConnectBridge.getGrantedPermissions() else emptySet()
            val hasWritePerms = granted.any { it.contains("WRITE") }
            val hasAllWritePerms = granted.containsAll(HealthConnectBridge.WRITE_PERMISSIONS)
            _uiState.update {
                it.copy(
                    isHealthConnectAvailable = available,
                    hasHealthPermissions = hasWritePerms,
                    statusMessage = when {
                        hasAllWritePerms -> "Health Connect vinculado y activo"
                        hasWritePerms -> "Health Connect parcialmente concedido (${granted.size} permisos)"
                        else -> "Se requieren permisos en Health Connect"
                    }
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
                    BleConnectionState.CONNECTING -> "Estableciendo conexión BLE (YCBT)..."
                    BleConnectionState.CONNECTED -> "Conectado al anillo"
                    BleConnectionState.SYNCING -> "Descargando historial de salud..."
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
        // 1. Pulso cardíaco en tiempo real
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

                // Intentar siempre escribir en Health Connect si está disponible
                if (healthConnectBridge.isAvailable) {
                    healthConnectBridge.writeHeartRateSample(sample.bpm, Instant.ofEpochMilli(sample.timestamp))
                }
            }
        }

        // 2. Saturación de oxígeno (SpO2)
        viewModelScope.launch {
            bleManager.spo2Flow.collect { percent ->
                val now = System.currentTimeMillis()
                _uiState.update {
                    it.copy(
                        currentSpo2 = percent,
                        lastSpo2Time = now
                    )
                }

                sessionLogs.add(
                    CsvLogEntry(
                        timestamp = now,
                        metricType = "SPO2",
                        value1 = "$percent%"
                    )
                )

                if (healthConnectBridge.isAvailable) {
                    healthConnectBridge.writeOxygenSaturation(percent, Instant.ofEpochMilli(now))
                }
            }
        }

        // 3. Nivel de batería del anillo
        viewModelScope.launch {
            bleManager.batteryFlow.collect { (battery, isCharging) ->
                _uiState.update {
                    it.copy(
                        batteryPercent = battery,
                        isCharging = isCharging,
                        statusMessage = "Batería del anillo: $battery%${if (isCharging) " (Cargando ⚡)" else ""}"
                    )
                }
            }
        }

        // 3b. Presión arterial en vivo
        viewModelScope.launch {
            bleManager.bloodPressureFlow.collect { (sys, dia) ->
                val now = System.currentTimeMillis()
                _uiState.update {
                    it.copy(
                        currentBloodPressureSys = sys,
                        currentBloodPressureDia = dia,
                        lastBloodPressureTime = now,
                        isMeasuringBloodPressure = false,
                        isOffWristAlert = false,
                        statusMessage = "Presión capturada: $sys/$dia mmHg"
                    )
                }
                sessionLogs.add(
                    CsvLogEntry(
                        timestamp = now,
                        metricType = "BLOOD_PRESSURE",
                        value1 = "$sys/$dia mmHg"
                    )
                )
            }
        }

        // 3c. Temperatura dérmica en vivo
        viewModelScope.launch {
            bleManager.temperatureFlow.collect { celsius ->
                val now = System.currentTimeMillis()
                _uiState.update {
                    it.copy(
                        currentTemperatureCelsius = celsius,
                        lastTemperatureTime = now,
                        isMeasuringTemperature = false,
                        isOffWristAlert = false,
                        statusMessage = "Temperatura capturada: ${String.format(Locale.getDefault(), "%.1f", celsius)} °C"
                    )
                }
                sessionLogs.add(
                    CsvLogEntry(
                        timestamp = now,
                        metricType = "TEMPERATURE",
                        value1 = "$celsius °C"
                    )
                )
            }
        }

        // 3d. Estado de contacto con piel (Off-wrist)
        viewModelScope.launch {
            bleManager.sensorContactFlow.collect { hasContact ->
                _uiState.update {
                    it.copy(
                        isOffWristAlert = !hasContact,
                        statusMessage = if (!hasContact) "¡Aviso! Sensor sin contacto: coloca el anillo en tu dedo" else "Sensor en contacto firme"
                    )
                }
            }
        }

        // 4. Bloques de pasos en tiempo real
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

                if (healthConnectBridge.isAvailable) {
                    val instantNow = Instant.ofEpochMilli(now)
                    val windowStart = instantNow.minus(30, ChronoUnit.MINUTES)
                    healthConnectBridge.writeStepsWindow(sample.steps.toLong(), windowStart, instantNow)
                    healthConnectBridge.writeCalories(sample.calories.toDouble(), windowStart, instantNow)
                    healthConnectBridge.writeDistance(sample.distanceMeters.toDouble(), windowStart, instantNow)
                }
            }
        }

        // 5. Ticks instantáneos de zancada
        viewModelScope.launch {
            bleManager.instantStepFlow.collect {
                _uiState.update {
                    it.copy(totalStepsToday = it.totalStepsToday + 1)
                }
            }
        }

        // 6. Actividad y sueño
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
            viewModelScope.launch {
                _uiState.update { it.copy(statusMessage = "Descargando historial YCBT del anillo...") }
                val history = bleManager.syncAllHistory()

                var totalWritten = 0
                if (healthConnectBridge.isAvailable) {
                    if (history.sportRecords.isNotEmpty()) {
                        totalWritten += healthConnectBridge.writeSportBatch(history.sportRecords)
                    }
                    for (session in history.sleepSessions) {
                        if (healthConnectBridge.writeSleepSession(session)) totalWritten++
                    }
                    if (history.heartRateRecords.isNotEmpty()) {
                        totalWritten += healthConnectBridge.writeHeartRateBatch(history.heartRateRecords)
                    }
                    if (history.spo2Records.isNotEmpty()) {
                        totalWritten += healthConnectBridge.writeSpo2Batch(history.spo2Records)
                    }
                }

                val now = System.currentTimeMillis()
                val syncMsg = if (totalWritten > 0) {
                    "Sincronizados ${history.totalCount} registros del anillo ($totalWritten en Samsung Health)"
                } else if (history.totalCount > 0) {
                    "Recibidos ${history.totalCount} registros del anillo. Concede permisos en Health Connect para guardar"
                } else {
                    "Anillo al día (sin nuevos registros pendientes en memoria)"
                }

                // Cálculo y desglose de fases de sueño si hay sesiones descargadas
                val latestSession = history.sleepSessions.maxByOrNull { it.endTime }
                val sleepMetrics = if (latestSession != null) {
                    val totSec = latestSession.stages.sumOf { it.durationSeconds }
                    val d = (latestSession.stages.filter { it.type == YcbtSleepStageType.DEEP }.sumOf { it.durationSeconds } / 60).toInt()
                    val l = (latestSession.stages.filter { it.type == YcbtSleepStageType.LIGHT }.sumOf { it.durationSeconds } / 60).toInt()
                    val r = (latestSession.stages.filter { it.type == YcbtSleepStageType.REM }.sumOf { it.durationSeconds } / 60).toInt()
                    val a = (latestSession.stages.filter { it.type == YcbtSleepStageType.AWAKE }.sumOf { it.durationSeconds } / 60).toInt()
                    val t = (totSec / 60).toInt()
                    val formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault())
                    val dateFormatted = "${formatter.format(latestSession.startTime)} - ${formatter.format(latestSession.endTime)}"
                    SleepSummaryMetrics(t, d, l, r, a, dateFormatted)
                } else {
                    SleepSummaryMetrics(
                        _uiState.value.lastSleepTotalMinutes,
                        _uiState.value.lastSleepDeepMinutes,
                        _uiState.value.lastSleepLightMinutes,
                        _uiState.value.lastSleepRemMinutes,
                        _uiState.value.lastSleepAwakeMinutes,
                        _uiState.value.lastSleepDateStr
                    )
                }

                val sleepTextUpdated = if (sleepMetrics.totalMinutes > 0) {
                    "Último sueño: ${sleepMetrics.totalMinutes / 60}h ${sleepMetrics.totalMinutes % 60}m"
                } else {
                    _uiState.value.sleepStatusText
                }

                _uiState.update {
                    it.copy(
                        lastSyncTimestamp = now,
                        syncedSleepSessionsCount = it.syncedSleepSessionsCount + history.sleepSessions.size,
                        syncedHistoryRecordsCount = it.syncedHistoryRecordsCount + history.totalCount,
                        lastSleepTotalMinutes = sleepMetrics.totalMinutes,
                        lastSleepDeepMinutes = sleepMetrics.deepMinutes,
                        lastSleepLightMinutes = sleepMetrics.lightMinutes,
                        lastSleepRemMinutes = sleepMetrics.remMinutes,
                        lastSleepAwakeMinutes = sleepMetrics.awakeMinutes,
                        lastSleepDateStr = sleepMetrics.dateRangeStr,
                        sleepStatusText = sleepTextUpdated,
                        statusMessage = syncMsg
                    )
                }
            }
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

    fun refreshBattery() {
        if (_uiState.value.connectionState == BleConnectionState.CONNECTED) {
            _uiState.update { it.copy(statusMessage = "Consultando batería del anillo...") }
            bleManager.requestBatteryInfo()
        }
    }

    fun triggerHeartRateMeasurement() {
        if (_uiState.value.connectionState != BleConnectionState.CONNECTED) {
            _uiState.update { it.copy(statusMessage = "Conecta el anillo antes de medir el pulso") }
            return
        }
        if (_uiState.value.isMeasuringHeartRate) return

        viewModelScope.launch {
            val initialBpm = _uiState.value.currentHeartRate
            _uiState.update {
                it.copy(
                    isMeasuringHeartRate = true,
                    isOffWristAlert = false,
                    statusMessage = "Midiendo pulso cardíaco en tiempo real... (Sensor PPG activo)"
                )
            }
            bleManager.startSpotMeasurement(YcbtConstants.MEASURE_MODE_HEART_RATE)
            
            var receivedRealSample = false
            for (i in 1..20) {
                delay(1000)
                if (_uiState.value.currentHeartRate != initialBpm && _uiState.value.currentHeartRate > 0) {
                    receivedRealSample = true
                    break
                }
            }
            bleManager.stopSpotMeasurement(YcbtConstants.MEASURE_MODE_HEART_RATE)
            _uiState.update {
                it.copy(
                    isMeasuringHeartRate = false,
                    statusMessage = if (receivedRealSample) {
                        "¡Pulso real detectado: ${it.currentHeartRate} BPM!"
                    } else if (it.currentHeartRate > 0) {
                        "Pulso confirmado: ${it.currentHeartRate} BPM"
                    } else {
                        "Sin lectura: coloca el anillo firmemente en tu dedo e inténtalo de nuevo"
                    }
                )
            }
        }
    }

    fun triggerSpo2Measurement() {
        if (_uiState.value.connectionState != BleConnectionState.CONNECTED) {
            _uiState.update { it.copy(statusMessage = "Conecta el anillo antes de medir oxígeno") }
            return
        }
        if (_uiState.value.isMeasuringSpo2) return

        viewModelScope.launch {
            val initialSpo2 = _uiState.value.currentSpo2
            _uiState.update {
                it.copy(
                    isMeasuringSpo2 = true,
                    isOffWristAlert = false,
                    statusMessage = "Midiendo SpO2 (Sensor óptico infrarrojo)... Mantén la mano en reposo"
                )
            }
            bleManager.startSpotMeasurement(YcbtConstants.MEASURE_MODE_SPO2)

            var receivedRealSample = false
            for (i in 1..25) {
                delay(1000)
                if (_uiState.value.currentSpo2 != initialSpo2 && (_uiState.value.currentSpo2 ?: 0) > 0) {
                    receivedRealSample = true
                    break
                }
            }
            bleManager.stopSpotMeasurement(YcbtConstants.MEASURE_MODE_SPO2)
            _uiState.update {
                it.copy(
                    isMeasuringSpo2 = false,
                    statusMessage = if (receivedRealSample || (it.currentSpo2 ?: 0) > 0) {
                        "Oxígeno SpO2 registrado: ${it.currentSpo2}%"
                    } else {
                        "Sin lectura de SpO2: asegúrate de no mover el dedo"
                    }
                )
            }
        }
    }

    fun triggerBloodPressureMeasurement() {
        if (_uiState.value.connectionState != BleConnectionState.CONNECTED) {
            _uiState.update { it.copy(statusMessage = "Conecta el anillo antes de medir presión") }
            return
        }
        if (_uiState.value.isMeasuringBloodPressure) return

        viewModelScope.launch {
            val initialSys = _uiState.value.currentBloodPressureSys
            _uiState.update {
                it.copy(
                    isMeasuringBloodPressure = true,
                    isOffWristAlert = false,
                    statusMessage = "Estimando presión arterial... Mantén la mano quieta"
                )
            }
            bleManager.startSpotMeasurement(0x01.toByte())

            var receivedRealSample = false
            for (i in 1..25) {
                delay(1000)
                if (_uiState.value.currentBloodPressureSys != initialSys && (_uiState.value.currentBloodPressureSys ?: 0) > 0) {
                    receivedRealSample = true
                    break
                }
            }
            bleManager.stopSpotMeasurement(0x01.toByte())
            _uiState.update {
                it.copy(
                    isMeasuringBloodPressure = false,
                    statusMessage = if (receivedRealSample || (it.currentBloodPressureSys ?: 0) > 0) {
                        "Presión arterial estimada: ${it.currentBloodPressureSys}/${it.currentBloodPressureDia} mmHg"
                    } else {
                        "Sin lectura: coloca el anillo con el sensor hacia la palma del dedo"
                    }
                )
            }
        }
    }

    fun triggerTemperatureMeasurement() {
        if (_uiState.value.connectionState != BleConnectionState.CONNECTED) {
            _uiState.update { it.copy(statusMessage = "Conecta el anillo antes de medir temperatura") }
            return
        }
        if (_uiState.value.isMeasuringTemperature) return

        viewModelScope.launch {
            val initialTemp = _uiState.value.currentTemperatureCelsius
            _uiState.update {
                it.copy(
                    isMeasuringTemperature = true,
                    isOffWristAlert = false,
                    statusMessage = "Iniciando sensor térmico y leyendo temperatura dérmica..."
                )
            }
            // Disparar comando real según protocolo SmartHealth (0x03 0x18 + polling 0x02 0x0E)
            bleManager.startTemperatureMeasurement()

            var receivedRealSample = false
            for (i in 1..20) {
                delay(1000)
                if (_uiState.value.currentTemperatureCelsius != initialTemp && (_uiState.value.currentTemperatureCelsius ?: 0f) > 0f) {
                    receivedRealSample = true
                    break
                }
            }
            bleManager.stopTemperatureMeasurement()
            _uiState.update {
                it.copy(
                    isMeasuringTemperature = false,
                    statusMessage = if (receivedRealSample || (it.currentTemperatureCelsius ?: 0f) > 0f) {
                        "Temperatura capturada: ${String.format(Locale.getDefault(), "%.1f", it.currentTemperatureCelsius)} °C"
                    } else {
                        "Sin lectura térmica: coloca el sensor del anillo en contacto firme con la piel"
                    }
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.disconnect()
    }
}
