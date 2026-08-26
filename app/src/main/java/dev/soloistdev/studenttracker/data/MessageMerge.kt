package dev.soloistdev.studenttracker.data

import org.json.JSONObject

/**
 * Mail-merge for parent messaging.
 *
 * The bulk sender previously put every guardian number into a single smsto: URI with one shared
 * body, which meant identical text for everyone and, in most SMS apps, a group thread that shows
 * each parent the others numbers. Rendering per recipient fixes both.
 *
 * Any custom field defined in the template manager is addressable too, so {{Bus_Route}} resolves
 * from a student customDataJson without this file needing to know the field exists.
 */
object MessageMerge {

    /** Tokens offered in the composer UI. Custom fields work without being listed here. */
    val SUGGESTED_TOKENS = listOf(
        "{{first_name}}",
        "{{last_name}}",
        "{{full_name}}",
        "{{class}}",
        "{{guardian}}",
        "{{absences}}",
        "{{present}}",
        "{{attendance_rate}}",
        "{{grade}}"
    )

    private val TOKEN_REGEX = Regex("""\{\{\s*([A-Za-z0-9_ ]+)\s*}}""")

    fun hasTokens(text: String): Boolean = TOKEN_REGEX.containsMatchIn(text)

    data class MergeData(
        val student: StudentEntity,
        val guardianName: String? = null,
        val absences: Int? = null,
        val present: Int? = null,
        val attendanceRate: Double? = null,
        val grade: String? = null
    )

    fun render(template: String, data: MergeData): String =
        TOKEN_REGEX.replace(template) { match ->
            // An unresolved token is left verbatim rather than silently blanked, so a typo is
            // visible in the preview instead of producing a sentence with a hole in it.
            resolve(match.groupValues[1].trim(), data) ?: match.value
        }

    private fun resolve(rawToken: String, data: MergeData): String? {
        val student = data.student
        return when (rawToken.lowercase()) {
            "first_name" -> student.firstName
            "last_name" -> student.lastName
            "full_name" -> "${student.firstName} ${student.lastName}".trim()
            "class" -> student.getClassNamesList().firstOrNull() ?: ""
            "guardian" -> data.guardianName
            "contact" -> student.contactNumber
            "absences" -> data.absences?.toString()
            "present" -> data.present?.toString()
            "attendance_rate" -> data.attendanceRate?.let { String.format(java.util.Locale.US, "%.0f%%", it) }
            "grade" -> data.grade
            else -> resolveCustomField(rawToken, student)
        }
    }

    /** Custom template fields, matched case-insensitively and tolerating spaces for underscores. */
    private fun resolveCustomField(rawToken: String, student: StudentEntity): String? {
        return try {
            val json = JSONObject(student.customDataJson)
            val normalised = rawToken.replace(' ', '_').lowercase()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.replace(' ', '_').lowercase() == normalised) {
                    return json.optString(key, "")
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
