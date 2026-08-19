package com.example.buttonremapping

import android.content.Context
import android.view.Surface
import com.example.buttonremapping.profile.ProfileManager

/**
 * 双击触发模式配置：在指定区域把"单击触发"改为"双击触发"。
 * 单次点击被吃掉；双击才注入一次点击到区域中心。
 */
data class DoubleTapConfig(
    val area: ComponentRatio = ComponentRatio(
        xRatio = 0.5f,
        yRatio = 0.5f,
        widthRatio = 0.17f,
        heightRatio = 0.13f,
    ),
    val areaAlpha: Float = 0.35f,
    val cornerRadius: Float = 0.5f,
    val screenshotUri: String? = null,
    val screenshotWidth: Int = 0,
    val screenshotHeight: Int = 0,
    val coordinateSpaceVersion: Int = OverlayGeometry.CURRENT_COORDINATE_SPACE_VERSION,
    val coordinateRotation: Int = Surface.ROTATION_90,
    val triggerEnabled: Boolean = false,
    val triggerPackages: List<String> = emptyList(),
) {
    val hasScreenshot: Boolean
        get() = !screenshotUri.isNullOrBlank() && screenshotWidth > 0 && screenshotHeight > 0
}

object DoubleTapPrefs {
    fun load(context: Context): DoubleTapConfig = ProfileManager.loadDouble(context)

    fun save(context: Context, config: DoubleTapConfig) = ProfileManager.saveDouble(context, config)
}
