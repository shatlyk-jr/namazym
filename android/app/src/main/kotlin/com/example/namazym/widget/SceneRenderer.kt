package com.example.namazym.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Colours and visibility of the sky at one point of the day. */
data class SkyColors(val top: Int, val bottom: Int, val stars: Float)

/**
 * Draws the illustrated day/night backdrop into a bitmap. Everything is derived from the
 * real prayer times of the day: dawn starts around the Fajr time, the sun travels from the
 * sunrise to the sunset ("evening") column, and the moon crosses the sky during the night.
 *
 * The scene is a static snapshot: widgets cannot animate continuously, so it is re-rendered
 * on every widget update (see [WidgetScheduler]).
 */
object SceneRenderer {

    private class Key(val minute: Float, val colors: SkyColors)

    private val NIGHT = SkyColors(0xFF070B24.toInt(), 0xFF1B2A5E.toInt(), 1f)

    /** Sky gradient at [nowMin] (minutes after midnight, fractional) for the given day. */
    fun sky(day: DayTimes, nowMin: Float): SkyColors {
        val fajr = day.minuteOf(PrayerEvent.FASTING).toFloat()
        val rise = day.minuteOf(PrayerEvent.SUNRISE).toFloat()
        val noon = day.minuteOf(PrayerEvent.NOON).toFloat()
        val set = day.minuteOf(PrayerEvent.EVENING).toFloat()
        val keys = listOf(
            Key(fajr - 40, NIGHT),
            Key(fajr + 20, SkyColors(0xFF141B4D.toInt(), 0xFF4B3F86.toInt(), 0.8f)),
            Key(rise - 15, SkyColors(0xFF3B4294.toInt(), 0xFFFF8F66.toInt(), 0.2f)),
            Key(rise + 25, SkyColors(0xFF3F7FD0.toInt(), 0xFFF4A460.toInt(), 0f)),
            Key(noon, SkyColors(0xFF1D68C9.toInt(), 0xFF6DB4EA.toInt(), 0f)),
            Key(set - 100, SkyColors(0xFF3A78C8.toInt(), 0xFFE8B25E.toInt(), 0f)),
            Key(set - 5, SkyColors(0xFF54479A.toInt(), 0xFFF2713F.toInt(), 0f)),
            Key(set + 35, SkyColors(0xFF272769.toInt(), 0xFFB04A6A.toInt(), 0.45f)),
            Key(set + 80, NIGHT),
        ).sortedBy { it.minute }
        if (nowMin <= keys.first().minute || nowMin >= keys.last().minute) return NIGHT
        val i = keys.indexOfLast { it.minute <= nowMin }
        val a = keys[i]
        val b = keys[i + 1]
        val t = ((nowMin - a.minute) / (b.minute - a.minute)).coerceIn(0f, 1f)
        return SkyColors(
            lerp(a.colors.top, b.colors.top, t),
            lerp(a.colors.bottom, b.colors.bottom, t),
            a.colors.stars + (b.colors.stars - a.colors.stars) * t,
        )
    }

    fun render(width: Int, height: Int, day: DayTimes, nowMin: Float, seed: Long): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = min(w, h) * 0.16f
        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w, h), radius, radius, Path.Direction.CW) })

        val sky = sky(day, nowMin)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, h, sky.top, sky.bottom, Shader.TileMode.CLAMP)
        c.drawPaint(paint)
        paint.shader = null

        val horizon = h * 0.80f
        val rise = day.minuteOf(PrayerEvent.SUNRISE).toFloat()
        val set = day.minuteOf(PrayerEvent.EVENING).toFloat()

        drawStars(c, paint, w, horizon, sky.stars, seed)
        drawClouds(c, paint, w, h, sky, seed)

        // Sun: arcs between sunrise and sunset, slightly past the edges so it dips below the hills.
        val sunP = (nowMin - rise) / (set - rise)
        if (sunP in -0.04f..1.04f) {
            drawSun(c, paint, w, h, horizon, sunP, sky)
        }
        // Moon: crosses the sky between sunset and the next sunrise.
        if (sky.stars > 0.05f || nowMin < rise || nowMin > set) {
            val nightLen = (1440f - set) + rise
            val moonP = if (nowMin >= set) (nowMin - set) / nightLen else (nowMin + 1440f - set) / nightLen
            drawMoon(c, paint, w, h, horizon, moonP.coerceIn(0f, 1f), sky, nowMin in (rise - 30f)..(set + 30f))
        }

        drawSkyline(c, paint, w, h, horizon, sky)

        // Scrim keeps the white overlay text readable on bright skies.
        paint.shader = LinearGradient(
            0f, 0f, 0f, h, Color.argb(70, 0, 0, 0), Color.argb(120, 0, 0, 0), Shader.TileMode.CLAMP,
        )
        c.drawPaint(paint)
        return bmp
    }

    private fun drawStars(c: Canvas, p: Paint, w: Float, horizon: Float, alpha: Float, seed: Long) {
        if (alpha <= 0.02f) return
        val rnd = Random(seed)
        val count = (w * horizon / 2600f).toInt().coerceIn(14, 70)
        p.style = Paint.Style.FILL
        repeat(count) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * horizon * 0.85f
            val size = (0.5f + rnd.nextFloat() * 1.4f) * (w / 360f).coerceIn(0.7f, 1.6f)
            val a = (alpha * (0.35f + rnd.nextFloat() * 0.65f) * 255).toInt()
            p.color = Color.argb(a, 255, 250, 235)
            c.drawCircle(x, y, size, p)
            if (size > 1.4f) { // a few brighter stars get a cross glint
                p.strokeWidth = 0.8f
                c.drawLine(x - size * 2.2f, y, x + size * 2.2f, y, p)
                c.drawLine(x, y - size * 2.2f, x, y + size * 2.2f, p)
            }
        }
    }

    private fun drawClouds(c: Canvas, p: Paint, w: Float, h: Float, sky: SkyColors, seed: Long) {
        val daylight = 1f - sky.stars
        if (daylight < 0.25f) return
        val rnd = Random(seed + 7)
        p.style = Paint.Style.FILL
        p.color = Color.argb((60 * daylight).toInt(), 255, 255, 255)
        repeat(3) {
            val cx = rnd.nextFloat() * w
            val cy = h * (0.15f + rnd.nextFloat() * 0.35f)
            val cw = w * (0.10f + rnd.nextFloat() * 0.08f)
            c.drawOval(RectF(cx - cw, cy - cw * 0.22f, cx + cw, cy + cw * 0.22f), p)
            c.drawOval(RectF(cx - cw * 0.55f, cy - cw * 0.42f, cx + cw * 0.5f, cy + cw * 0.1f), p)
        }
    }

    private fun arcPoint(w: Float, h: Float, horizon: Float, progress: Float): Pair<Float, Float> {
        val x = w * (0.10f + 0.80f * progress)
        val peak = h * 0.20f
        val y = horizon - (horizon - peak) * sin(PI.toFloat() * progress.coerceIn(0f, 1f)) +
            if (progress < 0f || progress > 1f) h * 0.05f else 0f
        return x to y
    }

    private fun drawSun(c: Canvas, p: Paint, w: Float, h: Float, horizon: Float, progress: Float, sky: SkyColors) {
        val (x, y) = arcPoint(w, h, horizon, progress)
        val r = max(min(w, h) * 0.075f, 6f)
        val low = 1f - sin(PI.toFloat() * progress.coerceIn(0f, 1f)) // 1 near the horizon
        val core = lerp(0xFFFFE070.toInt(), 0xFFFF9A3C.toInt(), low)
        p.shader = RadialGradient(x, y, r * 4.2f, Color.argb(150, Color.red(core), Color.green(core), Color.blue(core)), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * 4.2f, p)
        p.shader = null
        p.color = core
        c.drawCircle(x, y, r, p)
    }

    private fun drawMoon(c: Canvas, p: Paint, w: Float, h: Float, horizon: Float, progress: Float, sky: SkyColors, dim: Boolean) {
        val (x, y) = arcPoint(w, h, horizon, progress)
        val r = max(min(w, h) * 0.065f, 5f)
        val strength = if (dim) 0.5f else 1f
        p.shader = RadialGradient(x, y, r * 3.6f, Color.argb((90 * strength).toInt(), 190, 205, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * 3.6f, p)
        p.shader = null
        // Crescent = full disc minus an offset disc.
        val moon = Path().apply { addCircle(x, y, r, Path.Direction.CW) }
        val bite = Path().apply { addCircle(x + r * 0.55f, y - r * 0.2f, r * 0.9f, Path.Direction.CW) }
        moon.op(bite, Path.Op.DIFFERENCE)
        p.color = Color.argb((255 * strength).toInt(), 250, 244, 214)
        c.drawPath(moon, p)
    }

    private fun drawSkyline(c: Canvas, p: Paint, w: Float, h: Float, horizon: Float, sky: SkyColors) {
        val dark = lerp(sky.bottom, Color.BLACK, 0.72f)
        val mid = lerp(sky.bottom, Color.BLACK, 0.55f)
        p.style = Paint.Style.FILL
        p.shader = null
        // Back dunes.
        p.color = mid
        c.drawPath(Path().apply {
            moveTo(0f, h)
            lineTo(0f, horizon - h * 0.03f)
            cubicTo(w * 0.25f, horizon - h * 0.12f, w * 0.45f, horizon + h * 0.02f, w * 0.7f, horizon - h * 0.06f)
            cubicTo(w * 0.85f, horizon - h * 0.10f, w * 0.95f, horizon - h * 0.02f, w, horizon - h * 0.04f)
            lineTo(w, h)
            close()
        }, p)
        // Mosque, standing on the front dune.
        p.color = dark
        val u = min(h * 0.30f, w * 0.30f)
        val bx = w * 0.72f
        val by = horizon + h * 0.04f
        val body = RectF(bx - u * 0.55f, by - u * 0.42f, bx + u * 0.55f, by + 1f)
        c.drawRect(body, p)
        c.drawArc(RectF(bx - u * 0.38f, by - u * 0.42f - u * 0.38f, bx + u * 0.38f, by - u * 0.42f + u * 0.38f), 180f, 180f, true, p)
        c.drawRect(bx - u * 0.015f, by - u * 0.42f - u * 0.52f, bx + u * 0.015f, by - u * 0.42f - u * 0.36f, p) // finial
        for (side in listOf(-1f, 1f)) { // minarets
            val mx = bx + side * u * 0.68f
            c.drawRect(mx - u * 0.045f, by - u * 0.95f, mx + u * 0.045f, by + 1f, p)
            c.drawRect(mx - u * 0.07f, by - u * 0.72f, mx + u * 0.07f, by - u * 0.68f, p) // balcony
            c.drawPath(Path().apply {
                moveTo(mx - u * 0.055f, by - u * 0.95f)
                lineTo(mx, by - u * 1.15f)
                lineTo(mx + u * 0.055f, by - u * 0.95f)
                close()
            }, p)
        }
        // Front dune.
        c.drawPath(Path().apply {
            moveTo(0f, h)
            lineTo(0f, horizon + h * 0.08f)
            cubicTo(w * 0.3f, horizon - h * 0.02f, w * 0.55f, horizon + h * 0.12f, w, horizon + h * 0.06f)
            lineTo(w, h)
            close()
        }, p)
        // Lit windows after dark.
        if (sky.stars > 0.3f) {
            p.color = Color.argb((230 * sky.stars).toInt(), 255, 206, 110)
            for (dx in listOf(-0.3f, 0f, 0.3f)) {
                c.drawRect(bx + u * dx - u * 0.03f, by - u * 0.28f, bx + u * dx + u * 0.03f, by - u * 0.18f, p)
            }
        }
    }

    internal fun lerp(a: Int, b: Int, t: Float): Int = Color.argb(
        (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t).toInt(),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )
}
