package dev.soloistdev.studenttracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The spreadsheet a teacher actually has, rather than the one the parser was written against.
 *
 * A roster arrives as whatever Excel, Google Sheets or a school information system produced, and
 * the import screen maps its columns by matching the header text. Anything that corrupts a header
 * silently costs the teacher that column.
 */
class CsvImportEdgeCaseTest {

    private fun parse(text: String) = CsvImportEngine.parse(text)

    // --- empty and near-empty input -----------------------------------------------------------

    @Test
    fun `an empty file parses to nothing rather than one blank row`() {
        assertTrue(parse("").isEmpty())
    }

    @Test
    fun `a file of only whitespace and newlines yields no rows`() {
        // Every row is blank, and a blank row carries no student.
        assertTrue(parse("\n\n\r\n   \n").isEmpty())
    }

    @Test
    fun `a file of only commas yields no rows`() {
        assertTrue(parse(",,,\n,,,").isEmpty())
    }

    @Test
    fun `a header with no data rows still yields the header`() {
        val table = parse("First Name,Last Name\n")
        assertEquals(1, table.size)
        assertEquals(listOf("First Name", "Last Name"), table[0])
    }

    // --- quoting ------------------------------------------------------------------------------

    @Test
    fun `a quoted field may contain commas`() {
        val table = parse("""name,address${'\n'}Ana,"12 Mabini St, Quezon City"""")
        assertEquals(listOf("Ana", "12 Mabini St, Quezon City"), table[1])
    }

    @Test
    fun `a quoted field may contain a newline`() {
        val table = parse("name,note\nAna,\"line one\nline two\"")
        assertEquals(2, table.size)
        assertEquals("line one\nline two", table[1][1])
    }

    @Test
    fun `a doubled quote inside a quoted field becomes one quote`() {
        val table = parse("name,note\nAna,\"she said \"\"hello\"\"\"")
        assertEquals("she said \"hello\"", table[1][1])
    }

    @Test
    fun `an unterminated quote does not lose the rest of the file`() {
        // Malformed, but it must not throw and must not silently drop the data it did read.
        val table = parse("name,note\nAna,\"unclosed")
        assertEquals(2, table.size)
        assertEquals("Ana", table[1][0])
        assertEquals("unclosed", table[1][1])
    }

    // --- line endings -------------------------------------------------------------------------

    @Test
    fun `windows line endings do not leave a stray carriage return in the last cell`() {
        val table = parse("a,b\r\nc,d\r\n")
        assertEquals(listOf("a", "b"), table[0])
        assertEquals(listOf("c", "d"), table[1])
    }

    @Test
    fun `classic mac line endings are treated as row breaks`() {
        val table = parse("a,b\rc,d")
        assertEquals(2, table.size)
        assertEquals(listOf("c", "d"), table[1])
    }

    @Test
    fun `a file with no trailing newline keeps its last row`() {
        val table = parse("a,b\nc,d")
        assertEquals(2, table.size)
        assertEquals(listOf("c", "d"), table[1])
    }

    // --- ragged data --------------------------------------------------------------------------

    @Test
    fun `a row with fewer columns than the header is kept, not dropped`() {
        // The importer reads cells by index and tolerates a short row; dropping it here would
        // lose a student for the sake of a missing trailing field.
        val table = parse("first,last,contact\nAna,Cruz")
        assertEquals(2, table.size)
        assertEquals(listOf("Ana", "Cruz"), table[1])
    }

    @Test
    fun `a row with more columns than the header is kept intact`() {
        val table = parse("first,last\nAna,Cruz,extra")
        assertEquals(listOf("Ana", "Cruz", "extra"), table[1])
    }

    @Test
    fun `blank lines between records are skipped`() {
        val table = parse("first,last\nAna,Cruz\n\n\nBen,Reyes\n")
        assertEquals(3, table.size)
        assertEquals(listOf("Ben", "Reyes"), table[2])
    }

    // --- the header the mapping depends on ----------------------------------------------------

    @Test
    fun `a byte order mark does not corrupt the first header`() {
        // Excel writes one whenever a sheet is saved as "CSV UTF-8", which is the obvious choice
        // in its own save dialog. The import screen auto-maps columns by matching the header text
        // exactly, so a BOM glued to the front of the first header silently costs the teacher
        // that column - and it is always the first column, which is usually the name.
        val table = parse("﻿First Name,Last Name\nAna,Cruz")
        assertEquals("First Name", table[0][0])
    }

    // --- dates --------------------------------------------------------------------------------

    @Test
    fun `an empty or unparseable date is null rather than the epoch`() {
        // Zero is a real date the app uses to mean "unset", but returning it from a failed parse
        // would make a garbled cell indistinguishable from a blank one.
        assertNull(CsvImportEngine.parseDate(""))
        assertNull(CsvImportEngine.parseDate("   "))
        assertNull(CsvImportEngine.parseDate("not a date"))
        assertNull(CsvImportEngine.parseDate("13/45/9999"))
    }

    @Test
    fun `an impossible day is rejected rather than rolled forward`() {
        // Lenient parsing would turn the 30th of February into the 1st or 2nd of March and import
        // a birthday nobody entered.
        assertNull(CsvImportEngine.parseDate("02-30-2013"))
    }

    @Test
    fun `the documented date formats parse`() {
        assertTrue(CsvImportEngine.parseDate("04-12-2013") != null)
    }
}
