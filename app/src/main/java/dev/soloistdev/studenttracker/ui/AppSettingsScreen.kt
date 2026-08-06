package dev.soloistdev.studenttracker.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.AppDatabase
import dev.soloistdev.studenttracker.data.FormTemplateEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import dev.soloistdev.studenttracker.data.JsonSyncEngine
import dev.soloistdev.studenttracker.data.ImportResult
import dev.soloistdev.studenttracker.data.CsvExportEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    onBack: () -> Unit,
    onNavigateToBiometrics: () -> Unit,
    onNavigateToSync: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { StudentRepository(context) }

    val sharedPrefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }

    var dynamicColorsEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("dynamic_colors", true)) }

    var appTheme by remember { mutableStateOf(sharedPrefs.getString("app_theme", "System") ?: "System") }
    var themeDropdownExpanded by remember { mutableStateOf(false) }

    var activeBadgeField by remember { mutableStateOf(sharedPrefs.getString("card_banner_field", "") ?: "") }
    var availableTemplates by remember { mutableStateOf<List<FormTemplateEntity>>(emptyList()) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    var showPurgeDialog by remember { mutableStateOf(false) }
    var purgeInputText by remember { mutableStateOf("") }

    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var selectedImportUri by remember { mutableStateOf<Uri?>(null) }

    var showLoadingPopup by remember { mutableStateOf(false) }
    var isImportDone by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }

    // Filename prompt states
    var showExportNameDialog by remember { mutableStateOf(false) }
    var exportType by remember { mutableStateOf("") } // "JSON" or "CSV"
    var exportFileNameInput by remember { mutableStateOf("") }

    val m3TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
    )

    val cardBadgeDisabledMsg = stringResource(R.string.settings_card_badge_none)
    val dbCompactSuccessMsg = stringResource(R.string.settings_compact_db_success)

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            selectedImportUri = selectedUri
            showImportConfirmDialog = true
        }
    }

    LaunchedEffect(Unit) {
        availableTemplates = repository.getAllFormTemplates()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.menu_app_settings), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.menu_app_settings))
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
            // Appearance Category
            Text(stringResource(R.string.settings_appearance), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_dynamic_colors), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.settings_dynamic_colors_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Switch(
                            checked = dynamicColorsEnabled,
                            onCheckedChange = { enabled ->
                                dynamicColorsEnabled = enabled
                                sharedPrefs.edit { putBoolean("dynamic_colors", enabled) }
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 3-CHOICE SELECTION DROPDOWN
                    val themeLabel = when (appTheme) {
                        "Light" -> "Light Theme"
                        "Dark" -> "Dark Theme"
                        else -> "System Default"
                    }

                    ExposedDropdownMenuBox(
                        expanded = themeDropdownExpanded,
                        onExpandedChange = { themeDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = themeLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("App Theme Configuration") },
                            colors = m3TextFieldColors,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        )

                        ExposedDropdownMenu(
                            expanded = themeDropdownExpanded,
                            onDismissRequest = { themeDropdownExpanded = false }
                        ) {
                            listOf(
                                "System" to "System Default",
                                "Light" to "Light Theme",
                                "Dark" to "Dark Theme"
                            ).forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        appTheme = value
                                        sharedPrefs.edit { putString("app_theme", value) }
                                        themeDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Security & Privacy Category
            Text("Security & Privacy", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToBiometrics() }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.menu_biometrics_privacy),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Configure master passcode lock & biometric fingerprint settings",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Navigate to Security Settings",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Card Customization Category
            Text(stringResource(R.string.settings_card_customization), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val currentLabel = if (activeBadgeField.isEmpty()) stringResource(R.string.settings_card_badge_none) else activeBadgeField.replace("_", " ")

                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = currentLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.settings_card_badge_label)) },
                            colors = m3TextFieldColors,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        )

                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_card_badge_none)) },
                                onClick = {
                                    activeBadgeField = ""
                                    sharedPrefs.edit { putString("card_banner_field", "") }
                                    dropdownExpanded = false
                                    Toast.makeText(context, cardBadgeDisabledMsg, Toast.LENGTH_SHORT).show()
                                }
                            )

                            availableTemplates.forEach { template ->
                                val userFriendlyLabel = template.fieldName.replace("_", " ")
                                val setBadgeMsg = stringResource(R.string.settings_card_badge_set_toast, userFriendlyLabel)

                                DropdownMenuItem(
                                    text = { Text(userFriendlyLabel) },
                                    onClick = {
                                        activeBadgeField = template.fieldName
                                        sharedPrefs.edit { putString("card_banner_field", template.fieldName) }
                                        dropdownExpanded = false
                                        Toast.makeText(context, setBadgeMsg, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Storage, Backup & Database Category
            Text(stringResource(R.string.settings_storage_database), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    // 1. Sync Data Row (Click to open SyncScreen)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToSync() }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sync Data", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Share databases locally via P2P Wi-Fi", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Icon(Icons.Default.Wifi, contentDescription = "Sync", tint = MaterialTheme.colorScheme.primary)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 2. Export JSON Backup Action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                exportType = "JSON"
                                exportFileNameInput = "student_tracker_backup_" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                                showExportNameDialog = true
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Export Database (JSON)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Saves all student directories, classes, and evaluations as a local JSON file.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Icon(Icons.Default.Save, contentDescription = "Export Backup", tint = MaterialTheme.colorScheme.primary)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 3. Export CSV Spreadsheet Action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                exportType = "CSV"
                                exportFileNameInput = "student_roster_export_" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                                showExportNameDialog = true
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Export CSV Spreadsheet", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Decrypted spreadsheet row formatting compatible with Excel", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Icon(Icons.Default.TableChart, contentDescription = "Export CSV", tint = MaterialTheme.colorScheme.primary)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 4. Import JSON Backup Action
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                filePickerLauncher.launch("application/json") // Restricted strictly to JSON format
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Import Database (JSON)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Restores all student profiles, behavior logs, and records from a JSON backup.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Icon(Icons.Default.Download, contentDescription = "Import Backup", tint = MaterialTheme.colorScheme.primary)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 5. Compact Local Database Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val db = AppDatabase.getDatabase(context)
                                        db.openHelper.writableDatabase.execSQL("VACUUM")
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, dbCompactSuccessMsg, Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_compact_db), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.settings_compact_db_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Icon(Icons.Default.Storage, contentDescription = stringResource(R.string.settings_compact_db), tint = MaterialTheme.colorScheme.primary)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 6. Purge Database Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPurgeDialog = true }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Purge Database", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.error)
                            Text("Permanently erases all students, classes, filters, and sheets", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Icon(Icons.Default.Delete, contentDescription = "Purge Database", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.settings_footer_version),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }

        // DATABASE PURGE DIALOG
        if (showPurgeDialog) {
            AlertDialog(
                onDismissRequest = {
                    showPurgeDialog = false
                    purgeInputText = ""
                },
                title = { Text("Purge Database?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Warning: This action is irreversible. This will permanently delete all student profiles, behavior logs, classrooms, filters, attendance sheets, and gradebook evaluation records. To confirm, type \"delete\" in the box below.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = purgeInputText,
                            onValueChange = { purgeInputText = it },
                            label = { Text("Type \"delete\" to confirm") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPurgeDialog = false
                            purgeInputText = ""
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val db = AppDatabase.getDatabase(context)
                                    db.clearAllTables()
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Database successfully purged!", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        enabled = purgeInputText.trim().lowercase() == "delete",
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Purge All Data")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showPurgeDialog = false
                            purgeInputText = ""
                        }
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

        // DIRECT DATABASE IMPORT CONFIRMATION DIALOG
        if (showImportConfirmDialog && selectedImportUri != null) {
            val importUri = selectedImportUri!!
            AlertDialog(
                onDismissRequest = { showImportConfirmDialog = false },
                title = { Text("Restore Database?", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to restore your database from this JSON backup? This will import all Classrooms, Students, Behavior Logs, Attendance Records, and Gradebooks.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showImportConfirmDialog = false
                            showLoadingPopup = true // Reveals dynamic restoration tracker

                            scope.launch {
                                val result = JsonSyncEngine.importUnencryptedBackup(context, importUri, repository)
                                importResult = result
                                isImportDone = true
                            }
                        }
                    ) {
                        Text(stringResource(R.string.action_yes))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImportConfirmDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

        // DATABASE RESTORATION PROGRESS POPUP
        if (showLoadingPopup) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(if (isImportDone) "Restoration Complete" else "Importing Data", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val res = importResult
                        if (res != null) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                val studentsCountText = res.studentsCount.toString()
                                val savedFiltersCountText = res.filtersCount.toString()
                                val attendanceRecordsCountText = res.attendanceCount.toString()
                                val gradebookCountText = res.gradebookCount.toString()

                                Text("Classrooms Loaded: ${res.classroomsCount}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Students Loaded: $studentsCountText", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Saved Filters Loaded: $savedFiltersCountText", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Attendance Sheets: $attendanceRecordsCountText", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Gradebooks Loaded: $gradebookCountText", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        } else {
                            Text("Failed to parse the backup payload. Verify your JSON schema.", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                        }
                    }
                },
                confirmButton = {
                    if (isImportDone) {
                        Button(
                            onClick = {
                                showLoadingPopup = false
                                importResult = null
                                isImportDone = false
                            }
                        ) {
                            Text("Done")
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

        // DYNAMIC FILENAME PROMPT OVERLAY
        if (showExportNameDialog) {
            AlertDialog(
                onDismissRequest = {
                    showExportNameDialog = false
                    exportFileNameInput = ""
                },
                title = { Text("Export $exportType File", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().imePadding()
                    ) {
                        Text("Provide a custom filename for your exported $exportType backup. The file extension will be appended automatically.", fontSize = 13.sp)
                        OutlinedTextField(
                            value = exportFileNameInput,
                            onValueChange = { input ->
                                // Sanitizes filename inputs to prevent directory traversal injections
                                exportFileNameInput = input.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
                            },
                            label = { Text("Filename") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val finalName = exportFileNameInput.trim().ifEmpty {
                                if (exportType == "JSON") "student_tracker_backup" else "student_roster_export"
                            }
                            showExportNameDialog = false
                            exportFileNameInput = ""

                            scope.launch {
                                if (exportType == "JSON") {
                                    JsonSyncEngine.exportBackupJson(context, repository, finalName)
                                } else {
                                    val list = repository.getAllActiveStudents()
                                    CsvExportEngine.exportRosterToCsv(context, list, finalName)
                                }
                            }
                        }
                    ) {
                        Text("Export")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showExportNameDialog = false
                            exportFileNameInput = ""
                        }
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}