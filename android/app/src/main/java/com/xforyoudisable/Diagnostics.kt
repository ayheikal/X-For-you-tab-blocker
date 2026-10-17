package com.xforyoudisable

import android.content.Context
import java.text.DateFormat
import java.util.Date

/** What the service last saw, shown in MainActivity so it can be copied without adb. */
object Diagnostics {
    const val KEY_CONNECTED = "connected"
    const val KEY_REPORT = "report"

    private const val PREFS = "diagnostics"

    fun timestamp(): String = DateFormat.getDateTimeInstance().format(Date())

    fun write(context: Context, key: String, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, value).apply()
    }

    fun read(context: Context, key: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null)
}
