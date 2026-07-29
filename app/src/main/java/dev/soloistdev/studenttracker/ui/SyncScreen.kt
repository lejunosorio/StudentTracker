package dev.soloistdev.studenttracker.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.CsvExportEngine
import dev.soloistdev.studenttracker.data.FormTemplateEntity
import dev.soloistdev.studenttracker.data.JsonSyncEngine
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { StudentRepository(context) }
    val scope = rememberCoroutineScope()

    var showHelpDialog by remember { mutableStateOf(false) }
    var showSampleFormat by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(targetValue = if (showSampleFormat) 180f else 0f)

    // Interactive Wizard States
    var tempStudents by remember { mutableStateOf<List<StudentEntity>>(emptyList()) }
    var tempDiscoveredFields by remember { mutableStateOf<List<String>>(emptyList()) }

    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var showCustomFieldPromptDialog by remember { mutableStateOf(false) }
    var showCustomFieldSelectorScreen by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            scope.launch {
                try {
                    val result = JsonSyncEngine.parseBackup(context, selectedUri)
                    tempStudents = result.first
                    val discoveredKeys = result.second

                    val currentTemplates = repository.getAllFormTemplates().map { it.fieldName }.toSet()
                    tempDiscoveredFields = discoveredKeys.filter { !currentTemplates.contains(it) }

                    showImportConfirmDialog = true
                } catch (e: Exception) {
                    Toast.makeText(context, "Parsing error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (showCustomFieldSelectorScreen) {
        CustomFieldSelectorScreen(
            fields = tempDiscoveredFields,
            onDismiss = { showCustomFieldSelectorScreen = false },
            onCreateSelected = { selectedFields ->
                scope.launch {
                    selectedFields.forEach { fieldName ->
                        repository.insertFormTemplate(
                            FormTemplateEntity(
                                fieldName = fieldName,
                                fieldType = "TEXT",
                                isRequired = false
                            )
                        )
                    }
                    tempStudents.forEach { repository.insertStudent(it) }
                    showCustomFieldSelectorScreen = false
                    Toast.makeText(context, "${tempStudents.size} records imported successfully!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    } else {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.menu_backup_sync), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showHelpDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = stringResource(R.string.sync_help_guide_title))
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Resolved: Dummy cryptographic password fields completely removed from screen layout [1]

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.sync_export_backup_title), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.sync_export_backup_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val list = repository.getAllActiveStudents()
                                    JsonSyncEngine.exportSecureBackup(context, list)
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.sync_export_backup_title), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.sync_export_csv_title), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.sync_export_csv_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val list = repository.getAllActiveStudents()
                                    CsvExportEngine.exportRosterToCsv(context, list)
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = stringResource(R.string.sync_export_csv_title), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.sync_database_restoration_title),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { filePickerLauncher.launch("*/*") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Download, contentDescription = stringResource(R.string.sync_import_backup_label), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.sync_import_backup_label), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSampleFormat = !showSampleFormat },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.sync_view_sample_json), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.sync_view_sample_json_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Toggle Sample Format",
                                modifier = Modifier.rotate(rotationAngle)
                            )
                        }

                        if (showSampleFormat) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(12.dp))

                            val sampleJson = """
                            [
                              {
                                "firstName": "John",
                                "lastName": "Doe",
                                "gender": "M",
                                "birthday": 1378598400000,
                                "address": "123 Main Street, City",
                                "guardiansJson": [
                                  {
                                    "name": "Jane Doe",
                                    "relationship": "Mother",
                                    "phones": ["555-0198"]
                                  }
                                ],
                                "customDataJson": "{\"Field_1\": \"Value_1\", \"Field_2\": \"Value_2\"}"
                              }
                            ]
                            """.trimIndent()

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = sampleJson,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .horizontalScroll(rememberScrollState())
                                )
                            }
                        }
                    }
                }
            }

            // STAGE 1: Accept Records Dialog [1]
            if (showImportConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showImportConfirmDialog = false },
                    title = { Text(stringResource(R.string.sync_dialog_accept_import_title)) },
                    text = { Text(stringResource(R.string.sync_dialog_accept_import_desc, tempStudents.size)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                showImportConfirmDialog = false
                                if (tempDiscoveredFields.isNotEmpty()) {
                                    showCustomFieldPromptDialog = true
                                } else {
                                    scope.launch {
                                        tempStudents.forEach { repository.insertStudent(it) }
                                        Toast.makeText(context, "${tempStudents.size} records imported successfully!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Text(stringResource(R.string.action_yes))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showImportConfirmDialog = false }) {
                            Text(stringResource(R.string.action_no))
                        }
                    },
                    shape = RoundedCornerShape(28.dp)
                )
            }

            // STAGE 2: Custom Field Discovery Dialog [1]
            if (showCustomFieldPromptDialog) {
                AlertDialog(
                    onDismissRequest = { showCustomFieldPromptDialog = false },
                    title = { Text(stringResource(R.string.sync_dialog_discovered_fields_title)) },
                    text = { Text(stringResource(R.string.sync_dialog_discovered_fields_desc)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                showCustomFieldPromptDialog = false
                                showCustomFieldSelectorScreen = true
                            }
                        ) {
                            Text(stringResource(R.string.action_yes))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showCustomFieldPromptDialog = false
                                scope.launch {
                                    tempStudents.forEach { repository.insertStudent(it) }
                                    Toast.makeText(context, "${tempStudents.size} records imported successfully!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text(stringResource(R.string.action_no))
                        }
                    },
                    shape = RoundedCornerShape(28.dp)
                )
            }

            if (showHelpDialog) {
                AlertDialog(
                    onDismissRequest = { showHelpDialog = false },
                    title = { Text(stringResource(R.string.sync_help_guide_title), fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.sync_help_secure_backups_title),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = stringResource(R.string.sync_help_secure_backups_desc),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )

                            Text(
                                text = stringResource(R.string.sync_help_plain_files_title),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = stringResource(R.string.sync_help_plain_files_desc),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )

                            Text(
                                text = stringResource(R.string.sync_help_security_warning_title),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 14.sp
                            )
                            Text(
                                text = stringResource(R.string.sync_help_security_warning_desc),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { showHelpDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(stringResource(R.string.sync_help_button_dismiss))
                        }
                    },
                    shape = RoundedCornerShape(28.dp)
                )
            }
        }
    }
}