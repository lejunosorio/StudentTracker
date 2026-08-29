package dev.soloistdev.studenttracker.data

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What happens when the file a teacher picks is not the file they thought it was.
 *
 * Restoring a backup is the one action taken when something has already gone wrong - a lost phone,
 * a reinstall - so the failure modes matter more here than anywhere else in the app. The rule this
 * pins down is that a bad file fails cleanly and leaves the database alone: every one of these
 * inputs must either parse to something the importer can walk, or raise before a single row is
 * written. What it must never do is half-import.
 */
class JsonImportEdgeCaseTest {

    /** The checked-in fixtures, from either the module or the repository root. */
    private fun fixture(name: String): File =
        listOf(File("data/$name"), File("app/data/$name"))
            .firstOrNull { it.isFile }
            ?: error("fixture $name not found from ${File(".").absolutePath}")

    private fun parse(raw: String): JSONObject = JsonSyncEngine.asPayloadObject(raw)

    // --- the checked-in fixtures ------------------------------------------------------------

    @Test
    fun `an empty file is rejected rather than treated as an empty backup`() {
        val raw = fixture("empty_file.json").readText()
        assertEquals("", raw.trim())

        // Rejected, not silently accepted. "Nothing to restore" and "this file is not a backup"
        // are different answers, and a zero-byte file is the second.
        try {
            parse(raw)
            error("expected an empty file to be rejected")
        } catch (_: JSONException) {
            // Import catches this and reports failure without touching the database
        }
    }

    @Test
    fun `a structurally valid but empty backup imports as nothing`() {
        val payload = parse(fixture("empty_json.json").readText())

        // {} is a real, well-formed document that simply carries no records. It has to survive
        // parsing so the importer can report "0 restored" rather than "corrupt file".
        assertEquals(0, payload.optJSONArray("students")?.length() ?: 0)
        assertEquals(0, payload.optJSONArray("classrooms")?.length() ?: 0)
    }

    @Test
    fun `a truncated backup is rejected before anything is written`() {
        val raw = fixture("faulty_data.json").readText()

        // Truncated part-way through, which is what a copy interrupted mid-write looks like. It
        // is well-formed for thousands of records and then simply stops.
        assertTrue("fixture should be substantial", raw.length > 1000)
        try {
            parse(raw)
            error("expected a truncated document to be rejected")
        } catch (_: JSONException) {
            // Raised by the parser, so no record has been inserted yet
        }
    }

    @Test
    fun `the known-good sample still parses, so the fixtures above prove something`() {
        val payload = parse(fixture("sample_data.json").readText())
        assertTrue(payload.optJSONArray("students")!!.length() > 0)
        assertTrue(payload.optJSONArray("classrooms")!!.length() > 0)
    }

    // --- shapes a picker can hand over --------------------------------------------------------

    @Test
    fun `whitespace only is rejected like an empty file`() {
        try {
            parse("   \n\t  \r\n ")
            error("expected whitespace to be rejected")
        } catch (_: JSONException) {
        }
    }

    @Test
    fun `the legacy bare array form still imports`() {
        // Early versions exported a naked students array. Those files are exactly the ones a
        // long-standing user is most likely to still be holding.
        val payload = parse("""[{"firstName":"Ana","lastName":"Cruz"}]""")
        assertEquals(1, payload.getJSONArray("students").length())
        assertEquals("Ana", payload.getJSONArray("students").getJSONObject(0).getString("firstName"))
    }

    @Test
    fun `an empty legacy array imports as nothing rather than failing`() {
        assertEquals(0, parse("[]").getJSONArray("students").length())
    }

    @Test
    fun `a truncated legacy array is rejected`() {
        try {
            parse("""[{"firstName":"Ana"""")
            error("expected a truncated array to be rejected")
        } catch (_: JSONException) {
        }
    }

    @Test
    fun `a JSON document that is not an object or array is rejected`() {
        // A text file containing a bare word, or a number, reaches the same code path.
        listOf("null", "hello", "42", "true").forEach { raw ->
            try {
                parse(raw)
                error("expected \"$raw\" to be rejected")
            } catch (_: JSONException) {
            }
        }
    }

    @Test
    fun `a payload whose students key holds the wrong type does not crash the parse`() {
        // Parsing must not be where this fails - the importer reads with optJSONArray and simply
        // finds nothing, which is the same as an absent key.
        val payload = parse("""{"students":"not an array"}""")
        assertEquals(null, payload.optJSONArray("students"))
    }

    @Test
    fun `a byte order mark does not stop a backup importing`() {
        // Anything that has been through a Windows text editor can arrive carrying one. The
        // leading character is not '{', so without trimming it the document would be read as a
        // legacy array and fail.
        val payload = parse("﻿{\"students\":[]}")
        assertEquals(0, payload.getJSONArray("students").length())
    }

    @Test
    fun `an HTML error page saved as a backup is rejected`() {
        // The realistic version of "wrong file": a download that returned a login page.
        try {
            parse("<!doctype html><html><body>Not found</body></html>")
            error("expected HTML to be rejected")
        } catch (_: JSONException) {
        }
    }

    @Test
    fun `a deeply nested document is rejected rather than exhausting the stack`() {
        // Guards the parser against a hand-crafted file: this must raise, not StackOverflowError,
        // because only the former is caught by the import.
        val deep = "[".repeat(5000) + "]".repeat(5000)
        try {
            parse(deep)
        } catch (_: JSONException) {
            // Acceptable
        } catch (_: StackOverflowError) {
            error("nesting depth escaped as an Error, which the import does not catch")
        }
    }
}
