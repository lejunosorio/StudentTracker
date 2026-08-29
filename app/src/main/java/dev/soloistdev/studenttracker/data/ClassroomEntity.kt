package dev.soloistdev.studenttracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar

@Entity(tableName = "classrooms")
data class ClassroomEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,         // e.g., "Class 10-A"
    val startTime: String,    // e.g., "08:00 AM"
    val endTime: String,      // e.g., "04:00 PM"

    /**
     * Which days this class actually meets, as Calendar day constants joined by commas -
     * "2,3,4,5,6" for Monday to Friday.
     *
     * Without it the app assumed every class met every day, so "up next" offered Monday's class
     * on a Sunday and a new attendance sheet generated weekend columns nobody would ever fill.
     * Empty means unspecified, which is treated as "every day" so existing classrooms behave as
     * they always did.
     */
    val meetingDays: String = "",

    val isDeleted: Boolean = false,
    val lastModified: Long = System.currentTimeMillis()
) {
    /** Parsed meeting days. Empty set means the class has no restriction. */
    fun meetingDaySet(): Set<Int> =
        meetingDays.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in Calendar.SUNDAY..Calendar.SATURDAY }
            .toSet()

    /** True when the class meets on [dayOfWeek], a Calendar.DAY_OF_WEEK value. */
    fun meetsOn(dayOfWeek: Int): Boolean {
        val days = meetingDaySet()
        return days.isEmpty() || days.contains(dayOfWeek)
    }

    fun meetsOnDate(millis: Long): Boolean =
        meetsOn(Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK))

    companion object {
        /** The default for a new classroom: the ordinary school week. */
        val WEEKDAYS = listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY
        )

        fun encode(days: Collection<Int>): String = days.sorted().joinToString(",")

        /** Single-letter labels in week order, for compact day pickers. */
        val DAY_ORDER = listOf(
            Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
        )

        fun shortLabel(dayOfWeek: Int): String = when (dayOfWeek) {
            Calendar.SUNDAY -> "S"
            Calendar.MONDAY -> "M"
            Calendar.TUESDAY -> "T"
            Calendar.WEDNESDAY -> "W"
            Calendar.THURSDAY -> "Th"
            Calendar.FRIDAY -> "F"
            else -> "Sa"
        }
    }
}
