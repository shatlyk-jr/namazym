package com.example.namazym.widget

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.example.namazym.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Location picker shown when a widget is added and when it is reconfigured (long-press).
 * Declared `configuration_optional`, so pressing back still places the widget with the default.
 */
class WidgetConfigActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val provinces = Province.entries
        val names = provinces.map { getString(resources.getIdentifier("province_${it.code}", "string", packageName)) }.toTypedArray()
        val current = WidgetPrefs.province(this, appWidgetId).ordinal
        AlertDialog.Builder(this)
            .setTitle(R.string.widget_config_title)
            .setSingleChoiceItems(names, current) { dialog, which ->
                WidgetPrefs.setProvince(this, appWidgetId, provinces[which])
                val app = applicationContext
                CoroutineScope(Dispatchers.Default).launch {
                    val glanceId = GlanceAppWidgetManager(app).getGlanceIdBy(appWidgetId)
                    PrayerWidget().update(app, glanceId)
                }
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                dialog.dismiss()
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
    }
}
