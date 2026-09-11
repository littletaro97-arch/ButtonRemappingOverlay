package com.example.buttonremapping

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import kotlin.math.abs

enum class FullscreenState {
    READY,
    RESTRICTED,
    UNKNOWN,
}

data class FullscreenDisplayStatus(
    val state: FullscreenState,
    val windowWidth: Int,
    val windowHeight: Int,
    val displayWidth: Int,
    val displayHeight: Int,
) {
    val detail: String
        get() = "window=${windowWidth}x${windowHeight}; display=${displayWidth}x${displayHeight}"
}

object FullscreenDisplay {
    private const val SIZE_TOLERANCE_PX = 4

    fun inspect(activity: Activity): FullscreenDisplayStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInMultiWindowMode) {
            return FullscreenDisplayStatus(FullscreenState.RESTRICTED, 0, 0, 0, 0)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            inspectCurrentMetrics(activity)
        } else {
            inspectLegacyMetrics(activity)
        }
    }

    fun openSettings(activity: Activity) {
        RuntimeProtection.recordEvent(
            activity,
            "打开全屏显示设置",
            "action=${Settings.ACTION_DISPLAY_SETTINGS}",
        )
        runCatching {
            activity.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        }.onFailure {
            RuntimeProtection.recordFailure(activity, "打开显示设置失败", it)
            RuntimeProtection.openAppDetails(activity)
        }
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.R)
    private fun inspectCurrentMetrics(activity: Activity): FullscreenDisplayStatus {
        val manager = activity.windowManager
        val current = manager.currentWindowMetrics.bounds
        val maximum = manager.maximumWindowMetrics.bounds
        return status(current.width(), current.height(), maximum.width(), maximum.height())
    }

    @Suppress("DEPRECATION")
    private fun inspectLegacyMetrics(activity: Activity): FullscreenDisplayStatus {
        val decor = activity.window.decorView
        if (decor.width <= 0 || decor.height <= 0) {
            return FullscreenDisplayStatus(FullscreenState.UNKNOWN, 0, 0, 0, 0)
        }
        val real = DisplayMetrics()
        activity.windowManager.defaultDisplay.getRealMetrics(real)
        val visible = Rect()
        decor.getWindowVisibleDisplayFrame(visible)
        val insets = decor.rootWindowInsets
        val occupiedWidth = decor.width + (insets?.systemWindowInsetLeft ?: 0) +
            (insets?.systemWindowInsetRight ?: 0)
        val occupiedHeight = decor.height + (insets?.systemWindowInsetTop ?: 0) +
            (insets?.systemWindowInsetBottom ?: 0)
        val windowWidth = maxOf(occupiedWidth, visible.width())
        val windowHeight = maxOf(occupiedHeight, visible.height())
        return status(windowWidth, windowHeight, real.widthPixels, real.heightPixels)
    }

    private fun status(
        windowWidth: Int,
        windowHeight: Int,
        displayWidth: Int,
        displayHeight: Int,
    ): FullscreenDisplayStatus {
        if (windowWidth <= 0 || windowHeight <= 0 || displayWidth <= 0 || displayHeight <= 0) {
            return FullscreenDisplayStatus(
                FullscreenState.UNKNOWN,
                windowWidth,
                windowHeight,
                displayWidth,
                displayHeight,
            )
        }
        val fillsDisplay = abs(windowWidth - displayWidth) <= SIZE_TOLERANCE_PX &&
            abs(windowHeight - displayHeight) <= SIZE_TOLERANCE_PX
        return FullscreenDisplayStatus(
            if (fillsDisplay) FullscreenState.READY else FullscreenState.RESTRICTED,
            windowWidth,
            windowHeight,
            displayWidth,
            displayHeight,
        )
    }
}
