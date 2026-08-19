package com.example.buttonremapping

import android.content.Context
import android.content.Intent
import com.example.buttonremapping.highrisk.HighRiskManager
import com.example.buttonremapping.highrisk.HighRiskOverlayService

/**
 * 各方案互斥：同时只能运行一个。启动任一方案前，先停止其它服务。
 */
object ModeExclusive {

    fun startLowRisk(context: Context) {
        stopOthers(context, keep = Mode.LOW)
        LowRiskManager.start(context)
    }

    fun startLongPress(context: Context) {
        stopOthers(context, keep = Mode.LONG)
        context.startForegroundService(Intent(context, LongPressOverlayService::class.java))
    }

    fun startDoubleTap(context: Context) {
        stopOthers(context, keep = Mode.DOUBLE)
        context.startForegroundService(Intent(context, DoubleTapOverlayService::class.java))
    }

    fun startHighRisk(context: Context) {
        stopOthers(context, keep = Mode.HIGH)
        HighRiskManager.start(context)
    }

    private fun stopOthers(context: Context, keep: Mode) {
        if (keep != Mode.LOW) {
            LowRiskManager.stop(context)
        }
        if (keep != Mode.LONG) {
            LongPressOverlayService.markUserStop()
            LongPressOverlayService.isRunning = false
            context.stopService(Intent(context, LongPressOverlayService::class.java))
        }
        if (keep != Mode.DOUBLE) {
            DoubleTapOverlayService.markUserStop()
            DoubleTapOverlayService.isRunning = false
            context.stopService(Intent(context, DoubleTapOverlayService::class.java))
        }
        if (keep != Mode.HIGH) {
            HighRiskManager.stop(context)
        }
    }

    private enum class Mode { LOW, LONG, DOUBLE, HIGH }
}
