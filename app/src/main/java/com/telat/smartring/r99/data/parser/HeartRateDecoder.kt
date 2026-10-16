package com.telat.smartring.r99.data.parser

import com.telat.smartring.r99.data.model.HeartRateSample

class HeartRateDecoder(
    private val deduplicationWindowMillis: Long = 15_000L
) {
    private var lastRecordedBpm: Int = -1
    private var lastRecordedTimestamp: Long = 0L

    /**
     * Decodifica el paquete binario de la característica 0x2A37 (Heart Rate Measurement).
     * Retorna una instancia de HeartRateSample si la lectura es válida y aporta nueva información,
     * o null si es un valor idéntico dentro de la ventana de deduplicación.
     */
    fun decode(data: ByteArray, forceRecord: Boolean = false): HeartRateSample? {
        if (data.isEmpty()) return null

        val flags = data[0].toInt() and 0xFF
        val is16Bit = (flags and 0x01) != 0

        val bpm: Int = if (is16Bit) {
            if (data.size < 3) return null
            val lsb = data[1].toInt() and 0xFF
            val msb = data[2].toInt() and 0xFF
            (msb shl 8) or lsb
        } else {
            if (data.size < 2) return null
            data[1].toInt() and 0xFF
        }

        // Filtro de rango fisiológico razonable (30 a 240 BPM)
        if (bpm < 30 || bpm > 240) return null

        val now = System.currentTimeMillis()
        val isDifferent = bpm != lastRecordedBpm
        val isWindowExpired = (now - lastRecordedTimestamp) >= deduplicationWindowMillis

        if (forceRecord || isDifferent || isWindowExpired) {
            lastRecordedBpm = bpm
            lastRecordedTimestamp = now
            return HeartRateSample(bpm = bpm, timestamp = now)
        }

        return null
    }

    fun reset() {
        lastRecordedBpm = -1
        lastRecordedTimestamp = 0L
    }
}
