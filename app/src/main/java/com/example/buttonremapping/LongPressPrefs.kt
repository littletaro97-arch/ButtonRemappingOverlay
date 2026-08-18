package com.example.buttonremapping

import android.content.Context
import android.view.Surface
import com.example.buttonremapping.profile.ProfileManager

/**
 * 长按触发模式配置：在指定区域把"按一下触发"变为"长按满设定时间才触发"。
 */
data class LongPressConfig(
    val area: ComponentRatio = ComponentRatio(
        xRatio = 0.5f,
        yRatio = 0.5f,
        widthRatio = 0.17f,
        heightRatio = 0.13f,
    ),
    val areaAlpha: Float = 0.35f,
    val cornerRadius: Float = 0.5f,
    /** 长按触发时间（毫秒）。甜点值默认 500ms，可调 300–3000ms。 */
    val longPressMs: Int = 500,
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

object LongPressPrefs {
    fun load(context: Context): LongPressConfig = ProfileManager.loadLong(context)

    fun save(context: Context, config: LongPressConfig) = ProfileManager.saveLong(context, config)
}
