package dev.soloistdev.studenttracker.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.soloistdev.studenttracker.data.Guardian
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class AddEditViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudentRepository(application)

    var firstName by mutableStateOf("")
    var lastName by mutableStateOf("")
    var gender by mutableStateOf("F")
    var birthday by mutableStateOf<Long?>(null)
    var address by mutableStateOf("")
    var picturePath by mutableStateOf("")

    // New: State parameters to capture and hold coordinates
    var latitude by mutableStateOf<Double?>(null)
    var longitude by mutableStateOf<Double?>(null)

    val guardiansStateList = mutableStateListOf<Guardian>()
    val customDataMap = mutableStateMapOf<String, String>()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess

    private var editingStudentId: Int? = null

    fun loadStudentForEditing(studentId: Int) {
        customDataMap.clear()
        guardiansStateList.clear()
        latitude = null
        longitude = null

        viewModelScope.launch {
            val templates = repository.getAllFormTemplates()

            if (studentId == -1) {
                templates.forEach { template ->
                    customDataMap[template.fieldName] = ""
                }
                return@launch
            }

            editingStudentId = studentId
            val students = repository.getAllActiveStudents()
            val student = students.find { it.id == studentId }
            if (student != null) {
                firstName = student.firstName
                lastName = student.lastName
                gender = student.gender
                birthday = student.birthday
                address = student.address
                picturePath = student.picturePath

                val list = Guardian.listFromJsonString(student.guardiansJson)
                guardiansStateList.addAll(list)

                try {
                    val json = JSONObject(student.customDataJson)
                    templates.forEach { template ->
                        customDataMap[template.fieldName] = json.optString(template.fieldName, "")
                    }

                    // Retrieve saved coordinates from customDataJson if they exist
                    if (json.has("latitude") && json.has("longitude")) {
                        latitude = json.optDouble("latitude")
                        longitude = json.optDouble("longitude")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun addGuardian(name: String, relationship: String, contact: String) {
        if (name.isNotBlank() && contact.isNotBlank()) {
            guardiansStateList.add(
                Guardian(
                    name = name.trim(),
                    relationship = relationship.trim().ifEmpty { "Guardian" },
                    phones = listOf(contact.trim())
                )
            )
        }
    }

    fun removeGuardian(index: Int) {
        if (index in guardiansStateList.indices) {
            guardiansStateList.removeAt(index)
        }
    }

    fun saveStudent() {
        if (firstName.isBlank() || lastName.isBlank() || birthday == null || guardiansStateList.isEmpty()) return

        viewModelScope.launch {
            val jsonObject = JSONObject()
            customDataMap.forEach { (key, value) ->
                jsonObject.put(key, value.trim())
            }

            // Serialize coordinates directly into customDataJson
            latitude?.let { jsonObject.put("latitude", it) }
            longitude?.let { jsonObject.put("longitude", it) }

            val student = StudentEntity(
                id = editingStudentId ?: 0,
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                gender = gender,
                birthday = birthday!!,
                address = address.trim(),
                picturePath = picturePath,
                guardiansJson = Guardian.listToJsonString(guardiansStateList.toList()),
                customDataJson = jsonObject.toString()
            )
            repository.insertStudent(student)
            _saveSuccess.value = true
        }
    }
}