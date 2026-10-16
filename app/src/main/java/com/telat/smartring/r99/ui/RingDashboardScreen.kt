package com.telat.smartring.r99.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telat.smartring.r99.data.model.BleConnectionState
import com.telat.smartring.r99.data.model.RingDashboardUiState
import com.telat.smartring.r99.ui.theme.DarkSurface
import com.telat.smartring.r99.ui.theme.DarkSurfaceElevated
import com.telat.smartring.r99.ui.theme.StatusAmber
import com.telat.smartring.r99.ui.theme.StatusGreen
import com.telat.smartring.r99.ui.theme.StatusRed
import com.telat.smartring.r99.ui.theme.TelatBlue
import com.telat.smartring.r99.ui.theme.TelatOrange
import com.telat.smartring.r99.ui.theme.TelatYellow
import com.telat.smartring.r99.ui.theme.TextMuted
import com.telat.smartring.r99.ui.theme.TextPrimary
import com.telat.smartring.r99.ui.theme.TextSecondary
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Encabezado principal del anillo
        HeaderSection(
            deviceName = uiState.deviceName,
            deviceAddress = uiState.deviceAddress,
            connectionState = uiState.connectionState,
            batteryPercent = uiState.batteryPercent,
            isCharging = uiState.isCharging
        )

        // 2. Estado de vinculación con Health Connect & Samsung Health
        if (!uiState.hasHealthPermissions) {
            HealthConnectBanner(
                onRequestPermissions = onRequestHealthPermissions,
                onOpenSettings = onOpenHealthSettings
            )
        } else {
            HealthConnectStatusCard(onOpenSettings = onOpenHealthSettings)
        }

        // 3. Fila de Signos Vitales Interactivos: Pulso cardíaco y SpO2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InteractiveVitalCard(
                title = "Pulso cardíaco",
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

            InteractiveVitalCard(
                title = "Oxígeno SpO2",
                value = if (uiState.currentSpo2 != null && uiState.currentSpo2!! > 0) "${uiState.currentSpo2}%" else "--",
                unit = "Sat.",
                lastRecordedTime = uiState.lastSpo2Time,
                icon = Icons.Default.WaterDrop,
                accentColor = TelatBlue,
                isMeasuring = uiState.isMeasuringSpo2,
                measuringText = "Midiendo SpO2...",
                buttonText = "Medir SpO2",
                onMeasureClick = { viewModel.triggerSpo2Measurement() },
                modifier = Modifier.weight(1f)
            )
        }

        // 4. Fila de tarjetas de Actividad: Pasos y Calorías con barra de progreso
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StepsMetricCard(
                steps = uiState.totalStepsToday,
                goal = 10000,
                modifier = Modifier.weight(1f)
            )

            CaloriesMetricCard(
                calories = uiState.totalCaloriesToday,
                distanceMeters = uiState.totalDistanceMetersToday,
                modifier = Modifier.weight(1f)
            )
        }

        // 5. Tarjeta de Estado de Actividad y Sueño
        ActivityAndSleepCard(
            isMoving = uiState.isMoving,
            sleepText = uiState.sleepStatusText,
            syncedSleepSessions = uiState.syncedSleepSessionsCount
        )

        // 6. Controles y Sincronización Explicativa (sin botones comprimidos)
        ControlsSection(
            uiState = uiState,
            onForceSync = { viewModel.forceSyncNow() },
            onToggleConnect = {
                if (uiState.connectionState == BleConnectionState.CONNECTED) {
                    viewModel.disconnectRing()
                } else {
                    viewModel.startScanningAndConnect()
                }
            },
            onTogglePeriodicSync = { viewModel.togglePeriodicSync(it) },
            onExportCsv = { viewModel.exportSessionCsv() }
        )

        // 7. Pie de estado con detalles
        StatusFooter(
            message = uiState.statusMessage,
            lastSync = uiState.lastSyncTimestamp,
            historyCount = uiState.syncedHistoryRecordsCount
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun HeaderSection(
    deviceName: String,
    deviceAddress: String,
    connectionState: BleConnectionState,
    batteryPercent: Int?,
    isCharging: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Smart Ring R99",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (deviceAddress.isNotBlank()) "$deviceName ($deviceAddress)" else "Buscando prefijo R99...",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (batteryPercent != null) {
                    Text(
                        text = if (isCharging) "⚡ $batteryPercent%" else "🔋 $batteryPercent%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (batteryPercent > 20) StatusGreen else StatusAmber,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceElevated)
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                val (statusText, statusColor) = when (connectionState) {
                    BleConnectionState.CONNECTED -> "Conectado" to StatusGreen
                    BleConnectionState.CONNECTING, BleConnectionState.SYNCING -> "Sincronizando" to StatusAmber
                    BleConnectionState.SCANNING -> "Escaneando" to TelatBlue
                    BleConnectionState.DISCONNECTED -> "Desconectado" to TextMuted
                    BleConnectionState.ERROR -> "Error" to StatusRed
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkSurfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = statusColor
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthConnectStatusCard(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Vinculado",
                        tint = StatusGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Health Connect & Samsung Health",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusGreen
                        )
                        Text(
                            text = "Sincronización activa con Galaxy S24",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                OutlinedButton(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = TextSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ajustes", fontSize = 12.sp, color = TextPrimary)
                }
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Vincular con Health Connect & Samsung Health",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TelatOrange
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Concede permisos para escribir tus pasos, pulso, sueño y oxígeno directamente en Samsung Health.",
                style = MaterialTheme.typography.bodyMedium,
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
                    Text("Conceder permisos", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
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
private fun InteractiveVitalCard(
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
        modifier = modifier.clickable { onMeasureClick() },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
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
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            val timeText = if (lastRecordedTime != null && lastRecordedTime > 0) {
                val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                "Hora: ${format.format(Date(lastRecordedTime))}"
            } else {
                "Sin lecturas hoy"
            }
            Text(
                text = timeText,
                fontSize = 10.sp,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Botón interactivo de medición bajo demanda
            if (isMeasuring) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = accentColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = measuringText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = accentColor
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onMeasureClick,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = accentColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = buttonText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun StepsMetricCard(
    steps: Long,
    goal: Long = 10000,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pasos de hoy",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                Icon(
                    imageVector = Icons.Default.DirectionsRun,
                    contentDescription = "Pasos",
                    tint = TelatBlue,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = String.format(Locale.getDefault(), "%,d", steps),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = "Objetivo: ${String.format(Locale.getDefault(), "%,d", goal)}",
                fontSize = 11.sp,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(8.dp))

            val progress = (steps.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = TelatBlue,
                trackColor = DarkSurfaceElevated
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Se actualiza al caminar o sincronizar",
                fontSize = 9.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun CaloriesMetricCard(
    calories: Long,
    distanceMeters: Long,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Calorías & Distancia",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                Icon(
                    imageVector = Icons.Default.LocalFireDepartment,
                    contentDescription = "Calorías",
                    tint = TelatOrange,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "$calories kcal",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            val km = distanceMeters / 1000.0
            Text(
                text = "${String.format(Locale.getDefault(), "%.2f", km)} km recorridos",
                fontSize = 11.sp,
                color = TextMuted
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Calculado según el sensor del anillo",
                fontSize = 9.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun ActivityAndSleepCard(
    isMoving: Boolean,
    sleepText: String,
    syncedSleepSessions: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Estado en tiempo real", fontSize = 11.sp, color = TextMuted)
                    Text(
                        text = if (isMoving) "En movimiento (Zancada activa)" else "En reposo / Estático",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isMoving) StatusGreen else TextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = "Sueño",
                        tint = TelatYellow,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = sleepText,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            if (syncedSleepSessions > 0) {
                Text(
                    text = "Sesiones de sueño en Health Connect: $syncedSleepSessions",
                    fontSize = 11.sp,
                    color = TelatYellow
                )
            }
        }
    }
}

@Composable
private fun ControlsSection(
    uiState: RingDashboardUiState,
    onForceSync: () -> Unit,
    onToggleConnect: () -> Unit,
    onTogglePeriodicSync: (Boolean) -> Unit,
    onExportCsv: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Control y Sincronización",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            // 1. Botón Principal: Sincronizar Historial Completo
            Column {
                Button(
                    onClick = onForceSync,
                    colors = ButtonDefaults.buttonColors(containerColor = TelatBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sincronizar historial con Samsung Health",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Descarga pasos, sueños, pulso y SpO2 acumulados en la memoria del anillo y los guarda en Samsung Health.",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            // 2. Fila con 2 botones secundarios amplios (no comprimidos)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isConnected = uiState.connectionState == BleConnectionState.CONNECTED
                val connIcon = if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth
                val connText = if (isConnected) "Desconectar" else "Conectar"

                OutlinedButton(
                    onClick = onToggleConnect,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(connIcon, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextSecondary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(connText, fontSize = 12.sp, color = TextPrimary)
                }

                OutlinedButton(
                    onClick = onExportCsv,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextSecondary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Exportar CSV", fontSize = 12.sp, color = TextPrimary)
                }
            }

            // 3. Sincronización automática periódica en segundo plano
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sincronización en segundo plano (cada 30 min)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Descarga automáticamente nuevos datos para Samsung Health y desconecta el BLE para cuidar la batería.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = uiState.isPeriodicSyncEnabled,
                    onCheckedChange = onTogglePeriodicSync,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TelatBlue,
                        checkedTrackColor = TelatBlue.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}

@Composable
private fun StatusFooter(message: String, lastSync: Long?, historyCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "Estado: $message",
            fontSize = 12.sp,
            color = TextSecondary
        )
        if (historyCount > 0) {
            Text(
                text = "Registros históricos sincronizados: $historyCount",
                fontSize = 11.sp,
                color = StatusGreen
            )
        }
        lastSync?.let {
            val format = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            Text(
                text = "Última sincronización: ${format.format(Date(it))}",
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}
