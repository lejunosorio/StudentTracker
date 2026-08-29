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
        val isTomorrow: Boolean = false,
        /** How many days ahead that is: 1 is tomorrow, 3 on a Friday evening means Monday. */
        val daysAhead: Int = 0
    ) {
        /** Minutes until this class begins; negative once it has started. */
        fun minutesUntilStart(nowMinute: Int): Int =
            if (isTomorrow) {
                (MINUTES_PER_DAY - nowMinute) + startMinute + (daysAhead - 1).coerceAtLeast(0) * MINUTES_PER_DAY
            } else {
                startMinute - nowMinute
            }
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

    fun describe(
        classroom: ClassroomEntity,
        nowMinute: Int,
        /** Which day is being asked about, so a class that does not meet today says so. */
        dayOfWeek: Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    ): ScheduledClass {
        val start = parseMinuteOfDay(classroom.startTime)
        val end = parseMinuteOfDay(classroom.endTime)

        if (start == null || end == null) {
            return ScheduledClass(classroom, -1, -1, Status.UNSCHEDULED)
        }

        // A class that does not meet today has already happened, as far as today is concerned.
        // Saying ENDED rather than UNSCHEDULED keeps it eligible for tomorrow's rollover.
        if (!classroom.meetsOn(dayOfWeek)) {
            return ScheduledClass(classroom, start, end, Status.ENDED)
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

    /** A session start as an absolute instant, rather than a minute-of-day. */
    data class Upcoming(val classroom: ClassroomEntity, val startMillis: Long)

    /**
     * Every session starting between now and [days] ahead, soonest first.
     *
     * [upNext] answers "what is next" for a screen, which only ever needs one. Arming a reminder
     * needs real timestamps and needs to be able to skip past a class that is already too close
     * to warn about, so it gets the whole run rather than the head of it.
     */
    fun nextStartsWithin(
        classrooms: List<ClassroomEntity>,
        nowMillis: Long,
        days: Int = 7
    ): List<Upcoming> {
        val starts = mutableListOf<Upcoming>()

        for (offset in 0..days) {
            val dayStart = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayOfWeek = dayStart.get(Calendar.DAY_OF_WEEK)

            classrooms.forEach { classroom ->
                if (!classroom.meetsOn(dayOfWeek)) return@forEach
                val startMinute = parseMinuteOfDay(classroom.startTime) ?: return@forEach
                val at = dayStart.timeInMillis + startMinute * 60_000L
                if (at > nowMillis) starts.add(Upcoming(classroom, at))
            }
        }

        return starts.sortedBy { it.startMillis }
    }

    /** The class happening right now, earliest start first when sessions overlap. */
    fun inSession(
        classrooms: List<ClassroomEntity>,
        nowMinute: Int = nowMinuteOfDay(),
        today: Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    ): ScheduledClass? =
        classrooms.map { describe(it, nowMinute, today) }
            .filter { it.status == Status.IN_SESSION }
            .minByOrNull { it.startMinute }

    /**
     * The next class due to start, rolling over to the next teaching day once today is done.
     *
     * Without the rollover this went blank the moment the last session ended and stayed blank
     * until the following morning - so the card vanished for the whole evening, which is exactly
     * when a teacher is likely to be planning tomorrow.
     */
    fun upNext(
        classrooms: List<ClassroomEntity>,
        nowMinute: Int = nowMinuteOfDay(),
        today: Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    ): ScheduledClass? {
        val scheduled = classrooms.map { describe(it, nowMinute, today) }
            .filter { it.status != Status.UNSCHEDULED }

        scheduled.filter { it.status == Status.UPCOMING }
            .minByOrNull { it.startMinute }
            ?.let { return it }

        // Everything today has been and gone. Walk forward to the next day any class actually
        // meets - which on a Friday evening means Monday, not "tomorrow", now that a classroom
        // knows which days it runs.
        for (offset in 1..7) {
            val day = ((today - 1 + offset) % 7) + 1
            val meeting = classrooms
                .filter { it.meetsOn(day) }
                .map { describe(it, -1, day) }
                .filter { it.status != Status.UNSCHEDULED }
                .minByOrNull { it.startMinute }
            if (meeting != null) {
                return meeting.copy(status = Status.UPCOMING, isTomorrow = true, daysAhead = offset)
            }
        }
        return null
    }
}
