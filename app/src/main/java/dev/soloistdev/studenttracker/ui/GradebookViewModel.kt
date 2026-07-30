package dev.soloistdev.studenttracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.soloistdev.studenttracker.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Calendar

class GradebookViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudentRepository(application)

    private val _columns = MutableStateFlow<List<AssessmentColumnEntity>>(emptyList())
    val columns: StateFlow<List<AssessmentColumnEntity>> = _columns

    private val _savedFilters = MutableStateFlow<List<SavedFilterEntity>>(emptyList())
    val savedFilters: StateFlow<List<SavedFilterEntity>> = _savedFilters

    private val _students = MutableStateFlow<List<StudentEntity>>(emptyList())
    val students: StateFlow<List<StudentEntity>> = _students

    private val _scores = MutableStateFlow<List<AssessmentScoreEntity>>(emptyList())
    val scores: StateFlow<List<AssessmentScoreEntity>> = _scores

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _columns.value = repository.getAllAssessmentColumns()
            _savedFilters.value = repository.getAllSavedFilters()
            _students.value = repository.getAllActiveStudents()
            _scores.value = repository.getAllAssessmentScores()
        }
    }

    fun createGradingSheet(
        name: String,
        maxPoints: Double,
        examDate: Long,
        checkDate: Long,
        filterId: Int
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val column = AssessmentColumnEntity(
                name = name.trim(),
                maxPoints = maxPoints,
                examDate = examDate,
                checkDate = checkDate,
                savedFilterId = filterId
            )
            val columnId = repository.insertAssessmentColumn(column).toInt()

            // Resolve and pre-populate roster slots based on filter
            val activeRoster = repository.getAllActiveStudents()
            val matchedStudents = if (filterId == 0) {
                activeRoster
            } else {
                val filter = repository.getAllSavedFilters().find { it.id == filterId }
                if (filter != null) {
                    val filterState = FilterState(
                        field = filter.fieldName,
                        comparison = filter.comparison,
                        value1 = filter.value1,
                        value2 = filter.value2
                    )
                    activeRoster.filter { student ->
                        applyComparison(getFieldValue(student, filter.fieldName), filterState)
                    }
                } else {
                    activeRoster
                }
            }

            // Create placeholder scores
            matchedStudents.forEach { s ->
                repository.insertAssessmentScore(
                    AssessmentScoreEntity(
                        columnId = columnId,
                        studentId = s.id,
                        score = ""
                    )
                )
            }

            loadData()
        }
    }

    fun saveRosterScores(columnId: Int, scoresMap: Map<Int, String>) {
        viewModelScope.launch {
            scoresMap.forEach { (studentId, score) ->
                repository.insertAssessmentScore(
                    AssessmentScoreEntity(
                        columnId = columnId,
                        studentId = studentId,
                        score = score.trim()
                    )
                )
            }
            loadData()
        }
    }

    fun softDeleteColumn(columnId: Int) {
        viewModelScope.launch {
            repository.softDeleteAssessmentColumn(columnId)
            loadData()
        }
    }

    // Helper functions mirroring local sorting checks
    private fun getFieldValue(student: StudentEntity, field: String): String {
        return when (field) {
            "First Name" -> student.firstName
            "Last Name" -> student.lastName
            "Gender" -> if (student.gender == "F") "Female" else "Male"
            "Address", "Home Address" -> student.address
            "Student Contact" -> student.contactNumber
            "Age" -> {
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val birthCal = Calendar.getInstance().apply { timeInMillis = student.birthday }
                val birthYear = birthCal.get(Calendar.YEAR)
                (currentYear - birthYear).toString()
            }
            "Birthday" -> student.birthday.toString()
            else -> {
                try {
                    JSONObject(student.customDataJson).optString(field, "")
                } catch (_: Exception) {
                    ""
                }
            }
        }
    }

    private fun applyComparison(fieldValue: String, filter: FilterState): Boolean {
        val value1 = filter.value1.trim()
        val value2 = filter.value2.trim()

        if (filter.field == "Birthday") {
            val studentBirthday = fieldValue.toLongOrNull() ?: return false
            val studentCal = Calendar.getInstance().apply { timeInMillis = studentBirthday }
            return when (filter.comparison) {
                "birth_year" -> {
                    val yearVal = value1.toIntOrNull() ?: return false
                    studentCal.get(Calendar.YEAR) == yearVal
                }
                "birth_month" -> {
                    val monthVal = value1.toIntOrNull() ?: return false
                    (studentCal.get(Calendar.MONTH) + 1) == monthVal
                }
                "birth_month_year" -> {
                    val monthVal = value1.toIntOrNull() ?: return false
                    val yearVal = value2.toIntOrNull() ?: return false
                    (studentCal.get(Calendar.MONTH) + 1) == monthVal && studentCal.get(Calendar.YEAR) == yearVal
                }
                "exact_birthday" -> {
                    val targetBirthday = value1.toLongOrNull() ?: return false
                    val calFilter = Calendar.getInstance().apply { timeInMillis = targetBirthday }
                    studentCal.get(Calendar.YEAR) == calFilter.get(Calendar.YEAR) &&
                            studentCal.get(Calendar.DAY_OF_YEAR) == calFilter.get(Calendar.DAY_OF_YEAR)
                }
                else -> false
            }
        }

        return when (filter.comparison) {
            "contains" -> fieldValue.contains(value1, ignoreCase = true)
            "does not contain" -> !fieldValue.contains(value1, ignoreCase = true)
            "equal" -> fieldValue.equals(value1, ignoreCase = true)
            "not equal" -> !fieldValue.equals(value1, ignoreCase = true)
            "greater than" -> {
                val numField = fieldValue.toDoubleOrNull()
                val numVal = value1.toDoubleOrNull()
                if (numField != null && numVal != null) numField > numVal else false
            }
            "less than" -> {
                val numField = fieldValue.toDoubleOrNull()
                val numVal = value1.toDoubleOrNull()
                if (numField != null && numVal != null) numField < numVal else false
            }
            "In between" -> {
                val numField = fieldValue.toDoubleOrNull()
                val numMin = value1.toDoubleOrNull()
                val numMax = value2.toDoubleOrNull()
                if (numField != null && numMin != null && numMax != null) {
                    numField in numMin..numMax
                } else false
            }
            else -> true
        }
    }
}