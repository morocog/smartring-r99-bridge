package com.telat.smartring.r99.data.model

data class HeartRateSample(
    val bpm: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class BloodPressureSample(
    val systolic: Int,
    val diastolic: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class TemperatureSample(
    val celsius: Float,
    val timestamp: Long = System.currentTimeMillis()
)

data class StepsSample(
    val steps: Int,
    val calories: Int,
    val distanceMeters: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class ActivityState(
    val isMoving: Boolean,
    val rawState: String
)

data class SleepState(
    val wearOk: Boolean,
    val eventId: Int?
)

enum class BleConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    SYNCING,
    ERROR
}

enum class VitalDetailType {
    HEART_RATE,
    SPO2,
    BLOOD_PRESSURE,
    HRV,
    RESPIRATORY_RATE,
    SLEEP,
    STEPS
}

enum class HistoryPeriodTab {
    DAY,
    WEEK,
    MONTH
}

data class DailyMetricAggregate(
    val dateKey: String,
    val shortDateLabel: String,
    val avgValue: Float,
    val minValue: Float,
    val maxValue: Float,
    val secondaryAvg: Float? = null,
    val sampleCount: Int,
    val unit: String
)

data class DailyActivitySummary(
    val dateKey: String,
    val shortDateLabel: String,
    val steps: Int,
    val calories: Int,
    val distanceMeters: Int
)

data class SleepSessionSummaryRecord(
    val dateKey: String,
    val shortDateLabel: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val totalMinutes: Int,
    val deepMinutes: Int,
    val lightMinutes: Int,
    val remMinutes: Int,
    val awakeMinutes: Int,
    val dateRangeStr: String,
    val stages: List<SleepStageUiItem>
)

data class MetricHistoryItem(
    val timestampMillis: Long,
    val formattedTime: String,
    val primaryValue: Float,
    val secondaryValue: Float? = null,
    val displayValue: String,
    val unit: String,
    val statusLabel: String,
    val dateKey: String = ""
)

data class SleepStageUiItem(
    val stageName: String,
    val startTimeFormatted: String,
    val durationMinutes: Int
)

data class RingDashboardUiState(
    val connectionState: BleConnectionState = BleConnectionState.DISCONNECTED,
    val deviceName: String = "No conectado",
    val deviceAddress: String = "",
    val batteryPercent: Int? = null,
    val isCharging: Boolean = false,
    val firmwareVersion: String = "--",
    val hardwareModel: String = "Smart Ring R99",
    val supportedCapabilitiesSummary: String = "Detectando sensores...",
    val currentHeartRate: Int = 0,
    val lastHeartRateTime: Long? = null,
    val currentSpo2: Int? = null,
    val lastSpo2Time: Long? = null,
    val currentBloodPressureSys: Int? = null,
    val currentBloodPressureDia: Int? = null,
    val lastBloodPressureTime: Long? = null,
    val currentHrvMs: Int? = null,
    val lastHrvTime: Long? = null,
    val currentRespiratoryRateRpm: Int? = null,
    val lastRespiratoryRateTime: Long? = null,
    val totalStepsToday: Int = 0,
    val totalCaloriesToday: Int = 0,
    val totalDistanceMetersToday: Int = 0,
    val stepGoal: Int = 8000,
    val sleepGoalMinutes: Int = 480,
    val monitoringIntervalMinutes: Int = 30,
    val userHeightCm: Int = 175,
    val userWeightKg: Int = 75,
    val userAge: Int = 35,
    val userSex: Int = 0,
    val isMoving: Boolean = false,
    val sleepStatusText: String = "En reposo",
    val syncedSleepSessionsCount: Int = 0,
    val lastSleepTotalMinutes: Int = 0,
    val lastSleepDeepMinutes: Int = 0,
    val lastSleepLightMinutes: Int = 0,
    val lastSleepRemMinutes: Int = 0,
    val lastSleepAwakeMinutes: Int = 0,
    val lastSleepDateStr: String = "",
    val syncedHistoryRecordsCount: Int = 0,
    val isMeasuringHeartRate: Boolean = false,
    val isMeasuringSpo2: Boolean = false,
    val isMeasuringBloodPressure: Boolean = false,
    val isMeasuringHrv: Boolean = false,
    val measurementProgressSeconds: Int = 0,
    val measurementTargetSeconds: Int = 60,
    val activeMeasurementLabel: String? = null,
    val activeMeasurementModeByte: Int? = null,
    val isOffWristAlert: Boolean = false,
    val isPeriodicSyncEnabled: Boolean = true,
    val isHealthConnectAvailable: Boolean = false,
    val hasHealthPermissions: Boolean = false,
    val lastSyncTimestamp: Long? = null,
    val statusMessage: String = "Listo para escanear y conectar",
    val heartRateHistory: List<MetricHistoryItem> = emptyList(),
    val spo2History: List<MetricHistoryItem> = emptyList(),
    val bloodPressureHistory: List<MetricHistoryItem> = emptyList(),
    val hrvHistory: List<MetricHistoryItem> = emptyList(),
    val respiratoryRateHistory: List<MetricHistoryItem> = emptyList(),
    val sleepStagesHistory: List<SleepStageUiItem> = emptyList(),
    val dailyActivityHistory: List<DailyActivitySummary> = emptyList(),
    val sleepSessionsHistory: List<SleepSessionSummaryRecord> = emptyList()
)
