package dev.soloistdev.studenttracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * The two folds the Today screen, the reminders and the widget all read from.
 *
 * They were private to a ViewModel and therefore untestable; they now decide what a teacher is
 * woken up about, which is a much better reason to pin them down.
 */
class TodayDigestTest {

    private fun day(year: Int, month: Int, dayOfMonth: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, dayOfMonth, 0, 0, 0)
        }.timeInMillis

    private fun log(studentId: Int, dateMillis: Long, status: String) =
        AttendanceLogEntity(recordId = 1, dateMillis = dateMillis, studentId = studentId, status = status)

    private fun column(id: Int, dueDate: Long) = AssessmentColumnEntity(
        id = id,
        name = "Assessment $id",
        maxPoints = 100.0,
        examDate = 0L,
        checkDate = 0L,
        dueDate = dueDate
    )

    private fun score(columnId: Int, studentId: Int, value: String) =
        AssessmentScoreEntity(columnId = columnId, studentId = studentId, score = value)

    // --- who missed the last session --------------------------------------------------------

    @Test
    fun `the last marked day is used, not literally yesterday`() {
        val friday = day(2026, Calendar.MARCH, 13)
        val monday = day(2026, Calendar.MARCH, 16)

        // Nothing was taken over the weekend, so Friday is still the last real session.
        val absent = TodayDigest.absentOnLastMarkedDay(
            logs = listOf(
                log(1, friday, "ABSENT"),
                log(2, friday, "PRESENT"),
                log(3, friday, "EXCUSED")
            ),
            scopedIds = setOf(1, 2, 3),
            todayStartMillis = monday
        )

        assertEquals(listOf(1), absent)
    }

    @Test
    fun `an untaken sheet does not become the last marked day`() {
        val monday = day(2026, Calendar.MARCH, 9)
        val tuesday = day(2026, Calendar.MARCH, 10)
        val wednesday = day(2026, Calendar.MARCH, 11)

        val absent = TodayDigest.absentOnLastMarkedDay(
            logs = listOf(
                log(1, monday, "ABSENT"),
                log(1, tuesday, "NOT_SET")
            ),
            scopedIds = setOf(1),
            todayStartMillis = wednesday
        )

        assertEquals(listOf(1), absent)
    }

    @Test
    fun `today is excluded so a half-marked register is not reported as absences`() {
        val today = day(2026, Calendar.MARCH, 11)

        val absent = TodayDigest.absentOnLastMarkedDay(
            logs = listOf(log(1, today, "ABSENT")),
            scopedIds = setOf(1),
            todayStartMillis = today
        )

        assertTrue(absent.isEmpty())
    }

    @Test
    fun `students outside the class in front of the teacher are ignored`() {
        val yesterday = day(2026, Calendar.MARCH, 10)
        val today = day(2026, Calendar.MARCH, 11)

        val absent = TodayDigest.absentOnLastMarkedDay(
            logs = listOf(log(1, yesterday, "ABSENT"), log(99, yesterday, "ABSENT")),
            scopedIds = setOf(1),
            todayStartMillis = today
        )

        assertEquals(listOf(1), absent)
    }

    // --- what still needs marking -----------------------------------------------------------

    @Test
    fun `an assessment everyone has handed in is not reported`() {
        val now = day(2026, Calendar.MARCH, 11)

        val due = TodayDigest.dueSoon(
            columns = listOf(column(1, dueDate = now + 2 * 24 * 60 * 60 * 1000L)),
            scores = listOf(score(1, 1, "80"), score(1, 2, "70")),
            scopedIds = setOf(1, 2),
            nowMillis = now
        )

        assertTrue(due.isEmpty())
    }

    @Test
    fun `a blank score counts as outstanding rather than handed in`() {
        val now = day(2026, Calendar.MARCH, 11)

        val due = TodayDigest.dueSoon(
            columns = listOf(column(1, dueDate = now + 24 * 60 * 60 * 1000L)),
            scores = listOf(score(1, 1, "80"), score(1, 2, "  ")),
            scopedIds = setOf(1, 2),
            nowMillis = now
        )

        assertEquals(1, due.single().outstanding)
    }

    @Test
    fun `a past due date is flagged as overdue`() {
        val now = day(2026, Calendar.MARCH, 11)

        val due = TodayDigest.dueSoon(
            columns = listOf(column(1, dueDate = now - 24 * 60 * 60 * 1000L)),
            scores = emptyList(),
            scopedIds = setOf(1),
            nowMillis = now
        )

        assertTrue(due.single().isOverdue)
    }

    @Test
    fun `an assessment beyond the horizon is left alone`() {
        val now = day(2026, Calendar.MARCH, 11)

        val due = TodayDigest.dueSoon(
            columns = listOf(column(1, dueDate = now + TodayDigest.DUE_HORIZON_MILLIS + 1000L)),
            scores = emptyList(),
            scopedIds = setOf(1),
            nowMillis = now
        )

        assertTrue(due.isEmpty())
    }

    @Test
    fun `an assessment with no due date set is never chased`() {
        val now = day(2026, Calendar.MARCH, 11)

        val due = TodayDigest.dueSoon(
            columns = listOf(column(1, dueDate = 0L)),
            scores = emptyList(),
            scopedIds = setOf(1),
            nowMillis = now
        )

        assertTrue(due.isEmpty())
    }

    @Test
    fun `the soonest deadline is listed first`() {
        val now = day(2026, Calendar.MARCH, 11)
        val dayMillis = 24 * 60 * 60 * 1000L

        val due = TodayDigest.dueSoon(
            columns = listOf(
                column(1, dueDate = now + 5 * dayMillis),
                column(2, dueDate = now + dayMillis)
            ),
            scores = emptyList(),
            scopedIds = setOf(1),
            nowMillis = now
        )

        assertEquals(listOf(2, 1), due.map { it.column.id })
    }
}
