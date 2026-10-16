package com.telat.smartring.r99.data.ycbt

import java.time.Instant

sealed class YcbtDecodedEvent {
    data class SportHistory(val records: List<YcbtSportRecord>) : YcbtDecodedEvent()
    data class HeartRateHistory(val records: List<YcbtHeartRateRecord>) : YcbtDecodedEvent()
    data class Spo2History(val records: List<YcbtSpo2Record>) : YcbtDecodedEvent()
    data class SleepHistory(val sessions: List<YcbtSleepSession>) : YcbtDecodedEvent()
    data class RealtimeStatus(val steps: Long, val calories: Long, val distanceMeters: Long) : YcbtDecodedEvent()
    data class RealtimeHeartRate(val bpm: Int) : YcbtDecodedEvent()
    data class RealtimeSpo2(val percent: Int) : YcbtDecodedEvent()
    data class BatteryStatus(val percent: Int, val isCharging: Boolean) : YcbtDecodedEvent()
}

data class YcbtSportRecord(
    val startTime: Instant,
    val endTime: Instant,
    val steps: Int,
    val distanceMeters: Int,
    val caloriesKcal: Int
)

data class YcbtHistorySyncResult(
    val sportRecords: List<YcbtSportRecord> = emptyList(),
    val sleepSessions: List<YcbtSleepSession> = emptyList(),
    val heartRateRecords: List<YcbtHeartRateRecord> = emptyList(),
    val spo2Records: List<YcbtSpo2Record> = emptyList()
) {
    val totalCount: Int
        get() = sportRecords.size + sleepSessions.size + heartRateRecords.size + spo2Records.size
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
     * Decodifica sesiones completas de sueño (query 0x04, longitud variable)
     * Cabecera: 20 bytes [flags:2][recordLen:u16 LE@2][start:u32 LE@4][end:u32 LE@8]
     * Segmentos: 8 bytes [tag:1B][segStart:u32 LE@1][duration:u24 LE@5]
     * tag & 0x0F: 1=Deep, 2=Light, 3=REM, 4=Awake
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
            val sessionEndSec = YcbtUtils.readU32LE(buffer, cursor + 8)

            val segmentsStart = cursor + headerLength
            val declaredSegments = (recordLength - headerLength) / segmentLength
            val availableSegments = (buffer.size - segmentsStart) / segmentLength
            val count = minOf(declaredSegments, availableSegments)

            val stages = mutableListOf<YcbtSleepStage>()
            for (i in 0 until count) {
                val segOffset = segmentsStart + i * segmentLength
                val tag = buffer[segOffset].toInt() and 0x0F
                val segStartSec = YcbtUtils.readU32LE(buffer, segOffset + 1)
                val durationSec = YcbtUtils.readU24LE(buffer, segOffset + 5).toLong()

                val stageType = when (tag) {
                    1 -> YcbtSleepStageType.DEEP
                    2 -> YcbtSleepStageType.LIGHT
                    3 -> YcbtSleepStageType.REM
                    else -> YcbtSleepStageType.AWAKE
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

            if (sessionEndSec > sessionStartSec) {
                sessions.add(
                    YcbtSleepSession(
                        startTime = YcbtUtils.toInstant(sessionStartSec),
                        endTime = YcbtUtils.toInstant(sessionEndSec),
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
}
