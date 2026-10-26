package com.example.namazym.widget

import android.content.Context

/**
 * Persistent widget settings. A per-widget province overrides the app-wide default, which the
 * Flutter UI edits through the `namazym/widget` method channel (see MainActivity).
 */
object WidgetPrefs {
    private const val PREFS = "prayer_widget"
    private const val DEFAULT_KEY = "default_province"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun defaultProvince(context: Context): Province =
        Province.fromCode(prefs(context).getString(DEFAULT_KEY, null))

    /** Sets the app-wide location and drops per-widget overrides so every widget follows it. */
    fun setDefaultProvince(context: Context, province: Province) {
        val prefs = prefs(context)
        prefs.edit().apply {
            prefs.all.keys.filter { it.startsWith("province_") }.forEach { remove(it) }
            putString(DEFAULT_KEY, province.code)
        }.apply()
    }

    fun province(context: Context, appWidgetId: Int): Province {
        val code = prefs(context).getString("province_$appWidgetId", null)
        return if (code == null) defaultProvince(context) else Province.fromCode(code)
    }

    fun setProvince(context: Context, appWidgetId: Int, province: Province) {
        prefs(context).edit().putString("province_$appWidgetId", province.code).apply()
    }

    fun forget(context: Context, appWidgetIds: IntArray) {
        prefs(context).edit().apply { appWidgetIds.forEach { remove("province_$it") } }.apply()
    }
}
