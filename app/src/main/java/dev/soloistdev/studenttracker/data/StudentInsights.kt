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

    /** Absence share at which most systems treat a student as chronically absent. */
    const val CHRONIC_ABSENCE_THRESHOLD = 0.10

    /** Percentage below which a running grade counts as failing for flagging purposes. */
    const val FAILING_GRADE_THRESHOLD = 60.0

    private const val REPEATED_INCIDENTS_THRESHOLD = 2

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
            get() = (absenceRate ?: 0.0) > CHRONIC_ABSENCE_THRESHOLD && marked > 0
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

    fun compute(
        students: List<StudentEntity>,
        logs: List<AttendanceLogEntity>,
        columns: List<AssessmentColumnEntity>,
        scores: List<AssessmentScoreEntity>,
        categories: List<AssessmentCategoryEntity>,
        incidents: List<BehaviorIncidentEntity>,
        termId: Int = 0
    ): Map<Int, Insight> {
        val logsByStudent = logs.groupBy { it.studentId }
        val incidentsByStudent = incidents.groupBy { it.studentId }

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
    fun attendanceTimeline(studentId: Int, logs: List<AttendanceLogEntity>): List<Pair<Long, String>> =
        logs.filter { it.studentId == studentId }
            .sortedBy { it.dateMillis }
            .map { it.dateMillis to it.status }
}
