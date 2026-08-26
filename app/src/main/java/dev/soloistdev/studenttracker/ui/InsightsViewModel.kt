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

    private val _terms = MutableStateFlow<List<GradingTermEntity>>(emptyList())
    val terms: StateFlow<List<GradingTermEntity>> = _terms

    /** Null means the whole year. Defaults to the active period, which is what a teacher is in. */
    private val _selectedTerm = MutableStateFlow<GradingTermEntity?>(null)
    val selectedTerm: StateFlow<GradingTermEntity?> = _selectedTerm

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
            val terms = repository.getAllGradingTerms()

            _students.value = students
            _logs.value = logs
            _terms.value = terms

            // Open on the period being taught, so the first thing shown describes now rather than
            // averaging a concern that was resolved two quarters ago back into invisibility.
            val term = _selectedTerm.value?.let { chosen -> terms.find { it.id == chosen.id } }
                ?: terms.firstOrNull { it.isActive }
            _selectedTerm.value = term

            recompute(students, logs, columns, scores, categories, incidents, term)
            _isLoading.value = false
        }
    }

    /** Re-scopes everything to [term]; null widens back to the whole year. */
    fun setTerm(term: GradingTermEntity?) {
        _selectedTerm.value = term
        viewModelScope.launch {
            recompute(
                students = _students.value,
                logs = _logs.value,
                columns = repository.getAllAssessmentColumns(),
                scores = repository.getAllAssessmentScores(),
                categories = repository.getAllAssessmentCategories(),
                incidents = repository.getAllIncidents(),
                term = term
            )
        }
    }

    private fun recompute(
        students: List<StudentEntity>,
        logs: List<AttendanceLogEntity>,
        columns: List<AssessmentColumnEntity>,
        scores: List<AssessmentScoreEntity>,
        categories: List<AssessmentCategoryEntity>,
        incidents: List<BehaviorIncidentEntity>,
        term: GradingTermEntity?
    ) {
        _insights.value = StudentInsights.compute(
            students = students,
            logs = logs,
            columns = columns,
            scores = scores,
            categories = categories,
            incidents = incidents,
            term = term
        )
    }

    /** The attendance strip for one student, scoped the same way the flags are. */
    fun timelineFor(studentId: Int): List<Pair<Long, String>> =
        StudentInsights.attendanceTimeline(studentId, _logs.value, _selectedTerm.value)

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
