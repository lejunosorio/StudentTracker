package dev.soloistdev.studenttracker.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.Guardian
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class AddEditViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudentRepository(application)

    var firstName by mutableStateOf("")
    var lastName by mutableStateOf("")
    var gender by mutableStateOf("F")
    var birthday by mutableStateOf<Long?>(null)
    var address by mutableStateOf("")
    var contactNumber by mutableStateOf("")
    var picturePath by mutableStateOf("")

    // Track selections of multiple classrooms via Compose state list
    val selectedClassrooms = mutableStateListOf<String>()

    val guardiansStateList = mutableStateListOf<Guardian>()
    val customDataMap = mutableStateMapOf<String, String>()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess

    private var editingStudentId: Int? = null

    private val _classrooms = MutableStateFlow<List<dev.soloistdev.studenttracker.data.ClassroomEntity>>(emptyList())
    val classrooms: StateFlow<List<dev.soloistdev.studenttracker.data.ClassroomEntity>> = _classrooms

    fun loadStudentForEditing(studentId: Int) {
        customDataMap.clear()
        guardiansStateList.clear()
        selectedClassrooms.clear()

        viewModelScope.launch {
            _classrooms.value = repository.getAllClassrooms()
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
                contactNumber = student.contactNumber
                picturePath = student.picturePath

                // Deserializes assigned classes into local selection array
                selectedClassrooms.addAll(student.getClassNamesList())

                val list = Guardian.listFromJsonString(student.guardiansJson)
                guardiansStateList.addAll(list)

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

    fun toggleClassroomSelection(className: String) {
        if (selectedClassrooms.contains(className)) {
            selectedClassrooms.remove(className)
        } else {
            selectedClassrooms.add(className)
        }
    }

    fun addGuardian(name: String, relationship: String, contact: String) {
        if (name.isNotBlank() && contact.isNotBlank()) {
            val fallbackRelationship = getApplication<Application>().getString(R.string.guardian_fallback_relationship)

            guardiansStateList.add(
                Guardian(
                    name = name.trim(),
                    relationship = relationship.trim().ifEmpty { fallbackRelationship },
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

            val classesJsonArray = JSONArray().apply {
                selectedClassrooms.forEach { put(it) }
            }

            // Retain seating configurations only for classrooms that remain active
            val oldStudent = editingStudentId?.let { id -> repository.getAllActiveStudents().find { it.id == id } }
            val oldSeatingJson = oldStudent?.seatingJson ?: "{}"
            val updatedSeatingJson = try {
                val oldObj = JSONObject(oldSeatingJson)
                val updatedObj = JSONObject()
                selectedClassrooms.forEach { activeClass ->
                    if (oldObj.has(activeClass)) {
                        updatedObj.put(activeClass, oldObj.getJSONObject(activeClass))
                    }
                }
                updatedObj.toString()
            } catch (e: Exception) {
                e.printStackTrace()
                "{}"
            }

            val student = StudentEntity(
                id = editingStudentId ?: 0,
                firstName = firstName.trim(),
                lastName = lastName.trim(),
                gender = gender,
                birthday = birthday!!,
                address = address.trim(),
                contactNumber = contactNumber.trim(),
                picturePath = picturePath,
                guardiansJson = Guardian.listToJsonString(guardiansStateList.toList()),
                customDataJson = jsonObject.toString(),
                classNamesJson = classesJsonArray.toString(),
                seatingJson = updatedSeatingJson
            )
            repository.insertStudent(student)
            _saveSuccess.value = true
        }
    }
}