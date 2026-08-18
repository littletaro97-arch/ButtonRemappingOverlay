package com.example.buttonremapping

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager

/**
 * 前台应用检测（用于“指定应用启动”）。
 *
 * 基于 UsageStatsManager 的 UsageEvents 事件流判断当前前台应用，
 * 需要用户授予「使用情况访问」权限（PACKAGE_USAGE_STATS）。
 */
object AppTrigger {

    fun hasUsageAccess(context: Context): Boolean = try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }

    fun openUsageAccessSettings(context: Context) {
        try {
            context.startActivity(
                android.content.Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            )
        } catch (_: Exception) {
            RuntimeProtection.recordEvent(context, "打开使用情况访问设置失败")
        }
    }

    /**
     * 返回最近一次进入前台的包名；没有可用事件时返回 null。
     * 自身应用包名会被过滤（打开本应用设置时不应触发屏蔽方案）。
     *
     * 事件窗口由短到长重试：即使用户停留在目标应用超过数分钟，
     * 长窗口仍能捕获该应用上次进入前台的事件，避免检测不到。
     */
    fun currentForegroundPackage(context: Context): String? {
        val appContext = context.applicationContext
        val usageStatsManager = appContext
            .getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        return try {
            val now = System.currentTimeMillis()
            // 依次尝试 5s / 30s / 2min / 10min 窗口。
            val windows = longArrayOf(5_000L, 30_000L, 120_000L, 600_000L)
            for (window in windows) {
                val events = usageStatsManager.queryEvents(now - window, now)
                val event = UsageEvents.Event()
                var lastPackage: String? = null
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                        event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                    ) {
                        lastPackage = event.packageName
                    }
                }
                val result = lastPackage
                    ?.takeIf { it != appContext.packageName }
                    ?.takeIf { it.isNotBlank() }
                if (result != null) return result
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /** 已安装且可启动的应用包名列表（用于添加触发应用）。 */
    fun installedLaunchablePackages(context: Context): List<String> {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        return try {
            context.packageManager
                .queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.applicationInfo?.packageName }
                .distinct()
                .sorted()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun applicationLabel(context: Context, packageName: String): String = try {
        val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(appInfo).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }
}
