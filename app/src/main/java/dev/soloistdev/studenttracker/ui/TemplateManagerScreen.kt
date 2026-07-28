package dev.soloistdev.studenttracker.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit // Explicit Edit icon import [1]
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.soloistdev.studenttracker.data.FormTemplateEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateManagerScreen(
    onBack: () -> Unit,
    viewModel: TemplateViewModel = viewModel()
) {
    val templates by viewModel.templates.collectAsState()
    val unconfiguredFields by viewModel.unconfiguredCustomFields.collectAsState()
    val context = LocalContext.current
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    var newFieldName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("TEXT") }
    var tempIsRequired by remember { mutableStateOf(false) }

    var templateToDelete by remember { mutableStateOf<FormTemplateEntity?>(null) }

    // Shows the custom field creation selection page
    var showDiscoveredFieldsScreen by remember { mutableStateOf(false) }

    // Active editing template state manager [1]
    var editingTemplate by remember { mutableStateOf<FormTemplateEntity?>(null) }

    // Dynamic state synchronization on bottom sheet open [1]
    LaunchedEffect(showBottomSheet, editingTemplate) {
        if (showBottomSheet) {
            val template = editingTemplate
            if (template != null) {
                newFieldName = template.fieldName
                selectedType = template.fieldType
                tempIsRequired = template.isRequired
            } else {
                newFieldName = ""
                selectedType = "TEXT"
                tempIsRequired = false
            }
        }
    }

    if (showDiscoveredFieldsScreen) {
        CustomFieldSelectorScreen(
            fields = unconfiguredFields,
            onDismiss = { showDiscoveredFieldsScreen = false },
            onCreateSelected = { selectedFields ->
                viewModel.addTemplatesBulk(selectedFields)
                showDiscoveredFieldsScreen = false
                Toast.makeText(context, "${selectedFields.size} custom fields successfully created!", Toast.LENGTH_SHORT).show()
            }
        )
    } else {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Template Manager", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (unconfiguredFields.isNotEmpty()) {
                            IconButton(onClick = { showDiscoveredFieldsScreen = true }) {
                                BadgedBox(
                                    badge = { Badge { Text(unconfiguredFields.size.toString()) } }
                                ) {
                                    Icon(Icons.Default.Notifications, contentDescription = "Unconfigured Fields Found")
                                }
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        editingTemplate = null // Nullifies to treat sheet as New Creation [1]
                        showBottomSheet = true
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Template")
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                item {
                    Text(
                        text = "Active Custom Fields",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(templates) { template ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = template.fieldName.replace("_", " "),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Type: ${template.fieldType} | Required: ${if (template.isRequired) "Yes" else "No"}",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            // Row Actions Container [1]
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(onClick = {
                                    editingTemplate = template // Directs sheet to populate with values [1]
                                    showBottomSheet = true
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Template",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { templateToDelete = template }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = {
                        showBottomSheet = false
                        editingTemplate = null
                    },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = if (editingTemplate != null) "Edit Custom Field" else "New Custom Field",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedTextField(
                            value = newFieldName,
                            onValueChange = { newFieldName = it },
                            label = { Text("Field Name (Alphanumeric/Underscores)") },
                            placeholder = { Text("e.g., Grade_Level") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        val chipColors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = Color.Transparent,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = selectedType == "TEXT",
                                onClick = { selectedType = "TEXT" },
                                label = { Text("Text") },
                                colors = chipColors
                            )
                            FilterChip(
                                selected = selectedType == "NUMBER",
                                onClick = { selectedType = "NUMBER" },
                                label = { Text("Number") },
                                colors = chipColors
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Required field", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            Switch(
                                checked = tempIsRequired,
                                onCheckedChange = { tempIsRequired = it }
                            )
                        }

                        Button(
                            onClick = {
                                val isEdit = editingTemplate != null
                                val success = viewModel.saveTemplate(
                                    id = editingTemplate?.id ?: 0,
                                    name = newFieldName,
                                    type = selectedType,
                                    isRequired = tempIsRequired
                                )
                                if (success) {
                                    Toast.makeText(context, if (isEdit) "Field Updated Successfully!" else "Field Created Successfully!", Toast.LENGTH_SHORT).show()
                                    newFieldName = ""
                                    tempIsRequired = false
                                    editingTemplate = null
                                    showBottomSheet = false
                                } else {
                                    Toast.makeText(context, "Invalid name. Alphanumeric & underscores only.", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(if (editingTemplate != null) "Update Template" else "Create Template")
                        }
                    }
                }
            }

            templateToDelete?.let { template ->
                AlertDialog(
                    onDismissRequest = { templateToDelete = null },
                    title = { Text("Delete Custom Field?") },
                    text = { Text("Are you sure you want to move the '${template.fieldName.replace("_", " ")}' custom field to the Recycle Bin? It can be restored within 30 days.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteTemplate(template.id)
                                templateToDelete = null
                                Toast.makeText(context, "Field moved to Recycle Bin.", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { templateToDelete = null }) {
                            Text("Cancel")
                        }
                    },
                    shape = RoundedCornerShape(28.dp)
                )
            }
        }
    }
}