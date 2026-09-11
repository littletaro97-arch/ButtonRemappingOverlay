package com.example.buttonremapping

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.view.Surface
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class InsetsPx(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
)

data class DisplayGeometry(
    val width: Int,
    val height: Int,
    val safeBounds: Rect,
    val rotation: Int,
    val density: Float,
) {
    val fullBounds: Rect
        get() = Rect(0, 0, width, height)
}

object OverlayGeometry {
    const val CURRENT_COORDINATE_SPACE_VERSION = 2
    const val MIN_COMPONENT_DP = 38
    const val MIN_COMPONENT_RATIO = 0.018f
    private const val HORIZONTAL_SAFE_RATIO = 0.06f
    private const val VERTICAL_SAFE_RATIO = 0.08f

    fun fromRoot(view: View, windowInsets: WindowInsets?): DisplayGeometry {
        val insets = windowInsets?.let(::readInsets) ?: InsetsPx()
        return create(
            width = view.width,
            height = view.height,
            insets = insets,
            density = view.resources.displayMetrics.density,
            rotation = view.display?.rotation ?: Surface.ROTATION_0,
        )
    }

    fun fromWindowManager(context: Context): DisplayGeometry {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = context.resources.displayMetrics.density
        @Suppress("DEPRECATION")
        val rotation = windowManager.defaultDisplay.rotation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            val insets = readInsets(metrics.windowInsets)
            return create(bounds.width(), bounds.height(), insets, density, rotation)
        }

        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return create(metrics.widthPixels, metrics.heightPixels, InsetsPx(), density, rotation)
    }

    /**
     * Layout ratios are stored on a canonical landscape canvas. The returned
     * rectangle is transformed into the current display orientation, so a
     * portrait service start does not reinterpret landscape width as portrait
     * height.
     */
    fun toPixelRect(
        component: ComponentRatio,
        geometry: DisplayGeometry,
        coordinateRotation: Int = Surface.ROTATION_90,
    ): Rect {
        val canonicalWidth = max(geometry.width, geometry.height)
        val canonicalHeight = min(geometry.width, geometry.height)
        val canonicalBounds = Rect(0, 0, canonicalWidth, canonicalHeight)
        val canonicalRect = ratioToRect(component, canonicalBounds, geometryDensity(geometry))
        val turns = quarterTurns(coordinateRotation, geometry.rotation)
        return rotateRect(canonicalRect, canonicalWidth, canonicalHeight, turns)
    }

    fun toPixelPoint(
        xRatio: Float,
        yRatio: Float,
        geometry: DisplayGeometry,
        coordinateRotation: Int = Surface.ROTATION_90,
    ): Point {
        val canonicalWidth = max(geometry.width, geometry.height)
        val canonicalHeight = min(geometry.width, geometry.height)
        val x = (canonicalWidth * xRatio.coerceIn(0f, 1f)).roundToInt()
        val y = (canonicalHeight * yRatio.coerceIn(0f, 1f)).roundToInt()
        val turns = quarterTurns(coordinateRotation, geometry.rotation)
        return rotatePoint(x, y, canonicalWidth, canonicalHeight, turns)
    }

    /**
     * Converts an editor rectangle back to the canonical landscape canvas.
     * The editor is locked to landscape, but the rotation parameter keeps the
     * conversion correct if the device uses the opposite landscape side.
     */
    fun toRatio(
        rect: Rect,
        geometry: DisplayGeometry,
        coordinateRotation: Int = geometry.rotation,
    ): ComponentRatio {
        val turns = quarterTurns(geometry.rotation, coordinateRotation)
        val canonicalRect = rotateRect(rect, geometry.width, geometry.height, turns)
        val canonicalWidth = max(geometry.width, geometry.height)
        val canonicalHeight = min(geometry.width, geometry.height)
        return ratioFromRect(
            canonicalRect,
            Rect(0, 0, canonicalWidth, canonicalHeight),
        )
    }

    /**
     * 将当前屏幕坐标点转换为 canonical 横屏坐标系比例（与 [toPixelPoint] 互逆）。
     * 编辑器保存目标位置时应使用此方法，保证与运行时方向一致。
     */
    fun pointToRatio(
        x: Int,
        y: Int,
        geometry: DisplayGeometry,
        coordinateRotation: Int = geometry.rotation,
    ): Pair<Float, Float> {
        val canonicalWidth = max(geometry.width, geometry.height)
        val canonicalHeight = min(geometry.width, geometry.height)
        // 从当前几何旋转转到保存方向（与 toPixelPoint 的 turns 相反）。
        val turns = quarterTurns(geometry.rotation, coordinateRotation)
        val canonicalPoint = rotatePoint(x, y, geometry.width, geometry.height, turns)
        return (canonicalPoint.x.toFloat() / canonicalWidth.coerceAtLeast(1))
            .coerceIn(0f, 1f) to
            (canonicalPoint.y.toFloat() / canonicalHeight.coerceAtLeast(1))
                .coerceIn(0f, 1f)
    }

    /**
     * AccessibilityService.dispatchGesture uses the same current logical
     * display coordinates exposed by accessibility windows. WindowManager
     * overlay layout and accessibility window bounds therefore share this
     * coordinate space; applying a second physical-display rotation would put
     * a landscape point outside the reported accessibility display.
     */
    @Suppress("UNUSED_PARAMETER")
    fun toAccessibilityPoint(point: Point, geometry: DisplayGeometry): Point = Point(point)

    fun minimumWidth(bounds: Rect, density: Float): Int = max(
        dp(MIN_COMPONENT_DP, density),
        (bounds.width() * MIN_COMPONENT_RATIO).roundToInt(),
    ).coerceAtMost(bounds.width().coerceAtLeast(1))

    fun minimumHeight(bounds: Rect, density: Float): Int = max(
        dp(MIN_COMPONENT_DP, density),
        (bounds.height() * MIN_COMPONENT_RATIO).roundToInt(),
    ).coerceAtMost(bounds.height().coerceAtLeast(1))

    fun componentToRect(component: ComponentRatio, bounds: Rect, density: Float): Rect =
        ratioToRect(component, bounds, density)

    fun componentFromRect(rect: Rect, bounds: Rect): ComponentRatio =
        ratioFromRect(rect, bounds)

    private fun ratioToRect(
        component: ComponentRatio,
        bounds: Rect,
        density: Float,
    ): Rect {
        val width = (bounds.width() * component.widthRatio.coerceIn(MIN_COMPONENT_RATIO, 1f))
            .roundToInt()
            .coerceIn(minimumWidth(bounds, density), bounds.width().coerceAtLeast(1))
        val height = (bounds.height() * component.heightRatio.coerceIn(MIN_COMPONENT_RATIO, 1f))
            .roundToInt()
            .coerceIn(minimumHeight(bounds, density), bounds.height().coerceAtLeast(1))
        val left = (bounds.left + bounds.width() * component.xRatio.coerceIn(0f, 1f))
            .roundToInt()
            .coerceIn(bounds.left, bounds.right - width)
        val top = (bounds.top + bounds.height() * component.yRatio.coerceIn(0f, 1f))
            .roundToInt()
            .coerceIn(bounds.top, bounds.bottom - height)
        return Rect(left, top, left + width, top + height)
    }

    private fun ratioFromRect(rect: Rect, bounds: Rect): ComponentRatio {
        val widthRatio = (rect.width().toFloat() / bounds.width().coerceAtLeast(1))
            .coerceIn(MIN_COMPONENT_RATIO, 1f)
        val heightRatio = (rect.height().toFloat() / bounds.height().coerceAtLeast(1))
            .coerceIn(MIN_COMPONENT_RATIO, 1f)
        val maxX = (1f - widthRatio).coerceAtLeast(0f)
        val maxY = (1f - heightRatio).coerceAtLeast(0f)
        return ComponentRatio(
            xRatio = ((rect.left - bounds.left).toFloat() / bounds.width().coerceAtLeast(1))
                .coerceIn(0f, maxX),
            yRatio = ((rect.top - bounds.top).toFloat() / bounds.height().coerceAtLeast(1))
                .coerceIn(0f, maxY),
            widthRatio = widthRatio,
            heightRatio = heightRatio,
        )
    }

    private fun rotateRect(rect: Rect, sourceWidth: Int, sourceHeight: Int, turns: Int): Rect {
        return when (turns.mod(4)) {
            0 -> Rect(rect)
            1 -> Rect(
                sourceHeight - rect.bottom,
                rect.left,
                sourceHeight - rect.top,
                rect.right,
            )
            2 -> Rect(
                sourceWidth - rect.right,
                sourceHeight - rect.bottom,
                sourceWidth - rect.left,
                sourceHeight - rect.top,
            )
            else -> Rect(
                rect.top,
                sourceWidth - rect.right,
                rect.bottom,
                sourceWidth - rect.left,
            )
        }
    }

    private fun rotatePoint(
        x: Int,
        y: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        turns: Int,
    ): Point = when (turns.mod(4)) {
        0 -> Point(x, y)
        1 -> Point(sourceHeight - y, x)
        2 -> Point(sourceWidth - x, sourceHeight - y)
        else -> Point(y, sourceWidth - x)
    }

    private fun quarterTurns(fromRotation: Int, toRotation: Int): Int =
        CoordinateRotationPolicy.quarterTurns(fromRotation, toRotation)

    private fun geometryDensity(geometry: DisplayGeometry): Float = geometry.density

    private fun create(
        width: Int,
        height: Int,
        insets: InsetsPx,
        density: Float,
        rotation: Int,
    ): DisplayGeometry {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val horizontalMargin = max(dp(24, density), (safeWidth * HORIZONTAL_SAFE_RATIO).roundToInt())
        val verticalMargin = max(dp(24, density), (safeHeight * VERTICAL_SAFE_RATIO).roundToInt())
        var left = insets.left + horizontalMargin
        var top = insets.top + verticalMargin
        var right = safeWidth - insets.right - horizontalMargin
        var bottom = safeHeight - insets.bottom - verticalMargin

        if (right <= left + dp(120, density)) {
            left = insets.left + dp(24, density)
            right = safeWidth - insets.right - dp(24, density)
        }
        if (bottom <= top + dp(96, density)) {
            top = insets.top + dp(24, density)
            bottom = safeHeight - insets.bottom - dp(24, density)
        }

        return DisplayGeometry(
            width = safeWidth,
            height = safeHeight,
            safeBounds = Rect(
                left.coerceIn(0, safeWidth),
                top.coerceIn(0, safeHeight),
                right.coerceIn(0, safeWidth),
                bottom.coerceIn(0, safeHeight),
            ),
            rotation = rotation,
            density = density,
        )
    }

    private fun readInsets(windowInsets: WindowInsets): InsetsPx {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val types = WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            val insets = windowInsets.getInsets(types)
            return InsetsPx(insets.left, insets.top, insets.right, insets.bottom)
        }

        @Suppress("DEPRECATION")
        return InsetsPx(
            left = windowInsets.systemWindowInsetLeft,
            top = windowInsets.systemWindowInsetTop,
            right = windowInsets.systemWindowInsetRight,
            bottom = windowInsets.systemWindowInsetBottom,
        )
    }

    private fun dp(value: Int, density: Float): Int = (value * density).roundToInt()
}
