package dev.soloistdev.studenttracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One cell of an attendance sheet: a student's status on one day of one record.
 *
 * The table grows as days x roster, so a term's worth of a few classes is tens of thousands of
 * rows. Every read is by record, or by record and date, and both were full table scans until
 * these indices existed.
 */
@Entity(
    tableName = "attendance_logs",
    indices = [
        Index(value = ["recordId", "dateMillis"]),
        Index(value = ["studentId"])
    ]
)
data class AttendanceLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val recordId: Int,
    val dateMillis: Long,
    val studentId: Int,
    val status: String = "NOT_SET",
    val lastModified: Long = System.currentTimeMillis() 
)