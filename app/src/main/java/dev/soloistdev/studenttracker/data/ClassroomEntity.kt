package dev.soloistdev.studenttracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "classrooms")
data class ClassroomEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,         // e.g., "Class 10-A"
    val startTime: String,    // e.g., "08:00 AM"
    val endTime: String,      // e.g., "04:00 PM"
    val isDeleted: Boolean = false,
    val lastModified: Long = System.currentTimeMillis()
)