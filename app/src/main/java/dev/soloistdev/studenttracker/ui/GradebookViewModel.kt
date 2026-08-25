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
                        FilterEngine.applyComparison(FilterEngine.getFieldValue(student, filter.fieldName), filterState)
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
}