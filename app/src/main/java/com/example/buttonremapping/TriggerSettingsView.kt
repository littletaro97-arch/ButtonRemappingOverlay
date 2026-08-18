package com.example.buttonremapping

import android.app.AlertDialog
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.example.buttonremapping.highrisk.MappingPrefs
import com.example.buttonremapping.profile.ProfileMode

/**
 * “指定应用启动”设置面板：权限状态、启动勾选、已配置应用列表、添加/删除。
 * 低风险配置写入 LayoutConfig（经 LayoutPrefs/ProfileManager），高风险写入 MappingConfig。
 *
 * 勾选「启动指定应用监控」时会回调 [onStartRequested] 让宿主自动启动服务，
 * 使指定应用启动不再依赖手动点击「启动屏蔽/映射」。
 */
@SuppressLint("ViewConstructor")
class TriggerSettingsView(
    context: Context,
    private val mode: ProfileMode,
    private val onConfigChanged: () -> Unit,
    private val onStartRequested: () -> Unit,
) : LinearLayout(context) {

    private val packageContainer = LinearLayout(context).apply {
        orientation = VERTICAL
    }
    private lateinit var permissionStatus: TextView
    private lateinit var permissionButton: Button
    private lateinit var enabledSwitch: Switch
    private lateinit var startStateText: TextView

    init {
        orientation = VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = roundedBackground(Color.rgb(255, 255, 255), Color.rgb(226, 230, 236))

        val titleRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(textView("指定应用启动", 15f, Color.rgb(26, 31, 39)), LayoutParams(0, -2, 1f))
        titleRow.addView(TextView(context).apply {
            text = "测试"
            textSize = 12f
            setTextColor(Color.rgb(255, 90, 90))
            maxLines = 1
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.rgb(58, 22, 24))
                setStroke(dp(1), Color.rgb(255, 90, 90))
            }
            setPadding(dp(10), dp(3), dp(10), dp(3))
        }, LayoutParams(-2, -2).apply { leftMargin = dp(10) })
        addView(titleRow, LayoutParams(-1, -2))

        // 使用情况访问权限状态行
        val permissionRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        permissionStatus = textView("使用情况访问权限", 13f, Color.rgb(176, 122, 26))
        permissionRow.addView(permissionStatus, LayoutParams(0, -2, 1f))
        permissionButton = actionButton("去开启", false).apply {
            setOnClickListener {
                RuntimeProtection.recordEvent(context, "点击开启使用情况访问权限")
                AppTrigger.openUsageAccessSettings(context)
            }
        }
        permissionRow.addView(permissionButton, LayoutParams(-2, dp(40)).apply { leftMargin = dp(8) })
        addView(permissionRow, LayoutParams(-1, -2).apply { topMargin = dp(8) })

        // 启动勾选行
        val switchRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        switchRow.addView(textView("启动指定应用监控", 14f, Color.rgb(26, 31, 39)), LayoutParams(0, -2, 1f))
        enabledSwitch = Switch(context).apply {
            isChecked = currentTriggerEnabled()
            setOnCheckedChangeListener { _, checked ->
                RuntimeProtection.recordEvent(
                    context,
                    if (checked) "指定应用监控已启动" else "指定应用监控已停止",
                )
                setTriggerEnabled(checked)
                refreshPackageList()
                refreshStartState()
                if (checked) onStartRequested() else onConfigChanged()
            }
        }
        switchRow.addView(enabledSwitch, LayoutParams(-2, -2))
        addView(switchRow, LayoutParams(-1, -2).apply { topMargin = dp(12) })

        startStateText = textView("", 12f, Color.rgb(90, 100, 114), top = 6)
        addView(startStateText)

        addView(textView("触发应用", 14f, Color.rgb(26, 31, 39)), LayoutParams(-1, -2).apply {
            topMargin = dp(14)
        })
        addView(packageContainer, LayoutParams(-1, -2).apply { topMargin = dp(6) })

        addView(actionButton("添加应用", true).apply {
            setOnClickListener { showAppPicker() }
        }, LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })

        refreshPackageList()
        refreshPermissionState()
        refreshStartState()
    }

    /** 宿主 onResume 时调用，从系统设置返回后刷新权限状态。 */
    fun refresh() {
        refreshPermissionState()
        refreshPackageList()
        refreshStartState()
    }

    private fun refreshPermissionState() {
        if (!::permissionStatus.isInitialized) return
        val granted = AppTrigger.hasUsageAccess(context)
        if (granted) {
            permissionStatus.text = "使用情况访问权限：已开启"
            permissionStatus.setTextColor(Color.rgb(24, 130, 90))
            permissionButton.text = "已开启"
            permissionButton.setTextColor(Color.rgb(9, 17, 28))
            permissionButton.background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(Color.rgb(102, 217, 163))
                setStroke(dp(1), Color.rgb(102, 217, 163))
            }
            permissionButton.isEnabled = false
        } else {
            permissionStatus.text = "使用情况访问权限：未开启"
            permissionStatus.setTextColor(Color.rgb(176, 122, 26))
            permissionButton.text = "去开启"
            permissionButton.setTextColor(Color.rgb(26, 31, 39))
            permissionButton.background = GradientDrawable().apply {
                cornerRadius = dp(9).toFloat()
                setColor(Color.rgb(232, 236, 242))
                setStroke(dp(1), Color.rgb(206, 212, 222))
            }
            permissionButton.isEnabled = true
        }
    }

    private fun refreshStartState() {
        if (!::enabledSwitch.isInitialized || !::startStateText.isInitialized) return
        val enabled = enabledSwitch.isChecked
        val granted = AppTrigger.hasUsageAccess(context)
        startStateText.text = when {
            !enabled -> "未启动：打开下方应用不会触发屏蔽/映射。"
            !granted -> "已勾选，但需先开启使用情况访问权限。"
            else -> "已启动：打开下方应用时将自动开启屏蔽/映射，离开后自动关闭。"
        }
        startStateText.setTextColor(
            if (enabled && granted) Color.rgb(24, 130, 90) else Color.rgb(90, 100, 114),
        )
    }

    private fun currentTriggerEnabled(): Boolean = when (mode) {
        ProfileMode.LOW -> LayoutPrefs.load(context).triggerEnabled
        ProfileMode.LONG -> LongPressPrefs.load(context).triggerEnabled
        ProfileMode.HIGH -> MappingPrefs.load(context).triggerEnabled
    }

    private fun currentTriggerPackages(): List<String> = when (mode) {
        ProfileMode.LOW -> LayoutPrefs.load(context).triggerPackages
        ProfileMode.LONG -> LongPressPrefs.load(context).triggerPackages
        ProfileMode.HIGH -> MappingPrefs.load(context).triggerPackages
    }

    private fun setTriggerEnabled(enabled: Boolean) {
        when (mode) {
            ProfileMode.LOW -> {
                val config = LayoutPrefs.load(context)
                LayoutPrefs.save(context, config.copy(triggerEnabled = enabled))
            }
            ProfileMode.LONG -> {
                val config = LongPressPrefs.load(context)
                LongPressPrefs.save(context, config.copy(triggerEnabled = enabled))
            }
            ProfileMode.HIGH -> {
                val config = MappingPrefs.load(context)
                MappingPrefs.save(context, config.copy(triggerEnabled = enabled))
            }
        }
    }

    private fun setTriggerPackages(packages: List<String>) {
        when (mode) {
            ProfileMode.LOW -> {
                val config = LayoutPrefs.load(context)
                LayoutPrefs.save(context, config.copy(triggerPackages = packages))
            }
            ProfileMode.LONG -> {
                val config = LongPressPrefs.load(context)
                LongPressPrefs.save(context, config.copy(triggerPackages = packages))
            }
            ProfileMode.HIGH -> {
                val config = MappingPrefs.load(context)
                MappingPrefs.save(context, config.copy(triggerPackages = packages))
            }
        }
    }

    private fun refreshPackageList() {
        packageContainer.removeAllViews()
        val packages = currentTriggerPackages()
        if (packages.isEmpty()) {
            packageContainer.addView(textView(
                "尚未添加应用",
                13f,
                Color.rgb(90, 100, 114),
            ))
            return
        }
        packages.forEach { packageName ->
            // 每个应用一行独立圆角框，行与行之间留间距，避免糊在一起。
            packageContainer.addView(
                createPackageRow(packageName, packages),
                LayoutParams(-1, -2).apply { bottomMargin = dp(6) },
            )
        }
    }

    private fun createPackageRow(packageName: String, allPackages: List<String>): View =
        LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = roundedBackground(Color.rgb(244, 246, 249), Color.rgb(222, 227, 234))
            addView(textView(
                AppTrigger.applicationLabel(context, packageName),
                14f,
                Color.rgb(26, 31, 39),
            ), LayoutParams(0, -2, 1f))
            addView(textView(packageName, 11f, Color.rgb(122, 132, 148)), LayoutParams(-2, -2))
            addView(actionButton("移除", false).apply {
                setOnClickListener {
                    RuntimeProtection.recordEvent(context, "移除触发应用", packageName)
                    setTriggerPackages(allPackages.filterNot { it == packageName })
                    refreshPackageList()
                    onConfigChanged()
                }
            }, LayoutParams(-2, dp(38)).apply { leftMargin = dp(8) })
        }

    private fun showAppPicker() {
        val installed = AppTrigger.installedLaunchablePackages(context)
            .filterNot { it == context.packageName }
        if (installed.isEmpty()) {
            Toast.makeText(context, "未找到可添加的应用", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = installed.map { AppTrigger.applicationLabel(context, it) }
        val selected = BooleanArray(installed.size)
        val current = currentTriggerPackages()
        installed.forEachIndexed { index, packageName ->
            selected[index] = packageName in current
        }
        AlertDialog.Builder(context)
            .setTitle("选择触发应用")
            .setMultiChoiceItems(labels.toTypedArray(), selected) { _, index, isChecked ->
                selected[index] = isChecked
            }
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                val newPackages = installed.filterIndexed { index, _ -> selected[index] }
                RuntimeProtection.recordEvent(
                    context,
                    "更新触发应用列表",
                    "count=${newPackages.size}",
                )
                setTriggerPackages(newPackages)
                refreshPackageList()
                onConfigChanged()
            }
            .show()
    }

    private fun textView(text: String, size: Float, color: Int, top: Int = 0): TextView =
        TextView(context).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            setLineSpacing(0f, 1.08f)
            layoutParams = LayoutParams(-1, -2).apply { topMargin = dp(top) }
        }

    private fun actionButton(text: String, primary: Boolean): Button = Button(context).apply {
        this.text = text
        isAllCaps = false
        textSize = 13f
        stateListAnimator = null
        setTextColor(if (primary) Color.rgb(9, 17, 28) else Color.rgb(26, 31, 39))
        background = GradientDrawable().apply {
            cornerRadius = dp(9).toFloat()
            setColor(if (primary) Color.rgb(116, 167, 255) else Color.rgb(232, 236, 242))
            setStroke(dp(1), if (primary) Color.rgb(116, 167, 255) else Color.rgb(206, 212, 222))
        }
        setPadding(dp(6), 0, dp(6), 0)
    }

    private fun roundedBackground(fill: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(9).toFloat()
            setColor(fill)
            setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)
}
