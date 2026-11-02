package com.vurka.emogy

import com.vurka.emogy.data.time.GameCalendar
import com.vurka.emogy.data.time.TaskClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class GameCalendarTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val newYork = TimeZone.getTimeZone("America/New_York")

    private fun millis(
        zone: TimeZone,
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
        second: Int = 0,
    ): Long = Calendar.getInstance(zone).apply {
        clear()
        set(year, month - 1, day, hour, minute, second)
    }.timeInMillis

    private fun calendar(zone: TimeZone = utc, now: Long): GameCalendar =
        GameCalendar(TaskClock { now }, zone)

    @Test
    fun `local day changes at midnight not at a fixed 24 hour offset`() {
        val cal = calendar(now = millis(utc, 2024, 3, 1, 23, 59, 59))
        val lateToday = cal.today()
        val earlyToday = calendar(now = millis(utc, 2024, 3, 1, 0, 0, 1)).today()
        val tomorrow = calendar(now = millis(utc, 2024, 3, 2, 0, 0, 1)).today()

        assertEquals(lateToday, earlyToday)
        assertEquals(lateToday + 1, tomorrow)
    }

    @Test
    fun `week key changes exactly at Sunday midnight`() {
        val saturday = calendar(now = millis(utc, 2024, 6, 1, 23, 59, 59))
        val sunday = calendar(now = millis(utc, 2024, 6, 2, 0, 0, 0))

        assertEquals(6, saturday.dayOfWeek(saturday.today()))
        assertEquals(0, sunday.dayOfWeek(sunday.today()))
        assertEquals(1, sunday.weekKey(sunday.today()) - saturday.weekKey(saturday.today()))
    }

    @Test
    fun `week starts on Sunday`() {
        val cal = calendar(now = millis(utc, 2024, 6, 5, 12, 0))
        val today = cal.today()
        val start = cal.weekStart(today)

        assertEquals(today - 3, start)
        assertEquals(0, cal.dayOfWeek(start))
        assertEquals("2024-06", cal.monthKey(start))
    }

    @Test
    fun `monthly window closes at day 28 midnight`() {
        val onFirst = calendar(now = millis(utc, 2024, 5, 1, 0, 0))
        val onTwentySeventh = calendar(now = millis(utc, 2024, 5, 27, 23, 59, 59))
        val onTwentyEighth = calendar(now = millis(utc, 2024, 5, 28, 0, 0, 0))

        val first = onFirst.today()
        val twentySeventh = onTwentySeventh.today()
        val twentyEighth = onTwentyEighth.today()

        assertEquals(first, onFirst.monthStart(first))
        assertEquals(first + 27, onFirst.monthDeadline(first))
        assertEquals(27, onFirst.daysUntilMonthlyDeadline(first))
        assertTrue(onFirst.isMonthlyWindowOpen(first))
        assertTrue(onTwentySeventh.isMonthlyWindowOpen(twentySeventh))
        assertEquals(1, onTwentySeventh.daysUntilMonthlyDeadline(twentySeventh))
        assertFalse(onTwentyEighth.isMonthlyWindowOpen(twentyEighth))
        assertEquals(0, onTwentyEighth.daysUntilMonthlyDeadline(twentyEighth))
    }

    @Test
    fun `leap year February keeps 29 real days`() {
        val cal = calendar(now = millis(utc, 2024, 2, 29, 12, 0))
        val februaryFirst = millis(utc, 2024, 2, 1, 12, 0)
        val leapDay = cal.today()

        assertEquals(cal.epochDayAt(millis(utc, 2024, 2, 1)), cal.monthStart(leapDay))
        assertEquals(28L, leapDay - cal.epochDayAt(februaryFirst))
        assertEquals("2024-02", cal.monthKey(leapDay))
        assertEquals("Feb 28", cal.deadlineLabel(leapDay))
    }

    @Test
    fun `non leap year has no 29 February`() {
        val cal = calendar(now = millis(utc, 2023, 3, 1, 12, 0))
        val marchFirst = cal.today()
        val februaryFirst = cal.monthStart(marchFirst - 1)

        assertEquals("2023-02", cal.monthKey(februaryFirst))
        assertEquals(28L, marchFirst - februaryFirst)
        assertEquals(27, cal.daysUntilMonthlyDeadline(cal.monthStart(februaryFirst)))
    }

    @Test
    fun `spring DST transition still advances exactly one local day`() {
        // 2024-03-10 in New York is only 23 hours long.
        val beforeMidnight = millis(newYork, 2024, 3, 10, 0, 30)
        val cal = calendar(newYork, beforeMidnight)
        val today = cal.today()
        val nextMidnight = cal.nextMidnightMillis()

        assertTrue(nextMidnight > beforeMidnight)
        assertEquals(today + 1, cal.epochDayAt(nextMidnight))
        assertTrue((nextMidnight - beforeMidnight) <= 24L * 60 * 60 * 1000)
    }

    @Test
    fun `autumn DST transition still advances exactly one local day`() {
        // 2024-11-03 in New York is 25 hours long.
        val beforeMidnight = millis(newYork, 2024, 11, 3, 0, 30)
        val cal = calendar(newYork, beforeMidnight)
        val today = cal.today()
        val nextMidnight = cal.nextMidnightMillis()

        assertEquals(today + 1, cal.epochDayAt(nextMidnight))
        assertTrue((nextMidnight - beforeMidnight) <= 25L * 60 * 60 * 1000)
    }

    @Test
    fun `month keys and labels are local and stable`() {
        val cal = calendar(now = millis(utc, 2024, 12, 31, 23, 59))
        val today = cal.today()

        assertEquals("2024-12", cal.monthKey(today))
        assertEquals("Dec 28", cal.deadlineLabel(today))
        assertEquals("December", cal.monthLabel(today))
    }
}
