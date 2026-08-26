package dev.soloistdev.studenttracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.soloistdev.studenttracker.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    private val _terms = MutableStateFlow<List<GradingTermEntity>>(emptyList())
    val terms: StateFlow<List<GradingTermEntity>> = _terms

    private val _categories = MutableStateFlow<List<AssessmentCategoryEntity>>(emptyList())
    val categories: StateFlow<List<AssessmentCategoryEntity>> = _categories

    private val _rubrics = MutableStateFlow<List<RubricEntity>>(emptyList())
    val rubrics: StateFlow<List<RubricEntity>> = _rubrics

    private val _rubricLevels = MutableStateFlow<List<RubricLevelEntity>>(emptyList())
    val rubricLevels: StateFlow<List<RubricLevelEntity>> = _rubricLevels

    // 0 = every term. Drives both the assessment list and the running grade column.
    private val _selectedTermId = MutableStateFlow(0)
    val selectedTermId: StateFlow<Int> = _selectedTermId

    /** Assessments in scope for the selected grading period. */
    val visibleColumns: StateFlow<List<AssessmentColumnEntity>> =
        combine(_columns, _selectedTermId) { cols, termId ->
            if (termId == 0) cols else cols.filter { it.termId == termId }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Running grade per student, recomputed whenever scores, weights or the term change. */
    val grades: StateFlow<Map<Int, GradeCalculator.StudentGrade>> =
        combine(_students, _columns, _scores, _categories, _selectedTermId) { students, cols, scores, cats, termId ->
            GradeCalculator.computeForRoster(students, cols, scores, cats, termId)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val classAverage: StateFlow<Double?> =
        grades.combine(_selectedTermId) { gradeMap, _ ->
            GradeCalculator.classAverage(gradeMap.values)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _columns.value = repository.getAllAssessmentColumns()
            _savedFilters.value = repository.getAllSavedFilters()
            _students.value = repository.getAllActiveStudents()
            _scores.value = repository.getAllAssessmentScores()
            _terms.value = repository.getAllGradingTerms()
            _categories.value = repository.getAllAssessmentCategories()
            _rubrics.value = repository.getAllRubrics()
            _rubricLevels.value = repository.getAllRubricLevels()

            // Default the view to whichever period is marked active
            if (_selectedTermId.value == 0) {
                _terms.value.find { it.isActive }?.let { _selectedTermId.value = it.id }
            }
        }
    }

    fun selectTerm(termId: Int) {
        _selectedTermId.value = termId
    }

    fun createGradingSheet(
        name: String,
        maxPoints: Double,
        examDate: Long,
        checkDate: Long,
        filterId: Int,
        termId: Int = _selectedTermId.value,
        rubricId: Int = 0,
        categoryId: Int = 0
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val column = AssessmentColumnEntity(
                name = name.trim(),
                maxPoints = maxPoints,
                examDate = examDate,
                checkDate = checkDate,
                savedFilterId = filterId,
                termId = termId,
                categoryId = categoryId,
                rubricId = rubricId
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

            matchedStudents.forEach { s ->
                repository.upsertAssessmentScore(columnId, s.id, "")
            }

            loadData()
        }
    }

    fun saveRosterScores(columnId: Int, scoresMap: Map<Int, String>) {
        viewModelScope.launch {
            scoresMap.forEach { (studentId, score) ->
                // Upsert, not insert: the previous insert-with-id-0 appended a fresh row on
                // every save, so scores accumulated duplicates and averages double-counted.
                repository.upsertAssessmentScore(columnId, studentId, score.trim())
            }
            loadData()
        }
    }


    /**
     * Edits an existing sheet in place. Moving a sheet between grading periods reassigns every
     * score it holds to the new period, since the running grade is scoped by term.
     */
    fun updateColumn(column: AssessmentColumnEntity) {
        viewModelScope.launch {
            repository.updateAssessmentColumn(column.copy(lastModified = System.currentTimeMillis()))
            loadData()
        }
    }

    fun softDeleteColumn(columnId: Int) {
        viewModelScope.launch {
            repository.softDeleteAssessmentColumn(columnId)
            loadData()
        }
    }

    // --- Grading periods ---

    fun saveTerm(term: GradingTermEntity) {
        viewModelScope.launch {
            repository.insertGradingTerm(term)
            loadData()
        }
    }

    fun deleteTerm(termId: Int) {
        viewModelScope.launch {
            repository.softDeleteGradingTerm(termId)
            if (_selectedTermId.value == termId) _selectedTermId.value = 0
            loadData()
        }
    }

    fun setActiveTerm(termId: Int) {
        viewModelScope.launch {
            repository.setActiveTerm(termId)
            _selectedTermId.value = termId
            loadData()
        }
    }

    // --- Weighted categories ---

    fun saveCategory(category: AssessmentCategoryEntity) {
        viewModelScope.launch {
            repository.insertAssessmentCategory(category)
            loadData()
        }
    }


    /** Renames a period or moves its date range. Uses @Update: no cascade risk either way. */
    fun updateTerm(term: GradingTermEntity) {
        viewModelScope.launch {
            repository.updateGradingTerm(term.copy(lastModified = System.currentTimeMillis()))
            loadData()
        }
    }

    fun updateCategory(category: AssessmentCategoryEntity) {
        viewModelScope.launch {
            repository.updateAssessmentCategory(category.copy(lastModified = System.currentTimeMillis()))
            loadData()
        }
    }

    /**
     * Renames a rubric.
     *
     * updateRubric, not insert: rubric_levels cascade from this row, so an INSERT OR REPLACE
     * would silently delete every level the rubric owns.
     */
    fun renameRubric(rubric: RubricEntity, newName: String) {
        viewModelScope.launch {
            repository.updateRubric(rubric.copy(name = newName.trim(), lastModified = System.currentTimeMillis()))
            loadData()
        }
    }
    fun deleteCategory(categoryId: Int) {
        viewModelScope.launch {
            repository.softDeleteAssessmentCategory(categoryId)
            loadData()
        }
    }

    /** Total assigned weight, so the editor can warn when it does not add up to 100. */
    val assignedWeight: StateFlow<Double> =
        _categories.combine(_selectedTermId) { cats, _ ->
            cats.sumOf { it.weight }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
}
