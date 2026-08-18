package com.example.buttonremapping

/** Pure policies kept outside Android views so their edge cases can be unit tested. */
object TargetAreaPolicy {
    fun center(start: Int, end: Int): Int = start + (end - start) / 2
}

object LongPressGesturePolicy {
    fun shouldCancel(
        downRawX: Float,
        downRawY: Float,
        currentRawX: Float,
        currentRawY: Float,
        localX: Float,
        localY: Float,
        width: Int,
        height: Int,
        touchSlop: Int,
        pointerCount: Int,
    ): Boolean {
        if (pointerCount != 1) return true
        if (localX < 0f || localY < 0f || localX >= width || localY >= height) return true
        val deltaX = currentRawX - downRawX
        val deltaY = currentRawY - downRawY
        val slop = touchSlop.coerceAtLeast(0).toFloat()
        return deltaX * deltaX + deltaY * deltaY > slop * slop
    }
}
