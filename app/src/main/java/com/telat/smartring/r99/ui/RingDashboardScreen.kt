package com.telat.smartring.r99.ui

import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.telat.smartring.r99.data.model.BleConnectionState
import com.telat.smartring.r99.data.model.DailyActivitySummary
import com.telat.smartring.r99.data.model.DailyMetricAggregate
import com.telat.smartring.r99.data.model.HistoryPeriodTab
import com.telat.smartring.r99.data.model.MetricHistoryItem
import com.telat.smartring.r99.data.model.RingDashboardUiState
import com.telat.smartring.r99.data.model.SleepSessionSummaryRecord
import com.telat.smartring.r99.data.model.SleepStageUiItem
import com.telat.smartring.r99.data.model.VitalDetailType
import com.telat.smartring.r99.ui.theme.DarkBackground
import com.telat.smartring.r99.ui.theme.DarkBorder
import com.telat.smartring.r99.ui.theme.DarkBorderHighlight
import com.telat.smartring.r99.ui.theme.DarkSurface
import com.telat.smartring.r99.ui.theme.DarkSurfaceElevated
import com.telat.smartring.r99.ui.theme.OxygenCyan
import com.telat.smartring.r99.ui.theme.RingGlowActive
import com.telat.smartring.r99.ui.theme.SleepAwake
import com.telat.smartring.r99.ui.theme.SleepDeep
import com.telat.smartring.r99.ui.theme.SleepIndigo
import com.telat.smartring.r99.ui.theme.SleepLight
import com.telat.smartring.r99.ui.theme.SleepRem
import com.telat.smartring.r99.ui.theme.StatusAmber
import com.telat.smartring.r99.ui.theme.StatusGreen
import com.telat.smartring.r99.ui.theme.StatusRed
import com.telat.smartring.r99.ui.theme.TelatBlue
import com.telat.smartring.r99.ui.theme.TelatOrange
import com.telat.smartring.r99.ui.theme.TextMuted
import com.telat.smartring.r99.ui.theme.TextPrimary
import com.telat.smartring.r99.ui.theme.TextSecondary
import com.telat.smartring.r99.ui.theme.TitaniumRingDark
import com.telat.smartring.r99.ui.theme.TitaniumRingLight
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun RingDashboardScreen(
    viewModel: RingViewModel,
    onRequestHealthPermissions: () -> Unit,
    onOpenHealthSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var activeDetailModal by remember { mutableStateOf<VitalDetailType?>(null) }

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                contentColor = TextPrimary,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Favorite, contentDescription = "Salud") },
                    label = { Text("Salud", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TelatBlue,
                        selectedTextColor = TelatBlue,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = TelatBlue.copy(alpha = 0.14f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Dispositivo") },
                    label = { Text("Dispositivo", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TelatBlue,
                        selectedTextColor = TelatBlue,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted,
                        indicatorColor = TelatBlue.copy(alpha = 0.14f)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> HealthTabContent(
                    uiState = uiState,
                    onConnect = { viewModel.startScanningAndConnect() },
                    onDisconnect = { viewModel.disconnectRing() },
                    onSyncNow = { viewModel.forceSyncNow() },
                    onRefreshBattery = { viewModel.refreshBattery() },
                    onCancelMeasurement = { viewModel.cancelActiveMeasurement() },
                    onRequestHealthPermissions = onRequestHealthPermissions,
                    onOpenDetail = { detailType -> activeDetailModal = detailType }
                )

                1 -> DeviceSettingsTab(
                    uiState = uiState,
                    viewModel = viewModel,
                    onRequestHealthPermissions = onRequestHealthPermissions,
                    onOpenHealthSettings = onOpenHealthSettings
                )
            }
        }
    }

    activeDetailModal?.let { detailType ->
        UnifiedHistoryDetailDialog(
            detailType = detailType,
            uiState = uiState,
            onDismiss = { activeDetailModal = null },
            onTriggerMeasurement = {
                when (detailType) {
                    VitalDetailType.HEART_RATE -> viewModel.triggerHeartRateMeasurement()
                    VitalDetailType.SPO2 -> viewModel.triggerSpo2Measurement()
                    VitalDetailType.BLOOD_PRESSURE -> viewModel.triggerBloodPressureMeasurement()
                    VitalDetailType.HRV -> viewModel.triggerHrvMeasurement()
                    else -> {}
                }
            },
            onCancelMeasurement = { viewModel.cancelActiveMeasurement() }
        )
    }
}

@Composable
private fun HealthTabContent(
    uiState: RingDashboardUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSyncNow: () -> Unit,
    onRefreshBattery: () -> Unit,
    onCancelMeasurement: () -> Unit,
    onRequestHealthPermissions: () -> Unit,
    onOpenDetail: (VitalDetailType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HeroRingSection(
            uiState = uiState,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            onSyncNow = onSyncNow,
            onRefreshBattery = onRefreshBattery
        )

        if (uiState.activeMeasurementLabel != null) {
            ActiveMeasurementBanner(
                label = uiState.activeMeasurementLabel,
                progressSeconds = uiState.measurementProgressSeconds,
                targetSeconds = uiState.measurementTargetSeconds,
                onCancel = onCancelMeasurement
            )
        }

        if (uiState.isOffWristAlert) {
            OffWristWarningBanner()
        }

        if (!uiState.hasHealthPermissions) {
            HealthConnectBanner(onRequestPermissions = onRequestHealthPermissions)
        }

        Text(
            text = "Biometría en Tiempo Real",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp
        )

        // 2x2 Grid: Pulso Cardíaco + Oxígeno SpO2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VitalSummaryCard(
                modifier = Modifier.weight(1f),
                title = "Pulso Cardíaco",
                value = uiState.currentHeartRate?.let { "$it" } ?: "--",
                unit = "BPM",
                subtitle = formatTimestampShort(uiState.lastHeartRateTime) ?: "Sin lectura reciente",
                accentColor = StatusRed,
                icon = Icons.Filled.Favorite,
                isMeasuring = uiState.isMeasuringHeartRate,
                onClick = { onOpenDetail(VitalDetailType.HEART_RATE) }
            )
            VitalSummaryCard(
                modifier = Modifier.weight(1f),
                title = "Oxígeno SpO2",
                value = uiState.currentSpo2?.let { "$it" } ?: "--",
                unit = "%",
                subtitle = formatTimestampShort(uiState.lastSpo2Time) ?: "Sin lectura reciente",
                accentColor = OxygenCyan,
                icon = Icons.Filled.WaterDrop,
                isMeasuring = uiState.isMeasuringSpo2,
                onClick = { onOpenDetail(VitalDetailType.SPO2) }
            )
        }

        // 2x2 Grid: Presión Arterial + Variabilidad HRV
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val bpText = if (uiState.currentBloodPressureSys != null && uiState.currentBloodPressureDia != null) {
                "${uiState.currentBloodPressureSys}/${uiState.currentBloodPressureDia}"
            } else {
                "--/--"
            }
            VitalSummaryCard(
                modifier = Modifier.weight(1f),
                title = "Presión Arterial",
                value = bpText,
                unit = "mmHg",
                subtitle = formatTimestampShort(uiState.lastBloodPressureTime) ?: "Sin lectura reciente",
                accentColor = TelatBlue,
                icon = Icons.Filled.Speed,
                isMeasuring = uiState.isMeasuringBloodPressure,
                onClick = { onOpenDetail(VitalDetailType.BLOOD_PRESSURE) }
            )
            VitalSummaryCard(
                modifier = Modifier.weight(1f),
                title = "Variabilidad HRV",
                value = uiState.currentHrvMs?.let { "$it" } ?: "--",
                unit = "ms",
                subtitle = formatTimestampShort(uiState.lastHrvTime) ?: "Sin lectura reciente",
                accentColor = StatusGreen,
                icon = Icons.Filled.Timeline,
                isMeasuring = uiState.isMeasuringHrv,
                onClick = { onOpenDetail(VitalDetailType.HRV) }
            )
        }

        // Full-width Frecuencia Respiratoria Card
        RespiratoryRateCard(
            rpm = uiState.currentRespiratoryRateRpm,
            lastTime = formatTimestampShort(uiState.lastRespiratoryRateTime),
            onClick = { onOpenDetail(VitalDetailType.RESPIRATORY_RATE) }
        )

        // Activity Summary Card
        ActivityRingsCard(
            steps = uiState.totalStepsToday,
            stepGoal = uiState.stepGoal,
            calories = uiState.totalCaloriesToday,
            distanceMeters = uiState.totalDistanceMetersToday,
            isMoving = uiState.isMoving,
            onClick = { onOpenDetail(VitalDetailType.STEPS) }
        )

        // Sleep Summary Card with 4 high-contrast colors
        SleepHypnogramCard(
            totalMinutes = uiState.lastSleepTotalMinutes,
            deepMinutes = uiState.lastSleepDeepMinutes,
            lightMinutes = uiState.lastSleepLightMinutes,
            remMinutes = uiState.lastSleepRemMinutes,
            awakeMinutes = uiState.lastSleepAwakeMinutes,
            dateStr = uiState.lastSleepDateStr,
            sleepStatusText = uiState.sleepStatusText,
            onClick = { onOpenDetail(VitalDetailType.SLEEP) }
        )

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun HeroRingSection(
    uiState: RingDashboardUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSyncNow: () -> Unit,
    onRefreshBattery: () -> Unit
) {
    val isConnected = uiState.connectionState == BleConnectionState.CONNECTED ||
        uiState.connectionState == BleConnectionState.SYNCING
    val isBusy = uiState.connectionState == BleConnectionState.SCANNING ||
        uiState.connectionState == BleConnectionState.CONNECTING ||
        uiState.connectionState == BleConnectionState.SYNCING

    val infiniteTransition = rememberInfiniteTransition(label = "ringPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isConnected) TelatBlue.copy(alpha = 0.45f) else DarkBorder, RoundedCornerShape(22.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.size(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            if (isConnected) {
                                drawCircle(
                                    color = RingGlowActive.copy(alpha = pulseAlpha * 0.35f),
                                    radius = size.minDimension / 2f
                                )
                            }
                            drawCircle(
                                brush = Brush.linearGradient(
                                    colors = listOf(TitaniumRingLight, TitaniumRingDark)
                                ),
                                radius = size.minDimension * 0.38f,
                                style = Stroke(width = 7.dp.toPx())
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.BluetoothConnected,
                            contentDescription = null,
                            tint = if (isConnected) TelatBlue else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = uiState.deviceName ?: "Smart Ring R99",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val statusColor = when (uiState.connectionState) {
                            BleConnectionState.CONNECTED -> StatusGreen
                            BleConnectionState.SYNCING, BleConnectionState.CONNECTING, BleConnectionState.SCANNING -> TelatBlue
                            BleConnectionState.ERROR -> StatusRed
                            BleConnectionState.DISCONNECTED -> TextMuted
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = uiState.statusMessage,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Battery pill
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceElevated)
                        .clickable { onRefreshBattery() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    val battText = uiState.batteryPercent?.let { "$it%" } ?: "--%"
                    val battColor = when {
                        uiState.isCharging -> StatusGreen
                        (uiState.batteryPercent ?: 100) <= 20 -> StatusRed
                        else -> TextPrimary
                    }
                    Text(
                        text = if (uiState.isCharging) "⚡ $battText" else battText,
                        color = battColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (uiState.isCharging) "Cargando" else "Batería",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isConnected) {
                    Button(
                        onClick = onConnect,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TelatBlue,
                            contentColor = DarkBackground
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = DarkBackground,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = when (uiState.connectionState) {
                                BleConnectionState.SCANNING -> "Buscando R99..."
                                BleConnectionState.CONNECTING -> "Conectando..."
                                else -> "Conectar Anillo"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onSyncNow,
                        enabled = uiState.connectionState != BleConnectionState.SYNCING,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TelatBlue,
                            contentColor = DarkBackground
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (uiState.connectionState == BleConnectionState.SYNCING) "Sincronizando..." else "Sincronizar",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = onDisconnect,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Text("Desconectar")
                    }
                }
            }

            uiState.lastSyncTimestamp?.let { ts ->
                val formattedSync = formatTimestampShort(ts) ?: "$ts"
                Text(
                    text = "Última sincronización: $formattedSync · ${uiState.syncedHistoryRecordsCount} registros · ${uiState.syncedSleepSessionsCount} noches",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ActiveMeasurementBanner(
    label: String,
    progressSeconds: Int,
    targetSeconds: Int,
    onCancel: () -> Unit
) {
    val progress = if (targetSeconds > 0) (progressSeconds.toFloat() / targetSeconds.toFloat()).coerceIn(0f, 1f) else 0f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TelatBlue.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = TelatBlue.copy(alpha = 0.14f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$label (${progressSeconds}s / ${targetSeconds}s)",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                TextButton(onClick = onCancel) {
                    Text("Cancelar", color = StatusRed, fontSize = 12.sp)
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = TelatBlue,
                trackColor = DarkSurfaceElevated
            )
        }
    }
}

@Composable
private fun OffWristWarningBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, StatusAmber.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = StatusAmber.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(20.dp))
            Text(
                text = "El anillo detecta que no está colocado en el dedo. Colócalo para obtener lecturas válidas.",
                color = TextPrimary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun HealthConnectBanner(onRequestPermissions: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, StatusGreen.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable { onRequestPermissions() },
        colors = CardDefaults.cardColors(containerColor = StatusGreen.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Health Connect disponible", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("Toca para autorizar lectura/escritura de 30 días.", color = TextSecondary, fontSize = 11.sp)
            }
            Text("Autorizar", color = StatusGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VitalSummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    unit: String,
    subtitle: String,
    accentColor: Color,
    icon: ImageVector,
    isMeasuring: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .border(1.dp, if (isMeasuring) accentColor else DarkBorder, RoundedCornerShape(18.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                }
                if (isMeasuring) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = accentColor,
                        strokeWidth = 2.dp
                    )
                }
            }

            Text(text = title, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = unit,
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RespiratoryRateCard(
    rpm: Int?,
    lastTime: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(18.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(OxygenCyan.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Air, contentDescription = null, tint = OxygenCyan, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text("Frecuencia Respiratoria", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = lastTime ?: "Derivada de ciclos nocturnos y pulso",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = rpm?.let { "$it" } ?: "--",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("rpm", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun ActivityRingsCard(
    steps: Int,
    stepGoal: Int,
    calories: Int,
    distanceMeters: Int,
    isMoving: Boolean,
    onClick: () -> Unit
) {
    val progress = if (stepGoal > 0) (steps.toFloat() / stepGoal.toFloat()).coerceIn(0f, 1f) else 0f
    val km = distanceMeters / 1000f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(18.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(TelatOrange.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, tint = TelatOrange, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text("Actividad Diaria", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (isMoving) "En movimiento ahora" else "Meta: $stepGoal pasos",
                            color = if (isMoving) StatusGreen else TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
                Text(
                    text = "${(progress * 100).roundToInt()}%",
                    color = TelatOrange,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = TelatOrange,
                trackColor = DarkSurfaceElevated
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ActivityStatMini(label = "Pasos", value = "$steps")
                ActivityStatMini(label = "Calorías", value = "$calories kcal")
                ActivityStatMini(label = "Distancia", value = String.format(Locale.US, "%.2f km", km))
            }
        }
    }
}

@Composable
private fun ActivityStatMini(label: String, value: String) {
    Column {
        Text(label, color = TextMuted, fontSize = 11.sp)
        Text(value, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SleepHypnogramCard(
    totalMinutes: Int,
    deepMinutes: Int,
    lightMinutes: Int,
    remMinutes: Int,
    awakeMinutes: Int,
    dateStr: String?,
    sleepStatusText: String?,
    onClick: () -> Unit
) {
    val hours = totalMinutes / 60
    val mins = totalMinutes % 60
    val sum = max(1, deepMinutes + lightMinutes + remMinutes + awakeMinutes)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(18.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SleepDeep.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Bedtime, contentDescription = null, tint = SleepDeep, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text("Arquitectura del Sueño", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = dateStr ?: sleepStatusText ?: "Sin sesión sincronizada",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
                Text(
                    text = if (totalMinutes > 0) "${hours}h ${mins}m" else "--",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (totalMinutes > 0) {
                // Proportional 4-color high-contrast bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurfaceElevated)
                ) {
                    if (deepMinutes > 0) {
                        Box(
                            modifier = Modifier
                                .weight(deepMinutes.toFloat() / sum.toFloat())
                                .fillMaxHeight()
                                .background(SleepDeep)
                        )
                    }
                    if (lightMinutes > 0) {
                        Box(
                            modifier = Modifier
                                .weight(lightMinutes.toFloat() / sum.toFloat())
                                .fillMaxHeight()
                                .background(SleepLight)
                        )
                    }
                    if (remMinutes > 0) {
                        Box(
                            modifier = Modifier
                                .weight(remMinutes.toFloat() / sum.toFloat())
                                .fillMaxHeight()
                                .background(SleepRem)
                        )
                    }
                    if (awakeMinutes > 0) {
                        Box(
                            modifier = Modifier
                                .weight(awakeMinutes.toFloat() / sum.toFloat())
                                .fillMaxHeight()
                                .background(SleepAwake)
                        )
                    }
                }

                // 4-color legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SleepLegendChip("Profundo", "${deepMinutes}m", SleepDeep)
                    SleepLegendChip("Ligero", "${lightMinutes}m", SleepLight)
                    SleepLegendChip("REM", "${remMinutes}m", SleepRem)
                    SleepLegendChip("Vigilia", "${awakeMinutes}m", SleepAwake)
                }
            } else {
                Text(
                    text = "Toca para explorar tu historial de sueño por día, semana o mes.",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SleepLegendChip(label: String, duration: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(color)
        )
        Column {
            Text(label, color = TextMuted, fontSize = 10.sp)
            Text(duration, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun UnifiedHistoryDetailDialog(
    detailType: VitalDetailType,
    uiState: RingDashboardUiState,
    onDismiss: () -> Unit,
    onTriggerMeasurement: () -> Unit,
    onCancelMeasurement: () -> Unit
) {
    var selectedPeriod by remember { mutableStateOf(HistoryPeriodTab.DAY) }
    var selectedDayOffset by remember { mutableIntStateOf(0) }

    val title = when (detailType) {
        VitalDetailType.HEART_RATE -> "Pulso Cardíaco"
        VitalDetailType.SPO2 -> "Oxígeno en Sangre (SpO2)"
        VitalDetailType.BLOOD_PRESSURE -> "Presión Arterial"
        VitalDetailType.HRV -> "Variabilidad HRV"
        VitalDetailType.RESPIRATORY_RATE -> "Frecuencia Respiratoria"
        VitalDetailType.STEPS -> "Actividad y Pasos"
        VitalDetailType.SLEEP -> "Sueño e Hipnograma"
    }

    val accentColor = when (detailType) {
        VitalDetailType.HEART_RATE -> StatusRed
        VitalDetailType.SPO2 -> OxygenCyan
        VitalDetailType.BLOOD_PRESSURE -> TelatBlue
        VitalDetailType.HRV -> StatusGreen
        VitalDetailType.RESPIRATORY_RATE -> OxygenCyan
        VitalDetailType.STEPS -> TelatOrange
        VitalDetailType.SLEEP -> SleepIndigo
    }

    val canMeasure = detailType in listOf(
        VitalDetailType.HEART_RATE,
        VitalDetailType.SPO2,
        VitalDetailType.BLOOD_PRESSURE,
        VitalDetailType.HRV
    )

    val isMeasuringThis = when (detailType) {
        VitalDetailType.HEART_RATE -> uiState.isMeasuringHeartRate
        VitalDetailType.SPO2 -> uiState.isMeasuringSpo2
        VitalDetailType.BLOOD_PRESSURE -> uiState.isMeasuringBloodPressure
        VitalDetailType.HRV -> uiState.isMeasuringHrv
        else -> false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        SurfaceDialogContainer(
            title = title,
            accentColor = accentColor,
            onDismiss = onDismiss
        ) {
            // Period tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceElevated)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    HistoryPeriodTab.DAY to "Día",
                    HistoryPeriodTab.WEEK to "Semana",
                    HistoryPeriodTab.MONTH to "Mes"
                ).forEach { (tab, label) ->
                    val selected = selectedPeriod == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) accentColor.copy(alpha = 0.24f) else Color.Transparent)
                            .clickable {
                                selectedPeriod = tab
                                selectedDayOffset = 0
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (selected) accentColor else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            if (canMeasure) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (isMeasuringThis) {
                        OutlinedButton(onClick = onCancelMeasurement, shape = RoundedCornerShape(10.dp)) {
                            Text("Detener Medición", color = StatusRed, fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = onTriggerMeasurement,
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Medir Ahora", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            when (detailType) {
                VitalDetailType.SLEEP -> SleepPeriodContent(
                    sessions = uiState.sleepSessionsHistory,
                    period = selectedPeriod,
                    dayOffset = selectedDayOffset,
                    onChangeDayOffset = { selectedDayOffset = it }
                )

                VitalDetailType.STEPS -> StepsPeriodContent(
                    activityHistory = uiState.dailyActivityHistory,
                    stepGoal = uiState.stepGoal,
                    period = selectedPeriod,
                    dayOffset = selectedDayOffset,
                    onChangeDayOffset = { selectedDayOffset = it }
                )

                else -> {
                    val items = when (detailType) {
                        VitalDetailType.HEART_RATE -> uiState.heartRateHistory
                        VitalDetailType.SPO2 -> uiState.spo2History
                        VitalDetailType.BLOOD_PRESSURE -> uiState.bloodPressureHistory
                        VitalDetailType.HRV -> uiState.hrvHistory
                        VitalDetailType.RESPIRATORY_RATE -> uiState.respiratoryRateHistory
                        else -> emptyList()
                    }
                    val unit = when (detailType) {
                        VitalDetailType.HEART_RATE -> "BPM"
                        VitalDetailType.SPO2 -> "%"
                        VitalDetailType.BLOOD_PRESSURE -> "mmHg"
                        VitalDetailType.HRV -> "ms"
                        VitalDetailType.RESPIRATORY_RATE -> "rpm"
                        else -> ""
                    }
                    MetricPeriodContent(
                        items = items,
                        unit = unit,
                        accentColor = accentColor,
                        period = selectedPeriod,
                        dayOffset = selectedDayOffset,
                        onChangeDayOffset = { selectedDayOffset = it },
                        isBloodPressure = detailType == VitalDetailType.BLOOD_PRESSURE
                    )
                }
            }
        }
    }
}

@Composable
private fun SurfaceDialogContainer(
    title: String,
    accentColor: Color,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = TextSecondary)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun DateNavigatorRow(
    label: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, enabled = canGoBack) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Anterior",
                tint = if (canGoBack) TextPrimary else TextMuted.copy(alpha = 0.3f)
            )
        }
        Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onForward, enabled = canGoForward) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Siguiente",
                tint = if (canGoForward) TextPrimary else TextMuted.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun MetricPeriodContent(
    items: List<MetricHistoryItem>,
    unit: String,
    accentColor: Color,
    period: HistoryPeriodTab,
    dayOffset: Int,
    onChangeDayOffset: (Int) -> Unit,
    isBloodPressure: Boolean
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("es", "MX"))

    if (period == HistoryPeriodTab.DAY) {
        val targetDate = today.plusDays(dayOffset.toLong())
        val dayItems = items.filter {
            Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() == targetDate
        }.sortedBy { it.timestampMillis }

        DateNavigatorRow(
            label = if (dayOffset == 0) "Hoy · ${targetDate.format(formatter)}" else targetDate.format(formatter),
            canGoBack = dayOffset > -29,
            canGoForward = dayOffset < 0,
            onBack = { onChangeDayOffset(dayOffset - 1) },
            onForward = { onChangeDayOffset(dayOffset + 1) }
        )

        if (dayItems.isEmpty()) {
            EmptyHistoryCard("Sin registros para este día.")
        } else {
            val minVal = dayItems.minOf { it.primaryValue.toInt() }
            val maxVal = dayItems.maxOf { it.primaryValue.toInt() }
            val avgVal = dayItems.map { it.primaryValue.toDouble() }.average().roundToInt()

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatChipCard(Modifier.weight(1f), "Mínimo", "$minVal $unit", accentColor)
                StatChipCard(Modifier.weight(1f), "Promedio", "$avgVal $unit", accentColor)
                StatChipCard(Modifier.weight(1f), "Máximo", "$maxVal $unit", accentColor)
            }

            MetricLineChartCard(items = dayItems, accentColor = accentColor)

            Text("Lecturas (${dayItems.size})", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            dayItems.reversed().forEach { item ->
                val valueStr = if (isBloodPressure && item.secondaryValue != null) {
                    "${item.primaryValue.toInt()}/${item.secondaryValue.toInt()} $unit"
                } else {
                    "${item.primaryValue.toInt()} $unit"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurface)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.formattedTime, color = TextSecondary, fontSize = 13.sp)
                    Text(valueStr, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        val daysCount = if (period == HistoryPeriodTab.WEEK) 7 else 30
        val startDate = today.minusDays((daysCount - 1).toLong())
        val aggregates = (0 until daysCount).map { idx ->
            val d = startDate.plusDays(idx.toLong())
            val dayVals = items.filter {
                Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() == d
            }
            DailyMetricAggregate(
                dateKey = d.toString(),
                shortDateLabel = d.format(DateTimeFormatter.ofPattern("dd/MM")),
                avgValue = if (dayVals.isNotEmpty()) dayVals.map { it.primaryValue.toDouble() }.average().toFloat() else 0f,
                minValue = dayVals.minOfOrNull { it.primaryValue } ?: 0f,
                maxValue = dayVals.maxOfOrNull { it.primaryValue } ?: 0f,
                secondaryAvg = null,
                sampleCount = dayVals.size,
                unit = unit
            )
        }
        val nonZero = aggregates.filter { it.sampleCount > 0 }
        if (nonZero.isEmpty()) {
            EmptyHistoryCard("Sin datos acumulados en este periodo.")
        } else {
            DailyAggregateBarChart(aggregates = aggregates, accentColor = accentColor, unit = unit)
            nonZero.reversed().forEach { agg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurface)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(agg.shortDateLabel, color = TextSecondary, fontSize = 13.sp)
                    Text(
                        "Prom ${agg.avgValue.roundToInt()} · (${agg.minValue.roundToInt()}-${agg.maxValue.roundToInt()}) $unit",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricLineChartCard(items: List<MetricHistoryItem>, accentColor: Color) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .padding(16.dp)
        ) {
            if (items.isEmpty()) return@Canvas
            val minV = items.minOf { it.primaryValue }
            val maxV = items.maxOf { it.primaryValue }
            val span = max(1f, maxV - minV)
            val stepX = if (items.size > 1) size.width / (items.size - 1) else size.width

            val path = Path()
            items.forEachIndexed { idx, item ->
                val x = if (items.size > 1) idx * stepX else size.width / 2f
                val normY = (item.primaryValue - minV) / span
                val y = size.height - (normY * size.height * 0.85f + size.height * 0.08f)
                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = accentColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
            items.forEachIndexed { idx, item ->
                val x = if (items.size > 1) idx * stepX else size.width / 2f
                val normY = (item.primaryValue - minV) / span
                val y = size.height - (normY * size.height * 0.85f + size.height * 0.08f)
                drawCircle(color = accentColor, radius = 4.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

@Composable
private fun DailyAggregateBarChart(
    aggregates: List<DailyMetricAggregate>,
    accentColor: Color,
    unit: String
) {
    val maxVal = max(1f, aggregates.maxOf { it.maxValue })
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tendencia ($unit)", color = TextSecondary, fontSize = 12.sp)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                val barW = (size.width / aggregates.size.coerceAtLeast(1)) * 0.62f
                val slotW = size.width / aggregates.size.coerceAtLeast(1)
                aggregates.forEachIndexed { index, agg ->
                    if (agg.avgValue > 0f) {
                        val barH = (agg.avgValue / maxVal) * size.height * 0.9f
                        drawRoundRect(
                            color = accentColor,
                            topLeft = Offset(index * slotW + (slotW - barW) / 2f, size.height - barH),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

private fun formatTimestampShort(timestampMs: Long?): String? {
    if (timestampMs == null || timestampMs <= 0L) return null
    val fmt = SimpleDateFormat("HH:mm · dd/MM", Locale.getDefault())
    return fmt.format(Date(timestampMs))
}

@Composable
private fun StepsPeriodContent(
    activityHistory: List<DailyActivitySummary>,
    stepGoal: Int,
    period: HistoryPeriodTab,
    dayOffset: Int,
    onChangeDayOffset: (Int) -> Unit
) {
    val today = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("es", "MX"))

    if (period == HistoryPeriodTab.DAY) {
        val targetDate = today.plusDays(dayOffset.toLong())
        val targetKey = targetDate.toString()
        val daySummary = activityHistory.find { it.dateKey == targetKey }

        DateNavigatorRow(
            label = if (dayOffset == 0) "Hoy · ${targetDate.format(formatter)}" else targetDate.format(formatter),
            canGoBack = dayOffset > -29,
            canGoForward = dayOffset < 0,
            onBack = { onChangeDayOffset(dayOffset - 1) },
            onForward = { onChangeDayOffset(dayOffset + 1) }
        )

        if (daySummary == null) {
            EmptyHistoryCard("Sin datos de actividad para este día.")
        } else {
            val pct = if (stepGoal > 0) (daySummary.steps * 100f / stepGoal).roundToInt() else 0
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatChipCard(Modifier.weight(1f), "Pasos", "${daySummary.steps}", TelatOrange)
                StatChipCard(Modifier.weight(1f), "Calorías", "${daySummary.calories} kcal", StatusAmber)
                StatChipCard(Modifier.weight(1f), "Meta", "$pct%", StatusGreen)
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Distancia recorrida: ${String.format(Locale.US, "%.2f", daySummary.distanceMeters / 1000f)} km",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    LinearProgressIndicator(
                        progress = { (daySummary.steps.toFloat() / max(1, stepGoal)).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = TelatOrange,
                        trackColor = DarkSurfaceElevated
                    )
                }
            }
        }
    } else {
        val daysCount = if (period == HistoryPeriodTab.WEEK) 7 else 30
        val slice = activityHistory.takeLast(daysCount)
        if (slice.isEmpty()) {
            EmptyHistoryCard("Sin historial de actividad en el periodo.")
        } else {
            val maxSteps = max(stepGoal, slice.maxOf { it.steps })
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkBorder, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(16.dp)
                ) {
                    val slotW = size.width / slice.size.coerceAtLeast(1)
                    val barW = slotW * 0.62f
                    slice.forEachIndexed { idx, item ->
                        val barH = (item.steps.toFloat() / maxSteps) * size.height * 0.92f
                        drawRoundRect(
                            color = if (item.steps >= stepGoal) StatusGreen else TelatOrange,
                            topLeft = Offset(idx * slotW + (slotW - barW) / 2f, size.height - barH),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }
            }
            slice.reversed().forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurface)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(item.shortDateLabel, color = TextSecondary, fontSize = 13.sp)
                    Text("${item.steps} pasos · ${item.calories} kcal", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SleepPeriodContent(
    sessions: List<SleepSessionSummaryRecord>,
    period: HistoryPeriodTab,
    dayOffset: Int,
    onChangeDayOffset: (Int) -> Unit
) {
    if (sessions.isEmpty()) {
        EmptyHistoryCard("Aún no hay sesiones de sueño registradas.")
        return
    }

    if (period == HistoryPeriodTab.DAY) {
        val sorted = sessions.sortedByDescending { it.endTimeMs }
        val index = (-dayOffset).coerceIn(0, sorted.lastIndex)
        val session = sorted[index]

        DateNavigatorRow(
            label = "${session.shortDateLabel} · ${session.dateRangeStr}",
            canGoBack = index < sorted.lastIndex,
            canGoForward = index > 0,
            onBack = { onChangeDayOffset(dayOffset - 1) },
            onForward = { onChangeDayOffset(dayOffset + 1) }
        )

        val hours = session.totalMinutes / 60
        val mins = session.totalMinutes % 60
        val sleepDenominator = max(1, session.totalMinutes + session.awakeMinutes)
        val efficiencyPct = ((session.totalMinutes.toFloat() / sleepDenominator.toFloat()) * 100f).roundToInt()

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatChipCard(Modifier.weight(1f), "Total Sueño", "${hours}h ${mins}m", SleepLight)
            StatChipCard(Modifier.weight(1f), "Eficiencia", "$efficiencyPct%", SleepRem)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChipCard(Modifier.weight(1f), "Profundo", "${session.deepMinutes}m", SleepDeep)
            StatChipCard(Modifier.weight(1f), "Ligero", "${session.lightMinutes}m", SleepLight)
            StatChipCard(Modifier.weight(1f), "REM", "${session.remMinutes}m", SleepRem)
            StatChipCard(Modifier.weight(1f), "Vigilia", "${session.awakeMinutes}m", SleepAwake)
        }

        // Stepped Temporal Hypnogram Canvas
        SleepHypnogramChart(session = session)

        Text("Segmentos de la Noche (${session.stages.size})", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        session.stages.forEach { stage ->
            val stageColor = stageColorForName(stage.stageName)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(stageColor)
                    )
                    Column {
                        Text(stage.stageName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Inicio ${stage.startTimeFormatted}", color = TextMuted, fontSize = 11.sp)
                    }
                }
                Text("${stage.durationMinutes} min", color = stageColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        val daysCount = if (period == HistoryPeriodTab.WEEK) 7 else 30
        val slice = sessions.sortedBy { it.endTimeMs }.takeLast(daysCount)

        SleepStackedBarChart(sessions = slice)

        slice.reversed().forEach { s ->
            val h = s.totalMinutes / 60
            val m = s.totalMinutes % 60
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(s.shortDateLabel, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Prof ${s.deepMinutes}m · Lig ${s.lightMinutes}m · REM ${s.remMinutes}m · Vig ${s.awakeMinutes}m",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
                Text("${h}h ${m}m", color = SleepLight, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SleepHypnogramChart(session: SleepSessionSummaryRecord) {
    val stages = session.stages
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val startClock = timeFmt.format(Date(session.startTimeMs))
    val endClock = timeFmt.format(Date(session.endTimeMs))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Hipnograma Temporal", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

            if (stages.isEmpty()) {
                Text("Sin detalle de segmentos para graficar.", color = TextMuted, fontSize = 12.sp)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.height(148.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Vigilia", color = SleepAwake, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Text("REM", color = SleepRem, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Text("Ligero", color = SleepLight, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                        Text("Profundo", color = SleepDeep, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .height(148.dp)
                    ) {
                        val rowHeight = size.height / 4f
                        val barHeight = rowHeight * 0.64f
                        val totalDur = max(1, stages.sumOf { max(1, it.durationMinutes) }).toFloat()
                        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

                        for (r in 0..3) {
                            val centerY = r * rowHeight + rowHeight / 2f
                            drawLine(
                                color = DarkBorderHighlight.copy(alpha = 0.45f),
                                start = Offset(0f, centerY),
                                end = Offset(size.width, centerY),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = dash
                            )
                        }

                        var cursorX = 0f
                        stages.forEach { st ->
                            val w = (max(1, st.durationMinutes).toFloat() / totalDur) * size.width
                            val rowIdx = when {
                                st.stageName.contains("Vigilia", ignoreCase = true) -> 0
                                st.stageName.contains("REM", ignoreCase = true) -> 1
                                st.stageName.contains("Ligero", ignoreCase = true) -> 2
                                else -> 3
                            }
                            val topY = rowIdx * rowHeight + (rowHeight - barHeight) / 2f
                            drawRoundRect(
                                color = stageColorForName(st.stageName),
                                topLeft = Offset(cursorX, topY),
                                size = Size(max(2f, w - 1f), barHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                            cursorX += w
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(startClock, color = TextMuted, fontSize = 11.sp)
                    Text(endClock, color = TextMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun SleepStackedBarChart(sessions: List<SleepSessionSummaryRecord>) {
    val maxTotal = max(1, sessions.maxOf { it.deepMinutes + it.lightMinutes + it.remMinutes + it.awakeMinutes })

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Comparativa de Fases por Noche", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            ) {
                val slotW = size.width / sessions.size.coerceAtLeast(1)
                val barW = slotW * 0.58f
                sessions.forEachIndexed { idx, s ->
                    val x = idx * slotW + (slotW - barW) / 2f
                    var bottomY = size.height
                    val segments = listOf(
                        s.deepMinutes to SleepDeep,
                        s.lightMinutes to SleepLight,
                        s.remMinutes to SleepRem,
                        s.awakeMinutes to SleepAwake
                    )
                    segments.forEach { (mins, color) ->
                        if (mins > 0) {
                            val h = (mins.toFloat() / maxTotal.toFloat()) * size.height * 0.92f
                            drawRect(
                                color = color,
                                topLeft = Offset(x, bottomY - h),
                                size = Size(barW, h)
                            )
                            bottomY -= h
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SleepLegendChip("Profundo", "", SleepDeep)
                SleepLegendChip("Ligero", "", SleepLight)
                SleepLegendChip("REM", "", SleepRem)
                SleepLegendChip("Vigilia", "", SleepAwake)
            }
        }
    }
}

private fun stageColorForName(stageName: String): Color {
    return when {
        stageName.contains("Profundo", ignoreCase = true) -> SleepDeep
        stageName.contains("Ligero", ignoreCase = true) -> SleepLight
        stageName.contains("REM", ignoreCase = true) -> SleepRem
        else -> SleepAwake
    }
}

@Composable
private fun StatChipCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accentColor: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label, color = TextMuted, fontSize = 10.sp)
        Text(value, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyHistoryCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = message,
            color = TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(18.dp)
        )
    }
}

@Composable
private fun DeviceSettingsTab(
    uiState: RingDashboardUiState,
    viewModel: RingViewModel,
    onRequestHealthPermissions: () -> Unit,
    onOpenHealthSettings: () -> Unit
) {
    val context = LocalContext.current
    var showFactoryResetDialog by remember { mutableStateOf(false) }
    var exportFeedback by remember { mutableStateOf<String?>(null) }

    var editHeight by remember(uiState.userHeightCm) { mutableIntStateOf(uiState.userHeightCm) }
    var editWeight by remember(uiState.userWeightKg) { mutableIntStateOf(uiState.userWeightKg) }
    var editAge by remember(uiState.userAge) { mutableIntStateOf(uiState.userAge) }
    var editSex by remember(uiState.userSex) { mutableIntStateOf(uiState.userSex) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hardware & Database Summary
        SettingsSectionCard(title = "Hardware y Base de Datos", icon = Icons.Filled.Tune) {
            InfoRow("Modelo", uiState.hardwareModel ?: "Smart Ring R99")
            InfoRow("Firmware", uiState.firmwareVersion ?: "--")
            InfoRow("MAC BLE", uiState.deviceAddress ?: "--")
            InfoRow("Capacidades Reales", uiState.supportedCapabilitiesSummary ?: "Sin detectar")
            InfoRow(
                "SQLite Local",
                "${uiState.syncedHistoryRecordsCount} métricas · ${uiState.syncedSleepSessionsCount} noches"
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.refreshBattery() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Batería", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { viewModel.forceSyncNow() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sincronizar", fontSize = 12.sp)
                }
            }
        }

        // User Profile Steppers
        SettingsSectionCard(title = "Perfil Biométrico (0x01 0x03)", icon = Icons.Filled.Settings) {
            StepperRow(
                label = "Estatura",
                valueText = "$editHeight cm",
                onMinus = { if (editHeight > 100) editHeight-- },
                onPlus = { if (editHeight < 230) editHeight++ }
            )
            StepperRow(
                label = "Peso",
                valueText = "$editWeight kg",
                onMinus = { if (editWeight > 30) editWeight-- },
                onPlus = { if (editWeight < 200) editWeight++ }
            )
            StepperRow(
                label = "Edad",
                valueText = "$editAge años",
                onMinus = { if (editAge > 12) editAge-- },
                onPlus = { if (editAge < 100) editAge++ }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sexo", color = TextSecondary, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChipPill("Masculino", editSex == 1) { editSex = 1 }
                    ChoiceChipPill("Femenino", editSex == 0) { editSex = 0 }
                }
            }
            Button(
                onClick = { viewModel.updateUserProfile(editHeight, editWeight, editAge, editSex) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TelatBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Guardar Perfil en Anillo", fontWeight = FontWeight.SemiBold)
            }
        }

        // Monitoring Interval & Step Goal
        SettingsSectionCard(title = "Monitoreo Automático y Metas", icon = Icons.Filled.LocalFireDepartment) {
            Text("Intervalo de Medición Continua", color = TextSecondary, fontSize = 12.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(10, 20, 30, 60).forEach { mins ->
                    ChoiceChipPill(
                        label = "${mins} min",
                        selected = uiState.monitoringIntervalMinutes == mins,
                        onClick = { viewModel.setMonitoringInterval(mins) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("Meta Diaria de Pasos", color = TextSecondary, fontSize = 12.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(6000, 8000, 10000, 12000, 15000).forEach { goal ->
                    ChoiceChipPill(
                        label = "${goal / 1000}k",
                        selected = uiState.stepGoal == goal,
                        onClick = { viewModel.setStepGoal(goal) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sincronización Periódica en App", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Mantiene telemetría al día mientras está conectado", color = TextMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = uiState.isPeriodicSyncEnabled,
                    onCheckedChange = { viewModel.togglePeriodicSync(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = TelatBlue)
                )
            }
        }

        // Blood Pressure Calibration
        SettingsSectionCard(title = "Calibración de Presión Arterial (0x03 0x03)", icon = Icons.Filled.Speed) {
            Text(
                "Ingresa una referencia de baumanómetro estándar para calibrar el algoritmo PPG del anillo:",
                color = TextMuted,
                fontSize = 12.sp
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(110 to 70, 120 to 80, 130 to 85).forEach { (sys, dia) ->
                    OutlinedButton(
                        onClick = { viewModel.calibrateBloodPressure(sys, dia) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("$sys/$dia", fontSize = 12.sp, color = TextPrimary)
                    }
                }
            }
        }

        // Health Connect & Export
        SettingsSectionCard(title = "Health Connect y Exportación", icon = Icons.Filled.CheckCircle) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uiState.hasHealthPermissions) "Permisos de Health Connect activos" else "Permisos pendientes",
                    color = if (uiState.hasHealthPermissions) StatusGreen else StatusAmber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                TextButton(
                    onClick = if (uiState.hasHealthPermissions) onOpenHealthSettings else onRequestHealthPermissions
                ) {
                    Text(if (uiState.hasHealthPermissions) "Abrir Ajustes" else "Conceder")
                }
            }

            OutlinedButton(
                onClick = {
                    val path = viewModel.exportSessionCsv()
                    if (path != null) {
                        exportFeedback = "CSV guardado en: $path"
                        runCatching {
                            val file = File(path)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Compartir Telemetría R99"))
                        }
                    } else {
                        exportFeedback = "Sin datos suficientes para exportar CSV."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Exportar Historial CSV")
            }

            exportFeedback?.let {
                Text(it, color = TextMuted, fontSize = 11.sp)
            }
        }

        // Maintenance & Factory Reset
        SettingsSectionCard(title = "Mantenimiento del Anillo", icon = Icons.Filled.DeleteSweep) {
            OutlinedButton(
                onClick = { viewModel.clearRingFlashMemory() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Liberar Buffer Flash del Anillo", color = StatusAmber)
            }

            OutlinedButton(
                onClick = { showFactoryResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null, tint = StatusRed, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Restaurar Anillo a Valores de Fábrica (0x01 0x0E)", color = StatusRed, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showFactoryResetDialog) {
        AlertDialog(
            onDismissRequest = { showFactoryResetDialog = false },
            containerColor = DarkSurface,
            title = { Text("Confirmar Reset de Fábrica", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Se enviará el comando 0x01 0x0E al anillo R99 para restaurar sus parámetros de fábrica y limpiar su memoria interna. Tu historial local en SQLite permanecerá intacto.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFactoryResetDialog = false
                        viewModel.restoreRingFactorySettings()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                ) {
                    Text("Restaurar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFactoryResetDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = TelatBlue, modifier = Modifier.size(18.dp))
                Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StepperRow(
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceElevated)
                    .clickable { onMinus() },
                contentAlignment = Alignment.Center
            ) {
                Text("-", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Text(valueText, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceElevated)
                    .clickable { onPlus() },
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun ChoiceChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) TelatBlue.copy(alpha = 0.22f) else DarkSurfaceElevated)
            .border(1.dp, if (selected) TelatBlue else DarkBorder, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (selected) TelatBlue else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
