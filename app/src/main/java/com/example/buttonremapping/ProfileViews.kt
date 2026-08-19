package com.example.buttonremapping

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.example.buttonremapping.profile.ProfileManager
import com.example.buttonremapping.profile.ProfileMode
import com.example.buttonremapping.profile.ProfileSummary

class ProfilePanel(
    context: Context,
    private val mode: ProfileMode,
    private val onProfileChanged: () -> Unit,
    private val onEditNewProfile: () -> Unit,
) : LinearLayout(context) {
    private val spinner = Spinner(context).apply {
        // 蓝色文本框 + 黑字，解决浅色背景下白字看不清的问题。
        background = GradientDrawable().apply {
            cornerRadius = dp(9).toFloat()
            setColor(Color.rgb(79, 195, 247))
            setStroke(dp(1), Color.rgb(79, 195, 247))
        }
        setPadding(dp(10), 0, dp(10), 0)
    }
    private val listContainer = LinearLayout(context)
    private var summaries: List<ProfileSummary> = emptyList()
    private var selectedForManagement: String? = null
    private var suppressSpinnerCallback = false

    init {
        orientation = VERTICAL
        addView(sectionLabel("\u5F53\u524D\u65B9\u6848"))
        addView(spinner, LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        addView(sectionLabel("\u65B9\u6848\u7BA1\u7406").apply {
            layoutParams = LayoutParams(-1, -2).apply { topMargin = dp(22) }
        })
        listContainer.orientation = VERTICAL
        addView(listContainer, LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val actions = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(actionButton("\u65B0\u5EFA\u65B9\u6848", true).apply {
            setOnClickListener {
                RuntimeProtection.recordEvent(context, "点击新建方案")
                showCreateDialog()
            }
        }, LayoutParams(0, dp(46), 1f))
        actions.addView(actionButton("\u590D\u5236\u65B9\u6848", false).apply {
            setOnClickListener {
                RuntimeProtection.recordEvent(context, "点击复制方案")
                showCopyDialog()
            }
        }, LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(8) })
        actions.addView(actionButton("\u5220\u9664\u65B9\u6848", false).apply {
            setOnClickListener {
                RuntimeProtection.recordEvent(context, "点击删除方案")
                deleteSelected()
            }
        }, LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(8) })
        addView(actions, LayoutParams(-1, dp(46)).apply { topMargin = dp(12) })

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                if (suppressSpinnerCallback || position !in summaries.indices) return
                val target = summaries[position]
                if (!target.isActive && ProfileManager.select(context, mode, target.profileId)) {
                    RuntimeProtection.recordEvent(context, "方案下拉框切换", "mode=${mode.wireName}; name=${target.name}")
                    selectedForManagement = target.profileId
                    onProfileChanged()
                    refresh()
                }
            }
        }
        refresh()
    }

    fun refresh() {
        summaries = ProfileManager.list(context, mode)
        val active = summaries.firstOrNull { it.isActive } ?: return
        if (selectedForManagement == null || summaries.none { it.profileId == selectedForManagement }) {
            selectedForManagement = active.profileId
        }

        val adapter = object : ArrayAdapter<String>(
            context,
            android.R.layout.simple_spinner_item,
            summaries.map { it.name },
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(Color.rgb(9, 17, 28))
                }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(Color.rgb(9, 17, 28))
                }
        }
        suppressSpinnerCallback = true
        spinner.adapter = adapter
        spinner.setSelection(summaries.indexOfFirst { it.isActive }.coerceAtLeast(0), false)
        suppressSpinnerCallback = false

        listContainer.removeAllViews()
        summaries.forEach { summary ->
            listContainer.addView(createProfileRow(summary))
        }
    }

    private fun createProfileRow(summary: ProfileSummary): View = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(4), dp(8), dp(4))
        background = if (summary.profileId == selectedForManagement) {
            roundedBackground(Color.rgb(232, 236, 242), Color.rgb(116, 167, 255))
        } else {
            roundedBackground(Color.rgb(255, 255, 255), Color.rgb(226, 230, 236))
        }
        setOnClickListener {
            selectedForManagement = summary.profileId
            RuntimeProtection.recordEvent(context, "选择方案管理目标", "mode=${mode.wireName}; name=${summary.name}")
            refresh()
        }

        addView(RadioButton(context).apply {
            isChecked = summary.isActive
            isClickable = false
            buttonTintList = ColorStateList.valueOf(Color.rgb(116, 167, 255))
        }, LayoutParams(dp(42), dp(48)))
        addView(LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = summary.name
                textSize = 15f
                setTextColor(Color.rgb(26, 31, 39))
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(TextView(context).apply {
                text = if (summary.isActive) "\u5F53\u524D\u4F7F\u7528" else "\u53EF\u5207\u6362"
                textSize = 12f
                setTextColor(if (summary.isActive) Color.rgb(24, 130, 90) else Color.rgb(90, 100, 114))
            })
        }, LayoutParams(0, -2, 1f))
    }

    private fun showCreateDialog() {
        showNameDialog(
            title = "\u65B0\u5EFA\u65B9\u6848",
            defaultName = "",
            copyCurrentDefault = true,
            allowCopyChoice = true,
        ) { name, copyCurrent ->
            ProfileManager.create(context, mode, name, copyCurrent)
            onProfileChanged()
            refresh()
            onEditNewProfile()
        }
    }

    private fun showCopyDialog() {
        val current = ProfileManager.current(context, mode)
        showNameDialog(
            title = "\u590D\u5236\u5F53\u524D\u65B9\u6848",
            defaultName = "${current.name} \u526F\u672C",
            copyCurrentDefault = true,
            allowCopyChoice = false,
        ) { name, _ ->
            ProfileManager.create(context, mode, name, copyCurrent = true)
            onProfileChanged()
            refresh()
            onEditNewProfile()
        }
    }

    private fun showNameDialog(
        title: String,
        defaultName: String,
        copyCurrentDefault: Boolean,
        allowCopyChoice: Boolean,
        onCreate: (String, Boolean) -> Unit,
    ) {
        val content = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(24), dp(4), dp(24), 0)
        }
        val input = EditText(context).apply {
            hint = "\u65B9\u6848\u540D\u79F0"
            setText(defaultName)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        content.addView(input, LayoutParams(-1, dp(56)))

        val copyGroup = RadioGroup(context).apply {
            orientation = VERTICAL
            visibility = if (allowCopyChoice) VISIBLE else GONE
        }
        val blank = RadioButton(context).apply { text = "\u7A7A\u767D\u65B9\u6848" }
        val copy = RadioButton(context).apply { text = "\u590D\u5236\u5F53\u524D\u65B9\u6848" }
        copyGroup.addView(blank)
        copyGroup.addView(copy)
        if (copyCurrentDefault) copy.isChecked = true else blank.isChecked = true
        content.addView(copyGroup, LayoutParams(-1, -2).apply { topMargin = dp(6) })

        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(content)
            .setNegativeButton("\u53D6\u6D88", null)
            .setPositiveButton("\u786E\u5B9A", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isBlank()) {
                    Toast.makeText(context, "\u8BF7\u8F93\u5165\u65B9\u6848\u540D\u79F0", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                onCreate(name, allowCopyChoice && copy.isChecked)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun deleteSelected() {
        val target = summaries.firstOrNull { it.profileId == selectedForManagement }
            ?: summaries.firstOrNull { it.isActive }
            ?: return
        if (target.isActive) {
            AlertDialog.Builder(context)
                .setTitle("\u65E0\u6CD5\u5220\u9664\u5F53\u524D\u65B9\u6848")
                .setMessage("\u8BE5\u65B9\u6848\u6B63\u5728\u4F7F\u7528\u3002\u8BF7\u5207\u6362\u5230\u5176\u4ED6\u65B9\u6848\u540E\u5220\u9664\u3002")
                .setPositiveButton("\u77E5\u9053\u4E86", null)
                .show()
            return
        }
        AlertDialog.Builder(context)
            .setTitle("\u5220\u9664\u65B9\u6848")
            .setMessage("\u786E\u5B9A\u5220\u9664\u201C${target.name}\u201D\u5417\uFF1F\u8BE5\u64CD\u4F5C\u4E0D\u4F1A\u5F71\u54CD\u5176\u4ED6\u65B9\u6848\u3002")
            .setNegativeButton("\u53D6\u6D88", null)
            .setPositiveButton("\u5220\u9664") { _, _ ->
                if (ProfileManager.delete(context, mode, target.profileId)) {
                    selectedForManagement = ProfileManager.current(context, mode).profileId
                    onProfileChanged()
                    refresh()
                }
            }
            .show()
    }

    private fun sectionLabel(text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 13f
        setTextColor(Color.rgb(116, 167, 255))
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
        setPadding(dp(4), 0, dp(4), 0)
    }

    private fun roundedBackground(fill: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(9).toFloat()
        setColor(fill)
        setStroke(dp(1), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)
}
