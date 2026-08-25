package dev.soloistdev.studenttracker.ui

import dev.soloistdev.studenttracker.data.Guardian
import dev.soloistdev.studenttracker.data.StudentEntity
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Single source of truth for roster filtering.
 *
 * This logic previously lived as five independently drifting copies (StudentListViewModel,
 * SavedFiltersScreen, QueryResultsScreen, AttendanceViewModel, GradebookViewModel). The
 * behaviour below is the union of the most complete copy of each part, so every caller now
 * resolves the same fields and honours the same operators.
 */
object FilterEngine {

    private val BIRTHDAY_OPERATORS = setOf("birth_year", "birth_month", "birth_month_year", "exact_birthday")

    /** Resolves a filterable field name to the comparable string value held by [student]. */
    fun getFieldValue(student: StudentEntity, field: String): String {
        return when (field) {
            "First Name" -> student.firstName
            "Last Name" -> student.lastName
            "Gender" -> if (student.gender == "F") "Female" else "Male"
            "Address", "Home Address" -> student.address
            "Student Contact" -> student.contactNumber
            // Returns the raw JSON array so evaluateCondition can apply set-membership logic
            "Class", "Classroom" -> student.classNamesJson
            "Age" -> {
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val birthYear = Calendar.getInstance().apply { timeInMillis = student.birthday }.get(Calendar.YEAR)
                (currentYear - birthYear).toString()
            }
            "Birthday" -> student.birthday.toString()
            "Guardian Name" -> {
                val guardians = Guardian.listFromJsonString(student.guardiansJson)
                if (guardians.isNotEmpty()) guardians[0].name else ""
            }
            "Guardian Contact" -> {
                val guardians = Guardian.listFromJsonString(student.guardiansJson)
                if (guardians.isNotEmpty()) guardians[0].phones.firstOrNull() ?: "" else ""
            }
            else -> {
                try {
                    JSONObject(student.customDataJson).optString(field, "")
                } catch (_: Exception) {
                    ""
                }
            }
        }
    }

    /** Evaluates a whole [FilterState] against an already-resolved field value. */
    fun applyComparison(fieldValue: String, filter: FilterState): Boolean =
        evaluateCondition(fieldValue, filter.comparison, filter.value1, filter.value2)

    fun evaluateCondition(fieldVal: String, operator: String, v1: String, v2: String): Boolean {
        val cleanVal = fieldVal.trim()
        val value1 = v1.trim()
        val value2 = v2.trim()

        // Multi-class membership. Detected by value shape, so any JSON array field is covered.
        if (cleanVal.startsWith("[") && cleanVal.endsWith("]")) {
            val members = parseJsonArrayLowercase(cleanVal)
            val target = value1.lowercase()
            return when (operator) {
                "member_of", "member of", "contains", "equal" -> members.contains(target)
                "not_member_of", "not member of", "does not contain", "not equal" -> !members.contains(target)
                "empty" -> members.isEmpty()
                "not empty" -> members.isNotEmpty()
                else -> true
            }
        }

        if (operator in BIRTHDAY_OPERATORS) {
            return evaluateBirthday(cleanVal, operator, value1, value2)
        }

        return when (operator) {
            "contains" -> cleanVal.contains(value1, ignoreCase = true)
            "does not contain" -> !cleanVal.contains(value1, ignoreCase = true)
            "equal" -> cleanVal.equals(value1, ignoreCase = true)
            "not equal" -> !cleanVal.equals(value1, ignoreCase = true)
            "empty" -> cleanVal.isBlank()
            "not empty" -> cleanVal.isNotBlank()
            // Strict parsing: a non-numeric value never satisfies a numeric comparison
            "greater than" -> compareNumeric(cleanVal, value1) { field, target -> field > target }
            "less than" -> compareNumeric(cleanVal, value1) { field, target -> field < target }
            "In between" -> {
                val num = cleanVal.toDoubleOrNull()
                val min = value1.toDoubleOrNull()
                val max = value2.toDoubleOrNull()
                if (num != null && min != null && max != null) num in min..max else false
            }
            else -> true
        }
    }

    private fun evaluateBirthday(fieldVal: String, operator: String, value1: String, value2: String): Boolean {
        val birthday = fieldVal.toLongOrNull() ?: return false
        val studentCal = Calendar.getInstance().apply { timeInMillis = birthday }
        return when (operator) {
            "birth_year" -> {
                val year = value1.toIntOrNull() ?: return false
                studentCal.get(Calendar.YEAR) == year
            }
            "birth_month" -> {
                val month = value1.toIntOrNull() ?: return false
                (studentCal.get(Calendar.MONTH) + 1) == month
            }
            "birth_month_year" -> {
                val month = value1.toIntOrNull() ?: return false
                val year = value2.toIntOrNull() ?: return false
                (studentCal.get(Calendar.MONTH) + 1) == month && studentCal.get(Calendar.YEAR) == year
            }
            "exact_birthday" -> {
                val target = value1.toLongOrNull() ?: return false
                val targetCal = Calendar.getInstance().apply { timeInMillis = target }
                studentCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
                        studentCal.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR)
            }
            else -> false
        }
    }

    private fun compareNumeric(fieldVal: String, target: String, predicate: (Double, Double) -> Boolean): Boolean {
        val numField = fieldVal.toDoubleOrNull() ?: return false
        val numTarget = target.toDoubleOrNull() ?: return false
        return predicate(numField, numTarget)
    }

    private fun parseJsonArrayLowercase(raw: String): List<String> {
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i).lowercase())
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

/** Expands an inclusive start..end range into one midnight-normalised timestamp per day. */
fun generateDateList(startDate: Long, endDate: Long): List<Long> {
    val dates = mutableListOf<Long>()
    val startCal = Calendar.getInstance().apply {
        timeInMillis = startDate
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val endCal = Calendar.getInstance().apply {
        timeInMillis = endDate
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    while (!startCal.after(endCal)) {
        dates.add(startCal.timeInMillis)
        startCal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return dates
}
