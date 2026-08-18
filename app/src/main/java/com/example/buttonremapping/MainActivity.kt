package com.example.buttonremapping

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.buttonremapping.highrisk.HighRiskActivity
import com.example.buttonremapping.profile.ProfileManager
import com.example.buttonremapping.profile.ProfileMode
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private var firstRunStep = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RuntimeProtection.recordEvent(this, "应用启动")
        window.statusBarColor = Color.rgb(14, 17, 22)
        window.navigationBarColor = Color.rgb(14, 17, 22)
        ProfileManager.list(this, ProfileMode.LOW)
        ProfileManager.list(this, ProfileMode.HIGH)
        showModeSelection()
        window.decorView.post { showFirstRunPreparationIfNeeded() }
    }

    override fun onResume() {
        super.onResume()
        // 从悬浮窗系统设置页返回后继续首启流程。
        if (firstRunStep > 0 && firstRunStep < FIRST_RUN_FINISHED) {
            runNextFirstRunStep()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_FIRST_RUN_NOTIFICATION) {
            RuntimeProtection.recordEvent(
                this,
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    "首次进入：通知权限已允许"
                } else {
                    "首次进入：通知权限未允许"
                },
            )
            runNextFirstRunStep()
        }
    }

    private fun showModeSelection() {
        RuntimeProtection.recordEvent(this, "打开模式选择")
        setContentView(createModeSelection())
    }

    private fun showLowRiskMode() {
        RuntimeProtection.recordEvent(this, "进入屏蔽区域模式")
        // 与另外两个方案一致：以独立 Activity 启动，由系统提供水平推入过渡动画。
        startActivity(Intent(this, LowRiskActivity::class.java))
    }

    private fun showLongPressMode() {
        RuntimeProtection.recordEvent(this, "进入长按触发模式")
        startActivity(Intent(this, LongPressActivity::class.java))
    }

    private fun createModeSelection(): View {
        val scrollView = baseScrollView()
        val root = baseRoot()
        scrollView.addView(root, LinearLayout.LayoutParams(-1, -2))

        root.addView(textView("游戏按钮映射", 26f, Color.rgb(244, 247, 251)))
        root.addView(textView(
            "选择一种触控管理方案。修改键位模式仅用于受控兼容性测试。",
            14f,
            Color.rgb(170, 181, 196),
            top = 8,
        ))
        root.addView(sectionLabel("请选择模式"), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(28)
        })
        root.addView(modePanel(
            title = "屏蔽区域模式",
            description = "屏蔽误触按钮区域，并用悬浮开关临时恢复触摸。",
            buttonText = "进入",
            enabled = true,
            badge = badgeChip("低风险方案", Color.rgb(102, 217, 163), Color.rgb(24, 58, 44)),
            onClick = { showLowRiskMode() },
        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(modePanel(
            title = "长按触发模式",
            description = "把按一下触发的按钮变为长按满时间才触发，防止误触。",
            buttonText = "进入",
            enabled = true,
            badge = badgeChip("中风险方案", Color.rgb(255, 193, 107), Color.rgb(64, 48, 24)),
            onClick = { showLongPressMode() },
        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(modePanel(
            title = "修改键位模式",
            description = "单按钮位置映射：一次人工点击对应一次目标 Tap。",
            buttonText = "进入",
            enabled = true,
            badge = badgeChip("高风险方案", Color.rgb(255, 130, 127), Color.rgb(64, 26, 28)),
            onClick = { confirmHighRiskEntry() },
        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(modePanel(
            title = "设置",
            description = RuntimeProtection.homeSummary(this),
            buttonText = "进入设置",
            enabled = true,
            onClick = {
                RuntimeProtection.recordEvent(this, "进入权限与运行保障")
                openRuntimeProtection()
            },
        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        root.addView(textView(
            "修改键位模式禁止真实游戏测试、连点、宏、自动操作、Root、Accessibility 和反作弊规避。",
            12f,
            Color.rgb(133, 146, 164),
            top = 18,
        ))

        root.addView(sectionLabel("问题反馈"), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(24)
        })
        root.addView(contactRow(
            "小红书：小红书号：${AppContact.XHS_ID}，昵称：${AppContact.XHS_NAME}",
            AppContact.XHS_URL,
            AppContact.XHS_PACKAGE,
        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(contactRow(
            "bilibili：UID：${AppContact.BILI_UID}，昵称：${AppContact.BILI_NAME}",
            AppContact.BILI_URL,
            AppContact.BILI_PACKAGE,
        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        return scrollView
    }

    private fun contactRow(text: String, url: String, appPackage: String?): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground(Color.rgb(22, 27, 35), Color.rgb(42, 52, 68))
            isClickable = true
            setOnClickListener {
                RuntimeProtection.recordEvent(this@MainActivity, "打开问题反馈主页", text)
                openContactPage(url, appPackage)
            }
            addView(textView(text, 14f, Color.rgb(244, 247, 251)), LinearLayout.LayoutParams(
                0, -2, 1f,
            ))
            addView(badgeChip(
                "点击跳转",
                Color.rgb(116, 167, 255),
                Color.rgb(22, 34, 54),
            ), LinearLayout.LayoutParams(-2, -2).apply {
                leftMargin = dp(10)
            })
        }

    private fun openContactPage(url: String, appPackage: String?) {
        if (appPackage != null && isAppInstalled(appPackage)) {
            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage(appPackage)
            }
            try {
                startActivity(appIntent)
                RuntimeProtection.recordEvent(this, "通过 App 打开主页", appPackage)
                return
            } catch (_: Exception) {
                RuntimeProtection.recordEvent(this, "App 打开主页失败，改用浏览器", appPackage)
            }
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            RuntimeProtection.recordEvent(this, "打开主页失败", url)
            Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAppInstalled(packageName: String): Boolean = try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        false
    }

    private fun confirmHighRiskEntry() {
        RuntimeProtection.recordEvent(this, "打开修改键位模式说明")
        AlertDialog.Builder(this)
            .setTitle("进入修改键位模式")
            .setMessage(
                "当前阶段尚不明确是否触及封号红线，请谨慎使用。",
            )
            .setNegativeButton("取消", null)
            .setPositiveButton("继续") { _, _ ->
                confirmHighRiskSecond()
            }
            .show()
    }

    /**
     * 二次确认：红色文字强调风险，确认后才进入修改键位模式。
     */
    private fun confirmHighRiskSecond() {
        RuntimeProtection.recordEvent(this, "打开修改键位模式二次确认")
        val message = android.text.SpannableStringBuilder()
            .append("是否确定进入该模式？")
        message.setSpan(
            android.text.style.ForegroundColorSpan(Color.rgb(255, 90, 90)),
            0,
            message.length,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        AlertDialog.Builder(this)
            .setTitle("再次确认")
            .setMessage(message)
            .setNegativeButton("取消") { _, _ ->
                RuntimeProtection.recordEvent(this, "修改键位模式二次确认：取消")
            }
            .setPositiveButton("继续") { _, _ ->
                RuntimeProtection.recordEvent(this, "确认进入修改键位模式")
                startActivity(Intent(this, HighRiskActivity::class.java))
            }
            .show()
    }

    private fun openRuntimeProtection() {
        RuntimeProtection.recordEvent(this, "打开权限与运行保障页面")
        startActivity(Intent(this, RuntimeProtectionActivity::class.java))
    }

    private fun showFirstRunPreparationIfNeeded() {
        val preferences = getSharedPreferences(PREFS_RUNTIME, MODE_PRIVATE)
        if (preferences.getBoolean(KEY_PREPARATION_SHOWN, false)) return
        preferences.edit().putBoolean(KEY_PREPARATION_SHOWN, true).apply()
        firstRunStep = 0
        RuntimeProtection.recordEvent(this, "首次进入：开始检查运行权限")
        runNextFirstRunStep()
    }

    /**
     * 首次进入权限引导：
     * step 0 悬浮窗权限（必需，跳系统设置）；
     * step 1 通知权限（Android 13+ 可请求）；
     * step 2 完成。
     */
    private fun runNextFirstRunStep() {
        val status = RuntimeProtection.inspect(this)
        when (firstRunStep) {
            0 -> {
                if (status.overlayGranted) {
                    firstRunStep = 1
                    runNextFirstRunStep()
                } else {
                    firstRunStep = 1
                    RuntimeProtection.recordEvent(this, "首次进入：悬浮窗权限未开启，引导授权")
                    AlertDialog.Builder(this)
                        .setTitle("需要悬浮窗权限")
                        .setMessage(
                            "屏蔽区域模式与修改键位模式需要悬浮窗权限，才能显示屏蔽区域、悬浮开关和虚拟按钮。\n\n" +
                                "请允许“显示在其他应用上层”。",
                        )
                        .setNegativeButton("稍后") { _, _ -> runNextFirstRunStep() }
                        .setPositiveButton("去授权") { _, _ ->
                            RuntimeProtection.openOverlaySettings(this)
                        }
                        .show()
                }
            }
            1 -> {
                if (status.notificationRequired && !status.notificationGranted) {
                    firstRunStep = 2
                    RuntimeProtection.recordEvent(this, "首次进入：请求通知权限")
                    @Suppress("InlinedApi")
                    requestPermissions(
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        REQUEST_FIRST_RUN_NOTIFICATION,
                    )
                } else {
                    firstRunStep = 2
                    runNextFirstRunStep()
                }
            }
            else -> RuntimeProtection.recordEvent(this, "首次进入：运行权限检查完成")
        }
    }

    private fun modePanel(
        title: String,
        description: String,
        buttonText: String,
        enabled: Boolean,
        onClick: () -> Unit,
        badge: View? = null,
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = roundedBackground(Color.rgb(22, 27, 35), Color.rgb(42, 52, 68))

        // 左侧：标题（含标签）+ 描述，占满剩余宽度。
        val textColumn = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val titleRow = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(textView(title, 16f, Color.rgb(244, 247, 251)), LinearLayout.LayoutParams(
            0, -2, 1f,
        ))
        if (badge != null) {
            titleRow.addView(badge, LinearLayout.LayoutParams(-2, -2).apply {
                leftMargin = dp(8)
            })
        }
        textColumn.addView(titleRow, LinearLayout.LayoutParams(-1, -2))
        textColumn.addView(textView(description, 12f, Color.rgb(170, 181, 196), top = 5))
        addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))

        // 右侧：进入按钮，垂直居中，宽度固定。
        addView(actionButton(buttonText, primary = enabled).apply {
            isEnabled = enabled
            if (!enabled) alpha = 0.55f
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(dp(88), dp(42)).apply { leftMargin = dp(12) })
    }

    /**
     * 小圆角标签：文字与边框同色系，字号与“点击跳转”一致，贴合文本不溢出。
     */
    private fun badgeChip(text: String, textColor: Int, fillColor: Int): View =
        TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(textColor)
            maxLines = 1
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(fillColor)
                setStroke(dp(1), textColor)
            }
            setPadding(dp(10), dp(3), dp(10), dp(3))
        }

    private fun baseScrollView(): ScrollView = ScrollView(this).apply {
        setBackgroundColor(Color.rgb(14, 17, 22))
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
    }

    private fun baseRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(28), dp(24), dp(28))
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
        textSize = 15f
        setTextColor(if (primary) Color.rgb(9, 17, 28) else Color.rgb(244, 247, 251))
        background = roundedBackground(
            if (primary) Color.rgb(116, 167, 255) else Color.rgb(32, 41, 56),
            if (primary) Color.rgb(116, 167, 255) else Color.rgb(64, 80, 104),
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val REQUEST_FIRST_RUN_NOTIFICATION = 2002
        private const val PREFS_RUNTIME = "runtime_protection"
        private const val KEY_PREPARATION_SHOWN = "preparation_shown"
        private const val FIRST_RUN_FINISHED = 2
    }
}
