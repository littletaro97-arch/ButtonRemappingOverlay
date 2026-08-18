package com.example.buttonremapping

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class TouchPoliciesTest {
    @Test
    fun targetCenterUsesTheUserRectangleEvenAtDisplayEdge() {
        assertEquals(50, TargetAreaPolicy.center(0, 100))
        assertEquals(950, TargetAreaPolicy.center(900, 1000))
        assertEquals(10, TargetAreaPolicy.center(10, 11))
    }

    @Test
    fun stationarySinglePointerRemainsEligible() {
        assertFalse(
            LongPressGesturePolicy.shouldCancel(
                downRawX = 100f,
                downRawY = 100f,
                currentRawX = 105f,
                currentRawY = 95f,
                localX = 30f,
                localY = 20f,
                width = 80,
                height = 60,
                touchSlop = 8,
                pointerCount = 1,
            ),
        )
    }

    @Test
    fun movingBeyondTouchSlopCancels() {
        assertTrue(
            LongPressGesturePolicy.shouldCancel(
                downRawX = 100f,
                downRawY = 100f,
                currentRawX = 109f,
                currentRawY = 100f,
                localX = 39f,
                localY = 20f,
                width = 80,
                height = 60,
                touchSlop = 8,
                pointerCount = 1,
            ),
        )
    }

    @Test
    fun leavingAreaOrAddingPointerCancels() {
        assertTrue(
            LongPressGesturePolicy.shouldCancel(
                100f, 100f, 101f, 101f,
                -1f, 20f, 80, 60, 8, 1,
            ),
        )
        assertTrue(
            LongPressGesturePolicy.shouldCancel(
                100f, 100f, 101f, 101f,
                20f, 20f, 80, 60, 8, 2,
            ),
        )
    }
}
