package me.vripperoid.android.settings

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {
  private val prefs: SharedPreferences = context.getSharedPreferences("vripper_settings", Context.MODE_PRIVATE)

  var maxGlobalConcurrent: Int
    get() = prefs.getInt(KEY_MAX_GLOBAL, 4)
    set(value) { prefs.edit().putInt(KEY_MAX_GLOBAL, value).apply() }

  var maxConcurrentPosts: Int
    get() = prefs.getInt(KEY_MAX_CONCURRENT_POSTS, 2)
    set(value) { prefs.edit().putInt(KEY_MAX_CONCURRENT_POSTS, value).apply() }

  var maxConcurrentPerHost: Int
    get() = prefs.getInt(KEY_MAX_PER_HOST, 2)
    set(value) { prefs.edit().putInt(KEY_MAX_PER_HOST, value).apply() }

  var downloadPathUri: String?
    get() = prefs.getString(KEY_DOWNLOAD_PATH_URI, null)
    set(value) { prefs.edit().putString(KEY_DOWNLOAD_PATH_URI, value).apply() }

  var deleteFromStorage: Boolean
    get() = prefs.getBoolean(KEY_DELETE_FROM_STORAGE, false)
    set(value) { prefs.edit().putBoolean(KEY_DELETE_FROM_STORAGE, value).apply() }

  var retryCount: Int
    get() = prefs.getInt(KEY_RETRY_COUNT, 3)
    set(value) { prefs.edit().putInt(KEY_RETRY_COUNT, value).apply() }

  var isDarkMode: Boolean
    get() = prefs.getBoolean(KEY_IS_DARK_MODE, true)
    set(value) { prefs.edit().putBoolean(KEY_IS_DARK_MODE, value).apply() }

  companion object {
    private const val KEY_MAX_GLOBAL = "max_global"
    private const val KEY_MAX_CONCURRENT_POSTS = "max_concurrent_posts"
    private const val KEY_MAX_PER_HOST = "max_per_host"
    private const val KEY_DOWNLOAD_PATH_URI = "download_path_uri"
    private const val KEY_DELETE_FROM_STORAGE = "delete_from_storage"
    private const val KEY_RETRY_COUNT = "retry_count"
    private const val KEY_IS_DARK_MODE = "is_dark_mode"
  }
}
