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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.*

// SPRINT 9 UPDATE: Added unique ID to cleanly track and toggle multiple pinned filters
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

    // SPRINT 9 DUAL-STATE STORAGE: Separates the currently active filter from your pinned collection
    private val _activeFilter = MutableStateFlow<FilterState?>(null)
    val activeFilter: StateFlow<FilterState?> = _activeFilter

    private val _pinnedFilters = MutableStateFlow<List<FilterState>>(emptyList())
    val pinnedFilters: StateFlow<List<FilterState>> = _pinnedFilters

    private val _availableTemplates = MutableStateFlow<List<FormTemplateEntity>>(emptyList())
    val availableTemplates: StateFlow<List<FormTemplateEntity>> = _availableTemplates

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

        // Apply active filter if one is selected (Null means "All" is active)
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

        if (filter.field == "Birthday") {
            val studentBday = fieldValue.toLongOrNull() ?: 0L
            val filterMinBday = value1.toLongOrNull() ?: 0L
            val filterMaxBday = value2.toLongOrNull() ?: 0L

            return when (filter.comparison) {
                "equal" -> {
                    val calStudent = Calendar.getInstance().apply { timeInMillis = studentBday }
                    val calFilter = Calendar.getInstance().apply { timeInMillis = filterMinBday }
                    calStudent.get(Calendar.YEAR) == calFilter.get(Calendar.YEAR) &&
                            calStudent.get(Calendar.DAY_OF_YEAR) == calFilter.get(Calendar.DAY_OF_YEAR)
                }
                "in range" -> studentBday in filterMinBday..filterMaxBday
                else -> true
            }
        }

        return when (filter.comparison) {
            "contains" -> fieldValue.contains(value1, ignoreCase = true)
            "equal" -> fieldValue.equals(value1, ignoreCase = true)
            "not equal" -> !fieldValue.equals(value1, ignoreCase = true)
            "does not contain" -> !fieldValue.contains(value1, ignoreCase = true)
            "empty" -> fieldValue.isBlank()
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
            "in range" -> {
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

    private fun loadJsonFromAsset(fileName: String): String? {
        return try {
            val inputStream = getApplication<Application>().assets.open(fileName)
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            String(buffer, Charsets.UTF_8)
        } catch (ex: IOException) {
            ex.printStackTrace()
            null
        }
    }

    fun loadStudents() {
        viewModelScope.launch {
            var list = repository.getAllActiveStudents()

            if (list.isEmpty()) {
                val jsonString = loadJsonFromAsset("defaultdata.json")
                if (jsonString != null) {
                    try {
                        val jsonArray = JSONArray(jsonString)
                        for (i in 0 until jsonArray.length()) {
                            val jsonObj = jsonArray.getJSONObject(i)

                            val defaultGuardians = listOf(
                                Guardian(
                                    name = jsonObj.optString("guardianName", "Jane Doe"),
                                    relationship = "Mother",
                                    phones = listOf(jsonObj.optString("guardianContact", "555-0198"), "555-0199")
                                )
                            )

                            val student = StudentEntity(
                                firstName = jsonObj.getString("firstName"),
                                lastName = jsonObj.getString("lastName"),
                                gender = jsonObj.getString("gender"),
                                birthday = jsonObj.getLong("birthday"),
                                address = jsonObj.getString("address"),
                                guardiansJson = Guardian.listToJsonString(defaultGuardians),
                                customDataJson = jsonObj.getString("customDataJson")
                            )
                            repository.insertStudent(student)
                        }
                        list = repository.getAllActiveStudents()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            _rawStudents.value = list
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

    // SPRINT 9 WORKBENCH OPERATORS:
    fun applyFilter(filter: FilterState) {
        _activeFilter.value = filter
        if (filter.isPinned) {
            // Add to our pinned directory, avoiding duplicates
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
        _activeFilter.value = null // Resets view-all while preserving pinned layout chips
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
}