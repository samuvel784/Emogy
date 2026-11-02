package com.vurka.emogy.data.time

import com.vurka.emogy.data.StreakCalculator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Injectable wall clock so every reset rule is testable without waiting for midnight. */
fun interface TaskClock {
    fun nowMillis(): Long
}

object SystemTaskClock : TaskClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/**
 * All calendar maths for tasks, login rewards and the 30-day streak.
 *
 * Everything is expressed in *local calendar days*: a day is a local date, not 24 hours, so a DST
 * shift can never skip or repeat a reset. `java.time` is avoided because `minSdk` is 24 and no
 * desugaring is configured; [Calendar] with an explicit [TimeZone] gives the same guarantees.
 *
 * Week rule: weeks start on Sunday. `floorDiv(epochDay + 4, 7)` returns a new value exactly at
 * Sunday 00:00 local time (1970-01-01 was a Thursday, hence the +4).
 */
class GameCalendar(
    private val clock: TaskClock = SystemTaskClock,
    val timeZone: TimeZone = TimeZone.getDefault(),
) {
    fun nowMillis(): Long = clock.nowMillis()

    fun epochDayAt(millis: Long): Long = StreakCalculator.localEpochDay(millis, timeZone)

    fun today(): Long = epochDayAt(nowMillis())

    /** 0 = Sunday … 6 = Saturday. */
    fun dayOfWeek(epochDay: Long): Int = Math.floorMod(epochDay + 4L, 7L).toInt()

    /** Changes at Sunday 00:00 local time. */
    fun weekKey(epochDay: Long): Int = Math.floorDiv(epochDay + 4L, 7L).toInt()

    fun weekStart(epochDay: Long): Long = epochDay - dayOfWeek(epochDay)

    /** `YYYY-MM` in local time. */
    fun monthKey(epochDay: Long): String {
        val cal = localNoon(epochDay)
        return String.format(
            Locale.US,
            "%04d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
        )
    }

    fun monthStart(epochDay: Long): Long {
        val cal = localNoon(epochDay)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return epochDayAt(cal.timeInMillis)
    }

    /** The first instant of day 28, when monthly progress stops. */
    fun monthDeadline(epochDay: Long): Long = monthStart(epochDay) + 27L

    fun daysUntilMonthlyDeadline(epochDay: Long): Int =
        (monthDeadline(epochDay) - epochDay).coerceAtLeast(0L).toInt()

    fun isMonthlyWindowOpen(epochDay: Long): Boolean = epochDay < monthDeadline(epochDay)

    /** First instant of the next local day, expressed as epoch millis. */
    fun nextMidnightMillis(): Long {
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = nowMillis()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis
    }

    fun dayLabel(epochDay: Long): String =
        format(epochDay, "EEE, MMM d")

    fun shortDayLabel(epochDay: Long): String =
        format(epochDay, "EEE")

    fun monthLabel(epochDay: Long): String =
        format(epochDay, "MMMM")

    fun deadlineLabel(epochDay: Long): String =
        format(monthDeadline(epochDay), "MMM d")

    /** Noon on the given local date: safe from DST transitions, which happen around midnight. */
    private fun localNoon(epochDay: Long): Calendar {
        val cal = Calendar.getInstance(timeZone)
        cal.clear()
        cal.set(1970, Calendar.JANUARY, 1, 12, 0, 0)
        cal.add(Calendar.DAY_OF_MONTH, epochDay.toInt())
        return cal
    }

    private fun format(epochDay: Long, pattern: String): String {
        val cal = localNoon(epochDay)
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(cal.timeInMillis))
    }
}
