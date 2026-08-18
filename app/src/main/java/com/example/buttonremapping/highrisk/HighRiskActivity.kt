package com.example.buttonremapping.highrisk

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.buttonremapping.LowRiskManager
import com.example.buttonremapping.OverlayGeometry
import com.example.buttonremapping.ProfilePanel
import com.example.buttonremapping.RuntimeProtection
import com.example.buttonremapping.RuntimeProtectionActivity
import com.example.buttonremapping.TriggerSettingsView
import com.example.buttonremapping.profile.ProfileManager
import com.example.buttonremapping.profile.ProfileMode
import rikka.shizuku.Shizuku
import kotlin.math.roundToInt

class HighRiskActivity : Activity() {
    private lateinit var inputStatus: TextView
    private lateinit var accessibilityStatus: TextView
    private lateinit var mappingStatus: TextView
    private lateinit var layoutSummary: TextView
    private lateinit var screenshotSummary: TextView
    private lateinit var highProfilePanel: ProfilePanel
    private lateinit var highStartStopButton: Button
    private lateinit var highAdvancedContent: LinearLayout
    private lateinit var highAdvancedToggle: TextView
    private var highTriggerSettings: TriggerSettingsView? = null
    private var highAdvancedExpanded = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread { refreshState() }
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread { refreshState() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RuntimeProtection.recordEvent(this, "打开修改键位模式页面")
        window.statusBarColor = Color.rgb(14, 17, 22)
        window.navigationBarColor = Color.rgb(14, 17, 22)
        setContentView(createContent())
    }

    override fun onStart() {
        super.onStart()
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
    }

    override fun onStop() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::highProfilePanel.isInitialized) highProfilePanel.refresh()
        if (::inputStatus.isInitialized) refreshState()
        // 从使用情况访问设置页返回后刷新权限状态。
        highTriggerSettings?.refresh()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SCREENSHOT) return
        if (resultCode != RESULT_OK) {
            RuntimeProtection.recordEvent(this, "取消选择高风险截图")
            return
        }
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some providers only offer a temporary read grant.
        }
        val dimensions = readImageDimensions(uri)
        if (dimensions == null) {
            RuntimeProtection.recordEvent(this, "读取高风险截图尺寸失败")
            Toast.makeText(this, "无法读取图片尺寸，请重新选择截图", Toast.LENGTH_SHORT).show()
            return
        }
        RuntimeProtection.recordEvent(this, "读取高风险截图尺寸成功", "${dimensions.first}x${dimensions.second}")
        openEditor(uri, dimensions.first, dimensions.second)
    }

    private fun createContent(): View {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(14, 17, 22))
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            setOnApplyWindowInsetsListener { view, insets ->
                var left = 0
                var top = 0
                var right = 0
                var bottom = 0
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
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

        root.addView(textView("修改键位模式", 26f, Color.rgb(244, 247, 251)))
        root.addView(textView("修改键位模式：单按钮位置映射，仅发送一次人工 Tap。", 14f, Color.rgb(170, 181, 196), 8))
        root.addView(textView(
            "该模式会生成系统级输入事件。请仅用于兼容性测试、自有应用测试或确认允许使用输入映射的应用。当前开发阶段禁止使用真实游戏测试。",
            13f,
            Color.rgb(255, 193, 107),
            18,
        ))

        highProfilePanel = ProfilePanel(
            context = this,
            mode = ProfileMode.HIGH,
            onProfileChanged = {
                restartHighRiskIfRunning()
                refreshState()
            },
            onEditNewProfile = { openEditorFromSavedConfig() },
        )

        root.addView(sectionLabel("修改键位模式环境", 28))
        inputStatus = textView("输入模块：检查中…", 14f, Color.rgb(170, 181, 196))
        accessibilityStatus = textView("无障碍：检查中…", 14f, Color.rgb(170, 181, 196), 6)
        root.addView(inputStatus)
        root.addView(accessibilityStatus)
        root.addView(actionButton("开启无障碍注入", false).apply {
            setOnClickListener {
                RuntimeProtection.recordEvent(this@HighRiskActivity, "点击开启无障碍注入")
                InputAccessibilityService.openAccessibilitySettings(this@HighRiskActivity)
            }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        root.addView(textView(
            "注入后端：当前使用无障碍（免 Root）。",
            12f,
            Color.rgb(133, 146, 164),
            top = 8,
        ))

        root.addView(sectionLabel("单按钮映射", 28))
        mappingStatus = textView("映射：未启动", 14f, Color.rgb(170, 181, 196))
        root.addView(mappingStatus)
        val config = MappingPrefs.load(this)
        layoutSummary = textView(
            if (config.configured) {
                "目标位置：已设置 · 透明度 ${(config.targetBlockAlpha * 100f).roundToInt()}%\n新按钮：已设置"
            } else {
                "目标位置：未设置\n新按钮：未设置"
            },
            14f,
            Color.rgb(170, 181, 196),
            8,
        )
        root.addView(layoutSummary)
        screenshotSummary = textView("未使用截图，编辑器将使用空白画布", 13f, Color.rgb(170, 181, 196), 8)
        root.addView(screenshotSummary)
        root.addView(actionButton("上传游戏截图", false).apply {
            setOnClickListener {
                RuntimeProtection.recordEvent(this@HighRiskActivity, "点击上传高风险截图")
                chooseScreenshot()
            }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        root.addView(actionButton("编辑按钮位置", true).apply {
            setOnClickListener {
                RuntimeProtection.recordEvent(this@HighRiskActivity, "点击编辑高风险按钮位置")
                openEditorFromSavedConfig()
            }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
        highStartStopButton = stateButton("启动映射").apply {
            setOnClickListener {
                if (HighRiskOverlayService.isRunning) {
                    RuntimeProtection.recordEvent(this@HighRiskActivity, "点击停止高风险映射")
                    stopMapping()
                } else {
                    RuntimeProtection.recordEvent(this@HighRiskActivity, "点击启动高风险映射")
                    startMapping()
                }
            }
        }
        root.addView(highStartStopButton, LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(22)
        })
        root.addView(textView(
            "运行时只显示新按钮。短按产生一次目标点击；长按、拖动和其他自动操作均不会执行。",
            12f,
            Color.rgb(133, 146, 164),
            16,
        ))

        root.addView(sectionLabel("高级", 28))
        highAdvancedToggle = textView("高级设置（点击展开）", 13f, Color.rgb(116, 167, 255), 6).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBackground(Color.rgb(22, 27, 35), Color.rgb(42, 52, 68))
            isClickable = true
            setOnClickListener { toggleAdvanced() }
        }
        root.addView(highAdvancedToggle)
        highAdvancedContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        highAdvancedContent.addView(highProfilePanel, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(12)
        })
        highAdvancedContent.addView(TriggerSettingsView(
            context = this,
            mode = ProfileMode.HIGH,
            onConfigChanged = {
                RuntimeProtection.recordEvent(this, "高风险触发配置变化")
                HighRiskOverlayService.serviceRestartTrigger()
            },
            onStartRequested = {
                // 勾选「启动指定应用监控」后自动启动服务，不再依赖手动点击启动映射。
                RuntimeProtection.recordEvent(this, "高风险指定应用监控启动请求")
                if (HighRiskOverlayService.isRunning) {
                    HighRiskOverlayService.serviceRestartTrigger()
                } else {
                    startMappingNow()
                }
            },
        ).also { highTriggerSettings = it }, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(16)
        })
        root.addView(highAdvancedContent)
        return scrollView
    }

    private fun toggleAdvanced() {
        highAdvancedExpanded = !highAdvancedExpanded
        highAdvancedContent.visibility = if (highAdvancedExpanded) View.VISIBLE else View.GONE
        highAdvancedToggle.text = if (highAdvancedExpanded) {
            "高级设置（点击收起）"
        } else {
            "高级设置（点击展开）"
        }
        RuntimeProtection.recordEvent(
            this,
            if (highAdvancedExpanded) "高级设置已展开" else "高级设置已折叠",
        )
    }

    private fun stateButton(text: String): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 15f
        stateListAnimator = null
        setPadding(dp(12), 0, dp(12), 0)
        setTextColor(Color.rgb(244, 247, 251))
        background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(Color.rgb(22, 34, 54))
            setStroke(dp(1), Color.rgb(116, 167, 255))
        }
    }

    private fun updateHighStateButton(running: Boolean) {
        if (!::highStartStopButton.isInitialized) return
        highStartStopButton.text = if (running) "正在映射，点击停止" else "启动映射"
        highStartStopButton.setTextColor(
            if (running) Color.rgb(9, 17, 28) else Color.rgb(244, 247, 251),
        )
        highStartStopButton.background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(if (running) Color.rgb(102, 217, 163) else Color.rgb(22, 34, 54))
            setStroke(
                dp(1),
                if (running) Color.rgb(102, 217, 163) else Color.rgb(116, 167, 255),
            )
        }
    }

    private fun roundedBackground(fill: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(fill)
            setStroke(dp(1), stroke)
        }

    private fun refreshState() {
        val status = HighRiskManager.getShizukuStatus(this)
        inputStatus.text = when (status.state) {
            ShizukuState.READY -> "输入模块：可用（Shizuku 单次 Tap）"
            else -> "输入模块：可用（无障碍单次 Tap）"
        }
        val accessibilityEnabled = HighRiskManager.isAccessibilityEnabled(this)
        val accessibilityConnected = HighRiskManager.isAccessibilityConnected()
        accessibilityStatus.text = when {
            accessibilityEnabled && accessibilityConnected -> "无障碍：已连接（可注入）"
            accessibilityEnabled -> "无障碍：已开启（等待连接）"
            else -> "无障碍：未开启"
        }
        accessibilityStatus.setTextColor(
            if (accessibilityEnabled && accessibilityConnected) {
                Color.rgb(102, 217, 163)
            } else {
                Color.rgb(255, 193, 107)
            },
        )
        val config = MappingPrefs.load(this)
        layoutSummary.text = if (config.configured) {
            "目标位置：已设置 · 透明度 ${(config.targetBlockAlpha * 100f).roundToInt()}%\n新按钮：已设置"
        } else {
            "目标位置：未设置\n新按钮：未设置"
        }
        screenshotSummary.text = if (config.hasScreenshot) {
            "已上传截图：${config.screenshotWidth} × ${config.screenshotHeight}"
        } else {
            "未使用截图，编辑器将使用空白画布"
        }
        val profileName = ProfileManager.currentName(this, ProfileMode.HIGH)
        mappingStatus.text = if (HighRiskOverlayService.isRunning) {
            "映射：正在运行 · $profileName"
        } else {
            "映射：未启动 · $profileName"
        }
        updateHighStateButton(HighRiskOverlayService.isRunning)
    }

    private fun requestShizukuPermission() {
        when (HighRiskManager.getShizukuStatus(this).state) {
            ShizukuState.NOT_INSTALLED -> {
                RuntimeProtection.recordEvent(this, "Shizuku 授权失败：未安装")
                Toast.makeText(this, "请先安装官方 Shizuku", Toast.LENGTH_LONG).show()
            }
            ShizukuState.NOT_RUNNING -> {
                RuntimeProtection.recordEvent(this, "Shizuku 授权失败：服务未运行")
                Toast.makeText(this, "请先启动 Shizuku 服务", Toast.LENGTH_LONG).show()
            }
            ShizukuState.READY -> refreshState()
            else -> HighRiskManager.requestPermission(this) { refreshState() }
        }
    }

    private fun openShizuku() {
        if (HighRiskManager.getShizukuStatus(this).state == ShizukuState.NOT_INSTALLED) {
            RuntimeProtection.recordEvent(this, "打开 Shizuku 失败：未安装")
            Toast.makeText(this, "当前设备未安装 Shizuku，请从官方渠道安装", Toast.LENGTH_LONG).show()
            return
        }
        try {
            HighRiskManager.openShizuku(this)
        } catch (_: Exception) {
            RuntimeProtection.recordEvent(this, "打开 Shizuku 失败：无法启动")
            Toast.makeText(this, "无法打开 Shizuku", Toast.LENGTH_SHORT).show()
        }
    }

    private fun chooseScreenshot() {
        RuntimeProtection.recordEvent(this, "打开高风险截图选择器")
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_SCREENSHOT)
    }

    private fun openEditorFromSavedConfig() {
        RuntimeProtection.recordEvent(this, "准备打开高风险布局编辑器")
        val config = MappingPrefs.load(this)
        if (config.hasScreenshot) {
            openEditor(Uri.parse(config.screenshotUri), config.screenshotWidth, config.screenshotHeight)
        } else {
            val geometry = OverlayGeometry.fromWindowManager(this)
            openEditor(null, geometry.width, geometry.height)
        }
    }

    private fun openEditor(uri: Uri?, width: Int, height: Int) {
        RuntimeProtection.recordEvent(this, "打开高风险布局编辑器", "${width}x${height}")
        LowRiskManager.stop(this)
        HighRiskManager.stop(this)
        startActivity(
            Intent(this, HighRiskEditorActivity::class.java).apply {
                putExtra(HighRiskEditorActivity.EXTRA_URI, uri?.toString().orEmpty())
                putExtra(HighRiskEditorActivity.EXTRA_WIDTH, width)
                putExtra(HighRiskEditorActivity.EXTRA_HEIGHT, height)
            },
        )
    }

    private fun startMapping() {
        RuntimeProtection.recordEvent(this, "检查高风险映射启动条件")
        val config = MappingPrefs.load(this)
        if (!config.configured) {
            RuntimeProtection.recordEvent(this, "高风险映射启动阻止：布局未配置")
            Toast.makeText(this, "请先编辑并保存按钮位置", Toast.LENGTH_SHORT).show()
            return
        }
        val runtimeStatus = RuntimeProtection.inspect(this)
        if (!runtimeStatus.overlayGranted) {
            RuntimeProtection.recordEvent(this, "高风险映射启动阻止：悬浮窗权限未开启")
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
        if (runtimeStatus.hasOptionalWarnings) {
            RuntimeProtection.recordEvent(this, "高风险映射启动提示：存在后台运行设置风险")
            AlertDialog.Builder(this)
                .setTitle("后台运行设置有风险")
                .setMessage("当前通知或电池后台设置可能导致映射悬浮按钮被系统关闭。")
                .setNegativeButton("检查设置") { _, _ ->
                    startActivity(Intent(this, RuntimeProtectionActivity::class.java))
                }
                .setPositiveButton("继续启动") { _, _ -> startMappingNow() }
                .show()
            return
        }
        startMappingNow()
    }

    private fun startMappingNow() {
        RuntimeProtection.recordEvent(this, "请求启动高风险映射")
        val shizukuReady = HighRiskManager.getShizukuStatus(this).state == ShizukuState.READY
        val accessibilityReady = HighRiskManager.isAccessibilityConnected()
        if (!shizukuReady && !accessibilityReady) {
            RuntimeProtection.recordEvent(
                this,
                "高风险映射启动阻止：注入后端不可用",
                "shizuku=${HighRiskManager.getShizukuStatus(this).state}; accessibility=$accessibilityReady",
            )
            Toast.makeText(
                this,
                "无可用注入后端：请开启无障碍注入或 Shizuku",
                Toast.LENGTH_LONG,
            ).show()
            refreshState()
            return
        }
        com.example.buttonremapping.ModeExclusive.startHighRisk(this)
        mappingStatus.text = "映射：正在启动…"
        // 立即反映按钮状态，无需等待 onResume。
        updateHighStateButton(running = true)
    }

    private fun stopMapping() {
        RuntimeProtection.recordEvent(this, "请求停止高风险映射")
        HighRiskManager.stop(this)
        updateHighStateButton(running = false)
        refreshState()
    }

    private fun restartHighRiskIfRunning() {
        if (!HighRiskOverlayService.isRunning) return
        HighRiskManager.stop(this)
        val shizukuReady = HighRiskManager.getShizukuStatus(this).state == ShizukuState.READY
        val accessibilityReady = HighRiskManager.isAccessibilityConnected()
        if (shizukuReady || accessibilityReady) {
            HighRiskManager.start(this)
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

    private fun sectionLabel(text: String, top: Int): TextView = textView(text, 13f, Color.rgb(116, 167, 255), top)

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
        textSize = 15f
        setTextColor(if (primary) Color.rgb(9, 17, 28) else Color.rgb(244, 247, 251))
        background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(if (primary) Color.rgb(116, 167, 255) else Color.rgb(32, 41, 56))
            setStroke(dp(1), if (primary) Color.rgb(116, 167, 255) else Color.rgb(64, 80, 104))
        }
        stateListAnimator = null
        setPadding(dp(12), 0, dp(12), 0)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val REQUEST_SCREENSHOT = 4003
    }
}
