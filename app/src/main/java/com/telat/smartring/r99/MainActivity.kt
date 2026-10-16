package com.telat.smartring.r99

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.health.connect.client.PermissionController
import com.telat.smartring.r99.data.healthconnect.HealthConnectBridge
import com.telat.smartring.r99.ui.RingDashboardScreen
import com.telat.smartring.r99.ui.RingViewModel
import com.telat.smartring.r99.ui.theme.SmartRingR99BridgeTheme

import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: RingViewModel

    // Contrato oficial para solicitud de permisos de Health Connect
    private val healthPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        if (grantedPermissions.containsAll(HealthConnectBridge.REQUIRED_PERMISSIONS)) {
            Toast.makeText(this, "Permisos de Health Connect concedidos", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Algunos permisos de Health Connect fueron omitidos", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Se requieren permisos Bluetooth para detectar el anillo", Toast.LENGTH_LONG).show()
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
                "Health Connect no está habilitado o requiere actualización en los Ajustes del Galaxy S24",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        healthPermissionLauncher.launch(HealthConnectBridge.REQUIRED_PERMISSIONS)
    }
}
