package dev.soloistdev.studenttracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.AttendanceLogEntity
import dev.soloistdev.studenttracker.data.AttendanceRecordEntity
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.launch

/**
 * Takes the roll by scanning printed student QR badges.
 *
 * For a class of forty this is the difference between a minute and ten. Badges are the ones the
 * profile screen already generates, so no new artefact is introduced: the payload is the same
 * studenttracker:// link, and a scan resolves by id first and by name plus birthday second, so a
 * badge printed on another device still works.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanAttendanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { StudentRepository(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var students by remember { mutableStateOf<List<StudentEntity>>(emptyList()) }
    var records by remember { mutableStateOf<List<AttendanceRecordEntity>>(emptyList()) }
    var activeRecord by remember { mutableStateOf<AttendanceRecordEntity?>(null) }
    var activeDate by remember { mutableLongStateOf(0L) }
    var showPicker by remember { mutableStateOf(true) }

    val scanned = remember { mutableStateListOf<Pair<String, Boolean>>() }
    val markedIds = remember { mutableStateListOf<Int>() }
    var lastPayload by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        students = repository.getAllActiveStudents()
        records = repository.getAllAttendanceRecords()
    }

    fun resolveStudent(payload: String): StudentEntity? {
        return try {
            val uri = Uri.parse(payload)
            if (uri.scheme != "studenttracker") return null

            val id = uri.getQueryParameter("id")?.toIntOrNull()
            val byId = id?.let { wanted -> students.firstOrNull { it.id == wanted } }
            if (byId != null) return byId

            // Badge printed on another device: ids differ, identity does not
            val first = uri.getQueryParameter("first").orEmpty()
            val last = uri.getQueryParameter("last").orEmpty()
            val birthday = uri.getQueryParameter("birthday")?.toLongOrNull()
            students.firstOrNull {
                it.firstName.equals(first, ignoreCase = true) &&
                        it.lastName.equals(last, ignoreCase = true) &&
                        (birthday == null || it.birthday == birthday)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun handleScan(payload: String) {
        val record = activeRecord ?: return
        if (payload == lastPayload) return // Same badge still in frame
        lastPayload = payload

        val student = resolveStudent(payload)
        if (student == null) {
            scanned.add(0, "Unrecognised badge" to false)
            return
        }
        if (markedIds.contains(student.id)) {
            scanned.add(0, "${student.firstName} ${student.lastName} (already marked)" to true)
            return
        }

        markedIds.add(student.id)
        scanned.add(0, "${student.firstName} ${student.lastName}" to true)

        scope.launch {
            val updated = repository.updateAttendanceStatus(record.id, activeDate, student.id, "PRESENT")
            if (updated == 0) {
                repository.insertAttendanceLog(
                    AttendanceLogEntity(
                        recordId = record.id,
                        dateMillis = activeDate,
                        studentId = student.id,
                        status = "PRESENT"
                    )
                )
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Scan Attendance", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                !hasCameraPermission -> CameraDeniedNotice { permissionLauncher.launch(Manifest.permission.CAMERA) }
                activeRecord == null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Choose an attendance sheet to begin.", fontSize = 13.sp)
                }
                else -> {
                    ScannerViewport(onDecoded = { handleScan(it) })

                    Text(
                        text = "${markedIds.size} marked present",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(scanned) { entry ->
                            Text(
                                text = entry.first,
                                fontSize = 12.sp,
                                color = if (entry.second) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showPicker && hasCameraPermission) {
            AttendanceSheetPicker(
                records = records,
                onPick = { record, date ->
                    activeRecord = record
                    activeDate = date
                    showPicker = false
                    scope.launch {
                        // Anyone already present should not be re-counted by a stray scan
                        val existing = repository.getLogsForDate(record.id, date)
                        markedIds.clear()
                        markedIds.addAll(existing.filter { it.status == "PRESENT" }.map { it.studentId })
                    }
                },
                onDismiss = {
                    showPicker = false
                    if (activeRecord == null) onBack()
                }
            )
        }
    }
}

@Composable
private fun CameraDeniedNotice(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Camera access is needed to read badges. It is used only for scanning and nothing is uploaded.",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRequest) { Text(stringResource(R.string.s_grant_camera_access)) }
    }
}

/**
 * Continuous camera preview. Wraps the zxing view directly rather than launching a separate
 * capture activity, because a roll call is dozens of scans in a row and returning to a result
 * screen between each one would defeat the point.
 */
@Composable
private fun ScannerViewport(onDecoded: (String) -> Unit) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val currentOnDecoded by rememberUpdatedState(onDecoded)
    var view by remember { mutableStateOf<com.journeyapps.barcodescanner.CompoundBarcodeView?>(null) }

    // The preview must release the camera when the app is backgrounded, or returning to the
    // screen leaves a black viewport and a held camera handle.
    DisposableEffect(lifecycleOwner, view) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> view?.resume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> view?.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view?.pause()
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        factory = { ctx ->
            com.journeyapps.barcodescanner.CompoundBarcodeView(ctx).apply {
                setStatusText("")
                decodeContinuous(object : com.journeyapps.barcodescanner.BarcodeCallback {
                    override fun barcodeResult(result: com.journeyapps.barcodescanner.BarcodeResult) {
                        result.text?.let { currentOnDecoded(it) }
                    }
                })
                resume()
                view = this
            }
        }
    )
}

/** Sheet and day selection, mirroring the seating chart picker so the flows feel identical. */
@Composable
private fun AttendanceSheetPicker(
    records: List<AttendanceRecordEntity>,
    onPick: (AttendanceRecordEntity, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var chosen by remember { mutableStateOf<AttendanceRecordEntity?>(null) }
    val sdf = remember { java.text.SimpleDateFormat("EEE, MMM dd", java.util.Locale.US) }
    val dates = remember(chosen) { chosen?.let { generateDateList(it.startDate, it.endDate) } ?: emptyList() }

    val todayMillis = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (chosen == null) "Choose Attendance Sheet" else "Choose Day", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (records.isEmpty()) {
                    Text(
                        text = "No attendance sheets exist yet. Create one from the attendance screen first.",
                        fontSize = 13.sp
                    )
                } else if (chosen == null) {
                    records.forEach { record ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { chosen = record },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(record.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    text = "${sdf.format(java.util.Date(record.startDate))} - ${sdf.format(java.util.Date(record.endDate))}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                } else {
                    dates.forEach { dateMillis ->
                        val isToday = dateMillis == todayMillis
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(chosen!!, dateMillis) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isToday) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(sdf.format(java.util.Date(dateMillis)), fontSize = 13.sp)
                                if (isToday) Text("Today", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (chosen != null) TextButton(onClick = { chosen = null }) { Text(stringResource(R.string.s_back)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp)
    )
}
