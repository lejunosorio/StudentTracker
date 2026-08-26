package dev.soloistdev.studenttracker.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AgeCalculatorTest {

    private fun millisOf(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    @Test
    fun `birthday not yet reached this year does not count`() {
        // The bug this replaced: a plain year subtraction reported 11 here.
        val birthday = millisOf(2014, 12, 25)
        val today = LocalDate.of(2025, 6, 1)
        assertEquals(10, AgeCalculator.ageInYears(birthday, today))
    }

    @Test
    fun `birthday today counts`() {
        val birthday = millisOf(2014, 6, 1)
        val today = LocalDate.of(2025, 6, 1)
        assertEquals(11, AgeCalculator.ageInYears(birthday, today))
    }

    @Test
    fun `day before birthday does not count`() {
        val birthday = millisOf(2014, 6, 2)
        val today = LocalDate.of(2025, 6, 1)
        assertEquals(10, AgeCalculator.ageInYears(birthday, today))
    }

    @Test
    fun `leap day birthday on a non-leap year`() {
        val birthday = millisOf(2016, 2, 29)
        assertEquals(8, AgeCalculator.ageInYears(birthday, LocalDate.of(2025, 2, 28)))
        assertEquals(9, AgeCalculator.ageInYears(birthday, LocalDate.of(2025, 3, 1)))
    }

    @Test
    fun `unset and future birthdays report zero rather than a negative age`() {
        assertEquals(0, AgeCalculator.ageInYears(0L, LocalDate.of(2025, 6, 1)))
        assertEquals(0, AgeCalculator.ageInYears(millisOf(2030, 1, 1), LocalDate.of(2025, 6, 1)))
    }
}
