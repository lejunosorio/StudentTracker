package dev.soloistdev.studenttracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val firstName: String,
    val lastName: String,
    val gender: String,
    val birthday: Long,
    val address: String = "",
    val picturePath: String = "", // Local file URI path
    val guardiansJson: String = "[]", // Serialized JSON array of multiple Guardians
    val customDataJson: String = "{}", // Dynamic templates
    val isDeleted: Boolean = false // Soft delete flag
)