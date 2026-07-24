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

class TemplateViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StudentRepository(application)
    private val _templates = MutableStateFlow<List<FormTemplateEntity>>(emptyList())
    val templates: StateFlow<List<FormTemplateEntity>> = _templates

    init {
        loadTemplates()
    }

    fun loadTemplates() {
        viewModelScope.launch {
            val list = repository.getAllFormTemplates()

            _templates.value = list
        }
    }

    // UPDATED: Now accepts isRequired parameter to save dynamic mandatory states
    fun addTemplate(name: String, type: String, isRequired: Boolean): Boolean {
        val sanitized = name.trim().replace(" ", "_")
        val regex = Regex("^[a-zA-Z0-9_]+$")
        if (!regex.matches(sanitized) || sanitized.isBlank()) return false

        viewModelScope.launch {
            val newTemplate = FormTemplateEntity(
                fieldName = sanitized,
                fieldType = type.uppercase(),
                isRequired = isRequired // Fixed: Saves true/false dynamically
            )
            repository.insertFormTemplate(newTemplate)
            loadTemplates()
        }
        return true
    }

    // UPDATED: Deletes template and automatically clears the shared card banner preference if matched [3]
    fun deleteTemplate(id: Int) {
        viewModelScope.launch {
            try {
                val list = repository.getAllFormTemplates()
                val targetTemplate = list.find { it.id == id }

                targetTemplate?.let { template ->
                    val sharedPrefs = getApplication<Application>().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    val activeBannerField = sharedPrefs.getString("card_banner_field", "")
                    if (activeBannerField == template.fieldName) {
                        sharedPrefs.edit().remove("card_banner_field").apply() // Automatic cleanup [3]
                    }
                }

                repository.deleteFormTemplate(id)
                loadTemplates()
            } catch (_: Exception) {
                // Suppressed warning
            }
        }
    }
}