package com.telat.smartring.r99.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.telat.smartring.r99.data.ble.RingBleManager
import com.telat.smartring.r99.data.db.RingHistoryDatabase
import com.telat.smartring.r99.data.export.CsvExporter
import com.telat.smartring.r99.data.export.CsvLogEntry
import com.telat.smartring.r99.data.healthconnect.HealthConnectBridge
import com.telat.smartring.r99.data.model.BleConnectionState
import com.telat.smartring.r99.data.model.MetricHistoryItem
import com.telat.smartring.r99.data.model.RingDashboardUiState
import com.telat.smartring.r99.data.model.SleepStageUiItem
import com.telat.smartring.r99.data.ycbt.YcbtCommands
import com.telat.smartring.r99.data.ycbt.YcbtConstants
import com.telat.smartring.r99.data.ycbt.YcbtHistorySyncResult
import com.telat.smartring.r99.data.ycbt.YcbtSleepSession
import com.telat.smartring.r99.data.ycbt.YcbtSleepStageType
import com.telat.smartring.r99.worker.RingSyncWorker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class RingViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "RingViewModel"
        private const val PREFS_NAME = "r99_telemetry_cache"
        private const val KEY_CACHE_JSON = "cached_dashboard_v2"
        private const val KEY_PROFILE_JSON = "user_profile_prefs_v1"
        private const val KEY_MIGRATED_TO_SQLITE = "migrated_to_sqlite_v1"
        private const val HISTORY_WINDOW_DAYS = 35L
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val historyDb = RingHistoryDatabase(application)
    private val bleManager = RingBleManager(application, viewModelScope)
    val healthConnectBridge = HealthConnectBridge(application)
    private val csvExporter = CsvExporter(application)
    private val workManager = WorkManager.getInstance(application)

    private val _uiState = MutableStateFlow(RingDashboardUiState())
    val uiState: StateFlow<RingDashboardUiState> = _uiState.asStateFlow()

    private val sessionLogs = Collections.synchronizedList(mutableListOf<CsvLogEntry>())
    private var spotMeasurementJob: Job? = null

    private val timeFormatter = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    init {
        restoreProfileAndLegacyCache()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                migrateLegacyCacheToDbIfNeeded()
            }
            refreshHistoriesFromDatabase()
            importHealthConnectHistory()
        }
        checkHealthConnectStatus()
        observeBleConnection()
        observeTelemetry()
        configurePeriodicSync(true)
    }

    private fun restoreProfileAndLegacyCache() {
        prefs.getString(KEY_PROFILE_JSON, null)?.let { rawProf ->
            try {
                val p = JSONObject(rawProf)
                _uiState.update {
                    it.copy(
                        userHeightCm = p.optInt("height", 175),
                        userWeightKg = p.optInt("weight", 75),
                        userAge = p.optInt("age", 35),
                        userSex = p.optInt("sex", 0),
                        stepGoal = p.optInt("stepGoal", 8000),
                        monitoringIntervalMinutes = p.optInt("monitorMin", 30)
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error restaurando perfil local: ${e.message}")
            }
        }

        prefs.getString(KEY_CACHE_JSON, null)?.let { raw ->
            try {
                val json = JSONObject(raw)
                _uiState.update { state ->
                    state.copy(
                        currentHeartRate = json.optInt("hr", 0),
                        lastHeartRateTime = json.optLong("hrTime", 0L).takeIf { it > 0L },
                        currentSpo2 = json.optInt("spo2", 0).takeIf { it in 70..100 },
                        lastSpo2Time = json.optLong("spo2Time", 0L).takeIf { it > 0L },
                        currentBloodPressureSys = json.optInt("sbp", 0).takeIf { it > 0 },
                        currentBloodPressureDia = json.optInt("dbp", 0).takeIf { it > 0 },
                        lastBloodPressureTime = json.optLong("bpTime", 0L).takeIf { it > 0L },
                        currentHrvMs = json.optInt("hrv", 0).takeIf { it > 0 },
                        lastHrvTime = json.optLong("hrvTime", 0L).takeIf { it > 0L },
                        currentRespiratoryRateRpm = json.optInt("resp", 0).takeIf { it > 0 },
                        lastRespiratoryRateTime = json.optLong("respTime", 0L).takeIf { it > 0L },
                        totalStepsToday = json.optInt("steps", 0),
                        totalCaloriesToday = json.optInt("cal", 0),
                        totalDistanceMetersToday = json.optInt("dist", 0),
                        lastSyncTimestamp = json.optLong("lastSync", 0L).takeIf { it > 0L }
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo restaurar caché legacy: ${e.message}")
            }
        }
    }

    private fun saveProfilePrefs() {
        val s = _uiState.value
        try {
            val json = JSONObject().apply {
                put("height", s.userHeightCm)
                put("weight", s.userWeightKg)
                put("age", s.userAge)
                put("sex", s.userSex)
                put("stepGoal", s.stepGoal)
                put("monitorMin", s.monitoringIntervalMinutes)
            }
            prefs.edit().putString(KEY_PROFILE_JSON, json.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Error guardando perfil: ${e.message}")
        }
    }

    private fun saveSnapshotPrefs() {
        val s = _uiState.value
        try {
            val json = JSONObject().apply {
                put("hr", s.currentHeartRate)
                put("hrTime", s.lastHeartRateTime ?: 0L)
                put("spo2", s.currentSpo2 ?: 0)
                put("spo2Time", s.lastSpo2Time ?: 0L)
                put("sbp", s.currentBloodPressureSys ?: 0)
                put("dbp", s.currentBloodPressureDia ?: 0)
                put("bpTime", s.lastBloodPressureTime ?: 0L)
                put("hrv", s.currentHrvMs ?: 0)
                put("hrvTime", s.lastHrvTime ?: 0L)
                put("resp", s.currentRespiratoryRateRpm ?: 0)
                put("respTime", s.lastRespiratoryRateTime ?: 0L)
                put("steps", s.totalStepsToday)
                put("cal", s.totalCaloriesToday)
                put("dist", s.totalDistanceMetersToday)
                put("lastSync", s.lastSyncTimestamp ?: 0L)
            }
            prefs.edit().putString(KEY_CACHE_JSON, json.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Error guardando snapshot: ${e.message}")
        }
    }

    private fun migrateLegacyCacheToDbIfNeeded() {
        if (prefs.getBoolean(KEY_MIGRATED_TO_SQLITE, false)) return
        val raw = prefs.getString(KEY_CACHE_JSON, null)
        if (raw != null) {
            try {
                val json = JSONObject(raw)
                historyDb.insertVitalBatch(RingHistoryDatabase.METRIC_HEART_RATE, parseLegacyHistoryArray(json.optJSONArray("hrHist")), "CACHE")
                historyDb.insertVitalBatch(RingHistoryDatabase.METRIC_SPO2, parseLegacyHistoryArray(json.optJSONArray("spo2Hist")), "CACHE")
                historyDb.insertVitalBatch(RingHistoryDatabase.METRIC_BLOOD_PRESSURE, parseLegacyHistoryArray(json.optJSONArray("bpHist")), "CACHE")
                historyDb.insertVitalBatch(RingHistoryDatabase.METRIC_HRV, parseLegacyHistoryArray(json.optJSONArray("hrvHist")), "CACHE")
                historyDb.insertVitalBatch(RingHistoryDatabase.METRIC_RESPIRATORY_RATE, parseLegacyHistoryArray(json.optJSONArray("respHist")), "CACHE")
            } catch (e: Exception) {
                Log.w(TAG, "Error migrando caché legacy a SQLite: ${e.message}")
            }
        }
        prefs.edit().putBoolean(KEY_MIGRATED_TO_SQLITE, true).apply()
    }

    private fun parseLegacyHistoryArray(arr: JSONArray?): List<MetricHistoryItem> {
        if (arr == null) return emptyList()
        val list = mutableListOf<MetricHistoryItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val ts = o.optLong("ts", 0L)
            if (ts <= 0L) continue
            list.add(
                MetricHistoryItem(
                    timestampMillis = ts,
                    formattedTime = o.optString("ft", ""),
                    primaryValue = o.optDouble("v1", 0.0).toFloat(),
                    secondaryValue = if (o.has("v2")) o.optDouble("v2").toFloat() else null,
                    displayValue = o.optString("disp", ""),
                    unit = o.optString("unit", ""),
                    statusLabel = o.optString("status", "Normal"),
                    dateKey = RingHistoryDatabase.toDateKey(ts)
                )
            )
        }
        return list
    }

    private suspend fun refreshHistoriesFromDatabase() {
        val sinceMs = Instant.now().minus(HISTORY_WINDOW_DAYS, ChronoUnit.DAYS).toEpochMilli()
        val snapshot = withContext(Dispatchers.IO) {
            val hr = historyDb.getVitalRecordsSince(RingHistoryDatabase.METRIC_HEART_RATE, sinceMs)
            val spo2 = historyDb.getVitalRecordsSince(RingHistoryDatabase.METRIC_SPO2, sinceMs)
            val bp = historyDb.getVitalRecordsSince(RingHistoryDatabase.METRIC_BLOOD_PRESSURE, sinceMs)
            val hrv = historyDb.getVitalRecordsSince(RingHistoryDatabase.METRIC_HRV, sinceMs)
            val resp = historyDb.getVitalRecordsSince(RingHistoryDatabase.METRIC_RESPIRATORY_RATE, sinceMs)
            val activity = historyDb.getDailyActivityLastDays(35)
            val sleep = historyDb.getSleepSessionsLastDays(35)
            val count = historyDb.getTotalRecordsCount()
            DbLoadedBundle(hr, spo2, bp, hrv, resp, activity, sleep, count)
        }

        val todayKey = RingHistoryDatabase.toDateKey(System.currentTimeMillis())
        val todayActivity = snapshot.activity.firstOrNull { it.dateKey == todayKey }
        val latestSleep = snapshot.sleep.firstOrNull()
        val latestHr = snapshot.hr.firstOrNull()
        val latestSpo2 = snapshot.spo2.firstOrNull()
        val latestBp = snapshot.bp.firstOrNull()
        val latestHrv = snapshot.hrv.firstOrNull()
        val latestResp = snapshot.resp.firstOrNull()

        _uiState.update { s ->
            s.copy(
                currentHeartRate = if (s.currentHeartRate > 0) s.currentHeartRate else (latestHr?.primaryValue?.toInt() ?: 0),
                lastHeartRateTime = s.lastHeartRateTime ?: latestHr?.timestampMillis,
                currentSpo2 = s.currentSpo2 ?: latestSpo2?.primaryValue?.toInt(),
                lastSpo2Time = s.lastSpo2Time ?: latestSpo2?.timestampMillis,
                currentBloodPressureSys = s.currentBloodPressureSys ?: latestBp?.primaryValue?.toInt(),
                currentBloodPressureDia = s.currentBloodPressureDia ?: latestBp?.secondaryValue?.toInt(),
                lastBloodPressureTime = s.lastBloodPressureTime ?: latestBp?.timestampMillis,
                currentHrvMs = s.currentHrvMs ?: latestHrv?.primaryValue?.toInt(),
                lastHrvTime = s.lastHrvTime ?: latestHrv?.timestampMillis,
                currentRespiratoryRateRpm = s.currentRespiratoryRateRpm ?: latestResp?.primaryValue?.toInt(),
                lastRespiratoryRateTime = s.lastRespiratoryRateTime ?: latestResp?.timestampMillis,
                totalStepsToday = maxOf(s.totalStepsToday, todayActivity?.steps ?: 0),
                totalCaloriesToday = if (s.totalCaloriesToday > 0) s.totalCaloriesToday else (todayActivity?.calories ?: 0),
                totalDistanceMetersToday = if (s.totalDistanceMetersToday > 0) s.totalDistanceMetersToday else (todayActivity?.distanceMeters ?: 0),
                lastSleepTotalMinutes = latestSleep?.totalMinutes ?: s.lastSleepTotalMinutes,
                lastSleepDeepMinutes = latestSleep?.deepMinutes ?: s.lastSleepDeepMinutes,
                lastSleepLightMinutes = latestSleep?.lightMinutes ?: s.lastSleepLightMinutes,
                lastSleepRemMinutes = latestSleep?.remMinutes ?: s.lastSleepRemMinutes,
                lastSleepAwakeMinutes = latestSleep?.awakeMinutes ?: s.lastSleepAwakeMinutes,
                lastSleepDateStr = latestSleep?.dateRangeStr ?: s.lastSleepDateStr,
                sleepStagesHistory = latestSleep?.stages ?: s.sleepStagesHistory,
                sleepStatusText = if (latestSleep != null && latestSleep.totalMinutes > 0) {
                    "Último sueño: ${latestSleep.totalMinutes / 60}h ${latestSleep.totalMinutes % 60}m"
                } else s.sleepStatusText,
                syncedSleepSessionsCount = maxOf(s.syncedSleepSessionsCount, snapshot.sleep.size),
                syncedHistoryRecordsCount = snapshot.totalCount,
                heartRateHistory = snapshot.hr,
                spo2History = snapshot.spo2,
                bloodPressureHistory = snapshot.bp,
                hrvHistory = snapshot.hrv,
                respiratoryRateHistory = snapshot.resp,
                dailyActivityHistory = snapshot.activity,
                sleepSessionsHistory = snapshot.sleep
            )
        }
    }

    private data class DbLoadedBundle(
        val hr: List<MetricHistoryItem>,
        val spo2: List<MetricHistoryItem>,
        val bp: List<MetricHistoryItem>,
        val hrv: List<MetricHistoryItem>,
        val resp: List<MetricHistoryItem>,
        val activity: List<com.telat.smartring.r99.data.model.DailyActivitySummary>,
        val sleep: List<com.telat.smartring.r99.data.model.SleepSessionSummaryRecord>,
        val totalCount: Int
    )

    private suspend fun importHealthConnectHistory() {
        if (!healthConnectBridge.isAvailable) return
        val granted = healthConnectBridge.getGrantedPermissions()
        if (granted.none { it.contains("READ") }) return

        val hr = healthConnectBridge.readHeartRateHistory(30)
        val spo2 = healthConnectBridge.readSpo2History(30)
        val sleep = healthConnectBridge.readSleepSessionsHistory(30)
        val sport = healthConnectBridge.readStepsHistory(30)
        val bp = healthConnectBridge.readBloodPressureHistory(30)
        val hrv = healthConnectBridge.readHrvHistory(30)
        val resp = healthConnectBridge.readRespiratoryRateHistory(30)

        withContext(Dispatchers.IO) {
            val hrItems = hr.map { rec ->
                val ms = rec.timestamp.toEpochMilli()
                MetricHistoryItem(ms, timeFormatter.format(Date(ms)), rec.bpm.toFloat(), displayValue = "${rec.bpm}", unit = "BPM", statusLabel = classifyHeartRate(rec.bpm), dateKey = RingHistoryDatabase.toDateKey(ms))
            }
            val spo2Items = spo2.map { rec ->
                val ms = rec.timestamp.toEpochMilli()
                MetricHistoryItem(ms, timeFormatter.format(Date(ms)), rec.percent.toFloat(), displayValue = "${rec.percent}%", unit = "%", statusLabel = classifySpo2(rec.percent), dateKey = RingHistoryDatabase.toDateKey(ms))
            }
            val bpItems = bp.map { rec ->
                val ms = rec.timestamp.toEpochMilli()
                MetricHistoryItem(ms, timeFormatter.format(Date(ms)), rec.systolic.toFloat(), rec.diastolic.toFloat(), "${rec.systolic}/${rec.diastolic}", "mmHg", classifyBloodPressure(rec.systolic, rec.diastolic), dateKey = RingHistoryDatabase.toDateKey(ms))
            }
            val hrvItems = hrv.map { rec ->
                val ms = rec.timestamp.toEpochMilli()
                MetricHistoryItem(ms, timeFormatter.format(Date(ms)), rec.hrvMs.toFloat(), displayValue = "${rec.hrvMs}", unit = "ms", statusLabel = if (rec.hrvMs >= 30) "Óptimo" else "Fatiga / Estrés", dateKey = RingHistoryDatabase.toDateKey(ms))
            }
            val respItems = resp.map { rec ->
                val ms = rec.timestamp.toEpochMilli()
                MetricHistoryItem(ms, timeFormatter.format(Date(ms)), rec.rpm.toFloat(), displayValue = "${rec.rpm}", unit = "rpm", statusLabel = if (rec.rpm in 12..20) "Normal" else "Atípica", dateKey = RingHistoryDatabase.toDateKey(ms))
            }
            historyDb.insertVitalBatch(RingHistoryDatabase.METRIC_HEART_RATE, hrItems, "HEALTH_CONNECT")
            historyDb.insertVitalBatch(RingHistoryDatabase.METRIC_SPO2, spo2Items, "HEALTH_CONNECT")
            historyDb.insertVitalBatch(RingHistoryDatabase.METRIC_BLOOD_PRESSURE, bpItems, "HEALTH_CONNECT")
            historyDb.insertVitalBatch(RingHistoryDatabase.METRIC_HRV, hrvItems, "HEALTH_CONNECT")
            historyDb.insertVitalBatch(RingHistoryDatabase.METRIC_RESPIRATORY_RATE, respItems, "HEALTH_CONNECT")

            val groupedSport = sport.groupBy { RingHistoryDatabase.toDateKey(it.startTime.toEpochMilli()) }
            for ((dateKey, recs) in groupedSport) {
                val sumSteps = recs.sumOf { it.steps }
                val sumCal = recs.sumOf { it.caloriesKcal }
                val sumDist = recs.sumOf { it.distanceMeters }
                val ts = recs.maxOfOrNull { it.endTime.toEpochMilli() } ?: System.currentTimeMillis()
                historyDb.upsertDailyActivity(dateKey, sumSteps, sumCal, sumDist, ts)
            }

            for (session in sleep) {
                persistSleepSession(session)
            }
        }
        refreshHistoriesFromDatabase()
    }

    private fun persistSleepSession(session: YcbtSleepSession) {
        val totSec = session.stages.sumOf { it.durationSeconds }
        val totalMin = (totSec / 60).toInt()
        if (totalMin <= 0) return

        val d = (session.stages.filter { it.type == YcbtSleepStageType.DEEP }.sumOf { it.durationSeconds } / 60).toInt()
        val l = (session.stages.filter { it.type == YcbtSleepStageType.LIGHT }.sumOf { it.durationSeconds } / 60).toInt()
        val r = (session.stages.filter { it.type == YcbtSleepStageType.REM }.sumOf { it.durationSeconds } / 60).toInt()
        val a = (session.stages.filter { it.type == YcbtSleepStageType.AWAKE }.sumOf { it.durationSeconds } / 60).toInt()
        val formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault())
        val stageFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
        val stagesUi = session.stages.map { st ->
            val name = when (st.type) {
                YcbtSleepStageType.DEEP -> "Sueño Profundo"
                YcbtSleepStageType.LIGHT -> "Sueño Ligero"
                YcbtSleepStageType.REM -> "Fase REM"
                YcbtSleepStageType.AWAKE -> "Vigilia"
            }
            SleepStageUiItem(name, stageFmt.format(st.startTime), (st.durationSeconds / 60).toInt().coerceAtLeast(1))
        }
        val endMs = session.endTime.toEpochMilli()
        val dateKey = RingHistoryDatabase.toDateKey(endMs)
        val rangeStr = "${formatter.format(session.startTime)} - ${formatter.format(session.endTime)}"
        historyDb.upsertSleepSession(
            startMs = session.startTime.toEpochMilli(),
            endMs = endMs,
            dateKey = dateKey,
            totalMin = totalMin,
            deepMin = d,
            lightMin = l,
            remMin = r,
            awakeMin = a,
            dateRangeStr = rangeStr,
            stages = stagesUi
        )
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
            if (granted.any { it.contains("READ") }) {
                importHealthConnectHistory()
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
                if (state == BleConnectionState.CONNECTED) {
                    syncStoredSettingsToRing()
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

    private fun syncStoredSettingsToRing() {
        val s = _uiState.value
        bleManager.sendYcbtCommands(
            listOf(
                YcbtCommands.settingUserInfo(s.userHeightCm, s.userWeightKg, s.userSex, s.userAge),
                YcbtCommands.settingUnit(tempCelsius = true),
                YcbtCommands.settingGoal(0, s.stepGoal)
            )
        )
    }

    private fun observeTelemetry() {
        viewModelScope.launch {
            bleManager.heartRateFlow.collect { sample ->
                val status = classifyHeartRate(sample.bpm)
                val dk = RingHistoryDatabase.toDateKey(sample.timestamp)
                val item = MetricHistoryItem(sample.timestamp, timeFormatter.format(Date(sample.timestamp)), sample.bpm.toFloat(), displayValue = "${sample.bpm}", unit = "BPM", statusLabel = status, dateKey = dk)
                withContext(Dispatchers.IO) {
                    historyDb.insertVitalRecord(RingHistoryDatabase.METRIC_HEART_RATE, item, "RING")
                }
                _uiState.update {
                    it.copy(
                        currentHeartRate = sample.bpm,
                        lastHeartRateTime = sample.timestamp,
                        statusMessage = "Pulso detectado: ${sample.bpm} BPM"
                    )
                }
                saveSnapshotPrefs()
                refreshHistoriesFromDatabase()

                sessionLogs.add(CsvLogEntry(timestamp = sample.timestamp, metricType = "HEART_RATE", value1 = "${sample.bpm} BPM"))
                if (healthConnectBridge.isAvailable) {
                    healthConnectBridge.writeHeartRateSample(sample.bpm, Instant.ofEpochMilli(sample.timestamp))
                }
            }
        }

        viewModelScope.launch {
            bleManager.spo2Flow.collect { percent ->
                val now = System.currentTimeMillis()
                val dk = RingHistoryDatabase.toDateKey(now)
                val item = MetricHistoryItem(now, timeFormatter.format(Date(now)), percent.toFloat(), displayValue = "$percent%", unit = "%", statusLabel = classifySpo2(percent), dateKey = dk)
                withContext(Dispatchers.IO) {
                    historyDb.insertVitalRecord(RingHistoryDatabase.METRIC_SPO2, item, "RING")
                }
                _uiState.update {
                    it.copy(
                        currentSpo2 = percent,
                        lastSpo2Time = now,
                        statusMessage = "Oxígeno SpO2: $percent%"
                    )
                }
                saveSnapshotPrefs()
                refreshHistoriesFromDatabase()

                sessionLogs.add(CsvLogEntry(timestamp = now, metricType = "SPO2", value1 = "$percent%"))
                if (healthConnectBridge.isAvailable) {
                    healthConnectBridge.writeOxygenSaturation(percent, Instant.ofEpochMilli(now))
                }
            }
        }

        viewModelScope.launch {
            bleManager.batteryFlow.collect { (battery, isCharging) ->
                _uiState.update {
                    it.copy(
                        batteryPercent = battery,
                        isCharging = isCharging,
                        statusMessage = "Batería del anillo: $battery%${if (isCharging) " (Cargando)" else ""}"
                    )
                }
            }
        }

        viewModelScope.launch {
            bleManager.bloodPressureFlow.collect { (sys, dia) ->
                val now = System.currentTimeMillis()
                val dk = RingHistoryDatabase.toDateKey(now)
                val item = MetricHistoryItem(now, timeFormatter.format(Date(now)), sys.toFloat(), dia.toFloat(), "$sys/$dia", "mmHg", classifyBloodPressure(sys, dia), dateKey = dk)
                withContext(Dispatchers.IO) {
                    historyDb.insertVitalRecord(RingHistoryDatabase.METRIC_BLOOD_PRESSURE, item, "RING")
                }
                if (healthConnectBridge.isAvailable) {
                    healthConnectBridge.writeBloodPressure(sys, dia, Instant.ofEpochMilli(now))
                }
                _uiState.update {
                    it.copy(
                        currentBloodPressureSys = sys,
                        currentBloodPressureDia = dia,
                        lastBloodPressureTime = now,
                        isOffWristAlert = false,
                        statusMessage = "Presión capturada: $sys/$dia mmHg"
                    )
                }
                saveSnapshotPrefs()
                refreshHistoriesFromDatabase()
                sessionLogs.add(CsvLogEntry(timestamp = now, metricType = "BLOOD_PRESSURE", value1 = "$sys/$dia mmHg"))
            }
        }

        viewModelScope.launch {
            bleManager.hrvFlow.collect { hrv ->
                val now = System.currentTimeMillis()
                val dk = RingHistoryDatabase.toDateKey(now)
                val item = MetricHistoryItem(now, timeFormatter.format(Date(now)), hrv.toFloat(), displayValue = "$hrv", unit = "ms", statusLabel = if (hrv >= 30) "Óptimo" else "Fatiga / Estrés", dateKey = dk)
                withContext(Dispatchers.IO) {
                    historyDb.insertVitalRecord(RingHistoryDatabase.METRIC_HRV, item, "RING")
                }
                if (healthConnectBridge.isAvailable) {
                    healthConnectBridge.writeHrv(hrv, Instant.ofEpochMilli(now))
                }
                _uiState.update { it.copy(currentHrvMs = hrv, lastHrvTime = now) }
                saveSnapshotPrefs()
                refreshHistoriesFromDatabase()
            }
        }

        viewModelScope.launch {
            bleManager.respiratoryRateFlow.collect { rpm ->
                val now = System.currentTimeMillis()
                val dk = RingHistoryDatabase.toDateKey(now)
                val item = MetricHistoryItem(now, timeFormatter.format(Date(now)), rpm.toFloat(), displayValue = "$rpm", unit = "rpm", statusLabel = if (rpm in 12..20) "Normal" else "Atípica", dateKey = dk)
                withContext(Dispatchers.IO) {
                    historyDb.insertVitalRecord(RingHistoryDatabase.METRIC_RESPIRATORY_RATE, item, "RING")
                }
                if (healthConnectBridge.isAvailable) {
                    healthConnectBridge.writeRespiratoryRate(rpm, Instant.ofEpochMilli(now))
                }
                _uiState.update { it.copy(currentRespiratoryRateRpm = rpm, lastRespiratoryRateTime = now) }
                saveSnapshotPrefs()
                refreshHistoriesFromDatabase()
            }
        }

        viewModelScope.launch {
            bleManager.deviceInfoFlow.collect { info ->
                if (info != null) {
                    _uiState.update {
                        it.copy(
                            firmwareVersion = "v${info.firmwareVersion}",
                            batteryPercent = info.batteryPercent,
                            isCharging = info.isCharging
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            bleManager.deviceCapabilitiesFlow.collect { caps ->
                if (caps != null) {
                    _uiState.update { it.copy(supportedCapabilitiesSummary = caps.toSummaryString()) }
                }
            }
        }

        viewModelScope.launch {
            bleManager.historySyncResultFlow.collect { history ->
                applyHistorySyncResult(history)
            }
        }

        viewModelScope.launch {
            bleManager.sensorContactFlow.collect { hasContact ->
                _uiState.update {
                    it.copy(
                        isOffWristAlert = !hasContact,
                        statusMessage = if (!hasContact) "Aviso: coloca el anillo firmemente en tu dedo" else "Sensor en contacto firme"
                    )
                }
            }
        }

        viewModelScope.launch {
            bleManager.consolidatedStepsFlow.collect { sample ->
                val now = System.currentTimeMillis()
                val dk = RingHistoryDatabase.toDateKey(now)
                withContext(Dispatchers.IO) {
                    historyDb.upsertDailyActivity(dk, sample.steps, sample.calories, sample.distanceMeters, now)
                }
                _uiState.update {
                    it.copy(
                        totalStepsToday = maxOf(it.totalStepsToday, sample.steps),
                        totalCaloriesToday = if (sample.calories > 0) sample.calories else it.totalCaloriesToday,
                        totalDistanceMetersToday = if (sample.distanceMeters > 0) sample.distanceMeters else it.totalDistanceMetersToday,
                        lastSyncTimestamp = now
                    )
                }
                saveSnapshotPrefs()
                refreshHistoriesFromDatabase()

                sessionLogs.add(CsvLogEntry(timestamp = now, metricType = "STEPS_CONSOLIDATED", value1 = "${sample.steps} steps", value2 = "${sample.calories} kcal", value3 = "${sample.distanceMeters} m"))
                if (healthConnectBridge.isAvailable && sample.steps > 0) {
                    val instantNow = Instant.ofEpochMilli(now)
                    val windowStart = instantNow.minus(30, ChronoUnit.MINUTES)
                    healthConnectBridge.writeStepsWindow(sample.steps.toLong(), windowStart, instantNow)
                    if (sample.calories > 0) healthConnectBridge.writeCalories(sample.calories.toDouble(), windowStart, instantNow)
                    if (sample.distanceMeters > 0) healthConnectBridge.writeDistance(sample.distanceMeters.toDouble(), windowStart, instantNow)
                }
            }
        }

        viewModelScope.launch {
            bleManager.instantStepFlow.collect {
                _uiState.update { it.copy(totalStepsToday = it.totalStepsToday + 1) }
            }
        }

        viewModelScope.launch {
            bleManager.activityStateFlow.collect { act ->
                _uiState.update { it.copy(isMoving = act.isMoving) }
            }
        }

        viewModelScope.launch {
            bleManager.sleepStateFlow.collect { sleep ->
                val desc = if (sleep.eventId != null) "Evento de sueño #${sleep.eventId}" else "Anillo en uso (Wear OK)"
                _uiState.update { it.copy(sleepStatusText = desc) }
            }
        }

        viewModelScope.launch {
            bleManager.rawLogsFlow.collect { raw ->
                Log.d(TAG, "RAW BLE LOG: $raw")
            }
        }
    }

    private suspend fun applyHistorySyncResult(history: YcbtHistorySyncResult) {
        var totalWritten = 0
        if (healthConnectBridge.isAvailable) {
            if (history.sportRecords.isNotEmpty()) totalWritten += healthConnectBridge.writeSportBatch(history.sportRecords)
            for (session in history.sleepSessions) {
                if (healthConnectBridge.writeSleepSession(session)) totalWritten++
            }
            if (history.heartRateRecords.isNotEmpty()) totalWritten += healthConnectBridge.writeHeartRateBatch(history.heartRateRecords)
            if (history.spo2Records.isNotEmpty()) totalWritten += healthConnectBridge.writeSpo2Batch(history.spo2Records)
            if (history.bloodPressureRecords.isNotEmpty()) totalWritten += healthConnectBridge.writeBloodPressureBatch(history.bloodPressureRecords)
            if (history.hrvRecords.isNotEmpty()) totalWritten += healthConnectBridge.writeHrvBatch(history.hrvRecords)
            if (history.respiratoryRateRecords.isNotEmpty()) totalWritten += healthConnectBridge.writeRespiratoryRateBatch(history.respiratoryRateRecords)
        }

        withContext(Dispatchers.IO) {
            historyDb.insertVitalBatch(
                RingHistoryDatabase.METRIC_HEART_RATE,
                history.heartRateRecords.map {
                    val ms = it.timestamp.toEpochMilli()
                    MetricHistoryItem(ms, timeFormatter.format(Date(ms)), it.bpm.toFloat(), displayValue = "${it.bpm}", unit = "BPM", statusLabel = classifyHeartRate(it.bpm), dateKey = RingHistoryDatabase.toDateKey(ms))
                }
            )
            historyDb.insertVitalBatch(
                RingHistoryDatabase.METRIC_SPO2,
                history.spo2Records.map {
                    val ms = it.timestamp.toEpochMilli()
                    MetricHistoryItem(ms, timeFormatter.format(Date(ms)), it.percent.toFloat(), displayValue = "${it.percent}%", unit = "%", statusLabel = classifySpo2(it.percent), dateKey = RingHistoryDatabase.toDateKey(ms))
                }
            )
            historyDb.insertVitalBatch(
                RingHistoryDatabase.METRIC_BLOOD_PRESSURE,
                history.bloodPressureRecords.map {
                    val ms = it.timestamp.toEpochMilli()
                    MetricHistoryItem(ms, timeFormatter.format(Date(ms)), it.systolic.toFloat(), it.diastolic.toFloat(), "${it.systolic}/${it.diastolic}", "mmHg", classifyBloodPressure(it.systolic, it.diastolic), dateKey = RingHistoryDatabase.toDateKey(ms))
                }
            )
            historyDb.insertVitalBatch(
                RingHistoryDatabase.METRIC_HRV,
                history.hrvRecords.map {
                    val ms = it.timestamp.toEpochMilli()
                    MetricHistoryItem(ms, timeFormatter.format(Date(ms)), it.hrvMs.toFloat(), displayValue = "${it.hrvMs}", unit = "ms", statusLabel = if (it.hrvMs >= 30) "Óptimo" else "Fatiga", dateKey = RingHistoryDatabase.toDateKey(ms))
                }
            )
            historyDb.insertVitalBatch(
                RingHistoryDatabase.METRIC_RESPIRATORY_RATE,
                history.respiratoryRateRecords.map {
                    val ms = it.timestamp.toEpochMilli()
                    MetricHistoryItem(ms, timeFormatter.format(Date(ms)), it.rpm.toFloat(), displayValue = "${it.rpm}", unit = "rpm", statusLabel = if (it.rpm in 12..20) "Normal" else "Atípica", dateKey = RingHistoryDatabase.toDateKey(ms))
                }
            )

            val groupedSport = history.sportRecords.groupBy { RingHistoryDatabase.toDateKey(it.startTime.toEpochMilli()) }
            for ((dateKey, recs) in groupedSport) {
                val sumSteps = recs.sumOf { it.steps }
                val sumCal = recs.sumOf { it.caloriesKcal }
                val sumDist = recs.sumOf { it.distanceMeters }
                val ts = recs.maxOfOrNull { it.endTime.toEpochMilli() } ?: System.currentTimeMillis()
                historyDb.upsertDailyActivity(dateKey, sumSteps, sumCal, sumDist, ts)
            }

            for (session in history.sleepSessions) {
                persistSleepSession(session)
            }
        }

        val now = System.currentTimeMillis()
        _uiState.update { s ->
            s.copy(
                lastSyncTimestamp = now,
                statusMessage = when {
                    totalWritten > 0 -> "Sincronizados ${history.totalCount} registros ($totalWritten en Samsung Health)"
                    history.totalCount > 0 -> "Sincronizados ${history.totalCount} registros al historial SQLite"
                    else -> s.statusMessage
                }
            )
        }
        saveSnapshotPrefs()
        refreshHistoriesFromDatabase()
    }

    fun startScanningAndConnect() {
        bleManager.startScan()
    }

    fun disconnectRing() {
        cancelActiveMeasurement()
        bleManager.disconnect()
    }

    fun forceSyncNow() {
        if (_uiState.value.connectionState == BleConnectionState.CONNECTED) {
            viewModelScope.launch {
                _uiState.update { it.copy(statusMessage = "Descargando historial completo (05 02/04/06/08/09)...") }
                val history = bleManager.syncAllHistory()
                importHealthConnectHistory()
                if (history.totalCount == 0) {
                    _uiState.update { it.copy(statusMessage = "Anillo y base histórica al día") }
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
            val syncRequest = PeriodicWorkRequestBuilder<RingSyncWorker>(30, TimeUnit.MINUTES).build()
            workManager.enqueueUniquePeriodicWork(
                RingSyncWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )
        } else {
            workManager.cancelUniqueWork(RingSyncWorker.WORK_NAME)
        }
    }

    fun exportSessionCsv(): String? {
        val result = csvExporter.exportSessionToDownloads(sessionLogs.toList())
        _uiState.update {
            it.copy(statusMessage = if (result != null) "CSV guardado en $result" else "Error al exportar CSV")
        }
        return result
    }

    fun refreshBattery() {
        if (_uiState.value.connectionState == BleConnectionState.CONNECTED) {
            _uiState.update { it.copy(statusMessage = "Consultando estado de hardware y batería...") }
            bleManager.requestBatteryInfo()
        }
    }

    fun triggerHeartRateMeasurement() = runSpotMeasurement(
        mode = YcbtConstants.MEASURE_MODE_HEART_RATE,
        label = "Pulso Cardíaco (PPG)",
        timeoutSeconds = 45
    )

    fun triggerSpo2Measurement() = runSpotMeasurement(
        mode = YcbtConstants.MEASURE_MODE_SPO2,
        label = "Saturación de Oxígeno SpO2",
        timeoutSeconds = 60
    )

    fun triggerBloodPressureMeasurement() = runSpotMeasurement(
        mode = YcbtConstants.MEASURE_MODE_BP,
        label = "Presión Arterial",
        timeoutSeconds = 60
    )

    fun triggerHrvMeasurement() = runSpotMeasurement(
        mode = YcbtConstants.MEASURE_MODE_HRV,
        label = "Variabilidad Cardíaca (HRV)",
        timeoutSeconds = 60
    )

    private fun runSpotMeasurement(mode: Byte, label: String, timeoutSeconds: Int = 60) {
        if (_uiState.value.connectionState != BleConnectionState.CONNECTED) {
            _uiState.update { it.copy(statusMessage = "Conecta el Smart Ring antes de iniciar medición") }
            return
        }
        val modeInt = mode.toInt() and 0xFF
        spotMeasurementJob?.cancel()

        spotMeasurementJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val finishSignal = CompletableDeferred<Boolean>()

            _uiState.update {
                it.copy(
                    isMeasuringHeartRate = (mode == YcbtConstants.MEASURE_MODE_HEART_RATE),
                    isMeasuringSpo2 = (mode == YcbtConstants.MEASURE_MODE_SPO2),
                    isMeasuringBloodPressure = (mode == YcbtConstants.MEASURE_MODE_BP),
                    isMeasuringHrv = (mode == YcbtConstants.MEASURE_MODE_HRV),
                    measurementProgressSeconds = 0,
                    measurementTargetSeconds = timeoutSeconds,
                    activeMeasurementLabel = label,
                    activeMeasurementModeByte = modeInt,
                    isOffWristAlert = false,
                    statusMessage = "Midiendo $label... Mantén el dedo inmóvil (30-60s)"
                )
            }

            val hwFinishJob = launch {
                bleManager.spotMeasurementStatusFlow.collect { status ->
                    if (status.type == modeInt && status.isFinished) {
                        finishSignal.complete(status.isSuccess)
                    }
                }
            }

            bleManager.startSpotMeasurement(mode)

            var elapsed = 0
            while (elapsed < timeoutSeconds && !finishSignal.isCompleted) {
                delay(1_000L)
                elapsed++
                _uiState.update { it.copy(measurementProgressSeconds = elapsed) }
            }

            val hwFinishedNormally = finishSignal.isCompleted
            hwFinishJob.cancel()

            if (!hwFinishedNormally) {
                bleManager.stopSpotMeasurement(mode)
                bleManager.syncAfterSpotMeasurement(modeInt)
            }
            delay(150L)

            val updatedFreshly = hasFreshSampleSince(mode, startTime)
            clearMeasurementFlags(
                statusMessage = when {
                    hwFinishedNormally && finishSignal.await() -> "$label confirmada por el sensor del anillo"
                    updatedFreshly -> "$label capturada desde telemetría en vivo"
                    hwFinishedNormally -> "Medición cancelada por el sensor: coloca el anillo firmemente"
                    else -> "Sin respuesta óptica: ajusta el anillo firmemente y reintenta"
                }
            )
        }
    }

    fun cancelActiveMeasurement() {
        val activeMode = _uiState.value.activeMeasurementModeByte
        spotMeasurementJob?.cancel()
        spotMeasurementJob = null
        if (activeMode != null) {
            bleManager.stopSpotMeasurement(activeMode.toByte())
        }
        clearMeasurementFlags("Medición cancelada por el usuario")
    }

    private fun hasFreshSampleSince(mode: Byte, sinceMs: Long): Boolean {
        val s = _uiState.value
        return when (mode) {
            YcbtConstants.MEASURE_MODE_HEART_RATE -> (s.lastHeartRateTime ?: 0L) >= sinceMs
            YcbtConstants.MEASURE_MODE_SPO2 -> (s.lastSpo2Time ?: 0L) >= sinceMs
            YcbtConstants.MEASURE_MODE_BP -> (s.lastBloodPressureTime ?: 0L) >= sinceMs
            YcbtConstants.MEASURE_MODE_HRV -> (s.lastHrvTime ?: 0L) >= sinceMs
            else -> false
        }
    }

    private fun clearMeasurementFlags(statusMessage: String) {
        _uiState.update {
            it.copy(
                isMeasuringHeartRate = false,
                isMeasuringSpo2 = false,
                isMeasuringBloodPressure = false,
                isMeasuringHrv = false,
                measurementProgressSeconds = 0,
                activeMeasurementLabel = null,
                activeMeasurementModeByte = null,
                statusMessage = statusMessage
            )
        }
    }

    // --- Perfil Biométrico, Calibración y Memoria Flash ---

    fun setMonitoringInterval(minutes: Int) {
        val cadence = minutes.coerceIn(10, 60)
        _uiState.update {
            it.copy(
                monitoringIntervalMinutes = cadence,
                statusMessage = "Monitoreo automático fijado cada $cadence minutos"
            )
        }
        saveProfilePrefs()
        bleManager.sendYcbtCommands(YcbtCommands.enablePeriodicMonitors(cadence))
    }

    fun setStepGoal(steps: Int) {
        val cleanGoal = steps.coerceIn(2000, 30000)
        _uiState.update {
            it.copy(
                stepGoal = cleanGoal,
                statusMessage = "Meta diaria actualizada a ${String.format(Locale.getDefault(), "%,d", cleanGoal)} pasos"
            )
        }
        saveProfilePrefs()
        bleManager.sendYcbtCommand(YcbtCommands.settingGoal(0, cleanGoal))
    }

    fun updateUserProfile(
        heightCm: Int,
        weightKg: Int,
        age: Int,
        sex: Int
    ) {
        val h = heightCm.coerceIn(100, 220)
        val w = weightKg.coerceIn(35, 180)
        val a = age.coerceIn(12, 100)
        val s = sex.coerceIn(0, 1)
        _uiState.update {
            it.copy(
                userHeightCm = h,
                userWeightKg = w,
                userAge = a,
                userSex = s,
                statusMessage = "Perfil biométrico (${h}cm, ${w}kg, ${a} años) guardado en el anillo"
            )
        }
        saveProfilePrefs()
        bleManager.sendYcbtCommand(YcbtCommands.settingUserInfo(h, w, s, a))
    }

    fun clearRingFlashMemory() {
        bleManager.sendYcbtCommands(
            listOf(
                YcbtCommands.deleteHistory(YcbtConstants.DELETE_SPORT_STEPS),
                YcbtCommands.deleteHistory(YcbtConstants.DELETE_SLEEP),
                YcbtCommands.deleteHistory(YcbtConstants.DELETE_HEART_RATE),
                YcbtCommands.deleteHistory(YcbtConstants.DELETE_BLOOD_PRESSURE),
                YcbtCommands.deleteHistory(YcbtConstants.DELETE_COMBINED_VITALS)
            )
        )
        _uiState.update {
            it.copy(statusMessage = "Buffer flash del anillo liberado (tu historial SQLite permanece intacto)")
        }
    }

    fun restoreRingFactorySettings() {
        bleManager.sendYcbtCommand(YcbtCommands.restoreFactorySettings())
        _uiState.update {
            it.copy(statusMessage = "Comando de restauración de fábrica (0x01 0x0E) enviado al anillo")
        }
    }

    fun calibrateBloodPressure(sys: Int, dia: Int) {
        bleManager.sendYcbtCommand(YcbtCommands.calibrateBloodPressure(sys, dia))
        _uiState.update { it.copy(statusMessage = "Presión de referencia calibrada a $sys/$dia mmHg") }
    }

    private fun classifyHeartRate(bpm: Int): String = when {
        bpm < 50 -> "Bradicardia"
        bpm in 50..100 -> "Normal"
        bpm in 101..125 -> "Elevado"
        else -> "Taquicardia"
    }

    private fun classifySpo2(spo2: Int): String = when {
        spo2 >= 95 -> "Óptimo"
        spo2 in 90..94 -> "Aceptable"
        else -> "Hipoxemia"
    }

    private fun classifyBloodPressure(sys: Int, dia: Int): String = when {
        sys < 90 || dia < 60 -> "Hipotensión"
        sys <= 120 && dia <= 80 -> "Óptima"
        sys <= 139 && dia <= 89 -> "Normal-Alta"
        else -> "Hipertensión"
    }

    override fun onCleared() {
        super.onCleared()
        spotMeasurementJob?.cancel()
        bleManager.disconnect()
    }
}
