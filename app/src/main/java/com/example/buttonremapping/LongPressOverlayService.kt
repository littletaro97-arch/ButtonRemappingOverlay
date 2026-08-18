package com.example.buttonremapping

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Point
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import com.example.buttonremapping.highrisk.HighRiskManager
import com.example.buttonremapping.highrisk.InputAccessibilityService
import com.example.buttonremapping.highrisk.ShizukuInputClient
import com.example.buttonremapping.highrisk.ShizukuState
import com.example.buttonremapping.profile.ProfileManager
import com.example.buttonremapping.profile.ProfileMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * 长按触发模式运行态服务。
 *
 * 在配置区域创建 Overlay 窗口（半透明或全透明），吞掉玩家触摸：
 * - 按下：开始计时，区域中心显示进度圆环。
 * - 计时满（longPressMs）：注入一次 tap 到区域中心（复用无障碍/Shizuku），进度环消失。
 * - 提前松开：取消，进度环消失，不注入。
 */
class LongPressOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val injectionExecutor = Executors.newSingleThreadExecutor()
    private var areaView: LongPressTouchAreaView? = null
    private var areaParams: WindowManager.LayoutParams? = null
    private var areaAttached = false
    private var areaRect = Rect()
    private var areaGesturePoint: Point? = null
    private var shizukuClient: ShizukuInputClient? = null
    private var active = false
    private var triggerPolling = false
    private var lastForegroundPackage: String? = null

    private val triggerPollRunnable = object : Runnable {
        override fun run() {
            pollForegroundApp()
            if (triggerPolling) mainHandler.postDelayed(this, TRIGGER_POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        instance = this
        RuntimeProtection.recordEvent(this, "Long-press overlay service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        userStopRequested = false
        RuntimeProtection.recordEvent(this, "Long-press overlay service start")
        val overlayGranted = runCatching { Settings.canDrawOverlays(this) }
            .onFailure { RuntimeProtection.recordFailure(this, "Overlay permission check exception", it) }
            .getOrDefault(false)
        if (!overlayGranted) {
            RuntimeProtection.recordEvent(this, "Long-press overlay permission check failed")
            stopSelf()
            return START_NOT_STICKY
        }
        return try {
            startAsForeground()
            showArea()
            ensureShizukuBound()
            startTriggerPolling()
            if (isRunning) START_STICKY else START_NOT_STICKY
        } catch (error: Exception) {
            RuntimeProtection.recordFailure(this, "长按触发服务启动失败，将安全退出", error)
            removeAreaView()
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        RuntimeProtection.recordEvent(this, "Long-press overlay configuration changed")
        if (isRunning) showArea()
    }

    override fun onDestroy() {
        val reason = if (userStopRequested) "user stop" else "system/error"
        stopTriggerPolling()
        removeAreaView()
        shizukuClient?.close()
        shizukuClient = null
        injectionExecutor.shutdownNow()
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForeground = false
        isRunning = false
        active = false
        userStopRequested = false
        if (instance === this) instance = null
        RuntimeProtection.recordEvent(this, "Long-press service destroyed: $reason")
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        RuntimeProtection.recordEvent(this, "Long-press service task removed")
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showArea() {
        removeAreaView()
        try {
            val geometry = OverlayGeometry.fromWindowManager(this)
            val config = LongPressPrefs.load(this)
            areaRect = OverlayGeometry.toPixelRect(
                config.area,
                geometry,
                config.coordinateRotation,
            )
            val areaCenter = Point(areaRect.centerX(), areaRect.centerY())
            areaGesturePoint = OverlayGeometry.toAccessibilityPoint(areaCenter, geometry)
            val view = LongPressTouchAreaView(
                context = this,
                onPressStart = { onPressStart() },
                onPressCancel = { reason -> onPressCancel(reason) },
            ).apply {
                alpha = config.areaAlpha
                setCornerRadiusRatio(config.cornerRadius)
            }
            val params = createWindowParams(areaRect)
            windowManager.addView(view, params)
            areaView = view
            areaParams = params
            areaAttached = true
            isRunning = true
            active = true
            RuntimeProtection.recordEvent(
                this,
                "长按触发窗口几何",
                "screen=${geometry.width}x${geometry.height}; rotation=${geometry.rotation}; area=$areaRect; gesture=${areaGesturePoint?.x},${areaGesturePoint?.y}; alpha=${config.areaAlpha}",
            )
            RuntimeProtection.recordEvent(this, "WindowManager addView: long-press area success")
        } catch (error: Exception) {
            RuntimeProtection.recordFailure(this, "长按触发窗口创建失败，服务将安全退出", error)
            removeAreaView()
            stopSelf()
        }
    }

    private fun onPressStart() {
        val view = areaView ?: return
        val config = LongPressPrefs.load(this)
        view.beginProgress(config.longPressMs)
        view.onProgressComplete = {
            RuntimeProtection.recordEvent(this, "长按时间已满，注入一次 Tap")
            val windowPoint = Point(areaRect.centerX(), areaRect.centerY())
            val gesturePoint = areaGesturePoint ?: windowPoint
            // 进度完成时仍在主线程，先暂时关闭区域窗口触摸，再把注入放到
            // 单独线程，避免等待无障碍回调时阻塞主线程。
            if (!hideAreaView()) {
                RuntimeProtection.recordEvent(this, "长按注入阻止：暂时关闭自身屏蔽触摸失败")
                view.cancelProgress()
            } else {
                try {
                    injectionExecutor.execute {
                        val accepted = injectSingleTap(windowPoint, gesturePoint)
                        RuntimeProtection.recordEvent(this, "长按单次 Tap 结果", "accepted=$accepted")
                        mainHandler.post { restoreAreaView() }
                    }
                } catch (error: RuntimeException) {
                    RuntimeProtection.recordFailure(this, "长按注入任务提交失败", error)
                    restoreAreaView()
                }
            }
        }
    }

    private fun onPressCancel(reason: String) {
        RuntimeProtection.recordEvent(this, "长按资格取消", reason)
        areaView?.cancelProgress()
    }

    /** Temporarily pass the area through without rebuilding its input window. */
    private fun hideAreaView(): Boolean {
        if (Looper.myLooper() !== Looper.getMainLooper()) return false
        val view = areaView ?: return true
        if (!areaAttached) return true
        val params = areaParams ?: return false
        return try {
            if ((params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                windowManager.updateViewLayout(view, params)
                RuntimeProtection.recordEvent(this, "长按注入前暂时关闭屏蔽触摸")
            }
            true
        } catch (error: RuntimeException) {
            RuntimeProtection.recordFailure(this, "长按注入前暂时关闭屏蔽触摸失败", error)
            false
        }
    }

    private fun restoreAreaView() {
        if (Looper.myLooper() !== Looper.getMainLooper()) {
            mainHandler.post { restoreAreaView() }
            return
        }
        val view = areaView ?: return
        if (!active) return
        val params = areaParams ?: return
        try {
            if (!areaAttached) {
                windowManager.addView(view, params)
                areaAttached = true
                RuntimeProtection.recordEvent(this, "长按注入完成后已恢复屏蔽窗口")
            }
            if ((params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                windowManager.updateViewLayout(view, params)
                RuntimeProtection.recordEvent(this, "长按注入完成后已恢复屏蔽窗口触摸")
            }
        } catch (error: RuntimeException) {
            RuntimeProtection.recordFailure(this, "长按注入后恢复屏蔽窗口失败", error)
        }
    }

    private fun injectSingleTap(windowPoint: Point, gesturePoint: Point): Boolean {
        // 优先 Shizuku（需已连接）；未连接或失败时回退无障碍。
        val client = shizukuClient
        if (client != null && client.isRemoteReady()) {
            waitForInputWindowUpdate()
            return client.injectTap(windowPoint.x, windowPoint.y, 0)
        }
        if (InputAccessibilityService.isConnected) {
            waitForInputWindowUpdate()
            val result = AtomicBoolean(false)
            val completed = CountDownLatch(1)
            val dispatched = InputAccessibilityService.injectTap(
                gesturePoint.x.toFloat(),
                gesturePoint.y.toFloat(),
                onResult = { result.set(it) },
                onFinished = { completed.countDown() },
            )
            if (!dispatched) return false
            val finished = try {
                completed.await(ACCESSIBILITY_GESTURE_SETTLE_MS, TimeUnit.MILLISECONDS)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
            if (!finished) {
                RuntimeProtection.recordEvent(this, "无障碍 tap 回调超时，按已派发处理")
            }
            return if (finished) result.get() else true
        }
        return false
    }

    /** See HighRiskOverlayService.waitForInputWindowUpdate(). */
    private fun waitForInputWindowUpdate() {
        SystemClock.sleep(INPUT_WINDOW_SETTLE_MS)
    }

    /** 服务启动时预绑定 Shizuku，避免首次注入时异步绑定未完成导致失败。 */
    private fun ensureShizukuBound() {
        if (shizukuClient != null) return
        if (HighRiskManager.getShizukuStatus(this).state != ShizukuState.READY) return
        shizukuClient = ShizukuInputClient(
            context = this,
            onConnected = { /* 保持连接，注入时直接使用 */ },
            onDisconnected = {},
        ).apply {
            bind()
        }
        RuntimeProtection.recordEvent(this, "长按触发预绑定 Shizuku UserService")
    }

    private fun startTriggerPolling() {
        if (triggerPolling) return
        val config = LongPressPrefs.load(this)
        if (!config.triggerEnabled || config.triggerPackages.isEmpty()) return
        triggerPolling = true
        lastForegroundPackage = null
        RuntimeProtection.recordEvent(this, "指定应用启动：开始监控前台应用", "apps=${config.triggerPackages.size}")
        mainHandler.post(triggerPollRunnable)
    }

    private fun stopTriggerPolling() {
        triggerPolling = false
        lastForegroundPackage = null
        mainHandler.removeCallbacks(triggerPollRunnable)
    }

    private fun pollForegroundApp() {
        if (!triggerPolling) return
        val config = LongPressPrefs.load(this)
        if (!config.triggerEnabled || config.triggerPackages.isEmpty()) {
            stopTriggerPolling()
            return
        }
        val foreground = AppTrigger.currentForegroundPackage(this)
        if (foreground == lastForegroundPackage) return
        lastForegroundPackage = foreground
        val shouldRun = foreground != null && config.triggerPackages.contains(foreground)
        if (shouldRun && !active) {
            RuntimeProtection.recordEvent(this, "指定应用启动：进入目标应用，显示长按区域", foreground)
            showArea()
        } else if (!shouldRun && active) {
            RuntimeProtection.recordEvent(this, "指定应用启动：离开目标应用，隐藏长按区域", foreground ?: "未知")
            removeAreaView()
        }
    }

    fun restartTriggerIfNeeded() {
        if (!isRunning) return
        stopTriggerPolling()
        startTriggerPolling()
    }

    private fun removeAreaView() {
        areaView?.let { view ->
            if (areaAttached) {
                try {
                    windowManager.removeViewImmediate(view)
                    RuntimeProtection.recordEvent(this, "WindowManager removeView: long-press area")
                } catch (_: RuntimeException) {
                }
            }
        }
        areaView = null
        areaParams = null
        areaAttached = false
        areaGesturePoint = null
        isRunning = false
        active = false
    }

    private fun createWindowParams(rect: Rect): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            rect.width(),
            rect.height(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SPLIT_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = rect.left
            y = rect.top
            title = "长按触发区域"
        }

    private fun startAsForeground() {
        val profileName = ProfileManager.currentName(this, ProfileMode.LONG)
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("游戏按钮映射 · $profileName")
            .setContentText("长按触发模式正在运行")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isForeground = true
        RuntimeProtection.recordEvent(this, "Long-press foreground service started")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "长按触发模式",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "长按触发模式：长按满设定时间后注入一次点击"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "LongPressOverlay"
        private const val CHANNEL_ID = "long_press_mode"
        private const val NOTIFICATION_ID = 3001
        private const val TRIGGER_POLL_INTERVAL_MS = 1_500L
        private const val ACCESSIBILITY_GESTURE_SETTLE_MS = 90L
        private const val INPUT_WINDOW_SETTLE_MS = 64L

        @Volatile
        var isRunning: Boolean = false

        @Volatile
        var isForeground: Boolean = false

        @Volatile
        private var userStopRequested: Boolean = false

        @Volatile
        private var instance: LongPressOverlayService? = null

        fun markUserStop() {
            userStopRequested = true
        }

        @JvmStatic
        fun serviceRestartTrigger() {
            instance?.restartTriggerIfNeeded()
        }
    }
}

/**
 * 长按触发区域视图：消费触摸，按下时以区域外部轮廓作为进度条。
 */
@android.annotation.SuppressLint("ViewConstructor")
class LongPressTouchAreaView(
    context: Context,
    private val onPressStart: () -> Unit,
    private val onPressCancel: (String) -> Unit,
) : android.view.View(context) {
    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(116, 167, 255)
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = density * 3f
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    private val outlineBgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 255, 255, 255)
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = density * 3f
    }
    private var cornerRadiusRatio = 0.5f
    private var pressed = false
    private var progress = 0f
    private var longPressMs = 500
    private var startUptime = 0L
    private var downRawX = 0f
    private var downRawY = 0f
    private var gestureEligible = false

    var onProgressComplete: (() -> Unit)? = null

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (!pressed) return
            val elapsed = SystemClock.uptimeMillis() - startUptime
            progress = (elapsed.toFloat() / longPressMs.coerceAtLeast(1)).coerceIn(0f, 1f)
            invalidate()
            if (progress >= 1f) {
                pressed = false
                gestureEligible = false
                onProgressComplete?.invoke()
            } else {
                postDelayed(this, 16L)
            }
        }
    }

    init {
        isClickable = true
        isFocusable = false
        setBackgroundColor(Color.TRANSPARENT)
    }

    fun setCornerRadiusRatio(value: Float) {
        cornerRadiusRatio = value.coerceIn(0f, 0.5f)
        invalidate()
    }

    fun beginProgress(ms: Int) {
        longPressMs = ms.coerceIn(300, 3000)
        progress = 0f
        startUptime = SystemClock.uptimeMillis()
        pressed = true
        removeCallbacks(progressRunnable)
        post(progressRunnable)
        invalidate()
    }

    fun cancelProgress() {
        pressed = false
        progress = 0f
        removeCallbacks(progressRunnable)
        invalidate()
    }

    private val drawRect = android.graphics.RectF()
    private val outlinePath = android.graphics.Path()
    private val progressPath = android.graphics.Path()
    private val pathMeasure = android.graphics.PathMeasure()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                gestureEligible = true
                onPressStart()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (gestureEligible && LongPressGesturePolicy.shouldCancel(
                        downRawX = downRawX,
                        downRawY = downRawY,
                        currentRawX = event.rawX,
                        currentRawY = event.rawY,
                        localX = event.x,
                        localY = event.y,
                        width = width,
                        height = height,
                        touchSlop = touchSlop,
                        pointerCount = event.pointerCount,
                    )
                ) {
                    gestureEligible = false
                    onPressCancel("移动超出系统容差或触点离开区域")
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (gestureEligible) {
                    gestureEligible = false
                    onPressCancel("检测到多指触摸")
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (gestureEligible && pressed) onPressCancel("未达到阈值即抬起")
                if (gestureEligible) performClick()
                gestureEligible = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (gestureEligible || pressed) onPressCancel("系统取消本次触摸")
                gestureEligible = false
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        // 窗口整个布局矩形都接收触摸，边框只内缩半个线宽，保持视觉
        // 范围与实际 WindowManager 输入矩形一致。
        val inset = outlinePaint.strokeWidth / 2f
        val radius = minOf(width, height) * cornerRadiusRatio
        drawRect.set(inset, inset, width - inset, height - inset)

        // 区域底框（编辑透明度由外部 alpha 控制）。
        fillPaint.style = android.graphics.Paint.Style.STROKE
        fillPaint.color = Color.argb(150, 255, 170, 72)
        fillPaint.strokeWidth = density * 1.5f
        canvas.drawRoundRect(drawRect, radius, radius, fillPaint)

        if (!pressed) return
        // 外部轮廓作为进度条：先画白色底轮廓，再按进度画蓝色轮廓。
        canvas.drawRoundRect(drawRect, radius, radius, outlineBgPaint)
        outlinePath.reset()
        outlinePath.addRoundRect(drawRect, radius, radius, android.graphics.Path.Direction.CW)
        pathMeasure.setPath(outlinePath, false)
        val totalLength = pathMeasure.length
        if (totalLength > 0f) {
            progressPath.reset()
            pathMeasure.getSegment(0f, totalLength * progress.coerceIn(0f, 1f), progressPath, true)
            canvas.drawPath(progressPath, outlinePaint)
        }
    }
}
