package dev.soloistdev.studenttracker.data

import java.util.Calendar

/**
 * "What is happening today", lifted out of the Today screen so more than one thing can ask.
 *
 * The computation already existed but was only reachable by opening a screen and looking at it,
 * which is the wrong shape for the two callers that need it most: a reminder that fires before the
 * teacher opens the app at all, and a home-screen widget. Sharing one implementation is also the
 * only way the notification, the widget and the screen can never disagree about who is flagged -
 * three copies of this arithmetic would drift within a release.
 */
object TodayDigest {

    /** How far ahead an unmarked assessment is worth mentioning. */
    const val DUE_HORIZON_MILLIS = 7L * 24 * 60 * 60 * 1000

    data class DueSoon(
        val column: AssessmentColumnEntity,
        val outstanding: Int,
        val isOverdue: Boolean
    )

    data class Digest(
        val current: ClassSchedule.ScheduledClass? = null,
        val next: ClassSchedule.ScheduledClass? = null,
        /** The class everything below is scoped to, or null when it spans the whole roster. */
        val focusClassName: String? = null,
        val classSize: Int = 0,
        val summary: StudentInsights.ClassSummary? = null,
        val absentLastSession: List<StudentEntity> = emptyList(),
        val onStreak: List<Pair<StudentEntity, Int>> = emptyList(),
        val openConcerns: List<Pair<StudentEntity, Int>> = emptyList(),
        val dueSoon: List<DueSoon> = emptyList()
    ) {
        val overdue: List<DueSoon> get() = dueSoon.filter { it.isOverdue }

        /** True when there is at least one thing a teacher could act on. */
        val hasAnythingToFlag: Boolean
            get() = onStreak.isNotEmpty() || absentLastSession.isNotEmpty() ||
                    openConcerns.isNotEmpty() || dueSoon.isNotEmpty()
    }

    /**
     * Reads every table the picture needs and folds them into one snapshot.
     *
     * The clock is a parameter rather than being read inside, so the whole thing is reproducible
     * in a test and a caller that already knows "now" - a reminder firing for a specific class -
     * does not get a subtly different answer to the one it scheduled itself against.
     */
    suspend fun compute(
        repository: StudentRepository,
        nowMillis: Long = System.currentTimeMillis(),
        nowMinute: Int = ClassSchedule.nowMinuteOfDay(),
        dayOfWeek: Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK),
        /**
         * Forces the scope to one class instead of inferring it from the clock.
         *
         * A pre-class reminder fires while the previous session may still be running, and the
         * automatic choice prefers whatever is in session - which would brief the teacher on the
         * class they are about to leave rather than the one they are about to walk into.
         */
        focusClassOverride: String? = null
    ): Digest {
        val students = repository.getAllActiveStudents()
        val classrooms = repository.getAllClassrooms()
        val logs = repository.getAllAttendanceLogs()
        val columns = repository.getAllAssessmentColumns()
        val scores = repository.getAllAssessmentScores()
        val categories = repository.getAllAssessmentCategories()
        val incidents = repository.getAllIncidents()
        val contactLog = repository.getAllContactLog()
        val activeTerm = repository.getAllGradingTerms().firstOrNull { it.isActive }

        val insights = StudentInsights.compute(
            students = students,
            logs = logs,
            columns = columns,
            scores = scores,
            categories = categories,
            incidents = incidents,
            term = activeTerm,
            contactLog = contactLog
        )

        val current = ClassSchedule.inSession(classrooms, nowMinute, dayOfWeek)
        val next = ClassSchedule.upNext(classrooms, nowMinute, dayOfWeek)
        val focusClass = focusClassOverride ?: (current ?: next)?.classroom?.name

        // Scoped to the class in front of the teacher when there is one, because that is who they
        // can actually do something about in the next hour.
        val scoped = if (focusClass == null) students
        else students.filter { it.getClassNamesList().contains(focusClass) }
        val scopedIds = scoped.map { it.id }.toSet()
        val scopedInsights = insights.filterKeys { it in scopedIds }

        val byId = students.associateBy { it.id }

        return Digest(
            current = current,
            next = next,
            focusClassName = focusClass,
            classSize = scoped.size,
            summary = StudentInsights.summarise(scopedInsights.values),
            absentLastSession = absentOnLastMarkedDay(logs, scopedIds, startOfDay(nowMillis))
                .mapNotNull { byId[it] },
            onStreak = scopedInsights.values
                .filter { it.isOnAbsenceStreak }
                .sortedByDescending { it.currentAbsenceStreak }
                .mapNotNull { insight -> byId[insight.studentId]?.let { it to insight.currentAbsenceStreak } },
            openConcerns = scopedInsights.values
                .filter { it.openConcerns > 0 }
                .sortedByDescending { it.openConcerns }
                .mapNotNull { insight -> byId[insight.studentId]?.let { it to insight.openConcerns } },
            dueSoon = dueSoon(columns, scores, scopedIds, nowMillis)
        )
    }

    /**
     * Who missed the most recent day that was actually taken.
     *
     * Deliberately not "yesterday" by the calendar: after a weekend or a holiday the useful
     * question is still who missed the last session, and a literal yesterday would be blank.
     */
    fun absentOnLastMarkedDay(
        logs: List<AttendanceLogEntity>,
        scopedIds: Set<Int>,
        todayStartMillis: Long
    ): List<Int> {
        val relevant = logs.filter {
            it.studentId in scopedIds && it.status != "NOT_SET" && it.dateMillis < todayStartMillis
        }
        val lastDay = relevant.maxOfOrNull { it.dateMillis } ?: return emptyList()
        return relevant.filter { it.dateMillis == lastDay && it.status == "ABSENT" }.map { it.studentId }
    }

    /** Assessments due within the horizon, or already past, that are not fully marked. */
    fun dueSoon(
        columns: List<AssessmentColumnEntity>,
        scores: List<AssessmentScoreEntity>,
        scopedIds: Set<Int>,
        nowMillis: Long
    ): List<DueSoon> {
        val horizon = nowMillis + DUE_HORIZON_MILLIS
        val scoredByColumn = scores.filter { it.score.isNotBlank() }.groupBy { it.columnId }

        return columns
            .filter { it.dueDate > 0L && it.dueDate <= horizon }
            .mapNotNull { column ->
                val handedIn = scoredByColumn[column.id].orEmpty().count { it.studentId in scopedIds }
                val outstanding = (scopedIds.size - handedIn).coerceAtLeast(0)
                if (outstanding == 0) null
                else DueSoon(column, outstanding, isOverdue = column.dueDate < nowMillis)
            }
            .sortedBy { it.column.dueDate }
    }

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
