package com.telat.smartring.r99.data.ycbt

import android.util.Log
import java.time.Instant

sealed class YcbtDecodedEvent {
    data class SportHistory(val records: List<YcbtSportRecord>) : YcbtDecodedEvent()
    data class HeartRateHistory(val records: List<YcbtHeartRateRecord>) : YcbtDecodedEvent()
    data class Spo2History(val records: List<YcbtSpo2Record>) : YcbtDecodedEvent()
    data class SleepHistory(val sessions: List<YcbtSleepSession>) : YcbtDecodedEvent()
    data class TemperatureHistory(val records: List<YcbtTemperatureRecord>) : YcbtDecodedEvent()
    data class RealtimeStatus(val steps: Long, val calories: Long, val distanceMeters: Long) : YcbtDecodedEvent()
    data class RealtimeHeartRate(val bpm: Int) : YcbtDecodedEvent()
    data class RealtimeSpo2(val percent: Int) : YcbtDecodedEvent()
    data class RealtimeBloodPressure(
        val systolic: Int,
        val diastolic: Int,
        val bpm: Int? = null,
        val hrv: Int? = null,
        val spo2: Int? = null,
        val tempCelsius: Float? = null
    ) : YcbtDecodedEvent()
    data class RealtimeTemperature(val celsius: Float) : YcbtDecodedEvent()
    data class RealtimeRespiratoryRate(val rpm: Int) : YcbtDecodedEvent()
    data class RealtimeComprehensive(
        val steps: Long,
        val calories: Long,
        val distanceMeters: Long,
        val bpm: Int,
        val systolic: Int,
        val diastolic: Int,
        val spo2: Int,
        val respiratoryRate: Int?,
        val tempCelsius: Float?,
        val isWorn: Boolean,
        val batteryPercent: Int?
    ) : YcbtDecodedEvent()
    data class SensorContactStatus(val hasContact: Boolean) : YcbtDecodedEvent()
    data class BatteryStatus(val percent: Int, val isCharging: Boolean) : YcbtDecodedEvent()
}

data class YcbtSportRecord(
    val startTime: Instant,
    val endTime: Instant,
    val steps: Int,
    val distanceMeters: Int,
    val caloriesKcal: Int
)

data class YcbtTemperatureRecord(
    val timestamp: Instant,
    val celsius: Float
)

data class YcbtBloodPressureRecord(
    val timestamp: Instant,
    val systolic: Int,
    val diastolic: Int
)

data class YcbtHrvRecord(
    val timestamp: Instant,
    val hrvMs: Int
)

data class YcbtRespiratoryRateRecord(
    val timestamp: Instant,
    val rpm: Int
)

data class YcbtDeviceInfo(
    val deviceId: Int,
    val firmwareVersion: String,
    val batteryState: Int,
    val batteryPercent: Int,
    val hardwareType: Int
) {
    val isCharging: Boolean get() = batteryState == 2
}

data class YcbtDeviceCapabilities(
    val hasStepCount: Boolean = false,
    val hasSleep: Boolean = false,
    val hasHeartRate: Boolean = false,
    val hasBloodPressure: Boolean = false,
    val hasBloodPressureCalibration: Boolean = false,
    val hasSpo2: Boolean = false,
    val hasRespiratoryRate: Boolean = false,
    val hasHrv: Boolean = false,
    val hasTemperature: Boolean = false,
    val hasTempAxillaryTest: Boolean = false,
    val hasTestHeart: Boolean = false,
    val hasTestBlood: Boolean = false,
    val hasTestSpo2: Boolean = false,
    val hasTestTemp: Boolean = false,
    val hasTestRespiratoryRate: Boolean = false,
    val hasBloodSugar: Boolean = false,
    val hasPressure: Boolean = false,
    val hasFindDevice: Boolean = false,
    val rawHex: String = ""
) {
    fun toSummaryString(): String {
        val features = mutableListOf<String>()
        if (hasStepCount) features.add("Pasos")
        if (hasSleep) features.add("Sueño")
        if (hasHeartRate || hasTestHeart) features.add("Pulso")
        if (hasSpo2 || hasTestSpo2) features.add("SpO2")
        if (hasTemperature || hasTestTemp || hasTempAxillaryTest) features.add("Temp")
        if (hasBloodPressure || hasTestBlood) features.add("Presión")
        if (hasHrv) features.add("HRV")
        if (hasRespiratoryRate || hasTestRespiratoryRate) features.add("Resp")
        if (hasPressure) features.add("Estrés")
        if (hasBloodSugar) features.add("Glucosa")
        return if (features.isEmpty()) "Estándar YCBT" else features.joinToString(" · ")
    }
}

data class YcbtAllRealData(
    val bpm: Int?,
    val systolic: Int?,
    val diastolic: Int?,
    val spo2: Int?,
    val respiratoryRate: Int?,
    val tempCelsius: Float?,
    val steps: Long,
    val calories: Long,
    val distanceMeters: Long
)

data class YcbtHistorySyncResult(
    val sportRecords: List<YcbtSportRecord> = emptyList(),
    val sleepSessions: List<YcbtSleepSession> = emptyList(),
    val heartRateRecords: List<YcbtHeartRateRecord> = emptyList(),
    val spo2Records: List<YcbtSpo2Record> = emptyList(),
    val temperatureRecords: List<YcbtTemperatureRecord> = emptyList(),
    val bloodPressureRecords: List<YcbtBloodPressureRecord> = emptyList(),
    val hrvRecords: List<YcbtHrvRecord> = emptyList(),
    val respiratoryRateRecords: List<YcbtRespiratoryRateRecord> = emptyList()
) {
    val totalCount: Int
        get() = sportRecords.size + sleepSessions.size + heartRateRecords.size +
                spo2Records.size + temperatureRecords.size + bloodPressureRecords.size +
                hrvRecords.size + respiratoryRateRecords.size
}

data class YcbtHeartRateRecord(
    val timestamp: Instant,
    val bpm: Int
)

data class YcbtSpo2Record(
    val timestamp: Instant,
    val percent: Int
)

enum class YcbtSleepStageType {
    DEEP, LIGHT, REM, AWAKE
}

data class YcbtSleepStage(
    val startTime: Instant,
    val durationSeconds: Long,
    val type: YcbtSleepStageType
)

data class YcbtSleepSession(
    val startTime: Instant,
    val endTime: Instant,
    val stages: List<YcbtSleepStage>
)

object YcbtHealthDecoder {

    /**
     * Valida y formatea temperatura dérmica según DataUnpack + TemperatureActivity:
     * Yucheng usa tempFloat == 15 (0x0F) como centinela de lectura inválida y concatena "$tempInt.$tempFloat".
     */
    fun parseValidTemperature(tempInt: Int, tempFloat: Int): Float? {
        if (tempFloat == 15 || tempInt !in 32..43 || tempFloat !in 0..99) return null
        val parsed = "$tempInt.$tempFloat".toFloatOrNull() ?: return null
        return if (parsed in 32.0f..43.0f) parsed else null
    }

    /**
     * Decodifica registros de historial deportivo/pasos (query 0x02, registros de 14 bytes)
     * Layout: [start:u32 LE][end:u32 LE][steps:u16 LE@8][dist:u16 LE@10][cal:u16 LE@12]
     */
    fun decodeSport(buffer: ByteArray): List<YcbtSportRecord> {
        val records = mutableListOf<YcbtSportRecord>()
        val stride = 14
        var offset = 0

        while (offset + stride <= buffer.size) {
            val startSec = YcbtUtils.readU32LE(buffer, offset)
            val endSec = YcbtUtils.readU32LE(buffer, offset + 4)
            val steps = YcbtUtils.readU16LE(buffer, offset + 8)
            val dist = YcbtUtils.readU16LE(buffer, offset + 10)
            val cal = YcbtUtils.readU16LE(buffer, offset + 12)

            if (steps > 0 || dist > 0) {
                records.add(
                    YcbtSportRecord(
                        startTime = YcbtUtils.toInstant(startSec),
                        endTime = YcbtUtils.toInstant(endSec),
                        steps = steps,
                        distanceMeters = dist,
                        caloriesKcal = cal
                    )
                )
            }
            offset += stride
        }

        return records
    }

    /**
     * Decodifica registros de frecuencia cardíaca (query 0x06, registros de 6 bytes)
     * Layout: [ts:u32 LE][mode:1B][hr:1B]
     */
    fun decodeHeartRate(buffer: ByteArray): List<YcbtHeartRateRecord> {
        val records = mutableListOf<YcbtHeartRateRecord>()
        val stride = 6
        var offset = 0

        while (offset + stride <= buffer.size) {
            val tsSec = YcbtUtils.readU32LE(buffer, offset)
            val hr = buffer[offset + 5].toInt() and 0xFF

            if (hr in 35..230) {
                records.add(
                    YcbtHeartRateRecord(
                        timestamp = YcbtUtils.toInstant(tsSec),
                        bpm = hr
                    )
                )
            }
            offset += stride
        }

        return records
    }

    /**
     * Decodifica registros de SpO2 (query 0x1A, registros de 6 bytes)
     * Layout: [ts:u32 LE][type:1B][spo2:1B]
     */
    fun decodeSpo2(buffer: ByteArray): List<YcbtSpo2Record> {
        val records = mutableListOf<YcbtSpo2Record>()
        val stride = 6
        var offset = 0

        while (offset + stride <= buffer.size) {
            val tsSec = YcbtUtils.readU32LE(buffer, offset)
            val spo2 = buffer[offset + 5].toInt() and 0xFF

            if (spo2 in 70..100) {
                records.add(
                    YcbtSpo2Record(
                        timestamp = YcbtUtils.toInstant(tsSec),
                        percent = spo2
                    )
                )
            }
            offset += stride
        }

        return records
    }

    /**
     * Decodifica registros de presión arterial almacenados (query 0x08, registros de 8 bytes)
     * Layout SmartHealth (DataUnpack case 8): [ts:u32 LE][isInflated:1B][sbp:1B@5][dbp:1B@6][hr:1B@7]
     */
    fun decodeBloodPressure(buffer: ByteArray): List<YcbtBloodPressureRecord> {
        val records = mutableListOf<YcbtBloodPressureRecord>()
        val stride = 8
        var offset = 0

        while (offset + stride <= buffer.size) {
            val tsSec = YcbtUtils.readU32LE(buffer, offset)
            val sbp = buffer[offset + 5].toInt() and 0xFF
            val dbp = buffer[offset + 6].toInt() and 0xFF
            val pulsePressure = sbp - dbp

            if (tsSec > 0 && sbp in 60..250 && dbp in 30..160 && pulsePressure in 10..90) {
                records.add(
                    YcbtBloodPressureRecord(
                        timestamp = YcbtUtils.toInstant(tsSec),
                        systolic = sbp,
                        diastolic = dbp
                    )
                )
            }
            offset += stride
        }

        return records
    }

    /**
     * Decodifica registros de temperatura dérmica almacenados (query 0x1E, registros de 7 bytes)
     * Layout SmartHealth (case 30): [ts:u32 LE][type:1B][tempInt:1B][tempDec:1B]
     */
    fun decodeTemperature(buffer: ByteArray): List<YcbtTemperatureRecord> {
        val records = mutableListOf<YcbtTemperatureRecord>()
        val stride = 7
        var offset = 0

        while (offset + stride <= buffer.size) {
            val tsSec = YcbtUtils.readU32LE(buffer, offset)
            val tempInt = buffer[offset + 5].toInt() and 0xFF
            val tempDec = buffer[offset + 6].toInt() and 0xFF
            val celsius = parseValidTemperature(tempInt, tempDec)

            if (tsSec > 0 && celsius != null) {
                records.add(
                    YcbtTemperatureRecord(
                        timestamp = YcbtUtils.toInstant(tsSec),
                        celsius = celsius
                    )
                )
            }
            offset += stride
        }

        return records
    }

    /**
     * Decodifica información de hardware, firmware y batería (Group 0x02, Cmd 0x00 / DataUnpack.unpackDeviceInfoData).
     * bArr[0..1]=deviceId, bArr[2]=subVer, bArr[3]=mainVer, bArr[4]=batteryState (2=charging), bArr[5]=batteryPercent.
     */
    fun decodeDeviceInfo(payload: ByteArray): YcbtDeviceInfo? {
        if (payload.size < 6) return null
        val deviceId = YcbtUtils.readU16LE(payload, 0)
        val subVer = payload[2].toInt() and 0xFF
        val mainVer = payload[3].toInt() and 0xFF
        val batteryState = payload[4].toInt() and 0xFF
        val batteryPercent = (payload[5].toInt() and 0xFF).coerceIn(0, 100)
        val versionStr = if (subVer < 10) "$mainVer.0$subVer" else "$mainVer.$subVer"
        val hardwareType = if (payload.size >= 24) payload[18].toInt() and 0xFF else 1
        return YcbtDeviceInfo(
            deviceId = deviceId,
            firmwareVersion = versionStr,
            batteryState = batteryState,
            batteryPercent = batteryPercent,
            hardwareType = hardwareType
        )
    }

    /**
     * Decodifica el mapa exacto de capacidades soportadas por el anillo
     * (Group 0x02, Cmd 0x01 / DataUnpack.saveDeviceSupportFunctionData).
     */
    fun decodeDeviceSupportFunction(payload: ByteArray): YcbtDeviceCapabilities {
        if (payload.size < 12) {
            return YcbtDeviceCapabilities(rawHex = payload.joinToString("") { "%02X".format(it) })
        }
        val b0 = payload[0].toInt() and 0xFF
        val b1 = payload[1].toInt() and 0xFF
        val b6 = payload[6].toInt() and 0xFF
        val b8 = payload[8].toInt() and 0xFF
        val b15 = if (payload.size >= 18) payload[15].toInt() and 0xFF else 0
        val b17 = if (payload.size >= 18) payload[17].toInt() and 0xFF else 0
        val b22 = if (payload.size >= 23) payload[22].toInt() and 0xFF else 0

        return YcbtDeviceCapabilities(
            hasStepCount = ((b0 shr 7) and 1) != 0,
            hasSleep = ((b0 shr 6) and 1) != 0,
            hasHeartRate = ((b0 shr 3) and 1) != 0,
            hasBloodPressure = (b0 and 1) != 0,
            hasBloodPressureCalibration = ((b8 shr 4) and 1) != 0,
            hasSpo2 = ((b1 shr 3) and 1) != 0,
            hasRespiratoryRate = ((b1 shr 2) and 1) != 0,
            hasHrv = ((b1 shr 1) and 1) != 0,
            hasTemperature = (b8 and 1) != 0,
            hasTempAxillaryTest = ((b8 shr 6) and 1) != 0,
            hasTestHeart = ((b15 shr 1) and 1) != 0,
            hasTestBlood = ((b15 shr 2) and 1) != 0,
            hasTestSpo2 = ((b15 shr 3) and 1) != 0,
            hasTestTemp = ((b15 shr 4) and 1) != 0,
            hasTestRespiratoryRate = ((b15 shr 5) and 1) != 0,
            hasBloodSugar = ((b17 shr 3) and 1) != 0,
            hasPressure = ((b22 shr 6) and 1) != 0,
            hasFindDevice = ((b6 shr 4) and 1) != 0,
            rawHex = payload.joinToString("") { "%02X".format(it) }
        )
    }

    /**
     * Decodifica snapshot instantáneo completo del anillo (0x02 0x20 / DataUnpack.unpackGetAllRealDataFromDevice).
     */
    fun decodeAllRealDataFromDevice(payload: ByteArray): YcbtAllRealData? {
        if (payload.size < 14) return null
        val hr = payload[0].toInt() and 0xFF
        val sbp = payload[1].toInt() and 0xFF
        val dbp = payload[2].toInt() and 0xFF
        val spo2 = payload[3].toInt() and 0xFF
        val respRate = payload[4].toInt() and 0xFF
        val temp = parseValidTemperature(payload[5].toInt() and 0xFF, payload[6].toInt() and 0xFF)
        val steps = YcbtUtils.readU24LE(payload, 7).toLong()
        val cal = YcbtUtils.readU16LE(payload, 10).toLong()
        val dist = YcbtUtils.readU16LE(payload, 12).toLong()
        val validBp = sbp in 60..250 && dbp in 30..160 && (sbp - dbp) in 10..90
        return YcbtAllRealData(
            bpm = if (hr in 35..230) hr else null,
            systolic = if (validBp) sbp else null,
            diastolic = if (validBp) dbp else null,
            spo2 = if (spo2 in 70..100) spo2 else null,
            respiratoryRate = if (respRate in 6..50) respRate else null,
            tempCelsius = temp,
            steps = steps,
            calories = cal,
            distanceMeters = dist
        )
    }

    /**
     * Decodifica respuesta de SpO2 en tiempo real (0x02 0x11 / DataUnpack.unpackGetRealBloodOxygen).
     * Layout: [bloodOxygenIsTest:1B][bloodOxygenValue:1B].
     */
    fun decodeGetRealBloodOxygen(payload: ByteArray): Int? {
        if (payload.size >= 2) {
            val val1 = payload[1].toInt() and 0xFF
            if (val1 in 70..100) return val1
        }
        if (payload.isNotEmpty()) {
            val val0 = payload[0].toInt() and 0xFF
            if (val0 in 70..100) return val0
        }
        return null
    }

    /**
     * Decodifica la respuesta de consulta puntual de temperatura dérmica
     * (YCBTClient.getRealTemp / Group 0x02, Cmd 0x0E / DataUnpack.unpackGetRealTemp).
     * Soporta tanto payload directo [tempInt:1B, tempFloat:1B] como con byte de status inicial.
     */
    fun decodeGetRealTemp(payload: ByteArray): Float? {
        if (payload.size >= 2) {
            parseValidTemperature(payload[0].toInt() and 0xFF, payload[1].toInt() and 0xFF)?.let {
                return it
            }
            // Si el primer byte era un código de estado (ej: 0x00=OK) y la temperatura viene en bytes [1..2]
            if (payload.size >= 3) {
                parseValidTemperature(payload[1].toInt() and 0xFF, payload[2].toInt() and 0xFF)?.let {
                    return it
                }
            }
        }
        return null
    }

    /**
     * Decodifica notificación del dispositivo sobre resultados de medición puntual
     * (SmartHealth DataUnpack.unpackParseData para dataType 1038 [0x04 0x0E] y dataType 1043 [0x04 0x13]).
     */
    fun decodeDevMeasurementResult(cmd: Byte, payload: ByteArray): Float? {
        val cmdInt = cmd.toInt() and 0xFF
        if (cmdInt == 0x13 && payload.size >= 4) {
            // dataType 1043: [type:1B][state:1B][tempInt:1B][tempFloat:1B] cuando type == 4 (Temp)
            val type = payload[0].toInt() and 0xFF
            if (type == 4) {
                return parseValidTemperature(payload[2].toInt() and 0xFF, payload[3].toInt() and 0xFF)
            }
        } else if (cmdInt == 0x0E && payload.size >= 2) {
            // dataType 1038: [measureType:1B][result:1B] (measureType 4 = Temp, result 1 = Success)
            val measureType = payload[0].toInt() and 0xFF
            val result = payload[1].toInt() and 0xFF
            Log.d("YCBT_RAW", "DeviceMeasurementResult (04 0E): measureType=$measureType, result=$result")
        }
        return null
    }

    /**
     * Decodifica sesiones completas de sueño (query 0x04, longitud variable)
     * Cabecera SmartHealth (case 4): 20 bytes [flags:2][recordLen:u16 LE@2][start:u32 LE@4][end:u32 LE@8]
     * Segmentos: 8 bytes [sleepType:1B][segStart:u32 LE@1][duration:u24 LE@5]
     * sleepType: 241/1=Deep, 242/2=Light, 243/3=REM, 244/4=Awake
     */
    fun decodeSleep(buffer: ByteArray): List<YcbtSleepSession> {
        val sessions = mutableListOf<YcbtSleepSession>()
        val headerLength = 20
        val segmentLength = 8
        var cursor = 0

        while (cursor + headerLength <= buffer.size) {
            val recordLength = YcbtUtils.readU16LE(buffer, cursor + 2)
            if (recordLength < headerLength) break

            val sessionStartSec = YcbtUtils.readU32LE(buffer, cursor + 4)
            var sessionEndSec = YcbtUtils.readU32LE(buffer, cursor + 8)

            val segmentsStart = cursor + headerLength
            val declaredSegments = (recordLength - headerLength) / segmentLength
            val availableSegments = (buffer.size - segmentsStart) / segmentLength
            val count = minOf(declaredSegments, availableSegments)

            val stages = mutableListOf<YcbtSleepStage>()
            for (i in 0 until count) {
                val segOffset = segmentsStart + i * segmentLength
                val rawTag = buffer[segOffset].toInt() and 0xFF
                val segStartSec = YcbtUtils.readU32LE(buffer, segOffset + 1)
                val durationSec = YcbtUtils.readU24LE(buffer, segOffset + 5).toLong()

                val stageType = when (rawTag) {
                    YcbtConstants.SLEEP_TAG_DEEP, 1 -> YcbtSleepStageType.DEEP
                    YcbtConstants.SLEEP_TAG_LIGHT, 2 -> YcbtSleepStageType.LIGHT
                    YcbtConstants.SLEEP_TAG_REM, 3 -> YcbtSleepStageType.REM
                    YcbtConstants.SLEEP_TAG_AWAKE, 4 -> YcbtSleepStageType.AWAKE
                    else -> when (rawTag and 0x0F) {
                        1 -> YcbtSleepStageType.DEEP
                        2 -> YcbtSleepStageType.LIGHT
                        3 -> YcbtSleepStageType.REM
                        else -> YcbtSleepStageType.AWAKE
                    }
                }

                if (durationSec > 0) {
                    stages.add(
                        YcbtSleepStage(
                            startTime = YcbtUtils.toInstant(segStartSec),
                            durationSeconds = durationSec,
                            type = stageType
                        )
                    )
                }
            }

            // Si sessionEndSec es inconsistente, inferir fin a partir de la última fase de sueño
            val calculatedEndSec = if (stages.isNotEmpty()) {
                val lastStage = stages.maxByOrNull { it.startTime }!!
                val lastStageSec = lastStage.startTime.epochSecond - YcbtConstants.YCBT_EPOCH_OFFSET_SECONDS + lastStage.durationSeconds
                maxOf(sessionEndSec, lastStageSec)
            } else {
                sessionEndSec
            }

            if (calculatedEndSec > sessionStartSec && stages.isNotEmpty()) {
                sessions.add(
                    YcbtSleepSession(
                        startTime = YcbtUtils.toInstant(sessionStartSec),
                        endTime = YcbtUtils.toInstant(calculatedEndSec),
                        stages = stages
                    )
                )
            }

            cursor += recordLength
        }

        return sessions
    }

    /**
     * Decodifica flujo continuo en tiempo real (Grupo 0x06)
     */
    fun decodeRealtime(cmd: Byte, payload: ByteArray): YcbtDecodedEvent? {
        return when (cmd.toInt() and 0xFF) {
            0x00 -> {
                // Pasos, calorías y distancia
                if (payload.size >= 12) {
                    val steps = YcbtUtils.readU32LE(payload, 0)
                    val cal = YcbtUtils.readU32LE(payload, 4)
                    val dist = YcbtUtils.readU32LE(payload, 8)
                    YcbtDecodedEvent.RealtimeStatus(steps, cal, dist)
                } else if (payload.size >= 6) {
                    val steps = YcbtUtils.readU16LE(payload, 0).toLong()
                    val dist = YcbtUtils.readU16LE(payload, 2).toLong()
                    val cal = YcbtUtils.readU16LE(payload, 4).toLong()
                    YcbtDecodedEvent.RealtimeStatus(steps, cal, dist)
                } else if (payload.size >= 2) {
                    val steps = YcbtUtils.readU16LE(payload, 0).toLong()
                    YcbtDecodedEvent.RealtimeStatus(steps, 0, 0)
                } else null
            }
            0x01 -> {
                // Frecuencia cardíaca en vivo
                if (payload.isNotEmpty()) {
                    val hr = payload[0].toInt() and 0xFF
                    if (hr in 35..230) YcbtDecodedEvent.RealtimeHeartRate(hr) else null
                } else null
            }
            0x02 -> {
                // SpO2 en vivo
                if (payload.isNotEmpty()) {
                    val spo2 = payload[0].toInt() and 0xFF
                    if (spo2 in 70..100) YcbtDecodedEvent.RealtimeSpo2(spo2) else null
                } else null
            }
            0x03 -> {
                // DataUnpack.unpackRealBloodData: [sbp:1B][dbp:1B][hr:1B][hrv:1B?][spo2:1B?][tempInt:1B?][tempFloat:1B?]
                if (payload.size >= 2) {
                    val sys = payload[0].toInt() and 0xFF
                    val dia = payload[1].toInt() and 0xFF
                    val hr = if (payload.size >= 3) (payload[2].toInt() and 0xFF).takeIf { it in 35..230 } else null
                    val hrv = if (payload.size > 3) (payload[3].toInt() and 0xFF).takeIf { it in 10..200 } else null
                    val spo2 = if (payload.size > 4) (payload[4].toInt() and 0xFF).takeIf { it in 70..100 } else null
                    val temp = if (payload.size > 6) {
                        parseValidTemperature(payload[5].toInt() and 0xFF, payload[6].toInt() and 0xFF)
                    } else null

                    val validBp = sys in 60..250 && dia in 30..160 && (sys - dia) in 10..90
                    if (validBp || hr != null || hrv != null || spo2 != null || temp != null) {
                        YcbtDecodedEvent.RealtimeBloodPressure(
                            systolic = if (validBp) sys else 0,
                            diastolic = if (validBp) dia else 0,
                            bpm = hr,
                            hrv = hrv,
                            spo2 = spo2,
                            tempCelsius = temp
                        )
                    } else null
                } else null
            }
            0x04 -> {
                // 0x06 0x04 es Real_UploadPPG (onda óptica raw, NO temperatura)
                null
            }
            0x07 -> {
                // Frecuencia respiratoria en vivo (Real_UploadRespiratoryRate)
                if (payload.isNotEmpty()) {
                    val rpm = payload[0].toInt() and 0xFF
                    if (rpm in 6..50) YcbtDecodedEvent.RealtimeRespiratoryRate(rpm) else null
                } else null
            }
            0x0A -> {
                // Paquete integral en tiempo real (SmartHealth Real_UploadComprehensive / dataType 1546)
                if (payload.size >= 14) {
                    val steps = YcbtUtils.readU24LE(payload, 0).toLong()
                    val dist = YcbtUtils.readU16LE(payload, 3).toLong()
                    val cal = YcbtUtils.readU16LE(payload, 5).toLong()
                    val hr = payload[7].toInt() and 0xFF
                    val sys = payload[8].toInt() and 0xFF
                    val dia = payload[9].toInt() and 0xFF
                    val spo2 = payload[10].toInt() and 0xFF
                    val respRate = (payload[11].toInt() and 0xFF).takeIf { it in 6..50 }
                    val temp = parseValidTemperature(payload[12].toInt() and 0xFF, payload[13].toInt() and 0xFF)
                    val isWorn = if (payload.size > 14) (payload[14].toInt() and 0xFF) != 0 else true
                    val battery = if (payload.size > 15) (payload[15].toInt() and 0xFF).coerceIn(0, 100) else null

                    YcbtDecodedEvent.RealtimeComprehensive(
                        steps = steps,
                        calories = cal,
                        distanceMeters = dist,
                        bpm = hr,
                        systolic = sys,
                        diastolic = dia,
                        spo2 = spo2,
                        respiratoryRate = respRate,
                        tempCelsius = temp,
                        isWorn = isWorn,
                        batteryPercent = battery
                    )
                } else null
            }
            0x0B, 0x16 -> {
                // Detección de contacto con piel / desgaste
                if (payload.isNotEmpty()) {
                    val contact = (payload[0].toInt() and 0xFF) != 0
                    YcbtDecodedEvent.SensorContactStatus(contact)
                } else null
            }
            0x15 -> {
                // Batería en vivo [chargingStatus:1B][percent:1B]
                if (payload.size >= 2) {
                    val isCharging = payload[0].toInt() != 0
                    val percent = (payload[1].toInt() and 0xFF).coerceIn(0, 100)
                    YcbtDecodedEvent.BatteryStatus(percent, isCharging)
                } else null
            }
            else -> null
        }
    }

    /**
     * Decodifica registros combinados de salud (query 0x09 Health_HistoryAll, registros de 20 bytes)
     * Layout DataUnpack (case 9):
     * [ts:u32 LE@0][steps:u16 LE@4][hr:1B@6][sys:1B@7][dia:1B@8][spo2:1B@9]
     * [resp:1B@10][hrv:1B@11][cvrr:1B@12][tempInt:1B@13][tempFloat:1B@14][fatInt:1B@15][fatFloat:1B@16][sugar:1B@17]
     */
    fun decodeCombinedVitals(buffer: ByteArray): List<YcbtCombinedVitalsRecord> {
        val records = mutableListOf<YcbtCombinedVitalsRecord>()
        val stride = 20
        var offset = 0

        while (offset + stride <= buffer.size) {
            val tsSec = YcbtUtils.readU32LE(buffer, offset)
            val steps = YcbtUtils.readU16LE(buffer, offset + 4)
            val hr = buffer[offset + 6].toInt() and 0xFF
            val sys = buffer[offset + 7].toInt() and 0xFF
            val dia = buffer[offset + 8].toInt() and 0xFF
            val spo2 = buffer[offset + 9].toInt() and 0xFF
            val respRate = buffer[offset + 10].toInt() and 0xFF
            val hrv = buffer[offset + 11].toInt() and 0xFF
            val cvrr = buffer[offset + 12].toInt() and 0xFF
            val tempInt = buffer[offset + 13].toInt() and 0xFF
            val tempFloat = buffer[offset + 14].toInt() and 0xFF
            val bloodSugar = buffer[offset + 17].toInt() and 0xFF
            val tempCelsius = parseValidTemperature(tempInt, tempFloat)
            val validBp = sys in 60..250 && dia in 30..160 && (sys - dia) in 10..90

            if (tsSec > 0) {
                records.add(
                    YcbtCombinedVitalsRecord(
                        timestamp = YcbtUtils.toInstant(tsSec),
                        steps = steps,
                        heartRate = if (hr in 35..230) hr else null,
                        systolic = if (validBp) sys else null,
                        diastolic = if (validBp) dia else null,
                        spo2 = if (spo2 in 70..100) spo2 else null,
                        respiratoryRate = if (respRate in 6..50) respRate else null,
                        hrv = if (hrv in 1..200) hrv else null,
                        cvrr = if (cvrr in 1..200) cvrr else null,
                        tempCelsius = tempCelsius,
                        bloodSugarRaw = if (bloodSugar in 11..333) bloodSugar else null
                    )
                )
            }
            offset += stride
        }
        return records
    }
}

data class YcbtCombinedVitalsRecord(
    val timestamp: Instant,
    val steps: Int,
    val heartRate: Int?,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val spo2: Int?,
    val respiratoryRate: Int? = null,
    val hrv: Int? = null,
    val cvrr: Int? = null,
    val tempCelsius: Float? = null,
    val bloodSugarRaw: Int? = null
)

