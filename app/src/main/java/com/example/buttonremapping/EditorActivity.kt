package com.example.buttonremapping

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.roundToInt

class EditorActivity : Activity() {
    private lateinit var editorRoot: FrameLayout
    private lateinit var backdrop: EditorBackdropView
    private lateinit var blockedView: ComponentEditorView
    private lateinit var blockedOpacityLabel: TextView
    private lateinit var blockedOpacitySeekBar: SeekBar
    private lateinit var blockedCornerLabel: TextView
    private lateinit var blockedCornerSeekBar: SeekBar

    private var windowInsets: WindowInsets? = null
    private var geometry: DisplayGeometry? = null
    private lateinit var config: LayoutConfig
    private var layoutReady = false
    private var lastRootWidth = 0
    private var lastRootHeight = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        super.onCreate(savedInstanceState)
        RuntimeProtection.recordEvent(this, "打开低风险空白布局编辑器")
        stopService(android.content.Intent(this, OverlayService::class.java))
        config = LayoutPrefs.load(this)
        window.statusBarColor = Color.rgb(14, 17, 22)
        window.navigationBarColor = Color.rgb(14, 17, 22)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        setContentView(createEditor())
        editorRoot.post { hideEditorStatusBar() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideEditorStatusBar()
    }

    private fun createEditor(): View {
        editorRoot = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(14, 17, 22))
            setOnApplyWindowInsetsListener { _, insets ->
                windowInsets = insets
                refreshGeometry()
                insets
            }
            addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                val width = right - left
                val height = bottom - top
                if (width != lastRootWidth || height != lastRootHeight) {
                    lastRootWidth = width
                    lastRootHeight = height
                    refreshGeometry()
                }
            }
        }

        backdrop = EditorBackdropView(this)
        editorRoot.addView(backdrop, FrameLayout.LayoutParams(-1, -1))

        blockedView = ComponentEditorView(
            context = this,
            label = "原按钮区域",
            fillColor = Color.rgb(255, 101, 101),
            strokeColor = Color.rgb(255, 130, 127),
            movementBoundsProvider = { geometry?.fullBounds },
            onGeometryChanged = { syncConfigFromViews() },
        )
        editorRoot.addView(blockedView, FrameLayout.LayoutParams(dp(160), dp(100)))

        val topBar = createTopBar()
        val bottomBar = createBottomBar()
        editorRoot.addView(topBar, FrameLayout.LayoutParams(-1, -2).apply {
            gravity = Gravity.TOP
        })
        editorRoot.addView(bottomBar, FrameLayout.LayoutParams(-1, dp(56)).apply {
            gravity = Gravity.BOTTOM
        })
        // 点击编辑区空白处（非组件、非遮挡栏）：隐藏/重现上下遮挡栏。
        editorRoot.isClickable = true
        editorRoot.setOnClickListener { toggleEditorBars(topBar, bottomBar) }
        return editorRoot
    }

    private fun toggleEditorBars(topBar: View, bottomBar: View) {
        val visible = topBar.visibility == View.VISIBLE
        topBar.visibility = if (visible) View.GONE else View.VISIBLE
        bottomBar.visibility = if (visible) View.GONE else View.VISIBLE
    }

    private fun createTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(4))
            background = GradientDrawable().apply { setColor(Color.argb(232, 22, 27, 35)) }
        }
        val title = TextView(this).apply {
            text = "布局编辑 · 横屏"
            textSize = 17f
            setTextColor(Color.rgb(244, 247, 251))
        }
        bar.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(Button(this).apply {
            text = "保存并返回"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.rgb(9, 17, 28))
            background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(Color.rgb(116, 167, 255))
            }
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { saveAndExit() }
        }, LinearLayout.LayoutParams(dp(104), dp(24)))
        return bar
    }

    private fun createBottomBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(2), dp(12), dp(2))
            background = GradientDrawable().apply { setColor(Color.argb(232, 22, 27, 35)) }
        }
        blockedOpacityLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(170, 181, 196))
        }
        bar.addView(blockedOpacityLabel, LinearLayout.LayoutParams(dp(76), -2))
        blockedOpacitySeekBar = createSeekBar(
            max = 100,
            progress = (config.blockedAreaAlpha * 100f).roundToInt(),
        ) { progress ->
            val alpha = (progress / 100f).coerceIn(0.05f, 1f)
            config = config.copy(blockedAreaAlpha = alpha)
            blockedView.alpha = alpha
            updateBlockedOpacityLabel()
        }
        bar.addView(blockedOpacitySeekBar, LinearLayout.LayoutParams(0, dp(28), 1f))
        blockedCornerLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(170, 181, 196))
        }
        bar.addView(blockedCornerLabel, LinearLayout.LayoutParams(dp(76), -2).apply {
            leftMargin = dp(10)
        })
        blockedCornerSeekBar = createSeekBar(
            max = 100,
            progress = (config.blockedCornerRadius * 200f).roundToInt(),
        ) { progress ->
            val radius = (progress / 200f).coerceIn(0f, 0.5f)
            config = config.copy(blockedCornerRadius = radius)
            blockedView.setCornerRadiusRatio(radius)
            updateBlockedCornerLabel()
        }
        bar.addView(blockedCornerSeekBar, LinearLayout.LayoutParams(0, dp(28), 1f))
        updateBlockedOpacityLabel()
        updateBlockedCornerLabel()
        return bar
    }

    private fun createSeekBar(
        max: Int,
        progress: Int,
        onProgressChanged: (Int) -> Unit,
    ): SeekBar = SeekBar(this).apply {
        this.max = max
        this.progress = progress.coerceIn(0, max)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                onProgressChanged(value)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun refreshGeometry() {
        if (!::editorRoot.isInitialized || editorRoot.width <= 0 || editorRoot.height <= 0) return
        geometry = OverlayGeometry.fromRoot(editorRoot, windowInsets)
        backdrop.geometry = geometry
        blockedView.setPixelRect(
            OverlayGeometry.toPixelRect(
                config.blockedArea,
                geometry!!,
                config.coordinateRotation,
            ),
        )
        blockedView.alpha = config.blockedAreaAlpha
        blockedView.setCornerRadiusRatio(config.blockedCornerRadius)
        layoutReady = true
        editorRoot.post { syncConfigFromViews() }
    }

    private fun syncConfigFromViews() {
        if (!layoutReady) return
        val currentGeometry = geometry ?: return
        config = config.copy(
            blockedArea = OverlayGeometry.toRatio(
                blockedView.pixelRect(),
                currentGeometry,
                currentGeometry.rotation,
            ),
            coordinateSpaceVersion = OverlayGeometry.CURRENT_COORDINATE_SPACE_VERSION,
            coordinateRotation = currentGeometry.rotation,
        )
        updateBlockedOpacityLabel()
    }

    private fun updateBlockedOpacityLabel() {
        blockedOpacityLabel.text = getString(
            R.string.blocked_opacity_summary,
            (config.blockedAreaAlpha * 100f).roundToInt(),
        )
    }

    private fun updateBlockedCornerLabel() {
        if (!::blockedCornerLabel.isInitialized) return
        val percent = (config.blockedCornerRadius * 200f).roundToInt()
        blockedCornerLabel.text = when {
            percent >= 100 -> "形状 圆形"
            percent <= 0 -> "形状 正方形"
            else -> "圆角 $percent%"
        }
    }

    private fun saveAndExit() {
        syncConfigFromViews()
        LayoutPrefs.save(this, config)
        RuntimeProtection.recordEvent(
            this,
            "保存低风险空白布局并退出编辑器",
            "blocked=${config.blockedArea}; alpha=${config.blockedAreaAlpha}; corner=${config.blockedCornerRadius}",
        )
        setResult(RESULT_OK)
        restoreSystemBars()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        finish()
    }

    override fun onBackPressed() {
        saveAndExit()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun hideEditorStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsets.Type.statusBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    private fun restoreSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }
}

class EditorBackdropView(context: android.content.Context) : View(context) {
    var geometry: DisplayGeometry? = null
        set(value) {
            field = value
            invalidate()
        }

    private val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(130, 116, 167, 255)
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }
    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(14, 17, 22))
        val full = geometry?.fullBounds ?: return
        canvas.drawRect(full, borderPaint)
    }
}
