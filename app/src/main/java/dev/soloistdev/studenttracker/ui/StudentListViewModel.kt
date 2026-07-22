package dev.soloistdev.studenttracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.soloistdev.studenttracker.data.FormTemplateEntity
import dev.soloistdev.studenttracker.data.Guardian
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.*

data class FilterState(
    val id: String = UUID.randomUUID().toString(),
    val field: String = "",
    val comparison: String = "contains",
    val value1: String = "",
    val value2: String = "",
    val isPinned: Boolean = false
)

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

    // DYNAMIC COMBINED FLOW: Manages live search, sorting, and advanced filtering
    val students: StateFlow<List<StudentEntity>> = combine(
        _rawStudents, _searchQuery, _sortOrder, _activeFilter
    ) { rawList, query, sort, filter ->

        var processedList = if (query.isBlank()) {
            rawList
        } else {
            rawList.filter { student ->
                student.firstName.contains(query, ignoreCase = true) ||
                        student.lastName.contains(query, ignoreCase = true) ||
                        student.address.contains(query, ignoreCase = true)
            }
        }

        if (filter != null && filter.field.isNotEmpty()) {
            processedList = processedList.filter { student ->
                val fieldValue: String = when (filter.field) {
                    "First Name" -> student.firstName
                    "Last Name" -> student.lastName
                    "Gender" -> if (student.gender == "F") "Female" else "Male"
                    "Home Address" -> student.address
                    "Age" -> {
                        val age = Calendar.getInstance().get(Calendar.YEAR) - Calendar.getInstance().apply { timeInMillis = student.birthday }.get(Calendar.YEAR)
                        age.toString()
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
                            val json = JSONObject(student.customDataJson)
                            json.optString(filter.field, "")
                        } catch (e: Exception) {
                            ""
                        }
                    }
                }

                applyComparison(fieldValue, filter)
            }
        }

        when (sort) {
            "lastNameAsc" -> processedList.sortedBy { it.lastName.lowercase() }
            "lastNameDesc" -> processedList.sortedByDescending { it.lastName.lowercase() }
            "ageYoungest" -> processedList.sortedByDescending { it.birthday }
            "recentlyAdded" -> processedList.sortedByDescending { it.id }
            else -> processedList
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadStudents()
        loadTemplates()
    }

    private fun applyComparison(fieldValue: String, filter: FilterState): Boolean {
        val value1 = filter.value1.trim()
        val value2 = filter.value2.trim()

        // specialized Birthday comparator logic
        if (filter.field == "Birthday") {
            val studentBday = fieldValue.toLongOrNull() ?: return false
            val studentCal = Calendar.getInstance().apply { timeInMillis = studentBday }
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
                    val targetBday = value1.toLongOrNull() ?: return false
                    val calFilter = Calendar.getInstance().apply { timeInMillis = targetBday }
                    studentCal.get(Calendar.YEAR) == calFilter.get(Calendar.YEAR) &&
                            studentCal.get(Calendar.DAY_OF_YEAR) == calFilter.get(Calendar.DAY_OF_YEAR)
                }
                else -> false
            }
        }

        // specialized Standard Field comparators
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

    // PRODUCTION LOAD: Directly queries the live database (Seeder has been stripped out)
    fun loadStudents() {
        viewModelScope.launch {
            _rawStudents.value = repository.getAllActiveStudents()
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

    fun toggleStudentSelection(studentId: Int) {
        val currentSet = _selectedStudentIds.value.toMutableSet()
        if (currentSet.contains(studentId)) {
            currentSet.remove(studentId)
            if (currentSet.isEmpty()) {
                _isSelectionMode.value = false // Exit selection if nothing is selected
            }
        } else {
            currentSet.add(studentId)
            _isSelectionMode.value = true // Activate selection mode automatically
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
            clearSelection() // Reset state
            loadStudents() // Refresh active directory roster
        }
    }

    fun softDeleteStudent(studentId: Int) {
        viewModelScope.launch {
            repository.softDeleteStudent(studentId)
            loadStudents() // Re-queries database to update state automatically
        }
    }
}