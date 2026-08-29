package dev.soloistdev.studenttracker.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.AttendanceLogEntity
import dev.soloistdev.studenttracker.data.AttendanceRecordEntity
import dev.soloistdev.studenttracker.data.BehaviorIncidentEntity
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.round
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeatingChartScreen(
    className: String,
    onBack: () -> Unit,
    onStudentClick: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { StudentRepository(context) }
    val density = LocalDensity.current

    var students by remember { mutableStateOf<List<StudentEntity>>(emptyList()) }
    var selectedStudentForAction by remember { mutableStateOf<StudentEntity?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showAddIncidentDialog by remember { mutableStateOf(false) }

    // Placement Dialog state controllers
    var showLayoutSelectorDialog by remember { mutableStateOf(false) }
    var showCustomGridDialog by remember { mutableStateOf(false) }

    // --- Attendance mode ---
    // Taking the roll from the chart matches how a teacher actually scans a room: read the
    // seats, mark the gaps. Seats become status swatches and dragging is suspended so a roll
    // call cannot accidentally rearrange the layout.
    //
    // Saveable, because opening a student profile from a seat disposes this screen's composition
    // while it waits on the back stack. Everything below was a plain `remember`, so pressing Back
    // dropped the teacher out of attendance mode mid-roll with the sheet and date unpicked - the
    // marks were safely in the database, but the screen had forgotten which register they were in.
    //
    // The sheet is held as an id and a name rather than the row itself: an AttendanceRecordEntity
    // cannot go in a Bundle, and every operation here only ever needed the id anyway. Keeping the
    // name alongside it means the banner is correct on restore without reloading the record list,
    // which is only ever fetched when the picker is opened.
    var attendanceMode by rememberSaveable { mutableStateOf(false) }
    var showRecordPicker by remember { mutableStateOf(false) }
    var attendanceRecords by remember { mutableStateOf<List<AttendanceRecordEntity>>(emptyList()) }
    var activeRecordId by rememberSaveable { mutableIntStateOf(0) }
    var activeRecordName by rememberSaveable { mutableStateOf("") }
    var activeDateMillis by rememberSaveable { mutableLongStateOf(0L) }

    // Not saveable, and does not need to be: these are rows in the database, re-read below
    // whenever the sheet or the date changes - including on the way back from a profile.
    val seatStatuses = remember { mutableStateMapOf<Int, String>() }

    fun loadSeatStatuses() {
        if (activeRecordId == 0) return
        scope.launch {
            val logs = repository.getLogsForDate(activeRecordId, activeDateMillis)
            seatStatuses.clear()
            logs.forEach { seatStatuses[it.studentId] = it.status }
        }
    }

    fun cycleSeatStatus(studentId: Int) {
        if (activeRecordId == 0) return
        val next = when (seatStatuses[studentId] ?: "NOT_SET") {
            "NOT_SET" -> "PRESENT"
            "PRESENT" -> "ABSENT"
            "ABSENT" -> "EXCUSED"
            else -> "NOT_SET"
        }
        seatStatuses[studentId] = next
        scope.launch {
            // The roster for a sheet is materialised as logs up front, but a student added to
            // the class afterwards has no row yet, so fall back to an insert.
            val updated = repository.updateAttendanceStatus(activeRecordId, activeDateMillis, studentId, next)
            if (updated == 0) {
                repository.insertAttendanceLog(
                    AttendanceLogEntity(
                        recordId = activeRecordId,
                        dateMillis = activeDateMillis,
                        studentId = studentId,
                        status = next
                    )
                )
            }
        }
    }

    // Tracks the active row and column grid counts to draw the background grid.
    //
    // Saveable for the same reason as the attendance state: these also drive snap-to-grid and the
    // seat sizing, so losing them on the way back from a profile left the chart looking the same
    // but quietly no longer snapping.
    var activeGridRows by rememberSaveable { mutableIntStateOf(0) }
    var activeGridCols by rememberSaveable { mutableIntStateOf(0) }

    fun refreshStudents() {
        scope.launch {
            val list = repository.getAllActiveStudents()
            // Filter students who are members of this classroom
            students = list.filter { student ->
                student.getClassNamesList().contains(className)
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshStudents()
    }

    // Re-reads the marks whenever the sheet or the date changes, which also covers coming back
    // from a student profile: the ids above survive that, the seat colours themselves do not.
    LaunchedEffect(activeRecordId, activeDateMillis) {
        loadSeatStatuses()
    }

    // Filter placed/unplaced students according to active classroom coordinate configurations
    val placedStudents = remember(students) {
        students.filter { student ->
            val coords = student.getSeatingCoordinates(className)
            coords != null && coords.first >= 0f && coords.second >= 0f
        }
    }
    val unplacedStudents = remember(students) {
        students.filter { student ->
            val coords = student.getSeatingCoordinates(className)
            coords == null || coords.first < 0f || coords.second < 0f
        }
    }

    var canvasSize by remember { mutableStateOf(Offset.Zero) }

    // DYNAMIC PIXEL-LEVEL & RELATIONAL GRID OVERLAP CALCULATIONS
    val overlappingStudentIds = remember(placedStudents, canvasSize, activeGridCols, activeGridRows) {
        val set = mutableSetOf<Int>()
        if (canvasSize.x > 0 && canvasSize.y > 0 && placedStudents.isNotEmpty()) {
            if (activeGridCols > 0 && activeGridRows > 0) {
                placedStudents.forEachIndexed { i, studentA ->
                    val coordsA = studentA.getSeatingCoordinates(className) ?: Pair(-1f, -1f)
                    val cA = round(coordsA.first * activeGridCols - 0.5f).toInt()
                    val rA = round(coordsA.second * activeGridRows - 0.5f).toInt()

                    for (j in i + 1 until placedStudents.size) {
                        val studentB = placedStudents[j]
                        val coordsB = studentB.getSeatingCoordinates(className) ?: Pair(-1f, -1f)
                        val cB = round(coordsB.first * activeGridCols - 0.5f).toInt()
                        val rB = round(coordsB.second * activeGridRows - 0.5f).toInt()

                        if (cA == cB && rA == rB) {
                            set.add(studentA.id)
                            set.add(studentB.id)
                        }
                    }
                }
            } else {
                val diameterPx = with(density) { 48.dp.toPx() }
                placedStudents.forEachIndexed { i, studentA ->
                    val coordsA = studentA.getSeatingCoordinates(className) ?: Pair(-1f, -1f)
                    val ax = coordsA.first * canvasSize.x
                    val ay = coordsA.second * canvasSize.y
                    for (j in i + 1 until placedStudents.size) {
                        val studentB = placedStudents[j]
                        val coordsB = studentB.getSeatingCoordinates(className) ?: Pair(-1f, -1f)
                        val bx = coordsB.first * canvasSize.x
                        val by = coordsB.second * canvasSize.y

                        val dist = sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by))
                        if (dist < diameterPx) {
                            set.add(studentA.id)
                            set.add(studentB.id)
                        }
                    }
                }
            }
        }
        set
    }

    val nodeSizeDp = remember(placedStudents.size, canvasSize, activeGridCols, activeGridRows) {
        if (activeGridCols > 0 && activeGridRows > 0 && canvasSize.x > 0) {
            val cellWidthDp = (canvasSize.x / activeGridCols) / density.density
            val cellHeightDp = (canvasSize.y / activeGridRows) / density.density
            // Dynamically scales circle diameter based on grid density
            val dynamicSize = (minOf(cellWidthDp, cellHeightDp) * 0.85f).coerceIn(24f, 48f)
            dynamicSize.dp
        } else {
            48.dp
        }
    }

    // ADAPTIVE TYPOGRAPHY ENGINE
    val fontSizeSp = remember(nodeSizeDp) {
        if (nodeSizeDp < 32.dp) 10.sp else if (nodeSizeDp < 40.dp) 12.sp else 14.sp
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("$className Seating Chart", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (attendanceMode) {
                            attendanceMode = false
                            activeRecordId = 0
                            activeRecordName = ""
                            seatStatuses.clear()
                        } else {
                            scope.launch {
                                attendanceRecords = repository.getAllAttendanceRecords()
                                showRecordPicker = true
                            }
                        }
                    }) {
                        Icon(
                            imageVector = if (attendanceMode) Icons.Default.Close else Icons.Default.EventAvailable,
                            contentDescription = if (attendanceMode) "Leave attendance mode" else "Take attendance from chart",
                            tint = if (attendanceMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    if (!attendanceMode) {
                        IconButton(onClick = { showLayoutSelectorDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = stringResource(R.string.cd_choose_seating_layout)
                            )
                        }
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
            if (attendanceMode && activeRecordId != 0) {
                AttendanceSeatBanner(
                    recordName = activeRecordName,
                    dateMillis = activeDateMillis,
                    placedCount = placedStudents.size,
                    unplacedCount = unplacedStudents.size,
                    presentCount = placedStudents.count { seatStatuses[it.id] == "PRESENT" },
                    absentCount = placedStudents.count { seatStatuses[it.id] == "ABSENT" },
                    excusedCount = placedStudents.count { seatStatuses[it.id] == "EXCUSED" },
                    onMarkRestPresent = {
                        placedStudents.forEach { student ->
                            if ((seatStatuses[student.id] ?: "NOT_SET") == "NOT_SET") {
                                // Cycling once from NOT_SET lands on PRESENT
                                cycleSeatStatus(student.id)
                            }
                        }
                    }
                )
            }

            // Unplaced Students Shelf (Horizontal Drawer). Hidden during a roll call so the
            // chart itself gets the screen.
            if (!attendanceMode) Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Unplaced Students (${unplacedStudents.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (unplacedStudents.isEmpty()) {
                        Text(
                            text = "All roster members have been positioned.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(unplacedStudents, key = { it.id }) { student ->
                                Surface(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .clickable {
                                            scope.launch {
                                                // Places student inside selected class coordinate layout
                                                repository.updateStudentSeating(student.id, className, 0.5f, 0.5f)
                                                refreshStudents()
                                            }
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("${student.lastName}, ${student.firstName.take(1)}.", fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Placement stats.
            //
            // Above the canvas, not inside it. This used to be drawn at the top of the seat area
            // and before the seats themselves, so any student sitting near the front row covered
            // it - which is most classrooms, and exactly the layout a teacher is most likely to
            // build. Outside the canvas it stays readable no matter where the seats go.
            //
            // Hidden during a roll call because the attendance banner above already carries the
            // placed and unplaced counts.
            if (!attendanceMode) {
                val properlyPlacedCount = placedStudents.size - overlappingStudentIds.size
                val overlappedCount = overlappingStudentIds.size

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = "$properlyPlacedCount properly placed • $overlappedCount overlapped • ${unplacedStudents.size} unplaced",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Interactive 2D Workspace Canvas
            val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                    .drawBehind {
                        if (activeGridRows > 0 && activeGridCols > 0) {
                            val strokeWidthPx = 1.dp.toPx()

                            // Draw Vertical Grid Lines
                            for (i in 1 until activeGridCols) {
                                val x = (i * size.width) / activeGridCols
                                drawLine(
                                    color = gridLineColor,
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = strokeWidthPx
                                )
                            }

                            // Draw Horizontal Grid Lines
                            for (i in 1 until activeGridRows) {
                                val y = (i * size.height) / activeGridRows
                                drawLine(
                                    color = gridLineColor,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = strokeWidthPx
                                )
                            }
                        }
                    }
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val maxWidthPx = with(density) { maxWidth.toPx() }
                    val maxHeightPx = with(density) { maxHeight.toPx() }
                    canvasSize = Offset(maxWidthPx, maxHeightPx)

                    // Draw classroom whiteboard visual baseline indicator
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .width(120.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.outline)
                            .align(Alignment.TopCenter)
                    )

                    // Draw Placed Student Nodes
                    placedStudents.forEach { student ->
                        val currentCoordinates = remember(student.id, student.seatingJson) {
                            student.getSeatingCoordinates(className) ?: Pair(0.5f, 0.5f)
                        }
                        var localOffsetX by remember(student.id, currentCoordinates) { mutableFloatStateOf(currentCoordinates.first) }
                        var localOffsetY by remember(student.id, currentCoordinates) { mutableFloatStateOf(currentCoordinates.second) }

                        val leftOffset = (localOffsetX * maxWidth.value).dp
                        val topOffset = (localOffsetY * maxHeight.value).dp

                        val isOverlapping = overlappingStudentIds.contains(student.id)
                        val seatStatus = seatStatuses[student.id] ?: "NOT_SET"
                        val nodeContainerColor = when {
                            attendanceMode -> attendanceSeatColor(seatStatus)
                            isOverlapping -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        }
                        val nodeContentColor = when {
                            attendanceMode -> Color.White
                            isOverlapping -> MaterialTheme.colorScheme.onError
                            else -> MaterialTheme.colorScheme.onPrimary
                        }
                        // Overlap warning is suppressed during a roll call: the colour channel
                        // is carrying attendance status instead.
                        val nodeBorderStroke = if (isOverlapping && !attendanceMode) {
                            BorderStroke(2.dp, MaterialTheme.colorScheme.onErrorContainer)
                        } else null

                        Box(
                            modifier = Modifier
                                .offset(x = leftOffset - (nodeSizeDp / 2), y = topOffset - (nodeSizeDp / 2))
                                .size(nodeSizeDp)
                                .clip(CircleShape)
                                .background(nodeContainerColor)
                                .then(if (nodeBorderStroke != null) Modifier.border(nodeBorderStroke, CircleShape) else Modifier)
                                .pointerInput(student.id, attendanceMode) {
                                    detectTapGestures(
                                        onTap = {
                                            if (attendanceMode) {
                                                cycleSeatStatus(student.id)
                                            } else {
                                                selectedStudentForAction = student
                                                showActionSheet = true
                                            }
                                        }
                                    )
                                }
                                .pointerInput(student.id, attendanceMode) {
                                    // Seats are frozen while taking the roll
                                    if (attendanceMode) return@pointerInput
                                    detectDragGestures(
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            localOffsetX = (localOffsetX + dragAmount.x / canvasSize.x).coerceIn(0.05f, 0.95f)
                                            localOffsetY = (localOffsetY + dragAmount.y / canvasSize.y).coerceIn(0.05f, 0.95f)
                                        },
                                        onDragEnd = {
                                            if (activeGridRows > 0 && activeGridCols > 0) {
                                                val c = round(localOffsetX * activeGridCols - 0.5f).toInt().coerceIn(0, activeGridCols - 1)
                                                val r = round(localOffsetY * activeGridRows - 0.5f).toInt().coerceIn(0, activeGridRows - 1)

                                                val snappedX = (c + 0.5f) / activeGridCols.toFloat()
                                                val snappedY = (r + 0.5f) / activeGridRows.toFloat()

                                                localOffsetX = snappedX
                                                localOffsetY = snappedY

                                                scope.launch {
                                                    repository.updateStudentSeating(student.id, className, snappedX, snappedY)
                                                    refreshStudents()
                                                }
                                            } else {
                                                scope.launch {
                                                    repository.updateStudentSeating(student.id, className, localOffsetX, localOffsetY)
                                                    refreshStudents()
                                                }
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val initials = "${student.lastName.take(1)}${student.firstName.take(1)}".uppercase()
                            Text(
                                text = initials,
                                color = nodeContentColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = fontSizeSp
                            )
                        }
                    }
                }
            }
        }

        // ATTENDANCE SHEET AND DATE PICKER
        if (showRecordPicker) {
            AttendanceRecordPickerDialog(
                records = attendanceRecords,
                onPick = { record, dateMillis ->
                    activeRecordId = record.id
                    activeRecordName = record.name
                    activeDateMillis = dateMillis
                    attendanceMode = true
                    showRecordPicker = false
                    // No explicit load: the effect keyed on the sheet and date covers this and
                    // the return from a profile through one path rather than two.
                },
                onDismiss = { showRecordPicker = false }
            )
        }

        // QUICK ACTION SELECTION SHEET
        if (showActionSheet && selectedStudentForAction != null) {
            val student = selectedStudentForAction!!
            AlertDialog(
                onDismissRequest = { showActionSheet = false },
                title = { Text("${student.firstName} ${student.lastName}", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                showActionSheet = false
                                onStudentClick(student.id)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.s_view_student_profile))
                        }
                        Button(
                            onClick = {
                                showActionSheet = false
                                showAddIncidentDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text(stringResource(R.string.s_log_behavior_milestone))
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    // Removes seating coordinates bound specifically to this classroom
                                    repository.updateStudentSeating(student.id, className, -1f, -1f)
                                    refreshStudents()
                                    showActionSheet = false
                                    selectedStudentForAction = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.s_unplace_student_seat))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showActionSheet = false }) {
                        Text(stringResource(R.string.s_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

        // IN-CANVAS BEHAVIOR LOG DIALOG
        if (showAddIncidentDialog && selectedStudentForAction != null) {
            var incidentTitle by remember { mutableStateOf("") }
            var selectedCategory by remember { mutableStateOf("Positive") }
            var incidentDescription by remember { mutableStateOf("") }

            val categories = listOf("Positive", "Negative", "Neutral")
            val chipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = Color.Transparent,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AlertDialog(
                onDismissRequest = { showAddIncidentDialog = false },
                title = { Text("Log Behavior for ${selectedStudentForAction!!.firstName}", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .imePadding(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = incidentTitle,
                            onValueChange = { incidentTitle = it },
                            label = { Text(stringResource(R.string.behavior_field_title)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = stringResource(R.string.behavior_quick_category_label),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            categories.forEach { category ->
                                val labelRes = when (category) {
                                    "Positive" -> R.string.behavior_cat_positive
                                    "Negative" -> R.string.behavior_cat_negative
                                    else -> R.string.behavior_cat_neutral
                                }
                                FilterChip(
                                    selected = selectedCategory == category,
                                    onClick = { selectedCategory = category },
                                    label = { Text(stringResource(labelRes)) },
                                    colors = chipColors
                                )
                            }
                        }

                        OutlinedTextField(
                            value = incidentDescription,
                            onValueChange = { incidentDescription = it },
                            label = { Text(stringResource(R.string.behavior_field_notes)) },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (incidentTitle.isBlank()) {
                                Toast.makeText(context, R.string.error_behavior_title_required, Toast.LENGTH_SHORT).show()
                            } else {
                                scope.launch {
                                    val newIncident = BehaviorIncidentEntity(
                                        studentId = selectedStudentForAction!!.id,
                                        title = incidentTitle.trim(),
                                        category = selectedCategory,
                                        description = incidentDescription.trim()
                                    )
                                    repository.insertIncident(newIncident)
                                    showAddIncidentDialog = false
                                    selectedStudentForAction = null
                                    Toast.makeText(context, R.string.toast_incident_logged, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showAddIncidentDialog = false
                        selectedStudentForAction = null
                    }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

        // ALGORITHM LAYOUT SELECTOR MENU DIALOG
        if (showLayoutSelectorDialog) {
            AlertDialog(
                onDismissRequest = { showLayoutSelectorDialog = false },
                title = { Text("Choose Seating Layout Style", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        students.forEach { s ->
                                            repository.updateStudentSeating(s.id, className, -1f, -1f)
                                        }
                                        activeGridRows = 0
                                        activeGridCols = 0
                                        refreshStudents()
                                        showLayoutSelectorDialog = false
                                        Toast.makeText(context, context.getString(R.string.toast_arrangement_reset_to_manual_draft), Toast.LENGTH_SHORT).show()
                                    }
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Freeform / Manual Draft", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Resets all student seats so you can position them manually on the canvas.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val rosterSize = students.size
                                    if (rosterSize > 0) {
                                        val s = ceil(sqrt(rosterSize.toDouble())).toInt()
                                        scope.launch {
                                            students.forEachIndexed { index, student ->
                                                val r = index / s
                                                val c = index % s
                                                val x = (c + 0.5f) / s.toFloat()
                                                val y = (r + 0.5f) / s.toFloat()
                                                repository.updateStudentSeating(student.id, className, x, y)
                                            }
                                            activeGridRows = s
                                            activeGridCols = s
                                            refreshStudents()
                                            showLayoutSelectorDialog = false
                                            Toast.makeText(context, "Arranged in a $s x $s ceiling square!", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        showLayoutSelectorDialog = false
                                    }
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Ceiling Square Grid", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Auto-arranges students into a perfect square grid matching the nearest ceiling perfect square of the roster.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showCustomGridDialog = true
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Custom Grid (Rows x Columns)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Input custom row and column parameters to construct your own seating layout.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLayoutSelectorDialog = false }) {
                        Text(stringResource(R.string.s_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

        // CUSTOM GRID DIMENSIONS PROMPT DIALOG
        if (showCustomGridDialog) {
            var customRows by remember { mutableStateOf("5") }
            var customCols by remember { mutableStateOf("5") }

            AlertDialog(
                onDismissRequest = { showCustomGridDialog = false },
                title = { Text("Configure Custom Grid", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().imePadding()
                    ) {
                        Text(stringResource(R.string.s_define_the_dimensions_of_your_custom_grid))

                        OutlinedTextField(
                            value = customRows,
                            onValueChange = { customRows = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.s_grid_rows)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = customCols,
                            onValueChange = { customCols = it.filter { c -> c.isDigit() } },
                            label = { Text(stringResource(R.string.s_grid_columns)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val r = customRows.toIntOrNull() ?: 5
                            val c = customCols.toIntOrNull() ?: 5
                            val rosterSize = students.size

                            if (r * c < rosterSize) {
                                Toast.makeText(context, "Roster size ($rosterSize) exceeds your grid's capacity ($r x $c)!", Toast.LENGTH_LONG).show()
                            } else {
                                scope.launch {
                                    students.forEachIndexed { index, student ->
                                        val rowIdx = index / c
                                        val colIdx = index % c
                                        val x = (colIdx + 0.5f) / c.toFloat()
                                        val y = (rowIdx + 0.5f) / r.toFloat()
                                        repository.updateStudentSeating(student.id, className, x, y)
                                    }
                                    activeGridRows = r
                                    activeGridCols = c
                                    refreshStudents()
                                    showCustomGridDialog = false
                                    showLayoutSelectorDialog = false
                                    Toast.makeText(context, "Arranged in a $r x $c grid!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.s_apply_grid))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomGridDialog = false }) {
                        Text(stringResource(R.string.s_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}
/** Seat colour during a roll call. Fixed hues, since the theme carries no present/absent pair. */
@Composable
private fun attendanceSeatColor(status: String): Color = when (status) {
    "PRESENT" -> Color(0xFF2E7D32)
    "ABSENT" -> MaterialTheme.colorScheme.error
    "EXCUSED" -> Color(0xFFEF6C00)
    else -> Color(0xFF9E9E9E)
}

@Composable
private fun AttendanceSeatBanner(
    recordName: String,
    dateMillis: Long,
    placedCount: Int,
    unplacedCount: Int,
    presentCount: Int,
    absentCount: Int,
    excusedCount: Int,
    onMarkRestPresent: () -> Unit
) {
    val sdf = remember { java.text.SimpleDateFormat("EEE, MMM dd yyyy", java.util.Locale.US) }
    val unmarked = placedCount - presentCount - absentCount - excusedCount

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(recordName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                text = sdf.format(java.util.Date(dateMillis)),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SeatStatusTally("Present", presentCount, attendanceSeatColor("PRESENT"))
                SeatStatusTally("Absent", absentCount, attendanceSeatColor("ABSENT"))
                SeatStatusTally("Excused", excusedCount, attendanceSeatColor("EXCUSED"))
                SeatStatusTally("Unmarked", unmarked, attendanceSeatColor("NOT_SET"))
            }

            Text(
                text = "Tap a seat to cycle present, absent, excused. Seats are locked while marking.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            if (unplacedCount > 0) {
                Text(
                    text = "$unplacedCount student(s) have no seat and cannot be marked here. Use the attendance screen for those.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (unmarked > 0) {
                OutlinedButton(
                    onClick = onMarkRestPresent,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Mark remaining $unmarked present", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SeatStatusTally(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Picks which attendance sheet and which day the roll call writes to. Dates are constrained to
 * the range the sheet covers, so a tap can never file a mark outside its own sheet.
 */
@Composable
private fun AttendanceRecordPickerDialog(
    records: List<AttendanceRecordEntity>,
    onPick: (AttendanceRecordEntity, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var chosen by remember { mutableStateOf<AttendanceRecordEntity?>(null) }
    val sdf = remember { java.text.SimpleDateFormat("EEE, MMM dd", java.util.Locale.US) }

    val dates = remember(chosen) {
        chosen?.let { generateDateList(it.startDate, it.endDate) } ?: emptyList()
    }

    // Preselect today when the sheet covers it, which is the overwhelmingly common case
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
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (records.isEmpty()) {
                    Text(
                        text = "No attendance sheets exist yet. Create one from the attendance screen first.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                if (isToday) {
                                    Text("Today", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (chosen != null) {
                TextButton(onClick = { chosen = null }) { Text(stringResource(R.string.s_back)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp)
    )
}
