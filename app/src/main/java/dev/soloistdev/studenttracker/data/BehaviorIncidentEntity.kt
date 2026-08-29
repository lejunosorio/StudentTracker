package dev.soloistdev.studenttracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "behavior_incidents",
    foreignKeys = [
        ForeignKey(
            entity = StudentEntity::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["studentId"])]
)
data class BehaviorIncidentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studentId: Int,
    val title: String,
    val category: String,       // "Positive", "Negative", or "Neutral"
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val incidentDate: Long = System.currentTimeMillis(),
    val photoPath: String = "",

    /**
     * What was done about it, and when it was closed.
     *
     * The log recorded what happened and nothing else, which answers half the question. At a
     * parent conference or a referral the useful part is what the school did next, and an
     * incident left open is a reminder rather than a note.
     */
    val actionTaken: String = "",

    /** Epoch millis when this was closed off; 0 means still open. */
    val resolvedAt: Long = 0L
) {
    val isResolved: Boolean get() = resolvedAt > 0L

    /** Open concerns worth chasing. Positive notes are records, not tasks. */
    val isOpenConcern: Boolean
        get() = !isResolved && category.equals("Negative", ignoreCase = true)
}
