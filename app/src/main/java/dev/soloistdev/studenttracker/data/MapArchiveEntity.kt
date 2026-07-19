package dev.soloistdev.studenttracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "map_archives")
data class MapArchiveEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val filePath: String, // Absolute local private file path
    val isActive: Boolean = false // If true, OSMDroid will read map tiles from this file
)