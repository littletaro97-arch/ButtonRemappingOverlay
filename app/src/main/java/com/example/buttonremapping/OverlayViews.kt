package com.example.buttonremapping

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

class BlockOverlayView(context: Context) : View(context) {
    init {
        isClickable = true
        isFocusable = false
        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Deliberately consume only this small window. No event is forwarded or injected.
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

@SuppressLint("ViewConstructor")
class LowRiskToggleView(
    context: Context,
    private val onToggle: () -> Unit,
    private val onDragStart: () -> Unit,
    private val onDrag: (Float, Float) -> Unit,
    private val onDragEnd: () -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 245, 248, 252)
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
    private var blocked = true
    private var pressed = false
    private var dragging = false
    private var moved = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var downTime = 0L

    private val longPressRunnable = Runnable {
        if (pressed) {
            dragging = true
            onDragStart()
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            animate().cancel()
            animate().scaleX(0.92f).scaleY(0.92f).setDuration(120L).start()
        }
    }

    init {
        isClickable = true
        isFocusable = false
        isLongClickable = false
        isHapticFeedbackEnabled = true
        setBlocked(true)
    }

    fun setBlocked(value: Boolean) {
        blocked = value
        contentDescription = if (value) {
            "屏蔽开关：当前已屏蔽，长按可拖动"
        } else {
            "屏蔽开关：当前允许点击，长按可拖动"
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = density * 3f
        val radius = min(width, height) * 0.22f
        drawRect.set(inset, inset, width - inset, height - inset)
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = if (blocked) {
            Color.rgb(218, 78, 83)
        } else {
            Color.rgb(64, 181, 119)
        }
        canvas.drawRoundRect(drawRect, radius, radius, fillPaint)
        canvas.drawRoundRect(drawRect, radius, radius, outlinePaint)
        if (pressed) {
            fillPaint.color = Color.argb(55, 255, 255, 255)
            canvas.drawRoundRect(drawRect, radius, radius, fillPaint)
        }
        textPaint.textSize = min(
            density * 12f,
            min(width, height) * 0.22f,
        ).coerceAtLeast(density * 8f)
        val label = if (blocked) "屏蔽" else "允许"
        val baseline = height / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(label, width / 2f, baseline, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                moved = false
                dragging = false
                downRawX = event.rawX
                downRawY = event.rawY
                downTime = SystemClock.uptimeMillis()
                postDelayed(longPressRunnable, LONG_PRESS_MS)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaFromDownX = event.rawX - downRawX
                val deltaFromDownY = event.rawY - downRawY
                if (!dragging &&
                    (abs(deltaFromDownX) > touchSlop || abs(deltaFromDownY) > touchSlop)
                ) {
                    moved = true
                }
                if (dragging) {
                    onDrag(event.rawX - downRawX, event.rawY - downRawY)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                val duration = SystemClock.uptimeMillis() - downTime
                if (dragging) {
                    onDragEnd()
                } else if (!moved && duration < MAX_CLICK_MS) {
                    performClick()
                    onToggle()
                }
                pressed = false
                dragging = false
                animate().scaleX(1f).scaleY(1f).setDuration(140L).start()
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                if (dragging) onDragEnd()
                pressed = false
                dragging = false
                animate().scaleX(1f).scaleY(1f).setDuration(140L).start()
                invalidate()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    companion object {
        private const val LONG_PRESS_MS = 450L
        private const val MAX_CLICK_MS = 800L
    }
}
