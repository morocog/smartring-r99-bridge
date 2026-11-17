package com.telat.smartring.r99.ui

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telat.smartring.r99.data.model.BleConnectionState
import com.telat.smartring.r99.data.model.RingDashboardUiState
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
import com.telat.smartring.r99.ui.theme.StatusAmber
import com.telat.smartring.r99.ui.theme.StatusGreen
import com.telat.smartring.r99.ui.theme.StatusRed
import com.telat.smartring.r99.ui.theme.TelatBlue
import com.telat.smartring.r99.ui.theme.TelatOrange
import com.telat.smartring.r99.ui.theme.TelatYellow
import com.telat.smartring.r99.ui.theme.TextMuted
import com.telat.smartring.r99.ui.theme.TextPrimary
import com.telat.smartring.r99.ui.theme.TextSecondary
import com.telat.smartring.r99.ui.theme.ThermalGold
import com.telat.smartring.r99.ui.theme.TitaniumRingDark
import com.telat.smartring.r99.ui.theme.TitaniumRingLight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RingDashboardScreen(
    viewModel: RingViewModel,
    onRequestHealthPermissions: () -> Unit,
    onOpenHealthSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    val isAnyMeasuring = uiState.isMeasuringHeartRate ||
            uiState.isMeasuringSpo2 ||
            uiState.isMeasuringBloodPressure ||
            uiState.isMeasuringTemperature

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 18.dp, vertical = 20.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Ring 3D/Canvas interactivo con aura bioluminiscente
        HeroRingSection(
            deviceName = uiState.deviceName,
            deviceAddress = uiState.deviceAddress,
            connectionState = uiState.connectionState,
            batteryPercent = uiState.batteryPercent,
            isCharging = uiState.isCharging,
            isMeasuring = isAnyMeasuring,
            currentBpm = uiState.currentHeartRate,
            onRefreshBattery = { viewModel.refreshBattery() },
            onToggleConnect = {
                if (uiState.connectionState == BleConnectionState.CONNECTED) {
                    viewModel.disconnectRing()
                } else {
                    viewModel.startScanningAndConnect()
                }
            }
        )

        // 2. Alerta de sensor sin contacto con la piel (Off-wrist)
        if (uiState.isOffWristAlert) {
            OffWristWarningBanner()
        }

        // 3. Card de Integración Health Connect & Samsung Health
        if (!uiState.hasHealthPermissions) {
            HealthConnectBanner(
                onRequestPermissions = onRequestHealthPermissions,
                onOpenSettings = onOpenHealthSettings
            )
        } else {
            HealthConnectStatusCard(onOpenSettings = onOpenHealthSettings)
        }

        // 4. Bento Grid de Constantes Vitales (HR, SpO2, Presión, Temperatura)
        Text(
            text = "CONSTANTES VITALES EN TIEMPO REAL",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = TextMuted,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExecutiveVitalCard(
                title = "Pulso Cardíaco",
                value = if (uiState.currentHeartRate > 0) "${uiState.currentHeartRate}" else "--",
                unit = "BPM",
                lastRecordedTime = uiState.lastHeartRateTime,
                icon = Icons.Default.Favorite,
                accentColor = StatusRed,
                isMeasuring = uiState.isMeasuringHeartRate,
                measuringText = "Midiendo pulso...",
                buttonText = "Medir ahora",
                onMeasureClick = { viewModel.triggerHeartRateMeasurement() },
                modifier = Modifier.weight(1f)
            )

            ExecutiveVitalCard(
                title = "Oxígeno SpO2",
                value = if (uiState.currentSpo2 != null && uiState.currentSpo2!! > 0) "${uiState.currentSpo2}%" else "--",
                unit = "Sat.",
                lastRecordedTime = uiState.lastSpo2Time,
                icon = Icons.Default.WaterDrop,
                accentColor = OxygenCyan,
                isMeasuring = uiState.isMeasuringSpo2,
                measuringText = "Midiendo SpO2...",
                buttonText = "Medir SpO2",
                onMeasureClick = { viewModel.triggerSpo2Measurement() },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExecutiveVitalCard(
                title = "Presión Arterial",
                value = if (uiState.currentBloodPressureSys != null && uiState.currentBloodPressureSys!! > 0)
                    "${uiState.currentBloodPressureSys}/${uiState.currentBloodPressureDia}" else "--",
                unit = "mmHg",
                lastRecordedTime = uiState.lastBloodPressureTime,
                icon = Icons.Default.Speed,
                accentColor = StatusAmber,
                isMeasuring = uiState.isMeasuringBloodPressure,
                measuringText = "Estimando...",
                buttonText = "Medir presión",
                onMeasureClick = { viewModel.triggerBloodPressureMeasurement() },
                modifier = Modifier.weight(1f)
            )

            ExecutiveVitalCard(
                title = "Temperatura Dérmica",
                value = if (uiState.currentTemperatureCelsius != null && uiState.currentTemperatureCelsius!! > 0f)
                    "${String.format(Locale.getDefault(), "%.1f", uiState.currentTemperatureCelsius)}°" else "--",
                unit = "°C",
                lastRecordedTime = uiState.lastTemperatureTime,
                icon = Icons.Default.Thermostat,
                accentColor = ThermalGold,
                isMeasuring = uiState.isMeasuringTemperature,
                measuringText = "Leyendo sensor...",
                buttonText = "Medir temp",
                onMeasureClick = { viewModel.triggerTemperatureMeasurement() },
                modifier = Modifier.weight(1f)
            )
        }

        // 5. Anillos Concéntricos de Actividad Diaria
        Text(
            text = "ACTIVIDAD DEL DÍA",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = TextMuted,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        ActivityRingsCard(
            steps = uiState.totalStepsToday,
            stepGoal = 10000,
            calories = uiState.totalCaloriesToday,
            calorieGoal = 600,
            distanceMeters = uiState.totalDistanceMetersToday,
            isMoving = uiState.isMoving
        )

        // 6. Hipnograma Científico de Sueño
        Text(
            text = "ANÁLISIS POLISOMNOGRÁFICO DE SUEÑO",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = TextMuted,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        SleepHypnogramCard(
            totalMinutes = uiState.lastSleepTotalMinutes,
            deepMinutes = uiState.lastSleepDeepMinutes,
            lightMinutes = uiState.lastSleepLightMinutes,
            remMinutes = uiState.lastSleepRemMinutes,
            awakeMinutes = uiState.lastSleepAwakeMinutes,
            sleepDateStr = uiState.lastSleepDateStr,
            syncedSessions = uiState.syncedSleepSessionsCount,
            sleepStatusText = uiState.sleepStatusText
        )

        // 7. Panel de Control y Sincronización
        ExecutiveControlsSection(
            uiState = uiState,
            onForceSync = { viewModel.forceSyncNow() },
            onTogglePeriodicSync = { viewModel.togglePeriodicSync(it) },
            onExportCsv = { viewModel.exportSessionCsv() }
        )

        // 8. Pie de estado del sistema
        StatusFooter(
            message = uiState.statusMessage,
            lastSync = uiState.lastSyncTimestamp,
            historyCount = uiState.syncedHistoryRecordsCount
        )

        Spacer(modifier = Modifier.height(18.dp))
    }
}

/**
 * 1. Hero Ring Section: Componente visual insignia con Canvas, efecto de halo
 * respiratorio durante medición y telemetría de hardware integrada.
 */
@Composable
private fun HeroRingSection(
    deviceName: String,
    deviceAddress: String,
    connectionState: BleConnectionState,
    batteryPercent: Int?,
    isCharging: Boolean,
    isMeasuring: Boolean,
    currentBpm: Int,
    onRefreshBattery: () -> Unit,
    onToggleConnect: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RingPulseTransition")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (isMeasuring || connectionState == BleConnectionState.SCANNING) 0.95f else 0.50f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isMeasuring) 800 else 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlowAnim"
    )

    val (statusLabel, statusColor) = when (connectionState) {
        BleConnectionState.CONNECTED -> "Conectado" to StatusGreen
        BleConnectionState.CONNECTING, BleConnectionState.SYNCING -> "Sincronizando" to StatusAmber
        BleConnectionState.SCANNING -> "Escaneando" to OxygenCyan
        BleConnectionState.DISCONNECTED -> "Desconectado" to TextMuted
        BleConnectionState.ERROR -> "Fallo BLE" to StatusRed
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Fila superior: Nombre de hardware + Botón de Conexión / Estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Smart Ring R99",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (deviceAddress.isNotBlank()) "$deviceName • ${deviceAddress.takeLast(8)}" else "Buscando hardware...",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                // Píldora de estado interactiva (pulsar para conectar/desconectar)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurfaceElevated)
                        .clickable { onToggleConnect() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Canvas del Anillo Insignia (Iluminación circular biométrica)
            Box(
                modifier = Modifier.size(190.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val outerRadius = (size.minDimension / 2f) - 10.dp.toPx()
                    val strokeWidth = 14.dp.toPx()

                    // 1. Halo difuso exterior (Glow)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                if (isMeasuring) StatusRed.copy(alpha = pulseGlow * 0.45f)
                                else RingGlowActive.copy(alpha = pulseGlow * 0.25f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = outerRadius + 20.dp.toPx()
                        ),
                        radius = outerRadius + 20.dp.toPx(),
                        center = center
                    )

                    // 2. Chasis del anillo (Titanio oscuro texturizado)
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                TitaniumRingDark,
                                TitaniumRingLight,
                                TitaniumRingDark,
                                TitaniumRingLight,
                                TitaniumRingDark
                            ),
                            center = center
                        ),
                        radius = outerRadius,
                        center = center,
                        style = Stroke(width = strokeWidth)
                    )

                    // 3. Arco de batería / telemetría
                    val batteryRatio = ((batteryPercent ?: 0) / 100f).coerceIn(0f, 1f)
                    val arcColor = when {
                        isCharging -> OxygenCyan
                        batteryRatio > 0.4f -> StatusGreen
                        batteryRatio > 0.15f -> StatusAmber
                        else -> StatusRed
                    }

                    if (connectionState == BleConnectionState.CONNECTED && batteryPercent != null) {
                        drawArc(
                            color = arcColor,
                            startAngle = -90f,
                            sweepAngle = 360f * batteryRatio,
                            useCenter = false,
                            topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                            size = Size(outerRadius * 2f, outerRadius * 2f),
                            style = Stroke(width = strokeWidth * 0.55f, cap = StrokeCap.Round)
                        )
                    }
                }

                // Centro del Anillo: Núcleo de información vital
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isMeasuring && currentBpm > 0) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = StatusRed,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "$currentBpm",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                        Text(
                            text = "BPM EN VIVO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusRed
                        )
                    } else {
                        val batText = if (batteryPercent != null) {
                            if (isCharging) "⚡ $batteryPercent%" else "$batteryPercent%"
                        } else "--%"

                        Text(
                            text = batText,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onRefreshBattery() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Batería",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refrescar",
                                tint = TextMuted,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Subtítulo de sensor de piel
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BluetoothConnected,
                    contentDescription = null,
                    tint = OxygenCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isMeasuring) "Sensor óptico infrarrojo & termistor activos"
                    else "Sincronización biométrica directa con Galaxy S24",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * 2. Tarjeta Bento Ejecutiva de Constante Vital con Botón de Medición y Lectura Rápida
 */
@Composable
private fun ExecutiveVitalCard(
    title: String,
    value: String,
    unit: String,
    lastRecordedTime: Long?,
    icon: ImageVector,
    accentColor: Color,
    isMeasuring: Boolean,
    measuringText: String,
    buttonText: String,
    onMeasureClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .border(1.dp, if (isMeasuring) accentColor.copy(alpha = 0.6f) else DarkBorder, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }

            val timeFormatted = if (lastRecordedTime != null && lastRecordedTime > 0) {
                val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                "Último: ${format.format(Date(lastRecordedTime))}"
            } else {
                "Sin lectura hoy"
            }
            Text(
                text = timeFormatted,
                fontSize = 11.sp,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Botón interactivo de inicio / estado de medición
            if (isMeasuring) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.20f))
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = accentColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = measuringText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onMeasureClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = accentColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = buttonText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * 3. Tarjeta de Anillos Concéntricos de Actividad (Pasos, Calorías, Distancia)
 */
@Composable
private fun ActivityRingsCard(
    steps: Int,
    stepGoal: Int,
    calories: Int,
    calorieGoal: Int,
    distanceMeters: Int,
    isMoving: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DirectionsRun,
                        contentDescription = null,
                        tint = StatusGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pasos: ${String.format(Locale.getDefault(), "%,d", steps)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Text(
                    text = "Meta diaria: ${String.format(Locale.getDefault(), "%,d", stepGoal)} (${(steps * 100 / stepGoal.coerceAtLeast(1))}%)",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 24.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = TelatOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$calories kcal quemadas",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }

                val km = distanceMeters / 1000.0
                Text(
                    text = "Distancia: ${String.format(Locale.getDefault(), "%.2f", km)} km",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 24.dp)
                )
            }

            // Gráfico Circular de Progreso de Pasos
            Box(
                modifier = Modifier.size(76.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 7.dp.toPx()
                    val progress = (steps.toFloat() / stepGoal.toFloat()).coerceIn(0f, 1f)

                    // Track base
                    drawCircle(
                        color = DarkSurfaceElevated,
                        style = Stroke(width = stroke)
                    )

                    // Arco de progreso
                    drawArc(
                        brush = Brush.sweepGradient(listOf(StatusGreen, TelatBlue, StatusGreen)),
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${((steps.toFloat() / stepGoal.toFloat()) * 100).toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isMoving) "ACTIVO" else "PASIVO",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isMoving) StatusGreen else TextMuted
                    )
                }
            }
        }
    }
}

/**
 * 4. Hipnograma Científico de Sueño (Desglose polisomnográfico proporcional)
 */
@Composable
private fun SleepHypnogramCard(
    totalMinutes: Int,
    deepMinutes: Int,
    lightMinutes: Int,
    remMinutes: Int,
    awakeMinutes: Int,
    sleepDateStr: String,
    syncedSessions: Int,
    sleepStatusText: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SleepIndigo.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = "Sueño",
                            tint = SleepIndigo,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (totalMinutes > 0) "${totalMinutes / 60}h ${totalMinutes % 60}m" else "Sin sesión",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (sleepDateStr.isNotEmpty()) sleepDateStr else sleepStatusText,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                if (syncedSessions > 0) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceElevated)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$syncedSessions sesiones en Samsung Health",
                            fontSize = 11.sp,
                            color = SleepLight
                        )
                    }
                }
            }

            if (totalMinutes > 0) {
                val sumStages = (deepMinutes + lightMinutes + remMinutes + awakeMinutes).coerceAtLeast(1).toFloat()

                // Barra continua del hipnograma (proporcional por fases)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(DarkSurfaceElevated)
                ) {
                    if (deepMinutes > 0) {
                        Box(
                            modifier = Modifier
                                .weight(deepMinutes / sumStages)
                                .fillMaxSize()
                                .background(SleepDeep)
                        )
                    }
                    if (lightMinutes > 0) {
                        Box(
                            modifier = Modifier
                                .weight(lightMinutes / sumStages)
                                .fillMaxSize()
                                .background(SleepLight)
                        )
                    }
                    if (remMinutes > 0) {
                        Box(
                            modifier = Modifier
                                .weight(remMinutes / sumStages)
                                .fillMaxSize()
                                .background(SleepIndigo)
                        )
                    }
                    if (awakeMinutes > 0) {
                        Box(
                            modifier = Modifier
                                .weight(awakeMinutes / sumStages)
                                .fillMaxSize()
                                .background(SleepAwake)
                        )
                    }
                }

                // Leyenda de Fases con minutos exactos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HypnoStageTag("Profundo", deepMinutes, SleepDeep)
                    HypnoStageTag("Ligero", lightMinutes, SleepLight)
                    HypnoStageTag("REM", remMinutes, SleepIndigo)
                    HypnoStageTag("Vigilia", awakeMinutes, SleepAwake)
                }
            } else {
                Text(
                    text = "El sensor analiza automáticamente tus ciclos de sueño al dormir con el anillo puesto.",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun HypnoStageTag(name: String, minutes: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = name, fontSize = 10.sp, color = TextSecondary)
        }
        Text(text = "${minutes}m", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

/**
 * 5. Card de Estado de Health Connect / Samsung Health
 */
@Composable
private fun HealthConnectStatusCard(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Activo",
                    tint = StatusGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Samsung Health & Health Connect",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusGreen
                    )
                    Text(
                        text = "Puente local directo activo sin servidores externos",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            OutlinedButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = TextSecondary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ajustes", fontSize = 11.sp, color = TextPrimary)
            }
        }
    }
}

@Composable
private fun HealthConnectBanner(
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TelatOrange.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Vincular con Samsung Health",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TelatOrange
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Concede permisos en Health Connect para guardar pasos, pulso, sueño y oxígeno directamente en tu Galaxy S24.",
                fontSize = 12.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRequestPermissions,
                    colors = ButtonDefaults.buttonColors(containerColor = TelatOrange),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Text("Conceder permisos", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Ajustes", fontSize = 12.sp, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun OffWristWarningBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, StatusAmber.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = StatusAmber.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = StatusAmber,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Sensor sin contacto dérmico: coloca el anillo firmemente en tu dedo para medir con precisión.",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
        }
    }
}

/**
 * 6. Controles Ejecutivos y Acciones de Fondo
 */
@Composable
private fun ExecutiveControlsSection(
    uiState: RingDashboardUiState,
    onForceSync: () -> Unit,
    onTogglePeriodicSync: (Boolean) -> Unit,
    onExportCsv: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, DarkBorder, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Centro de Operaciones",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            // Botón Insignia de Sincronización Manual
            Button(
                onClick = onForceSync,
                colors = ButtonDefaults.buttonColors(containerColor = TelatBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = DarkBackground,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sincronizar ahora con Samsung Health",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = DarkBackground
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceElevated)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sincronización en 2º plano (30 min)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "WorkManager despierta el extractor sin drenar batería",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = uiState.isPeriodicSyncEnabled,
                    onCheckedChange = onTogglePeriodicSync,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TelatBlue,
                        checkedTrackColor = TelatBlue.copy(alpha = 0.4f)
                    )
                )
            }

            OutlinedButton(
                onClick = onExportCsv,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = TextSecondary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Exportar sesión local a CSV (Downloads)", fontSize = 12.sp, color = TextPrimary)
            }
        }
    }
}

@Composable
private fun StatusFooter(
    message: String,
    lastSync: Long?,
    historyCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (message.contains("Error") || message.contains("Fallo")) StatusRed else TextSecondary
        )
        if (lastSync != null && lastSync > 0) {
            val format = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            Text(
                text = "Última sincronización exitosa: ${format.format(Date(lastSync))} ($historyCount registros)",
                fontSize = 10.sp,
                color = TextMuted
            )
        }
    }
}
