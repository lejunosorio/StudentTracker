package dev.soloistdev.studenttracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_filters")
data class SavedFilterEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filterName: String,     // Friendly customized label name
    val fieldName: String,      // Target DB attribute (e.g. Age, Gender)
    val comparison: String,     // Operator (e.g. contains, in range)
    val value1: String,         // Primary filtering condition value
    val value2: String = "",    // Secondary boundary value (for in range mode)
    val displayOrder: Int = 0   // Drag-and-drop sort indicator
)