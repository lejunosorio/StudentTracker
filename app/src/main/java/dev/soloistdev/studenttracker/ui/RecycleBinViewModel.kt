package dev.soloistdev.studenttracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RecycleBinViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudentRepository(application)
    private val _deletedStudents = MutableStateFlow<List<StudentEntity>>(emptyList())
    val deletedStudents: StateFlow<List<StudentEntity>> = _deletedStudents

    init {
        loadDeletedStudents()
    }

    fun loadDeletedStudents() {
        viewModelScope.launch {
            _deletedStudents.value = repository.getAllDeletedStudents()
        }
    }

    fun restoreStudent(studentId: Int) {
        viewModelScope.launch {
            repository.restoreStudent(studentId)
            loadDeletedStudents()
        }
    }

    fun permanentDeleteStudent(studentId: Int) {
        viewModelScope.launch {
            repository.permanentDeleteStudent(studentId)
            loadDeletedStudents()
        }
    }
}