package me.vripper.android.settings

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {
  private val prefs: SharedPreferences = context.getSharedPreferences("vripper_settings", Context.MODE_PRIVATE)

  var maxGlobalConcurrent: Int
    get() = prefs.getInt(KEY_MAX_GLOBAL, 4)
    set(value) { prefs.edit().putInt(KEY_MAX_GLOBAL, value).apply() }

  var maxConcurrentPerHost: Int
    get() = prefs.getInt(KEY_MAX_PER_HOST, 2)
    set(value) { prefs.edit().putInt(KEY_MAX_PER_HOST, value).apply() }

  companion object {
    private const val KEY_MAX_GLOBAL = "max_global"
    private const val KEY_MAX_PER_HOST = "max_per_host"
  }
}
