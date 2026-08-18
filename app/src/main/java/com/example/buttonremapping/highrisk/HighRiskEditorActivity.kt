package com.example.buttonremapping.highrisk

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
import android.view.Surface
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.example.buttonremapping.ComponentEditorView
import com.example.buttonremapping.OverlayGeometry
import com.example.buttonremapping.RuntimeProtection
import kotlin.math.roundToInt

class HighRiskEditorActivity : Activity() {
    private lateinit var editorRoot: FrameLayout
    private lateinit var canvasView: MappingCanvasView
    private lateinit var targetView: ComponentEditorView
    private lateinit var virtualView: ComponentEditorView
    private lateinit var targetOpacityLabel: TextView
    private lateinit var opacityLabel: TextView
    private lateinit var cornerLabel: TextView
    private lateinit var sizeHint: TextView
    private val lastCanvasBounds = Rect()

    private lateinit var config: MappingConfig
    private var sourceUri: Uri? = null
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var bitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUri = intent.getStringExtra(EXTRA_URI)?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        sourceWidth = intent.getIntExtra(EXTRA_WIDTH, 0)
        sourceHeight = intent.getIntExtra(EXTRA_HEIGHT, 0)
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            val geometry = OverlayGeometry.fromWindowManager(this)
            sourceWidth = geometry.width
            sourceHeight = geometry.height
        }
        // 编辑器方向跟随截图方向：竖屏游戏截图在竖屏画布编辑，避免截图被横屏
        // 拉伸后对齐的位置与运行时真实位置不一致（与长按/低风险编辑器一致）。
        requestedOrientation = if (sourceWidth >= sourceHeight) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        RuntimeProtection.recordEvent(this, "打开高风险布局编辑器", "${sourceWidth}x${sourceHeight}")
        if (sourceUri != null) {
            bitmap = decodeBitmap(sourceUri!!, sourceWidth, sourceHeight)
            if (bitmap == null) {
                RuntimeProtection.recordEvent(this, "高风险布局编辑器打开失败：图片读取失败")
                finishWithMessage("无法读取截图，请重新选择")
                return
            }
        }

        stopService(Intent(this, com.example.buttonremapping.OverlayService::class.java))
        stopService(Intent(this, HighRiskOverlayService::class.java))
        config = MappingPrefs.load(this)
        val sameSource = config.hasScreenshot && config.screenshotUri == sourceUri?.toString()
        if (!sameSource) {
            config = config.copy(
                screenshotUri = sourceUri?.toString(),
                screenshotWidth = sourceWidth,
                screenshotHeight = sourceHeight,
                configured = false,
            )
        }
        // 竖屏截图统一以 ROTATION_90 作为保存方向（与长按编辑器一致），
        // 避免旧配置的保存方向与当前截图不匹配导致首次预览偏移。
        if (sourceWidth < sourceHeight && config.coordinateRotation != Surface.ROTATION_90) {
            config = config.copy(coordinateRotation = Surface.ROTATION_90)
        }

        window.statusBarColor = Color.rgb(247, 248, 250)
        window.navigationBarColor = Color.rgb(247, 248, 250)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        }
        setContentView(createEditor())
        editorRoot.post {
            hideSystemBars()
            refreshEditor()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onBackPressed() {
        saveAndExit()
    }

    private fun createEditor(): View {
        editorRoot = FrameLayout(this).apply { setBackgroundColor(Color.rgb(247, 248, 250)) }
        canvasView = MappingCanvasView(this, bitmap, sourceWidth, sourceHeight)
        editorRoot.addView(canvasView, FrameLayout.LayoutParams(-1, -1))

        targetView = ComponentEditorView(
            context = this,
            label = "目标位置",
            fillColor = Color.rgb(255, 153, 72),
            strokeColor = Color.rgb(255, 210, 132),
            movementBoundsProvider = { canvasView.contentRect() },
            onGeometryChanged = { syncConfigFromViews() },
        )
        editorRoot.addView(targetView, FrameLayout.LayoutParams(dp(180), dp(130)))

        virtualView = ComponentEditorView(
            context = this,
            label = "新按钮",
            fillColor = Color.rgb(74, 127, 214),
            strokeColor = Color.rgb(183, 211, 255),
            movementBoundsProvider = { canvasView.contentRect() },
            onGeometryChanged = { syncConfigFromViews() },
        )
        editorRoot.addView(virtualView, FrameLayout.LayoutParams(dp(160), dp(110)))
        val topBar = createTopBar()
        val bottomBar = createBottomBar()
        editorRoot.addView(topBar, FrameLayout.LayoutParams(-1, dp(48)).apply {
            gravity = Gravity.TOP
        })
        editorRoot.addView(bottomBar, FrameLayout.LayoutParams(-1, dp(96)).apply {
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
        addView(TextView(this@HighRiskEditorActivity).apply {
            text = "单按钮映射布局"
            textSize = 17f
            setTextColor(Color.rgb(26, 31, 39))
        }, LinearLayout.LayoutParams(0, -2, 0.8f))
        sizeHint = TextView(this@HighRiskEditorActivity).apply {
            textSize = 11f
            setTextColor(Color.rgb(90, 100, 114))
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        addView(sizeHint, LinearLayout.LayoutParams(0, -2, 1.2f))
        addView(TextView(this@HighRiskEditorActivity).apply {
            text = if (sourceUri == null) "空白画布" else "截图 ${sourceWidth}×${sourceHeight}"
            textSize = 12f
            setTextColor(Color.rgb(90, 100, 114))
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(-2, -1))
    }

    private fun createBottomBar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(4), dp(12), dp(4))
        background = GradientDrawable().apply { setColor(Color.argb(238, 255, 255, 255)) }

        // 第一行：目标位置透明度 + 新按钮透明度（复用长按编辑器透明度滑块逻辑）。
        val opacityRow = LinearLayout(this@HighRiskEditorActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        targetOpacityLabel = TextView(this@HighRiskEditorActivity).apply {
            textSize = 12f
            setTextColor(Color.rgb(90, 100, 114))
        }
        opacityRow.addView(targetOpacityLabel, LinearLayout.LayoutParams(dp(88), -2))
        opacityRow.addView(SeekBar(this@HighRiskEditorActivity).apply {
            max = 100
            progress = (config.targetBlockAlpha * 100f).roundToInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    config = config.copy(targetBlockAlpha = (progress / 100f).coerceIn(0.05f, 1f))
                    targetView.alpha = config.targetBlockAlpha
                    updateTargetOpacityLabel()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }, LinearLayout.LayoutParams(0, dp(28), 1f))

        opacityLabel = TextView(this@HighRiskEditorActivity).apply {
            textSize = 12f
            setTextColor(Color.rgb(90, 100, 114))
        }
        opacityRow.addView(opacityLabel, LinearLayout.LayoutParams(dp(88), -2).apply {
            leftMargin = dp(10)
        })
        opacityRow.addView(SeekBar(this@HighRiskEditorActivity).apply {
            max = 100
            progress = (config.virtualButtonAlpha * 100f).roundToInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    config = config.copy(virtualButtonAlpha = (progress / 100f).coerceIn(0.25f, 1f))
                    virtualView.alpha = config.virtualButtonAlpha
                    updateOpacityLabel()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }, LinearLayout.LayoutParams(0, dp(28), 1f))
        addView(opacityRow, LinearLayout.LayoutParams(-1, -2))

        // 第二行：按钮形状 + 保存。
        val cornerRow = LinearLayout(this@HighRiskEditorActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        cornerLabel = TextView(this@HighRiskEditorActivity).apply {
            textSize = 12f
            setTextColor(Color.rgb(90, 100, 114))
        }
        cornerRow.addView(cornerLabel, LinearLayout.LayoutParams(dp(88), -2))
        cornerRow.addView(SeekBar(this@HighRiskEditorActivity).apply {
            max = 100
            progress = (config.virtualButtonCornerRadius * 200f).roundToInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    config = config.copy(virtualButtonCornerRadius = (progress / 200f).coerceIn(0f, 0.5f))
                    virtualView.setCornerRadiusRatio(config.virtualButtonCornerRadius)
                    updateCornerLabel()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }, LinearLayout.LayoutParams(0, dp(28), 1f))
        cornerRow.addView(Button(this@HighRiskEditorActivity).apply {
            text = "保存布局"
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
        addView(cornerRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

        updateTargetOpacityLabel()
        updateOpacityLabel()
        updateCornerLabel()
    }

    private fun updateCornerLabel() {
        if (!::cornerLabel.isInitialized) return
        val percent = (config.virtualButtonCornerRadius * 200f).roundToInt()
        cornerLabel.text = when {
            percent >= 100 -> "按钮 圆形"
            percent <= 0 -> "按钮 方形"
            else -> "按钮圆角 $percent%"
        }
    }

    private fun refreshEditor() {
        val content = canvasView.contentRect()
        if (content.width() <= 0 || content.height() <= 0) return
        if (lastCanvasBounds != content) {
            // 与保存/运行时使用同一旋转换算（toPixelRect），目标区域和
            // 新按钮都是完整的位置+尺寸组件，编辑器所见即运行时所拦截/注入
            // （与中低风险编辑器一致）。
            val geometry = OverlayGeometry.fromWindowManager(this)
            targetView.setPixelRect(
                OverlayGeometry.toPixelRect(
                    config.targetBlock,
                    geometry,
                    config.coordinateRotation,
                ),
            )
            virtualView.setPixelRect(
                OverlayGeometry.toPixelRect(
                    config.virtualButton,
                    geometry,
                    config.coordinateRotation,
                ),
            )
            lastCanvasBounds.set(content)
        }
        // WindowManager 的输入命中区域是完整矩形。目标框也固定显示为矩形，
        // 避免圆角视觉暗示角落不属于实际屏蔽区。
        targetView.setCornerRadiusRatio(0f)
        targetView.alpha = config.targetBlockAlpha
        virtualView.setCornerRadiusRatio(config.virtualButtonCornerRadius)
        virtualView.alpha = config.virtualButtonAlpha
        updateTargetOpacityLabel()
        updateOpacityLabel()
        updateCornerLabel()
        updateSizeHint()
    }

    private fun syncConfigFromViews() {
        // 视图尚未按配置定位（首次布局未完成）时禁止回写：此时 pixelRect
        // 是初始布局值，保存会把目标/新按钮的位置尺寸写坏。
        if (lastCanvasBounds.width() <= 0 || lastCanvasBounds.height() <= 0) return
        val content = canvasView.contentRect()
        if (content.width() <= 0 || content.height() <= 0) return
        val geometry = OverlayGeometry.fromWindowManager(this)
        // 与显示路径使用同一保存方向，保证往返一致：
        // 竖屏截图统一按 ROTATION_90 保存，横屏截图按当前设备旋转保存。
        val savedRotation = if (sourceWidth >= sourceHeight) {
            geometry.rotation
        } else {
            Surface.ROTATION_90
        }
        config = config.copy(
            targetBlock = OverlayGeometry.toRatio(
                targetView.pixelRect(),
                geometry,
                savedRotation,
            ),
            virtualButton = OverlayGeometry.toRatio(
                virtualView.pixelRect(),
                geometry,
                savedRotation,
            ),
            coordinateRotation = savedRotation,
        )
    }

    private fun updateTargetOpacityLabel() {
        if (::targetOpacityLabel.isInitialized) {
            targetOpacityLabel.text = "目标位置 ${(config.targetBlockAlpha * 100f).roundToInt()}%"
        }
    }

    private fun updateOpacityLabel() {
        if (::opacityLabel.isInitialized) {
            opacityLabel.text = "新按钮 ${(config.virtualButtonAlpha * 100f).roundToInt()}%"
        }
    }

    private fun updateSizeHint() {
        if (!::sizeHint.isInitialized) return
        val geometry = OverlayGeometry.fromWindowManager(this)
        val sameSize = (geometry.width == sourceWidth && geometry.height == sourceHeight) ||
            (geometry.width == sourceHeight && geometry.height == sourceWidth)
        sizeHint.text = if (sameSize) {
            "运行时按当前屏幕坐标发送一次 Tap"
        } else {
            "尺寸不一致：保存后启动会被拒绝"
        }
        sizeHint.setTextColor(
            if (sameSize) Color.rgb(90, 100, 114) else Color.rgb(176, 122, 26),
        )
    }

    private fun saveAndExit() {
        syncConfigFromViews()
        MappingPrefs.save(
            this,
            config.copy(
                screenshotUri = sourceUri?.toString(),
                screenshotWidth = sourceWidth,
                screenshotHeight = sourceHeight,
                configured = true,
                // coordinateRotation 由 syncConfigFromViews 按截图方向维护
                // （横屏=当前设备旋转，竖屏=ROTATION_90），这里不再覆盖，
                // 保证保存的坐标与编辑器内所见、运行时换算完全一致。
            ),
        )
        RuntimeProtection.recordEvent(
            this,
            "保存高风险布局并退出编辑器",
            "screen=${sourceWidth}x${sourceHeight}; configured=true; target=${config.targetBlock}; virtual=${config.virtualButton}",
        )
        restoreSystemBars()
        setResult(RESULT_OK)
        finish()
    }

    private fun decodeBitmap(uri: Uri, width: Int, height: Int): Bitmap? = try {
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

    private fun calculateSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (width / sample > maxSide || height / sample > maxSide) sample *= 2
        return sample
    }

    private fun finishWithMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun hideSystemBars() {
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

    private fun restoreSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        const val EXTRA_URI = "mapping_screenshot_uri"
        const val EXTRA_WIDTH = "mapping_screenshot_width"
        const val EXTRA_HEIGHT = "mapping_screenshot_height"
        private const val MAX_BITMAP_SIDE = 2400
    }
}
