package com.example.buttonremapping.highrisk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.example.buttonremapping.RuntimeProtection
import rikka.shizuku.Shizuku

enum class ShizukuState {
    NOT_INSTALLED,
    NOT_RUNNING,
    NOT_AUTHORIZED,
    READY,
    UNSUPPORTED,
}

data class ShizukuStatus(
    val state: ShizukuState,
    val detail: String,
    val uid: Int? = null,
    val version: Int? = null,
)

object HighRiskManager {
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    const val REQUEST_PERMISSION_CODE = 4001

    /**
     * 无障碍服务是否可用（已在系统设置开启）。
     */
    fun isAccessibilityEnabled(context: Context): Boolean =
        InputAccessibilityService.isServiceEnabled(context)

    /**
     * 无障碍服务是否已连接（本进程内服务实例在线）。
     */
    fun isAccessibilityConnected(): Boolean = InputAccessibilityService.isConnected

    /**
     * 注入后端状态描述，用于界面展示。
     * 优先级：Shizuku READY > 无障碍可用。
     */
    fun inputBackendStatus(context: Context): String {
        val shizuku = getShizukuStatus(context)
        if (shizuku.state == ShizukuState.READY) return "Shizuku 已就绪"
        val enabled = isAccessibilityEnabled(context)
        val connected = isAccessibilityConnected()
        return when {
            enabled && connected -> "无障碍已连接"
            enabled -> "无障碍已开启（等待服务连接）"
            else -> "无障碍未开启"
        }
    }

    fun getShizukuStatus(context: Context): ShizukuStatus {
        if (!isPackageInstalled(context, SHIZUKU_PACKAGE)) {
            return ShizukuStatus(ShizukuState.NOT_INSTALLED, "未安装")
        }
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            return ShizukuStatus(ShizukuState.NOT_RUNNING, "未运行")
        }
        return try {
            val version = Shizuku.getVersion()
            if (Shizuku.isPreV11() || version < 11) {
                ShizukuStatus(ShizukuState.UNSUPPORTED, "Shizuku API 版本过低", version = version)
            } else {
                val uid = Shizuku.getUid()
                val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                ShizukuStatus(
                    state = if (granted) ShizukuState.READY else ShizukuState.NOT_AUTHORIZED,
                    detail = if (granted) "已授权" else "未授权",
                    uid = uid,
                    version = version,
                )
            }
        } catch (error: Throwable) {
            ShizukuStatus(ShizukuState.NOT_RUNNING, "状态读取失败：${error.javaClass.simpleName}")
        }
    }

    fun requestPermission(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                true
            } else {
                Shizuku.requestPermission(REQUEST_PERMISSION_CODE)
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun start(context: Context) {
        RuntimeProtection.recordEvent(context, "高风险管理器请求启动服务")
        context.startForegroundService(Intent(context, HighRiskOverlayService::class.java))
    }

    fun stop(context: Context) {
        HighRiskOverlayService.markUserStop()
        // stopService 是异步的：先同步翻转运行标志，让页面 UI（refreshState 等）
        // 立即读到已停止状态，避免“点击停止后按钮仍显示正在映射”的残留。
        HighRiskOverlayService.isRunning = false
        com.example.buttonremapping.RuntimeProtection.recordEvent(context, "High-risk overlay service stop requested")
        context.stopService(Intent(context, HighRiskOverlayService::class.java))
    }

    fun openShizuku(context: Context) {
        RuntimeProtection.recordEvent(context, "打开 Shizuku")
        val launchIntent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
            return
        }
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$SHIZUKU_PACKAGE"),
            ),
        )
    }

    fun requestPermission(activity: Activity, onResult: (Boolean) -> Unit) {
        RuntimeProtection.recordEvent(activity, "请求 Shizuku 权限")
        lateinit var listener: Shizuku.OnRequestPermissionResultListener
        listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_PERMISSION_CODE) {
                Shizuku.removeRequestPermissionResultListener(listener)
                RuntimeProtection.recordEvent(
                    activity,
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        "Shizuku 权限结果：已授权"
                    } else {
                        "Shizuku 权限结果：未授权"
                    },
                )
                onResult(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        if (!requestPermission()) {
            // The callback remains registered for the asynchronous Shizuku dialog.
            if (getShizukuStatus(activity).state != ShizukuState.NOT_AUTHORIZED) {
                Shizuku.removeRequestPermissionResultListener(listener)
                onResult(false)
            }
        } else {
            Shizuku.removeRequestPermissionResultListener(listener)
            onResult(true)
        }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
