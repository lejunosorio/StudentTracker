package dev.soloistdev.studenttracker.ui

import android.app.Application
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
            var list = repository.getAllFormTemplates()

            if (list.isEmpty()) {
                val defaultTemplates = listOf(
                    FormTemplateEntity(fieldName = "Purok", fieldType = "TEXT", isRequired = true),
                    FormTemplateEntity(fieldName = "Status", fieldType = "TEXT", isRequired = true),
                    FormTemplateEntity(fieldName = "Bautisado", fieldType = "TEXT", isRequired = false)
                )
                defaultTemplates.forEach { repository.insertFormTemplate(it) }
                list = repository.getAllFormTemplates()
            }
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

    fun deleteTemplate(id: Int) {
        viewModelScope.launch {
            repository.deleteFormTemplate(id)
            loadTemplates()
        }
    }
}