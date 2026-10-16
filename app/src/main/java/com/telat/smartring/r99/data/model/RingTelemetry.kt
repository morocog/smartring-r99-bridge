package com.telat.smartring.r99.data.model

data class HeartRateSample(
    val bpm: Int,
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

data class RingDashboardUiState(
    val connectionState: BleConnectionState = BleConnectionState.DISCONNECTED,
    val deviceName: String = "No conectado",
    val deviceAddress: String = "",
    val batteryPercent: Int? = null,
    val isCharging: Boolean = false,
    val currentHeartRate: Int = 0,
    val lastHeartRateTime: Long? = null,
    val currentSpo2: Int? = null,
    val lastSpo2Time: Long? = null,
    val totalStepsToday: Int = 0,
    val totalCaloriesToday: Int = 0,
    val totalDistanceMetersToday: Int = 0,
    val isMoving: Boolean = false,
    val sleepStatusText: String = "En reposo",
    val syncedSleepSessionsCount: Int = 0,
    val syncedHistoryRecordsCount: Int = 0,
    val isPeriodicSyncEnabled: Boolean = true,
    val isHealthConnectAvailable: Boolean = false,
    val hasHealthPermissions: Boolean = false,
    val lastSyncTimestamp: Long? = null,
    val statusMessage: String = "Listo para escanear y conectar"
)
