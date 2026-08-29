package dev.soloistdev.studenttracker.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.text.input.KeyboardType
import dev.soloistdev.studenttracker.data.BackupScheduler
import dev.soloistdev.studenttracker.data.BackupWorkScheduler
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.CsvExportEngine
import dev.soloistdev.studenttracker.data.ImportResult
import dev.soloistdev.studenttracker.data.JsonSyncEngine
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything about getting data out of the app and back in again.
 *
 * The rolling snapshots, the manual exports and the restore path all answer "what happens if this
 * phone dies", so they belong on one screen rather than scattered down a list with theming.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { StudentRepository(context) }

    // Bumped after any backup action so the snapshot list re-reads from disk
    var backupRefreshKey by remember { mutableIntStateOf(0) }
    var pendingRestoreFile by remember { mutableStateOf<java.io.File?>(null) }

    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var selectedImportUri by remember { mutableStateOf<Uri?>(null) }

    var showLoadingPopup by remember { mutableStateOf(false) }
    var isImportDone by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }

    var showExportNameDialog by remember { mutableStateOf(false) }
    var exportType by remember { mutableStateOf("") } // "JSON", "ENC" or "CSV"
    var exportFileNameInput by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            selectedImportUri = selectedUri
            showImportConfirmDialog = true
        }
    }

    fun promptExport(type: String) {
        exportType = type
        val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        exportFileNameInput =
            if (type == "CSV") "student_roster_export_$stamp" else "student_tracker_backup_$stamp"
        showExportNameDialog = true
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_cat_backup), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_cat_backup_auto),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            AutoBackupCard(
                onRestore = { file -> pendingRestoreFile = file },
                refreshKey = backupRefreshKey,
                onChanged = { backupRefreshKey++ }
            )

            SettingsSection(stringResource(R.string.settings_cat_backup_export)) {
                SettingsActionRow(
                    title = stringResource(R.string.settings_export_json),
                    subtitle = stringResource(R.string.settings_export_json_desc),
                    icon = Icons.Default.Save,
                    onClick = { promptExport("JSON") }
                )

                SettingsDivider()

                // Encrypted export. The importer for this format already existed; without a way to
                // produce a .enc file the round trip was broken in one direction.
                SettingsActionRow(
                    title = stringResource(R.string.sync_export_backup_title),
                    subtitle = stringResource(R.string.settings_export_enc_desc),
                    icon = Icons.Default.Security,
                    onClick = { promptExport("ENC") }
                )

                SettingsDivider()

                SettingsActionRow(
                    title = stringResource(R.string.settings_export_csv),
                    subtitle = stringResource(R.string.settings_export_csv_desc),
                    icon = Icons.Default.TableChart,
                    onClick = { promptExport("CSV") }
                )
            }

            Text(
                text = stringResource(R.string.photos_device_local),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            SettingsSection(stringResource(R.string.settings_cat_backup_restore)) {
                SettingsActionRow(
                    title = stringResource(R.string.settings_import_json),
                    subtitle = stringResource(R.string.settings_import_json_desc),
                    icon = Icons.Default.Download,
                    // Restricted strictly to JSON format
                    onClick = { filePickerLauncher.launch("application/json") }
                )
            }
        }

        // DIRECT DATABASE IMPORT CONFIRMATION DIALOG
        if (showImportConfirmDialog && selectedImportUri != null) {
            val importUri = selectedImportUri!!
            AlertDialog(
                onDismissRequest = { showImportConfirmDialog = false },
                title = { Text(stringResource(R.string.settings_restore_db_title), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.s_are_you_sure_you_want_to_restore_your_data)) },
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
                title = {
                    Text(
                        text = if (isImportDone) stringResource(R.string.settings_restore_done)
                        else stringResource(R.string.settings_restore_running),
                        fontWeight = FontWeight.Bold
                    )
                },
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
                                Text("Classrooms Loaded: ${res.classroomsCount}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Students Loaded: ${res.studentsCount}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Saved Filters Loaded: ${res.filtersCount}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Attendance Sheets: ${res.attendanceCount}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Gradebooks Loaded: ${res.gradebookCount}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        } else if (isImportDone) {
                            Text(
                                text = stringResource(R.string.settings_restore_failed),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 14.sp
                            )
                        } else {
                            CircularProgressIndicator()
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
                                backupRefreshKey++
                            }
                        ) {
                            Text(stringResource(R.string.s_done))
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
                title = { Text(stringResource(R.string.settings_export_title, exportType), fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().imePadding()
                    ) {
                        Text(stringResource(R.string.settings_export_body, exportType), fontSize = 13.sp)
                        OutlinedTextField(
                            value = exportFileNameInput,
                            onValueChange = { input ->
                                // Sanitizes filename inputs to prevent directory traversal injections
                                exportFileNameInput = input.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
                            },
                            label = { Text(stringResource(R.string.s_filename)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val finalName = exportFileNameInput.trim().ifEmpty {
                                if (exportType == "CSV") "student_roster_export" else "student_tracker_backup"
                            }
                            showExportNameDialog = false
                            exportFileNameInput = ""

                            scope.launch {
                                when (exportType) {
                                    "JSON" -> JsonSyncEngine.exportBackupJson(context, repository, finalName)
                                    "ENC" -> {
                                        val ok = JsonSyncEngine.exportEncryptedBackup(context, repository, finalName)
                                        if (!ok) {
                                            Toast.makeText(context, R.string.settings_export_enc_failed, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    else -> {
                                        val list = repository.getAllActiveStudents()
                                        CsvExportEngine.exportRosterToCsv(context, list, finalName)
                                    }
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.s_export))
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

        pendingRestoreFile?.let { file ->
            AlertDialog(
                onDismissRequest = { pendingRestoreFile = null },
                title = { Text(stringResource(R.string.settings_restore_snapshot_title), fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = stringResource(
                            R.string.settings_restore_snapshot_body,
                            dev.soloistdev.studenttracker.data.BackupScheduler.describe(file)
                        ),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        val target = file
                        pendingRestoreFile = null
                        showLoadingPopup = true
                        isImportDone = false
                        scope.launch {
                            // Snapshots are sealed on disk; importLocalBackup unwraps whichever
                            // form this one was written in.
                            val result = JsonSyncEngine.importLocalBackup(context, target, repository)
                            importResult = result
                            isImportDone = true
                            backupRefreshKey++
                        }
                    }) {
                        Text(stringResource(R.string.s_restore))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingRestoreFile = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}

@Composable
internal fun AutoBackupCard(
    onRestore: (java.io.File) -> Unit,
    refreshKey: Int,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { StudentRepository(context) }

    var enabled by remember { mutableStateOf(BackupScheduler.isEnabled(context)) }
    var retention by remember { mutableIntStateOf(BackupScheduler.retention(context)) }
    var intervalHours by remember { mutableIntStateOf(BackupScheduler.intervalHours(context)) }
    var isRunning by remember { mutableStateOf(false) }

    val backups = remember(refreshKey) { BackupScheduler.listBackups(context) }
    val lastRun = remember(refreshKey) { BackupScheduler.lastRunMillis(context) }

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
                    Text("Automatic backups", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (lastRun > 0L) {
                            "Last run ${SimpleDateFormat("MMM dd 'at' HH:mm", Locale.US).format(Date(lastRun))}"
                        } else {
                            "Never run yet"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        BackupScheduler.setEnabled(context, it)
                        BackupWorkScheduler.sync(context)
                    }
                )
            }

            Text(
                text = "A snapshot is written when the app goes to the background, and on a schedule every $intervalHours hours even with the app closed. The newest $retention are kept.",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = intervalHours.toString(),
                    onValueChange = { input ->
                        val v = input.filter { it.isDigit() }.toIntOrNull() ?: 1
                        intervalHours = v.coerceIn(1, 168)
                        BackupScheduler.setIntervalHours(context, intervalHours)
                        // The periodic work carries its own copy of the interval, so it has to be
                        // re-declared or the change would not take effect until the next launch.
                        BackupWorkScheduler.sync(context)
                    },
                    label = { Text("Every (hrs)", fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = retention.toString(),
                    onValueChange = { input ->
                        val v = input.filter { it.isDigit() }.toIntOrNull() ?: 1
                        retention = v.coerceIn(1, 30)
                        BackupScheduler.setRetention(context, retention)
                    },
                    label = { Text("Keep", fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = {
                    isRunning = true
                    scope.launch {
                        val file = BackupScheduler.runBackup(context, repository)
                        isRunning = false
                        onChanged()
                        Toast.makeText(
                            context,
                            if (file != null) "Backup saved" else "Nothing to back up",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.s_back_up_now))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (backups.isEmpty()) {
                Text(
                    text = "No snapshots yet.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            } else {
                backups.forEach { file ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = BackupScheduler.describe(file),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(onClick = { scope.launch { BackupScheduler.shareBackup(context, file) } }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.cd_share), modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { onRestore(file) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Restore, contentDescription = stringResource(R.string.cd_restore), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Text(
                    text = "Snapshots are encrypted to this device and are removed if the app is uninstalled. Sharing one exports a readable copy — keep it somewhere safe.",
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
