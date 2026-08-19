package com.example.buttonremapping

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.example.buttonremapping.profile.ProfileManager
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private val blockedViews = mutableListOf<BlockOverlayView>()
    private var toggleView: LowRiskToggleView? = null
    private var blockedRect = Rect()
    private var toggleRect = Rect()
    private var blockedCornerRadius = 0.5f
    private var blockedAreaAlpha = 0.38f
    private var toggleDragStartX = 0
    private var toggleDragStartY = 0
    private val triggerHandler = Handler(Looper.getMainLooper())
    private var lastForegroundPackage: String? = null
    private var triggerPolling = false
    private var triggerActive = false

    private val triggerPollRunnable = object : Runnable {
        override fun run() {
            pollForegroundApp()
            if (triggerPolling) triggerHandler.postDelayed(this, TRIGGER_POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        RuntimeProtection.recordEvent(this, "Overlay service created")
        serviceInstance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        userStopRequested = false
        RuntimeProtection.recordEvent(this, "Overlay service start")
        val overlayGranted = runCatching { Settings.canDrawOverlays(this) }
            .onFailure { RuntimeProtection.recordFailure(this, "Overlay permission check exception", it) }
            .getOrDefault(false)
        if (!overlayGranted) {
            RuntimeProtection.recordEvent(this, "Overlay permission check failed")
            stopSelf()
            return START_NOT_STICKY
        }
        return try {
            startAsForeground()
            showOverlays(shouldBlock = true)
            startTriggerPolling()
            if (isRunning) START_STICKY else START_NOT_STICKY
        } catch (error: Exception) {
            RuntimeProtection.recordFailure(this, "低风险悬浮层启动失败，服务将安全退出", error)
            removeOverlayViews()
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        RuntimeProtection.recordEvent(this, "Overlay configuration changed")
        if (isRunning) {
            val shouldBlock = isBlocked
            showOverlays(shouldBlock)
        }
    }

    override fun onDestroy() {
        val reason = if (userStopRequested) "user stop" else "system/error"
        stopTriggerPolling()
        removeOverlayViews()
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForeground = false
        isRunning = false
        isBlocked = false
        userStopRequested = false
        if (serviceInstance === this) serviceInstance = null
        RuntimeProtection.recordEvent(this, "Service destroyed: $reason")
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        RuntimeProtection.recordEvent(this, "Service task removed")
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlays(shouldBlock: Boolean) {
        removeOverlayViews()
        try {
            val geometry = OverlayGeometry.fromWindowManager(this)
            var config = LayoutPrefs.load(this)
            blockedCornerRadius = config.blockedCornerRadius
            blockedAreaAlpha = config.blockedAreaAlpha.coerceIn(0f, 1f)
            blockedRect = OverlayGeometry.toPixelRect(
                config.blockedArea,
                geometry,
                config.coordinateRotation,
            )
            val requestedToggleRect = OverlayGeometry.toPixelRect(
                config.toggleButton,
                geometry,
                config.coordinateRotation,
            )
            toggleRect = keepToggleOutsideBlockedArea(
                requested = requestedToggleRect,
                bounds = geometry.fullBounds,
                blocked = blockedRect,
            )
            if (toggleRect != requestedToggleRect) {
                config = config.copy(
                    toggleButton = OverlayGeometry.toRatio(
                        toggleRect,
                        geometry,
                        config.coordinateRotation,
                    ),
                )
                LayoutPrefs.save(this, config)
            }

            val newToggleView = LowRiskToggleView(
                context = this,
                onToggle = { toggleBlockedState() },
                onDragStart = { beginToggleDrag() },
                onDrag = { deltaX, deltaY -> moveToggle(deltaX, deltaY) },
                onDragEnd = { saveTogglePosition() },
            ).apply {
                setBlocked(shouldBlock)
                alpha = config.toggleAlpha.coerceIn(0.2f, 1f)
            }
            RuntimeProtection.recordEvent(this, "WindowManager addView: toggle")
            windowManager.addView(
                newToggleView,
                createWindowParams(toggleRect, "屏蔽开关"),
            )
            toggleView = newToggleView
            isRunning = true
            RuntimeProtection.recordEvent(this, "WindowManager addView: toggle success")
            setBlockedState(shouldBlock)
            if (shouldBlock && !isBlocked) {
                Log.e(TAG, "屏蔽区创建失败，开关仍保持可见并显示为允许点击")
                RuntimeProtection.recordEvent(this, "Overlay add failure: blocked area unavailable")
            }
        } catch (error: Exception) {
            RuntimeProtection.recordFailure(this, "Overlay 窗口创建失败，服务将安全退出", error)
            removeOverlayViews()
            stopSelf()
        }
    }

    private fun toggleBlockedState() {
        if (!isRunning) {
            RuntimeProtection.recordEvent(this, "屏蔽开关点击被忽略：服务未运行")
            return
        }
        setBlockedState(!isBlocked)
        RuntimeProtection.recordEvent(
            this,
            if (isBlocked) "屏蔽开关点击：已开启屏蔽" else "屏蔽开关点击：已关闭屏蔽",
        )
    }

    private fun setBlockedState(blocked: Boolean) {
        if (blocked) {
            if (blockedViews.isEmpty()) {
                addBlockedOverlayWindows()
            }
            blockedViews.forEach { it.visibility = android.view.View.VISIBLE }
            isBlocked = blockedViews.isNotEmpty()
        } else {
            // Keep the small local windows attached and only hide them. Recreating
            // many rounded stripes on every tap made the ON/OFF response feel slow.
            blockedViews.forEach { it.visibility = android.view.View.GONE }
            isBlocked = false
        }
        toggleView?.setBlocked(isBlocked)
    }

    /**
     * 指定应用启动：读取当前 Profile 的 trigger 配置。
     */
    private fun currentTriggerConfig(): Pair<Boolean, List<String>> {
        val config = LayoutPrefs.load(this)
        return config.triggerEnabled to config.triggerPackages
    }

    /**
     * 启动前台应用轮询。服务常驻期间，前台应用变化时自动开启/关闭屏蔽。
     */
    private fun startTriggerPolling() {
        if (triggerPolling) return
        val (enabled, packages) = currentTriggerConfig()
        if (!enabled || packages.isEmpty()) return
        triggerPolling = true
        triggerActive = true
        lastForegroundPackage = null
        RuntimeProtection.recordEvent(this, "指定应用启动：开始监控前台应用", "apps=${packages.size}")
        triggerHandler.post(triggerPollRunnable)
    }

    private fun stopTriggerPolling() {
        triggerPolling = false
        triggerActive = false
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
        val shouldBlock = foreground != null && packages.contains(foreground)
        val changed = shouldBlock != isBlocked
        if (changed) {
            RuntimeProtection.recordEvent(
                this,
                "指定应用启动：前台应用变化",
                "${foreground ?: "未知"} -> ${if (shouldBlock) "开启屏蔽" else "关闭屏蔽"}",
            )
            setBlockedState(shouldBlock)
        }
        // 指定应用启动模式下，悬浮开关随前台应用显示/隐藏：
        // 不在目标应用时连“允许”开关也隐藏，避免打扰正常操作手机。
        if (foreground != null) {
            toggleView?.visibility = if (shouldBlock) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
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

    /**
     * Prefer a small set of rounded stripes, but never let one OEM-specific
     * addView failure tear down the whole service. A single rectangular window
     * is the conservative fallback: it may block the rounded corners too, but
     * it keeps the user-visible switch and the safety block operational.
     */
    private fun addBlockedOverlayWindows(): Boolean {
        val stripeRects = blockedStripeRects(blockedRect, blockedCornerRadius)
        if (stripeRects.any { !isValidRect(it) }) {
            Log.e(TAG, "屏蔽区坐标无效：$blockedRect")
            return false
        }

        val added = mutableListOf<BlockOverlayView>()
        return try {
            RuntimeProtection.recordEvent(this, "WindowManager addView: blocked area count=${stripeRects.size}")
            stripeRects.forEach { rect ->
                val view = createBlockView()
                windowManager.addView(view, createWindowParams(rect, "原按钮屏蔽区域"))
                added += view
            }
            blockedViews += added
            RuntimeProtection.recordEvent(this, "WindowManager addView: blocked area success")
            true
        } catch (error: RuntimeException) {
            RuntimeProtection.recordFailure(
                this,
                "圆角屏蔽区创建失败，尝试单窗口兜底（窗口数=${stripeRects.size}）",
                error,
            )
            added.asReversed().forEach(::removeBlockView)
            blockedViews.clear()
            addRectangularFallback()
        }
    }

    private fun addRectangularFallback(): Boolean {
        if (!isValidRect(blockedRect)) return false
        return try {
            val view = createBlockView()
            windowManager.addView(view, createWindowParams(blockedRect, "原按钮屏蔽区域"))
            blockedViews += view
            Log.i(TAG, "已启用单窗口矩形屏蔽兜底")
            RuntimeProtection.recordEvent(this, "WindowManager addView: rectangular fallback success")
            true
        } catch (error: RuntimeException) {
            RuntimeProtection.recordFailure(this, "单窗口矩形屏蔽兜底也失败", error)
            false
        }
    }

    private fun createBlockView(): BlockOverlayView = BlockOverlayView(this).apply {
        // 屏蔽区域透明度由配置 blockedAreaAlpha 控制（下限 0.05，不为 0），
        // 运行时显示为半透明红色标识；触摸拦截逻辑不变。
        alpha = blockedAreaAlpha
        setBackgroundColor(BLOCKED_VIEW_COLOR)
    }

    private fun removeBlockView(view: BlockOverlayView) {
        try {
            windowManager.removeView(view)
        } catch (error: RuntimeException) {
            Log.d(TAG, "屏蔽窗口已经被系统移除", error)
        }
    }

    private fun isValidRect(rect: Rect): Boolean =
        rect.left >= 0 && rect.top >= 0 && rect.width() > 0 && rect.height() > 0

    private fun moveToggle(deltaX: Float, deltaY: Float) {
        val view = toggleView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val geometry = OverlayGeometry.fromWindowManager(this)
        val bounds = geometry.fullBounds
        val requested = Rect(
            toggleDragStartX + deltaX.toInt(),
            toggleDragStartY + deltaY.toInt(),
            toggleDragStartX + deltaX.toInt() + params.width,
            toggleDragStartY + deltaY.toInt() + params.height,
        )
        val adjusted = keepToggleOutsideBlockedArea(requested, bounds, blockedRect)
        params.x = adjusted.left
        params.y = adjusted.top
        toggleRect = Rect(params.x, params.y, params.x + params.width, params.y + params.height)
        try {
            windowManager.updateViewLayout(view, params)
        } catch (error: RuntimeException) {
            // The system removed the overlay while a drag was in progress.
            Log.d(TAG, "拖动时悬浮开关窗口已不可用", error)
            RuntimeProtection.recordEvent(this, "拖动悬浮开关失败", error.javaClass.simpleName)
        }
    }

    private fun beginToggleDrag() {
        val view = toggleView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        toggleDragStartX = params.x
        toggleDragStartY = params.y
        showBlockedAreaIndicator()
        RuntimeProtection.recordEvent(this, "开始拖动悬浮开关")
    }

    private fun saveTogglePosition() {
        hideBlockedAreaIndicator()
        val view = toggleView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val geometry = OverlayGeometry.fromWindowManager(this)
        val rect = Rect(params.x, params.y, params.x + params.width, params.y + params.height)
        toggleRect = rect
        val config = LayoutPrefs.load(this)
        LayoutPrefs.save(
            this,
            config.copy(
                toggleButton = OverlayGeometry.toRatio(
                    rect,
                    geometry,
                    config.coordinateRotation,
                ),
            ),
        )
        RuntimeProtection.recordEvent(
            this,
            "保存悬浮开关位置",
            "x=${rect.left}; y=${rect.top}",
        )
    }

    /**
     * 拖动悬浮开关时显示屏蔽区域（半透明红色标识），方便确认开关位置。
     * 屏蔽关闭时不显示。
     */
    private fun showBlockedAreaIndicator() {
        if (!isBlocked || blockedViews.isEmpty()) return
        blockedViews.forEach { view ->
            // 拖动悬浮开关时把屏蔽区域临时提亮，方便确认位置。
            view.alpha = 0.9f
            view.setBackgroundColor(BLOCKED_VIEW_COLOR)
        }
        RuntimeProtection.recordEvent(this, "显示屏蔽区域标识")
    }

    private fun hideBlockedAreaIndicator() {
        blockedViews.forEach { view ->
            view.alpha = blockedAreaAlpha
            view.setBackgroundColor(BLOCKED_VIEW_COLOR)
        }
    }

    private fun clampToBounds(rect: Rect, bounds: Rect): Rect {
        val left = rect.left.coerceIn(bounds.left, (bounds.right - rect.width()).coerceAtLeast(bounds.left))
        val top = rect.top.coerceIn(bounds.top, (bounds.bottom - rect.height()).coerceAtLeast(bounds.top))
        return Rect(left, top, left + rect.width(), top + rect.height())
    }

    private fun blockedStripeRects(rect: Rect, cornerRadiusRatio: Float): List<Rect> {
        val radius = min(rect.width(), rect.height()) * cornerRadiusRatio.coerceIn(0f, 0.5f)
        if (radius <= 0.5f) return listOf(Rect(rect))

        val stripeCount = ROUNDED_STRIPE_COUNT.coerceAtMost(rect.height().coerceAtLeast(1))
        val result = mutableListOf<Rect>()
        val localHeight = rect.height().toFloat()

        fun horizontalInset(y: Float): Float {
            val clampedY = y.coerceIn(0f, localHeight)
            val topInset = if (clampedY < radius) {
                radius - sqrt((radius * radius - (radius - clampedY) * (radius - clampedY)).coerceAtLeast(0f))
            } else {
                0f
            }
            val distanceFromBottom = localHeight - clampedY
            val bottomInset = if (distanceFromBottom < radius) {
                radius - sqrt((radius * radius - (radius - distanceFromBottom) * (radius - distanceFromBottom)).coerceAtLeast(0f))
            } else {
                0f
            }
            return max(topInset, bottomInset)
        }

        for (index in 0 until stripeCount) {
            val top = rect.top + rect.height() * index / stripeCount
            val bottom = rect.top + rect.height() * (index + 1) / stripeCount
            if (bottom <= top) continue
            val localTop = (top - rect.top).toFloat()
            val localBottom = (bottom - rect.top).toFloat()
            // 用 min 取该条纹内圆的最宽处，并向两侧 floor 外扩，保证相邻条纹无缝隙、
            // 整圆被完全覆盖，不会出现“圆内漏点”触发下层按钮。
            val inset = min(horizontalInset(localTop), horizontalInset(localBottom))
            val left = rect.left + floor(inset).toInt()
            val right = rect.right - floor(inset).toInt()
            if (right > left) result += Rect(left, top, right, bottom)
        }
        return result.ifEmpty { listOf(Rect(rect)) }
    }

    private fun keepToggleOutsideBlockedArea(
        requested: Rect,
        bounds: Rect,
        blocked: Rect,
    ): Rect {
        val clamped = clampToBounds(requested, bounds)
        if (!Rect.intersects(clamped, blocked)) return clamped

        val margin = (bounds.width() * 0.02f).toInt().coerceAtLeast(12)
        val width = clamped.width()
        val height = clamped.height()
        val candidates = mutableListOf<Rect>()
        fun add(left: Int, top: Int) {
            val candidate = clampToBounds(Rect(left, top, left + width, top + height), bounds)
            if (!Rect.intersects(candidate, blocked)) candidates += candidate
        }

        add(bounds.left + margin, bounds.top + margin)
        add(bounds.right - width - margin, bounds.top + margin)
        add(bounds.left + margin, bounds.bottom - height - margin)
        add(bounds.right - width - margin, bounds.bottom - height - margin)
        add(blocked.left - width - margin, blocked.top)
        add(blocked.right + margin, blocked.top)
        add(blocked.left, blocked.top - height - margin)
        add(blocked.left, blocked.bottom + margin)

        return candidates.minByOrNull { candidate ->
            val dx = candidate.centerX() - clamped.centerX()
            val dy = candidate.centerY() - clamped.centerY()
            abs(dx * dx) + abs(dy * dy)
        } ?: clamped
    }

    private fun createWindowParams(rect: Rect, title: String): WindowManager.LayoutParams =
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
            this.title = title
        }

    private fun removeOverlayViews() {
        val hadViews = blockedViews.isNotEmpty() || toggleView != null
        blockedViews.toList().forEach { view ->
            try {
                windowManager.removeView(view)
            } catch (error: RuntimeException) {
                // It was already removed by the system.
                Log.d(TAG, "移除屏蔽窗口时发现窗口已不存在", error)
            }
        }
        blockedViews.clear()
        toggleView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (error: RuntimeException) {
                // It was already removed by the system.
                Log.d(TAG, "移除悬浮开关时发现窗口已不存在", error)
            }
        }
        toggleView = null
        isRunning = false
        isBlocked = false
        if (hadViews) RuntimeProtection.recordEvent(this, "WindowManager removeView: overlay views")
    }

    private fun startAsForeground() {
        val profileName = ProfileManager.currentName(
            this,
            com.example.buttonremapping.profile.ProfileMode.LOW,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("游戏按钮映射 · $profileName")
            .setContentText("屏蔽区域模式：屏蔽开关正在运行")
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
        RuntimeProtection.recordEvent(this, "Foreground service started")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "屏蔽区域模式：屏蔽一个触摸区域并提供手动开关"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "ButtonOverlay"
        private const val CHANNEL_ID = "overlay_prototype"
        private const val NOTIFICATION_ID = 1001
        private const val ROUNDED_STRIPE_COUNT = 8
        /** 屏蔽区域运行时底色（与编辑器屏蔽框的红色系一致），透明度由配置控制。 */
        private val BLOCKED_VIEW_COLOR = Color.rgb(255, 101, 101)
        private const val TRIGGER_POLL_INTERVAL_MS = 1_500L

        @Volatile
        private var serviceInstance: OverlayService? = null

        @Volatile
        var isRunning: Boolean = false

        @Volatile
        var isBlocked: Boolean = false

        @Volatile
        var isForeground: Boolean = false

        @Volatile
        private var userStopRequested: Boolean = false

        fun markUserStop() {
            userStopRequested = true
        }

        /**
         * 拖动透明度滑块时实时更新运行中的悬浮开关透明度。
         * 服务未运行或开关未创建时静默忽略。
         */
        @JvmStatic
        fun setToggleAlpha(alpha: Float) {
            val view = serviceInstance?.toggleView ?: return
            view.alpha = alpha.coerceIn(0.2f, 1f)
        }

        /**
         * 指定应用启动配置变化后，让运行中的服务重启轮询。
         */
        @JvmStatic
        fun serviceRestartTrigger() {
            serviceInstance?.restartTriggerIfNeeded()
        }
    }
}
