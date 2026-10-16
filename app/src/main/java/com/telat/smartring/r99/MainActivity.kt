package com.telat.smartring.r99

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.ViewModelProvider
import com.telat.smartring.r99.data.healthconnect.HealthConnectBridge
import com.telat.smartring.r99.ui.RingDashboardScreen
import com.telat.smartring.r99.ui.RingViewModel
import com.telat.smartring.r99.ui.theme.SmartRingR99BridgeTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var viewModel: RingViewModel

    // Contrato oficial para solicitud de permisos de Health Connect
    private val healthPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        Log.i(TAG, "Resultado de permisos Health Connect: ${grantedPermissions.size} concedidos")
        if (grantedPermissions.isNotEmpty()) {
            Toast.makeText(this, "Permisos de Health Connect actualizados con éxito", Toast.LENGTH_SHORT).show()
        } else {
            // Si la ventana no devolvió permisos o fue cancelada, abrimos directamente los Ajustes de Health Connect
            Toast.makeText(this, "Abriendo configuración de Health Connect...", Toast.LENGTH_SHORT).show()
            openHealthConnectSettings()
        }
        viewModel.checkHealthConnectStatus()
    }

    // Contrato para solicitud de permisos de Bluetooth y Notificaciones
    private val blePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            viewModel.startScanningAndConnect()
        } else {
            Toast.makeText(this, "Se requieren permisos de Dispositivos Cercanos (Bluetooth) para comunicarse con el anillo", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[RingViewModel::class.java]

        checkAndRequestBlePermissions()

        setContent {
            SmartRingR99BridgeTheme {
                RingDashboardScreen(
                    viewModel = viewModel,
                    onRequestHealthPermissions = {
                        requestHealthConnectPermissions()
                    },
                    onOpenHealthSettings = {
                        openHealthConnectSettings()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkHealthConnectStatus()
    }

    private fun checkAndRequestBlePermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            blePermissionLauncher.launch(permissions.toTypedArray())
        } else {
            viewModel.startScanningAndConnect()
        }
    }

    private fun requestHealthConnectPermissions() {
        if (!viewModel.healthConnectBridge.isAvailable) {
            Toast.makeText(
                this,
                "Health Connect no está habilitado en este dispositivo. Abriendo Ajustes...",
                Toast.LENGTH_LONG
            ).show()
            openHealthConnectSettings()
            return
        }

        try {
            healthPermissionLauncher.launch(HealthConnectBridge.REQUIRED_PERMISSIONS)
        } catch (e: Exception) {
            Log.e(TAG, "Excepción lanzando contrato de Health Connect: ${e.message}", e)
            openHealthConnectSettings()
        }
    }

    fun openHealthConnectSettings() {
        try {
            // Android 14+ directo a la gestión de permisos de esta app en Health Connect
            val intent = Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").apply {
                putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                // Fallback a pantalla general de Health Connect
                val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                startActivity(intent)
            } catch (e2: Exception) {
                try {
                    // Fallback a Samsung Health si está instalado
                    val pm = packageManager
                    val intent = pm.getLaunchIntentForPackage("com.sec.android.app.shealth")
                    if (intent != null) {
                        startActivity(intent)
                        Toast.makeText(this, "En Samsung Health: Ajustes > Health Connect > Conceder permisos", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Abre Ajustes > Seguridad y privacidad > Health Connect en tu Galaxy S24", Toast.LENGTH_LONG).show()
                    }
                } catch (e3: Exception) {
                    Toast.makeText(this, "Abre Ajustes > Seguridad y privacidad > Health Connect en tu Galaxy S24", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
