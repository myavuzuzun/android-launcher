package com.minimal.launcher

import android.content.Context
import android.content.SharedPreferences

class LauncherPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var theme: LauncherTheme
        get() = parseEnum(prefs.getString(KEY_THEME, null), LauncherTheme.AMOLED_BLACK)
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    var alignment: HomeAlignment
        get() = parseEnum(prefs.getString(KEY_ALIGNMENT, null), HomeAlignment.RIGHT)
        set(value) = prefs.edit().putString(KEY_ALIGNMENT, value.name).apply()

    var textSize: TextSizeOption
        get() = parseEnum(prefs.getString(KEY_TEXT_SIZE, null), TextSizeOption.MEDIUM)
        set(value) = prefs.edit().putString(KEY_TEXT_SIZE, value.name).apply()

    var showClock: Boolean
        get() = prefs.getBoolean(KEY_SHOW_CLOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_CLOCK, value).apply()

    var swipeUpAllApps: Boolean
        get() = prefs.getBoolean(KEY_SWIPE_UP_ALL_APPS, true)
        set(value) = prefs.edit().putBoolean(KEY_SWIPE_UP_ALL_APPS, value).apply()

    var swipeDownHome: Boolean
        get() = prefs.getBoolean(KEY_SWIPE_DOWN_HOME, true)
        set(value) = prefs.edit().putBoolean(KEY_SWIPE_DOWN_HOME, value).apply()

    var swipeDownNotifications: Boolean
        get() = prefs.getBoolean(KEY_SWIPE_DOWN_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_SWIPE_DOWN_NOTIFICATIONS, value).apply()

    var doubleTapLock: Boolean
        get() = prefs.getBoolean(KEY_DOUBLE_TAP_LOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_DOUBLE_TAP_LOCK, value).apply()

    fun getPinnedKeys(): List<String> {
        val raw = prefs.getString(KEY_PINNED, "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split('\n').filter { it.isNotEmpty() }
    }

    fun setPinnedKeys(keys: List<String>) {
        prefs.edit().putString(KEY_PINNED, keys.joinToString("\n")).apply()
    }

    fun isPinned(idKey: String): Boolean = getPinnedKeys().contains(idKey)

    fun togglePin(idKey: String): Boolean {
        val current = getPinnedKeys().toMutableList()
        val nowPinned = if (current.contains(idKey)) {
            current.remove(idKey)
            false
        } else {
            current.add(idKey)
            true
        }
        setPinnedKeys(current)
        return nowPinned
    }

    fun removePin(idKey: String) {
        val current = getPinnedKeys()
        if (current.contains(idKey)) {
            setPinnedKeys(current.filterNot { it == idKey })
        }
    }

    fun getHiddenKeys(): Set<String> {
        return HashSet(prefs.getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet())
    }

    fun isHidden(idKey: String): Boolean = getHiddenKeys().contains(idKey)

    fun hideApp(idKey: String) {
        val updated = getHiddenKeys().toMutableSet()
        updated.add(idKey)
        prefs.edit().putStringSet(KEY_HIDDEN, updated).apply()
        removePin(idKey)
    }

    fun unhideApp(idKey: String) {
        val updated = getHiddenKeys().toMutableSet()
        if (updated.remove(idKey)) {
            prefs.edit().putStringSet(KEY_HIDDEN, updated).apply()
        }
    }

    fun restoreAllHidden() {
        prefs.edit().remove(KEY_HIDDEN).apply()
    }

    fun getCustomLabel(idKey: String): String? =
        prefs.getString("$KEY_RENAME_PREFIX$idKey", null)

    fun setCustomLabel(idKey: String, label: String?) {
        val trimmed = label?.trim()
        val editor = prefs.edit()
        if (trimmed.isNullOrEmpty()) {
            editor.remove("$KEY_RENAME_PREFIX$idKey")
        } else {
            editor.putString("$KEY_RENAME_PREFIX$idKey", trimmed)
        }
        editor.apply()
    }

    fun seedDefaultPinsIfFirstRun(installedApps: List<AppEntry>) {
        if (prefs.getBoolean(KEY_INITIALIZED, false)) return
        val preferredLabels = listOf(
            listOf("phone", "dialer", "call"),
            listOf("messages", "messaging", "sms"),
            listOf("chrome", "browser", "internet", "firefox"),
            listOf("camera"),
            listOf("notes", "keep", "samsung notes"),
            listOf("settings", "clock")
        )
        val seeded = mutableListOf<String>()
        for (candidates in preferredLabels) {
            val match = installedApps.firstOrNull { app ->
                !app.isWorkProfile && candidates.any { keyword ->
                    app.displayLabel == keyword || app.displayLabel.contains(keyword)
                } && !seeded.contains(app.idKey)
            }
            if (match != null) {
                seeded.add(match.idKey)
            }
        }
        if (seeded.isEmpty()) {
            installedApps.filter { !it.isWorkProfile }.take(5).forEach { seeded.add(it.idKey) }
        }
        prefs.edit()
            .putString(KEY_PINNED, seeded.joinToString("\n"))
            .putBoolean(KEY_INITIALIZED, true)
            .apply()
    }

    private inline fun <reified T : Enum<T>> parseEnum(raw: String?, fallback: T): T {
        if (raw == null) return fallback
        return try {
            enumValueOf<T>(raw)
        } catch (_: IllegalArgumentException) {
            fallback
        }
    }

    companion object {
        private const val PREFS_NAME = "minimal_launcher_prefs"
        private const val KEY_INITIALIZED = "initialized"
        private const val KEY_THEME = "theme"
        private const val KEY_ALIGNMENT = "alignment"
        private const val KEY_TEXT_SIZE = "text_size"
        private const val KEY_SHOW_CLOCK = "show_clock"
        private const val KEY_SWIPE_UP_ALL_APPS = "swipe_up_all_apps"
        private const val KEY_SWIPE_DOWN_HOME = "swipe_down_home"
        private const val KEY_SWIPE_DOWN_NOTIFICATIONS = "swipe_down_notifications"
        private const val KEY_DOUBLE_TAP_LOCK = "double_tap_lock"
        private const val KEY_PINNED = "pinned_keys"
        private const val KEY_HIDDEN = "hidden_keys"
        private const val KEY_RENAME_PREFIX = "rename_"
    }
}
