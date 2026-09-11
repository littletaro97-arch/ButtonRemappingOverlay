package com.example.buttonremapping

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.example.buttonremapping.highrisk.HighRiskOverlayService
import java.util.Locale

data class RuntimeProtectionStatus(
    val overlayGranted: Boolean,
    val notificationRequired: Boolean,
    val notificationGranted: Boolean,
    val batteryOptimizationSupported: Boolean,
    val batteryUnrestricted: Boolean,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val androidRelease: String,
    val apiLevel: Int,
    val systemDisplay: String,
) {
    val notificationReady: Boolean
        get() = !notificationRequired || notificationGranted

    val batteryReady: Boolean
        get() = !batteryOptimizationSupported || batteryUnrestricted

    val hasOptionalWarnings: Boolean
        get() = !notificationReady || !batteryReady

    val isVivoIqoo: Boolean
        get() {
            val values = listOf(manufacturer, brand, model).map { it.lowercase(Locale.ROOT) }
            return values.any { it.contains("vivo") || it.contains("iqoo") }
        }
}

object RuntimeProtection {
    private const val TAG = "RuntimeProtection"
    private const val PREFS_NAME = "runtime_diagnostics"
    private const val LAST_EVENT = "last_event"

    fun isHuaweiDevice(): Boolean = VendorGuidancePolicy.shouldShowHuaweiGuide(
        Build.MANUFACTURER,
        Build.BRAND,
    )

    fun inspect(context: Context): RuntimeProtectionStatus {
        val appContext = context.applicationContext
        val overlayGranted = runCatching { Settings.canDrawOverlays(appContext) }
            .onFailure { Log.w(TAG, "悬浮窗权限检查异常", it) }
            .getOrDefault(false)
        val notificationRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val notificationGranted = !notificationRequired ||
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val batterySupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        val batteryUnrestricted = if (!batterySupported) {
            true
        } else {
            runCatching {
                appContext.getSystemService(PowerManager::class.java)
                    ?.isIgnoringBatteryOptimizations(appContext.packageName) == true
            }.getOrDefault(false)
        }
        val status = RuntimeProtectionStatus(
            overlayGranted = overlayGranted,
            notificationRequired = notificationRequired,
            notificationGranted = notificationGranted,
            batteryOptimizationSupported = batterySupported,
            batteryUnrestricted = batteryUnrestricted,
            manufacturer = Build.MANUFACTURER.ifBlank { "未知" },
            brand = Build.BRAND.ifBlank { "未知" },
            model = Build.MODEL.ifBlank { "未知" },
            androidRelease = Build.VERSION.RELEASE.ifBlank { "未知" },
            apiLevel = Build.VERSION.SDK_INT,
            systemDisplay = readSystemDisplay(),
        )
        Log.i(TAG, "Overlay permission check: ${status.overlayGranted}")
        Log.i(TAG, "Notification permission state: ${notificationLabel(status)}")
        Log.i(TAG, "Battery optimization state: ${batteryLabel(status)}")
        OperationLog.append(
            appContext,
            "运行状态检查",
            "overlay=${status.overlayGranted}; notification=${notificationLabel(status)}; battery=${batteryLabel(status)}",
        )
        return status
    }

    fun homeSummary(context: Context): String {
        return homeSummary(inspect(context))
    }

    fun homeSummary(status: RuntimeProtectionStatus): String {
        return when {
            !status.overlayGranted -> "5 项需要检查 · 悬浮窗权限未开启"
            status.hasOptionalWarnings -> "5 项需要检查 · 还有建议设置未完成"
            else -> "5 项已检查 · 当前运行设置正常"
        }
    }

    fun notificationLabel(status: RuntimeProtectionStatus): String = when {
        !status.notificationRequired -> "系统无需单独授权"
        status.notificationGranted -> "已开启"
        else -> "未开启"
    }

    fun batteryLabel(status: RuntimeProtectionStatus): String = when {
        !status.batteryOptimizationSupported -> "系统无需单独设置"
        status.batteryUnrestricted -> "不受限制"
        else -> "系统可能限制后台运行"
    }

    fun serviceLabel(): String = when {
        OverlayService.isRunning -> "低风险运行中"
        HighRiskOverlayService.isRunning -> "高风险运行中"
        else -> "未运行"
    }

    fun foregroundLabel(): String = when {
        OverlayService.isForeground -> "低风险前台服务运行中"
        HighRiskOverlayService.isForeground -> "高风险前台服务运行中"
        else -> "未运行"
    }

    fun diagnosticText(context: Context, operationLogChars: Int = 24_000): String {
        val status = inspect(context)
        val lastEvent = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(LAST_EVENT, "无记录")
            ?: "无记录"
        return buildString {
            appendLine("设备：${status.manufacturer} ${status.brand} ${status.model}")
            appendLine("Android：${status.androidRelease}（API ${status.apiLevel}）")
            appendLine("系统：${status.systemDisplay}")
            appendLine("悬浮窗权限：${if (status.overlayGranted) "允许" else "未开启"}")
            appendLine("通知权限：${notificationLabel(status)}")
            appendLine("忽略电池优化：${batteryLabel(status)}")
            appendLine("Overlay Service：${serviceLabel()}")
            appendLine("Foreground Service：${foregroundLabel()}")
            appendLine("最近事件：$lastEvent")
            appendLine("日志文件：${OperationLog.filePath(context)}")
            appendLine("日志大小：${OperationLog.sizeBytes(context)} bytes")
            appendLine()
            appendLine("最近操作日志：")
            append(OperationLog.readTail(context, operationLogChars))
        }
    }

    fun recordEvent(context: Context, event: String) {
        recordEvent(context, event, null)
    }

    fun recordEvent(context: Context, event: String, detail: String?) {
        val suffix = detail?.takeIf { it.isNotBlank() }?.let { " | $it" }.orEmpty()
        val message = "${System.currentTimeMillis()} $event$suffix"
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LAST_EVENT, message)
            .apply()
        OperationLog.append(context, event, detail)
        Log.i(TAG, "$event$suffix")
    }

    fun recordFailure(context: Context, event: String, error: Throwable) {
        val detail = "$event: ${error.javaClass.simpleName}: ${error.message.orEmpty()}"
        recordEvent(context, detail)
        Log.e(TAG, event, error)
    }

    fun openOverlaySettings(context: Context) {
        recordEvent(context, "打开悬浮窗系统设置")
        launchOrAppDetails(
            context,
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }

    fun openNotificationSettings(context: Context) {
        recordEvent(context, "打开通知系统设置")
        launchOrAppDetails(
            context,
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            },
        )
    }

    fun openBatterySettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            recordEvent(context, "打开应用详情设置（系统不支持电池优化设置）")
            openAppDetails(context)
            return
        }
        recordEvent(context, "打开电池后台运行设置")
        launchOrAppDetails(
            context,
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }

    fun openAppDetails(context: Context) {
        recordEvent(context, "打开应用详情设置")
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        } catch (error: Exception) {
            recordFailure(context, "打开应用详情失败", error)
        }
    }

    private fun launchOrAppDetails(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (error: Exception) {
            recordFailure(context, "打开系统设置失败", error)
            openAppDetails(context)
        }
    }

    private fun readSystemDisplay(): String {
        val propertyKeys = listOf(
            "ro.vivo.os.version",
            "ro.vivo.os.build.display.id",
            "ro.build.display.id",
        )
        propertyKeys.forEach { key ->
            val value = readSystemProperty(key)
            if (!value.isNullOrBlank()) return value
        }
        return Build.DISPLAY.ifBlank { "未知" }
    }

    private fun readSystemProperty(key: String): String? = runCatching {
        val properties = Class.forName("android.os.SystemProperties")
        val get = properties.getMethod("get", String::class.java, String::class.java)
        (get.invoke(null, key, "") as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
