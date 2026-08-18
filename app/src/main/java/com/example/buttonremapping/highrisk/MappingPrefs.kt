package com.example.buttonremapping.highrisk

import android.content.Context
import android.view.Surface
import com.example.buttonremapping.ComponentRatio
import com.example.buttonremapping.OverlayGeometry
import com.example.buttonremapping.profile.ProfileManager

data class MappingConfig(
    /**
     * 目标拦截区域：位置与尺寸（与中低风险的屏蔽区域/长按区域同一套
     * ComponentRatio 模型），编辑器所见即运行时拦截窗口，往返一致。
     */
    val targetBlock: ComponentRatio = ComponentRatio(
        xRatio = 0.69f,
        yRatio = 0.67f,
        widthRatio = 0.14f,
        heightRatio = 0.18f,
    ),
    val virtualButton: ComponentRatio = ComponentRatio(
        xRatio = 0.48f,
        yRatio = 0.38f,
        widthRatio = 0.105f,
        heightRatio = 0.18f,
    ),
    val virtualButtonAlpha: Float = 0.88f,
    val virtualButtonCornerRadius: Float = 0.28f,
    val targetCornerRadius: Float = 0.08f,
    val screenshotUri: String? = null,
    val screenshotWidth: Int = 0,
    val screenshotHeight: Int = 0,
    val coordinateRotation: Int = Surface.ROTATION_90,
    val configured: Boolean = false,
    val triggerEnabled: Boolean = false,
    val triggerPackages: List<String> = emptyList(),
) {
    val hasScreenshot: Boolean
        get() = !screenshotUri.isNullOrBlank() && screenshotWidth > 0 && screenshotHeight > 0
}

object MappingPrefs {
    private const val PREFS_NAME = "single_mapping_config"
    private const val TARGET_X = "target_x_ratio"
    private const val TARGET_Y = "target_y_ratio"
    private const val VIRTUAL_X = "virtual_x_ratio"
    private const val VIRTUAL_Y = "virtual_y_ratio"
    private const val VIRTUAL_WIDTH = "virtual_width_ratio"
    private const val VIRTUAL_HEIGHT = "virtual_height_ratio"
    private const val VIRTUAL_ALPHA = "virtual_alpha"
    private const val SCREENSHOT_URI = "screenshot_uri"
    private const val SCREENSHOT_WIDTH = "screenshot_width"
    private const val SCREENSHOT_HEIGHT = "screenshot_height"
    private const val COORDINATE_ROTATION = "coordinate_rotation"
    private const val CONFIGURED = "configured"

    fun load(context: Context): MappingConfig = ProfileManager.loadHigh(context)

    fun save(context: Context, config: MappingConfig) = ProfileManager.saveHigh(context, config)

    internal fun loadLegacy(context: Context): MappingConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(TARGET_X) && !prefs.contains(VIRTUAL_X)) {
            return MappingConfig()
        }
        // 旧版只保存目标中心点：以默认尺寸围绕该中心构造目标区域。
        val defaultBlock = MappingConfig().targetBlock
        val centerX = prefs.getFloat(TARGET_X, 0.76f).coerceIn(0f, 1f)
        val centerY = prefs.getFloat(TARGET_Y, 0.76f).coerceIn(0f, 1f)
        return MappingConfig(
            targetBlock = ComponentRatio(
                xRatio = (centerX - defaultBlock.widthRatio / 2f)
                    .coerceIn(0f, (1f - defaultBlock.widthRatio).coerceAtLeast(0f)),
                yRatio = (centerY - defaultBlock.heightRatio / 2f)
                    .coerceIn(0f, (1f - defaultBlock.heightRatio).coerceAtLeast(0f)),
                widthRatio = defaultBlock.widthRatio,
                heightRatio = defaultBlock.heightRatio,
            ),
            virtualButton = ComponentRatio(
                xRatio = prefs.getFloat(VIRTUAL_X, 0.48f).coerceIn(0f, 1f),
                yRatio = prefs.getFloat(VIRTUAL_Y, 0.38f).coerceIn(0f, 1f),
                widthRatio = prefs.getFloat(VIRTUAL_WIDTH, 0.105f)
                    .coerceIn(OverlayGeometry.MIN_COMPONENT_RATIO, 1f),
                heightRatio = prefs.getFloat(VIRTUAL_HEIGHT, 0.18f)
                    .coerceIn(OverlayGeometry.MIN_COMPONENT_RATIO, 1f),
            ),
            virtualButtonAlpha = prefs.getFloat(VIRTUAL_ALPHA, 0.88f).coerceIn(0.25f, 1f),
            screenshotUri = prefs.getString(SCREENSHOT_URI, null),
            screenshotWidth = prefs.getInt(SCREENSHOT_WIDTH, 0),
            screenshotHeight = prefs.getInt(SCREENSHOT_HEIGHT, 0),
            coordinateRotation = prefs.getInt(COORDINATE_ROTATION, Surface.ROTATION_90).coerceIn(0, 3),
            configured = prefs.getBoolean(CONFIGURED, false),
        )
    }

    internal fun saveLegacy(context: Context, config: MappingConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(TARGET_X, (config.targetBlock.xRatio + config.targetBlock.widthRatio / 2f).coerceIn(0f, 1f))
            .putFloat(TARGET_Y, (config.targetBlock.yRatio + config.targetBlock.heightRatio / 2f).coerceIn(0f, 1f))
            .putFloat(VIRTUAL_X, config.virtualButton.xRatio.coerceIn(0f, 1f))
            .putFloat(VIRTUAL_Y, config.virtualButton.yRatio.coerceIn(0f, 1f))
            .putFloat(VIRTUAL_WIDTH, config.virtualButton.widthRatio.coerceIn(OverlayGeometry.MIN_COMPONENT_RATIO, 1f))
            .putFloat(VIRTUAL_HEIGHT, config.virtualButton.heightRatio.coerceIn(OverlayGeometry.MIN_COMPONENT_RATIO, 1f))
            .putFloat(VIRTUAL_ALPHA, config.virtualButtonAlpha.coerceIn(0.25f, 1f))
            .putInt(SCREENSHOT_WIDTH, config.screenshotWidth.coerceAtLeast(0))
            .putInt(SCREENSHOT_HEIGHT, config.screenshotHeight.coerceAtLeast(0))
            .putInt(COORDINATE_ROTATION, config.coordinateRotation.coerceIn(0, 3))
            .putBoolean(CONFIGURED, config.configured)
            .apply {
                if (config.screenshotUri.isNullOrBlank()) {
                    remove(SCREENSHOT_URI)
                } else {
                    putString(SCREENSHOT_URI, config.screenshotUri)
                }
            }
            .apply()
    }
}
