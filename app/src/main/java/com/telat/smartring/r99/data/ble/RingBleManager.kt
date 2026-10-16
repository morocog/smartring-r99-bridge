package com.telat.smartring.r99.data.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import com.telat.smartring.r99.data.model.ActivityState
import com.telat.smartring.r99.data.model.BleConnectionState
import com.telat.smartring.r99.data.model.HeartRateSample
import com.telat.smartring.r99.data.model.SleepState
import com.telat.smartring.r99.data.model.StepsSample
import com.telat.smartring.r99.data.parser.HeartRateDecoder
import com.telat.smartring.r99.data.parser.TelemetryParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

@SuppressLint("MissingPermission")
class RingBleManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "RingBleManager"
        private const val DEVICE_NAME_PREFIX = "R99"

        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

        val NUS_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val NUS_TX_CHAR_UUID: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        val NUS_RX_CHAR_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")

        val CCCD_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        // Trama de ping de sincronización [0xA5, 0x01, 0x00, 0xA6]
        val PING_KEEPALIVE_PAYLOAD = byteArrayOf(0xA5.toByte(), 0x01.toByte(), 0x00.toByte(), 0xA6.toByte())
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val telemetryParser = TelemetryParser(debounceMillis = 200L)
    private val heartRateDecoder = HeartRateDecoder(deduplicationWindowMillis = 15_000L)

    private var bluetoothGatt: BluetoothGatt? = null
    private var nusRxCharacteristic: BluetoothGattCharacteristic? = null
    private var nusTxCharacteristic: BluetoothGattCharacteristic? = null
    private var heartRateCharacteristic: BluetoothGattCharacteristic? = null

    private var keepAliveJob: Job? = null
    private var isScanning = false

    // Estados reactivos
    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow("Desconocido")
    val connectedDeviceName: StateFlow<String> = _connectedDeviceName.asStateFlow()

    private val _connectedDeviceAddress = MutableStateFlow("")
    val connectedDeviceAddress: StateFlow<String> = _connectedDeviceAddress.asStateFlow()

    // Flujos de eventos de telemetría hacia el ViewModel y Health Connect
    private val _heartRateFlow = MutableSharedFlow<HeartRateSample>(extraBufferCapacity = 64)
    val heartRateFlow: SharedFlow<HeartRateSample> = _heartRateFlow.asSharedFlow()

    private val _consolidatedStepsFlow = MutableSharedFlow<StepsSample>(extraBufferCapacity = 64)
    val consolidatedStepsFlow: SharedFlow<StepsSample> = _consolidatedStepsFlow.asSharedFlow()

    private val _instantStepFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    val instantStepFlow: SharedFlow<Unit> = _instantStepFlow.asSharedFlow()

    private val _activityStateFlow = MutableSharedFlow<ActivityState>(extraBufferCapacity = 16)
    val activityStateFlow: SharedFlow<ActivityState> = _activityStateFlow.asSharedFlow()

    private val _sleepStateFlow = MutableSharedFlow<SleepState>(extraBufferCapacity = 16)
    val sleepStateFlow: SharedFlow<SleepState> = _sleepStateFlow.asSharedFlow()

    private val _rawLogsFlow = MutableSharedFlow<String>(extraBufferCapacity = 128)
    val rawLogsFlow: SharedFlow<String> = _rawLogsFlow.asSharedFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val name = device.name ?: result.scanRecord?.deviceName
                if (name != null && name.startsWith(DEVICE_NAME_PREFIX, ignoreCase = true)) {
                    Log.i(TAG, "Smart Ring detectado: $name [${device.address}]")
                    stopScan()
                    connectToDevice(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Error en escaneo BLE: $errorCode")
            isScanning = false
            _connectionState.value = BleConnectionState.ERROR
        }
    }

    fun startScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth no disponible o apagado")
            _connectionState.value = BleConnectionState.ERROR
            return
        }

        if (isScanning) return

        val scanFilter = ScanFilter.Builder()
            .build() // Escaneo amplio con filtrado manual por prefijo R99 para mayor compatibilidad
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        isScanning = true
        _connectionState.value = BleConnectionState.SCANNING
        Log.i(TAG, "Iniciando escaneo BLE buscando prefijo $DEVICE_NAME_PREFIX...")
        scanner.startScan(listOf(scanFilter), settings, scanCallback)

        // Timeout de escaneo para no agotar la batería del teléfono si el anillo no está cerca
        coroutineScope.launch {
            delay(15_000L)
            if (isScanning) {
                stopScan()
                if (_connectionState.value == BleConnectionState.SCANNING) {
                    _connectionState.value = BleConnectionState.DISCONNECTED
                }
            }
        }
    }

    fun stopScan() {
        if (!isScanning) return
        isScanning = false
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            Log.i(TAG, "Escaneo BLE detenido")
        } catch (e: Exception) {
            Log.w(TAG, "Error al detener escaneo: ${e.message}")
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        _connectionState.value = BleConnectionState.CONNECTING
        _connectedDeviceName.value = device.name ?: "R99 Smart Ring"
        _connectedDeviceAddress.value = device.address

        Log.i(TAG, "Conectando a GATT de ${device.name} [${device.address}]...")
        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    fun disconnect() {
        stopKeepAlive()
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error al cerrar GATT: ${e.message}")
        }
        bluetoothGatt = null
        _connectionState.value = BleConnectionState.DISCONNECTED
    }

    /**
     * Envía manualmente el ping de sincronización a NUS RX.
     */
    fun sendSyncPing(): Boolean {
        val char = nusRxCharacteristic ?: return false
        val gatt = bluetoothGatt ?: return false

        return writeCharacteristic(gatt, char, PING_KEEPALIVE_PAYLOAD)
    }

    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(30_000L) // Ping cada 30 segundos
                if (_connectionState.value == BleConnectionState.CONNECTED || _connectionState.value == BleConnectionState.SYNCING) {
                    Log.d(TAG, "Enviando ping de keepalive al anillo...")
                    sendSyncPing()
                }
            }
        }
    }

    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = data
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallo al escribir característica: ${e.message}")
            false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Conexión BLE establecida. Descubriendo servicios GATT...")
                _connectionState.value = BleConnectionState.CONNECTING
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "Desconectado del Smart Ring (status: $status)")
                stopKeepAlive()
                _connectionState.value = BleConnectionState.DISCONNECTED
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) {
                Log.e(TAG, "Error descubriendo servicios GATT: $status")
                _connectionState.value = BleConnectionState.ERROR
                return
            }

            Log.i(TAG, "Servicios GATT descubiertos con éxito")

            // 1. Identificar servicio de frecuencia cardíaca
            val hrService = gatt.getService(HEART_RATE_SERVICE_UUID)
            heartRateCharacteristic = hrService?.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)

            // 2. Identificar servicio Nordic UART (NUS)
            val nusService = gatt.getService(NUS_SERVICE_UUID)
            nusTxCharacteristic = nusService?.getCharacteristic(NUS_TX_CHAR_UUID)
            nusRxCharacteristic = nusService?.getCharacteristic(NUS_RX_CHAR_UUID)

            _connectionState.value = BleConnectionState.CONNECTED

            // Iniciar suscripciones encadenadas (evitando colisión en descriptores CCCD)
            coroutineScope.launch(Dispatchers.IO) {
                // Suscribir a NUS TX primero
                nusTxCharacteristic?.let { txChar ->
                    enableNotification(gatt, txChar)
                    delay(400L) // Pausa de estabilización en bus GATT
                }

                // Suscribir a Heart Rate
                heartRateCharacteristic?.let { hrChar ->
                    enableNotification(gatt, hrChar)
                    delay(400L)
                }

                // Iniciar temporizador de keepalive
                startKeepAlive()

                // Enviar primer handshake de sincronización
                sendSyncPing()
            }
        }

        private fun enableNotification(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(CCCD_DESCRIPTOR_UUID)
            if (descriptor != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
                Log.d(TAG, "Notificaciones habilitadas para ${characteristic.uuid}")
            }
        }

        @Deprecated("Deprecated for SDK 33+")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            characteristic?.let { handleCharacteristicData(it.uuid, it.value) }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicData(characteristic.uuid, value)
        }

        private fun handleCharacteristicData(uuid: UUID, data: ByteArray) {
            when (uuid) {
                HEART_RATE_MEASUREMENT_UUID -> {
                    heartRateDecoder.decode(data)?.let { sample ->
                        coroutineScope.launch {
                            _heartRateFlow.emit(sample)
                        }
                    }
                }

                NUS_TX_CHAR_UUID -> {
                    val events = telemetryParser.parseChunk(data)
                    coroutineScope.launch {
                        for (event in events) {
                            when (event) {
                                is TelemetryParser.ParsedTelemetryEvent.ConsolidatedSteps -> {
                                    _consolidatedStepsFlow.emit(event.sample)
                                }
                                is TelemetryParser.ParsedTelemetryEvent.InstantStepDetected -> {
                                    _instantStepFlow.emit(Unit)
                                }
                                is TelemetryParser.ParsedTelemetryEvent.Activity -> {
                                    _activityStateFlow.emit(event.state)
                                }
                                is TelemetryParser.ParsedTelemetryEvent.Sleep -> {
                                    _sleepStateFlow.emit(event.state)
                                }
                                is TelemetryParser.ParsedTelemetryEvent.RawLog -> {
                                    _rawLogsFlow.emit(event.text)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
