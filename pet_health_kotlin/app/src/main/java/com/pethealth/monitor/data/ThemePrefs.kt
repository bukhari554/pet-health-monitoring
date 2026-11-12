package com.pethealth.monitor.data

import android.content.Context
import com.pethealth.monitor.ui.theme.ThemeMode

class ThemePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("pet_health_theme", Context.MODE_PRIVATE)

    fun load(): ThemeMode {
        val raw = prefs.getString(KEY, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun save(mode: ThemeMode) {
        prefs.edit().putString(KEY, mode.name).apply()
    }

    companion object {
        private const val KEY = "theme_mode"
    }
}
