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
     * Consecutive absences that warrant a call today.
     *
     * An absence *rate* is the right long-run measure and completely blind to pattern: three days
     * in a row inside a good term barely moves the percentage, and it is the thing schools
     * actually act on. Counted over marked days only, so an untaken sheet does not break a run.
     */
    const val CONSECUTIVE_ABSENCE_THRESHOLD = 3

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
        val reasons: List<String>,
        /** Absences in a row as of the most recent marked day. */
        val currentAbsenceStreak: Int = 0,
        /** Negative incidents with no action recorded against them. */
        val openConcerns: Int = 0,
        /** Millis since a guardian was last contacted, or null if never. */
        val millisSinceLastContact: Long? = null
    ) {
        val isOnAbsenceStreak: Boolean get() = currentAbsenceStreak >= CONSECUTIVE_ABSENCE_THRESHOLD
    }

    /**
     * How a whole class is doing, rather than one child at a time.
     *
     * Everything here was per-student, which answers "who needs help" but never "did that paper
     * land". A class average of 51% on one assessment is a fact about the assessment.
     */
    data class ClassSummary(
        val students: Int,
        val atRisk: Int,
        val watch: Int,
        val attendanceRate: Double?,
        val averageGrade: Double?,
        val openConcerns: Int,
        val onAbsenceStreak: Int
    )

    fun summarise(insights: Collection<Insight>): ClassSummary {
        val marked = insights.sumOf { it.attendance.marked }
        val present = insights.sumOf { it.attendance.present }
        val grades = insights.mapNotNull { it.gradePercent }

        return ClassSummary(
            students = insights.size,
            atRisk = insights.count { it.riskLevel == RiskLevel.AT_RISK },
            watch = insights.count { it.riskLevel == RiskLevel.WATCH },
            attendanceRate = if (marked == 0) null else present.toDouble() / marked * 100.0,
            averageGrade = if (grades.isEmpty()) null else grades.average(),
            openConcerns = insights.sumOf { it.openConcerns },
            onAbsenceStreak = insights.count { it.isOnAbsenceStreak }
        )
    }

    /**
     * Absences at the end of the marked run, oldest-to-newest.
     *
     * Unmarked days are skipped rather than treated as present: a sheet nobody filled in is
     * missing information, not evidence the student turned up.
     */
    fun currentAbsenceStreak(logs: List<AttendanceLogEntity>): Int {
        var streak = 0
        logs.sortedByDescending { it.dateMillis }.forEach { log ->
            when (log.status) {
                "NOT_SET" -> return@forEach
                "ABSENT" -> streak++
                else -> return streak
            }
        }
        return streak
    }

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
        term: GradingTermEntity? = null,
        contactLog: List<ContactLogEntity> = emptyList()
    ): Map<Int, Insight> {
        val window = term?.let { dateWindowOf(it) }
        val termId = term?.id ?: 0

        val scopedLogs = if (window == null) logs else logs.filter { it.dateMillis in window }
        val scopedIncidents = if (window == null) incidents else incidents.filter { it.incidentDate in window }

        val logsByStudent = scopedLogs.groupBy { it.studentId }
        val incidentsByStudent = scopedIncidents.groupBy { it.studentId }
        // Contact is never scoped to a term: "when did anyone last speak to this family" is a
        // question about now, not about a quarter.
        val lastContactByStudent = contactLog.groupBy { it.studentId }
            .mapValues { (_, entries) -> entries.maxOf { it.sentAt } }
        val now = System.currentTimeMillis()

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

            val streak = currentAbsenceStreak(studentLogs)
            val openConcerns = studentIncidents.count { it.isOpenConcern }
            val lastContact = lastContactByStudent[student.id]

            val reasons = mutableListOf<String>()

            // Attendance contributes at most one reason. A run of absences and a high absence
            // rate are usually the same problem described twice, and counting both would push a
            // student to AT_RISK over a single concern - which is how a risk level stops meaning
            // anything. The streak wins the wording: it is happening now and can be acted on
            // today, where a percentage describes the term.
            val attendanceReason = when {
                streak >= CONSECUTIVE_ABSENCE_THRESHOLD -> "Absent $streak days running"
                attendance.isChronicallyAbsent -> {
                    val pct = (attendance.absenceRate ?: 0.0) * 100.0
                    "Absent ${String.format(java.util.Locale.US, "%.0f", pct)}% of marked days"
                }
                else -> null
            }
            attendanceReason?.let { reasons.add(it) }
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
                reasons = reasons,
                currentAbsenceStreak = streak,
                openConcerns = openConcerns,
                millisSinceLastContact = lastContact?.let { now - it }
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
