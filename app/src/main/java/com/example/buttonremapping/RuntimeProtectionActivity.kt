package com.example.buttonremapping

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

class RuntimeProtectionActivity : Activity() {
    private lateinit var summaryView: TextView
    private lateinit var vendorNotice: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var notificationStatus: TextView
    private lateinit var batteryStatus: TextView
    private lateinit var diagnosticsView: TextView
    private lateinit var diagnosticsContent: LinearLayout
    private lateinit var diagnosticsToggle: TextView
    private lateinit var notificationButton: Button
    private var diagnosticsExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RuntimeProtection.recordEvent(this, "打开权限与运行保障")
        window.statusBarColor = Color.rgb(14, 17, 22)
        window.navigationBarColor = Color.rgb(14, 17, 22)
        setContentView(createContent())
    }

    override fun onResume() {
        super.onResume()
        if (::summaryView.isInitialized) refreshState()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATION) {
            RuntimeProtection.recordEvent(
                this,
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    "通知权限请求结果：已允许"
                } else {
                    "通知权限请求结果：未允许"
                },
            )
            refreshState()
            if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "通知权限未开启，可稍后在系统设置中允许", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createContent(): View {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(14, 17, 22))
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            setOnApplyWindowInsetsListener { view, insets ->
                // 这里只处理系统栏 insets；内容留白由 root 统一控制，避免双重 padding。
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val values = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                    view.setPadding(values.left, values.top, values.right, values.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(
                        insets.systemWindowInsetLeft,
                        insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight,
                        insets.systemWindowInsetBottom,
                    )
                }
                insets
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
        }
        scrollView.addView(root, LinearLayout.LayoutParams(-1, -2))

        root.addView(textView("权限与运行保障", 26f, Color.rgb(244, 247, 251)))
        summaryView = textView("检查中…", 14f, Color.rgb(170, 181, 196), 8)
        root.addView(summaryView)
        root.addView(textView(
            "悬浮窗是运行必需权限；通知和电池设置用于降低服务被系统关闭的概率。",
            13f,
            Color.rgb(170, 181, 196),
            8,
        ))

        vendorNotice = textView("", 13f, Color.rgb(255, 193, 107), 18).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBackground(Color.rgb(45, 36, 24), Color.rgb(130, 95, 48))
            visibility = View.GONE
        }
        root.addView(vendorNotice)

        root.addView(sectionLabel("运行必需", 28))
        val overlayRow = checkRow(
            title = "悬浮窗权限",
            description = "用于显示屏蔽区域、屏蔽开关和虚拟按钮。",
        )
        overlayStatus = overlayRow.status
        overlayRow.button.text = "去设置"
        overlayRow.button.setOnClickListener {
            RuntimeProtection.recordEvent(this, "点击悬浮窗权限设置")
            RuntimeProtection.openOverlaySettings(this)
        }
        root.addView(overlayRow.container)

        root.addView(sectionLabel("建议开启", 24))
        val notificationRow = checkRow(
            title = "通知权限",
            description = "用于显示游戏按钮映射正在运行的常驻通知，并提供停止入口。",
        )
        notificationStatus = notificationRow.status
        notificationButton = notificationRow.button
        notificationButton.setOnClickListener {
            RuntimeProtection.recordEvent(this, "点击通知权限设置")
            requestNotificationOrOpenSettings()
        }
        root.addView(notificationRow.container)

        val batteryRow = checkRow(
            title = "电池后台运行",
            description = "不同厂商名称可能不同，目标是允许后台运行或设置为不受限制。",
        )
        batteryStatus = batteryRow.status
        batteryRow.button.text = "设置为不受限制"
        batteryRow.button.setOnClickListener {
            RuntimeProtection.recordEvent(this, "点击电池后台运行设置")
            RuntimeProtection.openBatterySettings(this)
        }
        root.addView(batteryRow.container)

        root.addView(sectionLabel("建议操作", 24))
        val taskRow = checkRow(
            title = "后台任务锁定",
            description = "部分 vivo / iQOO / OPPO / 小米设备可能清理后台服务。",
        )
        taskRow.status.text = "需要用户手动完成"
        taskRow.status.setTextColor(Color.rgb(255, 193, 107))
        taskRow.button.text = "查看方法"
        taskRow.button.setOnClickListener { showTaskLockGuide() }
        root.addView(taskRow.container)

        root.addView(sectionLabel("运行诊断", 28))
        diagnosticsToggle = textView("运行诊断详情（点击展开）", 13f, Color.rgb(116, 167, 255), 6).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBackground(Color.rgb(22, 27, 35), Color.rgb(42, 52, 68))
            isClickable = true
            setOnClickListener { toggleDiagnostics() }
        }
        root.addView(diagnosticsToggle)
        diagnosticsContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        diagnosticsView = textView("检查中…", 13f, Color.rgb(170, 181, 196), 8).apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBackground(Color.rgb(22, 27, 35), Color.rgb(42, 52, 68))
        }
        diagnosticsContent.addView(diagnosticsView)
        diagnosticsContent.addView(actionButton("复制诊断与操作日志", false).apply {
            setOnClickListener { copyDiagnostics() }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        diagnosticsContent.addView(actionButton("清空操作日志", false).apply {
            setOnClickListener { confirmClearLog() }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        root.addView(diagnosticsContent)
        root.addView(textView(
            AppContact.displayText,
            12f,
            Color.rgb(133, 146, 164),
            18,
        ))

        return scrollView
    }

    private fun toggleDiagnostics() {
        diagnosticsExpanded = !diagnosticsExpanded
        diagnosticsContent.visibility = if (diagnosticsExpanded) View.VISIBLE else View.GONE
        diagnosticsToggle.text = if (diagnosticsExpanded) {
            "运行诊断详情（点击收起）"
        } else {
            "运行诊断详情（点击展开）"
        }
        RuntimeProtection.recordEvent(
            this,
            if (diagnosticsExpanded) "运行诊断已展开" else "运行诊断已折叠",
        )
        if (diagnosticsExpanded) diagnosticsView.text = RuntimeProtection.diagnosticText(this)
    }

    private fun refreshState() {
        val status = RuntimeProtection.inspect(this)
        summaryView.text = RuntimeProtection.homeSummary(status)
        summaryView.setTextColor(if (status.overlayGranted) GREEN else RED)

        overlayStatus.text = if (status.overlayGranted) "已开启" else "未开启"
        overlayStatus.setTextColor(if (status.overlayGranted) GREEN else RED)

        notificationStatus.text = RuntimeProtection.notificationLabel(status)
        notificationStatus.setTextColor(
            when {
                !status.notificationRequired || status.notificationGranted -> GREEN
                else -> YELLOW
            },
        )
        notificationButton.text = when {
            !status.notificationRequired -> "系统无需授权"
            status.notificationGranted -> "去设置"
            else -> "允许通知"
        }
        notificationButton.isEnabled = status.notificationRequired
        notificationButton.alpha = if (status.notificationRequired) 1f else 0.55f

        batteryStatus.text = RuntimeProtection.batteryLabel(status)
        batteryStatus.setTextColor(if (status.batteryReady) GREEN else YELLOW)
        if (diagnosticsExpanded) {
            diagnosticsView.text = RuntimeProtection.diagnosticText(this)
        }

        if (status.isVivoIqoo) {
            vendorNotice.visibility = View.VISIBLE
            vendorNotice.text = "检测到 vivo / iQOO 设备\n\n" +
                "OriginOS 可能对侧载 APK 的悬浮窗和后台运行实施额外限制。\n" +
                "建议确认：\n" +
                "1. 已允许“显示在其他应用上层”\n" +
                "2. 已允许通知\n" +
                "3. 电池设置为“不受限制”或允许后台高耗电\n" +
                "4. 在最近任务中锁定本应用\n" +
                "5. 如果系统提示权限受限，请手动解除限制"
        } else {
            vendorNotice.visibility = View.GONE
        }
    }

    private fun requestNotificationOrOpenSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            RuntimeProtection.recordEvent(this, "通知权限请求跳过：系统版本无需授权")
            return
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            RuntimeProtection.openNotificationSettings(this)
        } else {
            RuntimeProtection.recordEvent(this, "请求通知权限")
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATION)
        }
    }

    private fun showTaskLockGuide() {
        RuntimeProtection.recordEvent(this, "打开后台任务锁定说明")
        AlertDialog.Builder(this)
            .setTitle("后台任务锁定")
            .setMessage(
                "1. 打开“游戏按钮映射”\n" +
                    "2. 打开系统最近任务界面\n" +
                    "3. 找到“游戏按钮映射”\n" +
                    "4. 使用系统提供的“锁定”或“小锁”功能\n" +
                    "5. 确认应用不会被一键清理关闭\n\n" +
                    "不同品牌和系统版本操作可能不同。如果没有“锁定”选项，可以跳过。",
            )
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun copyDiagnostics() {
        RuntimeProtection.recordEvent(this, "复制诊断与操作日志")
        val exportText = RuntimeProtection.diagnosticText(this, operationLogChars = 2_000_000)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("游戏按钮映射诊断与操作日志", exportText))
        Toast.makeText(this, "诊断信息与操作日志已复制", Toast.LENGTH_SHORT).show()
    }

    private fun confirmClearLog() {
        AlertDialog.Builder(this)
            .setTitle("清空操作日志")
            .setMessage("清空后无法从应用内恢复历史日志。确定继续吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("清空") { _, _ ->
                OperationLog.clear(this)
                RuntimeProtection.recordEvent(this, "操作日志已清空")
                refreshState()
                Toast.makeText(this, "操作日志已清空", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun checkRow(title: String, description: String): RowViews {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(10), dp(12))
            background = roundedBackground(Color.rgb(22, 27, 35), Color.rgb(42, 52, 68))
        }
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        textColumn.addView(textView(title, 16f, Color.rgb(244, 247, 251)))
        textColumn.addView(textView(description, 12f, Color.rgb(170, 181, 196), 5))
        val status = textView("检查中…", 13f, Color.rgb(170, 181, 196), 7)
        textColumn.addView(status)
        container.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))
        val button = actionButton("去设置", false).apply {
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(6), 0, dp(6), 0)
        }
        container.addView(button, LinearLayout.LayoutParams(dp(126), dp(46)).apply { leftMargin = dp(8) })
        return RowViews(container, status, button)
    }

    private fun sectionLabel(text: String, top: Int): TextView =
        textView(text, 13f, Color.rgb(116, 167, 255), top)

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
        textSize = 13f
        setTextColor(if (primary) Color.rgb(9, 17, 28) else Color.rgb(244, 247, 251))
        background = roundedBackground(
            if (primary) Color.rgb(116, 167, 255) else Color.rgb(32, 41, 56),
            if (primary) Color.rgb(116, 167, 255) else Color.rgb(64, 80, 104),
        )
        stateListAnimator = null
        setPadding(dp(8), 0, dp(8), 0)
    }

    private fun roundedBackground(fill: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(fill)
            setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt().coerceAtLeast(1)

    private data class RowViews(
        val container: View,
        val status: TextView,
        val button: Button,
    )

    companion object {
        private const val REQUEST_NOTIFICATION = 6001
        private val GREEN = Color.rgb(102, 217, 163)
        private val YELLOW = Color.rgb(255, 193, 107)
        private val RED = Color.rgb(255, 130, 127)
    }
}
