package dev.soloistdev.studenttracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StudentListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudentRepository(application)
    private val _students = MutableStateFlow<List<StudentEntity>>(emptyList())
    val students: StateFlow<List<StudentEntity>> = _students

    init {
        loadStudents()
    }

    fun loadStudents() {
        viewModelScope.launch {
            var list = repository.getAllActiveStudents()

            // If the database is empty, pre-populate with our mockup's dummy students!
            if (list.isEmpty()) {
                val dummy1 = StudentEntity(
                    firstName = "John",
                    lastName = "Doe",
                    birthday = 1193270400000L, // Oct 25, 2007 (Unix Timestamp)
                    address = "123 Main St, Springfield"
                )
                val dummy2 = StudentEntity(
                    firstName = "Sarah",
                    lastName = "Smith",
                    birthday = 1200268800000L, // Jan 14, 2008 (Unix Timestamp)
                    address = "456 Oak Ln, Springfield"
                )
                repository.insertStudent(dummy1)
                repository.insertStudent(dummy2)
                list = repository.getAllActiveStudents()
            }
            _students.value = list
        }
    }
}