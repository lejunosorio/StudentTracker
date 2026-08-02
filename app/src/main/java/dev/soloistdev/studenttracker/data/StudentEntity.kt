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
    val contactNumber: String = "",
    val picturePath: String = "",
    val guardiansJson: String = "[]",
    val customDataJson: String = "{}",
    val isDeleted: Boolean = false,
    val lastModified: Long = System.currentTimeMillis(),
    val className: String = "",

    // ADDED: Coordinates serialized as normalized floats (0f..1f)
    val seatingX: Float = -1f,
    val seatingY: Float = -1f
)