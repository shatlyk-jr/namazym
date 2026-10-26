package com.example.namazym.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Battery-conscious refresh strategy. The per-second countdown is drawn by the platform's
 * Chronometer, so the widget itself only needs re-rendering when something changes:
 *  - a prayer boundary is reached (one alarm for the next boundary),
 *  - the sky scene drifts (WorkManager, 15 min - the platform minimum),
 *  - the clock/timezone/date changes or the app is updated (broadcasts).
 */
object WidgetScheduler {
    private const val WORK_NAME = "prayer_widget_refresh"
    const val ACTION_REFRESH = "com.example.namazym.widget.REFRESH"

    fun ensurePeriodicWork(context: Context) {
        val request = PeriodicWorkRequestBuilder<PrayerRefreshWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        alarmManager(context).cancel(pendingIntent(context))
    }

    /** Arms one alarm just after [nextAt]. Exact when the platform allows it, otherwise inexact (may be delayed). */
    fun schedule(context: Context, nextAt: LocalDateTime?) {
        nextAt ?: return
        val triggerAt = nextAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1_000
        val am = alarmManager(context)
        val pi = pendingIntent(context)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pi)
        }
    }

    private fun alarmManager(context: Context) = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, RefreshReceiver::class.java).setAction(ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Receives the boundary alarm and system time changes, then redraws all widgets. */
class RefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                PrayerWidget().updateAll(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}

class PrayerRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        PrayerWidget().updateAll(applicationContext)
        return Result.success()
    }
}
