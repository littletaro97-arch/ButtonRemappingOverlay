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

/**
 * 长按触发模式编辑器：上传截图后编辑一个"长按触发区域"。
 * 支持拖动、右下角缩放、透明度、圆角，最小尺寸与其他模式一致。
 */
class LongPressEditorActivity : Activity() {
    private lateinit var editorRoot: FrameLayout
    private lateinit var canvasView: ScreenshotCanvasView
    private lateinit var areaView: ComponentEditorView
    private lateinit var opacityLabel: TextView
    private lateinit var cornerLabel: TextView
    private lateinit var sizeHint: TextView

    private lateinit var sourceUri: Uri
    private var sourceWidth = 0
    private var sourceHeight = 0
    private lateinit var bitmap: Bitmap
    private lateinit var config: LongPressConfig
    private val lastCanvasBounds = Rect()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = Uri.parse(intent.getStringExtra(EXTRA_URI).orEmpty())
        sourceWidth = intent.getIntExtra(EXTRA_WIDTH, 0)
        sourceHeight = intent.getIntExtra(EXTRA_HEIGHT, 0)
        if (sourceWidth <= 0 || sourceHeight <= 0 || sourceUri.toString().isBlank()) {
            RuntimeProtection.recordEvent(this, "长按触发编辑器打开失败：截图参数无效")
            finishWithMessage("截图尺寸无效，无法编辑")
            return
        }
        RuntimeProtection.recordEvent(this, "打开长按触发编辑器", "${sourceWidth}x${sourceHeight}")
        requestedOrientation = if (sourceWidth >= sourceHeight) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        stopService(Intent(this, LongPressOverlayService::class.java))
        bitmap = decodeBitmap(sourceUri, sourceWidth, sourceHeight)
            ?: run {
                RuntimeProtection.recordEvent(this, "长按触发编辑器打开失败：图片读取失败")
                finishWithMessage("无法读取截图，请重新选择")
                return
            }

        val savedConfig = LongPressPrefs.load(this)
        val sameScreenshot = savedConfig.hasScreenshot &&
            savedConfig.screenshotUri == sourceUri.toString()
        config = if (sameScreenshot) {
            savedConfig
        } else {
            savedConfig.copy(
                area = LongPressConfig().area,
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

        areaView = ComponentEditorView(
            context = this,
            label = "长按区域",
            fillColor = Color.rgb(255, 170, 72),
            strokeColor = Color.rgb(255, 214, 132),
            movementBoundsProvider = { canvasView.contentRect() },
            onGeometryChanged = { syncConfigFromView() },
        )
        editorRoot.addView(areaView, FrameLayout.LayoutParams(dp(160), dp(100)))
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
        addView(TextView(this@LongPressEditorActivity).apply {
            text = "长按触发区域编辑"
            textSize = 17f
            setTextColor(Color.rgb(26, 31, 39))
        }, LinearLayout.LayoutParams(0, -2, 0.7f))
        sizeHint = TextView(this@LongPressEditorActivity).apply {
            textSize = 10f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        addView(sizeHint, LinearLayout.LayoutParams(0, -2, 1.3f))
        addView(TextView(this@LongPressEditorActivity).apply {
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

        opacityLabel = TextView(this@LongPressEditorActivity).apply {
            textSize = 12f
            setTextColor(Color.rgb(90, 100, 114))
        }
        addView(opacityLabel, LinearLayout.LayoutParams(dp(76), -2))
        addView(SeekBar(this@LongPressEditorActivity).apply {
            max = 100
            progress = (config.areaAlpha * 100f).roundToInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    config = config.copy(areaAlpha = (progress / 100f).coerceIn(0.05f, 1f))
                    areaView.alpha = config.areaAlpha
                    updateOpacityLabel()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }, LinearLayout.LayoutParams(0, dp(28), 1f))

        cornerLabel = TextView(this@LongPressEditorActivity).apply {
            textSize = 12f
            setTextColor(Color.rgb(90, 100, 114))
        }
        addView(cornerLabel, LinearLayout.LayoutParams(dp(76), -2).apply {
            leftMargin = dp(10)
        })
        addView(SeekBar(this@LongPressEditorActivity).apply {
            max = 100
            progress = (config.cornerRadius * 200f).roundToInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    config = config.copy(cornerRadius = (progress / 200f).coerceIn(0f, 0.5f))
                    areaView.setCornerRadiusRatio(config.cornerRadius)
                    updateCornerLabel()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }, LinearLayout.LayoutParams(0, dp(28), 1f))
        addView(Button(this@LongPressEditorActivity).apply {
            text = "保存区域"
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
        }, LinearLayout.LayoutParams(dp(110), dp(30)).apply { leftMargin = dp(10) })
        updateOpacityLabel()
        updateCornerLabel()
    }

    private fun refreshEditor() {
        val content = canvasView.contentRect()
        if (content.width() <= 0 || content.height() <= 0) return
        if (lastCanvasBounds != content) {
            // 与保存/运行时使用同一旋转换算，保证竖屏画布下预览一致。
            val geometry = OverlayGeometry.fromWindowManager(this)
            areaView.setPixelRect(
                OverlayGeometry.toPixelRect(
                    config.area,
                    geometry,
                    config.coordinateRotation,
                ),
            )
            lastCanvasBounds.set(content)
        }
        areaView.alpha = config.areaAlpha
        areaView.setCornerRadiusRatio(config.cornerRadius)
        updateSizeHint()
    }

    private fun syncConfigFromView() {
        val content = canvasView.contentRect()
        if (content.width() <= 0 || content.height() <= 0) return
        val geometry = OverlayGeometry.fromWindowManager(this)
        val savedRotation = if (sourceWidth >= sourceHeight) {
            geometry.rotation
        } else {
            android.view.Surface.ROTATION_90
        }
        config = config.copy(
            area = OverlayGeometry.toRatio(areaView.pixelRect(), geometry, savedRotation),
            coordinateRotation = savedRotation,
        )
    }

    private fun updateOpacityLabel() {
        if (::opacityLabel.isInitialized) {
            opacityLabel.text = "区域 ${(config.areaAlpha * 100f).roundToInt()}%"
        }
    }

    private fun updateCornerLabel() {
        if (!::cornerLabel.isInitialized) return
        val percent = (config.cornerRadius * 200f).roundToInt()
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
            "长按区域可完全透明或半透明显示"
        } else {
            "截图尺寸与当前设备不同，请重新使用本机截图"
        }
        sizeHint.setTextColor(
            if (sameSize) Color.rgb(90, 100, 114) else Color.rgb(176, 122, 26),
        )
    }

    private fun saveAndExit() {
        syncConfigFromView()
        LongPressPrefs.save(
            this,
            config.copy(
                screenshotUri = sourceUri.toString(),
                screenshotWidth = sourceWidth,
                screenshotHeight = sourceHeight,
                coordinateRotation = OverlayGeometry.fromWindowManager(this).rotation,
            ),
        )
        RuntimeProtection.recordEvent(
            this,
            "保存长按触发布局并退出编辑器",
            "screen=${sourceWidth}x${sourceHeight}; area=${config.area}; alpha=${config.areaAlpha}; corner=${config.cornerRadius}; longPressMs=${config.longPressMs}",
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

    private fun restoreStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        const val EXTRA_URI = "longpress_screenshot_uri"
        const val EXTRA_WIDTH = "longpress_screenshot_width"
        const val EXTRA_HEIGHT = "longpress_screenshot_height"
        private const val MAX_BITMAP_SIDE = 2400
    }
}
