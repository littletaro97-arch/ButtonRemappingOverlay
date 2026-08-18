package com.example.buttonremapping

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View

/** Displays the selected device screenshot across the full editor canvas. */
@SuppressLint("ViewConstructor")
class ScreenshotCanvasView(
    context: Context,
    private val bitmap: Bitmap,
    private val sourceWidth: Int,
    private val sourceHeight: Int,
) : View(context) {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val imageBounds = Rect()

    init {
        contentDescription = "游戏截图编辑画布"
        setBackgroundColor(Color.rgb(7, 9, 12))
    }

    fun contentRect(): Rect = Rect(imageBounds)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) return
        imageBounds.set(0, 0, width, height)
        canvas.drawBitmap(bitmap, null, imageBounds, bitmapPaint)
    }
}
