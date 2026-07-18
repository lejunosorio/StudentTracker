package dev.soloistdev.studenttracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.IOException

class StudentListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudentRepository(application)
    private val _students = MutableStateFlow<List<StudentEntity>>(emptyList())
    val students: StateFlow<List<StudentEntity>> = _students

    init {
        loadStudents()
    }

    // Helper function to safely read an asset file as a String
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

            // If the local database is empty, seed it using our defaultdata.json asset!
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
                                birthday = jsonObj.getLong("birthday"),
                                address = jsonObj.getString("address"),
                                customDataJson = jsonObj.getString("customDataJson")
                            )
                            repository.insertStudent(student)
                        }
                        // Refresh the list from the database
                        list = repository.getAllActiveStudents()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            _students.value = list
        }
    }
}