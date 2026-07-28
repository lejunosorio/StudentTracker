package dev.soloistdev.studenttracker.ui

import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.soloistdev.studenttracker.data.FormTemplateEntity
import dev.soloistdev.studenttracker.data.SavedFilterEntity
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class SavedFiltersViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val repository = StudentRepository(application)
    private val sharedPrefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _filters = MutableStateFlow<List<SavedFilterEntity>>(emptyList())
    val filters: StateFlow<List<SavedFilterEntity>> = _filters

    private val _students = MutableStateFlow<List<StudentEntity>>(emptyList())

    val students: StateFlow<List<StudentUiState>> = _students
        .map { list ->
            val activeBadgeField = sharedPrefs.getString("card_banner_field", "") ?: ""
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)

            list.map { student ->
                val genderStr = if (student.gender == "F") "Female" else "Male"
                val bdayFormatted = sdf.format(Date(student.birthday))
                val cal = Calendar.getInstance().apply { timeInMillis = student.birthday }
                val age = currentYear - cal.get(Calendar.YEAR)

                val dynamicBadgeValue = if (activeBadgeField.isNotEmpty()) {
                    try {
                        val json = JSONObject(student.customDataJson)
                        json.optString(activeBadgeField, "").trim().ifEmpty { null }
                    } catch (e: Exception) {
                        null
                    }
                } else null

                StudentUiState(
                    student = student,
                    genderString = genderStr,
                    formattedBirthday = bdayFormatted,
                    age = age,
                    customBadgeValue = dynamicBadgeValue
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _templates = MutableStateFlow<List<FormTemplateEntity>>(emptyList())
    val templates: StateFlow<List<FormTemplateEntity>> = _templates

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _filters.value = repository.getAllSavedFilters()
            _students.value = repository.getAllActiveStudents()
            _templates.value = repository.getAllFormTemplates()
        }
    }

    // Resolved: Keeps original displayOrder during filter updates [1]
    fun saveFilter(entity: SavedFilterEntity) {
        viewModelScope.launch {
            val filterToInsert = if (entity.id == 0) {
                val maxOrder = _filters.value.maxOfOrNull { it.displayOrder } ?: 0
                entity.copy(displayOrder = maxOrder + 1)
            } else {
                entity // Retains the original drag-and-drop order on edit [1]
            }
            repository.insertSavedFilter(filterToInsert)
            loadData()
        }
    }

    fun deleteFilter(filterId: Int) {
        viewModelScope.launch {
            repository.softDeleteSavedFilter(filterId)
            loadData()
        }
    }

    fun moveFilter(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in _filters.value.indices || toIndex !in _filters.value.indices) return
        viewModelScope.launch {
            val currentList = _filters.value.toMutableList()
            val item = currentList.removeAt(fromIndex)
            currentList.add(toIndex, item)

            val updatedList = currentList.mapIndexed { index, filter ->
                filter.copy(displayOrder = index)
            }
            _filters.value = updatedList
            repository.updateAllSavedFilterOrders(updatedList)
        }
    }
}