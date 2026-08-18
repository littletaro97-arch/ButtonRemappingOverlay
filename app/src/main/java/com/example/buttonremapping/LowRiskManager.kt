package com.example.buttonremapping

import android.content.Context
import android.content.Intent

object LowRiskManager {
    fun start(context: Context) {
        RuntimeProtection.recordEvent(context, "低风险管理器请求启动服务")
        context.startForegroundService(Intent(context, OverlayService::class.java))
    }

    fun stop(context: Context) {
        OverlayService.markUserStop()
        // stopService 是异步的：先同步翻转运行标志，让页面 UI 立即读到已停止状态。
        OverlayService.isRunning = false
        RuntimeProtection.recordEvent(context, "Overlay service stop requested")
        context.stopService(Intent(context, OverlayService::class.java))
    }

    fun openOverlaySettings(context: Context) {
        RuntimeProtection.openOverlaySettings(context)
    }
}

object HighRiskManager {
    const val isImplemented: Boolean = false
}
