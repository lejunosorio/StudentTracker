package dev.soloistdev.studenttracker.data

/**
 * Quoting for the CSV the app writes, matching the parser it reads back in [CsvImportEngine].
 *
 * The exports used to strip commas out of a few fields and leave the rest raw, so a surname like
 * "Dela Cruz, Jr." shifted every column after it, an address with a comma silently lost it, and a
 * value containing a quote or a line break produced a file nothing could parse - including this
 * app. Quoting where required keeps the data intact and makes an export re-importable.
 */
internal object CsvWriter {

    private val NEEDS_QUOTING = charArrayOf(',', '"', '\n', '\r')

    /** One field, quoted only when it has to be, with any internal quotes doubled per RFC 4180. */
    fun field(value: String?): String {
        val raw = value ?: ""
        if (raw.none { it in NEEDS_QUOTING }) return raw
        return "\"" + raw.replace("\"", "\"\"") + "\""
    }

    /** One complete row, terminated. */
    fun row(values: List<String?>): String =
        values.joinToString(",") { field(it) } + "\n"

    fun row(vararg values: String?): String = row(values.toList())
}
