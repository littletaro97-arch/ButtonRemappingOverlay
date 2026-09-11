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
import android.widget.ImageView
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
    private lateinit var updateController: GitHubUpdateController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RuntimeProtection.recordEvent(this, "应用启动")
        RecentsVisibility.applySavedPreference(this)
        window.statusBarColor = Color.rgb(247, 248, 250)
        window.navigationBarColor = Color.rgb(247, 248, 250)
        ProfileManager.list(this, ProfileMode.LOW)
        ProfileManager.list(this, ProfileMode.HIGH)
        updateController = GitHubUpdateController(this)
        showModeSelection()
        window.decorView.post {
            val preparationAlreadyShown = getSharedPreferences(PREFS_RUNTIME, MODE_PRIVATE)
                .getBoolean(KEY_PREPARATION_SHOWN, false)
            showFirstRunPreparationIfNeeded()
            if (preparationAlreadyShown) updateController.checkOnLaunch()
        }
    }

    override fun onStart() {
        super.onStart()
        updateController.attach()
    }

    override fun onStop() {
        updateController.detach()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        RecentsVisibility.applySavedPreference(this)
        updateController.resumePendingInstallIfAllowed()
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

    private fun showDoubleTapMode() {
        RuntimeProtection.recordEvent(this, "进入双击触发模式")
        startActivity(Intent(this, DoubleTapActivity::class.java))
    }

    private fun showHighRiskMode() {
        RuntimeProtection.recordEvent(this, "进入修改键位模式")
        startActivity(Intent(this, HighRiskActivity::class.java))
    }

    private fun createModeSelection(): View {
        val scrollView = baseScrollView()
        val root = baseRoot()
        scrollView.addView(root, LinearLayout.LayoutParams(-1, -2))

        root.addView(textView("游戏按钮映射", 26f, Color.rgb(26, 31, 39)))
        root.addView(modePanel(
            title = "屏蔽区域模式",
            description = "屏蔽目标区域，使用专用开关按需解锁",
            buttonText = "进入",
            enabled = true,
            badge = badgeChip("低风险", Color.rgb(102, 217, 163), Color.rgb(24, 58, 44)),
            onClick = { showLowRiskMode() },
        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })
        root.addView(modePanel(
            title = "双击触发模式",
            description = "将单击触发的按钮改为双击触发",
            buttonText = "进入",
            enabled = true,
            badge = badgeChip("低风险", Color.rgb(102, 217, 163), Color.rgb(24, 58, 44)),
            onClick = { showDoubleTapMode() },
        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(modePanel(
            title = "长按触发模式",
            description = "短按操作变更为长按",
            buttonText = "进入",
            enabled = true,
            badge = badgeChip("中风险", Color.rgb(255, 193, 107), Color.rgb(64, 48, 24)),
            onClick = { showLongPressMode() },
        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(modePanel(
            title = "修改键位模式",
            description = "屏蔽目标区域，修改目标位置的触发位置",
            buttonText = "进入",
            enabled = true,
            badge = badgeChip("中风险", Color.rgb(255, 193, 107), Color.rgb(64, 48, 24)),
            onClick = { showHighRiskMode() },
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
        root.addView(sectionLabel("问题反馈"), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(24)
        })
        root.addView(contactRow(
            name = "小红书",
            detail = "小红书号：${AppContact.XHS_ID} · 昵称：${AppContact.XHS_NAME}",
            url = AppContact.XHS_URL,
            appPackage = AppContact.XHS_PACKAGE,
            iconRes = R.drawable.ic_xhs,
        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(contactRow(
            name = "bilibili",
            detail = "UID：${AppContact.BILI_UID} · 昵称：${AppContact.BILI_NAME}",
            url = AppContact.BILI_URL,
            appPackage = AppContact.BILI_PACKAGE,
            iconRes = R.drawable.ic_bili,
        ), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        return scrollView
    }

    private fun contactRow(
        name: String,
        detail: String,
        url: String,
        appPackage: String?,
        iconRes: Int,
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = roundedBackground(Color.rgb(255, 255, 255), Color.rgb(226, 230, 236))
        isClickable = true
        setOnClickListener {
            RuntimeProtection.recordEvent(this@MainActivity, "打开问题反馈主页", name)
            openContactPage(url, appPackage)
        }
        // 左：品牌图标。
        addView(ImageView(this@MainActivity).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = name
        }, LinearLayout.LayoutParams(dp(40), dp(40)))
        // 中：联系方式。
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(textView(name, 15f, Color.rgb(26, 31, 39)))
            addView(textView(detail, 12f, Color.rgb(90, 100, 114), top = 2))
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
        // 右：跳转入口。
        addView(badgeChip(
            "点击跳转",
            Color.rgb(116, 167, 255),
            Color.rgb(226, 234, 246),
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
        background = roundedBackground(Color.rgb(255, 255, 255), Color.rgb(226, 230, 236))

        // 左侧：标题（含标签）+ 描述，占满剩余宽度。
        val textColumn = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val titleRow = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(textView(title, 16f, Color.rgb(26, 31, 39)), LinearLayout.LayoutParams(
            0, -2, 1f,
        ))
        if (badge != null) {
            titleRow.addView(badge, LinearLayout.LayoutParams(-2, -2).apply {
                leftMargin = dp(8)
            })
        }
        textColumn.addView(titleRow, LinearLayout.LayoutParams(-1, -2))
        textColumn.addView(textView(description, 12f, Color.rgb(90, 100, 114), top = 5))
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
        setBackgroundColor(Color.rgb(247, 248, 250))
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val REQUEST_FIRST_RUN_NOTIFICATION = 2002
        private const val PREFS_RUNTIME = "runtime_protection"
        private const val KEY_PREPARATION_SHOWN = "preparation_shown"
        private const val FIRST_RUN_FINISHED = 2
    }
}
