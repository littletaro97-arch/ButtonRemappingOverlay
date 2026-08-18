package com.example.buttonremapping

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.min
import kotlin.math.roundToInt

class ComponentEditorView(
    context: Context,
    label: String,
    fillColor: Int,
    strokeColor: Int,
    private val movementBoundsProvider: () -> Rect?,
    private val onGeometryChanged: () -> Unit,
) : FrameLayout(context) {
    private val resizeHandle: View
    private val labelView: TextView
    private val density = resources.displayMetrics.density
    private val backgroundDrawable = GradientDrawable()
    private var cornerRadiusRatio: Float? = null

    private var dragDownX = 0f
    private var dragDownY = 0f
    private var dragStartLeft = 0
    private var dragStartTop = 0
    private var resizeStartWidth = 0
    private var resizeStartHeight = 0
    private var handleDownX = 0f
    private var handleDownY = 0f
    private var resizing = false

    init {
        isClickable = true
        backgroundDrawable.shape = GradientDrawable.RECTANGLE
        backgroundDrawable.setColor(fillColor)
        backgroundDrawable.setStroke(dp(2), strokeColor)
        background = backgroundDrawable
        updateCornerRadius()
        labelView = TextView(context).apply {
            text = label
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(3), 0, dp(3), 0)
            setAutoSizeTextTypeUniformWithConfiguration(
                dp(8),
                dp(13),
                dp(1),
                TypedValue.COMPLEX_UNIT_PX,
            )
            setShadowLayer(3f, 0f, 1f, Color.BLACK)
            isClickable = false
        }
        addView(labelView, LayoutParams(-1, -1))

        resizeHandle = View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(4).toFloat()
                setColor(Color.argb(220, 244, 247, 251))
            }
            contentDescription = "调整大小"
            isClickable = false
            isFocusable = false
        }
        addView(resizeHandle, LayoutParams(dp(22), dp(22), Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, dp(5), dp(5))
        })
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val isResizeStart = event.actionMasked == MotionEvent.ACTION_DOWN &&
            isInsideResizeHandle(event.x, event.y)
        if (resizing || isResizeStart) {
            if (isResizeStart) resizing = true
            val handled = handleResizeTouch(event)
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                resizing = false
            }
            return handled
        }
        return handleDragTouch(event)
    }

    fun setPixelRect(rect: Rect) {
        val params = (layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(rect.width(), rect.height())
        params.width = rect.width().coerceAtLeast(dp(OverlayGeometry.MIN_COMPONENT_DP))
        params.height = rect.height().coerceAtLeast(dp(OverlayGeometry.MIN_COMPONENT_DP))
        params.leftMargin = rect.left
        params.topMargin = rect.top
        layoutParams = params
    }

    fun pixelRect(): Rect {
        val params = layoutParams as? FrameLayout.LayoutParams
            ?: return Rect(left, top, right, bottom)
        return Rect(
            params.leftMargin,
            params.topMargin,
            params.leftMargin + params.width,
            params.topMargin + params.height,
        )
    }

    fun setCornerRadiusRatio(value: Float) {
        cornerRadiusRatio = value.coerceIn(0f, 0.5f)
        updateCornerRadius()
    }

    private fun updateCornerRadius() {
        backgroundDrawable.cornerRadius = cornerRadiusRatio?.let {
            min(width, height) * it
        } ?: dp(10).toFloat()
        invalidate()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateCornerRadius()
    }

    private fun handleDragTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragDownX = event.rawX
                dragDownY = event.rawY
                val params = layoutParams as FrameLayout.LayoutParams
                dragStartLeft = params.leftMargin
                dragStartTop = params.topMargin
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val bounds = movementBoundsProvider()
                if (bounds == null) return true
                val parentPoint = rawToParent(event.rawX, event.rawY)
                val downPoint = rawToParent(dragDownX, dragDownY)
                val newLeft = dragStartLeft + (parentPoint.first - downPoint.first).roundToInt()
                val newTop = dragStartTop + (parentPoint.second - downPoint.second).roundToInt()
                moveWithin(bounds, newLeft, newTop)
                onGeometryChanged()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                onGeometryChanged()
                return true
            }
        }
        return true
    }

    private fun handleResizeTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handleDownX = event.rawX
                handleDownY = event.rawY
                val params = layoutParams as FrameLayout.LayoutParams
                resizeStartWidth = params.width
                resizeStartHeight = params.height
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val bounds = movementBoundsProvider()
                if (bounds == null) return true
                val parentPoint = rawToParent(event.rawX, event.rawY)
                val downPoint = rawToParent(handleDownX, handleDownY)
                val deltaX = (parentPoint.first - downPoint.first).roundToInt()
                val deltaY = (parentPoint.second - downPoint.second).roundToInt()
                val params = layoutParams as FrameLayout.LayoutParams
                val minWidth = OverlayGeometry.minimumWidth(bounds, density)
                val minHeight = OverlayGeometry.minimumHeight(bounds, density)
                val maxWidth = (bounds.right - params.leftMargin).coerceAtLeast(minWidth)
                val maxHeight = (bounds.bottom - params.topMargin).coerceAtLeast(minHeight)
                params.width = (resizeStartWidth + deltaX).coerceIn(minWidth, maxWidth)
                params.height = (resizeStartHeight + deltaY).coerceIn(minHeight, maxHeight)
                layoutParams = params
                onGeometryChanged()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                onGeometryChanged()
                return true
            }
        }
        return true
    }

    private fun moveWithin(bounds: Rect, requestedLeft: Int, requestedTop: Int) {
        val params = layoutParams as FrameLayout.LayoutParams
        params.leftMargin = requestedLeft.coerceIn(bounds.left, bounds.right - params.width)
        params.topMargin = requestedTop.coerceIn(bounds.top, bounds.bottom - params.height)
        layoutParams = params
    }

    private fun isInsideResizeHandle(x: Float, y: Float): Boolean =
        x >= resizeHandle.left &&
            x <= resizeHandle.right &&
            y >= resizeHandle.top &&
            y <= resizeHandle.bottom

    private fun rawToParent(rawX: Float, rawY: Float): Pair<Float, Float> {
        val parentView = parent as? View ?: return rawX to rawY
        val location = IntArray(2)
        parentView.getLocationOnScreen(location)
        return (rawX - location[0]) to (rawY - location[1])
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

}
