package com.example.buttonremapping.highrisk

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View

@SuppressLint("ViewConstructor")
class MappingCanvasView(
    context: Context,
    private val bitmap: Bitmap?,
    private val sourceWidth: Int,
    private val sourceHeight: Int,
) : View(context) {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(35, 45, 58)
        strokeWidth = resources.displayMetrics.density
    }
    private val imageBounds = Rect()

    init {
        contentDescription = "单按钮映射编辑画布"
        setBackgroundColor(Color.rgb(7, 9, 12))
    }

    fun contentRect(): Rect = Rect(imageBounds)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        imageBounds.set(0, 0, width, height)
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, imageBounds, bitmapPaint)
        } else {
            canvas.drawColor(Color.rgb(12, 16, 22))
            val step = (width / 12f).coerceAtLeast(80f)
            var x = 0f
            while (x <= width) {
                canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
                x += step
            }
            var y = 0f
            val verticalStep = (height / 6f).coerceAtLeast(80f)
            while (y <= height) {
                canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
                y += verticalStep
            }
        }
    }
}
