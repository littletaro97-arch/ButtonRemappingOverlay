package com.example.buttonremapping.highrisk

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.min

@SuppressLint("ViewConstructor")
class MappingVirtualButtonView(
    context: Context,
    private val onTap: () -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 244, 247, 251)
        style = Paint.Style.STROKE
        strokeWidth = density * 1.5f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD)
    }
    private val drawRect = RectF()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var cornerRadiusRatio = 0.22f
    private var pressed = false
    private var moved = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var downTime = 0L

    init {
        isClickable = true
        isFocusable = false
        isHapticFeedbackEnabled = true
        contentDescription = "新按钮，单击触发一次目标点击"
    }

    fun setCornerRadiusRatio(value: Float) {
        cornerRadiusRatio = value.coerceIn(0f, 0.5f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // View 的整个布局矩形就是实际可触摸范围；边框只内缩半个线宽，
        // 避免把可触摸区域视觉上画得比实际窗口小一圈。
        val inset = strokePaint.strokeWidth / 2f
        val radius = min(width, height) * cornerRadiusRatio
        drawRect.set(inset, inset, width - inset, height - inset)
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = if (pressed) Color.rgb(153, 193, 255) else Color.rgb(74, 127, 214)
        canvas.drawRoundRect(drawRect, radius, radius, fillPaint)
        canvas.drawRoundRect(drawRect, radius, radius, strokePaint)
        textPaint.textSize = min(density * 13f, min(width, height) * 0.2f)
            .coerceAtLeast(density * 8f)
        val baseline = height / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText("新按钮", width / 2f, baseline, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                moved = false
                downRawX = event.rawX
                downRawY = event.rawY
                downTime = SystemClock.uptimeMillis()
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (abs(event.rawX - downRawX) > touchSlop ||
                    abs(event.rawY - downRawY) > touchSlop
                ) {
                    moved = true
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val duration = SystemClock.uptimeMillis() - downTime
                if (!moved && duration < MAX_CLICK_MS) {
                    performClick()
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onTap()
                }
                pressed = false
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                pressed = false
                invalidate()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        // 只由 onDraw 的 pressed 颜色提供反馈，不缩放整个 View。
        // 缩放会让部分系统/OEM 按变换后的可见区域命中，造成快速重复
        // 点击时边缘区域看似存在但实际无法触发。
        invalidate()
        return true
    }

    companion object {
        private const val MAX_CLICK_MS = 800L
    }
}
