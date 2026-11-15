package com.telat.smartring.r99.data.ycbt

import java.util.Calendar

/**
 * Generador de comandos lógicos y empaquetados para el Smart Ring R99 bajo protocolo YCBT.
 */
object YcbtCommands {

    /**
     * Sincroniza el reloj del anillo con la hora local actual.
     * Trama Setting: 01 00 [year:u16 LE][month][day][hour][min][sec][weekday (Mon=0..Sun=6)]
     */
    fun setTime(calendar: Calendar = Calendar.getInstance()): ByteArray {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val min = calendar.get(Calendar.MINUTE)
        val sec = calendar.get(Calendar.SECOND)

        val calDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        // Conversión a estándar YCBT: Domingo (1) -> 6, Lunes (2) -> 0, etc.
        val weekday = if (calDayOfWeek == Calendar.SUNDAY) 6 else calDayOfWeek - 2

        val payload = byteArrayOf(
            (year and 0xFF).toByte(),
            ((year shr 8) and 0xFF).toByte(),
            month.toByte(),
            day.toByte(),
            hour.toByte(),
            min.toByte(),
            sec.toByte(),
            weekday.toByte()
        )

        return YcbtFrameBuilder.build(YcbtConstants.GROUP_SETTING, 0x00, payload)
    }

    /**
     * Activa los monitores continuos periódicos en el anillo con cadencia fija de 30 minutos.
     * Clamped a >= 30 minutos por restricción de hardware en SmartHealth.
     */
    fun enable30MinMonitors(intervalMinutes: Int = 30): List<ByteArray> {
        val cadence = intervalMinutes.coerceIn(30, 120).toByte()
        val payload = byteArrayOf(0x01, cadence) // {enable=1, interval=30m}

        // Se excluye 0x1c (Presión Arterial) debido a que el hardware del R99 lo rechaza con 0xFC (no soportado)
        return listOf(
            YcbtFrameBuilder.build(YcbtConstants.GROUP_SETTING, 0x0c, payload), // Frecuencia cardíaca
            YcbtFrameBuilder.build(YcbtConstants.GROUP_SETTING, 0x20, payload), // Temperatura
            YcbtFrameBuilder.build(YcbtConstants.GROUP_SETTING, 0x26, payload)  // Saturación de oxígeno (SpO2)
        )
    }

    /**
     * Activa el flujo continuo en tiempo real de pasos y estado de actividad (06 00).
     */
    fun enableLiveStatusPush(): ByteArray {
        val payload = byteArrayOf(0x01, 0x00, 0x02)
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_APP_CONTROL, 0x09, payload)
    }

    /**
     * Consulta información de batería y versión de firmware del anillo.
     */
    fun getDeviceInfo(): ByteArray {
        val payload = byteArrayOf(0x47, 0x43)
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_GET, 0x00, payload)
    }

    /**
     * Consulta el mapa de funciones de hardware y sensores soportados por el firmware
     * (YCBTClient.getDeviceSupportFunction / Group 0x02, Cmd 0x01 / dataType 513).
     * Requiere payload ASCII "GF" (0x47, 0x46) según descompilación de SmartHealth.
     */
    fun getDeviceSupportFunction(): ByteArray {
        val payload = byteArrayOf(0x47, 0x46)
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_GET, YcbtConstants.CMD_GET_DEVICE_SUPPORT_FUNCTION, payload)
    }

    /**
     * Consulta de historial de salud (05 <queryKey> con payload vacío).
     */
    fun queryHistory(queryKey: Byte): ByteArray {
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_HEALTH, queryKey, ByteArray(0))
    }

    /**
     * ACK obligatorio de bloque terminal de historial (05 80 00).
     * Sin este ACK el anillo detiene la cola y no entrega el siguiente bloque.
     */
    fun historyBlockAck(status: Byte = YcbtConstants.ACK_SUCCESS): ByteArray {
        val payload = byteArrayOf(status)
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_HEALTH, YcbtConstants.TERMINAL_BLOCK, payload)
    }

    /**
     * ACK mandatorio para eventos y notificaciones enviadas desde el dispositivo (DevControl).
     */
    fun devControlPushAck(cmdKey: Byte): ByteArray {
        val payload = byteArrayOf(0x00)
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_DEV_CONTROL, cmdKey, payload)
    }

    /**
     * Inicia o detiene la medición de temperatura corporal según el protocolo oficial de SmartHealth
     * (YCBTClient.appTemperatureMeasure: Group 0x03, Cmd 0x18 / dataType 792).
     */
    fun setTemperatureMeasure(enable: Boolean): ByteArray {
        val payload = byteArrayOf(if (enable) 0x01 else 0x00)
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_APP_CONTROL, YcbtConstants.CMD_APP_TEMP_MEASURE, payload)
    }

    /**
     * Consulta el valor actual de temperatura dérmica en tiempo real
     * (YCBTClient.getRealTemp: Group 0x02, Cmd 0x0E / dataType 526).
     */
    fun queryRealTemp(): ByteArray {
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_GET, YcbtConstants.CMD_GET_REAL_TEMP, ByteArray(0))
    }

    /**
     * Inicia o detiene una medición puntual bajo demanda.
     * mode: 0x00=HeartRate, 0x01=BP, 0x02=SpO2, 0x04=Temp
     */
    fun triggerMeasurement(enable: Boolean, mode: Byte = 0x00): ByteArray {
        val payload = byteArrayOf(if (enable) 0x01 else 0x00, mode)
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_APP_CONTROL, 0x2f, payload)
    }
}
