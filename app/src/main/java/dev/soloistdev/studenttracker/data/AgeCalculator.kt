package dev.soloistdev.studenttracker.data

import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

/**
 * Age in completed years.
 *
 * This existed as `currentYear - birthYear` in six separate places, which reads a year too high
 * for every student whose birthday has not come round yet - most of the roster, for most of the
 * year. Ages drive filters, PDF slips and report headers, so they need to agree with each other
 * and with reality.
 */
object AgeCalculator {

    /** Completed years between [birthdayMillis] and [today]. Negative or future dates give 0. */
    fun ageInYears(birthdayMillis: Long, today: LocalDate = LocalDate.now()): Int {
        if (birthdayMillis <= 0L) return 0
        val birthDate = Instant.ofEpochMilli(birthdayMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        if (birthDate.isAfter(today)) return 0
        return Period.between(birthDate, today).years
    }

    fun ageInYears(student: StudentEntity): Int = ageInYears(student.birthday)
}
