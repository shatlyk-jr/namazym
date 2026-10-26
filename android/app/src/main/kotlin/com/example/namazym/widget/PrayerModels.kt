package com.example.namazym.widget

import java.time.LocalDate
import java.time.LocalDateTime

/** The seven daily columns of `pray_times`, in chronological order. */
enum class PrayerEvent(val column: String) {
    FASTING("fasting"),
    MORNING("morning"),
    SUNRISE("sunrise"),
    NOON("noon"),
    AFTERNOON("afternoon"),
    EVENING("evening"),
    NIGHT("night"),
}

/** Values of `pray_times.province`. */
enum class Province(val code: String) {
    AHAL("ag"),
    BALKAN("bn"),
    DASHOGUZ("dz"),
    LEBAP("lb"),
    MARY("mr");

    fun next(): Province = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromCode(code: String?): Province = entries.firstOrNull { it.code == code } ?: AHAL
    }
}

/** One row of `pray_times`: minutes after midnight for every [PrayerEvent]. */
data class DayTimes(val date: LocalDate, val minutes: List<Int>) {
    init {
        require(minutes.size == PrayerEvent.entries.size)
    }

    fun minuteOf(event: PrayerEvent): Int = minutes[event.ordinal]

    fun at(event: PrayerEvent): LocalDateTime =
        date.atStartOfDay().plusMinutes(minuteOf(event).toLong())

    fun label(event: PrayerEvent): String = formatMinutes(minuteOf(event))
}

fun formatMinutes(total: Int): String = "%02d:%02d".format(total / 60, total % 60)

/** Where the clock currently sits inside the prayer timeline. */
data class PrayerStatus(
    val current: PrayerEvent,
    val currentStart: LocalDateTime,
    val next: PrayerEvent,
    val nextAt: LocalDateTime,
    val today: DayTimes,
    /** True when [next] belongs to the following calendar day. */
    val nextIsTomorrow: Boolean,
)
