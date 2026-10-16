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
import com.telat.smartring.r99.data.ycbt.YcbtCommands
import com.telat.smartring.r99.data.ycbt.YcbtConstants
import com.telat.smartring.r99.data.ycbt.YcbtDecodedEvent
import com.telat.smartring.r99.data.ycbt.YcbtFrame
import com.telat.smartring.r99.data.ycbt.YcbtFrameAssembler
import com.telat.smartring.r99.data.ycbt.YcbtHealthDecoder
import com.telat.smartring.r99.data.ycbt.YcbtHistorySyncResult
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
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
    private val ycbtAssembler = YcbtFrameAssembler()

    private var bluetoothGatt: BluetoothGatt? = null
    private var nusRxCharacteristic: BluetoothGattCharacteristic? = null
    private var nusTxCharacteristic: BluetoothGattCharacteristic? = null
    private var heartRateCharacteristic: BluetoothGattCharacteristic? = null
    private var ycbtCommandCharacteristic: BluetoothGattCharacteristic? = null
    private var ycbtStreamCharacteristic: BluetoothGattCharacteristic? = null

    private var keepAliveJob: Job? = null
    private var isScanning = false

    // Control de consultas de historial YCBT
    private val historySyncMutex = Mutex()
    private var activeQueryCmd: Byte? = null
    private val currentQueryBuffer = mutableListOf<Byte>()
    private var activeQueryDeferred: CompletableDeferred<ByteArray>? = null

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

    private val _batteryFlow = MutableSharedFlow<Pair<Int, Boolean>>(extraBufferCapacity = 16)
    val batteryFlow: SharedFlow<Pair<Int, Boolean>> = _batteryFlow.asSharedFlow()

    private val _spo2Flow = MutableSharedFlow<Int>(extraBufferCapacity = 32)
    val spo2Flow: SharedFlow<Int> = _spo2Flow.asSharedFlow()

    private val _rawLogsFlow = MutableSharedFlow<String>(extraBufferCapacity = 128)
    val rawLogsFlow: SharedFlow<String> = _rawLogsFlow.asSharedFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val name = device.name ?: result.scanRecord?.deviceName
                if (name != null && name.contains(DEVICE_NAME_PREFIX, ignoreCase = true)) {
                    Log.i(TAG, "Smart Ring detectado en escaneo: $name [${device.address}]")
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
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth no disponible o apagado")
            _connectionState.value = BleConnectionState.ERROR
            return
        }

        // 1. Revisar si el anillo ya está emparejado/vinculado en los Ajustes de Bluetooth de One UI
        val bonded = try {
            bluetoothAdapter.bondedDevices?.firstOrNull { device ->
                val dName = device.name
                dName != null && dName.contains(DEVICE_NAME_PREFIX, ignoreCase = true)
            }
        } catch (e: Exception) {
            null
        }

        if (bonded != null) {
            Log.i(TAG, "Smart Ring detectado en dispositivos vinculados del sistema: ${bonded.name} [${bonded.address}]")
            connectToDevice(bonded)
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            Log.w(TAG, "Escáner BLE no disponible")
            _connectionState.value = BleConnectionState.ERROR
            return
        }

        if (isScanning) return

        val scanFilter = ScanFilter.Builder().build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        isScanning = true
        _connectionState.value = BleConnectionState.SCANNING
        Log.i(TAG, "Iniciando escaneo BLE buscando prefijo $DEVICE_NAME_PREFIX...")
        scanner.startScan(listOf(scanFilter), settings, scanCallback)

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
        ycbtAssembler.reset()
        activeQueryDeferred?.cancel()
        activeQueryDeferred = null
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error al cerrar GATT: ${e.message}")
        }
        bluetoothGatt = null
        _connectionState.value = BleConnectionState.DISCONNECTED
    }

    fun sendSyncPing(): Boolean {
        // Enviar por YCBT si está activo
        if (ycbtCommandCharacteristic != null) {
            sendYcbtCommand(YcbtCommands.enableLiveStatusPush())
            return true
        }

        // Fallback a NUS
        val char = nusRxCharacteristic ?: return false
        val gatt = bluetoothGatt ?: return false
        return writeCharacteristic(gatt, char, PING_KEEPALIVE_PAYLOAD)
    }

    fun sendYcbtCommand(frame: ByteArray): Boolean {
        val char = ycbtCommandCharacteristic ?: return false
        val gatt = bluetoothGatt ?: return false
        return writeCharacteristic(gatt, char, frame)
    }

    /**
     * Sincroniza secuencialmente todo el historial almacenado en la memoria flash del anillo (YCBT)
     * para pasos/deporte, sueño profundo/ligero/REM, pulso y SpO2.
     */
    suspend fun syncAllHistory(): YcbtHistorySyncResult = historySyncMutex.withLock {
        if (ycbtCommandCharacteristic == null || bluetoothGatt == null) {
            Log.w(TAG, "Canal YCBT no disponible para descarga de historial")
            return YcbtHistorySyncResult()
        }

        Log.i(TAG, "Iniciando descarga secuencial de historial YCBT desde el anillo...")
        _connectionState.value = BleConnectionState.SYNCING

        // 1. Descarga de historial deportivo / pasos
        val sportBytes = queryHistoryBlock(YcbtConstants.QUERY_SPORT_STEPS)
        val sportRecords = YcbtHealthDecoder.decodeSport(sportBytes)
        Log.i(TAG, "Historial deportivo descargado: ${sportRecords.size} bloques")

        // 2. Descarga de sesiones de sueño
        val sleepBytes = queryHistoryBlock(YcbtConstants.QUERY_SLEEP)
        val sleepSessions = YcbtHealthDecoder.decodeSleep(sleepBytes)
        Log.i(TAG, "Historial de sueño descargado: ${sleepSessions.size} sesiones")

        // 3. Descarga de historial de frecuencia cardíaca
        val hrBytes = queryHistoryBlock(YcbtConstants.QUERY_HEART_RATE)
        val hrRecords = YcbtHealthDecoder.decodeHeartRate(hrBytes)
        Log.i(TAG, "Historial de frecuencia cardíaca descargado: ${hrRecords.size} muestras")

        // 4. Descarga de historial de saturación de oxígeno (SpO2)
        val spo2Bytes = queryHistoryBlock(YcbtConstants.QUERY_SPO2)
        val spo2Records = YcbtHealthDecoder.decodeSpo2(spo2Bytes)
        Log.i(TAG, "Historial de SpO2 descargado: ${spo2Records.size} muestras")

        _connectionState.value = BleConnectionState.CONNECTED

        return YcbtHistorySyncResult(
            sportRecords = sportRecords,
            sleepSessions = sleepSessions,
            heartRateRecords = hrRecords,
            spo2Records = spo2Records
        )
    }

    private suspend fun queryHistoryBlock(queryCmd: Byte): ByteArray {
        val deferred = CompletableDeferred<ByteArray>()
        synchronized(currentQueryBuffer) {
            currentQueryBuffer.clear()
            activeQueryCmd = queryCmd
            activeQueryDeferred = deferred
        }

        val requestBytes = YcbtCommands.queryHistory(queryCmd)
        sendYcbtCommand(requestBytes)

        val result = withTimeoutOrNull(6_000L) {
            deferred.await()
        }

        synchronized(currentQueryBuffer) {
            activeQueryCmd = null
            activeQueryDeferred = null
        }

        return result ?: ByteArray(0)
    }

    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(30_000L)
                if (_connectionState.value == BleConnectionState.CONNECTED || _connectionState.value == BleConnectionState.SYNCING) {
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
            Log.e(TAG, "Fallo al escribir característica ${characteristic.uuid}: ${e.message}")
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
                ycbtAssembler.reset()
                activeQueryDeferred?.cancel()
                activeQueryDeferred = null
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

            // 1. Servicio YCBT propietario (SmartHealth / Yucheng)
            val ycbtService = gatt.getService(YcbtConstants.SERVICE_UUID)
            ycbtCommandCharacteristic = ycbtService?.getCharacteristic(YcbtConstants.COMMAND_CHAR_UUID)
            ycbtStreamCharacteristic = ycbtService?.getCharacteristic(YcbtConstants.STREAM_CHAR_UUID)

            // 2. Fallbacks estándar (HR SIG y Nordic UART)
            val hrService = gatt.getService(HEART_RATE_SERVICE_UUID)
            heartRateCharacteristic = hrService?.getCharacteristic(HEART_RATE_MEASUREMENT_UUID)

            val nusService = gatt.getService(NUS_SERVICE_UUID)
            nusTxCharacteristic = nusService?.getCharacteristic(NUS_TX_CHAR_UUID)
            nusRxCharacteristic = nusService?.getCharacteristic(NUS_RX_CHAR_UUID)

            _connectionState.value = BleConnectionState.CONNECTED

            coroutineScope.launch(Dispatchers.IO) {
                // Suscribir características YCBT prioritariamente
                ycbtStreamCharacteristic?.let { streamChar ->
                    enableNotificationOrIndication(gatt, streamChar)
                    delay(350L)
                }

                ycbtCommandCharacteristic?.let { cmdChar ->
                    enableNotificationOrIndication(gatt, cmdChar)
                    delay(350L)
                }

                // Suscribir fallbacks
                heartRateCharacteristic?.let { hrChar ->
                    enableNotificationOrIndication(gatt, hrChar)
                    delay(300L)
                }

                nusTxCharacteristic?.let { nusChar ->
                    enableNotificationOrIndication(gatt, nusChar)
                    delay(300L)
                }

                // Secuencia oficial de inicialización YCBT
                if (ycbtCommandCharacteristic != null) {
                    Log.i(TAG, "Ejecutando protocolo de inicialización YCBT (Hora, Monitores 30m, Live Push)...")

                    // 1. Sincronización horaria
                    sendYcbtCommand(YcbtCommands.setTime())
                    delay(250L)

                    // 2. Activar monitores en cadencia fija obligatoria de 30 minutos
                    val monitorCmds = YcbtCommands.enable30MinMonitors(intervalMinutes = 30)
                    for (cmd in monitorCmds) {
                        sendYcbtCommand(cmd)
                        delay(200L)
                    }

                    // 3. Activar telemetría continua
                    sendYcbtCommand(YcbtCommands.enableLiveStatusPush())
                    delay(200L)

                    // 4. Solicitar info del dispositivo (batería)
                    sendYcbtCommand(YcbtCommands.getDeviceInfo())
                } else {
                    // Fallback a ping NUS
                    sendSyncPing()
                }

                startKeepAlive()
            }
        }

        private fun enableNotificationOrIndication(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(CCCD_DESCRIPTOR_UUID) ?: return

            val isIndicate = (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
            val descriptorValue = if (isIndicate) {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, descriptorValue)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = descriptorValue
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
            Log.d(TAG, "Notificaciones/Indicaciones habilitadas para ${characteristic.uuid}")
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
                YcbtConstants.STREAM_CHAR_UUID, YcbtConstants.COMMAND_CHAR_UUID -> {
                    // Ensamblar fragmentos YCBT
                    val completedFrames = ycbtAssembler.feed(data)
                    for (frame in completedFrames) {
                        handleYcbtFrame(frame)
                    }
                }

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

        private fun handleYcbtFrame(frame: YcbtFrame) {
            when (frame.type) {
                YcbtConstants.GROUP_HEALTH -> {
                    // Historial de salud (0x05)
                    if (frame.cmd == YcbtConstants.TERMINAL_BLOCK) {
                        // Bloque terminal alcanzado: Enviar ACK obligatorio de inmediato
                        Log.d(TAG, "Fin de bloque de historial (05 80). Despachando ACK...")
                        sendYcbtCommand(YcbtCommands.historyBlockAck(YcbtConstants.ACK_SUCCESS))

                        val deferred = activeQueryDeferred
                        if (deferred != null && !deferred.isCompleted) {
                            val accumulated = synchronized(currentQueryBuffer) {
                                currentQueryBuffer.toByteArray()
                            }
                            deferred.complete(accumulated)
                        }
                    } else {
                        // Chunk intermedio de datos del historial
                        synchronized(currentQueryBuffer) {
                            for (b in frame.payload) {
                                currentQueryBuffer.add(b)
                            }
                        }
                    }
                }

                YcbtConstants.GROUP_REAL -> {
                    // Telemetría en tiempo real (0x06)
                    val event = YcbtHealthDecoder.decodeRealtime(frame.cmd, frame.payload)
                    if (event != null) {
                        coroutineScope.launch {
                            when (event) {
                                is YcbtDecodedEvent.RealtimeStatus -> {
                                    _consolidatedStepsFlow.emit(
                                        StepsSample(
                                            steps = event.steps.toInt(),
                                            calories = event.calories.toInt(),
                                            distanceMeters = event.distanceMeters.toInt()
                                        )
                                    )
                                }
                                is YcbtDecodedEvent.RealtimeHeartRate -> {
                                    _heartRateFlow.emit(HeartRateSample(bpm = event.bpm))
                                }
                                is YcbtDecodedEvent.RealtimeSpo2 -> {
                                    _spo2Flow.emit(event.percent)
                                }
                                is YcbtDecodedEvent.BatteryStatus -> {
                                    _batteryFlow.emit(Pair(event.percent, event.isCharging))
                                }
                                else -> Unit
                            }
                        }
                    }
                }

                YcbtConstants.GROUP_GET -> {
                    // Info de batería y versión de firmware
                    if (frame.cmd.toInt() and 0xFF == 0x00 && frame.payload.size >= 2) {
                        val battery = frame.payload[0].toInt() and 0xFF
                        coroutineScope.launch {
                            _batteryFlow.emit(Pair(battery.coerceIn(0, 100), false))
                        }
                    }
                }
            }
        }
    }
}
