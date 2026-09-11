package com.example.buttonremapping

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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
import android.widget.Toast
import kotlin.math.roundToInt

class ScreenshotEditorActivity : Activity() {
    private lateinit var editorRoot: FrameLayout
    private lateinit var canvasView: ScreenshotCanvasView
    private lateinit var blockedView: ComponentEditorView
    private lateinit var opacityLabel: TextView
    private lateinit var cornerLabel: TextView
    private lateinit var sizeHint: TextView

    private lateinit var sourceUri: Uri
    private var sourceWidth = 0
    private var sourceHeight = 0
    private lateinit var bitmap: Bitmap
    private lateinit var config: LayoutConfig
    private val lastCanvasBounds = Rect()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = Uri.parse(intent.getStringExtra(EXTRA_URI).orEmpty())
        sourceWidth = intent.getIntExtra(EXTRA_WIDTH, 0)
        sourceHeight = intent.getIntExtra(EXTRA_HEIGHT, 0)
        if (sourceWidth <= 0 || sourceHeight <= 0 || sourceUri.toString().isBlank()) {
            RuntimeProtection.recordEvent(this, "截图布局编辑器打开失败：截图参数无效")
            finishWithMessage("截图尺寸无效，无法编辑")
            return
        }
        RuntimeProtection.recordEvent(this, "打开截图布局编辑器", "${sourceWidth}x${sourceHeight}")
        requestedOrientation = if (sourceWidth >= sourceHeight) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        stopService(Intent(this, OverlayService::class.java))
        bitmap = decodeBitmap(sourceUri, sourceWidth, sourceHeight)
            ?: run {
                RuntimeProtection.recordEvent(this, "截图布局编辑器打开失败：图片读取失败")
                finishWithMessage("无法读取截图，请重新选择")
                return
            }

        val savedConfig = LayoutPrefs.load(this)
        val sameScreenshot = savedConfig.hasScreenshot &&
            savedConfig.screenshotUri == sourceUri.toString()
        config = if (sameScreenshot) {
            savedConfig
        } else {
            savedConfig.copy(
                blockedArea = LayoutPrefs.defaults.blockedArea,
                screenshotUri = sourceUri.toString(),
                screenshotWidth = sourceWidth,
                screenshotHeight = sourceHeight,
            )
        }

        window.statusBarColor = Color.rgb(247, 248, 250)
        window.navigationBarColor = Color.rgb(247, 248, 250)
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
        editorRoot.post {
            hideStatusBar()
            refreshEditor()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    override fun onBackPressed() {
        saveAndExit()
    }

    private fun createEditor(): View {
        editorRoot = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(247, 248, 250))
        }
        canvasView = ScreenshotCanvasView(
            context = this,
            bitmap = bitmap,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
        )
        editorRoot.addView(canvasView, FrameLayout.LayoutParams(-1, -1))

        blockedView = ComponentEditorView(
            context = this,
            label = "屏蔽区域",
            fillColor = Color.rgb(255, 101, 101),
            strokeColor = Color.rgb(255, 130, 127),
            movementBoundsProvider = { canvasView.contentRect() },
            onGeometryChanged = { syncConfigFromView() },
        )
        editorRoot.addView(blockedView, FrameLayout.LayoutParams(dp(160), dp(100)))
        val topBar = createTopBar()
        val bottomBar = createBottomBar()
        editorRoot.addView(topBar, FrameLayout.LayoutParams(-1, dp(48)).apply {
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

    private fun createTopBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(4), dp(14), dp(4))
        background = GradientDrawable().apply { setColor(Color.argb(238, 255, 255, 255)) }
        addView(TextView(this@ScreenshotEditorActivity).apply {
            text = "截图编辑"
            textSize = 17f
            setTextColor(Color.rgb(26, 31, 39))
        }, LinearLayout.LayoutParams(0, -2, 0.7f))
        sizeHint = TextView(this@ScreenshotEditorActivity).apply {
            textSize = 10f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        addView(sizeHint, LinearLayout.LayoutParams(0, -2, 1.3f))
        addView(TextView(this@ScreenshotEditorActivity).apply {
            text = getString(R.string.screenshot_dimensions, sourceWidth, sourceHeight)
            textSize = 12f
            setTextColor(Color.rgb(90, 100, 114))
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(-2, -1))
        updateSizeHint()
    }

    private fun createBottomBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(2), dp(12), dp(2))
        background = GradientDrawable().apply { setColor(Color.argb(238, 255, 255, 255)) }

        opacityLabel = TextView(this@ScreenshotEditorActivity).apply {
            textSize = 12f
            setTextColor(Color.rgb(90, 100, 114))
        }
        addView(opacityLabel, LinearLayout.LayoutParams(dp(76), -2))
        addView(createTransparencySeekBar(
            context = this@ScreenshotEditorActivity,
            initialAlpha = config.blockedAreaAlpha,
        ) { alpha ->
            config = config.copy(blockedAreaAlpha = alpha)
            blockedView.alpha = alpha
            updateOpacityLabel()
        }, LinearLayout.LayoutParams(0, dp(28), 1f))

        cornerLabel = TextView(this@ScreenshotEditorActivity).apply {
            textSize = 12f
            setTextColor(Color.rgb(90, 100, 114))
        }
        addView(cornerLabel, LinearLayout.LayoutParams(dp(76), -2).apply {
            leftMargin = dp(10)
        })
        addView(SeekBar(this@ScreenshotEditorActivity).apply {
            max = 100
            progress = (config.blockedCornerRadius * 200f).roundToInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    config = config.copy(blockedCornerRadius = (progress / 200f).coerceIn(0f, 0.5f))
                    blockedView.setCornerRadiusRatio(config.blockedCornerRadius)
                    updateCornerLabel()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }, LinearLayout.LayoutParams(0, dp(28), 1f))
        addView(Button(this@ScreenshotEditorActivity).apply {
            text = "保存屏蔽区域"
            isAllCaps = false
            textSize = 12f
            minHeight = 0
            minimumHeight = 0
            setTextColor(Color.rgb(9, 17, 28))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(Color.rgb(116, 167, 255))
            }
            setPadding(dp(6), 0, dp(6), 0)
            setOnClickListener { saveAndExit() }
        }, LinearLayout.LayoutParams(dp(126), dp(30)).apply { leftMargin = dp(10) })
        updateOpacityLabel()
        updateCornerLabel()
    }

    private fun refreshEditor() {
        val content = canvasView.contentRect()
        if (content.width() <= 0 || content.height() <= 0) return
        if (lastCanvasBounds != content) {
            // 与保存/运行时使用同一旋转换算，保证竖屏画布下预览一致。
            val geometry = OverlayGeometry.fromWindowManager(this)
            blockedView.setPixelRect(
                OverlayGeometry.toPixelRect(
                    config.blockedArea,
                    geometry,
                    config.coordinateRotation,
                ),
            )
            lastCanvasBounds.set(content)
        }
        blockedView.alpha = config.blockedAreaAlpha
        blockedView.setCornerRadiusRatio(config.blockedCornerRadius)
        updateSizeHint()
    }

    private fun syncConfigFromView() {
        val content = canvasView.contentRect()
        if (content.width() <= 0 || content.height() <= 0) return
        val geometry = OverlayGeometry.fromWindowManager(this)
        // 保存方向：横屏画布记录实际旋转；竖屏画布统一转成横屏基准。
        val savedRotation = if (sourceWidth >= sourceHeight) {
            geometry.rotation
        } else {
            android.view.Surface.ROTATION_90
        }
        config = config.copy(
            blockedArea = OverlayGeometry.toRatio(blockedView.pixelRect(), geometry, savedRotation),
            coordinateRotation = savedRotation,
        )
    }

    private fun updateOpacityLabel() {
        if (::opacityLabel.isInitialized) {
            opacityLabel.text = getString(
                R.string.screenshot_preview_opacity,
                (config.blockedAreaAlpha * 100f).roundToInt(),
            )
        }
    }

    private fun updateCornerLabel() {
        if (!::cornerLabel.isInitialized) return
        val percent = (config.blockedCornerRadius * 200f).roundToInt()
        cornerLabel.text = when {
            percent >= 100 -> "形状 圆形"
            percent <= 0 -> "形状 正方形"
            else -> "圆角 $percent%"
        }
    }

    private fun updateSizeHint() {
        if (!::sizeHint.isInitialized) return
        val geometry = OverlayGeometry.fromWindowManager(this)
        val sameSize = (geometry.width == sourceWidth && geometry.height == sourceHeight) ||
            (geometry.width == sourceHeight && geometry.height == sourceWidth)
        sizeHint.text = if (sameSize) {
            "截图尺寸与当前设备一致，直接按原始全屏位置编辑"
        } else {
            "截图尺寸与当前设备不同，请重新使用本机截图"
        }
        sizeHint.setTextColor(
            if (sameSize) Color.rgb(90, 100, 114) else Color.rgb(176, 122, 26),
        )
    }

    private fun saveAndExit() {
        syncConfigFromView()
        LayoutPrefs.save(
            this,
            config.copy(
                screenshotUri = sourceUri.toString(),
                screenshotWidth = sourceWidth,
                screenshotHeight = sourceHeight,
                // coordinateRotation 已由 syncConfigFromView 按截图方向维护。
            ),
        )
        RuntimeProtection.recordEvent(
            this,
            "保存截图屏蔽区域并退出编辑器",
            "screen=${sourceWidth}x${sourceHeight}; blocked=${config.blockedArea}; alpha=${config.blockedAreaAlpha}; corner=${config.blockedCornerRadius}",
        )
        restoreStatusBar()
        setResult(RESULT_OK)
        finish()
    }

    private fun decodeBitmap(uri: Uri, width: Int, height: Int): Bitmap? {
        return try {
            val sample = calculateSampleSize(width, height, MAX_BITMAP_SIDE)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (width / sample > maxSide || height / sample > maxSide) sample *= 2
        return sample
    }

    private fun finishWithMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun hideStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsets.Type.systemBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    private fun restoreStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        const val EXTRA_URI = "screenshot_uri"
        const val EXTRA_WIDTH = "screenshot_width"
        const val EXTRA_HEIGHT = "screenshot_height"
        private const val MAX_BITMAP_SIDE = 2400
    }
}
