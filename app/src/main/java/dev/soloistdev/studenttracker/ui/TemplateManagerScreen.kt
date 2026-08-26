package dev.soloistdev.studenttracker.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource // Resolved: Explicit resource accessor import [1]
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.soloistdev.studenttracker.R // Resolved: Explicit R file import [1]
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
    var showDiscoveredFieldsScreen by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<FormTemplateEntity?>(null) }

    // Pre-read system Toast resources inside Composable scope to prevent context resolution stutters [1]
    val fieldCreatedMsg = stringResource(R.string.toast_field_created)
    val fieldUpdatedMsg = stringResource(R.string.toast_field_updated)
    val invalidNameErrorMsg = stringResource(R.string.error_invalid_field_name)

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
                    title = { Text(stringResource(R.string.menu_template_manager), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        if (unconfiguredFields.isNotEmpty()) {
                            IconButton(onClick = { showDiscoveredFieldsScreen = true }) {
                                BadgedBox(
                                    badge = { Badge { Text(unconfiguredFields.size.toString()) } }
                                ) {
                                    Icon(Icons.Default.Notifications, contentDescription = stringResource(R.string.cd_unconfigured_fields_found))
                                }
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        editingTemplate = null
                        showBottomSheet = true
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
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
                        text = stringResource(R.string.active_custom_fields_header),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(templates, key = { it.id }) { template ->
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
                                val requiredLabel = if (template.isRequired) stringResource(R.string.action_yes) else stringResource(R.string.action_no)
                                Text(
                                    text = stringResource(R.string.template_card_desc, template.fieldType, requiredLabel),
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(onClick = {
                                    editingTemplate = template
                                    showBottomSheet = true
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.template_edit_field_title),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { templateToDelete = template }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.action_delete),
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
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()) // Keyboard-safe layout scroll
                            .imePadding(), // Keyboard-safe layout height constraints
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = if (editingTemplate != null) stringResource(R.string.template_edit_field_title) else stringResource(R.string.template_new_field_title),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedTextField(
                            value = newFieldName,
                            onValueChange = { newFieldName = it },
                            label = { Text(stringResource(R.string.template_field_name_label)) },
                            placeholder = { Text(stringResource(R.string.template_field_name_placeholder)) },
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
                                label = { Text(stringResource(R.string.template_type_text)) },
                                colors = chipColors
                            )
                            FilterChip(
                                selected = selectedType == "NUMBER",
                                onClick = { selectedType = "NUMBER" },
                                label = { Text(stringResource(R.string.template_type_number)) },
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
                            Text(stringResource(R.string.template_required_field_label), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
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
                                    Toast.makeText(context, if (isEdit) fieldUpdatedMsg else fieldCreatedMsg, Toast.LENGTH_SHORT).show()
                                    newFieldName = ""
                                    tempIsRequired = false
                                    editingTemplate = null
                                    showBottomSheet = false
                                } else {
                                    Toast.makeText(context, invalidNameErrorMsg, Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(if (editingTemplate != null) stringResource(R.string.template_action_update) else stringResource(R.string.template_action_create))
                        }
                    }
                }
            }

            templateToDelete?.let { template ->
                AlertDialog(
                    onDismissRequest = { templateToDelete = null },
                    title = { Text(stringResource(R.string.template_delete_dialog_title)) },
                    text = { Text(stringResource(R.string.template_delete_dialog_desc, template.fieldName.replace("_", " "))) },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteTemplate(template.id)
                                templateToDelete = null
                                Toast.makeText(context, context.getString(R.string.toast_field_moved_to_recycle_bin), Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.action_delete))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { templateToDelete = null }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    },
                    shape = RoundedCornerShape(28.dp)
                )
            }
        }
    }
}