package dev.soloistdev.studenttracker.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.soloistdev.studenttracker.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class QueryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudentRepository(application)

    // In-memory stacked query rules shared across screens
    val queryRules = mutableStateListOf<QueryRule>()
    var matchOperator by mutableStateOf("AND")

    private val _students = MutableStateFlow<List<StudentEntity>>(emptyList())
    val students: StateFlow<List<StudentEntity>> = _students

    private val _messageTemplates = MutableStateFlow<List<MessageTemplateEntity>>(emptyList())
    val messageTemplates: StateFlow<List<MessageTemplateEntity>> = _messageTemplates

    // Backs the {{absences}} / {{grade}} merge tokens in the bulk message composer
    private val _insights = MutableStateFlow<Map<Int, StudentInsights.Insight>>(emptyMap())
    val insights: StateFlow<Map<Int, StudentInsights.Insight>> = _insights

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _students.value = repository.getAllActiveStudents()
            _messageTemplates.value = repository.getAllMessageTemplates()
            _insights.value = StudentInsights.compute(
                students = _students.value,
                logs = repository.getAllAttendanceLogs(),
                columns = repository.getAllAssessmentColumns(),
                scores = repository.getAllAssessmentScores(),
                categories = repository.getAllAssessmentCategories(),
                incidents = repository.getAllIncidents()
            )
        }
    }

    // Persists the active query configuration as a saved filter group
    fun saveQueryAsFilter(name: String) {
        if (queryRules.isEmpty()) return
        viewModelScope.launch {
            val firstRule = queryRules.first()
            val filter = SavedFilterEntity(
                filterName = name.trim(),
                fieldName = firstRule.field,
                comparison = firstRule.comparison,
                value1 = firstRule.value1,
                value2 = firstRule.value2
            )
            repository.insertSavedFilter(filter)
        }
    }
}