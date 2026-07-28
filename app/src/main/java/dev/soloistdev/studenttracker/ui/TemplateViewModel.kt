package dev.soloistdev.studenttracker.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.soloistdev.studenttracker.data.FormTemplateEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class TemplateViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudentRepository(application)
    private val _templates = MutableStateFlow<List<FormTemplateEntity>>(emptyList())
    val templates: StateFlow<List<FormTemplateEntity>> = _templates

    private val _unconfiguredCustomFields = MutableStateFlow<List<String>>(emptyList())
    val unconfiguredCustomFields: StateFlow<List<String>> = _unconfiguredCustomFields

    init {
        loadTemplates()
    }

    // Centralized strict sanitization block [1]
    private fun sanitizeFieldName(name: String): String {
        return name.trim()
            .replace(" ", "_")
            // Strictly retains only letters, digits, and underscores, stripping out any SQL command characters [1]
            .filter { it.isLetterOrDigit() || it == '_' }
    }

    fun loadTemplates() {
        viewModelScope.launch {
            val list = repository.getAllFormTemplates()
            _templates.value = list

            val activeStudents = repository.getAllActiveStudents()
            val studentKeys = mutableSetOf<String>()
            activeStudents.forEach { student ->
                try {
                    val json = JSONObject(student.customDataJson)
                    json.keys().forEach { studentKeys.add(it) }
                } catch (_: Exception) {}
            }
            val templateKeys = list.map { it.fieldName }.toSet()
            val missingKeys = studentKeys.filter { !templateKeys.contains(it) }
            _unconfiguredCustomFields.value = missingKeys
        }
    }

    fun addTemplate(name: String, type: String, isRequired: Boolean): Boolean {
        val sanitized = sanitizeFieldName(name) // Applies strict sanitization [1]
        val regex = Regex("^[a-zA-Z0-9_]+$")
        if (!regex.matches(sanitized) || sanitized.isBlank()) return false

        viewModelScope.launch {
            val newTemplate = FormTemplateEntity(
                fieldName = sanitized,
                fieldType = type.uppercase(),
                isRequired = isRequired
            )
            repository.insertFormTemplate(newTemplate)
            loadTemplates()
        }
        return true
    }

    // Resolved: Strict sanitization applied to bulk imports [1]
    fun addTemplatesBulk(fields: List<String>) {
        viewModelScope.launch {
            fields.forEach { field ->
                val sanitized = sanitizeFieldName(field) // Strips malicious SQL strings during JSON ingestion [1]
                if (sanitized.isNotBlank()) {
                    val newTemplate = FormTemplateEntity(
                        fieldName = sanitized,
                        fieldType = "TEXT",
                        isRequired = false
                    )
                    repository.insertFormTemplate(newTemplate)
                }
            }
            loadTemplates()
        }
    }

    fun deleteTemplate(id: Int) {
        viewModelScope.launch {
            try {
                val list = repository.getAllFormTemplates()
                val targetTemplate = list.find { it.id == id }

                targetTemplate?.let { template ->
                    val sharedPrefs = getApplication<Application>().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    val activeBannerField = sharedPrefs.getString("card_banner_field", "")
                    if (activeBannerField == template.fieldName) {
                        sharedPrefs.edit().remove("card_banner_field").apply()
                    }
                }

                repository.softDeleteFormTemplate(id)
                loadTemplates()
            } catch (_: Exception) {
                // Suppressed
            }
        }
    }
}