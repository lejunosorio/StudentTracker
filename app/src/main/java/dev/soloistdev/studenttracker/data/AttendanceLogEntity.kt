package dev.soloistdev.studenttracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_logs")
data class AttendanceLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val recordId: Int,
    val dateMillis: Long,
    val studentId: Int,
    val status: String = "NOT_SET",
    val lastModified: Long = System.currentTimeMillis() 
)