package dev.soloistdev.studenttracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "assessment_columns")
data class AssessmentColumnEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,             // e.g., "Quiz 1", "Midterm"
    val maxPoints: Double = 100.0,
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
    val score: String,            // Stored as String to support both numeric scores ("85") and qualitative scales ("Satisfactory")
    val lastModified: Long = System.currentTimeMillis()
)