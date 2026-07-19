package dev.soloistdev.studenttracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "form_templates")
data class FormTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fieldName: String, // Sanitized alphanumeric name (e.g. Bus_Route)
    val fieldType: String, // TEXT, NUMBER, DROPDOWN
    val isRequired: Boolean = false,
    val optionsJson: String = "[]" // Holds array of dropdown values if DROPDOWN
)