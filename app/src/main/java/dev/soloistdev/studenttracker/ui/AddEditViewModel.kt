package dev.soloistdev.studenttracker.ui

import android.app.Application
import androidx.compose.runtime.getValue
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
    var guardianName by mutableStateOf("")
    var guardianContact by mutableStateOf("")
    var picturePath by mutableStateOf("") // Added picturePath state

    val customDataMap = mutableStateMapOf<String, String>()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess

    private var editingStudentId: Int? = null

    fun loadStudentForEditing(studentId: Int) {
        customDataMap.clear()

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
                picturePath = student.picturePath // Load picturePath

                val guardians = Guardian.listFromJsonString(student.guardiansJson)
                if (guardians.isNotEmpty()) {
                    val primaryGuardian = guardians[0]
                    guardianName = primaryGuardian.name
                    guardianContact = primaryGuardian.phones.firstOrNull() ?: ""
                }

                try {
                    val json = JSONObject(student.customDataJson)
                    templates.forEach { template ->
                        customDataMap[template.fieldName] = json.optString(template.fieldName, "")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun saveStudent() {
        if (firstName.isBlank() || lastName.isBlank() || birthday == null || guardianName.isBlank() || guardianContact.isBlank()) return

        viewModelScope.launch {
            val jsonObject = JSONObject()
            customDataMap.forEach { (key, value) ->
                jsonObject.put(key, value.trim())
            }
            val customData = jsonObject.toString()

            val guardiansList = listOf(
                Guardian(
                    name = guardianName.trim(),
                    relationship = "Guardian",
                    phones = listOf(guardianContact.trim())
                )
            )

            val student = StudentEntity(
                id = editingStudentId ?: 0,
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                gender = gender,
                birthday = birthday!!,
                address = address.trim(),
                picturePath = picturePath, // Save picturePath
                guardiansJson = Guardian.listToJsonString(guardiansList),
                customDataJson = customData
            )
            repository.insertStudent(student)
            _saveSuccess.value = true
        }
    }
}