package dev.soloistdev.studenttracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val firstName: String,
    val lastName: String,
    val middleName: String = "",
    val birthday: Long, // Stored as Unix Timestamp
    val address: String = "",
    val picturePath: String = "", // Local file URI path
    val customDataJson: String = "{}", // To hold custom proctor fields in Sprint 5
    val isDeleted: Boolean = false // Soft delete flag for Sprint 9 Recycle Bin
)