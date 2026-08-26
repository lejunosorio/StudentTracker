package dev.soloistdev.studenttracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.soloistdev.studenttracker.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class InsightsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudentRepository(application)

    private val _students = MutableStateFlow<List<StudentEntity>>(emptyList())
    val students: StateFlow<List<StudentEntity>> = _students

    private val _logs = MutableStateFlow<List<AttendanceLogEntity>>(emptyList())
    val logs: StateFlow<List<AttendanceLogEntity>> = _logs

    private val _insights = MutableStateFlow<Map<Int, StudentInsights.Insight>>(emptyMap())
    val insights: StateFlow<Map<Int, StudentInsights.Insight>> = _insights

    private val _classFilter = MutableStateFlow<String?>(null)
    val classFilter: StateFlow<String?> = _classFilter

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true

            val students = repository.getAllActiveStudents()
            val logs = repository.getAllAttendanceLogs()
            val columns = repository.getAllAssessmentColumns()
            val scores = repository.getAllAssessmentScores()
            val categories = repository.getAllAssessmentCategories()
            val incidents = repository.getAllIncidents()

            _students.value = students
            _logs.value = logs
            _insights.value = StudentInsights.compute(
                students = students,
                logs = logs,
                columns = columns,
                scores = scores,
                categories = categories,
                incidents = incidents
            )

            _isLoading.value = false
        }
    }

    fun setClassFilter(className: String?) {
        _classFilter.value = className
    }

    /** Students in scope, ordered so the ones needing attention are impossible to miss. */
    fun rankedStudents(): List<StudentEntity> {
        val filter = _classFilter.value
        val scoped = if (filter == null) {
            _students.value
        } else {
            _students.value.filter { it.getClassNamesList().contains(filter) }
        }
        val map = _insights.value
        return scoped.sortedWith(
            compareByDescending<StudentEntity> { map[it.id]?.riskLevel?.ordinal ?: 0 }
                .thenByDescending { map[it.id]?.reasons?.size ?: 0 }
                .thenBy { it.lastName.lowercase() }
        )
    }

    fun availableClasses(): List<String> =
        _students.value.flatMap { it.getClassNamesList() }.filter { it.isNotBlank() }.distinct().sorted()
}
