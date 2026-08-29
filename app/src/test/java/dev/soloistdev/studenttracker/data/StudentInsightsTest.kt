package dev.soloistdev.studenttracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Early-warning flags. These decide which children a teacher is told to worry about, so the cost
 * of a false positive is a real conversation about a child who is fine.
 */
class StudentInsightsTest {

    private fun student(id: Int) = StudentEntity(
        id = id,
        firstName = "Ana",
        lastName = "Cruz",
        gender = "F",
        birthday = 0L,
        classNamesJson = "[\"Grade 7 - Sampaguita\"]"
    )

    private fun logs(studentId: Int, present: Int, absent: Int, unmarked: Int = 0): List<AttendanceLogEntity> {
        var day = 0L
        return buildList {
            repeat(present) { add(AttendanceLogEntity(recordId = 1, dateMillis = day++, studentId = studentId, status = "PRESENT")) }
            repeat(absent) { add(AttendanceLogEntity(recordId = 1, dateMillis = day++, studentId = studentId, status = "ABSENT")) }
            repeat(unmarked) { add(AttendanceLogEntity(recordId = 1, dateMillis = day++, studentId = studentId, status = "NOT_SET")) }
        }
    }

    private fun computeOne(
        logs: List<AttendanceLogEntity> = emptyList(),
        incidents: List<BehaviorIncidentEntity> = emptyList(),
        columns: List<AssessmentColumnEntity> = emptyList(),
        scores: List<AssessmentScoreEntity> = emptyList()
    ) = StudentInsights.compute(
        students = listOf(student(1)),
        logs = logs,
        columns = columns,
        scores = scores,
        categories = emptyList(),
        incidents = incidents
    ).getValue(1)

    private fun incident(category: String) = BehaviorIncidentEntity(
        studentId = 1, title = "t", category = category, description = "d"
    )

    // --- attendance ---------------------------------------------------------------------------

    @Test
    fun unmarkedDaysAreNotCountedAsPresent() {
        val insight = computeOne(logs(1, present = 5, absent = 1, unmarked = 20))
        assertEquals(6, insight.attendance.marked)
        assertEquals(26, insight.attendance.total)
        assertEquals(5.0 / 6.0 * 100.0, insight.attendance.attendanceRate!!, 0.001)
    }

    @Test
    fun aStudentWithNothingMarkedHasNoRate() {
        val insight = computeOne(logs(1, present = 0, absent = 0, unmarked = 5))
        assertNull(insight.attendance.attendanceRate)
        assertNull(insight.attendance.absenceRate)
        assertFalse(insight.attendance.isChronicallyAbsent)
    }

    @Test
    fun oneAbsenceOnTheOnlyMarkedDayDoesNotFlagAnyone() {
        // The regression this guards: 1 of 1 is a 100% absence rate, and the student was flagged
        // at risk on the first day a sheet was taken.
        val insight = computeOne(logs(1, present = 0, absent = 1))

        assertFalse(insight.attendance.isChronicallyAbsent)
        assertEquals(StudentInsights.RiskLevel.NONE, insight.riskLevel)
        assertTrue(insight.reasons.isEmpty())
    }

    @Test
    fun theAbsenceFlagWaitsForEnoughMarkedDays() {
        val justUnder = StudentInsights.MIN_MARKED_DAYS_FOR_ABSENCE_FLAG - 1
        assertFalse(computeOne(logs(1, present = 0, absent = justUnder)).attendance.isChronicallyAbsent)

        val enough = StudentInsights.MIN_MARKED_DAYS_FOR_ABSENCE_FLAG
        assertTrue(computeOne(logs(1, present = 0, absent = enough)).attendance.isChronicallyAbsent)
    }

    @Test
    fun aStudentJustOverTheThresholdIsFlaggedAndOneJustUnderIsNot() {
        // 10% is the line: 2 absences in 20 marked days is over it, 1 in 20 is not.
        assertTrue(computeOne(logs(1, present = 18, absent = 2)).attendance.isChronicallyAbsent)
        assertFalse(computeOne(logs(1, present = 19, absent = 1)).attendance.isChronicallyAbsent)
    }

    // --- risk levels --------------------------------------------------------------------------

    @Test
    fun oneConcernIsWatchAndTwoAreAtRisk() {
        val oneConcern = computeOne(
            logs = logs(1, present = 15, absent = 5),
            incidents = listOf(incident("Positive"))
        )
        assertEquals(StudentInsights.RiskLevel.WATCH, oneConcern.riskLevel)
        assertEquals(1, oneConcern.reasons.size)

        val twoConcerns = computeOne(
            logs = logs(1, present = 15, absent = 5),
            incidents = listOf(incident("Negative"), incident("Negative"))
        )
        assertEquals(StudentInsights.RiskLevel.AT_RISK, twoConcerns.riskLevel)
        assertEquals(2, twoConcerns.reasons.size)
    }

    @Test
    fun aSingleNegativeNoteIsNotAPattern() {
        val insight = computeOne(incidents = listOf(incident("Negative")))
        assertEquals(StudentInsights.RiskLevel.NONE, insight.riskLevel)
        assertEquals(1, insight.incidentCount)
        assertEquals(1, insight.negativeIncidents)
    }

    @Test
    fun positiveNotesNeverCountAgainstAStudent() {
        val insight = computeOne(incidents = List(5) { incident("Positive") })
        assertEquals(StudentInsights.RiskLevel.NONE, insight.riskLevel)
        assertEquals(0, insight.negativeIncidents)
        assertEquals(5, insight.incidentCount)
    }

    @Test
    fun aFailingGradeIsAReasonAndAPassingOneIsNot() {
        val column = AssessmentColumnEntity(id = 1, name = "Exam", maxPoints = 100.0, examDate = 0, checkDate = 0)

        val failing = computeOne(
            columns = listOf(column),
            scores = listOf(AssessmentScoreEntity(columnId = 1, studentId = 1, score = "45"))
        )
        assertEquals(StudentInsights.RiskLevel.WATCH, failing.riskLevel)
        assertTrue(failing.reasons.single().contains("45"))

        val passing = computeOne(
            columns = listOf(column),
            scores = listOf(AssessmentScoreEntity(columnId = 1, studentId = 1, score = "75"))
        )
        assertEquals(StudentInsights.RiskLevel.NONE, passing.riskLevel)
    }

    @Test
    fun aStudentWithNoDataAtAllIsNotFlagged() {
        val insight = computeOne()
        assertEquals(StudentInsights.RiskLevel.NONE, insight.riskLevel)
        assertNull(insight.gradePercent)
        assertTrue(insight.reasons.isEmpty())
    }

    // --- consecutive absences -----------------------------------------------------------------

    @Test
    fun aRunOfAbsencesIsCountedFromTheMostRecentMarkedDay() {
        // logs() lays present days first, then absences, so these five are the trailing run.
        assertEquals(5, StudentInsights.currentAbsenceStreak(logs(1, present = 15, absent = 5)))
        assertEquals(0, StudentInsights.currentAbsenceStreak(logs(1, present = 15, absent = 0)))
    }

    @Test
    fun anUnmarkedDayDoesNotBreakARun() {
        // A sheet nobody filled in is missing information, not evidence the student turned up.
        val withGap = listOf(
            AttendanceLogEntity(recordId = 1, dateMillis = 1, studentId = 1, status = "ABSENT"),
            AttendanceLogEntity(recordId = 1, dateMillis = 2, studentId = 1, status = "NOT_SET"),
            AttendanceLogEntity(recordId = 1, dateMillis = 3, studentId = 1, status = "ABSENT")
        )
        assertEquals(2, StudentInsights.currentAbsenceStreak(withGap))
    }

    @Test
    fun aPresentDayEndsTheRun() {
        val backAgain = listOf(
            AttendanceLogEntity(recordId = 1, dateMillis = 1, studentId = 1, status = "ABSENT"),
            AttendanceLogEntity(recordId = 1, dateMillis = 2, studentId = 1, status = "ABSENT"),
            AttendanceLogEntity(recordId = 1, dateMillis = 3, studentId = 1, status = "PRESENT")
        )
        assertEquals(0, StudentInsights.currentAbsenceStreak(backAgain))
    }

    @Test
    fun aRunOfAbsencesFlagsAStudentTheRateWouldMiss() {
        // Three days running inside an otherwise good term barely moves the percentage.
        val insight = computeOne(logs(1, present = 60, absent = 3))

        assertFalse("the rate alone would not flag this", insight.attendance.isChronicallyAbsent)
        assertTrue(insight.isOnAbsenceStreak)
        assertEquals(StudentInsights.RiskLevel.WATCH, insight.riskLevel)
        assertTrue(insight.reasons.single().contains("3 days running"))
    }

    @Test
    fun attendanceIsOnlyEverOneConcern() {
        // A run and a high rate are the same problem described twice. Counting both would make a
        // single concern read as AT_RISK.
        val insight = computeOne(logs(1, present = 15, absent = 5))

        assertTrue(insight.isOnAbsenceStreak)
        assertTrue(insight.attendance.isChronicallyAbsent)
        assertEquals(1, insight.reasons.size)
        assertEquals(StudentInsights.RiskLevel.WATCH, insight.riskLevel)
    }

    // --- open concerns and contact ------------------------------------------------------------

    @Test
    fun anUnresolvedNegativeNoteIsAnOpenConcern() {
        val incidents = listOf(
            BehaviorIncidentEntity(studentId = 1, title = "a", category = "Negative", description = "d"),
            BehaviorIncidentEntity(studentId = 1, title = "b", category = "Negative", description = "d", resolvedAt = 500L),
            BehaviorIncidentEntity(studentId = 1, title = "c", category = "Positive", description = "d")
        )
        assertEquals(1, computeOne(incidents = incidents).openConcerns)
    }

    @Test
    fun timeSinceLastContactComesFromTheMostRecentEntry() {
        val now = System.currentTimeMillis()
        val log = listOf(
            ContactLogEntity(studentId = 1, phone = "1", sentAt = now - 10_000L),
            ContactLogEntity(studentId = 1, phone = "2", sentAt = now - 500L)
        )
        val insight = StudentInsights.compute(
            students = listOf(student(1)),
            logs = emptyList(),
            columns = emptyList(),
            scores = emptyList(),
            categories = emptyList(),
            incidents = emptyList(),
            contactLog = log
        ).getValue(1)

        assertTrue(insight.millisSinceLastContact!! < 5_000L)
    }

    @Test
    fun aStudentNeverContactedHasNoElapsedTime() {
        assertNull(computeOne().millisSinceLastContact)
    }

    // --- class summary ------------------------------------------------------------------------

    @Test
    fun theClassSummaryAggregatesWhatTheRowsShow() {
        // Student 1 ends on a 3-day run; student 2 has a clean record.
        val insights = StudentInsights.compute(
            students = listOf(student(1), student(2)),
            logs = logs(1, present = 8, absent = 3) + logs(2, present = 10, absent = 0),
            columns = emptyList(),
            scores = emptyList(),
            categories = emptyList(),
            incidents = emptyList()
        )
        val summary = StudentInsights.summarise(insights.values)

        assertEquals(2, summary.students)
        assertEquals("18 present of 21 marked", 18.0 / 21.0 * 100.0, summary.attendanceRate!!, 0.001)
        assertEquals(1, summary.onAbsenceStreak)
    }

    @Test
    fun anEmptyClassSummarisesWithoutDividingByZero() {
        val summary = StudentInsights.summarise(emptyList())
        assertEquals(0, summary.students)
        assertNull(summary.attendanceRate)
        assertNull(summary.averageGrade)
    }

    // --- term scoping -------------------------------------------------------------------------

    private fun day(year: Int, month: Int, dayOfMonth: Int, hour: Int = 0): Long =
        java.util.Calendar.getInstance().apply {
            clear()
            set(year, month - 1, dayOfMonth, hour, 0)
        }.timeInMillis

    private fun term(start: Long, end: Long) =
        GradingTermEntity(id = 3, name = "Quarter 3", startDate = start, endDate = end)

    private val quarter3 = term(day(2026, 1, 19), day(2026, 3, 27))

    private fun log(millis: Long, status: String) =
        AttendanceLogEntity(recordId = 1, dateMillis = millis, studentId = 1, status = status)

    private fun computeScoped(
        logs: List<AttendanceLogEntity> = emptyList(),
        incidents: List<BehaviorIncidentEntity> = emptyList(),
        term: GradingTermEntity? = quarter3
    ) = StudentInsights.compute(
        students = listOf(student(1)),
        logs = logs,
        columns = emptyList(),
        scores = emptyList(),
        categories = emptyList(),
        incidents = incidents,
        term = term
    ).getValue(1)

    @Test
    fun attendanceOutsideTheTermIsNotCounted() {
        // The regression: a term used to scope the grade only, so a Q3 percentage sat next to a
        // whole year of absences.
        val logs = listOf(
            log(day(2025, 11, 4), "ABSENT"),   // Quarter 2
            log(day(2026, 2, 3), "PRESENT"),   // Quarter 3
            log(day(2026, 2, 4), "ABSENT"),    // Quarter 3
            log(day(2026, 5, 6), "ABSENT")     // Quarter 4
        )

        val scoped = computeScoped(logs)
        assertEquals(1, scoped.attendance.present)
        assertEquals(1, scoped.attendance.absent)

        val wholeYear = computeScoped(logs, term = null)
        assertEquals(3, wholeYear.attendance.absent)
    }

    @Test
    fun behaviourNotesOutsideTheTermAreNotCounted() {
        val incidents = listOf(
            BehaviorIncidentEntity(studentId = 1, title = "a", category = "Negative", description = "d", incidentDate = day(2025, 11, 4)),
            BehaviorIncidentEntity(studentId = 1, title = "b", category = "Negative", description = "d", incidentDate = day(2026, 2, 10)),
            BehaviorIncidentEntity(studentId = 1, title = "c", category = "Negative", description = "d", incidentDate = day(2026, 2, 11))
        )

        assertEquals(2, computeScoped(incidents = incidents).negativeIncidents)
        assertEquals(3, computeScoped(incidents = incidents, term = null).negativeIncidents)
    }

    @Test
    fun theFirstAndLastDaysOfTheTermAreIncluded() {
        val logs = listOf(
            log(day(2026, 1, 19), "PRESENT"),          // first day, at midnight
            log(day(2026, 3, 27), "PRESENT"),          // last day, at midnight
            log(day(2026, 1, 18), "PRESENT"),          // day before
            log(day(2026, 3, 28), "PRESENT")           // day after
        )
        assertEquals(2, computeScoped(logs).attendance.present)
    }

    @Test
    fun anIncidentLoggedLateOnTheFinalDayStillCounts() {
        // Attendance is stored at midnight, but a behaviour note carries the time it was written.
        // Without widening the window to the end of the day this would fall outside the term.
        val incidents = listOf(
            BehaviorIncidentEntity(
                studentId = 1, title = "late note", category = "Negative", description = "d",
                incidentDate = day(2026, 3, 27, hour = 16)
            )
        )
        assertEquals(1, computeScoped(incidents = incidents).incidentCount)
    }

    @Test
    fun aTermWithNoDatesSetFiltersNothingRatherThanEverything() {
        // A teacher can create a period without filling the dates in. Treating that as an empty
        // window would blank the whole screen.
        val undated = GradingTermEntity(id = 4, name = "Quarter 4", startDate = 0L, endDate = 0L)
        val logs = listOf(log(day(2026, 2, 3), "PRESENT"), log(day(2025, 11, 4), "ABSENT"))

        val insight = computeScoped(logs, term = undated)
        assertEquals(1, insight.attendance.present)
        assertEquals(1, insight.attendance.absent)
    }

    @Test
    fun aBackwardsTermIsIgnoredRatherThanExcludingEverything() {
        val backwards = term(day(2026, 3, 27), day(2026, 1, 19))
        val logs = listOf(log(day(2026, 2, 3), "PRESENT"))

        assertEquals(1, computeScoped(logs, term = backwards).attendance.present)
    }

    @Test
    fun theTimelineIsScopedTheSameWayTheFlagsAre() {
        val logs = listOf(
            log(day(2025, 11, 4), "ABSENT"),
            log(day(2026, 2, 3), "PRESENT"),
            log(day(2026, 5, 6), "ABSENT")
        )
        val scoped = StudentInsights.attendanceTimeline(1, logs, quarter3)

        assertEquals(1, scoped.size)
        assertEquals("PRESENT", scoped.single().second)
        assertEquals(3, StudentInsights.attendanceTimeline(1, logs, null).size)
    }

    // --- timeline -----------------------------------------------------------------------------

    @Test
    fun theTimelineIsOldestFirstAndOnlyThatStudent() {
        val mixed = listOf(
            AttendanceLogEntity(recordId = 1, dateMillis = 300, studentId = 1, status = "ABSENT"),
            AttendanceLogEntity(recordId = 1, dateMillis = 100, studentId = 1, status = "PRESENT"),
            AttendanceLogEntity(recordId = 1, dateMillis = 200, studentId = 2, status = "PRESENT")
        )
        val timeline = StudentInsights.attendanceTimeline(1, mixed)

        assertEquals(listOf(100L to "PRESENT", 300L to "ABSENT"), timeline)
    }
}
