package dev.soloistdev.studenttracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_records")
data class AttendanceRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val savedFilterId: Int, // Links to SavedFilterEntity.id
    val startDate: Long,    // Epoch millis
    val endDate: Long       // Epoch millis
)