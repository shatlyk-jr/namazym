package com.example.namazym.widget

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Uses the real repository-root times.db (copied into test assets by Gradle) and native bitmap rendering. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SceneAndDatabaseTest {

    private fun repo() = PrayerRepository.get(ApplicationProvider.getApplicationContext())

    @Test fun reads_real_rows() {
        val d = repo().dayTimes(Province.AHAL, LocalDate.of(2026, 1, 1))
        assertNotNull(d)
        assertEquals("06:32", d!!.label(PrayerEvent.FASTING))
        assertEquals("18:10", d.label(PrayerEvent.EVENING))
        assertEquals("19:30", d.label(PrayerEvent.NIGHT))
    }

    @Test fun every_province_and_calendar_day_resolves() {
        val repo = repo()
        var date = LocalDate.of(2024, 1, 1) // leap year covers 29 Feb
        while (date.year == 2024) {
            for (p in Province.entries) assertNotNull("$p $date", repo.dayTimes(p, date))
            date = date.plusDays(1)
        }
    }

    @Test fun scenes_render_for_all_day_phases() {
        val day = repo().dayTimes(Province.AHAL, LocalDate.of(2026, 6, 21))!!
        val out = File(System.getProperty("scene.out") ?: "build/scenes").also { it.mkdirs() }
        val moments = mapOf("0200" to 120f, "0500" to 300f, "0600" to 360f, "0900" to 540f, "1300" to 780f, "1800" to 1080f, "2030" to 1230f, "2100" to 1260f, "2300" to 1380f)
        for ((label, min) in moments) {
            for ((name, size) in mapOf("wide" to (480 to 106), "medium" to (480 to 211), "large" to (400 to 400))) {
                val bmp = SceneRenderer.render(size.first, size.second, day, min, 1L)
                assertTrue(bmp.width == size.first)
                File(out, "${name}_$label.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    @Test fun sky_is_dark_at_night_and_bright_at_noon() {
        val day = repo().dayTimes(Province.AHAL, LocalDate.of(2026, 6, 21))!!
        assertEquals(1f, SceneRenderer.sky(day, 60f).stars, 0f)
        assertEquals(0f, SceneRenderer.sky(day, 13 * 60f).stars, 0f)
    }
}
