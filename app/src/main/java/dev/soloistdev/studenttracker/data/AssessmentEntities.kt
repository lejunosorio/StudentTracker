package dev.soloistdev.studenttracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A grading period - quarter, term, semester. Assessments belong to one, so a running grade
 * can be scoped to the period being reported on rather than the whole year.
 */
@Entity(tableName = "grading_terms")
data class GradingTermEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,                  // e.g. "Quarter 1"
    val startDate: Long,
    val endDate: Long,
    val isActive: Boolean = false,     // The period new assessments default into
    val isDeleted: Boolean = false,
    val lastModified: Long = System.currentTimeMillis()
)

/**
 * A weighted bucket of assessments, e.g. "Quizzes 30%", "Exams 50%".
 *
 * [weight] is a percentage. When every category weight is zero the gradebook falls back to a
 * straight total-points average, so a teacher who does not want weighting never has to set one up.
 */
@Entity(tableName = "assessment_categories")
data class AssessmentCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val weight: Double = 0.0,
    val termId: Int = 0,               // 0 = applies across every term
    val isDeleted: Boolean = false,
    val lastModified: Long = System.currentTimeMillis()
)

@Entity(tableName = "assessment_columns")
data class AssessmentColumnEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,                  // e.g. "Math Quiz 1"
    val maxPoints: Double = 100.0,
    val examDate: Long,                // Timestamp of the exam
    val checkDate: Long,               // Timestamp of checking/evaluation
    val savedFilterId: Int = 0,        // Links to SavedFilterEntity.id (0 = All active students)
    val termId: Int = 0,               // Links to GradingTermEntity.id (0 = unassigned)
    val categoryId: Int = 0,           // Links to AssessmentCategoryEntity.id (0 = uncategorised)
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
        Index(value = ["columnId"]),
        // One score per student per assessment. Without this, re-saving a roster silently
        // appended duplicate rows and any average computed over them double-counted.
        Index(value = ["columnId", "studentId"], unique = true)
    ]
)
data class AssessmentScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val columnId: Int,
    val studentId: Int,
    val score: String,            // Supports both numeric scores ("85") and qualitative scales ("Outstanding")
    val lastModified: Long = System.currentTimeMillis()
)
