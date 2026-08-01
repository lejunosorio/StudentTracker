package dev.soloistdev.studenttracker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

class QueryRule(val id: String = UUID.randomUUID().toString()) {
    var field by mutableStateOf("Age")
    var comparison by mutableStateOf("In between")
    var value1 by mutableStateOf("")
    var value2 by mutableStateOf("")
}