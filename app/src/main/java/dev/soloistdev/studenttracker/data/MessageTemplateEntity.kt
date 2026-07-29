package dev.soloistdev.studenttracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "message_templates")
data class MessageTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val text: String,
    val lastModified: Long = System.currentTimeMillis()
)