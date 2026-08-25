package dev.soloistdev.studenttracker.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.soloistdev.studenttracker.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class StudentListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudentRepository(application)

    private val _rawStudents = MutableStateFlow<List<StudentEntity>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _sortOrder = MutableStateFlow("lastNameAsc")
    val sortOrder: StateFlow<String> = _sortOrder

    private val _activeFilter = MutableStateFlow<FilterState?>(null)
    val activeFilter: StateFlow<FilterState?> = _activeFilter

    private val _pinnedFilters = MutableStateFlow<List<FilterState>>(emptyList())
    val pinnedFilters: StateFlow<List<FilterState>> = _pinnedFilters

    private val _availableTemplates = MutableStateFlow<List<FormTemplateEntity>>(emptyList())
    val availableTemplates: StateFlow<List<FormTemplateEntity>> = _availableTemplates

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode

    private val _selectedStudentIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedStudentIds: StateFlow<Set<Int>> = _selectedStudentIds

    private val _isInitialLoadCompleted = MutableStateFlow(false)
    val isInitialLoadCompleted: StateFlow<Boolean> = _isInitialLoadCompleted

    private val sharedPrefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    val students: StateFlow<List<StudentUiState>> = combine(
        _rawStudents, _searchQuery, _sortOrder, _activeFilter
    ) { rawList, query, sort, filter ->

        var processedList = if (query.isBlank()) {
            rawList
        } else {
            rawList.filter { student ->
                val classes = student.getClassNamesList()
                student.firstName.contains(query, ignoreCase = true) ||
                        student.lastName.contains(query, ignoreCase = true) ||
                        student.address.contains(query, ignoreCase = true) ||
                        student.contactNumber.contains(query, ignoreCase = true) ||
                        classes.any { it.contains(query, ignoreCase = true) }
            }
        }

        if (filter != null && filter.field.isNotEmpty()) {
            processedList = processedList.filter { student ->
                FilterEngine.applyComparison(FilterEngine.getFieldValue(student, filter.field), filter)
            }
        }

        val sortedList = when (sort) {
            "lastNameAsc" -> processedList.sortedBy { it.lastName.lowercase() }
            "lastNameDesc" -> processedList.sortedByDescending { it.lastName.lowercase() }
            "ageYoungest" -> processedList.sortedByDescending { it.birthday }
            "recentlyAdded" -> processedList.sortedByDescending { it.id }
            else -> processedList
        }

        val activeBadgeField = sharedPrefs.getString("card_banner_field", "") ?: ""
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        sortedList.map { student ->
            val genderStr = if (student.gender == "F") "Female" else "Male"
            val birthdayFormatted = sdf.format(Date(student.birthday))
            val cal = Calendar.getInstance().apply { timeInMillis = student.birthday }
            val age = currentYear - cal.get(Calendar.YEAR)

            val dynamicBadgeValue = if (activeBadgeField.isNotEmpty()) {
                try {
                    val json = JSONObject(student.customDataJson)
                    json.optString(activeBadgeField, "").trim().ifEmpty { null }
                } catch (_: Exception) {
                    null
                }
            } else null

            StudentUiState(
                student = student,
                genderString = genderStr,
                formattedBirthday = birthdayFormatted,
                age = age,
                customBadgeValue = dynamicBadgeValue
            )
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadData()
    }

    fun loadData() {
        loadStudents()
        loadTemplates()
    }

    fun loadStudents() {
        viewModelScope.launch {
            _rawStudents.value = repository.getAllActiveStudents()
            _isInitialLoadCompleted.value = true
        }
    }

    fun loadTemplates() {
        viewModelScope.launch {
            _availableTemplates.value = repository.getAllFormTemplates()
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun updateSortOrder(order: String) {
        _sortOrder.value = order
    }

    fun applyFilter(filter: FilterState) {
        _activeFilter.value = filter
        if (filter.isPinned) {
            val currentList = _pinnedFilters.value.toMutableList()
            val exists = currentList.any { it.field == filter.field && it.comparison == filter.comparison && it.value1 == filter.value1 && it.value2 == filter.value2 }
            if (!exists) {
                currentList.add(filter)
                _pinnedFilters.value = currentList
            }
        }
    }

    fun selectPinnedFilter(filter: FilterState) {
        _activeFilter.value = filter
    }

    fun clearActiveFilter() {
        _activeFilter.value = null
    }

    fun removePinnedFilter(filter: FilterState) {
        val currentList = _pinnedFilters.value.toMutableList()
        currentList.removeAll { it.id == filter.id }
        _pinnedFilters.value = currentList
        if (_activeFilter.value?.id == filter.id) {
            _activeFilter.value = null
        }
    }

    fun clearFilter() {
        _activeFilter.value = null
        _pinnedFilters.value = emptyList()
    }

    fun createManualAttendanceRecord(
        name: String,
        selectedIds: List<Int>,
        startDateMillis: Long,
        endDateMillis: Long,
        onCreated: (Int, Long) -> Unit
    ) {
        viewModelScope.launch {
            val normalizedStart = Calendar.getInstance().apply {
                timeInMillis = startDateMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val normalizedEnd = Calendar.getInstance().apply {
                timeInMillis = endDateMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val record = dev.soloistdev.studenttracker.data.AttendanceRecordEntity(
                name = name.trim(),
                savedFilterId = 0,
                startDate = normalizedStart,
                endDate = normalizedEnd
            )
            val recordId = repository.insertAttendanceRecord(record).toInt()

            val daysList = generateDateList(normalizedStart, normalizedEnd)
            daysList.forEach { date ->
                selectedIds.forEach { studentId ->
                    repository.insertAttendanceLog(
                        dev.soloistdev.studenttracker.data.AttendanceLogEntity(
                            recordId = recordId,
                            dateMillis = date,
                            studentId = studentId,
                            status = "NOT_SET"
                        )
                    )
                }
            }

            clearSelection()
            onCreated(recordId, normalizedStart)
        }
    }

    fun toggleStudentSelection(studentId: Int) {
        val currentSet = _selectedStudentIds.value.toMutableSet()
        if (currentSet.contains(studentId)) {
            currentSet.remove(studentId)
            if (currentSet.isEmpty()) {
                _isSelectionMode.value = false
            }
        } else {
            currentSet.add(studentId)
            _isSelectionMode.value = true
        }
        _selectedStudentIds.value = currentSet
    }

    fun clearSelection() {
        _selectedStudentIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun deleteSelectedStudents() {
        val idsToDelete = _selectedStudentIds.value
        if (idsToDelete.isEmpty()) return
        viewModelScope.launch {
            idsToDelete.forEach { id ->
                repository.softDeleteStudent(id)
            }
            clearSelection()
            loadStudents()
        }
    }

    fun softDeleteStudent(studentId: Int) {
        viewModelScope.launch {
            repository.softDeleteStudent(studentId)
            loadStudents()
        }
    }

    fun updateCustomFieldForSelected(fieldName: String, newValue: String) {
        val selectedIds = _selectedStudentIds.value
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            val activeStudents = repository.getAllActiveStudents()
            selectedIds.forEach { studentId ->
                val targetStudent = activeStudents.find { it.id == studentId }
                targetStudent?.let { currentStudent ->
                    try {
                        val json = JSONObject(currentStudent.customDataJson)
                        json.put(fieldName, newValue.trim())

                        val updatedStudent = currentStudent.copy(
                            customDataJson = json.toString()
                        )
                        repository.insertStudent(updatedStudent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            clearSelection()
            loadStudents()
        }
    }

    fun createManualGradebookRecord(
        name: String,
        selectedIds: List<Int>,
        maxPoints: Double,
        examDate: Long,
        checkDate: Long,
        onCreated: (Int) -> Unit
    ) {
        viewModelScope.launch {
            val column = dev.soloistdev.studenttracker.data.AssessmentColumnEntity(
                name = name.trim(),
                maxPoints = maxPoints,
                examDate = examDate,
                checkDate = checkDate,
                savedFilterId = 0
            )
            val columnId = repository.insertAssessmentColumn(column).toInt()

            selectedIds.forEach { studentId ->
                repository.insertAssessmentScore(
                    dev.soloistdev.studenttracker.data.AssessmentScoreEntity(
                        columnId = columnId,
                        studentId = studentId,
                        score = ""
                    )
                )
            }

            clearSelection()
            onCreated(columnId)
        }
    }

    // Dynamic addition of students to a selected classroom cohort
    fun addStudentsToClassroom(studentIds: List<Int>, className: String) {
        viewModelScope.launch {
            val activeStudents = repository.getAllActiveStudents()
            studentIds.forEach { studentId ->
                val targetStudent = activeStudents.find { it.id == studentId }
                targetStudent?.let { currentStudent ->
                    val currentClasses = currentStudent.getClassNamesList().toMutableList()
                    if (!currentClasses.contains(className)) {
                        currentClasses.add(className)
                        val updatedClassesJson = JSONArray().apply {
                            currentClasses.forEach { put(it) }
                        }.toString()
                        val updatedStudent = currentStudent.copy(
                            classNamesJson = updatedClassesJson,
                            lastModified = System.currentTimeMillis()
                        )
                        repository.insertStudent(updatedStudent)
                    }
                }
            }
            loadStudents()
        }
    }

    // Dynamic removal of students from a selected classroom cohort
    fun removeStudentsFromClassroom(studentIds: List<Int>, className: String) {
        viewModelScope.launch {
            val activeStudents = repository.getAllActiveStudents()
            studentIds.forEach { studentId ->
                val targetStudent = activeStudents.find { it.id == studentId }
                targetStudent?.let { currentStudent ->
                    val currentClasses = currentStudent.getClassNamesList().toMutableList()
                    if (currentClasses.contains(className)) {
                        currentClasses.remove(className)
                        val updatedClassesJson = JSONArray().apply {
                            currentClasses.forEach { put(it) }
                        }.toString()

                        // Clean seating coordinate configurations to prevent leaks
                        val seatingObj = try {
                            JSONObject(currentStudent.seatingJson)
                        } catch (e: Exception) {
                            JSONObject()
                        }
                        seatingObj.remove(className)

                        val updatedStudent = currentStudent.copy(
                            classNamesJson = updatedClassesJson,
                            seatingJson = seatingObj.toString(),
                            lastModified = System.currentTimeMillis()
                        )
                        repository.insertStudent(updatedStudent)
                    }
                }
            }
            clearSelection()
            loadStudents()
        }
    }
}