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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

    // Tracks the active row and column grid counts to draw the background grid
    var activeGridRows by remember { mutableIntStateOf(0) }
    var activeGridCols by remember { mutableIntStateOf(0) }

    fun refreshStudents() {
        scope.launch {
            val list = repository.getAllActiveStudents()
            students = list.filter { it.className == className }
        }
    }

    LaunchedEffect(Unit) {
        refreshStudents()
    }

    val placedStudents = remember(students) { students.filter { it.seatingX >= 0f && it.seatingY >= 0f } }
    val unplacedStudents = remember(students) { students.filter { it.seatingX < 0f || it.seatingY < 0f } }

    var canvasSize by remember { mutableStateOf(Offset.Zero) }

    // DYNAMIC PIXEL-LEVEL & RELATIONAL GRID OVERLAP CALCULATIONS [1, 2]
    val overlappingStudentIds = remember(placedStudents, canvasSize, activeGridCols, activeGridRows) {
        val set = mutableSetOf<Int>()
        if (canvasSize.x > 0 && canvasSize.y > 0 && placedStudents.isNotEmpty()) {
            if (activeGridCols > 0 && activeGridRows > 0) {
                // GRID MODE: Overlap only if multiple students are assigned to the exact same cell coordinate (r, c) [1]
                placedStudents.forEachIndexed { i, studentA ->
                    val cA = round(studentA.seatingX * activeGridCols - 0.5f).toInt()
                    val rA = round(studentA.seatingY * activeGridRows - 0.5f).toInt()

                    for (j in i + 1 until placedStudents.size) {
                        val studentB = placedStudents[j]
                        val cB = round(studentB.seatingX * activeGridCols - 0.5f).toInt()
                        val rB = round(studentB.seatingY * activeGridRows - 0.5f).toInt()

                        if (cA == cB && rA == rB) {
                            set.add(studentA.id)
                            set.add(studentB.id)
                        }
                    }
                }
            } else {
                // FREEFORM MODE: Overlap if physical circle boundaries collide (distance < 48dp) [1]
                val diameterPx = with(density) { 48.dp.toPx() }
                placedStudents.forEachIndexed { i, studentA ->
                    val ax = studentA.seatingX * canvasSize.x
                    val ay = studentA.seatingY * canvasSize.y
                    for (j in i + 1 until placedStudents.size) {
                        val studentB = placedStudents[j]
                        val bx = studentB.seatingX * canvasSize.x
                        val by = studentB.seatingY * canvasSize.y

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
            // Dynamically scales circle diameter based on grid density [2]
            val dynamicSize = (minOf(cellWidthDp, cellHeightDp) * 0.85f).coerceIn(24f, 48f)
            dynamicSize.dp
        } else {
            48.dp // Default size for free form layouts
        }
    }

    // ADAPTIVE TYPOGRAPHY ENGINE [1, 2]
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
                    IconButton(onClick = { showLayoutSelectorDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = "Choose Seating Layout"
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
        ) {
            // Unplaced Students Shelf (Horizontal Drawer)
            Card(
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
                            items(unplacedStudents) { student ->
                                Surface(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .clickable {
                                            scope.launch {
                                                repository.updateStudentSeating(student.id, 0.5f, 0.5f)
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

                    // Live placement stats console
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp)
                            .align(Alignment.TopCenter),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            val properlyPlacedCount = placedStudents.size - overlappingStudentIds.size
                            val overlappedCount = overlappingStudentIds.size
                            val unplacedCount = unplacedStudents.size

                            Text(
                                text = "$properlyPlacedCount properly placed • $overlappedCount overlapped • $unplacedCount unplaced",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Draw Placed Student Nodes
                    placedStudents.forEach { student ->
                        var localOffsetX by remember(student.id, student.seatingX) { mutableFloatStateOf(student.seatingX) }
                        var localOffsetY by remember(student.id, student.seatingY) { mutableFloatStateOf(student.seatingY) }

                        val leftOffset = (localOffsetX * maxWidth.value).dp
                        val topOffset = (localOffsetY * maxHeight.value).dp

                        val isOverlapping = overlappingStudentIds.contains(student.id)
                        val nodeContainerColor = if (isOverlapping) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        val nodeContentColor = if (isOverlapping) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
                        val nodeBorderStroke = if (isOverlapping) BorderStroke(2.dp, MaterialTheme.colorScheme.onErrorContainer) else null

                        Box(
                            modifier = Modifier
                                .offset(x = leftOffset - (nodeSizeDp / 2), y = topOffset - (nodeSizeDp / 2)) // UPDATED: Centers correctly based on dynamic size [1, 2]
                                .size(nodeSizeDp) // UPDATED: Dynamic sizing applied [1, 2]
                                .clip(CircleShape)
                                .background(nodeContainerColor)
                                .then(if (nodeBorderStroke != null) Modifier.border(nodeBorderStroke, CircleShape) else Modifier)
                                .pointerInput(student.id) {
                                    detectTapGestures(
                                        onTap = {
                                            selectedStudentForAction = student
                                            showActionSheet = true
                                        }
                                    )
                                }
                                .pointerInput(student.id) {
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
                                                    repository.updateStudentSeating(student.id, snappedX, snappedY)
                                                    refreshStudents()
                                                }
                                            } else {
                                                scope.launch {
                                                    repository.updateStudentSeating(student.id, localOffsetX, localOffsetY)
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
                                fontSize = fontSizeSp // UPDATED: Adaptive typography applied [1, 2]
                            )
                        }
                    }
                }
            }
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
                            Text("View Student Profile")
                        }
                        Button(
                            onClick = {
                                showActionSheet = false
                                showAddIncidentDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Log Behavior / Milestone")
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    repository.updateStudentSeating(student.id, -1f, -1f)
                                    refreshStudents()
                                    showActionSheet = false
                                    selectedStudentForAction = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Text("Unplace Student Seat")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showActionSheet = false }) {
                        Text("Cancel")
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
                        // 1. FREEFORM / DRAFT OPTION
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        students.forEach { s ->
                                            repository.updateStudentSeating(s.id, -1f, -1f)
                                        }
                                        activeGridRows = 0
                                        activeGridCols = 0
                                        refreshStudents()
                                        showLayoutSelectorDialog = false
                                        Toast.makeText(context, "Arrangement reset to manual draft!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Freeform / Manual Draft", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Resets all student seats so you can position them manually on the canvas.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            }
                        }

                        // 2. CEILING PERFECT SQUARE OPTION
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
                                                repository.updateStudentSeating(student.id, x, y)
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

                        // 3. CUSTOM R x C GRID OPTION
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
                        Text("Cancel")
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
                        Text("Define the dimensions of your custom grid. Roster student size must fit within your Row x Column limits.")

                        OutlinedTextField(
                            value = customRows,
                            onValueChange = { customRows = it.filter { c -> c.isDigit() } },
                            label = { Text("Grid Rows *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = customCols,
                            onValueChange = { customCols = it.filter { c -> c.isDigit() } },
                            label = { Text("Grid Columns *") },
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
                                        val r = index / c
                                        val c = index % c
                                        val x = (c + 0.5f) / c.toFloat()
                                        val y = (r + 0.5f) / r.toFloat()
                                        repository.updateStudentSeating(student.id, x, y)
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
                        Text("Apply Grid")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomGridDialog = false }) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}