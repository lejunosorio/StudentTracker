package dev.soloistdev.studenttracker.ui

import android.net.Uri
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.data.CsvExportEngine
import dev.soloistdev.studenttracker.data.FormTemplateEntity
import dev.soloistdev.studenttracker.data.JsonSyncEngine
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { StudentRepository(context) }
    val scope = rememberCoroutineScope()

    var showHelpDialog by remember { mutableStateOf(false) }
    var showSampleFormat by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(targetValue = if (showSampleFormat) 180f else 0f)

    // Interactive Wizard States [1]
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
                    // Safe in-memory parse [1]
                    val result = JsonSyncEngine.parseBackup(context, selectedUri)
                    tempStudents = result.first
                    val discoveredKeys = result.second

                    // Cross-reference unconfigured custom fields
                    val currentTemplates = repository.getAllFormTemplates().map { it.fieldName }.toSet()
                    tempDiscoveredFields = discoveredKeys.filter { !currentTemplates.contains(it) }

                    // Trigger Stage 1 Confirmation [1]
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
                    // Bulk create selected custom fields
                    selectedFields.forEach { fieldName ->
                        repository.insertFormTemplate(
                            FormTemplateEntity(
                                fieldName = fieldName,
                                fieldType = "TEXT",
                                isRequired = false
                            )
                        )
                    }
                    // Insert students
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
                    title = { Text("Backup & Sync", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showHelpDialog = true }) {
                            Icon(Icons.Default.HelpOutline, contentDescription = "Help")
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
                Text(
                    text = "AES-GCM Password Encryption",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = "••••••••••••",
                    onValueChange = {},
                    label = { Text("Export Password") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )

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
                            Text("Export Encrypted Backup", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Outputs secure .enc JSON payload", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
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
                            Icon(Icons.Default.Lock, contentDescription = "Export JSON", tint = MaterialTheme.colorScheme.onPrimaryContainer)
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
                            Text("Export CSV Spreadsheet", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Decrypted rows for spreadsheets", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
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
                            Icon(Icons.Default.TableChart, contentDescription = "Export CSV", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }

                Text(
                    text = "Database Restoration",
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
                        Icon(Icons.Default.Download, contentDescription = "Import", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import Backup or JSON File", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                                Text("View Sample JSON Format", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("See the correct schema structure for imports", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
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
                                "firstName": "Troy",
                                "lastName": "Ordinario",
                                "gender": "M",
                                "birthday": 1378598400000,
                                "address": "Street Address, Brgy. 3, Taguig",
                                "guardiansJson": [
                                  {
                                    "name": "Jane Doe",
                                    "relationship": "Mother",
                                    "phones": ["555-0198"]
                                  }
                                ],
                                "customDataJson": "{\"Purok\": \"3\", \"Status\": \"Mang-aawit\", \"Bautisado\": \"\"}"
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
                    title = { Text("Accept Records Import") },
                    text = { Text("Do you accept importing ${tempStudents.size} records?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showImportConfirmDialog = false
                                if (tempDiscoveredFields.isNotEmpty()) {
                                    // Trigger Stage 2 Dialog [1]
                                    showCustomFieldPromptDialog = true
                                } else {
                                    scope.launch {
                                        tempStudents.forEach { repository.insertStudent(it) }
                                        Toast.makeText(context, "${tempStudents.size} records imported successfully!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Text("Yes")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showImportConfirmDialog = false }) {
                            Text("No")
                        }
                    },
                    shape = RoundedCornerShape(28.dp)
                )
            }

            // STAGE 2: Custom Field Discovery Dialog [1]
            if (showCustomFieldPromptDialog) {
                AlertDialog(
                    onDismissRequest = { showCustomFieldPromptDialog = false },
                    title = { Text("Discovered Fields") },
                    text = { Text("Custom Fields found in the JSON. Create them?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showCustomFieldPromptDialog = false
                                // Trigger Stage 3 Screen [1]
                                showCustomFieldSelectorScreen = true
                            }
                        ) {
                            Text("Yes")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showCustomFieldPromptDialog = false
                                scope.launch {
                                    // Proceed import without registering templates
                                    tempStudents.forEach { repository.insertStudent(it) }
                                    Toast.makeText(context, "${tempStudents.size} records imported successfully!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("No")
                        }
                    },
                    shape = RoundedCornerShape(28.dp)
                )
            }

            if (showHelpDialog) {
                AlertDialog(
                    onDismissRequest = { showHelpDialog = false },
                    title = { Text("Backup & Sync Guide", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Secure Backups (.enc)",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Backups exported with the lock icon use military-grade AES-GCM encryption. These are tied to your current Master recovery PIN. If you reset or forget this PIN, old backup files cannot be decrypted.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )

                            Text(
                                text = "Spreadsheets & Plain JSON (.csv / .json)",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "CSV and standard JSON imports are unencrypted. You can open them on external computers using spreadsheet software. Use these when migrating rosters across different devices without cryptographic keys.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )

                            Text(
                                text = "Offline Security Warning",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "This application operates 100% offline. There are no cloud servers. Keep your master passcodes safe as they are the only keys that can open your data.",
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
                            Text("Got it")
                        }
                    },
                    shape = RoundedCornerShape(28.dp)
                )
            }
        }
    }
}