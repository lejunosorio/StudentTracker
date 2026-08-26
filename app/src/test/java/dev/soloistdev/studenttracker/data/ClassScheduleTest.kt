package dev.soloistdev.studenttracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "in session / up next" card on the roster screen reads entirely from this, and the times it
 * reads are free text a teacher typed into a classroom.
 */
class ClassScheduleTest {

    private fun classroom(name: String, start: String, end: String) =
        ClassroomEntity(name = name, startTime = start, endTime = end)

    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    private val morning = classroom("Grade 7 - Sampaguita", "07:30 AM", "12:00 PM")
    private val midday = classroom("Grade 8 - Rizal", "08:00 AM", "01:00 PM")
    private val afternoon = classroom("Grade 10 - Molave", "01:00 PM", "06:00 PM")
    private val roster = listOf(afternoon, morning, midday)

    // --- parsing ---------------------------------------------------------------------------

    @Test
    fun readsTheTimeFormatsATeacherIsLikelyToType() {
        assertEquals(at(7, 30), ClassSchedule.parseMinuteOfDay("07:30 AM"))
        assertEquals(at(7, 30), ClassSchedule.parseMinuteOfDay("7:30 AM"))
        assertEquals(at(19, 5), ClassSchedule.parseMinuteOfDay("19:05"))
        assertEquals(at(13, 0), ClassSchedule.parseMinuteOfDay("01:00 PM"))
        assertEquals(at(0, 0), ClassSchedule.parseMinuteOfDay("12:00 AM"))
        assertEquals(at(12, 0), ClassSchedule.parseMinuteOfDay("12:00 PM"))
        assertEquals(at(8, 15), ClassSchedule.parseMinuteOfDay("  08:15 AM  "))
    }

    @Test
    fun unreadableTimesAreNotATimeRatherThanAnError() {
        assertNull(ClassSchedule.parseMinuteOfDay(""))
        assertNull(ClassSchedule.parseMinuteOfDay("   "))
        assertNull(ClassSchedule.parseMinuteOfDay("after lunch"))
        assertNull(ClassSchedule.parseMinuteOfDay("25:00"))
    }

    // --- status ----------------------------------------------------------------------------

    @Test
    fun aSessionIsLiveFromItsStartUpToButNotIncludingItsEnd() {
        assertEquals(ClassSchedule.Status.UPCOMING, ClassSchedule.describe(morning, at(7, 29)).status)
        assertEquals(ClassSchedule.Status.IN_SESSION, ClassSchedule.describe(morning, at(7, 30)).status)
        assertEquals(ClassSchedule.Status.IN_SESSION, ClassSchedule.describe(morning, at(11, 59)).status)
        assertEquals(ClassSchedule.Status.ENDED, ClassSchedule.describe(morning, at(12, 0)).status)
    }

    @Test
    fun aClassroomWithoutUsableTimesHasNoSchedule() {
        val vague = classroom("Homeroom", "whenever", "")
        assertEquals(ClassSchedule.Status.UNSCHEDULED, ClassSchedule.describe(vague, at(9)).status)
    }

    // --- in session ------------------------------------------------------------------------

    @Test
    fun theEarliestOverlappingSessionWins() {
        // 08:00-13:00 and 07:30-12:00 are both live at 09:00; the one that started first is the
        // room the teacher is actually standing in.
        val live = ClassSchedule.inSession(roster, at(9))
        assertEquals(morning.name, live?.classroom?.name)
    }

    @Test
    fun nothingIsInSessionBetweenClasses() {
        assertNull(ClassSchedule.inSession(listOf(morning, afternoon), at(12, 30)))
    }

    // --- up next ---------------------------------------------------------------------------

    @Test
    fun upNextIsTheSoonestClassStillToStartToday() {
        val next = ClassSchedule.upNext(roster, at(6))
        assertEquals(morning.name, next?.classroom?.name)
        assertFalse(next!!.isTomorrow)
        assertEquals(90, next.minutesUntilStart(at(6)))
    }

    @Test
    fun upNextSkipsClassesAlreadyUnderWay() {
        val next = ClassSchedule.upNext(roster, at(9))
        assertEquals(afternoon.name, next?.classroom?.name)
        assertFalse(next!!.isTomorrow)
    }

    @Test
    fun onceTheDayIsOverUpNextRollsOverToTomorrowsFirstClass() {
        // The regression this was written for: at 19:40 every class had ended, upNext returned
        // null, and the card removed itself for the whole evening.
        val next = ClassSchedule.upNext(roster, at(19, 40))

        assertEquals(morning.name, next?.classroom?.name)
        assertTrue("it should be flagged as tomorrow's", next!!.isTomorrow)
        assertEquals(ClassSchedule.Status.UPCOMING, next.status)
    }

    @Test
    fun theCountdownToTomorrowCrossesMidnightCorrectly() {
        // 19:40 tonight to 07:30 tomorrow is 11h50m.
        val next = ClassSchedule.upNext(roster, at(19, 40))!!
        assertEquals(11 * 60 + 50, next.minutesUntilStart(at(19, 40)))
        assertTrue("a rolled-over class is always ahead of now", next.minutesUntilStart(at(19, 40)) > 0)
    }

    @Test
    fun theRolloverIgnoresClassroomsWithNoSchedule() {
        val vague = classroom("Homeroom", "", "")
        val next = ClassSchedule.upNext(listOf(vague, afternoon), at(23))
        assertEquals(afternoon.name, next?.classroom?.name)
        assertTrue(next!!.isTomorrow)
    }

    @Test
    fun withNoSchedulableClassroomsThereIsStillNothingToShow() {
        assertNull(ClassSchedule.upNext(emptyList(), at(19)))
        assertNull(ClassSchedule.upNext(listOf(classroom("Homeroom", "", "")), at(19)))
    }

    @Test
    fun theCardAlwaysHasSomethingToShowWhenAnyClassIsScheduled() {
        // What the screen actually asks: inSession first, then upNext. Across a whole day there
        // should be no hour where both come back empty.
        (0 until 24 * 60 step 10).forEach { minute ->
            val target = ClassSchedule.inSession(roster, minute) ?: ClassSchedule.upNext(roster, minute)
            assertTrue("nothing to show at minute $minute", target != null)
        }
    }
}
