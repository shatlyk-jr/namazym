package com.example.namazym.widget

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerCalculatorTest {
    // Ahal, 1 January from times.db: 06:32 07:12 08:22 13:30 16:30 18:10 19:30
    private fun day(date: LocalDate, shift: Int = 0) =
        DayTimes(date, listOf(392, 432, 502, 810, 990, 1090, 1170).map { it + shift })

    private val d = LocalDate.of(2026, 1, 15)
    private fun status(now: LocalDateTime) =
        PrayerCalculator.status(now, day(d.minusDays(1)), day(d), day(d.plusDays(1)))

    @Test fun midday_is_noon_period_and_afternoon_next() {
        val s = status(d.atTime(14, 0))
        assertEquals(PrayerEvent.NOON, s.current)
        assertEquals(PrayerEvent.AFTERNOON, s.next)
        assertEquals(d.atTime(16, 30), s.nextAt)
        assertFalse(s.nextIsTomorrow)
    }

    @Test fun exactly_on_a_boundary_starts_the_new_period() {
        val s = status(d.atTime(13, 30))
        assertEquals(PrayerEvent.NOON, s.current)
        assertEquals(PrayerEvent.AFTERNOON, s.next)
    }

    @Test fun after_isha_next_is_tomorrows_fasting() {
        val s = status(d.atTime(22, 0))
        assertEquals(PrayerEvent.NIGHT, s.current)
        assertEquals(PrayerEvent.FASTING, s.next)
        assertEquals(d.plusDays(1).atTime(6, 32), s.nextAt)
        assertTrue(s.nextIsTomorrow)
    }

    @Test fun before_dawn_current_is_yesterdays_isha() {
        val s = status(d.atTime(2, 0))
        assertEquals(PrayerEvent.NIGHT, s.current)
        assertEquals(d.minusDays(1).atTime(19, 30), s.currentStart)
        assertEquals(PrayerEvent.FASTING, s.next)
        assertFalse(s.nextIsTomorrow)
    }

    @Test fun parseMinutes_validates_format() {
        assertEquals(392, PrayerRepository.parseMinutes("06:32"))
        assertNull(PrayerRepository.parseMinutes("6.32"))
        assertNull(PrayerRepository.parseMinutes("25:00"))
        assertNull(PrayerRepository.parseMinutes(null))
    }

    @Test fun formatMinutes_pads() {
        assertEquals("06:05", formatMinutes(365))
    }

    @Test fun province_codes_match_database() {
        assertEquals(listOf("ag", "bn", "dz", "lb", "mr"), Province.entries.map { it.code })
        assertEquals(Province.AHAL, Province.MARY.next())
    }
}
