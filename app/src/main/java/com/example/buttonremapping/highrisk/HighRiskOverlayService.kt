package com.example.buttonremapping.highrisk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Point
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.example.buttonremapping.AppTrigger
import com.example.buttonremapping.BlockOverlayView
import com.example.buttonremapping.MainActivity
import com.example.buttonremapping.OverlayGeometry
import com.example.buttonremapping.RuntimeProtection
import com.example.buttonremapping.profile.ProfileManager
import com.example.buttonremapping.profile.ProfileMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class HighRiskOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val injectionExecutor = Executors.newSingleThreadExecutor()
    private var virtualView: MappingVirtualButtonView? = null
    private var virtualAttached = false
    private var targetBlockView: BlockOverlayView? = null
    private var targetBlockParams: WindowManager.LayoutParams? = null
    private var targetBlockAttached = false
    private var targetBlockRect: android.graphics.Rect? = null
    private var inputClient: ShizukuInputClient? = null
    private var targetPoint: Point? = null
    private var targetGesturePoint: Point? = null
    private var active = false
    private var receiverRegistered = false
    private val triggerHandler = Handler(Looper.getMainLooper())
    private var lastForegroundPackage: String? = null
    private var triggerPolling = false

    private val triggerPollRunnable = object : Runnable {
        override fun run() {
            pollForegroundApp()
            if (triggerPolling) triggerHandler.postDelayed(this, TRIGGER_POLL_INTERVAL_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                failAndStop("屏幕关闭，映射已停止")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        registerScreenReceiver()
        instance = this
        RuntimeProtection.recordEvent(this, "High-risk overlay service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        userStopRequested = false
        RuntimeProtection.recordEvent(this, "High-risk overlay service start")
        if (active) return START_NOT_STICKY
        val overlayGranted = runCatching { Settings.canDrawOverlays(this) }
            .onFailure { RuntimeProtection.recordFailure(this, "High-risk overlay permission check exception", it) }
            .getOrDefault(false)
        if (!overlayGranted) {
            RuntimeProtection.recordEvent(this, "Overlay permission check failed for high-risk service")
            failAndStop("未获得悬浮窗权限")
            return START_NOT_STICKY
        }
        try {
            startAsForeground()
            isRunning = true
            val (enabled, packages) = currentTriggerConfig()
            if (enabled && packages.isNotEmpty()) {
                // 指定应用启动：服务常驻，由前台应用决定是否启动映射。
                active = false
                startTriggerPolling()
            } else {
                active = true
                startMapping()
            }
        } catch (error: Exception) {
            RuntimeProtection.recordFailure(this, "高风险悬浮服务启动失败", error)
            failAndStop("高风险悬浮服务启动失败：${error.javaClass.simpleName}")
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        RuntimeProtection.recordEvent(this, "High-risk overlay configuration changed")
        if (!isRunning) return
        // 横竖屏切换后重新计算坐标并重建虚拟按钮，而不是退出映射。
        rebuildMappingAfterRotation()
    }

    /**
     * 屏幕方向/尺寸变化后，按当前几何重新计算目标区域与虚拟按钮位置。
     */
    private fun rebuildMappingAfterRotation() {
        val config = MappingPrefs.load(this)
        if (!config.configured) {
            failAndStop("配置未保存，映射已停止")
            return
        }
        val geometry = OverlayGeometry.fromWindowManager(this)
        val blockRect = OverlayGeometry.toPixelRect(
            config.targetBlock,
            geometry,
            config.coordinateRotation,
        )
        targetBlockRect = blockRect
        val point = Point(
            com.example.buttonremapping.TargetAreaPolicy.center(blockRect.left, blockRect.right),
            com.example.buttonremapping.TargetAreaPolicy.center(blockRect.top, blockRect.bottom),
        )
        targetPoint = point
        targetGesturePoint = OverlayGeometry.toAccessibilityPoint(point, geometry)
        // 虚拟按钮窗口需要按新尺寸重新布局：移除旧窗口，重建。
        removeVirtualView()
        if (active) {
            mainHandler.post { addVirtualView(config, geometry) }
        }
    }

    override fun onDestroy() {
        val reason = if (userStopRequested) "user stop" else "system/error"
        stopTriggerPolling()
        active = false
        isRunning = false
        if (instance === this) instance = null
        removeVirtualView()
        inputClient?.close()
        inputClient = null
        injectionExecutor.shutdownNow()
        if (receiverRegistered) {
            unregisterReceiver(screenReceiver)
            receiverRegistered = false
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForeground = false
        userStopRequested = false
        RuntimeProtection.recordEvent(this, "High-risk service destroyed: $reason")
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        RuntimeProtection.recordEvent(this, "High-risk service task removed")
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMapping() {
        val config = MappingPrefs.load(this)
        if (!config.configured) {
            failAndStop("请先保存目标位置和新按钮位置")
            return
        }
        val geometry = OverlayGeometry.fromWindowManager(this)
        if (!sameScreenSize(geometry.width, geometry.height, config.screenshotWidth, config.screenshotHeight)) {
            failAndStop("屏幕尺寸已变化，请重新编辑布局")
            return
        }
        val blockRect = OverlayGeometry.toPixelRect(
            config.targetBlock,
            geometry,
            config.coordinateRotation,
        )
        targetBlockRect = blockRect
        val point = Point(
            com.example.buttonremapping.TargetAreaPolicy.center(blockRect.left, blockRect.right),
            com.example.buttonremapping.TargetAreaPolicy.center(blockRect.top, blockRect.bottom),
        )
        targetPoint = point
        targetGesturePoint = OverlayGeometry.toAccessibilityPoint(point, geometry)

        // 注入后端：Shizuku READY 优先，否则无障碍。
        val shizukuReady = HighRiskManager.getShizukuStatus(this).state == ShizukuState.READY
        if (!shizukuReady && !InputAccessibilityService.isConnected) {
            failAndStop("无可用注入后端（Shizuku 未就绪且无障碍未开启）")
            return
        }
        if (shizukuReady) {
            val client = ShizukuInputClient(
                context = this,
                onConnected = { mainHandler.post { addVirtualView(config, geometry) } },
                onDisconnected = { reason -> mainHandler.post { failAndStop(reason) } },
            )
            inputClient = client
            if (!client.bind()) {
                failAndStop("Shizuku UserService 启动失败")
            }
        } else {
            // 无障碍模式：不需要绑定 Shizuku，直接显示虚拟按钮。
            RuntimeProtection.recordEvent(this, "使用无障碍注入后端")
            mainHandler.post { addVirtualView(config, geometry) }
        }
    }

    private fun addVirtualView(
        config: MappingConfig,
        geometry: com.example.buttonremapping.DisplayGeometry,
    ) {
        if (!active || virtualView != null) return
        // 先添加目标拦截窗口（下层），再添加虚拟按钮（上层）。
        // WindowManager 中后添加的窗口在上层；若反过来，拦截窗口会盖住虚拟按钮使其无法点击。
        addTargetBlockOverlay(geometry)
        val rect = OverlayGeometry.toPixelRect(
            config.virtualButton,
            geometry,
            config.coordinateRotation,
        )
        val view = MappingVirtualButtonView(this) { requestTargetTap() }.apply {
            alpha = config.virtualButtonAlpha
            setCornerRadiusRatio(config.virtualButtonCornerRadius)
        }
        try {
            RuntimeProtection.recordEvent(this, "WindowManager addView: high-risk virtual button")
            val params = createWindowParams(rect)
            windowManager.addView(view, params)
            virtualView = view
            virtualAttached = true
            RuntimeProtection.recordEvent(
                this,
                "高风险映射触摸窗口几何",
                "screen=${geometry.width}x${geometry.height}; rotation=${geometry.rotation}; virtual=$rect; targetRect=$targetBlockRect; targetCenter=${targetPoint?.x},${targetPoint?.y}; gesture=${targetGesturePoint?.x},${targetGesturePoint?.y}",
            )
            RuntimeProtection.recordEvent(this, "WindowManager addView: high-risk virtual button success")
        } catch (error: RuntimeException) {
            RuntimeProtection.recordFailure(this, "高风险虚拟按钮 Overlay 添加失败", error)
            failAndStop("虚拟按钮 Overlay 启动失败：${error.javaClass.simpleName}")
        }
    }

    /**
     * 在目标位置创建一个透明的触摸拦截窗口：玩家直接点目标位置无效，
     * 只能通过虚拟按钮触发注入，与低风险屏蔽区行为一致。
     * 窗口的位置与尺寸来自用户保存的 targetBlock（与编辑器所见一致）。
     */
    private fun addTargetBlockOverlay(
        geometry: com.example.buttonremapping.DisplayGeometry,
    ) {
        if (targetBlockView != null) return
        val rect = targetBlockRect ?: return
        val width = rect.width().coerceAtLeast(1)
        val height = rect.height().coerceAtLeast(1)
        val left = rect.left.coerceIn(0, (geometry.width - width).coerceAtLeast(0))
        val top = rect.top.coerceIn(0, (geometry.height - height).coerceAtLeast(0))
        val clamped = android.graphics.Rect(left, top, left + width, top + height)
        val view = BlockOverlayView(this).apply {
            // 不把整个 View 设为 alpha=0：部分 OEM 会对完全透明的 Overlay
            // 做合成/输入优化。1/255 的表面肉眼不可见，但仍保留明确输入层。
            alpha = 1f
            setBackgroundColor(Color.argb(1, 0, 0, 0))
        }
        try {
            RuntimeProtection.recordEvent(this, "WindowManager addView: target block")
            val params = createWindowParams(clamped).apply {
                title = "目标位置拦截"
            }
            windowManager.addView(view, params)
            targetBlockView = view
            targetBlockParams = params
            targetBlockAttached = true
            RuntimeProtection.recordEvent(this, "WindowManager addView: target block success")
        } catch (error: RuntimeException) {
            RuntimeProtection.recordFailure(this, "目标位置拦截窗口添加失败", error)
        }
    }

    private fun requestTargetTap() {
        if (!active) {
            RuntimeProtection.recordEvent(this, "虚拟按钮点击被忽略：映射未运行")
            return
        }
        val windowPoint = targetPoint ?: run {
            RuntimeProtection.recordEvent(this, "虚拟按钮点击被忽略：目标位置为空")
            return
        }
        val gesturePoint = targetGesturePoint ?: windowPoint
        RuntimeProtection.recordEvent(this, "虚拟按钮点击：请求单次 Tap")
        try {
            injectionExecutor.execute {
                if (!active) return@execute
                val accepted = injectSingleTap(windowPoint, gesturePoint)
                RuntimeProtection.recordEvent(
                    this,
                    "单次 Tap 结果",
                    "accepted=$accepted; window=${windowPoint.x},${windowPoint.y}; gesture=${gesturePoint.x},${gesturePoint.y}",
                )
                if (!accepted) {
                    mainHandler.post { failAndStop("单次点击注入失败，映射已停止") }
                }
            }
        } catch (error: RuntimeException) {
            RuntimeProtection.recordFailure(this, "单次 Tap 任务提交失败", error)
            mainHandler.post { failAndStop("单次点击任务提交失败，映射已停止") }
        }
    }

    /**
     * 统一注入入口：优先 Shizuku，其次无障碍 dispatchGesture。
     * 两者都不可用时返回 false。
     *
     * 注入前暂时关闭目标拦截窗口的触摸能力，否则注入的 tap 会被我们
     * 自己的拦截窗口消费，目标应用收不到。窗口本身和新按钮层级保持不变。
     */
    private fun injectSingleTap(windowPoint: Point, gesturePoint: Point): Boolean {
        val client = inputClient
        if (client != null && HighRiskManager.getShizukuStatus(this).state == ShizukuState.READY) {
            if (!hideTargetBlockOverlay()) return false
            waitForInputWindowUpdate()
            return try {
                client.injectTap(windowPoint.x, windowPoint.y, 0)
            } finally {
                restoreTargetBlockOverlay()
            }
        }
        if (InputAccessibilityService.isConnected) {
            if (!hideTargetBlockOverlay()) return false
            waitForInputWindowUpdate()
            val result = AtomicBoolean(false)
            val completed = CountDownLatch(1)
            val dispatched = InputAccessibilityService.injectTap(
                gesturePoint.x.toFloat(),
                gesturePoint.y.toFloat(),
                onResult = { result.set(it) },
                onFinished = { completed.countDown() },
            )
            if (!dispatched) {
                restoreTargetBlockOverlay()
                return false
            }
            val finished = try {
                completed.await(ACCESSIBILITY_GESTURE_SETTLE_MS, TimeUnit.MILLISECONDS)
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            }
            // dispatchGesture 已经被系统接受且手势只有 50ms。不要因为厂商
            // 回调晚到而让目标屏蔽窗口保持关闭，也不要让后续点击排队数秒。
            val completedResult = if (finished) result.get() else {
                RuntimeProtection.recordEvent(
                    this,
                    "无障碍 tap 回调超时，按已派发处理",
                    "gesture=${gesturePoint.x},${gesturePoint.y}",
                )
                true
            }
            restoreTargetBlockOverlay()
            return completedResult
        }
        return false
    }

    /**
     * Some OEM InputDispatcher builds publish an updated input-window list one
     * frame after LayoutParams.flags changes. Starting dispatchGesture in that
     * gap can target a stale blocker input channel and be reported as completed
     * without reaching the app.
     */
    private fun waitForInputWindowUpdate() {
        SystemClock.sleep(INPUT_WINDOW_SETTLE_MS)
    }

    /** Temporarily pass the target area through without rebuilding its window. */
    private fun hideTargetBlockOverlay(): Boolean = runOnMainAndWait {
        val view = targetBlockView ?: return@runOnMainAndWait true
        if (!targetBlockAttached) return@runOnMainAndWait true
        val params = targetBlockParams ?: return@runOnMainAndWait false
        try {
            if ((params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) == 0) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                windowManager.updateViewLayout(view, params)
                RuntimeProtection.recordEvent(this, "注入前暂时关闭目标屏蔽触摸")
            }
            true
        } catch (error: RuntimeException) {
            // 窗口可能已被系统移除：标记为未挂载，恢复时重新添加，
            // 避免目标位置永久失去拦截（按钮可直接点击）。
            targetBlockAttached = false
            RuntimeProtection.recordFailure(this, "注入前暂时关闭目标屏蔽触摸失败", error)
            false
        }
    }

    /**
     * 注入完成后恢复目标拦截窗口的触摸拦截。即使映射正在停用也恢复触摸
     * （removeVirtualView 会清空引用），防止 FLAG_NOT_TOUCHABLE 残留导致
     * 目标位置按钮失去拦截、可直接点击。
     */
    private fun restoreTargetBlockOverlay(): Boolean = runOnMainAndWait {
        val view = targetBlockView ?: return@runOnMainAndWait true
        val params = targetBlockParams ?: return@runOnMainAndWait false
        try {
            if (!targetBlockAttached) {
                windowManager.addView(view, params)
                targetBlockAttached = true
                RuntimeProtection.recordEvent(this, "注入完成后已恢复目标屏蔽窗口")
            }
            if ((params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                windowManager.updateViewLayout(view, params)
                RuntimeProtection.recordEvent(this, "注入后已恢复目标屏蔽窗口触摸")
            }
            true
        } catch (error: RuntimeException) {
            RuntimeProtection.recordFailure(this, "注入后恢复目标屏蔽窗口失败", error)
            false
        }
    }


    private fun runOnMainAndWait(action: () -> Boolean): Boolean {
        if (Looper.myLooper() === Looper.getMainLooper()) return action()
        val completed = CountDownLatch(1)
        var result = false
        mainHandler.post {
            result = try {
                action()
            } catch (error: Throwable) {
                RuntimeProtection.recordFailure(this, "主线程执行屏蔽窗口操作失败", error)
                false
            } finally {
                completed.countDown()
            }
        }
        return try {
            completed.await(MAIN_THREAD_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS) && result
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private fun failAndStop(reason: String) {
        Log.w(TAG, reason)
        RuntimeProtection.recordEvent(this, "High-risk service stop: $reason")
        if (!active && !isRunning) {
            stopSelf()
            return
        }
        active = false
        isRunning = false
        removeVirtualView()
        stopSelf()
    }

    /**
     * 指定应用启动：读取当前 Profile 的 trigger 配置。
     */
    private fun currentTriggerConfig(): Pair<Boolean, List<String>> {
        val config = MappingPrefs.load(this)
        return config.triggerEnabled to config.triggerPackages
    }

    private fun startTriggerPolling() {
        if (triggerPolling) return
        val (enabled, packages) = currentTriggerConfig()
        if (!enabled || packages.isEmpty()) return
        triggerPolling = true
        lastForegroundPackage = null
        RuntimeProtection.recordEvent(this, "指定应用启动：开始监控前台应用", "apps=${packages.size}")
        triggerHandler.post(triggerPollRunnable)
    }

    private fun stopTriggerPolling() {
        triggerPolling = false
        lastForegroundPackage = null
        triggerHandler.removeCallbacks(triggerPollRunnable)
    }

    private fun pollForegroundApp() {
        if (!triggerPolling) return
        val (enabled, packages) = currentTriggerConfig()
        if (!enabled || packages.isEmpty()) {
            stopTriggerPolling()
            return
        }
        val foreground = AppTrigger.currentForegroundPackage(this)
        if (foreground == lastForegroundPackage) return
        lastForegroundPackage = foreground
        val shouldRun = foreground != null && packages.contains(foreground)
        if (shouldRun && !active) {
            // 轮询触发的启动：布局未配置或注入后端不可用时只记录，不停止服务。
            val config = MappingPrefs.load(this)
            if (!config.configured) {
                RuntimeProtection.recordEvent(this, "指定应用启动：布局未配置，等待编辑", foreground)
                return
            }
            val shizukuReady = HighRiskManager.getShizukuStatus(this).state == ShizukuState.READY
            val accessibilityReady = InputAccessibilityService.isConnected
            if (!shizukuReady && !accessibilityReady) {
                RuntimeProtection.recordEvent(
                    this,
                    "指定应用启动：注入后端不可用，等待（Shizuku 或无障碍）",
                    foreground,
                )
                return
            }
            RuntimeProtection.recordEvent(
                this,
                "指定应用启动：进入目标应用，启动映射",
                foreground,
            )
            active = true
            startMapping()
        } else if (!shouldRun && active) {
            RuntimeProtection.recordEvent(
                this,
                "指定应用启动：离开目标应用，停止映射",
                foreground ?: "未知",
            )
            active = false
            removeVirtualView()
            inputClient?.close()
            inputClient = null
        }
    }

    /**
     * 方案切换或配置变化后，若服务正在运行且启用了指定应用启动，立即重启轮询。
     */
    fun restartTriggerIfNeeded() {
        if (!isRunning) return
        stopTriggerPolling()
        startTriggerPolling()
    }

    private fun removeVirtualView() {
        virtualView?.let { view ->
            if (virtualAttached) {
                try {
                    windowManager.removeViewImmediate(view)
                    RuntimeProtection.recordEvent(this, "WindowManager removeView: high-risk virtual button")
                } catch (_: IllegalArgumentException) {
                    // The system already removed the window.
                }
            }
        }
        virtualView = null
        virtualAttached = false
        targetBlockView?.let { view ->
            if (targetBlockAttached) {
                try {
                    windowManager.removeViewImmediate(view)
                    RuntimeProtection.recordEvent(this, "WindowManager removeView: target block")
                } catch (_: IllegalArgumentException) {
                    // The system already removed the window.
                }
            }
        }
        targetBlockAttached = false
        targetBlockParams = null
        targetBlockView = null
        targetBlockRect = null
    }

    private fun createWindowParams(rect: android.graphics.Rect): WindowManager.LayoutParams =
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
            title = "高风险新按钮"
        }

    private fun sameScreenSize(width: Int, height: Int, savedWidth: Int, savedHeight: Int): Boolean =
        savedWidth > 0 && savedHeight > 0 &&
            ((width == savedWidth && height == savedHeight) ||
                (width == savedHeight && height == savedWidth))

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun registerScreenReceiver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }
        receiverRegistered = true
    }

    private fun startAsForeground() {
        val profileName = ProfileManager.currentName(this, ProfileMode.HIGH)
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("游戏按钮映射 · $profileName")
            .setContentText("修改键位模式：单按钮映射正在运行")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    4002,
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
        RuntimeProtection.recordEvent(this, "High-risk foreground service started")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "游戏按钮映射高风险映射",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "HighRiskOverlayService"
        private const val CHANNEL_ID = "high_risk_mapping"
        private const val NOTIFICATION_ID = 2001
        private const val TRIGGER_POLL_INTERVAL_MS = 1_500L
        private const val MAIN_THREAD_OPERATION_TIMEOUT_MS = 1_000L
        private const val ACCESSIBILITY_GESTURE_SETTLE_MS = 90L
        private const val INPUT_WINDOW_SETTLE_MS = 64L
        // 目标位置拦截窗口最小尺寸（与 v1.5.8 固定拦截尺寸一致），
        // 防止用户划定过小导致按钮仍可点击。

        @Volatile
        var isRunning: Boolean = false

        @Volatile
        var isForeground: Boolean = false

        @Volatile
        private var userStopRequested: Boolean = false

        @Volatile
        private var instance: HighRiskOverlayService? = null

        fun markUserStop() {
            userStopRequested = true
        }

        /**
         * 指定应用启动配置变化后，让运行中的服务重启轮询。
         */
        @JvmStatic
        fun serviceRestartTrigger() {
            instance?.restartTriggerIfNeeded()
        }
    }
}
