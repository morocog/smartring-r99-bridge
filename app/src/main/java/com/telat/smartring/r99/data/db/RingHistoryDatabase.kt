package com.telat.smartring.r99.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.telat.smartring.r99.data.model.DailyActivitySummary
import com.telat.smartring.r99.data.model.MetricHistoryItem
import com.telat.smartring.r99.data.model.SleepSessionSummaryRecord
import com.telat.smartring.r99.data.model.SleepStageUiItem
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class RingHistoryDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DB_NAME,
    null,
    DB_VERSION
) {
    companion object {
        private const val DB_NAME = "smartring_r99_history.db"
        private const val DB_VERSION = 1

        const val METRIC_HEART_RATE = "HEART_RATE"
        const val METRIC_SPO2 = "SPO2"
        const val METRIC_TEMPERATURE = "TEMPERATURE"
        const val METRIC_BLOOD_PRESSURE = "BLOOD_PRESSURE"
        const val METRIC_HRV = "HRV"
        const val METRIC_RESPIRATORY_RATE = "RESPIRATORY_RATE"

        private val DATE_KEY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
        private val SHORT_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM", Locale("es", "MX"))

        fun toDateKey(timestampMs: Long): String {
            return Instant.ofEpochMilli(timestampMs)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(DATE_KEY_FMT)
        }

        fun toShortDate(dateKey: String): String {
            return try {
                LocalDate.parse(dateKey, DATE_KEY_FMT).format(SHORT_DATE_FMT)
            } catch (_: Exception) {
                dateKey
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS vital_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                metric_type TEXT NOT NULL,
                timestamp_ms INTEGER NOT NULL,
                date_key TEXT NOT NULL,
                primary_value REAL NOT NULL,
                secondary_value REAL,
                display_value TEXT NOT NULL,
                unit TEXT NOT NULL,
                status_label TEXT NOT NULL,
                source TEXT NOT NULL DEFAULT 'RING',
                UNIQUE(metric_type, timestamp_ms) ON CONFLICT REPLACE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_vital_metric_ts ON vital_records(metric_type, timestamp_ms DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_vital_date ON vital_records(date_key)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sleep_sessions (
                session_end_ms INTEGER PRIMARY KEY,
                session_start_ms INTEGER NOT NULL,
                date_key TEXT NOT NULL,
                total_min INTEGER NOT NULL,
                deep_min INTEGER NOT NULL,
                light_min INTEGER NOT NULL,
                rem_min INTEGER NOT NULL,
                awake_min INTEGER NOT NULL,
                date_range_str TEXT NOT NULL,
                stages_json TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sleep_date ON sleep_sessions(date_key DESC)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_activity (
                date_key TEXT PRIMARY KEY,
                steps INTEGER NOT NULL,
                calories INTEGER NOT NULL,
                distance_meters INTEGER NOT NULL,
                updated_at_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun insertVitalRecord(metricType: String, item: MetricHistoryItem, source: String = "RING") {
        insertVitalBatch(metricType, listOf(item), source)
    }

    @Synchronized
    fun insertVitalBatch(metricType: String, items: List<MetricHistoryItem>, source: String = "RING") {
        if (items.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (item in items) {
                val dk = item.dateKey.ifBlank { toDateKey(item.timestampMillis) }
                val cv = ContentValues().apply {
                    put("metric_type", metricType)
                    put("timestamp_ms", item.timestampMillis)
                    put("date_key", dk)
                    put("primary_value", item.primaryValue)
                    if (item.secondaryValue != null) {
                        put("secondary_value", item.secondaryValue)
                    } else {
                        putNull("secondary_value")
                    }
                    put("display_value", item.displayValue)
                    put("unit", item.unit)
                    put("status_label", item.statusLabel)
                    put("source", source)
                }
                db.insertWithOnConflict("vital_records", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun upsertDailyActivity(
        dateKey: String,
        steps: Int,
        calories: Int,
        distanceMeters: Int,
        timestampMs: Long
    ) {
        if (steps <= 0 && calories <= 0 && distanceMeters <= 0) return
        val db = writableDatabase
        var finalSteps = steps
        var finalCal = calories
        var finalDist = distanceMeters

        db.rawQuery(
            "SELECT steps, calories, distance_meters FROM daily_activity WHERE date_key = ?",
            arrayOf(dateKey)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                finalSteps = maxOf(cursor.getInt(0), steps)
                finalCal = maxOf(cursor.getInt(1), calories)
                finalDist = maxOf(cursor.getInt(2), distanceMeters)
            }
        }

        val cv = ContentValues().apply {
            put("date_key", dateKey)
            put("steps", finalSteps)
            put("calories", finalCal)
            put("distance_meters", finalDist)
            put("updated_at_ms", timestampMs)
        }
        db.insertWithOnConflict("daily_activity", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun upsertSleepSession(
        startMs: Long,
        endMs: Long,
        dateKey: String,
        totalMin: Int,
        deepMin: Int,
        lightMin: Int,
        remMin: Int,
        awakeMin: Int,
        dateRangeStr: String,
        stages: List<SleepStageUiItem>
    ) {
        if (totalMin <= 0 || endMs <= startMs) return
        val stagesArray = JSONArray()
        for (st in stages) {
            stagesArray.put(
                JSONObject().apply {
                    put("name", st.stageName)
                    put("start", st.startTimeFormatted)
                    put("dur", st.durationMinutes)
                }
            )
        }
        val cv = ContentValues().apply {
            put("session_end_ms", endMs)
            put("session_start_ms", startMs)
            put("date_key", dateKey)
            put("total_min", totalMin)
            put("deep_min", deepMin)
            put("light_min", lightMin)
            put("rem_min", remMin)
            put("awake_min", awakeMin)
            put("date_range_str", dateRangeStr)
            put("stages_json", stagesArray.toString())
        }
        writableDatabase.insertWithOnConflict("sleep_sessions", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun getVitalRecordsSince(
        metricType: String,
        sinceMs: Long,
        limit: Int = 2000
    ): List<MetricHistoryItem> {
        val list = mutableListOf<MetricHistoryItem>()
        readableDatabase.rawQuery(
            """
            SELECT timestamp_ms, date_key, primary_value, secondary_value, display_value, unit, status_label
            FROM vital_records
            WHERE metric_type = ? AND timestamp_ms >= ?
            ORDER BY timestamp_ms DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(metricType, sinceMs.toString(), limit.toString())
        ).use { c ->
            val timeFmt = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.getDefault())
            while (c.moveToNext()) {
                val ts = c.getLong(0)
                val dk = c.getString(1)
                val v1 = c.getFloat(2)
                val v2 = if (c.isNull(3)) null else c.getFloat(3)
                val disp = c.getString(4)
                val unit = c.getString(5)
                val status = c.getString(6)
                val fmtTime = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(timeFmt)
                list.add(
                    MetricHistoryItem(
                        timestampMillis = ts,
                        formattedTime = fmtTime,
                        primaryValue = v1,
                        secondaryValue = v2,
                        displayValue = disp,
                        unit = unit,
                        statusLabel = status,
                        dateKey = dk
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun getDailyActivityLastDays(days: Int = 35): List<DailyActivitySummary> {
        val cutoffKey = LocalDate.now().minusDays(days.toLong()).format(DATE_KEY_FMT)
        val list = mutableListOf<DailyActivitySummary>()
        readableDatabase.rawQuery(
            """
            SELECT date_key, steps, calories, distance_meters
            FROM daily_activity
            WHERE date_key >= ?
            ORDER BY date_key DESC
            """.trimIndent(),
            arrayOf(cutoffKey)
        ).use { c ->
            while (c.moveToNext()) {
                val dk = c.getString(0)
                list.add(
                    DailyActivitySummary(
                        dateKey = dk,
                        shortDateLabel = toShortDate(dk),
                        steps = c.getInt(1),
                        calories = c.getInt(2),
                        distanceMeters = c.getInt(3)
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun getSleepSessionsLastDays(days: Int = 35): List<SleepSessionSummaryRecord> {
        val cutoffKey = LocalDate.now().minusDays(days.toLong()).format(DATE_KEY_FMT)
        val list = mutableListOf<SleepSessionSummaryRecord>()
        readableDatabase.rawQuery(
            """
            SELECT session_end_ms, session_start_ms, date_key, total_min, deep_min, light_min, rem_min, awake_min, date_range_str, stages_json
            FROM sleep_sessions
            WHERE date_key >= ?
            ORDER BY session_end_ms DESC
            """.trimIndent(),
            arrayOf(cutoffKey)
        ).use { c ->
            while (c.moveToNext()) {
                val endMs = c.getLong(0)
                val startMs = c.getLong(1)
                val dk = c.getString(2)
                val totalMin = c.getInt(3)
                val deepMin = c.getInt(4)
                val lightMin = c.getInt(5)
                val remMin = c.getInt(6)
                val awakeMin = c.getInt(7)
                val rangeStr = c.getString(8)
                val stagesJson = c.getString(9)
                val stages = parseStagesJson(stagesJson)

                list.add(
                    SleepSessionSummaryRecord(
                        dateKey = dk,
                        shortDateLabel = toShortDate(dk),
                        startTimeMs = startMs,
                        endTimeMs = endMs,
                        totalMinutes = totalMin,
                        deepMinutes = deepMin,
                        lightMinutes = lightMin,
                        remMinutes = remMin,
                        awakeMinutes = awakeMin,
                        dateRangeStr = rangeStr,
                        stages = stages
                    )
                )
            }
        }
        return list
    }

    @Synchronized
    fun getTotalRecordsCount(): Int {
        var total = 0
        readableDatabase.rawQuery("SELECT COUNT(*) FROM vital_records", null).use { c ->
            if (c.moveToFirst()) total += c.getInt(0)
        }
        readableDatabase.rawQuery("SELECT COUNT(*) FROM sleep_sessions", null).use { c ->
            if (c.moveToFirst()) total += c.getInt(0)
        }
        readableDatabase.rawQuery("SELECT COUNT(*) FROM daily_activity", null).use { c ->
            if (c.moveToFirst()) total += c.getInt(0)
        }
        return total
    }

    private fun parseStagesJson(raw: String): List<SleepStageUiItem> {
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<SleepStageUiItem>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(
                    SleepStageUiItem(
                        stageName = o.optString("name", ""),
                        startTimeFormatted = o.optString("start", ""),
                        durationMinutes = o.optInt("dur", 0)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
