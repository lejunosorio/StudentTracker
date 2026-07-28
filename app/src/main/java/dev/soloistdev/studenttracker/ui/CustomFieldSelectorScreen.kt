package dev.soloistdev.studenttracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFieldSelectorScreen(
    fields: List<String>,
    onDismiss: () -> Unit,
    onCreateSelected: (List<String>) -> Unit
) {
    var selectedFields by remember { mutableStateOf(emptySet<String>()) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val allSelected = remember(selectedFields, fields) {
        fields.isNotEmpty() && selectedFields.size == fields.size
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Create Discovered Fields", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        if (selectedFields.isNotEmpty()) {
                            showConfirmDialog = true
                        }
                    },
                    enabled = selectedFields.isNotEmpty(),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(48.dp)
                ) {
                    Text("Create Selected (${selectedFields.size})")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Select All Header Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedFields = if (allSelected) {
                                emptySet()
                            } else {
                                fields.toSet()
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Select All Discovered Fields", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Checkbox(
                        checked = allSelected,
                        onCheckedChange = { checked ->
                            selectedFields = if (checked) {
                                fields.toSet()
                            } else {
                                emptySet()
                            }
                        }
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(fields) { field ->
                    val isSelected = selectedFields.contains(field)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedFields = if (isSelected) {
                                    selectedFields - field
                                } else {
                                    selectedFields + field
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(field.replace("_", " "), fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedFields = if (checked) {
                                        selectedFields + field
                                    } else {
                                        selectedFields - field
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showConfirmDialog) {
            val selectedCount = selectedFields.size
            val unselectedCount = fields.size - selectedCount

            val dialogText = if (unselectedCount == 0) {
                "$selectedCount custom fields selected. Proceed on creation?"
            } else {
                "$selectedCount custom fields selected, and $unselectedCount not selected. Proceed on creation?"
            }

            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text("Confirm Creation") },
                text = { Text(dialogText) },
                confirmButton = {
                    Button(
                        onClick = {
                            showConfirmDialog = false
                            onCreateSelected(selectedFields.toList())
                        }
                    ) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text("No")
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}