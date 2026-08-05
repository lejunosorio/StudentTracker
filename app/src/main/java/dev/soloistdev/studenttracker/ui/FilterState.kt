package dev.soloistdev.studenttracker.ui

import java.util.UUID

data class FilterState(
    val id: String = UUID.randomUUID().toString(),
    val field: String = "",
    val comparison: String = "contains",
    val value1: String = "",
    val value2: String = "",
    val isPinned: Boolean = false
)