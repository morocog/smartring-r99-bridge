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
    data class RealtimeBloodPressure(val systolic: Int, val diastolic: Int) : YcbtDecodedEvent()
    data class RealtimeTemperature(val celsius: Float) : YcbtDecodedEvent()
    data class RealtimeComprehensive(
        val steps: Long,
        val calories: Long,
        val distanceMeters: Long,
        val bpm: Int,
        val systolic: Int,
        val diastolic: Int,
        val spo2: Int,
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

data class YcbtHistorySyncResult(
    val sportRecords: List<YcbtSportRecord> = emptyList(),
    val sleepSessions: List<YcbtSleepSession> = emptyList(),
    val heartRateRecords: List<YcbtHeartRateRecord> = emptyList(),
    val spo2Records: List<YcbtSpo2Record> = emptyList(),
    val temperatureRecords: List<YcbtTemperatureRecord> = emptyList()
) {
    val totalCount: Int
        get() = sportRecords.size + sleepSessions.size + heartRateRecords.size + spo2Records.size + temperatureRecords.size
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
            val celsius = tempInt + (tempDec / 100f)

            if (tsSec > 0 && celsius in 30.0f..45.0f) {
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
     * Decodifica la respuesta de consulta puntual de temperatura dérmica
     * (YCBTClient.getRealTemp / Group 0x02, Cmd 0x0E / DataUnpack.unpackGetRealTemp).
     * Soporta tanto payload directo [tempInt:1B, tempFloat:1B] como con byte de status inicial.
     */
    fun decodeGetRealTemp(payload: ByteArray): Float? {
        if (payload.size >= 2) {
            val tempInt = payload[0].toInt() and 0xFF
            val tempDec = payload[1].toInt() and 0xFF
            val celsius = "$tempInt.$tempDec".toFloatOrNull() ?: (tempInt + (tempDec / 100f))
            if (celsius in 30.0f..45.0f) {
                return celsius
            }
            // Si el primer byte era un código de estado (ej: 0x00=OK) y la temperatura viene en bytes [1..2]
            if (payload.size >= 3) {
                val altTempInt = payload[1].toInt() and 0xFF
                val altTempDec = payload[2].toInt() and 0xFF
                val altCelsius = "$altTempInt.$altTempDec".toFloatOrNull() ?: (altTempInt + (altTempDec / 100f))
                if (altCelsius in 30.0f..45.0f) {
                    return altCelsius
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
                val tempInt = payload[2].toInt() and 0xFF
                val tempFloat = payload[3].toInt() and 0xFF
                val temp = "$tempInt.$tempFloat".toFloatOrNull() ?: (tempInt + (tempFloat / 100f))
                if (temp in 30.0f..45.0f) return temp
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
                // Presión arterial en vivo [systolic: 1B][diastolic: 1B]
                // Si la longitud supera 6 bytes, contiene además temperatura dérmica (DataUnpack.unpackRealBloodData)
                if (payload.size >= 2) {
                    val sys = payload[0].toInt() and 0xFF
                    val dia = payload[1].toInt() and 0xFF
                    if (sys in 50..240 && dia in 30..150) {
                        YcbtDecodedEvent.RealtimeBloodPressure(sys, dia)
                    } else null
                } else null
            }
            0x04 -> {
                // Temperatura corporal en vivo
                if (payload.size >= 2) {
                    val integerPart = payload[0].toInt() and 0xFF
                    val decimalPart = payload[1].toInt() and 0xFF
                    val temp = integerPart + (decimalPart / 100f)
                    if (temp in 30.0f..45.0f) {
                        YcbtDecodedEvent.RealtimeTemperature(temp)
                    } else null
                } else null
            }
            0x0A -> {
                // Paquete integral en tiempo real (SmartHealth Real_UploadComprehensive / dataType 1546)
                if (payload.size >= 14) {
                    val steps = (payload[0].toLong() and 0xFF) or
                            ((payload[1].toLong() and 0xFF) shl 8) or
                            ((payload[2].toLong() and 0xFF) shl 16)
                    val dist = (payload[3].toLong() and 0xFF) or ((payload[4].toLong() and 0xFF) shl 8)
                    val cal = (payload[5].toLong() and 0xFF) or ((payload[6].toLong() and 0xFF) shl 8)
                    val hr = payload[7].toInt() and 0xFF
                    val sys = payload[8].toInt() and 0xFF
                    val dia = payload[9].toInt() and 0xFF
                    val spo2 = payload[10].toInt() and 0xFF
                    val tempInt = payload[12].toInt() and 0xFF
                    val tempFloat = payload[13].toInt() and 0xFF
                    val temp = if (tempInt in 30..45) tempInt + (tempFloat / 100f) else null
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
     * Decodifica registros combinados de salud (query 0x09, registros de 20 bytes)
     * Layout: [ts:u32 LE][steps:u16 LE@4][hr:1B@6][sys:1B@7][dia:1B@8][spo2:1B@9]...
     */
    fun decodeCombinedVitals(buffer: ByteArray): List<YcbtCombinedVitalsRecord> {
        val records = mutableListOf<YcbtCombinedVitalsRecord>()
        val stride = 20
        var offset = 0

        while (offset + stride <= buffer.size) {
            val tsSec = YcbtUtils.readU32LE(buffer, offset)
            val steps = YcbtUtils.readU16LE(buffer, offset + 4)
            val hr = buffer[offset + 6].toInt() and 0xFF
            val spo2 = buffer[offset + 9].toInt() and 0xFF
            val tempInt = buffer[offset + 13].toInt() and 0xFF
            val tempFloat = buffer[offset + 14].toInt() and 0xFF
            val tempCelsius = if (tempInt in 30..45) {
                "$tempInt.$tempFloat".toFloatOrNull() ?: (tempInt + (tempFloat / 100f))
            } else null

            if (tsSec > 0) {
                records.add(
                    YcbtCombinedVitalsRecord(
                        timestamp = YcbtUtils.toInstant(tsSec),
                        steps = steps,
                        heartRate = if (hr in 35..230) hr else null,
                        spo2 = if (spo2 in 70..100) spo2 else null,
                        tempCelsius = tempCelsius
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
    val spo2: Int?,
    val tempCelsius: Float? = null
)

