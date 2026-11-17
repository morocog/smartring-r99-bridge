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
import com.telat.smartring.r99.data.ycbt.YcbtBloodPressureRecord
import com.telat.smartring.r99.data.ycbt.YcbtCommands
import com.telat.smartring.r99.data.ycbt.YcbtConstants
import com.telat.smartring.r99.data.ycbt.YcbtCrc16
import com.telat.smartring.r99.data.ycbt.YcbtDecodedEvent
import com.telat.smartring.r99.data.ycbt.YcbtDeviceCapabilities
import com.telat.smartring.r99.data.ycbt.YcbtDeviceInfo
import com.telat.smartring.r99.data.ycbt.YcbtFrame
import com.telat.smartring.r99.data.ycbt.YcbtFrameAssembler
import com.telat.smartring.r99.data.ycbt.YcbtHealthDecoder
import com.telat.smartring.r99.data.ycbt.YcbtHeartRateRecord
import com.telat.smartring.r99.data.ycbt.YcbtHistorySyncResult
import com.telat.smartring.r99.data.ycbt.YcbtHrvRecord
import com.telat.smartring.r99.data.ycbt.YcbtRespiratoryRateRecord
import com.telat.smartring.r99.data.ycbt.YcbtTemperatureRecord
import com.telat.smartring.r99.data.ycbt.YcbtSpo2Record
import com.telat.smartring.r99.data.ycbt.YcbtUtils
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
    private var spotPollingJob: Job? = null
    private var isScanning = false

    // Control de consultas de historial YCBT
    private val historySyncMutex = Mutex()
    private var activeQueryCmd: Byte? = null
    private val currentQueryBuffer = mutableListOf<Byte>()
    private var activeQueryDeferred: CompletableDeferred<ByteArray>? = null

    // Cola GATT secuencial gobernada por callbacks reales para evitar colisiones "prior command is not finished"
    private val gattOperationMutex = Mutex()
    private var pendingDescriptorWrite: CompletableDeferred<Boolean>? = null
    private var pendingCharacteristicWrite: CompletableDeferred<Boolean>? = null

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

    private val _bloodPressureFlow = MutableSharedFlow<Pair<Int, Int>>(extraBufferCapacity = 32)
    val bloodPressureFlow: SharedFlow<Pair<Int, Int>> = _bloodPressureFlow.asSharedFlow()

    private val _temperatureFlow = MutableSharedFlow<Float>(extraBufferCapacity = 32)
    val temperatureFlow: SharedFlow<Float> = _temperatureFlow.asSharedFlow()

    private val _hrvFlow = MutableSharedFlow<Int>(extraBufferCapacity = 32)
    val hrvFlow: SharedFlow<Int> = _hrvFlow.asSharedFlow()

    private val _respiratoryRateFlow = MutableSharedFlow<Int>(extraBufferCapacity = 32)
    val respiratoryRateFlow: SharedFlow<Int> = _respiratoryRateFlow.asSharedFlow()

    private val _deviceInfoFlow = MutableStateFlow<YcbtDeviceInfo?>(null)
    val deviceInfoFlow: StateFlow<YcbtDeviceInfo?> = _deviceInfoFlow.asStateFlow()

    private val _deviceCapabilitiesFlow = MutableStateFlow<YcbtDeviceCapabilities?>(null)
    val deviceCapabilitiesFlow: StateFlow<YcbtDeviceCapabilities?> = _deviceCapabilitiesFlow.asStateFlow()

    private val _historySyncResultFlow = MutableSharedFlow<YcbtHistorySyncResult>(replay = 1, extraBufferCapacity = 8)
    val historySyncResultFlow: SharedFlow<YcbtHistorySyncResult> = _historySyncResultFlow.asSharedFlow()

    private val _cameraShutterEventFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val cameraShutterEventFlow: SharedFlow<Unit> = _cameraShutterEventFlow.asSharedFlow()

    private val _activeGestureModeFlow = MutableStateFlow(0)
    val activeGestureModeFlow: StateFlow<Int> = _activeGestureModeFlow.asStateFlow()

    private val _sensorContactFlow = MutableSharedFlow<Boolean>(extraBufferCapacity = 16)
    val sensorContactFlow: SharedFlow<Boolean> = _sensorContactFlow.asSharedFlow()

    data class SpotMeasurementStatus(
        val type: Int,
        val isFinished: Boolean,
        val isSuccess: Boolean
    )
    private val _spotMeasurementStatusFlow = MutableSharedFlow<SpotMeasurementStatus>(extraBufferCapacity = 16)
    val spotMeasurementStatusFlow: SharedFlow<SpotMeasurementStatus> = _spotMeasurementStatusFlow.asSharedFlow()

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
        spotPollingJob?.cancel()
        spotPollingJob = null
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
            coroutineScope.launch {
                sendYcbtCommandSequential(YcbtCommands.enableLiveStatusPush())
            }
            return true
        }

        // Fallback a NUS
        val char = nusRxCharacteristic ?: return false
        val gatt = bluetoothGatt ?: return false
        coroutineScope.launch {
            writeCharacteristicSequential(gatt, char, PING_KEEPALIVE_PAYLOAD)
        }
        return true
    }

    fun sendYcbtCommand(frame: ByteArray): Boolean {
        val char = ycbtCommandCharacteristic ?: return false
        val gatt = bluetoothGatt ?: return false
        coroutineScope.launch {
            sendYcbtCommandSequential(frame)
        }
        return true
    }

    fun sendYcbtCommands(frames: List<ByteArray>): Boolean {
        if (ycbtCommandCharacteristic == null || bluetoothGatt == null) return false
        coroutineScope.launch(Dispatchers.IO) {
            for (frame in frames) {
                sendYcbtCommandSequential(frame)
            }
        }
        return true
    }

    suspend fun sendYcbtCommandSequential(frame: ByteArray): Boolean {
        val char = ycbtCommandCharacteristic ?: return false
        val gatt = bluetoothGatt ?: return false
        val hexStr = frame.joinToString("") { "%02X".format(it) }
        Log.d("YCBT_RAW", "►► ENVIANDO_COMANDO [len=${frame.size}]: $hexStr")
        return writeCharacteristicSequential(gatt, char, frame)
    }

    private suspend fun writeCharacteristicSequential(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray
    ): Boolean = gattOperationMutex.withLock {
        val deferred = CompletableDeferred<Boolean>()
        pendingCharacteristicWrite = deferred
        try {
            val initiateSuccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = data
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }

            if (!initiateSuccess) {
                Log.w(TAG, "GATT writeCharacteristic() devolvió false de inmediato para ${characteristic.uuid}")
                pendingCharacteristicWrite = null
                return@withLock false
            }

            withTimeoutOrNull(2_500L) {
                deferred.await()
            } ?: run {
                Log.w(TAG, "Timeout esperando onCharacteristicWrite para ${characteristic.uuid}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en writeCharacteristicSequential: ${e.message}")
            false
        } finally {
            pendingCharacteristicWrite = null
        }
    }

    private suspend fun writeDescriptorSequential(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Boolean = gattOperationMutex.withLock {
        val deferred = CompletableDeferred<Boolean>()
        pendingDescriptorWrite = deferred
        try {
            val initiateSuccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = value
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }

            if (!initiateSuccess) {
                Log.w(TAG, "GATT writeDescriptor() devolvió false para ${descriptor.uuid}")
                pendingDescriptorWrite = null
                return@withLock false
            }

            withTimeoutOrNull(2_500L) {
                deferred.await()
            } ?: run {
                Log.w(TAG, "Timeout esperando onDescriptorWrite para ${descriptor.uuid}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en writeDescriptorSequential: ${e.message}")
            false
        } finally {
            pendingDescriptorWrite = null
        }
    }

    /**
     * Combina registros de tablas dedicadas y Health_HistoryAll (05 09),
     * emite los valores más recientes hacia los flujos reactivos y retorna el resultado consolidado.
     */
    private suspend fun buildAndEmitHistoryResult(
        sportRecords: List<com.telat.smartring.r99.data.ycbt.YcbtSportRecord> = emptyList(),
        sleepSessions: List<com.telat.smartring.r99.data.ycbt.YcbtSleepSession> = emptyList(),
        hrRecords: List<YcbtHeartRateRecord> = emptyList(),
        bpRecords: List<YcbtBloodPressureRecord> = emptyList(),
        spo2Records: List<YcbtSpo2Record> = emptyList(),
        tempRecords: List<YcbtTemperatureRecord> = emptyList(),
        combinedBytes: ByteArray
    ): YcbtHistorySyncResult {
        val combinedRecords = YcbtHealthDecoder.decodeCombinedVitals(combinedBytes)
        Log.i(TAG, "Historial combinado (05 09) decodificado: ${combinedRecords.size} registros")

        val allHr = (hrRecords + combinedRecords.mapNotNull { rec ->
            rec.heartRate?.let { YcbtHeartRateRecord(rec.timestamp, it) }
        }).distinctBy { it.timestamp }

        val allBp = (bpRecords + combinedRecords.mapNotNull { rec ->
            val sys = rec.systolic
            val dia = rec.diastolic
            if (sys != null && dia != null) YcbtBloodPressureRecord(rec.timestamp, sys, dia) else null
        }).distinctBy { it.timestamp }

        val allSpo2 = (spo2Records + combinedRecords.mapNotNull { rec ->
            rec.spo2?.let { YcbtSpo2Record(rec.timestamp, it) }
        }).distinctBy { it.timestamp }

        val allTemp = (tempRecords + combinedRecords.mapNotNull { rec ->
            rec.tempCelsius?.let { YcbtTemperatureRecord(rec.timestamp, it) }
        }).distinctBy { it.timestamp }

        val allHrv = combinedRecords.mapNotNull { rec ->
            rec.hrv?.let { YcbtHrvRecord(rec.timestamp, it) }
        }.distinctBy { it.timestamp }

        val allResp = combinedRecords.mapNotNull { rec ->
            rec.respiratoryRate?.let { YcbtRespiratoryRateRecord(rec.timestamp, it) }
        }.distinctBy { it.timestamp }

        val maxStepsCombined = combinedRecords.maxOfOrNull { it.steps } ?: 0
        if (maxStepsCombined > 0) {
            _consolidatedStepsFlow.emit(StepsSample(steps = maxStepsCombined, calories = 0, distanceMeters = 0))
        }
        allHr.maxByOrNull { it.timestamp }?.let { _heartRateFlow.emit(HeartRateSample(bpm = it.bpm, timestamp = it.timestamp.toEpochMilli())) }
        allBp.maxByOrNull { it.timestamp }?.let { _bloodPressureFlow.emit(Pair(it.systolic, it.diastolic)) }
        allSpo2.maxByOrNull { it.timestamp }?.let { _spo2Flow.emit(it.percent) }
        allTemp.maxByOrNull { it.timestamp }?.let { _temperatureFlow.emit(it.celsius) }
        allHrv.maxByOrNull { it.timestamp }?.let { _hrvFlow.emit(it.hrvMs) }
        allResp.maxByOrNull { it.timestamp }?.let { _respiratoryRateFlow.emit(it.rpm) }

        val result = YcbtHistorySyncResult(
            sportRecords = sportRecords,
            sleepSessions = sleepSessions,
            heartRateRecords = allHr,
            spo2Records = allSpo2,
            temperatureRecords = allTemp,
            bloodPressureRecords = allBp,
            hrvRecords = allHrv,
            respiratoryRateRecords = allResp
        )
        _historySyncResultFlow.emit(result)
        return result
    }

    /**
     * Sincroniza secuencialmente todo el historial almacenado en la memoria flash del anillo (YCBT)
     * siguiendo el orden oficial de DataSyncUtils (05 02, 05 04, 05 06, 05 08, 05 09 + fallbacks 1A/1E).
     */
    suspend fun syncAllHistory(): YcbtHistorySyncResult = historySyncMutex.withLock {
        if (ycbtCommandCharacteristic == null || bluetoothGatt == null) {
            Log.w(TAG, "Canal YCBT no disponible para descarga de historial")
            return YcbtHistorySyncResult()
        }

        Log.i(TAG, "Iniciando descarga secuencial de historial YCBT desde el anillo...")
        _connectionState.value = BleConnectionState.SYNCING

        val sportBytes = queryHistoryBlock(YcbtConstants.QUERY_SPORT_STEPS)
        val sportRecords = YcbtHealthDecoder.decodeSport(sportBytes)
        Log.i(TAG, "Historial deportivo (05 02): ${sportRecords.size} bloques")

        val sleepBytes = queryHistoryBlock(YcbtConstants.QUERY_SLEEP)
        val sleepSessions = YcbtHealthDecoder.decodeSleep(sleepBytes)
        Log.i(TAG, "Historial de sueño (05 04): ${sleepSessions.size} sesiones")

        val hrBytes = queryHistoryBlock(YcbtConstants.QUERY_HEART_RATE)
        val hrRecords = YcbtHealthDecoder.decodeHeartRate(hrBytes)
        Log.i(TAG, "Historial HR (05 06): ${hrRecords.size} muestras")

        val bpBytes = queryHistoryBlock(YcbtConstants.QUERY_BLOOD_PRESSURE)
        val bpRecords = YcbtHealthDecoder.decodeBloodPressure(bpBytes)
        Log.i(TAG, "Historial BP (05 08): ${bpRecords.size} muestras")

        val combinedBytes = queryHistoryBlock(YcbtConstants.QUERY_COMBINED_VITALS)

        val result = buildAndEmitHistoryResult(
            sportRecords = sportRecords,
            sleepSessions = sleepSessions,
            hrRecords = hrRecords,
            bpRecords = bpRecords,
            combinedBytes = combinedBytes
        )

        _connectionState.value = BleConnectionState.CONNECTED
        return result
    }

    /**
     * Sincronización enfocada tras finalizar medición puntual en hardware (0x04 0x0E).
     * Descarga Health_HistoryAll (0x05 0x09) donde el anillo R99 almacena SpO2, HRV y RespRate,
     * además de las tablas dedicadas de Pulso (05 06) o Presión (05 08).
     */
    suspend fun syncAfterSpotMeasurement(measureType: Int): YcbtHistorySyncResult = historySyncMutex.withLock {
        if (ycbtCommandCharacteristic == null || bluetoothGatt == null) {
            return YcbtHistorySyncResult()
        }

        Log.i(TAG, "Sincronizando post-medición puntual (measureType=$measureType) vía 0x05 0x09...")
        if (measureType == YcbtConstants.MEASURE_MODE_SPO2.toInt()) {
            sendYcbtCommandSequential(YcbtCommands.queryRealBloodOxygen())
        }

        val combinedBytes = queryHistoryBlock(YcbtConstants.QUERY_COMBINED_VITALS)
        val hrRecords = if (measureType == YcbtConstants.MEASURE_MODE_HEART_RATE.toInt()) {
            YcbtHealthDecoder.decodeHeartRate(queryHistoryBlock(YcbtConstants.QUERY_HEART_RATE))
        } else emptyList()
        val bpRecords = if (measureType == YcbtConstants.MEASURE_MODE_BP.toInt()) {
            YcbtHealthDecoder.decodeBloodPressure(queryHistoryBlock(YcbtConstants.QUERY_BLOOD_PRESSURE))
        } else emptyList()

        return buildAndEmitHistoryResult(
            hrRecords = hrRecords,
            bpRecords = bpRecords,
            combinedBytes = combinedBytes
        )
    }

    private fun startSpotPollingLoop(mode: Byte) {
        spotPollingJob?.cancel()
        spotPollingJob = coroutineScope.launch(Dispatchers.IO) {
            delay(3_500L)
            while (isActive && _connectionState.value == BleConnectionState.CONNECTED) {
                if (mode == YcbtConstants.MEASURE_MODE_SPO2) {
                    sendYcbtCommandSequential(YcbtCommands.queryRealBloodOxygen())
                }
                delay(4_000L)
            }
        }
    }

    /**
     * Inicia protocolo dual de medición puntual de temperatura corporal en el anillo:
     * 1) Dispara appStartMeasurement(1, 4) (0x03 0x2F 0x01 0x04) para recepción 0x06 0x0A / 0x04 0x0E.
     * 2) Dispara appTemperatureMeasure(1) (0x03 0x18 0x01) + sondeo 0x02 0x0E / 0x02 0x20 cada 3.5s.
     */
    fun startTemperatureMeasurement() {
        Log.i(TAG, "Iniciando medición dual de temperatura corporal (0x03 0x2F mode=4 + 0x03 0x18 + polling 0x02 0x0E)...")
        coroutineScope.launch(Dispatchers.IO) {
            sendYcbtCommandSequential(YcbtCommands.triggerMeasurement(enable = true, mode = YcbtConstants.MEASURE_MODE_TEMP))
            sendYcbtCommandSequential(YcbtCommands.setTemperatureMeasure(enable = true))
        }
        startSpotPollingLoop(YcbtConstants.MEASURE_MODE_TEMP)
    }

    /**
     * Detiene protocolo dual de medición de temperatura corporal.
     */
    fun stopTemperatureMeasurement() {
        Log.i(TAG, "Deteniendo medición de temperatura corporal...")
        spotPollingJob?.cancel()
        spotPollingJob = null
        coroutineScope.launch(Dispatchers.IO) {
            sendYcbtCommandSequential(YcbtCommands.triggerMeasurement(enable = false, mode = YcbtConstants.MEASURE_MODE_TEMP))
            sendYcbtCommandSequential(YcbtCommands.setTemperatureMeasure(enable = false))
        }
    }

    fun startSpotMeasurement(mode: Byte) {
        if (mode == YcbtConstants.MEASURE_MODE_TEMP) {
            startTemperatureMeasurement()
            return
        }
        Log.i(TAG, "Iniciando medicion puntual (mode 0x${Integer.toHexString(mode.toInt() and 0xFF)})...")
        sendYcbtCommand(YcbtCommands.triggerMeasurement(enable = true, mode = mode))
        if (mode == YcbtConstants.MEASURE_MODE_SPO2 || mode == YcbtConstants.MEASURE_MODE_BP || mode == YcbtConstants.MEASURE_MODE_HRV) {
            startSpotPollingLoop(mode)
        } else {
            spotPollingJob?.cancel()
            spotPollingJob = null
        }
    }

    fun stopSpotMeasurement(mode: Byte) {
        if (mode == YcbtConstants.MEASURE_MODE_TEMP) {
            stopTemperatureMeasurement()
            return
        }
        Log.i(TAG, "Deteniendo medicion puntual (mode 0x${Integer.toHexString(mode.toInt() and 0xFF)})...")
        spotPollingJob?.cancel()
        spotPollingJob = null
        sendYcbtCommand(YcbtCommands.triggerMeasurement(enable = false, mode = mode))
    }

    private suspend fun queryHistoryBlock(queryCmd: Byte, timeoutMs: Long = 6_000L): ByteArray {
        val deferred = CompletableDeferred<ByteArray>()
        synchronized(currentQueryBuffer) {
            currentQueryBuffer.clear()
            activeQueryCmd = queryCmd
            activeQueryDeferred = deferred
        }

        val requestBytes = YcbtCommands.queryHistory(queryCmd)
        sendYcbtCommandSequential(requestBytes)

        val result = withTimeoutOrNull(timeoutMs) {
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
                spotPollingJob?.cancel()
                spotPollingJob = null
                ycbtAssembler.reset()
                activeQueryDeferred?.cancel()
                activeQueryDeferred = null
                _connectionState.value = BleConnectionState.DISCONNECTED
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            Log.d(TAG, "onDescriptorWrite callback: ${descriptor?.uuid}, status=$status")
            pendingDescriptorWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
            pendingDescriptorWrite = null
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            Log.d(TAG, "onCharacteristicWrite callback: ${characteristic?.uuid}, status=$status")
            pendingCharacteristicWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
            pendingCharacteristicWrite = null
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.i(TAG, "onMtuChanged: MTU negociado a $mtu (status=$status)")
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) {
                Log.e(TAG, "Error descubriendo servicios GATT: $status")
                _connectionState.value = BleConnectionState.ERROR
                return
            }

            Log.i(TAG, "Servicios GATT descubiertos con éxito")

            // Solicitar MTU amplio y prioridad de conexión balanceada (idéntico a SmartHealth BleHelper)
            try {
                gatt.requestMtu(517)
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED)
            } catch (e: Exception) {
                Log.w(TAG, "Error al configurar MTU/ConnectionPriority: ${e.message}")
            }

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
                // Suscribir características YCBT prioritariamente esperando confirmación GATT real
                ycbtStreamCharacteristic?.let { streamChar ->
                    enableNotificationOrIndicationSequential(gatt, streamChar)
                }

                ycbtCommandCharacteristic?.let { cmdChar ->
                    enableNotificationOrIndicationSequential(gatt, cmdChar)
                }

                // Suscribir fallbacks
                heartRateCharacteristic?.let { hrChar ->
                    enableNotificationOrIndicationSequential(gatt, hrChar)
                }

                nusTxCharacteristic?.let { nusChar ->
                    enableNotificationOrIndicationSequential(gatt, nusChar)
                }

                // Secuencia oficial de inicialización YCBT de forma estrictamente secuencial
                if (ycbtCommandCharacteristic != null) {
                    Log.i(TAG, "Ejecutando protocolo de inicialización YCBT secuencial...")

                    // 1. Sincronización horaria y unidades métricas / perfil
                    sendYcbtCommandSequential(YcbtCommands.setTime())
                    sendYcbtCommandSequential(YcbtCommands.settingUnit())
                    sendYcbtCommandSequential(YcbtCommands.settingUserInfo())

                    // 2. Consultar funciones de hardware y estado de batería / firmware
                    sendYcbtCommandSequential(YcbtCommands.getDeviceSupportFunction())
                    sendYcbtCommandSequential(YcbtCommands.getDeviceInfo())

                    // 3. Activar monitores continuos (30m por defecto) y flujo en tiempo real
                    for (cmd in YcbtCommands.enablePeriodicMonitors(intervalMinutes = 30)) {
                        sendYcbtCommandSequential(cmd)
                    }
                    sendYcbtCommandSequential(YcbtCommands.enableLiveStatusPush())

                    // 4. Consulta puntual inicial de SpO2 (0x02 0x11)
                    sendYcbtCommandSequential(YcbtCommands.queryRealBloodOxygen())

                    // 5. Sincronización inicial automática de historial (pasos, sueño, pulso, presión, SpO2/HRV/Resp)
                    coroutineScope.launch {
                        delay(1200)
                        Log.i(TAG, "Iniciando descarga inicial de historial YCBT (05 02/04/06/08/09)...")
                        syncAllHistory()
                    }
                } else {
                    // Fallback a ping NUS
                    sendSyncPing()
                }

                startKeepAlive()
            }
        }

        private suspend fun enableNotificationOrIndicationSequential(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            gatt.setCharacteristicNotification(characteristic, true)
            val descriptor = characteristic.getDescriptor(CCCD_DESCRIPTOR_UUID) ?: return

            val isIndicate = (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
            val descriptorValue = if (isIndicate) {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }

            val success = writeDescriptorSequential(gatt, descriptor, descriptorValue)
            Log.d(TAG, "Notificaciones/Indicaciones configuradas para ${characteristic.uuid} [success=$success]")
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
                    val cmdInt = frame.cmd.toInt() and 0xFF
                    val isQuerySummaryPacket = cmdInt == (activeQueryCmd?.toInt()?.and(0xFF))

                    if (frame.cmd == YcbtConstants.TERMINAL_BLOCK) {
                        val accumulated = synchronized(currentQueryBuffer) {
                            currentQueryBuffer.toByteArray()
                        }
                        var ackStatus = YcbtConstants.ACK_SUCCESS
                        if (frame.payload.size >= 6 && accumulated.isNotEmpty()) {
                            val totalBytes = YcbtUtils.readU16LE(frame.payload, 2)
                            val expectedCrc = YcbtUtils.readU16LE(frame.payload, 4)
                            val computedCrc = YcbtCrc16.calculate(accumulated, 0, accumulated.size)
                            if (totalBytes == accumulated.size && expectedCrc != 0 && expectedCrc != computedCrc) {
                                Log.w(TAG, "CRC historial (05 80) difiere [exp=0x%04X, calc=0x%04X], solicitando reenvío status=0x04".format(expectedCrc, computedCrc))
                                ackStatus = 0x04
                            }
                        }

                        // Enviar ACK secuencial por GATT ANTES de completar el deferred para evitar colisiones con la siguiente consulta
                        val deferred = activeQueryDeferred
                        coroutineScope.launch(Dispatchers.IO) {
                            Log.d(TAG, "Fin de bloque de historial (05 80). Enviando ACK=$ackStatus...")
                            sendYcbtCommandSequential(YcbtCommands.historyBlockAck(ackStatus))
                            delay(40L)
                            if (deferred != null && !deferred.isCompleted) {
                                deferred.complete(accumulated)
                            }
                        }
                    } else if (isQuerySummaryPacket) {
                        // Paquete resumen de cabecera (10 bytes: numRegistros:2, totalPaquetes:4, totalBytes:4)
                        val recordCount = if (frame.payload.size >= 2) {
                            (frame.payload[0].toInt() and 0xFF) or ((frame.payload[1].toInt() and 0xFF) shl 8)
                        } else 0
                        Log.i(TAG, "Resumen de consulta historial (05 %02X): records=$recordCount, payloadLen=${frame.payload.size}".format(cmdInt))

                        if (frame.payload.size <= 9 || recordCount == 0 || frame.isErrorCode) {
                            Log.d(TAG, "Sin registros almacenados para 05 %02X, completando consulta vacía".format(cmdInt))
                            activeQueryDeferred?.complete(ByteArray(0))
                        }
                        // No agregar cabecera resumen al buffer de registros reales
                    } else {
                        // Chunk de datos reales de historial
                        synchronized(currentQueryBuffer) {
                            for (b in frame.payload) {
                                currentQueryBuffer.add(b)
                            }
                        }
                    }
                }

                YcbtConstants.GROUP_DEV_CONTROL -> {
                    // Notificaciones de control del dispositivo (0x04)
                    val cmdInt = frame.cmd.toInt() and 0xFF
                    Log.d("YCBT_RAW", "◄◄ DEV_CONTROL (04 %02X) payload=[%s]".format(cmdInt, frame.payload.joinToString("") { "%02X".format(it) }))

                    // Responder ACK mandatorio (0x04 <cmd> 0x00)
                    sendYcbtCommand(YcbtCommands.devControlPushAck(frame.cmd))

                    when (cmdInt) {
                        0x03 -> {
                            // dataType 1027: Obturador remoto desde el anillo
                            coroutineScope.launch { _cameraShutterEventFlow.emit(Unit) }
                        }
                        0x13 -> {
                            // dataType 1043: Muestra en tiempo real o resultado intermedio de medición puntual
                            if (frame.payload.size >= 3) {
                                val type = frame.payload[0].toInt() and 0xFF
                                when (type) {
                                    0 -> { // Pulso
                                        val hr = frame.payload[2].toInt() and 0xFF
                                        if (hr in 35..230) {
                                            coroutineScope.launch { _heartRateFlow.emit(HeartRateSample(bpm = hr)) }
                                        }
                                    }
                                    1 -> { // Presión
                                        if (frame.payload.size >= 4) {
                                            val sys = frame.payload[2].toInt() and 0xFF
                                            val dia = frame.payload[3].toInt() and 0xFF
                                            if (sys in 60..250 && dia in 30..160) {
                                                coroutineScope.launch { _bloodPressureFlow.emit(Pair(sys, dia)) }
                                            }
                                        }
                                    }
                                    2 -> { // SpO2
                                        val spo2 = frame.payload[2].toInt() and 0xFF
                                        if (spo2 in 70..100) {
                                            coroutineScope.launch { _spo2Flow.emit(spo2) }
                                        }
                                    }
                                    3 -> { // Frecuencia respiratoria
                                        val resp = frame.payload[2].toInt() and 0xFF
                                        if (resp in 6..50) {
                                            coroutineScope.launch { _respiratoryRateFlow.emit(resp) }
                                        }
                                    }
                                    4 -> { // Temperatura
                                        if (frame.payload.size >= 4) {
                                            val temp = YcbtHealthDecoder.parseValidTemperature(
                                                frame.payload[2].toInt() and 0xFF,
                                                frame.payload[3].toInt() and 0xFF
                                            )
                                            if (temp != null) {
                                                Log.i(TAG, "¡Temperatura recibida vía DevControl 1043: $temp °C!")
                                                coroutineScope.launch { _temperatureFlow.emit(temp) }
                                            }
                                        }
                                    }
                                    10 -> { // HRV
                                        val hrv = frame.payload[2].toInt() and 0xFF
                                        if (hrv in 1..200) {
                                            coroutineScope.launch { _hrvFlow.emit(hrv) }
                                        }
                                    }
                                }
                            }
                        }
                        0x0E -> {
                            // dataType 1038: Finalización de medición puntual en hardware [measureType, result]
                            if (frame.payload.size >= 2) {
                                val measureType = frame.payload[0].toInt() and 0xFF
                                val result = frame.payload[1].toInt() and 0xFF
                                val isSuccess = (result == 1)
                                Log.i(TAG, "DeviceMeasurementResult (04 0E): measureType=$measureType, result=$result (success=$isSuccess)")

                                spotPollingJob?.cancel()
                                spotPollingJob = null

                                if (isSuccess) {
                                    // Primero descargar 0x05 0x09 / 0x02 0x20 antes o junto al status de fin
                                    coroutineScope.launch(Dispatchers.IO) {
                                        syncAfterSpotMeasurement(measureType)
                                        _spotMeasurementStatusFlow.emit(
                                            SpotMeasurementStatus(
                                                type = measureType,
                                                isFinished = true,
                                                isSuccess = true
                                            )
                                        )
                                    }
                                } else {
                                    coroutineScope.launch {
                                        _spotMeasurementStatusFlow.emit(
                                            SpotMeasurementStatus(
                                                type = measureType,
                                                isFinished = true,
                                                isSuccess = false
                                            )
                                        )
                                    }
                                }
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
                                is YcbtDecodedEvent.RealtimeBloodPressure -> {
                                    if (event.systolic in 60..250 && event.diastolic in 30..160) {
                                        _bloodPressureFlow.emit(Pair(event.systolic, event.diastolic))
                                    }
                                    event.bpm?.let { _heartRateFlow.emit(HeartRateSample(bpm = it)) }
                                    event.hrv?.let { _hrvFlow.emit(it) }
                                    event.spo2?.let { _spo2Flow.emit(it) }
                                    event.tempCelsius?.let { _temperatureFlow.emit(it) }
                                }
                                is YcbtDecodedEvent.RealtimeTemperature -> {
                                    _temperatureFlow.emit(event.celsius)
                                }
                                is YcbtDecodedEvent.RealtimeRespiratoryRate -> {
                                    _respiratoryRateFlow.emit(event.rpm)
                                }
                                is YcbtDecodedEvent.RealtimeComprehensive -> {
                                    if (event.steps > 0 || event.calories > 0 || event.distanceMeters > 0) {
                                        _consolidatedStepsFlow.emit(
                                            StepsSample(
                                                steps = event.steps.toInt(),
                                                calories = event.calories.toInt(),
                                                distanceMeters = event.distanceMeters.toInt()
                                            )
                                        )
                                    }
                                    if (event.bpm in 35..230) {
                                        _heartRateFlow.emit(HeartRateSample(bpm = event.bpm))
                                    }
                                    if (event.spo2 in 70..100) {
                                        _spo2Flow.emit(event.spo2)
                                    }
                                    if (event.systolic in 60..250 && event.diastolic in 30..160) {
                                        _bloodPressureFlow.emit(Pair(event.systolic, event.diastolic))
                                    }
                                    event.respiratoryRate?.let { _respiratoryRateFlow.emit(it) }
                                    event.tempCelsius?.let { _temperatureFlow.emit(it) }
                                    _sensorContactFlow.emit(event.isWorn)
                                    if (event.batteryPercent != null) {
                                        _batteryFlow.emit(Pair(event.batteryPercent, false))
                                    }
                                }
                                is YcbtDecodedEvent.SensorContactStatus -> {
                                    _sensorContactFlow.emit(event.hasContact)
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
                    val cmdInt = frame.cmd.toInt() and 0xFF
                    when (cmdInt) {
                        (YcbtConstants.CMD_GET_DEVICE_INFO.toInt() and 0xFF) -> {
                            YcbtHealthDecoder.decodeDeviceInfo(frame.payload)?.let { info ->
                                _deviceInfoFlow.value = info
                                coroutineScope.launch {
                                    _batteryFlow.emit(Pair(info.batteryPercent, info.isCharging))
                                }
                            }
                        }
                        (YcbtConstants.CMD_GET_DEVICE_SUPPORT_FUNCTION.toInt() and 0xFF) -> {
                            val caps = YcbtHealthDecoder.decodeDeviceSupportFunction(frame.payload)
                            Log.i(TAG, "Capacidades R99 [${caps.rawHex}]: ${caps.toSummaryString()}")
                            _deviceCapabilitiesFlow.value = caps
                        }
                        (YcbtConstants.CMD_GET_REAL_TEMP.toInt() and 0xFF) -> {
                            YcbtHealthDecoder.decodeGetRealTemp(frame.payload)?.let { temp ->
                                coroutineScope.launch { _temperatureFlow.emit(temp) }
                            }
                        }
                        (YcbtConstants.CMD_GET_REAL_BLOOD_OXYGEN.toInt() and 0xFF) -> {
                            YcbtHealthDecoder.decodeGetRealBloodOxygen(frame.payload)?.let { spo2 ->
                                coroutineScope.launch { _spo2Flow.emit(spo2) }
                            }
                        }
                        (YcbtConstants.CMD_GET_ALL_REAL_DATA.toInt() and 0xFF) -> {
                            YcbtHealthDecoder.decodeAllRealDataFromDevice(frame.payload)?.let { all ->
                                coroutineScope.launch {
                                    all.bpm?.let { _heartRateFlow.emit(HeartRateSample(bpm = it)) }
                                    if (all.systolic != null && all.diastolic != null) {
                                        _bloodPressureFlow.emit(Pair(all.systolic, all.diastolic))
                                    }
                                    all.spo2?.let { _spo2Flow.emit(it) }
                                    all.respiratoryRate?.let { _respiratoryRateFlow.emit(it) }
                                    all.tempCelsius?.let { _temperatureFlow.emit(it) }
                                    if (all.steps > 0 || all.calories > 0 || all.distanceMeters > 0) {
                                        _consolidatedStepsFlow.emit(
                                            StepsSample(
                                                steps = all.steps.toInt(),
                                                calories = all.calories.toInt(),
                                                distanceMeters = all.distanceMeters.toInt()
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        0x03 -> {
                            if (frame.payload.isNotEmpty()) {
                                val battery = (frame.payload[0].toInt() and 0xFF).coerceIn(0, 100)
                                coroutineScope.launch { _batteryFlow.emit(Pair(battery, false)) }
                            }
                        }
                    }
                }

                YcbtConstants.GROUP_CUSTOMIZE -> {
                    val cmdInt = frame.cmd.toInt() and 0xFF
                    if (cmdInt == (YcbtConstants.CMD_CUSTOMIZE_WIT.toInt() and 0xFF) && frame.payload.size >= 2) {
                        val opcode = frame.payload[0].toInt() and 0xFF
                        if (opcode == 2) {
                            val mask = frame.payload[1].toInt() and 0xFF
                            _activeGestureModeFlow.value = when {
                                (mask and 0x01) != 0 -> 1
                                (mask and 0x02) != 0 -> 2
                                (mask and 0x04) != 0 -> 3
                                (mask and 0x08) != 0 -> 4
                                (mask and 0x20) != 0 -> 6
                                else -> 0
                            }
                        }
                    }
                }
            }
        }
    }

    fun requestBatteryInfo() {
        Log.i(TAG, "Solicitando lectura fresca de batería al anillo...")
        sendYcbtCommand(YcbtCommands.getDeviceInfo())
    }
}
