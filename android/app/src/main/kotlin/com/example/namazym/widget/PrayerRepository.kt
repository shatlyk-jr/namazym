package com.example.namazym.widget

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.time.LocalDate

/**
 * Read-only access to the bundled `times.db` (table `pray_times`).
 *
 * The database ships as an APK asset (copied from the repository root at build time) and is
 * unpacked once into app storage because SQLite cannot open assets directly.
 */
class PrayerRepository private constructor(private val db: SQLiteDatabase) {

    /** Row for [date] (the table has 366 rows per province, so leap days exist), or null. */
    fun dayTimes(province: Province, date: LocalDate): DayTimes? {
        val month = date.monthValue
        val day = date.dayOfMonth
        val columns = PrayerEvent.entries.map { it.column }.toTypedArray()
        db.query(
            "pray_times", columns, "province = ? AND month = ? AND day = ?",
            arrayOf(province.code, month.toString(), day.toString()), null, null, null, "1",
        ).use { c ->
            if (!c.moveToFirst()) return null
            val minutes = columns.indices.map { parseMinutes(c.getString(it)) ?: return null }
            return DayTimes(date, minutes)
        }
    }

    companion object {
        private const val ASSET = "times.db"
        private const val PREFS = "prayer_db"

        @Volatile private var instance: PrayerRepository? = null

        fun get(context: Context): PrayerRepository =
            instance ?: synchronized(this) {
                instance ?: open(context.applicationContext).also { instance = it }
            }

        private fun open(context: Context): PrayerRepository {
            val file = File(context.filesDir, ASSET)
            // Re-extract after every app install/update, since that is when the shipped asset can change.
            val stamp = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!file.exists() || prefs.getString("stamp", null) != stamp) {
                context.assets.open(ASSET).use { input -> file.outputStream().use { input.copyTo(it) } }
                prefs.edit().putString("stamp", stamp).apply()
            }
            return PrayerRepository(SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY))
        }

        internal fun parseMinutes(value: String?): Int? {
            val parts = value?.trim()?.split(":") ?: return null
            if (parts.size != 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            return if (h in 0..23 && m in 0..59) h * 60 + m else null
        }
    }
}
