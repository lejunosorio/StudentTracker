package dev.soloistdev.studenttracker.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.CsvExportEngine
import dev.soloistdev.studenttracker.data.JsonSyncEngine
import dev.soloistdev.studenttracker.data.ImportResult
import dev.soloistdev.studenttracker.data.LocalSyncEngine
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import org.json.JSONObject
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { StudentRepository(context) }
    val scope = rememberCoroutineScope()

    var showHelpDialog by remember { mutableStateOf(false) }
    var showSampleFormat by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(targetValue = if (showSampleFormat) 180f else 0f)

    // Database Restoration States
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var selectedImportUri by remember { mutableStateOf<Uri?>(null) }

    // Dynamic Loading Dialog States
    var showLoadingPopup by remember { mutableStateOf(false) }
    var loadingStatusText by remember { mutableStateOf("") }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var isImportDone by remember { mutableStateOf(false) }

    // P2P State Managers
    val localSyncEngine = remember { LocalSyncEngine(context) }
    val syncState by localSyncEngine.syncState.collectAsState()
    val discoveredPeers by localSyncEngine.discoveredPeers.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            localSyncEngine.stopActiveSession()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            selectedImportUri = selectedUri
            showImportConfirmDialog = true
        }
    }

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

            Text(
                text = stringResource(R.string.sync_local_p2p_title),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                syncState == "Listening" -> stringResource(R.string.sync_p2p_state_listening)
                                syncState == "Connecting" -> stringResource(R.string.sync_p2p_state_connecting)
                                syncState == "Syncing" -> stringResource(R.string.sync_p2p_state_syncing)
                                syncState == "Success" -> stringResource(R.string.sync_p2p_state_success)
                                syncState.startsWith("Error") -> stringResource(R.string.sync_p2p_state_error, syncState.substringAfter("Error: "))
                                else -> stringResource(R.string.sync_p2p_state_idle)
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (syncState.startsWith("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                localSyncEngine.startLocalServer { tempBackupFile ->
                                    scope.launch {
                                        try {
                                            val result = JsonSyncEngine.importUnencryptedBackup(context, Uri.fromFile(tempBackupFile), repository)
                                            if (result != null) {
                                                importResult = result
                                                isImportDone = true
                                                showLoadingPopup = true
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Payload parsing failed: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            enabled = syncState != "Listening" && syncState != "Scanning" && syncState != "Syncing",
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.sync_p2p_receive_btn), fontSize = 10.sp, textAlign = TextAlign.Center)
                        }

                        Button(
                            onClick = { localSyncEngine.startScanningPeers() },
                            shape = RoundedCornerShape(8.dp),
                            enabled = syncState != "Listening" && syncState != "Scanning" && syncState != "Syncing",
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.sync_p2p_send_btn), fontSize = 10.sp, textAlign = TextAlign.Center)
                        }
                    }

                    if (syncState == "Listening" || syncState == "Scanning") {
                        OutlinedButton(
                            onClick = { localSyncEngine.stopActiveSession() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.sync_p2p_stop_btn), color = MaterialTheme.colorScheme.error)
                        }
                    }

                    if (syncState == "Scanning") {
                        Text(stringResource(R.string.sync_p2p_peers_header), fontSize = 12.sp, fontWeight = FontWeight.Bold)

                        if (discoveredPeers.isEmpty()) {
                            Text(stringResource(R.string.sync_p2p_no_peers), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        } else {
                            discoveredPeers.forEach { peer ->
                                val peerToastSuccess = stringResource(R.string.toast_p2p_transmission_success)
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch(Dispatchers.IO) {
                                                try {
                                                    val activeRoster = repository.getAllActiveStudents()
                                                    val activeLogs = repository.getAllAttendanceLogs()

                                                    val cacheDir = File(context.cacheDir, "backups").apply { mkdirs() }
                                                    val tempFile = File(cacheDir, "temp_p2p_transmit.json")

                                                    val payloadObj = JSONObject().apply {
                                                        val studentsArr = JSONArray()
                                                        activeRoster.forEach { s ->
                                                            studentsArr.put(JSONObject().apply {
                                                                put("id", s.id)
                                                                put("firstName", s.firstName)
                                                                put("lastName", s.lastName)
                                                                put("gender", s.gender)
                                                                put("birthday", s.birthday)
                                                                put("address", s.address)
                                                                put("contactNumber", s.contactNumber)
                                                                put("picturePath", s.picturePath)
                                                                put("guardiansJson", s.guardiansJson)
                                                                put("customDataJson", s.customDataJson)
                                                                put("lastModified", s.lastModified)
                                                                put("classRoom", s.className)
                                                            })
                                                        }
                                                        put("students", studentsArr)

                                                        val logsArr = JSONArray()
                                                        activeLogs.forEach { l ->
                                                            logsArr.put(JSONObject().apply {
                                                                put("recordId", l.recordId)
                                                                put("dateMillis", l.dateMillis)
                                                                put("studentId", l.studentId)
                                                                put("status", l.status)
                                                                put("lastModified", l.lastModified)
                                                            })
                                                        }
                                                        put("attendanceLogs", logsArr)
                                                    }

                                                    FileOutputStream(tempFile).use { fos ->
                                                        fos.write(payloadObj.toString().toByteArray())
                                                        fos.flush()
                                                    }

                                                    localSyncEngine.transmitBackupToPeer(peer, tempFile) { success ->
                                                        if (success) {
                                                            Toast.makeText(context, peerToastSuccess, Toast.LENGTH_SHORT).show()
                                                        }
                                                        tempFile.delete()
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        }
                                        .padding(vertical = 2.dp)
                                ) {
                                    Text(peer.serviceName.replace("StudentTracker_", ""), fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

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

        // CONFIRMATION DIALOG PRIOR TO FULL RESTORATION
        if (showImportConfirmDialog && selectedImportUri != null) {
            val importUri = selectedImportUri!!
            AlertDialog(
                onDismissRequest = { showImportConfirmDialog = false },
                title = { Text("Restore Database?", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to restore your database from this backup file? This will import all Classrooms, Students, Behavior Logs, Attendance Records, and Gradebooks.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showImportConfirmDialog = false
                            loadingStatusText = "Restoring entities..."
                            isImportDone = false
                            showLoadingPopup = true // Reveals dynamic loading dialog

                            scope.launch {
                                val fileName = getFileName(context, importUri)
                                val isEncrypted = fileName?.endsWith(".enc", ignoreCase = true) == true

                                val result = if (isEncrypted) {
                                    JsonSyncEngine.importSecureBackup(context, importUri, repository)
                                } else {
                                    JsonSyncEngine.importUnencryptedBackup(context, importUri, repository)
                                }

                                importResult = result
                                isImportDone = true // Triggers Done button action block
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

        // DYNAMIC DATABASE RESTORATION PROGRESS POPUP
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
                        if (!isImportDone) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(loadingStatusText, fontSize = 14.sp, textAlign = TextAlign.Center)
                        } else {
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
                            } else {
                                Text("Failed to decrypt or parse the backup payload. Verify your encryption key.", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                            }
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

private fun getFileName(context: Context, uri: Uri): String? {
    if (uri.scheme == "file") return uri.lastPathSegment
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx != -1) it.getString(idx) else null
        } else null
    }
}