package com.example.buttonremapping.highrisk

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.example.buttonremapping.RuntimeProtection
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 无障碍注入服务：仅用于按需注入单点 tap（dispatchGesture）。
 *
 * 不监听用户触摸、不记录事件、不转发事件。注入手势与用户真实触摸是
 * 并行通道，不影响其他操作与多点触控。
 */
class InputAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        RuntimeProtection.recordEvent(this, "无障碍注入服务已连接")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        RuntimeProtection.recordEvent(this, "无障碍注入服务已断开")
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /**
     * 注入一次短按 tap。返回 true 表示手势已派发（异步），
     * 最终结果通过 [onResult] 回调，[onFinished] 在完成或取消时调用。
     */
    fun dispatchTap(
        x: Float,
        y: Float,
        onResult: ((Boolean) -> Unit)? = null,
        onFinished: (() -> Unit)? = null,
    ): Boolean = try {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()
        val callbackDelivered = AtomicBoolean(false)
        fun finish(result: Boolean, event: String) {
            if (!callbackDelivered.compareAndSet(false, true)) return
            RuntimeProtection.recordEvent(this, event)
            onResult?.invoke(result)
            onFinished?.invoke()
        }
        fun dispatchNow(): Boolean {
            val dispatched = try {
                dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        finish(true, "无障碍 tap 注入完成")
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        finish(false, "无障碍 tap 注入被取消")
                    }
                }, handler)
            } catch (error: Throwable) {
                RuntimeProtection.recordEvent(
                    this,
                    "无障碍 tap 注入异常",
                    error.javaClass.simpleName,
                )
                false
            }
            if (!dispatched) finish(false, "无障碍 tap 未被系统派发")
            return dispatched
        }

        if (Looper.myLooper() === Looper.getMainLooper()) {
            dispatchNow()
        } else {
            val submitted = CountDownLatch(1)
            val accepted = AtomicBoolean(false)
            if (!handler.post {
                    accepted.set(dispatchNow())
                    submitted.countDown()
                }) {
                finish(false, "无障碍 tap 无法提交到服务线程")
                false
            } else {
                val completed = try {
                    submitted.await(MAIN_THREAD_DISPATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    false
                }
                if (!completed) {
                    finish(false, "无障碍 tap 提交服务线程超时")
                    false
                } else {
                    accepted.get()
                }
            }
        }
    } catch (error: Throwable) {
        RuntimeProtection.recordEvent(
            this,
            "无障碍 tap 注入失败",
            error.javaClass.simpleName,
        )
        onResult?.invoke(false)
        onFinished?.invoke()
        false
    }

    companion object {
        private const val TAP_DURATION_MS = 50L
        private const val MAIN_THREAD_DISPATCH_TIMEOUT_MS = 1_000L

        @Volatile
        private var instance: InputAccessibilityService? = null

        /** 服务是否已连接（无障碍已开启且本服务被启用）。 */
        val isConnected: Boolean
            get() = instance != null

        /** 无障碍服务是否已开启（通过系统设置检查）。 */
        fun isServiceEnabled(context: android.content.Context): Boolean {
            val expected = context.packageName + "/" + InputAccessibilityService::class.java.name
            val enabledServices = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
        }

        fun openAccessibilitySettings(context: android.content.Context) {
            try {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS),
                )
            } catch (_: Exception) {
                RuntimeProtection.recordEvent(context, "打开无障碍设置失败")
            }
        }

        /** 注入一次 tap；服务未连接时返回 false。[onFinished] 在手势完成/取消时调用。 */
        fun injectTap(
            x: Float,
            y: Float,
            onResult: ((Boolean) -> Unit)? = null,
            onFinished: (() -> Unit)? = null,
        ): Boolean {
            val service = instance ?: return false
            return service.dispatchTap(x, y, onResult = onResult, onFinished = onFinished)
        }
    }
}
