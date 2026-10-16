package com.telat.smartring.r99.data.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CsvLogEntry(
    val timestamp: Long,
    val metricType: String,
    val value1: String,
    val value2: String = "",
    val value3: String = "",
    val notes: String = ""
)

class CsvExporter(private val context: Context) {
    companion object {
        private const val TAG = "CsvExporter"
    }

    /**
     * Exporta la lista de registros a un archivo CSV accesible en la carpeta Downloads del dispositivo.
     * Retorna la ruta o URI del archivo generado, o null en caso de error.
     */
    fun exportSessionToDownloads(entries: List<CsvLogEntry>): String? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "smartring_r99_telemetry_$timeStamp.csv"

        val header = "timestamp,iso_time,metric_type,value_1,value_2,value_3,notes\n"
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault())

        val sb = StringBuilder(header)
        for (e in entries) {
            val isoTime = isoFormat.format(Date(e.timestamp))
            sb.append("${e.timestamp},\"$isoTime\",\"${e.metricType}\",\"${e.value1}\",\"${e.value2}\",\"${e.value3}\",\"${e.notes}\"\n")
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return null

                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
                }
                Log.i(TAG, "CSV exportado exitosamente a Downloads vía MediaStore: $fileName")
                "Downloads/$fileName"
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use { outputStream ->
                    outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
                }
                Log.i(TAG, "CSV exportado a archivo directo: ${file.absolutePath}")
                file.absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al exportar CSV: ${e.message}", e)
            null
        }
    }
}
