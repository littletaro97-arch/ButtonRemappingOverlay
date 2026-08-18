package com.example.inputtestapp

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var probeView: TouchProbeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(14, 17, 22)
        window.navigationBarColor = Color.rgb(14, 17, 22)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        probeView = TouchProbeView(this)
        setContentView(probeView)
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }
}

private class TouchProbeView(context: android.content.Context) : View(context) {
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(14, 17, 22)
    }
    private val targetFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(82, 255, 193, 107)
        style = Paint.Style.FILL
    }
    private val targetStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 193, 107)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(244, 247, 251)
        typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(116, 167, 255)
        typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD)
    }
    private val targetRect = RectF()
    private val events = ArrayDeque<String>()
    private var count = 0
    private var downInside = false
    private var lastDownX = 0f
    private var lastDownY = 0f

    init {
        isClickable = true
        contentDescription = "输入映射测试触摸区域"
        setBackgroundColor(Color.rgb(14, 17, 22))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        updateTargetRect()
        canvas.drawRoundRect(targetRect, 22f, 22f, targetFillPaint)
        canvas.drawRoundRect(targetRect, 22f, 22f, targetStrokePaint)

        titlePaint.textSize = dp(26f)
        canvas.drawText("输入映射测试", dp(48f), dp(70f), titlePaint)
        textPaint.textSize = dp(17f)
        canvas.drawText("只允许使用本测试应用，不要连接真实游戏", dp(48f), dp(108f), textPaint)
        canvas.drawText("目标区域点击次数：$count", dp(48f), dp(158f), textPaint)

        textPaint.textSize = dp(14f)
        canvas.drawText(
            "目标坐标比例：${TARGET_CENTER_X_RATIO.formatRatio()}，${TARGET_CENTER_Y_RATIO.formatRatio()}",
            dp(48f),
            dp(194f),
            textPaint,
        )
        textPaint.textSize = dp(18f)
        canvas.drawText("目标区", targetRect.centerX() - dp(34f), targetRect.centerY() + dp(6f), textPaint)

        textPaint.textSize = dp(13f)
        canvas.drawText("最近事件", dp(48f), height - dp(178f), titlePaint)
        var y = height - dp(148f)
        events.forEach { event ->
            canvas.drawText(event, dp(48f), y, textPaint)
            y += dp(25f)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val inside = targetRect.contains(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downInside = inside
                lastDownX = event.x
                lastDownY = event.y
                addEvent("ACTION_DOWN x=${event.x.roundToInt()} y=${event.y.roundToInt()}")
                return true
            }

            MotionEvent.ACTION_UP -> {
                addEvent("ACTION_UP   x=${event.x.roundToInt()} y=${event.y.roundToInt()}")
                if (downInside && inside) {
                    count++
                    addEvent("TARGET +1  down=${lastDownX.roundToInt()},${lastDownY.roundToInt()}")
                }
                downInside = false
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                addEvent("ACTION_CANCEL")
                downInside = false
                invalidate()
                return true
            }
        }
        return true
    }

    private fun updateTargetRect() {
        val targetWidth = width * TARGET_WIDTH_RATIO
        val targetHeight = height * TARGET_HEIGHT_RATIO
        val centerX = width * TARGET_CENTER_X_RATIO
        val centerY = height * TARGET_CENTER_Y_RATIO
        targetRect.set(
            centerX - targetWidth / 2f,
            centerY - targetHeight / 2f,
            centerX + targetWidth / 2f,
            centerY + targetHeight / 2f,
        )
    }

    private fun addEvent(value: String) {
        val time = SystemClock.uptimeMillis() % 100000
        val line = String.format(Locale.US, "%05d  %s", time, value)
        events.addLast(line)
        Log.i("InputTestApp", line)
        while (events.size > 6) events.removeFirst()
        contentDescription = "目标区域点击次数：$count；$line"
        invalidate()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun Float.formatRatio(): String = String.format(Locale.US, "%.2f", this)

    companion object {
        // The high-risk editor uses this same fixed test target by default.
        private const val TARGET_CENTER_X_RATIO = 0.76f
        private const val TARGET_CENTER_Y_RATIO = 0.76f
        private const val TARGET_WIDTH_RATIO = 0.12f
        private const val TARGET_HEIGHT_RATIO = 0.16f
    }
}
