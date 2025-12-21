package me.vripperoid.android.util

import android.util.Log

object LogUtils {
    fun d(tag: String, msg: String) = Log.d(tag, msg)
    fun i(tag: String, msg: String) = Log.i(tag, msg)
    fun e(tag: String, msg: String, tr: Throwable? = null) = Log.e(tag, msg, tr)
}
