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
import android.provider.Settings
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
 * 屏蔽区域模式页面：截图辅助设置一个屏蔽区域，悬浮开关可临时恢复触摸。
 *
 * 独立 Activity，与长按触发/修改键位两个模式一样从主页通过 startActivity 进入，
 * 由系统提供与中/高风险一致的水平推入过渡动画（返回时同样有反向过渡）。
 */
class LowRiskActivity : Activity() {
    private lateinit var overlayRuntimeStatus: TextView
    private lateinit var layoutSummary: TextView
    private lateinit var screenshotSummary: TextView
    private lateinit var lowProfilePanel: ProfilePanel
    private lateinit var lowStartStopButton: Button
    private lateinit var advancedContent: LinearLayout
    private lateinit var advancedToggle: TextView
    private var lowTriggerSettings: TriggerSettingsView? = null
    private var advancedExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RuntimeProtection.recordEvent(this, "打开屏蔽区域模式页面")
        window.statusBarColor = Color.rgb(247, 248, 250)
        window.navigationBarColor = Color.rgb(247, 248, 250)
        setContentView(createContent())
    }

    override fun onResume() {
        super.onResume()
        if (::lowProfilePanel.isInitialized) lowProfilePanel.refresh()
        refreshState()
        // 从使用情况访问设置页返回后刷新权限状态。
        lowTriggerSettings?.refresh()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SCREENSHOT) return
        if (resultCode != RESULT_OK) {
            RuntimeProtection.recordEvent(this, "取消选择游戏截图")
            return
        }
        val uri = data?.data ?: return
        RuntimeProtection.recordEvent(this, "选择游戏截图完成")
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Some providers grant a temporary read permission only.
        }
        val dimensions = readImageDimensions(uri)
        if (dimensions == null) {
            RuntimeProtection.recordEvent(this, "读取游戏截图尺寸失败")
            Toast.makeText(this, "无法读取图片尺寸，请重新选择截图", Toast.LENGTH_SHORT).show()
            return
        }
        RuntimeProtection.recordEvent(this, "读取游戏截图尺寸成功", "${dimensions.first}x${dimensions.second}")
        startScreenshotEditor(uri, dimensions.first, dimensions.second)
    }

    private fun createContent(): View {
        val scrollView = baseScrollView()
        val root = baseRoot()
        lowProfilePanel = ProfilePanel(
            context = this,
            mode = ProfileMode.LOW,
            onProfileChanged = {
                restartLowRiskIfRunning()
                refreshState()
            },
            onEditNewProfile = { openEditor() },
        )
        scrollView.setOnApplyWindowInsetsListener { _, insets ->
            val systemInsets = readSystemInsets(insets)
            root.setPadding(
                dp(24) + systemInsets.left,
                dp(28) + systemInsets.top,
                dp(24) + systemInsets.right,
                dp(28) + systemInsets.bottom,
            )
            insets
        }
        scrollView.addView(root, LinearLayout.LayoutParams(-1, -2))

        root.addView(textView("屏蔽区域模式", 26f, Color.rgb(26, 31, 39)))
        root.addView(textView(
            "屏蔽区域模式：截图辅助设置一个屏蔽区域，悬浮开关可临时恢复触摸。",
            14f,
            Color.rgb(90, 100, 114),
            top = 8,
        ))

        root.addView(sectionLabel("游戏截图布局"), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(28)
        })
        screenshotSummary = textView("未上传游戏截图", 14f, Color.rgb(90, 100, 114))
        root.addView(screenshotSummary)
        val uploadEditRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        uploadEditRow.addView(actionButton("上传截图", primary = true).apply {
            setOnClickListener {
                RuntimeProtection.recordEvent(this@LowRiskActivity, "点击上传游戏截图")
                chooseScreenshot()
            }
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        uploadEditRow.addView(actionButton("编辑屏蔽区", primary = false).apply {
            setOnClickListener {
                RuntimeProtection.recordEvent(this@LowRiskActivity, "点击编辑屏蔽区域")
                openEditor()
            }
        }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(10) })
        root.addView(uploadEditRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        root.addView(sectionLabel("当前屏蔽区域"), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(28)
        })
        val layoutBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground(Color.rgb(255, 255, 255), Color.rgb(226, 230, 236))
        }
        layoutBox.addView(textView("已设置", 17f, Color.rgb(26, 31, 39)))
        layoutSummary = textView("读取布局中…", 13f, Color.rgb(90, 100, 114), top = 7)
        layoutBox.addView(layoutSummary)
        root.addView(layoutBox, LinearLayout.LayoutParams(-1, -2))

        root.addView(sectionLabel("悬浮屏蔽开关"), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(28)
        })
        overlayRuntimeStatus = textView("屏蔽：未启动", 14f, Color.rgb(90, 100, 114))
        root.addView(overlayRuntimeStatus)
        lowStartStopButton = stateButton("启动屏蔽").apply {
            setOnClickListener {
                if (OverlayService.isRunning) {
                    RuntimeProtection.recordEvent(this@LowRiskActivity, "点击停止屏蔽")
                    stopOverlay()
                } else {
                    RuntimeProtection.recordEvent(this@LowRiskActivity, "点击启动屏蔽")
                    startOverlay()
                }
            }
        }
        root.addView(lowStartStopButton, LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(28)
        })
        root.addView(textView(
            "运行中：红色开关表示已屏蔽，绿色表示当前允许点击。长按悬浮开关可拖动位置。",
            12f,
            Color.rgb(122, 132, 148),
            top = 18,
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
        advancedContent.addView(lowProfilePanel, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(12)
        })
        advancedContent.addView(createToggleAlphaSection(), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(16)
        })
        advancedContent.addView(TriggerSettingsView(
            context = this,
            mode = ProfileMode.LOW,
            onConfigChanged = {
                RuntimeProtection.recordEvent(this, "低风险触发配置变化")
                OverlayService.serviceRestartTrigger()
            },
            onStartRequested = {
                // 勾选「启动指定应用监控」后自动启动服务，不再依赖手动点击启动屏蔽。
                RuntimeProtection.recordEvent(this, "指定应用监控启动请求")
                if (OverlayService.isRunning) {
                    OverlayService.serviceRestartTrigger()
                } else {
                    startOverlayNow()
                }
            },
        ).also { lowTriggerSettings = it }, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(16)
        })
        root.addView(advancedContent)

        return scrollView
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
            if (advancedExpanded) "高级设置已展开" else "高级设置已折叠",
        )
    }

    /**
     * 悬浮开关（红“屏蔽”/绿“允许”）透明度调节。
     */
    private fun createToggleAlphaSection(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBackground(Color.rgb(255, 255, 255), Color.rgb(226, 230, 236))
        }
        val config = LayoutPrefs.load(this)
        box.addView(textView("悬浮开关透明度", 15f, Color.rgb(26, 31, 39)))
        val percentView = textView(
            "${(config.toggleAlpha * 100f).roundToInt()}%",
            13f,
            Color.rgb(90, 100, 114),
            top = 6,
        )
        box.addView(percentView)
        val seekBar = android.widget.SeekBar(this).apply {
            max = 80
            progress = ((config.toggleAlpha - 0.2f) / 0.8f * 80f).roundToInt()
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    val alpha = 0.2f + progress / 80f * 0.8f
                    percentView.text = "${(alpha * 100f).roundToInt()}%"
                    // 拖动时实时更新运行中的悬浮开关透明度，无需重启屏蔽。
                    OverlayService.setToggleAlpha(alpha)
                    if (fromUser) {
                        val current = LayoutPrefs.load(this@LowRiskActivity)
                        LayoutPrefs.save(
                            this@LowRiskActivity,
                            current.copy(toggleAlpha = alpha),
                        )
                        RuntimeProtection.recordEvent(
                            this@LowRiskActivity,
                            "修改悬浮开关透明度",
                            "${(alpha * 100f).roundToInt()}%",
                        )
                    }
                }

                override fun onStartTrackingTouch(seek: android.widget.SeekBar?) = Unit
                override fun onStopTrackingTouch(seek: android.widget.SeekBar?) = Unit
            })
        }
        box.addView(seekBar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        box.addView(textView(
            "调节红色“屏蔽”/绿色“允许”悬浮开关的透明度（20%–100%），拖动即时生效。",
            12f,
            Color.rgb(122, 132, 148),
            top = 8,
        ))
        return box
    }

    /**
     * 单状态启动/停止按钮：未运行时蓝色“启动…”，运行时绿色“正在…，点击停止”。
     */
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
        if (!::lowStartStopButton.isInitialized) return
        lowStartStopButton.text = if (running) "正在屏蔽，点击停止" else "启动屏蔽"
        lowStartStopButton.setTextColor(
            if (running) Color.rgb(9, 17, 28) else Color.rgb(26, 31, 39),
        )
        lowStartStopButton.background = roundedBackground(
            if (running) Color.rgb(102, 217, 163) else Color.rgb(226, 234, 246),
            if (running) Color.rgb(102, 217, 163) else Color.rgb(116, 167, 255),
        )
    }

    private fun refreshState() {
        if (!::layoutSummary.isInitialized) return
        val profileName = ProfileManager.currentName(this, ProfileMode.LOW)
        overlayRuntimeStatus.text = when {
            !OverlayService.isRunning -> "屏蔽：未启动"
            OverlayService.isBlocked -> "屏蔽：已启动 · 当前已屏蔽"
            else -> "屏蔽：已启动 · 当前允许点击"
        }.let { "$profileName · $it" }
        updateStateButton(OverlayService.isRunning)
        updateLayoutSummary()
    }

    private fun updateLayoutSummary() {
        val config = LayoutPrefs.load(this)
        layoutSummary.text = "方案：${ProfileManager.currentName(this, ProfileMode.LOW)}\n" + getString(
            R.string.blocked_layout_summary,
            percentValue(config.blockedArea.widthRatio),
            percentValue(config.blockedArea.heightRatio),
            percentValue(config.blockedAreaAlpha),
        )
        updateScreenshotSummary()
    }

    private fun openEditor() {
        RuntimeProtection.recordEvent(this, "打开低风险布局编辑器")
        LowRiskManager.stop(this)
        val config = LayoutPrefs.load(this)
        if (config.hasScreenshot) {
            startScreenshotEditor(
                Uri.parse(config.screenshotUri),
                config.screenshotWidth,
                config.screenshotHeight,
            )
        } else {
            startActivity(Intent(this, EditorActivity::class.java))
        }
    }

    private fun chooseScreenshot() {
        RuntimeProtection.recordEvent(this, "打开游戏截图选择器")
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, REQUEST_SCREENSHOT)
        } catch (_: Exception) {
            RuntimeProtection.recordEvent(this, "打开游戏截图选择器失败")
            Toast.makeText(this, "系统文件选择器不可用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startScreenshotEditor(uri: Uri, width: Int, height: Int) {
        RuntimeProtection.recordEvent(this, "打开截图布局编辑器", "${width}x${height}")
        startActivity(
            Intent(this, ScreenshotEditorActivity::class.java).apply {
                putExtra(ScreenshotEditorActivity.EXTRA_URI, uri.toString())
                putExtra(ScreenshotEditorActivity.EXTRA_WIDTH, width)
                putExtra(ScreenshotEditorActivity.EXTRA_HEIGHT, height)
            },
        )
    }

    private fun readImageDimensions(uri: Uri): Pair<Int, Int>? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }
            if (options.outWidth > 0 && options.outHeight > 0) {
                options.outWidth to options.outHeight
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun updateScreenshotSummary() {
        if (!::screenshotSummary.isInitialized) return
        val config = LayoutPrefs.load(this)
        if (!config.hasScreenshot) {
            screenshotSummary.text = "未上传游戏截图"
            screenshotSummary.setTextColor(Color.rgb(90, 100, 114))
            return
        }
        val geometry = OverlayGeometry.fromWindowManager(this)
        val sameSize = isSameDisplaySize(
            geometry.width,
            geometry.height,
            config.screenshotWidth,
            config.screenshotHeight,
        )
        screenshotSummary.text = if (sameSize) {
            "已上传：${config.screenshotWidth} × ${config.screenshotHeight} · 本机截图尺寸"
        } else {
            "已上传：${config.screenshotWidth} × ${config.screenshotHeight}\n截图尺寸与当前屏幕不同，请重新截图"
        }
        screenshotSummary.setTextColor(
            if (sameSize) Color.rgb(90, 100, 114) else Color.rgb(176, 122, 26),
        )
    }

    private fun isSameDisplaySize(
        displayWidth: Int,
        displayHeight: Int,
        screenshotWidth: Int,
        screenshotHeight: Int,
    ): Boolean = (displayWidth == screenshotWidth && displayHeight == screenshotHeight) ||
        (displayWidth == screenshotHeight && displayHeight == screenshotWidth)

    private fun startOverlay() {
        RuntimeProtection.recordEvent(this, "检查低风险启动条件")
        val status = RuntimeProtection.inspect(this)
        if (!status.overlayGranted) {
            RuntimeProtection.recordEvent(this, "低风险启动阻止：悬浮窗权限未开启")
            AlertDialog.Builder(this)
                .setTitle("无法启动")
                .setMessage("请先开启悬浮窗权限。权限与运行保障页面也会显示通知和后台运行状态。")
                .setNegativeButton("取消", null)
                .setPositiveButton("去设置") { _, _ -> openRuntimeProtection() }
                .show()
            return
        }
        if (status.hasOptionalWarnings) {
            RuntimeProtection.recordEvent(this, "低风险启动提示：存在后台运行设置风险")
            AlertDialog.Builder(this)
                .setTitle("后台运行设置有风险")
                .setMessage("当前通知或电池后台设置可能导致悬浮窗被系统关闭。")
                .setNegativeButton("检查设置") { _, _ -> openRuntimeProtection() }
                .setPositiveButton("继续启动") { _, _ -> startOverlayNow() }
                .show()
        } else {
            startOverlayNow()
        }
    }

    private fun startOverlayNow() {
        RuntimeProtection.recordEvent(this, "请求启动低风险悬浮层")
        ModeExclusive.startLowRisk(this)
        overlayRuntimeStatus.text = "屏蔽：正在启动…"
        // 立即反映按钮状态，无需等待 onResume。
        updateStateButton(running = true)
    }

    private fun stopOverlay() {
        RuntimeProtection.recordEvent(this, "请求停止低风险悬浮层")
        LowRiskManager.stop(this)
        if (::overlayRuntimeStatus.isInitialized) {
            overlayRuntimeStatus.text = "屏蔽：已停止"
        }
        updateStateButton(running = false)
    }

    private fun restartLowRiskIfRunning() {
        if (!OverlayService.isRunning) return
        RuntimeProtection.recordEvent(this, "当前方案变化，重启低风险悬浮层")
        LowRiskManager.stop(this)
        if (Settings.canDrawOverlays(this)) LowRiskManager.start(this)
    }

    private fun openRuntimeProtection() {
        RuntimeProtection.recordEvent(this, "打开权限与运行保障页面")
        startActivity(Intent(this, RuntimeProtectionActivity::class.java))
    }

    private fun baseScrollView(): ScrollView = ScrollView(this).apply {
        setBackgroundColor(Color.rgb(247, 248, 250))
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
    }

    private fun baseRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(28), dp(24), dp(28))
    }

    private fun readSystemInsets(insets: WindowInsets): InsetsPx {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val values = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return InsetsPx(values.left, values.top, values.right, values.bottom)
        }
        @Suppress("DEPRECATION")
        return InsetsPx(
            insets.systemWindowInsetLeft,
            insets.systemWindowInsetTop,
            insets.systemWindowInsetRight,
            insets.systemWindowInsetBottom,
        )
    }

    private fun sectionLabel(text: String): TextView =
        textView(text, 13f, Color.rgb(116, 167, 255), top = 0)

    private fun textView(
        text: String,
        size: Float,
        color: Int,
        top: Int = 0,
    ): TextView = TextView(this).apply {
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
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(fill)
            setStroke(dp(1), stroke)
        }

    private fun percentValue(value: Float): Int = (value * 100f).roundToInt()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val REQUEST_SCREENSHOT = 2001
    }
}
