package com.example.buttonremapping.highrisk

import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent

class InputUserService : IInputUserService.Stub() {
    @Suppress("UNUSED_PARAMETER")
    override fun injectTap(x: Int, y: Int, displayId: Int): Boolean =
        SystemInputTap.inject(x, y)

    override fun destroy() {
        System.exit(0)
    }

    override fun exit() {
        destroy()
    }
}

private object SystemInputTap {
    private const val TAG = "SingleMappingInput"
    private const val INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH = 2

    fun inject(x: Int, y: Int): Boolean {
        val inputManagerClass = Class.forName("android.hardware.input.InputManager")
        val getInstance = inputManagerClass.getDeclaredMethod("getInstance")
        val inputManager = getInstance.invoke(null)
        val injectInputEvent = inputManagerClass.getDeclaredMethod(
            "injectInputEvent",
            Class.forName("android.view.InputEvent"),
            Int::class.javaPrimitiveType,
        ).apply { isAccessible = true }

        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            x.toFloat(),
            y.toFloat(),
            0,
        )
        val upTime = SystemClock.uptimeMillis().coerceAtLeast(downTime + 1L)
        val up = MotionEvent.obtain(
            downTime,
            upTime,
            MotionEvent.ACTION_UP,
            x.toFloat(),
            y.toFloat(),
            0,
        )
        try {
            configureTouchEvent(down)
            configureTouchEvent(up)
            val downAccepted = injectInputEvent.invoke(
                inputManager,
                down,
                INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH,
            ) as Boolean
            val upAccepted = injectInputEvent.invoke(
                inputManager,
                up,
                INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH,
            ) as Boolean
            return downAccepted && upAccepted
        } catch (error: Throwable) {
            Log.e(TAG, "inject tap failed at $x,$y", error)
            return false
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    private fun configureTouchEvent(event: MotionEvent) {
        event.source = InputDevice.SOURCE_TOUCHSCREEN
    }
}
