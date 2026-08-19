package com.example.buttonremapping

import android.content.Context
import android.widget.SeekBar

/**
 * 透明度 SeekBar：在 5% 处设隔断，新增独立的 0% 档位。
 *
 * - 正常拖动范围 [5%, 100%]（progress 5..100），连续可滑。
 * - progress 0 是独立档位「0%」。
 * - 从 5% 以上向下拖，最低只能到 5%；从 5% 再次向下拖一次，才跳到 0%。
 * - 从 0% 向上拖，直接跳回 5%（0%↔5% 之间不平滑，采用隔断）。
 */
fun createTransparencySeekBar(
    context: Context,
    initialAlpha: Float,
    onAlphaChanged: (Float) -> Unit,
): SeekBar {
    var dragStartProgress = 100

    val seekBar = SeekBar(context)
    seekBar.max = 100
    seekBar.progress = if (initialAlpha < 0.05f) 0 else (initialAlpha * 100f).toInt().coerceIn(5, 100)

    seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onStartTrackingTouch(sb: SeekBar?) {
            dragStartProgress = sb?.progress ?: 100
        }

        override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
            if (!fromUser || sb == null) return
            if (progress in 1..4) {
                // 0%~5% 之间是隔断区，不允许平滑滑过。
                if (progress < dragStartProgress && dragStartProgress <= 5) {
                    // 从 5%（或 0%）向下再拖一次 -> 跳到 0%。
                    sb.progress = 0
                    onAlphaChanged(0f)
                } else {
                    // 正常拖 / 从 0% 向上拖 -> 停在 5%。
                    sb.progress = 5
                    onAlphaChanged(0.05f)
                }
            } else {
                onAlphaChanged(progress / 100f)
            }
        }

        override fun onStopTrackingTouch(sb: SeekBar?) {
            dragStartProgress = sb?.progress ?: 100
        }
    })

    return seekBar
}
