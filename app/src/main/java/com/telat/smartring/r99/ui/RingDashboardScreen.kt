package com.telat.smartring.r99.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.telat.smartring.r99.ui.theme.DarkBorder
import com.telat.smartring.r99.ui.theme.DarkSurface
import com.telat.smartring.r99.ui.theme.DarkSurfaceElevated
import com.telat.smartring.r99.ui.theme.StatusAmber
import com.telat.smartring.r99.ui.theme.StatusGreen
import com.telat.smartring.r99.ui.theme.StatusRed
import com.telat.smartring.r99.ui.theme.TelatBlue
import com.telat.smartring.r99.ui.theme.TelatOrange
import com.telat.smartring.r99.ui.theme.TextMuted
import com.telat.smartring.r99.ui.theme.TextPrimary
import com.telat.smartring.r99.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RingDashboardScreen(
    viewModel: RingViewModel,
    onRequestHealthPermissions: () -> Unit
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
        // Encabezado principal
        HeaderSection(
            deviceName = uiState.deviceName,
            deviceAddress = uiState.deviceAddress,
            connectionState = uiState.connectionState
        )

        // Banner de permisos de Health Connect si es necesario
        if (!uiState.hasHealthPermissions) {
            HealthConnectBanner(onRequestPermissions = onRequestHealthPermissions)
        }

        // Tarjeta destacada de Frecuencia Cardíaca
        HeartRateCard(
            bpm = uiState.currentHeartRate,
            lastRecordedTime = uiState.lastHeartRateTime
        )

        // Fila de tarjetas: Pasos y Calorías
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "Pasos de hoy",
                value = String.format(Locale.getDefault(), "%,d", uiState.totalStepsToday),
                subtitle = "Objetivo 10,000",
                icon = Icons.Default.DirectionsRun,
                accentColor = TelatBlue,
                modifier = Modifier.weight(1f)
            )

            MetricCard(
                title = "Calorías",
                value = "${uiState.totalCaloriesToday} kcal",
                subtitle = "${String.format(Locale.getDefault(), "%.1f", uiState.totalDistanceMetersToday / 1000.0)} km",
                icon = Icons.Default.LocalFireDepartment,
                accentColor = TelatOrange,
                modifier = Modifier.weight(1f)
            )
        }

        // Tarjeta de estado de actividad y sueño
        ActivityAndSleepCard(
            isMoving = uiState.isMoving,
            sleepText = uiState.sleepStatusText
        )

        // Controles de sincronización y configuración
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

        // Mensaje de estado al pie
        StatusFooter(
            message = uiState.statusMessage,
            lastSync = uiState.lastSyncTimestamp
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun HeaderSection(
    deviceName: String,
    deviceAddress: String,
    connectionState: BleConnectionState
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
            Column {
                Text(
                    text = "Smart Ring R99",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    text = if (deviceAddress.isNotBlank()) "$deviceName ($deviceAddress)" else "Buscando prefijo R99...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            // Badge de estado de conexión
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

@Composable
private fun HealthConnectBanner(onRequestPermissions: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Vincular con Samsung Health",
                style = MaterialTheme.typography.titleLarge,
                fontSize = 15.sp,
                color = TelatOrange
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Health Connect requiere permisos para registrar pasos, pulso y calorías en tu Galaxy S24.",
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onRequestPermissions,
                colors = ButtonDefaults.buttonColors(containerColor = TelatOrange),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Autorizar en Health Connect", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HeartRateCard(bpm: Int, lastRecordedTime: Long?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Frecuencia cardíaca",
                        tint = StatusRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Frecuencia Cardíaca",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (bpm > 0) "$bpm" else "--",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "BPM",
                        fontSize = 16.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                lastRecordedTime?.let {
                    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    Text(
                        text = "Última lectura: ${timeFormat.format(Date(it))}",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(StatusRed.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = StatusRed,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                color = TextSecondary
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun ActivityAndSleepCard(isMoving: Boolean, sleepText: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Estado de actividad", fontSize = 12.sp, color = TextMuted)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Control y Sincronización",
                style = MaterialTheme.typography.titleLarge,
                fontSize = 15.sp,
                color = TextPrimary
            )

            // Botón Sincronizar Ahora
            Button(
                onClick = onForceSync,
                colors = ButtonDefaults.buttonColors(containerColor = TelatBlue),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sincronizar ahora con Health Connect", fontWeight = FontWeight.SemiBold)
            }

            // Fila de botones secundarios
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleConnect,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val icon = if (uiState.connectionState == BleConnectionState.CONNECTED) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth
                    val text = if (uiState.connectionState == BleConnectionState.CONNECTED) "Desconectar" else "Conectar"
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextSecondary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text, fontSize = 13.sp, color = TextPrimary)
                }

                OutlinedButton(
                    onClick = onExportCsv,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextSecondary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Exportar CSV", fontSize = 13.sp, color = TextPrimary)
                }
            }

            // Switch de sincronización periódica (cada 30 min)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sincronización en 2º plano (30 min)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Conecta, vacía en Health Connect y desconecta para cuidar la batería.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
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
private fun StatusFooter(message: String, lastSync: Long?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Text(
            text = "Estado: $message",
            fontSize = 12.sp,
            color = TextSecondary
        )
        lastSync?.let {
            val format = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            Text(
                text = "Último corte guardado: ${format.format(Date(it))}",
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}
