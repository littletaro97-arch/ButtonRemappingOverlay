package com.example.buttonremapping

import android.app.ActivityManager
import android.content.Context

object RecentsVisibility {
    private const val PREFS_NAME = "runtime_preferences"
    private const val HIDE_FROM_RECENTS = "hide_from_recents"

    fun isHidden(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(HIDE_FROM_RECENTS, false)

    fun setHidden(context: Context, hidden: Boolean): Boolean {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(HIDE_FROM_RECENTS, hidden)
            .apply()
        return applyToCurrentTasks(context, hidden)
    }

    fun applySavedPreference(context: Context): Boolean =
        applyToCurrentTasks(context, isHidden(context))

    private fun applyToCurrentTasks(context: Context, hidden: Boolean): Boolean = runCatching {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return@runCatching false
        val tasks = manager.appTasks
        if (tasks.isEmpty()) return@runCatching false
        tasks.forEach { it.setExcludeFromRecents(hidden) }
        true
    }.getOrDefault(false)
}
