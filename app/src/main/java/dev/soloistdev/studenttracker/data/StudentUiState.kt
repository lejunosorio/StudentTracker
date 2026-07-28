package dev.soloistdev.studenttracker.ui

import dev.soloistdev.studenttracker.data.StudentEntity

data class StudentUiState(
    val student: StudentEntity,
    val genderString: String,
    val formattedBirthday: String,
    val age: Int,
    val customBadgeValue: String?
)