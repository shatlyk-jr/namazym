package com.example.namazym

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import com.example.namazym.widget.PrayerWidgetReceiver
import com.example.namazym.widget.Province
import com.example.namazym.widget.WidgetPrefs
import com.example.namazym.widget.updateAllPrayerWidgets
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "namazym/widget").setMethodCallHandler { call, result ->
            when (call.method) {
                "getProvince" -> result.success(WidgetPrefs.defaultProvince(this).code)
                "setProvince" -> {
                    val code = call.arguments as? String
                    val province = Province.entries.firstOrNull { it.code == code }
                    if (province == null) {
                        result.error("bad_province", "Unknown province: $code", null)
                    } else {
                        WidgetPrefs.setDefaultProvince(this, province)
                        CoroutineScope(Dispatchers.Default).launch { updateAllPrayerWidgets(applicationContext) }
                        result.success(null)
                    }
                }
                "requestPinWidget" -> {
                    val manager = AppWidgetManager.getInstance(this)
                    val supported = manager.isRequestPinAppWidgetSupported
                    if (supported) manager.requestPinAppWidget(ComponentName(this, PrayerWidgetReceiver::class.java), null, null)
                    result.success(supported)
                }
                else -> result.notImplemented()
            }
        }
    }
}
