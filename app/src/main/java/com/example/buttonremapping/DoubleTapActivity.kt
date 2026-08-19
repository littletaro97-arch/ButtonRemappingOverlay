package com.example.buttonremapping

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.buttonremapping.profile.ProfileManager
import com.example.buttonremapping.profile.ProfileMode
import kotlin.math.roundToInt

/**
 * 双击触发模式页面：上传截图、编辑双击区域、启动/停止、高级（方案管理/指定应用启动）。
 */
class DoubleTapActivity : Activity() {
    private lateinit var areaSummary: TextView
    private lateinit var screenshotSummary: TextView
    private lateinit var startStopButton: Button
    private lateinit var profilePanel: ProfilePanel
    private lateinit var advancedContent: LinearLayout
    private lateinit var advancedToggle: TextView
    private var advancedExpanded = false
    private var triggerSettings: TriggerSettingsView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RuntimeProtection.recordEvent(this, "打开双击触发模式页面")
        window.statusBarColor = Color.rgb(247, 248, 250)
        window.navigationBarColor = Color.rgb(247, 248, 250)
        setContentView(createContent())
    }

    override fun onResume() {
        super.onResume()
        if (::profilePanel.isInitialized) profilePanel.refresh()
        if (::areaSummary.isInitialized) refreshState()
        triggerSettings?.refresh()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SCREENSHOT) return
        if (resultCode != RESULT_OK) {
            RuntimeProtection.recordEvent(this, "取消选择双击触发截图")
            return
        }
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }
        val dimensions = readImageDimensions(uri)
        if (dimensions == null) {
            RuntimeProtection.recordEvent(this, "读取双击触发截图尺寸失败")
            Toast.makeText(this, "无法读取图片尺寸，请重新选择截图", Toast.LENGTH_SHORT).show()
            return
        }
        RuntimeProtection.recordEvent(this, "读取双击触发截图尺寸成功", "${dimensions.first}x${dimensions.second}")
        openEditor(uri, dimensions.first, dimensions.second)
    }

    override fun onBackPressed() {
        finish()
    }

    private fun createContent(): View {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(247, 248, 250))
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            setOnApplyWindowInsetsListener { view, insets ->
                var left = 0
                var top = 0
                var right = 0
                var bottom = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val values = insets.getInsets(WindowInsets.Type.systemBars())
                    left = values.left
                    top = values.top
                    right = values.right
                    bottom = values.bottom
                } else {
                    @Suppress("DEPRECATION")
                    run {
                        left = insets.systemWindowInsetLeft
                        top = insets.systemWindowInsetTop
                        right = insets.systemWindowInsetRight
                        bottom = insets.systemWindowInsetBottom
                    }
                }
                view.setPadding(dp(24) + left, dp(28) + top, dp(24) + right, dp(28) + bottom)
                insets
            }
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(root, LinearLayout.LayoutParams(-1, -2))

        root.addView(textView("双击触发模式", 26f, Color.rgb(26, 31, 39)))
        root.addView(textView(
            "将单击触发的按钮改为双击触发，防止误触。",
            14f,
            Color.rgb(90, 100, 114),
            top = 8,
        ))
        root.addView(textView(
            "低风险方案：需要无障碍（或 Shizuku）注入权限。仅在双击后注入一次点击，不连点、不自动触发。",
            13f,
            Color.rgb(24, 130, 90),
            top = 14,
        ))

        root.addView(sectionLabel("双击触发区域"), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(28)
        })
        screenshotSummary = textView("未上传游戏截图", 14f, Color.rgb(90, 100, 114))
        root.addView(screenshotSummary)
        val uploadEditRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        uploadEditRow.addView(actionButton("上传截图", true).apply {
            setOnClickListener {
                RuntimeProtection.recordEvent(this@DoubleTapActivity, "点击上传双击触发截图")
                chooseScreenshot()
            }
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        uploadEditRow.addView(actionButton("编辑双击区", false).apply {
            setOnClickListener {
                RuntimeProtection.recordEvent(this@DoubleTapActivity, "点击编辑双击区域")
                openEditorFromSavedConfig()
            }
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(10) })
        root.addView(uploadEditRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        val areaBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground(Color.rgb(255, 255, 255), Color.rgb(226, 230, 236))
        }
        areaBox.addView(textView("当前双击区域", 17f, Color.rgb(26, 31, 39)))
        areaSummary = textView("读取布局中…", 13f, Color.rgb(90, 100, 114), top = 7)
        areaBox.addView(areaSummary)
        root.addView(areaBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

        root.addView(sectionLabel("运行控制"), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(28)
        })
        startStopButton = stateButton("启动双击触发").apply {
            setOnClickListener {
                if (DoubleTapOverlayService.isRunning) {
                    RuntimeProtection.recordEvent(this@DoubleTapActivity, "点击停止双击触发")
                    stopService()
                } else {
                    RuntimeProtection.recordEvent(this@DoubleTapActivity, "点击启动双击触发")
                    startService()
                }
            }
        }
        root.addView(startStopButton, LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(10)
        })
        root.addView(textView(
            "双击区域可完全透明（区域不可见）或半透明（可见区域）。单击被吃掉，双击满时注入一次点击。",
            12f,
            Color.rgb(122, 132, 148),
            top = 16,
        ))

        root.addView(sectionLabel("高级"), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(28)
        })
        advancedToggle = textView("高级设置（点击展开）", 13f, Color.rgb(116, 167, 255), 6).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBackground(Color.rgb(255, 255, 255), Color.rgb(226, 230, 236))
            isClickable = true
            setOnClickListener { toggleAdvanced() }
        }
        root.addView(advancedToggle)
        advancedContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        profilePanel = ProfilePanel(
            context = this,
            mode = ProfileMode.DOUBLE,
            onProfileChanged = {
                restartIfRunning()
                refreshState()
            },
            onEditNewProfile = { openEditorFromSavedConfig() },
        )
        advancedContent.addView(profilePanel, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(12)
        })
        advancedContent.addView(TriggerSettingsView(
            context = this,
            mode = ProfileMode.DOUBLE,
            onConfigChanged = {
                RuntimeProtection.recordEvent(this, "双击触发配置变化")
                DoubleTapOverlayService.serviceRestartTrigger()
            },
            onStartRequested = {
                RuntimeProtection.recordEvent(this, "双击触发指定应用监控启动请求")
                if (DoubleTapOverlayService.isRunning) {
                    DoubleTapOverlayService.serviceRestartTrigger()
                } else {
                    startService()
                }
            },
        ).also { triggerSettings = it }, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(16)
        })
        root.addView(advancedContent)

        refreshState()
        return scrollView
    }

    private fun refreshState() {
        if (!::areaSummary.isInitialized) return
        val config = DoubleTapPrefs.load(this)
        val profileName = ProfileManager.currentName(this, ProfileMode.DOUBLE)
        areaSummary.text = "方案：$profileName\n" +
            "区域 ${(config.area.widthRatio * 100f).roundToInt()}% × " +
            "${(config.area.heightRatio * 100f).roundToInt()}% · 透明度 " +
            "${(config.areaAlpha * 100f).roundToInt()}%"
        updateStateButton(DoubleTapOverlayService.isRunning)
        updateScreenshotSummary()
    }

    private fun updateScreenshotSummary() {
        if (!::screenshotSummary.isInitialized) return
        val config = DoubleTapPrefs.load(this)
        if (!config.hasScreenshot) {
            screenshotSummary.text = "未上传游戏截图"
            screenshotSummary.setTextColor(Color.rgb(90, 100, 114))
            return
        }
        val geometry = OverlayGeometry.fromWindowManager(this)
        val sameSize = (geometry.width == config.screenshotWidth && geometry.height == config.screenshotHeight) ||
            (geometry.width == config.screenshotHeight && geometry.height == config.screenshotWidth)
        screenshotSummary.text = if (sameSize) {
            "已上传：${config.screenshotWidth} × ${config.screenshotHeight} · 本机截图尺寸"
        } else {
            "已上传：${config.screenshotWidth} × ${config.screenshotHeight}\n截图尺寸与当前屏幕不同，请重新截图"
        }
        screenshotSummary.setTextColor(
            if (sameSize) Color.rgb(90, 100, 114) else Color.rgb(176, 122, 26),
        )
    }

    private fun chooseScreenshot() {
        RuntimeProtection.recordEvent(this, "打开双击触发截图选择器")
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, REQUEST_SCREENSHOT)
        } catch (_: Exception) {
            RuntimeProtection.recordEvent(this, "打开双击触发截图选择器失败")
            Toast.makeText(this, "系统文件选择器不可用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun readImageDimensions(uri: Uri): Pair<Int, Int>? = try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
        if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
    } catch (_: Exception) {
        null
    }

    private fun openEditor(uri: Uri, width: Int, height: Int) {
        RuntimeProtection.recordEvent(this, "打开双击触发编辑器", "${width}x${height}")
        stopService()
        startActivity(
            Intent(this, DoubleTapEditorActivity::class.java).apply {
                putExtra(DoubleTapEditorActivity.EXTRA_URI, uri.toString())
                putExtra(DoubleTapEditorActivity.EXTRA_WIDTH, width)
                putExtra(DoubleTapEditorActivity.EXTRA_HEIGHT, height)
            },
        )
    }

    private fun openEditorFromSavedConfig() {
        RuntimeProtection.recordEvent(this, "准备打开双击触发编辑器")
        val config = DoubleTapPrefs.load(this)
        if (config.hasScreenshot) {
            openEditor(Uri.parse(config.screenshotUri), config.screenshotWidth, config.screenshotHeight)
        } else {
            val uri = config.screenshotUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            if (uri != null) {
                openEditor(uri, config.screenshotWidth, config.screenshotHeight)
            } else {
                Toast.makeText(this, "请先上传游戏截图", Toast.LENGTH_SHORT).show()
                RuntimeProtection.recordEvent(this, "打开双击触发编辑器失败：无截图")
            }
        }
    }

    private fun startService() {
        RuntimeProtection.recordEvent(this, "检查双击触发启动条件")
        val runtimeStatus = RuntimeProtection.inspect(this)
        if (!runtimeStatus.overlayGranted) {
            RuntimeProtection.recordEvent(this, "双击触发启动阻止：悬浮窗权限未开启")
            AlertDialog.Builder(this)
                .setTitle("无法启动")
                .setMessage("请先开启悬浮窗权限。")
                .setNegativeButton("取消", null)
                .setPositiveButton("去设置") { _, _ ->
                    startActivity(Intent(this, RuntimeProtectionActivity::class.java))
                }
                .show()
            return
        }
        val config = DoubleTapPrefs.load(this)
        if (!config.hasScreenshot) {
            RuntimeProtection.recordEvent(this, "双击触发启动阻止：未上传截图")
            Toast.makeText(this, "请先上传并编辑游戏截图", Toast.LENGTH_SHORT).show()
            return
        }
        val shizukuReady = com.example.buttonremapping.highrisk.HighRiskManager
            .getShizukuStatus(this).state == com.example.buttonremapping.highrisk.ShizukuState.READY
        if (!shizukuReady && !com.example.buttonremapping.highrisk.InputAccessibilityService.isConnected) {
            RuntimeProtection.recordEvent(this, "双击触发启动阻止：无注入后端")
            AlertDialog.Builder(this)
                .setTitle("无法启动")
                .setMessage("需要无障碍（或 Shizuku）注入权限才能触发双击点击。请开启无障碍注入或 Shizuku。")
                .setNegativeButton("取消", null)
                .setPositiveButton("去开启无障碍") { _, _ ->
                    com.example.buttonremapping.highrisk.InputAccessibilityService
                        .openAccessibilitySettings(this)
                }
                .show()
            return
        }
        startServiceNow()
    }

    private fun startServiceNow() {
        RuntimeProtection.recordEvent(this, "请求启动双击触发服务")
        ModeExclusive.startDoubleTap(this)
        updateStateButton(true)
    }

    private fun stopService() {
        RuntimeProtection.recordEvent(this, "请求停止双击触发服务")
        DoubleTapOverlayService.markUserStop()
        DoubleTapOverlayService.isRunning = false
        stopService(Intent(this, DoubleTapOverlayService::class.java))
        updateStateButton(false)
    }

    private fun restartIfRunning() {
        if (!DoubleTapOverlayService.isRunning) return
        stopService()
        val config = DoubleTapPrefs.load(this)
        if (config.hasScreenshot) startServiceNow()
    }

    private fun toggleAdvanced() {
        advancedExpanded = !advancedExpanded
        advancedContent.visibility = if (advancedExpanded) View.VISIBLE else View.GONE
        advancedToggle.text = if (advancedExpanded) {
            "高级设置（点击收起）"
        } else {
            "高级设置（点击展开）"
        }
        RuntimeProtection.recordEvent(
            this,
            if (advancedExpanded) "双击触发高级设置已展开" else "双击触发高级设置已折叠",
        )
    }

    private fun stateButton(text: String): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 15f
        stateListAnimator = null
        setPadding(dp(12), 0, dp(12), 0)
        setTextColor(Color.rgb(26, 31, 39))
        background = roundedBackground(Color.rgb(226, 234, 246), Color.rgb(116, 167, 255))
    }

    private fun updateStateButton(running: Boolean) {
        if (!::startStopButton.isInitialized) return
        startStopButton.text = if (running) "双击触发运行中，点击停止" else "启动双击触发"
        startStopButton.setTextColor(
            if (running) Color.rgb(9, 17, 28) else Color.rgb(26, 31, 39),
        )
        startStopButton.background = roundedBackground(
            if (running) Color.rgb(102, 217, 163) else Color.rgb(226, 234, 246),
            if (running) Color.rgb(102, 217, 163) else Color.rgb(116, 167, 255),
        )
    }

    private fun sectionLabel(text: String): TextView =
        textView(text, 13f, Color.rgb(116, 167, 255), top = 0)

    private fun textView(text: String, size: Float, color: Int, top: Int = 0): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            setLineSpacing(0f, 1.08f)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
        }

    private fun actionButton(text: String, primary: Boolean): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        maxLines = 1
        textSize = 15f
        setTextColor(if (primary) Color.rgb(9, 17, 28) else Color.rgb(26, 31, 39))
        background = roundedBackground(
            if (primary) Color.rgb(116, 167, 255) else Color.rgb(232, 236, 242),
            if (primary) Color.rgb(116, 167, 255) else Color.rgb(206, 212, 222),
        )
        stateListAnimator = null
        setPadding(dp(12), 0, dp(12), 0)
    }

    private fun roundedBackground(fill: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(fill)
            setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val REQUEST_SCREENSHOT = 5201
    }
}
