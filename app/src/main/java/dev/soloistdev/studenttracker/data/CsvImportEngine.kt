package dev.soloistdev.studenttracker.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Imports a roster from a spreadsheet.
 *
 * Teachers arrive holding a CSV exported from whatever system their school already runs, and the
 * column names in it are nobody guess. So the mapping is explicit: the file supplies headers, the
 * teacher says what each one means, and nothing is inferred silently.
 *
 * Rows are merged on the same identity used by the JSON restore - first name, last name and
 * birthday - so re-importing a corrected spreadsheet updates the roster instead of cloning it.
 */
object CsvImportEngine {

    /** A column can map to a core field, a custom template field, or nothing. */
    object Target {
        const val IGNORE = "Ignore"
        const val FIRST_NAME = "First Name"
        const val LAST_NAME = "Last Name"
        const val GENDER = "Gender"
        const val BIRTHDAY = "Birthday"
        const val ADDRESS = "Address"
        const val CONTACT = "Student Contact"
        const val CLASSROOMS = "Classrooms"
        const val GUARDIAN_NAME = "Guardian Name"
        const val GUARDIAN_CONTACT = "Guardian Contact"

        val CORE = listOf(
            IGNORE, LAST_NAME, FIRST_NAME, GENDER, BIRTHDAY, ADDRESS,
            CONTACT, CLASSROOMS, GUARDIAN_NAME, GUARDIAN_CONTACT
        )
    }

    private val DATE_PATTERNS = listOf(
        "yyyy-MM-dd", "MM-dd-yyyy", "MM/dd/yyyy", "dd/MM/yyyy",
        "dd-MM-yyyy", "yyyy/MM/dd", "MMM dd, yyyy", "dd MMM yyyy"
    )

    data class ParsedCsv(
        val headers: List<String>,
        val rows: List<List<String>>
    )

    data class ImportOutcome(
        val created: Int,
        val updated: Int,
        val skipped: Int,
        val errors: List<String>
    )

    suspend fun readCsv(context: Context, uri: Uri, maxRows: Int = 5000): ParsedCsv? = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return@withContext null

            val table = parse(text)
            if (table.isEmpty()) return@withContext null

            val rows = table.drop(1).filter { row -> row.any { it.isNotBlank() } }.take(maxRows)
            ParsedCsv(headers = table.first().map { it.trim() }, rows = rows)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * RFC 4180 style parser: honours quoted fields containing commas, newlines and doubled
     * quotes. Written by hand because pulling in a CSV library for one screen is not worth the
     * dependency.
     */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    row.add(field.toString())
                    field.setLength(0)
                }
                (c == '\n' || c == '\r') && !inQuotes -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(field.toString())
                    field.setLength(0)
                    rows.add(row)
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }

        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows.filter { r -> r.any { cell -> cell.isNotBlank() } }
    }

    fun parseDate(raw: String): Long? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return null
        DATE_PATTERNS.forEach { pattern ->
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                val parsed = sdf.parse(cleaned)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // Next pattern
            }
        }
        return null
    }

    private fun normaliseGender(raw: String): String {
        val v = raw.trim().lowercase()
        return when {
            v.startsWith("f") -> "F"
            v.startsWith("m") -> "M"
            else -> ""
        }
    }


    /**
     * Applies the mapping and writes the roster.
     *
     * @param mapping column index to a [Target] constant or a custom field name.
     */
    suspend fun import(
        repository: StudentRepository,
        parsed: ParsedCsv,
        mapping: Map<Int, String>,
        customFieldNames: Set<String>
    ): ImportOutcome = withContext(Dispatchers.IO) {
        var created = 0
        var updated = 0
        var skipped = 0
        val errors = mutableListOf<String>()

        val existing = repository.getAllActiveStudents() + repository.getAllDeletedStudents()
        val byIdentity = existing.associateBy { identity(it.firstName, it.lastName, it.birthday) }.toMutableMap()

        // Fallback index for spreadsheets with no birthday column. Without it every row would
        // key on birthday 0, match nothing, and clone the entire roster. Only used when the row
        // genuinely carries no date, and only when the name is unambiguous.
        val byName = existing
            .groupBy { nameKey(it.firstName, it.lastName) }
            .filterValues { it.size == 1 }
            .mapValues { it.value.first() }
            .toMutableMap()

        parsed.rows.forEachIndexed { rowIndex, row ->
            fun cell(target: String): String {
                val idx = mapping.entries.firstOrNull { it.value == target }?.key ?: return ""
                return row.getOrNull(idx)?.trim() ?: ""
            }

            val first = cell(Target.FIRST_NAME)
            val last = cell(Target.LAST_NAME)

            if (first.isBlank() && last.isBlank()) {
                skipped++
                return@forEachIndexed
            }

            val birthdayRaw = cell(Target.BIRTHDAY)
            val birthday = parseDate(birthdayRaw) ?: 0L
            if (birthdayRaw.isNotBlank() && birthday == 0L) {
                errors.add("Row ${rowIndex + 2}: could not read date '$birthdayRaw'")
            }

            val key = identity(first, last, birthday)
            // Birthday-keyed match first; fall back to an unambiguous name match only when the
            // row supplied no date at all.
            val prior = byIdentity[key]
                ?: if (birthday == 0L) byName[nameKey(first, last)] else null

            // A spreadsheet is a partial view of a student, not a replacement for one. Anything
            // the CSV does not carry is kept, so importing a two-column file cannot erase
            // guardians, custom fields or class membership that were entered in the app.
            fun keepOrReplace(target: String, priorValue: String): String =
                cell(target).ifBlank { priorValue }

            val csvClasses = cell(Target.CLASSROOMS).split(';', ',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val classNamesJson = if (csvClasses.isEmpty()) {
                prior?.classNamesJson ?: "[]"
            } else {
                // Union rather than replace: the file may list only the class it is about
                val merged = ((prior?.getClassNamesList() ?: emptyList()) + csvClasses).distinct()
                JSONArray().apply { merged.forEach { put(it) } }.toString()
            }

            val guardianName = cell(Target.GUARDIAN_NAME)
            val guardianPhone = cell(Target.GUARDIAN_CONTACT)
            val guardiansJson = if (guardianName.isBlank() && guardianPhone.isBlank()) {
                prior?.guardiansJson ?: "[]"
            } else {
                val merged = Guardian.listFromJsonString(prior?.guardiansJson ?: "[]").toMutableList()
                val incoming = Guardian(
                    name = guardianName,
                    relationship = "Guardian",
                    phones = if (guardianPhone.isBlank()) emptyList() else listOf(guardianPhone)
                )
                val at = merged.indexOfFirst { it.name.equals(guardianName, ignoreCase = true) }
                if (at >= 0) merged[at] = incoming else merged.add(incoming)
                Guardian.listToJsonString(merged)
            }

            // Mapped custom fields overlay whatever the student already has
            val customJson = try {
                JSONObject(prior?.customDataJson ?: "{}")
            } catch (_: Exception) {
                JSONObject()
            }
            mapping.forEach { (index, target) ->
                if (target in customFieldNames) {
                    val value = row.getOrNull(index)?.trim().orEmpty()
                    if (value.isNotEmpty()) customJson.put(target, value)
                }
            }

            val student = StudentEntity(
                id = prior?.id ?: 0,
                firstName = first.ifBlank { prior?.firstName ?: "" },
                lastName = last.ifBlank { prior?.lastName ?: "" },
                gender = normaliseGender(cell(Target.GENDER)).ifBlank { prior?.gender ?: "" },
                birthday = if (birthday != 0L) birthday else (prior?.birthday ?: 0L),
                address = keepOrReplace(Target.ADDRESS, prior?.address ?: ""),
                contactNumber = keepOrReplace(Target.CONTACT, prior?.contactNumber ?: ""),
                picturePath = prior?.picturePath ?: "",
                guardiansJson = guardiansJson,
                customDataJson = customJson.toString(),
                isDeleted = false,
                lastModified = System.currentTimeMillis(),
                classNamesJson = classNamesJson,
                seatingJson = prior?.seatingJson ?: "{}"
            )

            try {
                if (prior == null) {
                    val newId = repository.saveStudent(student)
                    val saved = student.copy(id = newId)
                    byIdentity[key] = saved
                    byName[nameKey(first, last)] = saved
                    created++
                } else {
                    repository.saveStudent(student)
                    updated++
                }
            } catch (e: Exception) {
                errors.add("Row ${rowIndex + 2}: ${e.message}")
                skipped++
            }
        }

        ImportOutcome(created, updated, skipped, errors)
    }
    private fun nameKey(first: String, last: String): String =
        "${first.trim().lowercase()}_${last.trim().lowercase()}"

    private fun identity(first: String, last: String, birthday: Long): String =
        "${first.trim().lowercase()}_${last.trim().lowercase()}_$birthday"
}
