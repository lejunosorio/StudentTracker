package dev.soloistdev.studenttracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "assessment_columns")
data class AssessmentColumnEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,                  // e.g. "Math Quiz 1"
    val maxPoints: Double = 100.0,
    val examDate: Long,                // ADDED: Timestamp of the exam
    val checkDate: Long,               // ADDED: Timestamp of checking/evaluation
    val savedFilterId: Int = 0,        // ADDED: Links to SavedFilterEntity.id (0 = All active students)
    val isDeleted: Boolean = false,
    val lastModified: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "assessment_scores",
    foreignKeys = [
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AssessmentColumnEntity::class,
            parentColumns = ["id"],
            childColumns = ["columnId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["studentId"]),
        Index(value = ["columnId"])
    ]
)
data class AssessmentScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val columnId: Int,
    val studentId: Int,
    val score: String,            // Supports both numeric scores ("85") and qualitative scales ("Outstanding")
    val lastModified: Long = System.currentTimeMillis()
)