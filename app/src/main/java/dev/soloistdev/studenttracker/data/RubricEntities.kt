package dev.soloistdev.studenttracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A reusable marking scale, e.g. "Essay rubric" with levels Exemplary/Proficient/Developing.
 *
 * Levels carry points, so a rubric-marked assessment still produces a number the gradebook can
 * average. Standards-based marking and points-based marking therefore coexist without the grade
 * calculation needing to know which was used.
 */
@Entity(tableName = "rubrics")
data class RubricEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isDeleted: Boolean = false,
    val lastModified: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "rubric_levels",
    foreignKeys = [
        ForeignKey(
            entity = RubricEntity::class,
            parentColumns = ["id"],
            childColumns = ["rubricId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["rubricId"])]
)
data class RubricLevelEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val rubricId: Int,
    val label: String,          // e.g. "Proficient"
    val points: Double,         // What this level is worth
    val descriptor: String = "", // Optional "what this looks like" text
    val displayOrder: Int = 0
)

/**
 * Cold-call equity tracking: how often each student has actually been asked to contribute.
 *
 * Teachers reliably under-estimate how skewed their questioning is, so the picker records it
 * rather than trusting recall.
 */
@Entity(
    tableName = "participation_counts",
    indices = [Index(value = ["studentId", "className"], unique = true)]
)
data class ParticipationCountEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studentId: Int,
    val className: String,
    val timesCalled: Int = 0,
    val lastCalledMillis: Long = 0L
)
