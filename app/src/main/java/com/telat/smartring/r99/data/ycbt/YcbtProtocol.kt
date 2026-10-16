package com.telat.smartring.r99.data.ycbt

import java.time.Instant
import java.util.UUID

/**
 * Especificación de primitivas y constantes de bajo nivel del protocolo YCBT
 * (Shenzhen Yucheng Innovation Technology / SmartHealth com.yucheng.ycbtsdk v4.0.10).
 */
object YcbtConstants {
    // UUIDs del servicio y características oficiales de Yucheng
    val SERVICE_UUID: UUID = UUID.fromString("be940000-7333-be46-b7ae-689e71722bd5")
    val COMMAND_CHAR_UUID: UUID = UUID.fromString("be940001-7333-be46-b7ae-689e71722bd5") // Write + Indicate
    val STREAM_CHAR_UUID: UUID = UUID.fromString("be940003-7333-be46-b7ae-689e71722bd5")  // Indicate

    val CCCD_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Época de Yucheng: segundos desde el 01-01-2000 00:00:00 UTC (946684800s respecto a Unix epoch 1970)
    const val YCBT_EPOCH_OFFSET_SECONDS = 946684800L

    // Grupos de comandos (TYPE)
    const val GROUP_SETTING: Byte = 0x01
    const val GROUP_GET: Byte = 0x02
    const val GROUP_APP_CONTROL: Byte = 0x03
    const val GROUP_DEV_CONTROL: Byte = 0x04
    const val GROUP_HEALTH: Byte = 0x05
    const val GROUP_REAL: Byte = 0x06

    // Comandos de consulta de historial (Health Group 0x05)
    const val QUERY_SPORT_STEPS: Byte = 0x02    // Responde con tramas 0x11 (registros de 14 bytes)
    const val QUERY_SLEEP: Byte = 0x04          // Responde con tramas 0x13 (longitud variable)
    const val QUERY_HEART_RATE: Byte = 0x06     // Responde con tramas 0x15 (registros de 6 bytes)
    const val QUERY_BLOOD_PRESSURE: Byte = 0x08 // Responde con tramas 0x17 (registros de 8 bytes)
    const val QUERY_COMBINED_VITALS: Byte = 0x09 // Registro combinado de 20 bytes (pasos acumulados, spo2, pulso, temp)
    const val QUERY_SPO2: Byte = 0x1A.toByte()  // Responde con tramas 0x22 (registros de 6 bytes)
    const val QUERY_TEMP: Byte = 0x1E.toByte()  // Responde con tramas 0x26 (registros de 7 bytes)

    // Modos de medición puntual bajo demanda (AppControl 0x03, Cmd 0x2F)
    const val MEASURE_MODE_HEART_RATE: Byte = 0x00
    const val MEASURE_MODE_BP: Byte = 0x01
    const val MEASURE_MODE_SPO2: Byte = 0x02
    const val MEASURE_MODE_TEMP: Byte = 0x04

    // Clave de bloque terminal y ACK obligatorio
    const val TERMINAL_BLOCK: Byte = 0x80.toByte()
    const val ACK_SUCCESS: Byte = 0x00
}

/**
 * Representa una trama lógica validada con cabecera y payload.
 */
data class YcbtFrame(
    val type: Byte,
    val cmd: Byte,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as YcbtFrame
        if (type != other.type) return false
        if (cmd != other.cmd) return false
        if (!payload.contentEquals(other.payload)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = type.toInt()
        result = 31 * result + cmd.toInt()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}

/**
 * Cálculo oficial de CRC-16/CCITT-FALSE
 * Polinomio: 0x1021, Valor inicial: 0xFFFF, Sin reflejo de entrada/salida.
 */
object YcbtCrc16 {
    fun calculate(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var crc = 0xFFFF
        for (i in offset until (offset + length)) {
            val byte = data[i].toInt() and 0xFF
            crc = crc xor (byte shl 8)
            for (bit in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor 0x1021
                } else {
                    crc shl 1
                } and 0xFFFF
            }
        }
        return crc and 0xFFFF
    }
}

/**
 * Empaquetador de tramas con longitud total en Little-Endian y sufijo CRC-16.
 * [TYPE:1][CMD:1][LEN:2 LE][PAYLOAD:N][CRC16:2 LE]
 */
object YcbtFrameBuilder {
    fun build(type: Byte, cmd: Byte, payload: ByteArray = ByteArray(0)): ByteArray {
        val totalLength = payload.size + 6 // 4 cabecera + N payload + 2 CRC
        val frame = ByteArray(totalLength)
        frame[0] = type
        frame[1] = cmd
        frame[2] = (totalLength and 0xFF).toByte()
        frame[3] = ((totalLength shr 8) and 0xFF).toByte()

        if (payload.isNotEmpty()) {
            System.arraycopy(payload, 0, frame, 4, payload.size)
        }

        val crc = YcbtCrc16.calculate(frame, 0, totalLength - 2)
        frame[totalLength - 2] = (crc and 0xFF).toByte()
        frame[totalLength - 1] = ((crc shr 8) and 0xFF).toByte()

        return frame
    }
}

/**
 * Ensamblador de fragmentos BLE de entrada con control de desbordamiento y resincronización.
 */
class YcbtFrameAssembler {
    private val buffer = mutableListOf<Byte>()

    @Synchronized
    fun feed(chunk: ByteArray): List<YcbtFrame> {
        val completedFrames = mutableListOf<YcbtFrame>()
        for (b in chunk) {
            buffer.add(b)
        }

        while (buffer.size >= 4) {
            val totalLength = (buffer[2].toInt() and 0xFF) or ((buffer[3].toInt() and 0xFF) shl 8)
            val group = buffer[0]

            // Validación de cabecera plausible (grupos 0x01 al 0x06, longitud mínima 6 bytes y máxima 1024)
            if (group < 0x01 || group > 0x06 || totalLength < 6 || totalLength > 1024) {
                buffer.removeAt(0) // Resincronizar descartando un byte corrupto
                continue
            }

            if (buffer.size < totalLength) {
                // Trama fragmentada, esperando paquetes subsiguientes
                break
            }

            val frameBytes = ByteArray(totalLength)
            for (i in 0 until totalLength) {
                frameBytes[i] = buffer[i]
            }

            val expectedCrc = (frameBytes[totalLength - 2].toInt() and 0xFF) or
                    ((frameBytes[totalLength - 1].toInt() and 0xFF) shl 8)
            val computedCrc = YcbtCrc16.calculate(frameBytes, 0, totalLength - 2)

            if (expectedCrc == computedCrc) {
                val payloadSize = totalLength - 6
                val payload = ByteArray(payloadSize)
                if (payloadSize > 0) {
                    System.arraycopy(frameBytes, 4, payload, 0, payloadSize)
                }
                completedFrames.add(YcbtFrame(frameBytes[0], frameBytes[1], payload))
            }

            // Desencolar la trama procesada
            for (i in 0 until totalLength) {
                buffer.removeAt(0)
            }
        }

        return completedFrames
    }

    @Synchronized
    fun reset() {
        buffer.clear()
    }
}

/**
 * Utilidades de conversión de marcas de tiempo y campos numéricos.
 */
object YcbtUtils {
    fun toInstant(ringSeconds: Long): Instant {
        val epochMilli = (ringSeconds + YcbtConstants.YCBT_EPOCH_OFFSET_SECONDS) * 1000L
        return Instant.ofEpochMilli(epochMilli)
    }

    fun toRingSeconds(instant: Instant = Instant.now()): Long {
        return (instant.epochSecond - YcbtConstants.YCBT_EPOCH_OFFSET_SECONDS)
    }

    fun readU16LE(bytes: ByteArray, offset: Int): Int {
        if (bytes.size < offset + 2) return 0
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    fun readU24LE(bytes: ByteArray, offset: Int): Int {
        if (bytes.size < offset + 3) return 0
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16)
    }

    fun readU32LE(bytes: ByteArray, offset: Int): Long {
        if (bytes.size < offset + 4) return 0L
        return (bytes[offset].toLong() and 0xFF) or
                ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
                ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
                ((bytes[offset + 3].toLong() and 0xFF) shl 24)
    }
}
