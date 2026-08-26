package dev.soloistdev.studenttracker.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The exporter writes CSV that [CsvImportEngine.parse] has to be able to read back.
 *
 * The previous exporter replaced commas with spaces in a few fields and left the rest raw, so a
 * name like "Dela Cruz, Jr." shifted every following column and a value containing a quote made
 * the file unparseable. These check the two halves agree.
 */
class CsvWriterTest {

    private fun roundTrip(vararg fields: String): List<String> {
        val csv = CsvWriter.row(fields.toList())
        val parsed = CsvImportEngine.parse(csv)
        return parsed.first()
    }

    @Test
    fun plainValuesAreLeftAlone() {
        assertEquals("Cruz,Ana,F\n", CsvWriter.row("Cruz", "Ana", "F"))
    }

    @Test
    fun aValueWithACommaIsQuotedRatherThanMangled() {
        assertEquals("\"Dela Cruz, Jr.\"", CsvWriter.field("Dela Cruz, Jr."))
        assertEquals(listOf("Dela Cruz, Jr.", "Ana"), roundTrip("Dela Cruz, Jr.", "Ana"))
    }

    @Test
    fun quotesAreDoubledAndSurvive() {
        assertEquals("\"She said \"\"hi\"\"\"", CsvWriter.field("She said \"hi\""))
        assertEquals(listOf("She said \"hi\"", "x"), roundTrip("She said \"hi\"", "x"))
    }

    @Test
    fun aLineBreakInsideAValueDoesNotStartANewRow() {
        val address = "12 Mabini St\nBarangay Malaya"
        val csv = CsvWriter.row(listOf(address, "Ana"))
        val parsed = CsvImportEngine.parse(csv)

        assertEquals("the row must stay one row", 1, parsed.size)
        assertEquals(listOf(address, "Ana"), parsed.first())
    }

    @Test
    fun emptyAndNullFieldsRoundTripAsEmpty() {
        assertEquals("", CsvWriter.field(null))
        assertEquals("", CsvWriter.field(""))
        assertEquals("Cruz,,Ana\n", CsvWriter.row("Cruz", "", "Ana"))
    }

    @Test
    fun aRealisticRosterRowSurvivesIntact() {
        val fields = listOf(
            "Dela Cruz, Jr.",
            "José",
            "M",
            "2013-03-14",
            "187 Quirino Highway, Barangay Novaliches Proper, Quezon City",
            "0917 200 1007",
            "Grade 7 - Sampaguita; Grade 8 - Rizal",
            "Maria \"Baby\" Cruz",
            "0918 201 1014"
        )
        assertEquals(fields, roundTrip(*fields.toTypedArray()))
    }
}
