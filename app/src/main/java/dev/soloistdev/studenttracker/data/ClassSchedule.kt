package dev.soloistdev.studenttracker.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Reads the session times on a classroom so the app can answer "what am I teaching right now".
 *
 * Times are free text the teacher typed, so parsing is deliberately forgiving: 12-hour with a
 * meridiem is the documented format, 24-hour is accepted, and anything unparseable simply means
 * that classroom has no schedule rather than causing an error.
 */
object ClassSchedule {

    enum class Status { IN_SESSION, UPCOMING, ENDED, UNSCHEDULED }

    data class ScheduledClass(
        val classroom: ClassroomEntity,
        val startMinute: Int,
        val endMinute: Int,
        val status: Status,
        /** True when this is the first class of the next teaching day rather than of today. */
        val isTomorrow: Boolean = false
    ) {
        /** Minutes until this class begins; negative once it has started. */
        fun minutesUntilStart(nowMinute: Int): Int =
            if (isTomorrow) (MINUTES_PER_DAY - nowMinute) + startMinute else startMinute - nowMinute
    }

    private const val MINUTES_PER_DAY = 24 * 60

    private val PATTERNS = listOf("hh:mm a", "h:mm a", "HH:mm", "H:mm")

    /** Minutes since midnight, or null when the text is not a time. */
    fun parseMinuteOfDay(text: String): Int? {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return null

        PATTERNS.forEach { pattern ->
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                val parsed = sdf.parse(cleaned) ?: return@forEach
                val cal = Calendar.getInstance().apply { time = parsed }
                return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            } catch (_: Exception) {
                // Try the next pattern
            }
        }
        return null
    }

    fun nowMinuteOfDay(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    fun describe(classroom: ClassroomEntity, nowMinute: Int): ScheduledClass {
        val start = parseMinuteOfDay(classroom.startTime)
        val end = parseMinuteOfDay(classroom.endTime)

        if (start == null || end == null) {
            return ScheduledClass(classroom, -1, -1, Status.UNSCHEDULED)
        }

        val status = when {
            // A session ending at or before it starts is malformed rather than overnight;
            // treat it as in session only from its start time onward.
            end <= start -> if (nowMinute >= start) Status.IN_SESSION else Status.UPCOMING
            nowMinute in start until end -> Status.IN_SESSION
            nowMinute < start -> Status.UPCOMING
            else -> Status.ENDED
        }

        return ScheduledClass(classroom, start, end, status)
    }

    /** The class happening right now, earliest start first when sessions overlap. */
    fun inSession(classrooms: List<ClassroomEntity>, nowMinute: Int = nowMinuteOfDay()): ScheduledClass? =
        classrooms.map { describe(it, nowMinute) }
            .filter { it.status == Status.IN_SESSION }
            .minByOrNull { it.startMinute }

    /**
     * The next class due to start, rolling over to the next teaching day once today is done.
     *
     * Without the rollover this went blank the moment the last session ended and stayed blank
     * until the following morning - so the card vanished for the whole evening, which is exactly
     * when a teacher is likely to be planning tomorrow.
     */
    fun upNext(classrooms: List<ClassroomEntity>, nowMinute: Int = nowMinuteOfDay()): ScheduledClass? {
        val scheduled = classrooms.map { describe(it, nowMinute) }
            .filter { it.status != Status.UNSCHEDULED }

        scheduled.filter { it.status == Status.UPCOMING }
            .minByOrNull { it.startMinute }
            ?.let { return it }

        // Everything today has been and gone: offer the earliest session of the next day.
        return scheduled.minByOrNull { it.startMinute }
            ?.copy(status = Status.UPCOMING, isTomorrow = true)
    }
}
