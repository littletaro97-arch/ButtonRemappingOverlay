package com.example.buttonremapping

import android.content.Context
import android.view.Surface
import com.example.buttonremapping.profile.ProfileManager

data class ComponentRatio(
    val xRatio: Float,
    val yRatio: Float,
    val widthRatio: Float,
    val heightRatio: Float,
)

data class LayoutConfig(
    val blockedArea: ComponentRatio,
    val virtualButton: ComponentRatio,
    val virtualButtonAlpha: Float,
    val blockedAreaAlpha: Float = 0.38f,
    val blockedCornerRadius: Float = 0.5f,
    val toggleButton: ComponentRatio = ComponentRatio(
        xRatio = 0.84f,
        yRatio = 0.55f,
        widthRatio = 0.065f,
        heightRatio = 0.09f,
    ),
    val screenshotUri: String? = null,
    val screenshotWidth: Int = 0,
    val screenshotHeight: Int = 0,
    val coordinateSpaceVersion: Int = OverlayGeometry.CURRENT_COORDINATE_SPACE_VERSION,
    val coordinateRotation: Int = Surface.ROTATION_90,
    val toggleAlpha: Float = 0.9f,
    val triggerEnabled: Boolean = false,
    val triggerPackages: List<String> = emptyList(),
) {
    val hasScreenshot: Boolean
        get() = !screenshotUri.isNullOrBlank() && screenshotWidth > 0 && screenshotHeight > 0
}

object LayoutPrefs {
    private const val PREFS_NAME = "layout_config"

    private const val SPACE_VERSION = "coordinate_space_version"
    private const val COORDINATE_ROTATION = "coordinate_rotation"
    private const val BLOCKED_X = "blocked_x"
    private const val BLOCKED_Y = "blocked_y"
    private const val BLOCKED_WIDTH = "blocked_width"
    private const val BLOCKED_HEIGHT = "blocked_height"
    private const val BUTTON_X = "button_x"
    private const val BUTTON_Y = "button_y"
    private const val BUTTON_WIDTH = "button_width"
    private const val BUTTON_HEIGHT = "button_height"
    private const val BUTTON_ALPHA = "button_alpha"
    private const val BLOCKED_ALPHA = "blocked_alpha"
    private const val BLOCKED_CORNER_RADIUS = "blocked_corner_radius"
    private const val TOGGLE_X = "toggle_x"
    private const val TOGGLE_Y = "toggle_y"
    private const val TOGGLE_WIDTH = "toggle_width"
    private const val TOGGLE_HEIGHT = "toggle_height"
    private const val SCREENSHOT_URI = "screenshot_uri"
    private const val SCREENSHOT_WIDTH = "screenshot_width"
    private const val SCREENSHOT_HEIGHT = "screenshot_height"

    /*
     * Ratios are relative to a canonical landscape full-screen canvas. The
     * defaults are deliberately inset from every edge, while the editor is
     * free to place a component anywhere on that canvas.
     */
    val defaults = LayoutConfig(
        blockedArea = ComponentRatio(
            xRatio = 0.68f,
            yRatio = 0.70f,
            widthRatio = 0.17f,
            heightRatio = 0.13f,
        ),
        virtualButton = ComponentRatio(
            xRatio = 0.78f,
            yRatio = 0.40f,
            widthRatio = 0.105f,
            heightRatio = 0.18f,
        ),
        virtualButtonAlpha = 0.88f,
        blockedAreaAlpha = 0.38f,
        blockedCornerRadius = 0.5f,
        toggleButton = ComponentRatio(
            xRatio = 0.84f,
            yRatio = 0.55f,
            widthRatio = 0.065f,
            heightRatio = 0.09f,
        ),
        coordinateSpaceVersion = OverlayGeometry.CURRENT_COORDINATE_SPACE_VERSION,
        coordinateRotation = Surface.ROTATION_90,
    )

    fun load(context: Context): LayoutConfig = ProfileManager.loadLow(context)

    fun save(context: Context, config: LayoutConfig) = ProfileManager.saveLow(context, config)

    internal fun loadLegacy(context: Context): LayoutConfig {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedVersion = preferences.getInt(SPACE_VERSION, 0)
        val hasSavedLayout = preferences.contains(BLOCKED_X) ||
            preferences.contains(BUTTON_X) ||
            preferences.contains(SCREENSHOT_URI)
        if (!hasSavedLayout) return defaults

        val raw = LayoutConfig(
            blockedArea = ComponentRatio(
                xRatio = preferences.getFloat(BLOCKED_X, defaults.blockedArea.xRatio),
                yRatio = preferences.getFloat(BLOCKED_Y, defaults.blockedArea.yRatio),
                widthRatio = preferences.getFloat(BLOCKED_WIDTH, defaults.blockedArea.widthRatio),
                heightRatio = preferences.getFloat(BLOCKED_HEIGHT, defaults.blockedArea.heightRatio),
            ),
            virtualButton = ComponentRatio(
                xRatio = preferences.getFloat(BUTTON_X, defaults.virtualButton.xRatio),
                yRatio = preferences.getFloat(BUTTON_Y, defaults.virtualButton.yRatio),
                widthRatio = preferences.getFloat(BUTTON_WIDTH, defaults.virtualButton.widthRatio),
                heightRatio = preferences.getFloat(BUTTON_HEIGHT, defaults.virtualButton.heightRatio),
            ),
            virtualButtonAlpha = preferences
                .getFloat(BUTTON_ALPHA, defaults.virtualButtonAlpha)
                .coerceIn(0.25f, 1f),
            blockedAreaAlpha = preferences
                .getFloat(BLOCKED_ALPHA, defaults.blockedAreaAlpha)
                .coerceIn(0f, 1f),
            blockedCornerRadius = preferences
                .getFloat(BLOCKED_CORNER_RADIUS, defaults.blockedCornerRadius)
                .coerceIn(0f, 0.5f),
            toggleButton = ComponentRatio(
                xRatio = preferences.getFloat(TOGGLE_X, defaults.toggleButton.xRatio),
                yRatio = preferences.getFloat(TOGGLE_Y, defaults.toggleButton.yRatio),
                widthRatio = preferences.getFloat(TOGGLE_WIDTH, defaults.toggleButton.widthRatio),
                heightRatio = preferences.getFloat(TOGGLE_HEIGHT, defaults.toggleButton.heightRatio),
            ),
            screenshotUri = preferences.getString(SCREENSHOT_URI, null),
            screenshotWidth = preferences.getInt(SCREENSHOT_WIDTH, 0),
            screenshotHeight = preferences.getInt(SCREENSHOT_HEIGHT, 0),
            coordinateSpaceVersion = storedVersion,
            coordinateRotation = preferences
                .getInt(COORDINATE_ROTATION, Surface.ROTATION_90)
                .coerceIn(0, 3),
        )

        if (storedVersion >= OverlayGeometry.CURRENT_COORDINATE_SPACE_VERSION) {
            val current = raw.copy(
                coordinateSpaceVersion = OverlayGeometry.CURRENT_COORDINATE_SPACE_VERSION,
            )
            if (!preferences.contains(TOGGLE_X)) write(context, current)
            return current
        }

        // v1 values were relative to a dynamic safe rectangle. Convert them
        // once to the new full-canvas space so an existing layout is not lost.
        val migrated = raw.copy(
            blockedArea = migrateLegacyComponent(raw.blockedArea),
            virtualButton = migrateLegacyComponent(raw.virtualButton),
            coordinateSpaceVersion = OverlayGeometry.CURRENT_COORDINATE_SPACE_VERSION,
            coordinateRotation = Surface.ROTATION_90,
        )
        write(context, migrated)
        return migrated
    }

    internal fun saveLegacy(context: Context, config: LayoutConfig) {
        write(
            context,
            config.copy(
                coordinateSpaceVersion = OverlayGeometry.CURRENT_COORDINATE_SPACE_VERSION,
                coordinateRotation = config.coordinateRotation.coerceIn(0, 3),
            ),
        )
    }

    private fun write(context: Context, config: LayoutConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(SPACE_VERSION, OverlayGeometry.CURRENT_COORDINATE_SPACE_VERSION)
            .putInt(COORDINATE_ROTATION, config.coordinateRotation.coerceIn(0, 3))
            .putFloat(BLOCKED_X, config.blockedArea.xRatio.coerceIn(0f, 1f))
            .putFloat(BLOCKED_Y, config.blockedArea.yRatio.coerceIn(0f, 1f))
            .putFloat(BLOCKED_WIDTH, config.blockedArea.widthRatio.coerceIn(OverlayGeometry.MIN_COMPONENT_RATIO, 1f))
            .putFloat(BLOCKED_HEIGHT, config.blockedArea.heightRatio.coerceIn(OverlayGeometry.MIN_COMPONENT_RATIO, 1f))
            .putFloat(BUTTON_X, config.virtualButton.xRatio.coerceIn(0f, 1f))
            .putFloat(BUTTON_Y, config.virtualButton.yRatio.coerceIn(0f, 1f))
            .putFloat(BUTTON_WIDTH, config.virtualButton.widthRatio.coerceIn(OverlayGeometry.MIN_COMPONENT_RATIO, 1f))
            .putFloat(BUTTON_HEIGHT, config.virtualButton.heightRatio.coerceIn(OverlayGeometry.MIN_COMPONENT_RATIO, 1f))
            .putFloat(BUTTON_ALPHA, config.virtualButtonAlpha.coerceIn(0.25f, 1f))
            .putFloat(BLOCKED_ALPHA, config.blockedAreaAlpha.coerceIn(0f, 1f))
            .putFloat(BLOCKED_CORNER_RADIUS, config.blockedCornerRadius.coerceIn(0f, 0.5f))
            .putFloat(TOGGLE_X, config.toggleButton.xRatio.coerceIn(0f, 1f))
            .putFloat(TOGGLE_Y, config.toggleButton.yRatio.coerceIn(0f, 1f))
            .putFloat(TOGGLE_WIDTH, config.toggleButton.widthRatio.coerceIn(OverlayGeometry.MIN_COMPONENT_RATIO, 1f))
            .putFloat(TOGGLE_HEIGHT, config.toggleButton.heightRatio.coerceIn(OverlayGeometry.MIN_COMPONENT_RATIO, 1f))
            .putInt(SCREENSHOT_WIDTH, config.screenshotWidth.coerceAtLeast(0))
            .putInt(SCREENSHOT_HEIGHT, config.screenshotHeight.coerceAtLeast(0))
            .apply {
                if (config.screenshotUri.isNullOrBlank()) {
                    remove(SCREENSHOT_URI)
                } else {
                    putString(SCREENSHOT_URI, config.screenshotUri)
                }
            }
            .apply()
    }

    private fun migrateLegacyComponent(component: ComponentRatio): ComponentRatio {
        val availableWidth = 1f - 2f * 0.06f
        val availableHeight = 1f - 2f * 0.08f
        val width = (component.widthRatio * availableWidth)
            .coerceIn(OverlayGeometry.MIN_COMPONENT_RATIO, 1f)
        val height = (component.heightRatio * availableHeight)
            .coerceIn(OverlayGeometry.MIN_COMPONENT_RATIO, 1f)
        return ComponentRatio(
            xRatio = (0.06f + component.xRatio.coerceIn(0f, 1f) * availableWidth)
                .coerceIn(0f, (1f - width).coerceAtLeast(0f)),
            yRatio = (0.08f + component.yRatio.coerceIn(0f, 1f) * availableHeight)
                .coerceIn(0f, (1f - height).coerceAtLeast(0f)),
            widthRatio = width,
            heightRatio = height,
        )
    }
}
