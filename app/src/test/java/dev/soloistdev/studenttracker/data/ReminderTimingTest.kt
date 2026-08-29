package dev.soloistdev.studenttracker.data

import dev.soloistdev.studenttracker.notifications.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * When the reminders actually fire.
 *
 * These are the calculations nobody can see going wrong until a teacher is told about a class that
 * finished an hour ago, or is briefed twice on the same morning.
 */
class ReminderTimingTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis

    private fun readBack(millis: Long): Triple<Int, Int, Int> =
        Calendar.getInstance().apply { timeInMillis = millis }.let {
            Triple(it.get(Calendar.DAY_OF_MONTH), it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE))
        }

    private fun classroom(name: String, start: String, end: String, days: List<Int>? = null) =
        ClassroomEntity(
            name = name,
            startTime = start,
            endTime = end,
            meetingDays = days?.let { ClassroomEntity.encode(it) } ?: ""
        )

    // --- the morning briefing ---------------------------------------------------------------

    @Test
    fun `a briefing time still ahead today fires today`() {
        val now = at(2026, Calendar.MARCH, 10, 5, 0)
        val next = ReminderScheduler.nextDigestMillis(now, hour = 6, minute = 30)

        assertEquals(Triple(10, 6, 30), readBack(next))
    }

    @Test
    fun `a briefing time already past rolls to tomorrow`() {
        val now = at(2026, Calendar.MARCH, 10, 9, 15)
        val next = ReminderScheduler.nextDigestMillis(now, hour = 6, minute = 30)

        assertEquals(Triple(11, 6, 30), readBack(next))
    }

    @Test
    fun `the briefing time landing exactly on now rolls forward rather than firing twice`() {
        val now = at(2026, Calendar.MARCH, 10, 6, 30)
        val next = ReminderScheduler.nextDigestMillis(now, hour = 6, minute = 30)

        assertEquals(Triple(11, 6, 30), readBack(next))
    }

    // --- the pre-class nudge ----------------------------------------------------------------

    @Test
    fun `upcoming starts are absolute instants in chronological order`() {
        val now = at(2026, Calendar.MARCH, 10, 6, 0)
        val rooms = listOf(
            classroom("Afternoon", "01:00 PM", "03:00 PM"),
            classroom("Morning", "07:30 AM", "09:00 AM")
        )

        val starts = ClassSchedule.nextStartsWithin(rooms, now, days = 1)

        assertEquals("Morning", starts.first().classroom.name)
        assertTrue(starts.zipWithNext().all { (a, b) -> a.startMillis <= b.startMillis })
        assertEquals(Triple(10, 7, 30), readBack(starts.first().startMillis))
    }

    @Test
    fun `a session that has already begun today is not offered again until tomorrow`() {
        val now = at(2026, Calendar.MARCH, 10, 8, 0)
        val rooms = listOf(classroom("Morning", "07:30 AM", "09:00 AM"))

        val starts = ClassSchedule.nextStartsWithin(rooms, now, days = 2)

        assertEquals(Triple(11, 7, 30), readBack(starts.first().startMillis))
    }

    @Test
    fun `a class that does not meet on a day is skipped to the day it does`() {
        // 2026-03-14 is a Saturday, so a weekdays-only class next meets on the Monday.
        val saturdayEvening = at(2026, Calendar.MARCH, 14, 20, 0)
        val rooms = listOf(
            classroom("Weekdays", "08:00 AM", "09:00 AM", ClassroomEntity.WEEKDAYS)
        )

        val starts = ClassSchedule.nextStartsWithin(rooms, saturdayEvening, days = 7)

        assertEquals(Triple(16, 8, 0), readBack(starts.first().startMillis))
    }

    @Test
    fun `a classroom with an unreadable time contributes no reminders`() {
        val now = at(2026, Calendar.MARCH, 10, 6, 0)
        val rooms = listOf(classroom("Broken", "sometime", "later"))

        assertTrue(ClassSchedule.nextStartsWithin(rooms, now).isEmpty())
    }

    @Test
    fun `a nudge too close to fire is skipped for the session after it`() {
        // 07:25 with a ten minute lead: the 07:30 class cannot be warned about any more, so the
        // reminder that gets armed is for the one at 09:00.
        val now = at(2026, Calendar.MARCH, 10, 7, 25)
        val lead = 10 * 60_000L
        val rooms = listOf(
            classroom("Morning", "07:30 AM", "08:30 AM"),
            classroom("Second", "09:00 AM", "10:00 AM")
        )

        val target = ClassSchedule.nextStartsWithin(rooms, now)
            .first { it.startMillis - lead > now }

        assertEquals("Second", target.classroom.name)
    }
}
