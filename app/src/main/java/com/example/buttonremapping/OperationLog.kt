package com.example.buttonremapping

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small, app-private operation log intended for remote troubleshooting.
 *
 * The log is deliberately kept out of external storage: no storage permission
 * is needed and no screenshot URI or raw touch coordinates are written here.
 */
object OperationLog {
    private const val TAG = "OperationLog"
    private const val DIRECTORY_NAME = "runtime-diagnostics"
    private const val FILE_NAME = "operations.log"
    private const val MAX_BYTES = 2L * 1024L * 1024L
    private const val RETAIN_BYTES = 1L * 1024L * 1024L
    private const val MAX_DETAIL_LENGTH = 2048

    private val lock = Any()
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)

    fun file(context: Context): File = context.applicationContext
        .filesDir
        .resolve(DIRECTORY_NAME)
        .resolve(FILE_NAME)

    fun filePath(context: Context): String = file(context).absolutePath

    fun sizeBytes(context: Context): Long = synchronized(lock) {
        file(context).length()
    }

    fun append(context: Context, event: String, detail: String? = null) {
        synchronized(lock) {
            try {
                // Build the timestamp under the same lock because SimpleDateFormat
                // is not thread-safe and service callbacks can arrive concurrently.
                val line = buildLine(event, detail)
                val logFile = file(context)
                logFile.parentFile?.mkdirs()
                rotateIfNeeded(logFile, line.toByteArray(Charsets.UTF_8).size.toLong())
                FileOutputStream(logFile, true).use { output ->
                    OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                        writer.write(line)
                        writer.flush()
                    }
                }
            } catch (error: Exception) {
                // Do not recurse through RuntimeProtection.recordFailure here.
                Log.e(TAG, "写入操作日志失败", error)
            }
        }
    }

    fun readTail(context: Context, maxChars: Int = 24_000): String = synchronized(lock) {
        val logFile = file(context)
        if (!logFile.exists()) return@synchronized "暂无操作日志"
        try {
            val text = logFile.readText(Charsets.UTF_8)
            if (text.length <= maxChars) {
                text.ifBlank { "暂无操作日志" }
            } else {
                "（仅显示最近 ${maxChars} 个字符）\n" + text.takeLast(maxChars)
            }
        } catch (error: Exception) {
            Log.e(TAG, "读取操作日志失败", error)
            "操作日志读取失败：${error.javaClass.simpleName}"
        }
    }

    fun clear(context: Context): Boolean = synchronized(lock) {
        try {
            val logFile = file(context)
            if (!logFile.exists()) {
                logFile.parentFile?.mkdirs()
                return@synchronized true
            }
            logFile.delete()
        } catch (error: Exception) {
            Log.e(TAG, "清空操作日志失败", error)
            false
        }
    }

    private fun buildLine(event: String, detail: String?): String {
        val safeEvent = sanitize(event, MAX_DETAIL_LENGTH)
        val safeDetail = detail?.let { sanitize(it, MAX_DETAIL_LENGTH) }
        return buildString {
            append(timestampFormat.format(Date()))
            append(" | ")
            append(safeEvent)
            if (!safeDetail.isNullOrBlank()) {
                append(" | ")
                append(safeDetail)
            }
            append('\n')
        }
    }

    private fun sanitize(value: String, maxLength: Int): String = value
        .replace('\r', ' ')
        .replace('\n', ' ')
        .trim()
        .take(maxLength)

    private fun rotateIfNeeded(logFile: File, incomingBytes: Long) {
        if (!logFile.exists() || logFile.length() + incomingBytes <= MAX_BYTES) return
        val retained = try {
            logFile.readBytes().takeLast(RETAIN_BYTES.toInt()).toByteArray()
        } catch (error: Exception) {
            Log.e(TAG, "轮转操作日志失败，重新建立日志文件", error)
            byteArrayOf()
        }
        FileOutputStream(logFile, false).use { output ->
            output.write("--- 操作日志已轮转，仅保留最近记录 ---\n".toByteArray(Charsets.UTF_8))
            output.write(retained)
        }
    }
}
