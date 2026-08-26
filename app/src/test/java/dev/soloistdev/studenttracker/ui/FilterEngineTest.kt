package dev.soloistdev.studenttracker.ui

import dev.soloistdev.studenttracker.data.StudentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * FilterEngine is the single source of truth for roster filtering, and five screens depend on it
 * agreeing with itself. These cover field resolution and every operator.
 */
class FilterEngineTest {

    private fun millisOf(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day)
        }.timeInMillis

    private fun student(
        first: String = "Ana",
        last: String = "Cruz",
        gender: String = "F",
        birthday: Long = millisOf(2012, Calendar.MARCH, 14),
        address: String = "12 Mabini St",
        contact: String = "09170000000",
        classes: String = "[\"Class 10-A\",\"Class 10-B\"]",
        custom: String = "{\"Section\":\"Rizal\"}",
        guardians: String = "[{\"name\":\"Maria Cruz\",\"relationship\":\"Mother\",\"phones\":[\"09171111111\"]}]"
    ) = StudentEntity(
        id = 1,
        firstName = first,
        lastName = last,
        gender = gender,
        birthday = birthday,
        address = address,
        contactNumber = contact,
        guardiansJson = guardians,
        customDataJson = custom,
        classNamesJson = classes
    )

    // --- field resolution ---------------------------------------------------------------------

    @Test
    fun resolvesThePlainFields() {
        val s = student()
        assertEquals("Ana", FilterEngine.getFieldValue(s, "First Name"))
        assertEquals("Cruz", FilterEngine.getFieldValue(s, "Last Name"))
        assertEquals("Female", FilterEngine.getFieldValue(s, "Gender"))
        assertEquals("12 Mabini St", FilterEngine.getFieldValue(s, "Home Address"))
        assertEquals("09170000000", FilterEngine.getFieldValue(s, "Student Contact"))
    }

    @Test
    fun resolvesGuardianDetailsFromStoredJson() {
        val s = student()
        assertEquals("Maria Cruz", FilterEngine.getFieldValue(s, "Guardian Name"))
        assertEquals("09171111111", FilterEngine.getFieldValue(s, "Guardian Contact"))
    }

    @Test
    fun resolvesACustomFieldAndUnknownFieldsResolveEmpty() {
        val s = student()
        assertEquals("Rizal", FilterEngine.getFieldValue(s, "Section"))
        assertEquals("", FilterEngine.getFieldValue(s, "Not A Field"))
    }

    @Test
    fun ageIsResolvedAsCompletedYears() {
        // Born a year and a day ago, so exactly one completed year whenever this runs. Under the
        // old year-subtraction this returned 2 for most of the calendar year.
        val birth = Calendar.getInstance().apply {
            add(Calendar.YEAR, -1)
            add(Calendar.DAY_OF_YEAR, -1)
        }
        assertEquals("1", FilterEngine.getFieldValue(student(birthday = birth.timeInMillis), "Age"))
    }

    // --- class membership ---------------------------------------------------------------------

    @Test
    fun classMembershipMatchesASingleClassInsideTheStoredList() {
        val classes = FilterEngine.getFieldValue(student(), "Classroom")
        assertTrue(FilterEngine.evaluateCondition(classes, "member_of", "Class 10-A", ""))
        assertTrue(FilterEngine.evaluateCondition(classes, "equal", "class 10-b", ""))
        assertFalse(FilterEngine.evaluateCondition(classes, "member_of", "Class 9-C", ""))
        assertTrue(FilterEngine.evaluateCondition(classes, "not_member_of", "Class 9-C", ""))
    }

    @Test
    fun aStudentInNoClassIsEmpty() {
        val none = FilterEngine.getFieldValue(student(classes = "[]"), "Classroom")
        assertTrue(FilterEngine.evaluateCondition(none, "empty", "", ""))
        assertFalse(FilterEngine.evaluateCondition(none, "not empty", "", ""))
    }

    // --- operators ----------------------------------------------------------------------------

    @Test
    fun textOperatorsAreCaseInsensitive() {
        assertTrue(FilterEngine.evaluateCondition("Mabini", "contains", "mab", ""))
        assertFalse(FilterEngine.evaluateCondition("Mabini", "does not contain", "MAB", ""))
        assertTrue(FilterEngine.evaluateCondition("Mabini", "equal", "mabini", ""))
        assertTrue(FilterEngine.evaluateCondition("Mabini", "not equal", "Rizal", ""))
    }

    @Test
    fun emptinessIgnoresSurroundingWhitespace() {
        assertTrue(FilterEngine.evaluateCondition("   ", "empty", "", ""))
        assertTrue(FilterEngine.evaluateCondition(" x ", "not empty", "", ""))
    }

    @Test
    fun numericComparisonsRejectNonNumericValues() {
        assertTrue(FilterEngine.evaluateCondition("15", "greater than", "10", ""))
        assertFalse(FilterEngine.evaluateCondition("10", "greater than", "10", ""))
        assertTrue(FilterEngine.evaluateCondition("5", "less than", "10", ""))
        assertFalse(FilterEngine.evaluateCondition("not a number", "greater than", "10", ""))
        assertFalse(FilterEngine.evaluateCondition("15", "greater than", "not a number", ""))
    }

    @Test
    fun inBetweenIsInclusiveAtBothEnds() {
        assertTrue(FilterEngine.evaluateCondition("10", "In between", "10", "20"))
        assertTrue(FilterEngine.evaluateCondition("20", "In between", "10", "20"))
        assertFalse(FilterEngine.evaluateCondition("21", "In between", "10", "20"))
        assertFalse(FilterEngine.evaluateCondition("15", "In between", "", "20"))
    }

    // --- birthday operators -------------------------------------------------------------------

    @Test
    fun birthdayOperatorsReadTheStoredTimestamp() {
        val bday = millisOf(2012, Calendar.MARCH, 14).toString()
        assertTrue(FilterEngine.evaluateCondition(bday, "birth_year", "2012", ""))
        assertFalse(FilterEngine.evaluateCondition(bday, "birth_year", "2013", ""))
        assertTrue(FilterEngine.evaluateCondition(bday, "birth_month", "3", ""))
        assertFalse(FilterEngine.evaluateCondition(bday, "birth_month", "2", ""))
        assertTrue(FilterEngine.evaluateCondition(bday, "birth_month_year", "3", "2012"))
        assertFalse(FilterEngine.evaluateCondition(bday, "birth_month_year", "3", "2011"))
    }

    @Test
    fun anExactBirthdayMatchesTheDayRegardlessOfTimeOfDay() {
        val stored = Calendar.getInstance().apply {
            clear()
            set(2012, Calendar.MARCH, 14, 8, 30)
        }.timeInMillis
        val target = Calendar.getInstance().apply {
            clear()
            set(2012, Calendar.MARCH, 14, 21, 45)
        }.timeInMillis

        assertTrue(FilterEngine.evaluateCondition(stored.toString(), "exact_birthday", target.toString(), ""))
    }

    @Test
    fun aMalformedBirthdayNeverMatches() {
        assertFalse(FilterEngine.evaluateCondition("", "birth_year", "2012", ""))
        assertFalse(FilterEngine.evaluateCondition("abc", "birth_month", "3", ""))
    }

    // --- labels -------------------------------------------------------------------------------

    @Test
    fun summaryLabelsRenderStoredTokensAsWhatTheTeacherPicked() {
        assertEquals("Birthday: March", filterSummaryLabel("Birthday", "birth_month", "3", ""))
        assertEquals("Birthday: March 2012", filterSummaryLabel("Birthday", "birth_month_year", "3", "2012"))
        assertEquals("Age > 12", filterSummaryLabel("Age", "greater than", "12", ""))
        assertEquals("Age: 10 - 12", filterSummaryLabel("Age", "In between", "10", "12"))
        assertEquals("Section: %Riz%", filterSummaryLabel("Section", "contains", "Riz", ""))
        assertEquals("Class: not 10-A", filterSummaryLabel("Class", "not_member_of", "10-A", ""))
    }

    // --- date expansion -----------------------------------------------------------------------

    @Test
    fun aDateRangeExpandsToOneNormalisedEntryPerDayInclusive() {
        val dates = generateDateList(
            millisOf(2025, Calendar.JANUARY, 1),
            millisOf(2025, Calendar.JANUARY, 5)
        )

        assertEquals(5, dates.size)
        assertEquals(dates, dates.sorted())
        dates.forEach { millis ->
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, cal.get(Calendar.MINUTE))
        }
    }

    @Test
    fun aSingleDayRangeYieldsOneDay() {
        val day = millisOf(2025, Calendar.JANUARY, 1)
        assertEquals(1, generateDateList(day, day).size)
    }
}
