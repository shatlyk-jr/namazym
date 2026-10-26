package com.example.namazym.widget

import java.time.LocalDateTime

/** Pure timeline logic (no Android dependencies) so it can be unit tested on the JVM. */
object PrayerCalculator {

    fun status(
        now: LocalDateTime,
        yesterday: DayTimes,
        today: DayTimes,
        tomorrow: DayTimes,
    ): PrayerStatus {
        val timeline = buildList {
            for (day in listOf(yesterday, today, tomorrow)) {
                for (event in PrayerEvent.entries) add(event to day.at(event))
            }
        }
        // First event strictly after `now`; the one before it is the running period.
        val nextIndex = timeline.indexOfFirst { it.second.isAfter(now) }
        check(nextIndex > 0) { "now=$now is outside the 3-day window" }
        val (current, currentStart) = timeline[nextIndex - 1]
        val (next, nextAt) = timeline[nextIndex]
        return PrayerStatus(
            current = current,
            currentStart = currentStart,
            next = next,
            nextAt = nextAt,
            today = today,
            nextIsTomorrow = nextAt.toLocalDate() != now.toLocalDate(),
        )
    }
}
