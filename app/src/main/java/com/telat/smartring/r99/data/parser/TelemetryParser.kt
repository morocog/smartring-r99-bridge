package com.telat.smartring.r99.data.parser

import com.telat.smartring.r99.data.model.ActivityState
import com.telat.smartring.r99.data.model.SleepState
import com.telat.smartring.r99.data.model.StepsSample

class TelemetryParser(
    private val debounceMillis: Long = 200L
) {
    private val consolidatedRegex = Regex("""STEPS:\s*(\d+)\s+CALO:\s*(\d+),\s*DIST:\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val actStateRegex = Regex("""MEAS_EVT_ACT_STATE\s*=\s*(\d+),(\d+)""", RegexOption.IGNORE_CASE)
    private val sleepWearRegex = Regex("""sleep_wear_state\s+OK""", RegexOption.IGNORE_CASE)
    private val sleepEvtRegex = Regex("""sleep\s+evt\s+id\s+(\d+)""", RegexOption.IGNORE_CASE)
    private val instantStepRegex = Regex("""STEPS:\s*1\b""", RegexOption.IGNORE_CASE)

    private var lastInstantStepTime = 0L

    sealed class ParsedTelemetryEvent {
        data class ConsolidatedSteps(val sample: StepsSample) : ParsedTelemetryEvent()
        object InstantStepDetected : ParsedTelemetryEvent()
        data class Activity(val state: ActivityState) : ParsedTelemetryEvent()
        data class Sleep(val state: SleepState) : ParsedTelemetryEvent()
        data class RawLog(val text: String) : ParsedTelemetryEvent()
    }

    /**
     * Parsea una línea o bloque de texto recibido desde el canal NUS TX.
     */
    fun parseLine(line: String): ParsedTelemetryEvent {
        val trimmed = line.trim()

        // 1. Verificar bloque consolidado (totales diarios)
        consolidatedRegex.find(trimmed)?.let { match ->
            val steps = match.groupValues[1].toIntOrNull() ?: 0
            val calo = match.groupValues[2].toIntOrNull() ?: 0
            val dist = match.groupValues[3].toIntOrNull() ?: 0
            return ParsedTelemetryEvent.ConsolidatedSteps(
                StepsSample(steps = steps, calories = calo, distanceMeters = dist)
            )
        }

        // 2. Verificar ticks de pasos instantáneos con debounce de 200 ms
        if (instantStepRegex.containsMatchIn(trimmed)) {
            val now = System.currentTimeMillis()
            if (now - lastInstantStepTime >= debounceMillis) {
                lastInstantStepTime = now
                return ParsedTelemetryEvent.InstantStepDetected
            }
            return ParsedTelemetryEvent.RawLog("Debounced instant step echo: $trimmed")
        }

        // 3. Verificar estado de actividad
        actStateRegex.find(trimmed)?.let { match ->
            val isMoving = match.groupValues[1] == "1"
            return ParsedTelemetryEvent.Activity(
                ActivityState(isMoving = isMoving, rawState = trimmed)
            )
        }

        // 4. Verificar estado de uso o sueño
        if (sleepWearRegex.containsMatchIn(trimmed)) {
            return ParsedTelemetryEvent.Sleep(SleepState(wearOk = true, eventId = null))
        }

        sleepEvtRegex.find(trimmed)?.let { match ->
            val evtId = match.groupValues[1].toIntOrNull()
            return ParsedTelemetryEvent.Sleep(SleepState(wearOk = true, eventId = evtId))
        }

        return ParsedTelemetryEvent.RawLog(trimmed)
    }

    /**
     * Desglosa un chunk de bytes entrante en líneas de texto ASCII delimitadas por \n o \r.
     */
    fun parseChunk(data: ByteArray): List<ParsedTelemetryEvent> {
        val text = String(data, Charsets.US_ASCII)
        val lines = text.split(Regex("[\r\n]+")).filter { it.isNotBlank() }
        return lines.map { parseLine(it) }
    }
}
