package dev.soloistdev.studenttracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance_logs")
data class AttendanceLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val recordId: Int,       // Links to AttendanceRecordEntity.id
    val dateMillis: Long,     // Midnight epoch millis of the day
    val studentId: Int,      // Links to StudentEntity.id
    val status: String = "NOT_SET" // NOT_SET, PRESENT, ABSENT, EXCUSED, REMOVED
)