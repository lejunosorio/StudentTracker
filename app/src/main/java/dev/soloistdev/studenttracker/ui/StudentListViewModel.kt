package dev.soloistdev.studenttracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.IOException

class StudentListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudentRepository(application)

    private val _rawStudents = MutableStateFlow<List<StudentEntity>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // DYNAMIC FILTER ENGINE: Combines the student list with your search query in real-time!
    val students: StateFlow<List<StudentEntity>> = combine(_rawStudents, _searchQuery) { studentsList, query ->
        if (query.isBlank()) {
            studentsList
        } else {
            studentsList.filter { student ->
                student.firstName.contains(query, ignoreCase = true) ||
                        student.lastName.contains(query, ignoreCase = true) ||
                        student.address.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadStudents()
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
                            val student = StudentEntity(
                                firstName = jsonObj.getString("firstName"),
                                lastName = jsonObj.getString("lastName"),
                                gender = jsonObj.getString("gender"),
                                birthday = jsonObj.getLong("birthday"),
                                address = jsonObj.getString("address"),
                                guardiansJson = jsonObj.optString("guardiansJson", "[]"),
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

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }
}