package com.example.namazym.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.namazym.MainActivity
import com.example.namazym.R
import java.time.Duration

import java.time.LocalDateTime

private val TINY = DpSize(110.dp, 55.dp)
private val WIDE = DpSize(250.dp, 55.dp)
private val SMALL = DpSize(110.dp, 110.dp)
private val MEDIUM = DpSize(250.dp, 110.dp)
private val LARGE = DpSize(250.dp, 250.dp)

private val GOLD = Color(0xFFFFD27A)
private val WHITE = Color.White
private val WHITE_DIM = Color(0xB3FFFFFF)
private val HILITE = Color(0x40FFFFFF)

/** Everything one render needs, computed once per update in [PrayerWidget.provideGlance]. */
private class WidgetModel(
    val province: Province,
    val status: PrayerStatus?,
    val scenes: Map<DpSize, Bitmap>,
    /** Base for the platform Chronometer: SystemClock.elapsedRealtime() + time until next event. */
    val countdownBase: Long,
)

class PrayerWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(TINY, WIDE, SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val model = loadModel(context, WidgetPrefs.province(context, appWidgetId))
        WidgetScheduler.schedule(context, model.status?.nextAt)
        provideContent { Content(model) }
    }

    private fun loadModel(context: Context, province: Province): WidgetModel {
        val now = LocalDateTime.now()
        val repo = PrayerRepository.get(context)
        val today = repo.dayTimes(province, now.toLocalDate())
        val status = today?.let {
            val yesterday = repo.dayTimes(province, now.toLocalDate().minusDays(1)) ?: it
            val tomorrow = repo.dayTimes(province, now.toLocalDate().plusDays(1)) ?: it
            PrayerCalculator.status(now, yesterday.copy(date = now.toLocalDate().minusDays(1)), it, tomorrow.copy(date = now.toLocalDate().plusDays(1)))
        }
        val nowMin = now.hour * 60f + now.minute + now.second / 60f
        // Pixel sizes are capped: all responsive variants live in one RemoteViews and bitmaps count against its memory budget.
        val scenes = if (today == null) emptyMap() else listOf(TINY, WIDE, SMALL, MEDIUM, LARGE).associateWith {
            val w = (it.width.value * 2.4f).toInt().coerceAtMost(480)
            val h = (w * it.height.value / it.width.value).toInt()
            SceneRenderer.render(w, h, today, nowMin, now.toLocalDate().toEpochDay())
        }
        val remaining = status?.let { Duration.between(now, it.nextAt).toMillis().coerceAtLeast(0) } ?: 0
        return WidgetModel(province, status, scenes, SystemClock.elapsedRealtime() + remaining)
    }
}

class PrayerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PrayerWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetScheduler.ensurePeriodicWork(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetScheduler.ensurePeriodicWork(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetScheduler.cancelAll(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        WidgetPrefs.forget(context, appWidgetIds)
    }
}

// ---- Actions ------------------------------------------------------------------------------

/** Tap on the location chip: switch this widget to the next province. */
class CycleProvinceAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        WidgetPrefs.setProvince(context, id, WidgetPrefs.province(context, id).next())
        PrayerWidget().update(context, glanceId)
    }
}

/** Tap on the refresh glyph: re-read the clock and redraw. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PrayerWidget().update(context, glanceId)
    }
}

// ---- UI -------------------------------------------------------------------------------------

@Composable
private fun Content(model: WidgetModel) {
    val size = LocalSize.current
    // LocalSize is one of the sizes declared above; pick the matching layout.
    val scene = model.scenes.entries.firstOrNull { it.key == size }?.value
    var modifier = GlanceModifier.fillMaxSize()
    if (scene != null) {
        modifier = modifier.background(ImageProvider(scene), ContentScale.Crop)
    } else {
        modifier = modifier.background(Color(0xFF1B2A5E)).cornerRadius(24.dp)
    }
    // Tapping anywhere that has no own action opens the app.
    modifier = modifier.clickable(actionStartActivity(Intent(LocalContext.current, MainActivity::class.java)))

    Box(modifier) {
        val status = model.status
        if (status == null) {
            Text(
                LocalContext.current.getString(R.string.widget_no_data),
                modifier = GlanceModifier.padding(12.dp),
                style = TextStyle(color = ColorProvider(WHITE), fontSize = 13.sp),
            )
            return@Box
        }
        when (size) {
            TINY -> TinyLayout(model, status)
            WIDE -> WideLayout(model, status)
            SMALL -> SmallLayout(model, status)
            MEDIUM -> MediumLayout(model, status)
            else -> LargeLayout(model, status)
        }
    }
}

@Composable
private fun name(event: PrayerEvent): String = LocalContext.current.getString(
    when (event) {
        PrayerEvent.FASTING -> R.string.prayer_fasting
        PrayerEvent.MORNING -> R.string.prayer_morning
        PrayerEvent.SUNRISE -> R.string.prayer_sunrise
        PrayerEvent.NOON -> R.string.prayer_noon
        PrayerEvent.AFTERNOON -> R.string.prayer_afternoon
        PrayerEvent.EVENING -> R.string.prayer_evening
        PrayerEvent.NIGHT -> R.string.prayer_night
    },
)

@Composable
private fun provinceName(p: Province): String = LocalContext.current.getString(
    when (p) {
        Province.AHAL -> R.string.province_ag
        Province.BALKAN -> R.string.province_bn
        Province.DASHOGUZ -> R.string.province_dz
        Province.LEBAP -> R.string.province_lb
        Province.MARY -> R.string.province_mr
    },
)

/** Time label of [event] as it will next occur ("18:10"). */
private fun nextTimeLabel(status: PrayerStatus): String = formatMinutes(status.nextAt.hour * 60 + status.nextAt.minute)

private fun text(color: Color = WHITE, size: Int, bold: Boolean = false) =
    TextStyle(color = ColorProvider(color), fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

/** Ticking countdown rendered by the platform Chronometer (counts down every second without widget updates). */
@Composable
private fun Countdown(model: WidgetModel, textSizeSp: Float, color: Int = 0xFFFFFFFF.toInt()) {
    val context = LocalContext.current
    val views = RemoteViews(context.packageName, R.layout.widget_countdown).apply {
        setChronometerCountDown(R.id.countdown, true)
        setChronometer(R.id.countdown, model.countdownBase, null, true)
        setTextViewTextSize(R.id.countdown, TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        setTextColor(R.id.countdown, color)
    }
    AndroidRemoteViews(views)
}

@Composable
private fun LocationChip(model: WidgetModel, sizeSp: Int = 11) {
    Text(
        "◉ " + provinceName(model.province),
        modifier = GlanceModifier.clickable(actionRunCallback<CycleProvinceAction>()).padding(vertical = 2.dp, horizontal = 2.dp),
        style = text(WHITE_DIM, sizeSp, bold = true),
        maxLines = 1,
    )
}

@Composable
private fun RefreshButton() {
    Text(
        "↻",
        modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()).padding(horizontal = 4.dp),
        style = text(WHITE_DIM, 16),
    )
}

@Composable
private fun TinyLayout(model: WidgetModel, status: PrayerStatus) {
    Column(GlanceModifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name(status.next) + "  " + nextTimeLabel(status), style = text(WHITE, 12, true), maxLines = 1)
        Countdown(model, 17f, 0xFFFFD27A.toInt())
    }
}

@Composable
private fun WideLayout(model: WidgetModel, status: PrayerStatus) {
    Row(GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            LocationChip(model, 10)
            Text(name(status.next) + "  " + nextTimeLabel(status), style = text(WHITE, 16, true), maxLines = 1)
        }
        Countdown(model, 24f, 0xFFFFD27A.toInt())
    }
}

@Composable
private fun SmallLayout(model: WidgetModel, status: PrayerStatus) {
    Column(GlanceModifier.fillMaxSize().padding(10.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.defaultWeight()) { LocationChip(model, 10) }
            RefreshButton()
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(LocalContext.current.getString(R.string.widget_next), style = text(WHITE_DIM, 11))
        Text(name(status.next), style = text(WHITE, 20, true), maxLines = 1)
        Text(nextTimeLabel(status), style = text(WHITE, 13), maxLines = 1)
        Countdown(model, 20f, 0xFFFFD27A.toInt())
    }
}

@Composable
private fun MediumLayout(model: WidgetModel, status: PrayerStatus) {
    Column(GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LocationChip(model)
            Spacer(GlanceModifier.width(8.dp))
            Text(
                LocalContext.current.getString(R.string.widget_now) + ": " + name(status.current),
                style = text(WHITE_DIM, 11),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            RefreshButton()
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Text(name(status.next) + "  " + nextTimeLabel(status), style = text(WHITE, 20, true), maxLines = 1)
            }
            Countdown(model, 22f, 0xFFFFD27A.toInt())
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth()) {
            PrayerEvent.entries.forEach { event ->
                val active = event == status.current
                Column(
                    GlanceModifier.defaultWeight().then(if (active) GlanceModifier.background(HILITE).cornerRadius(6.dp) else GlanceModifier).padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(name(event), style = text(if (active) GOLD else WHITE_DIM, 8), maxLines = 1)
                    Text(status.today.label(event), style = text(WHITE, 10, active), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun LargeLayout(model: WidgetModel, status: PrayerStatus) {
    Column(GlanceModifier.fillMaxSize().padding(14.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.defaultWeight()) { LocationChip(model, 13) }
            RefreshButton()
        }
        Spacer(GlanceModifier.height(2.dp))
        Text(LocalContext.current.getString(R.string.widget_next), style = text(WHITE_DIM, 12))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Text(name(status.next), style = text(WHITE, 26, true), maxLines = 1)
                val tomorrow = if (status.nextIsTomorrow) " · " + LocalContext.current.getString(R.string.widget_tomorrow) else ""
                Text(nextTimeLabel(status) + tomorrow, style = text(WHITE, 14), maxLines = 1)
            }
            Countdown(model, 30f, 0xFFFFD27A.toInt())
        }
        Spacer(GlanceModifier.defaultWeight())
        PrayerEvent.entries.forEach { event ->
            val active = event == status.current
            Row(
                GlanceModifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                    .then(if (active) GlanceModifier.background(HILITE).cornerRadius(8.dp) else GlanceModifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(name(event), style = text(if (active) GOLD else WHITE, 13, active), modifier = GlanceModifier.defaultWeight(), maxLines = 1)
                Text(status.today.label(event), style = text(if (active) GOLD else WHITE, 13, active), maxLines = 1)
            }
        }
    }
}

/** Helper for the config activity and Flutter bridge: redraw every placed widget. */
suspend fun updateAllPrayerWidgets(context: Context) {
    PrayerWidget().updateAll(context)
}

