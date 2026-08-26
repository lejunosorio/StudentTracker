package dev.soloistdev.studenttracker.data

/**
 * Early-warning analytics, computed entirely on device.
 *
 * Attendance, grades and behaviour already live in the database but have never been read
 * together. Combining them surfaces the students who are quietly slipping - the ones a teacher
 * would otherwise only notice at report time.
 *
 * Every flag carries a plain-language reason. A risk score a teacher cannot interrogate is one
 * they will not trust, and acting on it affects a real child.
 */
object StudentInsights {

    /**
     * Absence share at which most systems treat a student as chronically absent.
     *
     * The usual definition is missing ten percent of days *or more*, so the comparison is
     * inclusive: exactly 10% counts. It used to be strictly greater, which quietly let the
     * textbook case - 2 days missed in 20 - through unflagged.
     */
    const val CHRONIC_ABSENCE_THRESHOLD = 0.10

    /** Percentage below which a running grade counts as failing for flagging purposes. */
    const val FAILING_GRADE_THRESHOLD = 60.0

    private const val REPEATED_INCIDENTS_THRESHOLD = 2

    /**
     * Marked days needed before an absence rate means anything.
     *
     * Without this, one absence on the only day taken so far reads as 100% absent and the student
     * is flagged at risk on a single data point - most visibly on the first day of a new sheet,
     * when every rate is 0% or 100%. A flag that behaves like that is one a teacher stops
     * believing, and it is a real child it is being attached to.
     */
    const val MIN_MARKED_DAYS_FOR_ABSENCE_FLAG = 10

    enum class RiskLevel { NONE, WATCH, AT_RISK }

    data class AttendanceSummary(
        val present: Int,
        val absent: Int,
        val excused: Int,
        val unmarked: Int
    ) {
        /** Days actually marked. Unmarked days are excluded rather than assumed present. */
        val marked: Int get() = present + absent + excused
        val total: Int get() = marked + unmarked

        val attendanceRate: Double?
            get() = if (marked == 0) null else (present.toDouble() / marked.toDouble()) * 100.0

        val absenceRate: Double?
            get() = if (marked == 0) null else absent.toDouble() / marked.toDouble()

        val isChronicallyAbsent: Boolean
            get() = marked >= MIN_MARKED_DAYS_FOR_ABSENCE_FLAG &&
                    (absenceRate ?: 0.0) >= CHRONIC_ABSENCE_THRESHOLD
    }

    data class Insight(
        val studentId: Int,
        val attendance: AttendanceSummary,
        val gradePercent: Double?,
        val incidentCount: Int,
        val negativeIncidents: Int,
        val riskLevel: RiskLevel,
        val reasons: List<String>
    )

    /**
     * @param term restricts every part of the picture to one grading period. Grades are scoped by
     *   the period an assessment belongs to; attendance and behaviour by the period's dates, since
     *   those rows carry a date rather than a term. Null means the whole year.
     *
     *   Passing a term used to scope only the grade, so a Q3 percentage sat beside a year's worth
     *   of absences and behaviour notes and read as though they described the same weeks.
     */
    fun compute(
        students: List<StudentEntity>,
        logs: List<AttendanceLogEntity>,
        columns: List<AssessmentColumnEntity>,
        scores: List<AssessmentScoreEntity>,
        categories: List<AssessmentCategoryEntity>,
        incidents: List<BehaviorIncidentEntity>,
        term: GradingTermEntity? = null
    ): Map<Int, Insight> {
        val window = term?.let { dateWindowOf(it) }
        val termId = term?.id ?: 0

        val scopedLogs = if (window == null) logs else logs.filter { it.dateMillis in window }
        val scopedIncidents = if (window == null) incidents else incidents.filter { it.incidentDate in window }

        val logsByStudent = scopedLogs.groupBy { it.studentId }
        val incidentsByStudent = scopedIncidents.groupBy { it.studentId }

        return students.associate { student ->
            val studentLogs = logsByStudent[student.id].orEmpty()
            val attendance = AttendanceSummary(
                present = studentLogs.count { it.status == "PRESENT" },
                absent = studentLogs.count { it.status == "ABSENT" },
                excused = studentLogs.count { it.status == "EXCUSED" },
                unmarked = studentLogs.count { it.status == "NOT_SET" }
            )

            val grade = GradeCalculator
                .computeForStudent(student.id, columns, scores, categories, termId)
                .percent

            val studentIncidents = incidentsByStudent[student.id].orEmpty()
            val negative = studentIncidents.count { it.category.equals("Negative", ignoreCase = true) }

            val reasons = mutableListOf<String>()
            if (attendance.isChronicallyAbsent) {
                val pct = (attendance.absenceRate ?: 0.0) * 100.0
                reasons.add("Absent ${String.format(java.util.Locale.US, "%.0f", pct)}% of marked days")
            }
            if (grade != null && grade < FAILING_GRADE_THRESHOLD) {
                reasons.add("Running grade ${String.format(java.util.Locale.US, "%.1f", grade)}%")
            }
            if (negative >= REPEATED_INCIDENTS_THRESHOLD) {
                reasons.add("$negative negative behaviour notes")
            }

            val level = when (reasons.size) {
                0 -> RiskLevel.NONE
                1 -> RiskLevel.WATCH
                else -> RiskLevel.AT_RISK
            }

            student.id to Insight(
                studentId = student.id,
                attendance = attendance,
                gradePercent = grade,
                incidentCount = studentIncidents.size,
                negativeIncidents = negative,
                riskLevel = level,
                reasons = reasons
            )
        }
    }

    /**
     * Per-day status for one student, oldest first. Feeds the attendance heatmap, where a run of
     * absences is visible at a glance in a way a percentage is not.
     */
    fun attendanceTimeline(
        studentId: Int,
        logs: List<AttendanceLogEntity>,
        term: GradingTermEntity? = null
    ): List<Pair<Long, String>> {
        val window = term?.let { dateWindowOf(it) }
        return logs.filter { it.studentId == studentId && (window == null || it.dateMillis in window) }
            .sortedBy { it.dateMillis }
            .map { it.dateMillis to it.status }
    }

    /**
     * The period as a millisecond range covering whole days, or null when it has no usable dates.
     *
     * The end is pushed to the last moment of its day on purpose: attendance rows are stored at
     * midnight, but a behaviour note carries the time it was written, so an incident logged on the
     * final afternoon of a term would otherwise fall outside it. A term with no dates set filters
     * nothing rather than filtering everything away.
     */
    private fun dateWindowOf(term: GradingTermEntity): LongRange? {
        if (term.startDate <= 0L || term.endDate <= 0L) return null
        if (term.endDate < term.startDate) return null
        return startOfDay(term.startDate)..(startOfDay(term.endDate) + DAY_MILLIS - 1)
    }

    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    private fun startOfDay(millis: Long): Long =
        java.util.Calendar.getInstance().apply {
            timeInMillis = millis
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
}
