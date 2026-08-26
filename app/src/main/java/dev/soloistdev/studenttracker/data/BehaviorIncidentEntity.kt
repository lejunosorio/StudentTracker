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
    val photoPath: String = ""
)