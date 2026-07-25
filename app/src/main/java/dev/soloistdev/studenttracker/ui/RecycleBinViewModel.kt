package dev.soloistdev.studenttracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.soloistdev.studenttracker.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RecycleBinViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudentRepository(application)

    private val _deletedStudents = MutableStateFlow<List<StudentEntity>>(emptyList())
    val deletedStudents: StateFlow<List<StudentEntity>> = _deletedStudents

    private val _deletedTemplates = MutableStateFlow<List<FormTemplateEntity>>(emptyList())
    val deletedTemplates: StateFlow<List<FormTemplateEntity>> = _deletedTemplates

    private val _deletedFilters = MutableStateFlow<List<SavedFilterEntity>>(emptyList())
    val deletedFilters: StateFlow<List<SavedFilterEntity>> = _deletedFilters

    private val _deletedRecords = MutableStateFlow<List<AttendanceRecordEntity>>(emptyList())
    val deletedRecords: StateFlow<List<AttendanceRecordEntity>> = _deletedRecords

    init {
        loadAllDeleted()
    }

    fun loadAllDeleted() {
        viewModelScope.launch {
            _deletedStudents.value = repository.getAllDeletedStudents()
            _deletedTemplates.value = repository.getAllDeletedFormTemplates()
            _deletedFilters.value = repository.getAllDeletedSavedFilters()
            _deletedRecords.value = repository.getAllDeletedAttendanceRecords()
        }
    }

    // --- STUDENT LOGS ACTIONS ---
    fun restoreStudent(studentId: Int) {
        viewModelScope.launch {
            repository.restoreStudent(studentId)
            loadAllDeleted()
        }
    }

    fun permanentDeleteStudent(studentId: Int) {
        viewModelScope.launch {
            repository.permanentDeleteStudent(studentId)
            loadAllDeleted()
        }
    }

    // --- CUSTOM FIELDS ACTIONS ---
    fun restoreTemplate(templateId: Int) {
        viewModelScope.launch {
            repository.restoreFormTemplate(templateId)
            loadAllDeleted()
        }
    }

    fun permanentDeleteTemplate(templateId: Int) {
        viewModelScope.launch {
            repository.permanentDeleteFormTemplate(templateId)
            loadAllDeleted()
        }
    }

    // --- SAVED FILTERS ACTIONS ---
    // CORRECTED: Aligned method name with RecycleBinScreen's exact restoreFilter call [1]
    fun restoreFilter(filterId: Int) {
        viewModelScope.launch {
            repository.restoreSavedFilter(filterId)
            loadAllDeleted()
        }
    }

    fun permanentDeleteSavedFilter(filterId: Int) {
        viewModelScope.launch {
            repository.permanentDeleteSavedFilter(filterId)
            loadAllDeleted()
        }
    }

    // --- ATTENDANCE EVENT RECORDS ACTIONS ---
    fun restoreAttendanceRecord(recordId: Int) {
        viewModelScope.launch {
            repository.restoreAttendanceRecord(recordId)
            loadAllDeleted()
        }
    }

    fun permanentDeleteAttendanceRecord(recordId: Int) {
        viewModelScope.launch {
            repository.permanentDeleteAttendanceRecord(recordId)
            loadAllDeleted()
        }
    }
}