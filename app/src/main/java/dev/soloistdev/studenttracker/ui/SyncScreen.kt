package dev.soloistdev.studenttracker.ui

import android.content.Context
import android.net.Uri
import android.net.nsd.NsdServiceInfo
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.AttendanceRecordEntity
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

    // Dynamic Loading Dialog States for incoming P2P transfers
    var showLoadingPopup by remember { mutableStateOf(false) }
    var isImportDone by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }

    // P2P State Managers
    val localSyncEngine = remember { LocalSyncEngine(context) }
    val syncState by localSyncEngine.syncState.collectAsState()
    val discoveredPeers by localSyncEngine.discoveredPeers.collectAsState()
    val pairingCode by localSyncEngine.pairingCode.collectAsState()

    // Peer selected for transmission, held until the operator supplies its pairing code
    var peerAwaitingCode by remember { mutableStateOf<NsdServiceInfo?>(null) }
    var enteredCode by remember { mutableStateOf("") }

    val peerToastSuccess = stringResource(R.string.toast_p2p_transmission_success)

    // When armed, the next transmission is a scoped substitute packet rather than the full roster
    var handoffClass by remember { mutableStateOf<String?>(null) }
    var handoffRecord by remember { mutableStateOf<AttendanceRecordEntity?>(null) }
    var handoffDate by remember { mutableLongStateOf(0L) }
    var showHandoffPicker by remember { mutableStateOf(false) }

    // Serializes the active roster and hands it to the engine, which seals it under [code]
    fun sendRosterToPeer(peer: NsdServiceInfo, code: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val cacheDir = File(context.cacheDir, "backups").apply { mkdirs() }
                val tempFile = File(cacheDir, "temp_p2p_transmit.json")

                val armedClass = handoffClass
                val armedRecord = handoffRecord
                if (armedClass != null && armedRecord != null) {
                    val packet = JsonSyncEngine.buildSubstitutePacket(
                        repository, armedClass, armedRecord, handoffDate
                    )
                    FileOutputStream(tempFile).use { fos ->
                        fos.write(packet.toString().toByteArray())
                        fos.flush()
                    }
                    localSyncEngine.transmitBackupToPeer(peer, tempFile, code) { success ->
                        if (success) {
                            Toast.makeText(context, peerToastSuccess, Toast.LENGTH_SHORT).show()
                        }
                        tempFile.delete()
                    }
                    return@launch
                }

                val activeRoster = repository.getAllActiveStudents()
                val activeLogs = repository.getAllAttendanceLogs()

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
                            put("classRoom", s.getClassNamesList().firstOrNull() ?: "")
                            put("classNamesJson", JSONArray(s.classNamesJson))
                            put("seatingJson", JSONObject(s.seatingJson))
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

                localSyncEngine.transmitBackupToPeer(peer, tempFile, code) { success ->
                    if (success) {
                        Toast.makeText(context, peerToastSuccess, Toast.LENGTH_SHORT).show()
                    }
                    // The plaintext roster never outlives the transfer
                    tempFile.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            localSyncEngine.stopActiveSession()
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


                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    if (handoffClass != null && handoffRecord != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Substitute packet armed",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "$handoffClass  •  ${handoffRecord!!.name}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "Sends names, class and seats only. No addresses, guardians, contact numbers, grades or behaviour notes.",
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                )
                                TextButton(onClick = {
                                    handoffClass = null
                                    handoffRecord = null
                                }) {
                                    Text("Send full database instead", fontSize = 11.sp)
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showHandoffPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Prepare substitute handoff", fontSize = 11.sp, textAlign = TextAlign.Center)
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

                    val activeCode = pairingCode
                    if (syncState == "Listening" && activeCode != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.sync_p2p_pairing_code_label),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = activeCode,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = stringResource(R.string.sync_p2p_pairing_code_hint),
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    if (syncState == "Scanning") {
                        Text(stringResource(R.string.sync_p2p_peers_header), fontSize = 12.sp, fontWeight = FontWeight.Bold)

                        if (discoveredPeers.isEmpty()) {
                            Text(stringResource(R.string.sync_p2p_no_peers), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        } else {
                            discoveredPeers.forEach { peer ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            enteredCode = ""
                                            peerAwaitingCode = peer
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
                                Text("Classrooms Loaded: ${res.classroomsCount}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Students Loaded: ${res.studentsCount}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Saved Filters Loaded: ${res.filtersCount}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Attendance Sheets: ${res.attendanceCount}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Gradebooks Loaded: ${res.gradebookCount}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
                            Text(stringResource(R.string.s_done))
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

        val targetPeer = peerAwaitingCode
        if (targetPeer != null) {
            AlertDialog(
                onDismissRequest = { peerAwaitingCode = null },
                title = { Text(stringResource(R.string.sync_p2p_enter_code_title), fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.sync_p2p_enter_code_desc),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                        OutlinedTextField(
                            value = enteredCode,
                            onValueChange = { input -> enteredCode = input.filter { it.isDigit() }.take(6) },
                            label = { Text(stringResource(R.string.sync_p2p_code_field_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        enabled = enteredCode.length == 6,
                        onClick = {
                            sendRosterToPeer(targetPeer, enteredCode)
                            peerAwaitingCode = null
                        }
                    ) {
                        Text(stringResource(R.string.sync_p2p_code_send_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { peerAwaitingCode = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }


        if (showHandoffPicker) {
            SubstituteHandoffPicker(
                repository = repository,
                onArm = { className, record, date ->
                    handoffClass = className
                    handoffRecord = record
                    handoffDate = date
                    showHandoffPicker = false
                },
                onDismiss = { showHandoffPicker = false }
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
                            text = "Secure P2P Synchronization",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "This screen allows you to synchronize database rosters wirelessly with another device on the same local network.",
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
/**
 * Chooses which class and which day are handed to the substitute.
 *
 * Both are required: a packet without a day would give the substitute a roster and nowhere to
 * record it, and the return trip needs the same sheet to merge back into.
 */
@Composable
private fun SubstituteHandoffPicker(
    repository: StudentRepository,
    onArm: (String, AttendanceRecordEntity, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var classes by remember { mutableStateOf<List<String>>(emptyList()) }
    var records by remember { mutableStateOf<List<AttendanceRecordEntity>>(emptyList()) }
    var chosenClass by remember { mutableStateOf<String?>(null) }
    var chosenRecord by remember { mutableStateOf<AttendanceRecordEntity?>(null) }

    val sdf = remember { java.text.SimpleDateFormat("EEE, MMM dd", java.util.Locale.US) }

    LaunchedEffect(Unit) {
        classes = repository.getAllActiveStudents()
            .flatMap { it.getClassNamesList() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        records = repository.getAllAttendanceRecords()
    }

    val title = when {
        chosenClass == null -> "Choose Class"
        chosenRecord == null -> "Choose Attendance Sheet"
        else -> "Choose Day"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when {
                    chosenClass == null -> classes.forEach { name ->
                        HandoffOption(name) { chosenClass = name }
                    }
                    chosenRecord == null -> {
                        if (records.isEmpty()) {
                            Text("No attendance sheets exist yet.", fontSize = 13.sp)
                        }
                        records.forEach { record ->
                            HandoffOption("${record.name}  •  ${sdf.format(java.util.Date(record.startDate))}") {
                                chosenRecord = record
                            }
                        }
                    }
                    else -> generateDateList(chosenRecord!!.startDate, chosenRecord!!.endDate).forEach { day ->
                        HandoffOption(sdf.format(java.util.Date(day))) {
                            onArm(chosenClass!!, chosenRecord!!, day)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (chosenClass != null) {
                TextButton(onClick = {
                    if (chosenRecord != null) chosenRecord = null else chosenClass = null
                }) { Text(stringResource(R.string.s_back)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
private fun HandoffOption(label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(label, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
    }
}
