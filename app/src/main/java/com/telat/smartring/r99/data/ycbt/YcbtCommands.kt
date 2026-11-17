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
     * Activa los monitores continuos periódicos en el anillo (cadencia configurable 10..60 minutos).
     * Basado en MeHealthSettingActivity + YCBTClient (0x01 0x0C HR, 0x01 0x20 Temp, 0x01 0x26 SpO2).
     */
    fun enablePeriodicMonitors(intervalMinutes: Int = 30): List<ByteArray> {
        val cadence = intervalMinutes.coerceIn(10, 60).toByte()
        val payload = byteArrayOf(0x01, cadence)

        // Se excluye 0x1c (Presión Arterial) debido a que el hardware del R99 lo rechaza con 0xFC (no soportado)
        return listOf(
            YcbtFrameBuilder.build(YcbtConstants.GROUP_SETTING, YcbtConstants.CMD_SET_HEART_MONITOR, payload),
            YcbtFrameBuilder.build(YcbtConstants.GROUP_SETTING, YcbtConstants.CMD_SET_TEMP_MONITOR, payload),
            YcbtFrameBuilder.build(YcbtConstants.GROUP_SETTING, YcbtConstants.CMD_SET_SPO2_MONITOR, payload)
        )
    }

    /**
     * Compatibilidad con el nombre anterior: delega a [enablePeriodicMonitors].
     */
    fun enable30MinMonitors(intervalMinutes: Int = 30): List<ByteArray> {
        return enablePeriodicMonitors(intervalMinutes)
    }

    /**
     * Configura metas diarias (goalType: 0=Pasos, 3=Sueño en minutos).
     * YCBTClient.settingGoal -> Group 0x01, Cmd 0x02.
     */
    fun settingGoal(goalType: Int, target: Int, sleepHour: Int = 8, sleepMinute: Int = 0): ByteArray {
        val payload = byteArrayOf(
            (goalType and 0xFF).toByte(),
            (target and 0xFF).toByte(),
            ((target shr 8) and 0xFF).toByte(),
            ((target shr 16) and 0xFF).toByte(),
            ((target shr 24) and 0xFF).toByte(),
            (sleepHour and 0xFF).toByte(),
            (sleepMinute and 0xFF).toByte()
        )
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_SETTING, YcbtConstants.CMD_SET_GOAL, payload)
    }

    /**
     * Configura perfil biométrico del usuario para calibración de algoritmos (kcal, pasos, BP).
     * YCBTClient.settingUserInfo -> Group 0x01, Cmd 0x03.
     */
    fun settingUserInfo(heightCm: Int = 175, weightKg: Int = 75, sex: Int = 0, age: Int = 35): ByteArray {
        val payload = byteArrayOf(
            heightCm.coerceIn(80, 230).toByte(),
            weightKg.coerceIn(30, 200).toByte(),
            sex.coerceIn(0, 1).toByte(),
            age.coerceIn(10, 110).toByte()
        )
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_SETTING, YcbtConstants.CMD_SET_USER_INFO, payload)
    }

    /**
     * Configura unidades métricas en el firmware del anillo (0=Métrico/°C/24h, 1=Imperial/°F/12h).
     * YCBTClient.settingUnit -> Group 0x01, Cmd 0x04.
     */
    fun settingUnit(
        distanceKm: Boolean = true,
        weightKg: Boolean = true,
        tempCelsius: Boolean = true,
        is24Hour: Boolean = true
    ): ByteArray {
        val payload = byteArrayOf(
            if (distanceKm) 0x00 else 0x01,
            if (weightKg) 0x00 else 0x01,
            if (tempCelsius) 0x00 else 0x01,
            if (is24Hour) 0x00 else 0x01,
            0x00,
            0x00
        )
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_SETTING, YcbtConstants.CMD_SET_UNIT, payload)
    }

    /**
     * Configura alerta de frecuencia cardíaca (alta/baja). YCBTClient.settingHeartAlarm -> 0x01 0x0B.
     */
    fun setHeartAlarm(enable: Boolean, highBpm: Int = 120, lowBpm: Int = 45): ByteArray {
        val payload = byteArrayOf(
            if (enable) 0x01 else 0x00,
            highBpm.coerceIn(60, 220).toByte(),
            lowBpm.coerceIn(35, 100).toByte()
        )
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_SETTING, YcbtConstants.CMD_SET_HEART_ALARM, payload)
    }

    /**
     * Configura alerta de saturación baja de oxígeno (SpO2). YCBTClient.settingBloodOxygenAlarm -> 0x01 0x39.
     */
    fun setSpo2Alarm(enable: Boolean, lowSpo2: Int = 90): ByteArray {
        val payload = byteArrayOf(
            if (enable) 0x01 else 0x00,
            lowSpo2.coerceIn(75, 96).toByte()
        )
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_SETTING, YcbtConstants.CMD_SET_SPO2_ALARM, payload)
    }

    /**
     * Configura alerta de fiebre / temperatura alta. YCBTClient.newSettingTemperatureAlarm -> 0x01 0x1F.
     */
    fun setTemperatureAlarm(enable: Boolean, highCelsiusInt: Int = 38, highCelsiusDec: Int = 0): ByteArray {
        val payload = byteArrayOf(
            if (enable) 0x01 else 0x00,
            highCelsiusInt.coerceIn(36, 42).toByte(),
            highCelsiusDec.coerceIn(0, 9).toByte(),
            35,
            0
        )
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_SETTING, YcbtConstants.CMD_SET_TEMP_ALARM, payload)
    }

    /**
     * Configura intensidad de vibración (0=Bajo, 1=Medio, 2=Alto). YCBTClient.settingVibrationIntensity -> 0x01 0x3C.
     */
    fun setVibrationIntensity(level: Int = 1): ByteArray {
        val payload = byteArrayOf(level.coerceIn(0, 3).toByte())
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_SETTING, YcbtConstants.CMD_SET_VIBRATION_INTENSITY, payload)
    }

    /**
     * Restaura valores de fábrica en el anillo. YCBTClient.settingRestoreFactory -> 0x01 0x0E ("RSYS").
     */
    fun restoreFactorySettings(): ByteArray {
        val payload = byteArrayOf(82, 83, 89, 83)
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_SETTING, YcbtConstants.CMD_SET_RESTORE_FACTORY, payload)
    }

    /**
     * Activa el flujo continuo en tiempo real de pasos y estado de actividad (06 00).
     */
    fun enableLiveStatusPush(): ByteArray {
        val payload = byteArrayOf(0x01, 0x00, 0x02)
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_APP_CONTROL, YcbtConstants.CMD_APP_REAL_SPORT, payload)
    }

    /**
     * Consulta información de batería y versión de firmware del anillo (02 00 "GC").
     */
    fun getDeviceInfo(): ByteArray {
        val payload = byteArrayOf(0x47, 0x43)
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_GET, YcbtConstants.CMD_GET_DEVICE_INFO, payload)
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
     * Borra un bloque de historial ya respaldado (05 0x40..0x44, payload [0x02]).
     * YCBTClient.deleteHealthHistoryData.
     */
    fun deleteHistory(deleteKey: Byte): ByteArray {
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_HEALTH, deleteKey, byteArrayOf(0x02))
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
     * Consulta el valor actual de oxígeno SpO2 en tiempo real
     * (YCBTClient.getRealBloodOxygen: Group 0x02, Cmd 0x11 / dataType 529, payload ASCII "IS").
     */
    fun queryRealBloodOxygen(): ByteArray {
        val payload = byteArrayOf(0x49, 0x53)
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_GET, YcbtConstants.CMD_GET_REAL_BLOOD_OXYGEN, payload)
    }

    /**
     * Consulta instantánea completa de todas las métricas activas del anillo
     * (YCBTClient.getAllRealDataFromDevice: Group 0x02, Cmd 0x20 / dataType 544).
     */
    fun queryAllRealDataFromDevice(): ByteArray {
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_GET, YcbtConstants.CMD_GET_ALL_REAL_DATA, ByteArray(0))
    }

    /**
     * Localizador físico del anillo (Find My Ring). YCBTClient.appFindDevice -> 0x03 0x00.
     */
    fun findRing(enable: Boolean = true, remindCount: Int = 5, remindInterval: Int = 1): ByteArray {
        val payload = byteArrayOf(
            if (enable) 0x01 else 0x00,
            remindCount.coerceIn(1, 20).toByte(),
            remindInterval.coerceIn(1, 10).toByte()
        )
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_APP_CONTROL, YcbtConstants.CMD_APP_FIND_DEVICE, payload)
    }

    /**
     * Calibración óptica de presión arterial con valores de baumanómetro de referencia.
     * YCBTClient.appBloodCalibration -> 0x03 0x03.
     */
    fun calibrateBloodPressure(systolic: Int, diastolic: Int): ByteArray {
        val payload = byteArrayOf(
            systolic.coerceIn(70, 220).toByte(),
            diastolic.coerceIn(40, 140).toByte()
        )
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_APP_CONTROL, YcbtConstants.CMD_APP_BLOOD_CALIBRATION, payload)
    }

    /**
     * Activa o desactiva el modo obturador de cámara remoto. YCBTClient.appControlTakePhoto -> 0x03 0x0E.
     */
    fun controlCameraShutter(enable: Boolean): ByteArray {
        val payload = byteArrayOf(if (enable) 0x01 else 0x00)
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_APP_CONTROL, YcbtConstants.CMD_APP_CONTROL_TAKE_PHOTO, payload)
    }

    /**
     * Configura gestos táctiles inteligentes del anillo (Wisdom / LifeFragment).
     * YCBTClient.setWitOnOff(onOff, protocolIndex) -> Group 0x0D, Cmd 0x02, [1, onOff, protocolIndex].
     * protocolIndex: 1=Video corto, 2=Música, 3=Lectura, 4=Cámara, 5=SOS, 6=Presentación.
     */
    fun setSmartRingTouchGesture(enable: Boolean, protocolIndex: Int): ByteArray {
        val payload = byteArrayOf(
            0x01,
            if (enable) 0x01 else 0x00,
            protocolIndex.coerceIn(1, 6).toByte()
        )
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_CUSTOMIZE, YcbtConstants.CMD_CUSTOMIZE_WIT, payload)
    }

    /**
     * Consulta estado actual de gestos táctiles del anillo. YCBTClient.getWitState -> 0x0D 0x02 [0x02].
     */
    fun getSmartRingTouchGestureState(): ByteArray {
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_CUSTOMIZE, YcbtConstants.CMD_CUSTOMIZE_WIT, byteArrayOf(0x02))
    }

    /**
     * Inicia o detiene una medición puntual bajo demanda.
     * mode: 0x00=HeartRate, 0x01=BP, 0x02=SpO2, 0x04=Temp, 0x0A=HRV
     */
    fun triggerMeasurement(enable: Boolean, mode: Byte = 0x00): ByteArray {
        val payload = byteArrayOf(if (enable) 0x01 else 0x00, mode)
        return YcbtFrameBuilder.build(YcbtConstants.GROUP_APP_CONTROL, YcbtConstants.CMD_APP_START_MEASUREMENT, payload)
    }
}
