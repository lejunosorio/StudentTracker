package dev.soloistdev.studenttracker.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity // RESOLVED: Local density import
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.BehaviorIncidentEntity
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.launch
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

    // Density resolver for screen conversions [2]
    val density = LocalDensity.current

    var students by remember { mutableStateOf<List<StudentEntity>>(emptyList()) }
    var selectedStudentForAction by remember { mutableStateOf<StudentEntity?>(null) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showAddIncidentDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("$className Seating Chart", fontWeight = FontWeight.Bold) },
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    // RESOLVED: Convert Dp to Px to prevent drag delta jumps [1, 2]
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
                        var localOffsetX by remember(student.id) { mutableStateOf(student.seatingX) }
                        var localOffsetY by remember(student.id) { mutableStateOf(student.seatingY) }

                        val leftOffset = (localOffsetX * maxWidth.value).dp
                        val topOffset = (localOffsetY * maxHeight.value).dp

                        Box(
                            modifier = Modifier
                                .offset(x = leftOffset - 24.dp, y = topOffset - 24.dp)
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
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
                                            // Evaluates exact 1-to-1 pixel progress relative to finger movement [1, 2]
                                            val newX = (localOffsetX + dragAmount.x / canvasSize.x).coerceIn(0.05f, 0.95f)
                                            val newY = (localOffsetY + dragAmount.y / canvasSize.y).coerceIn(0.05f, 0.95f)
                                            localOffsetX = newX
                                            localOffsetY = newY

                                            scope.launch {
                                                repository.updateStudentSeating(student.id, newX, newY)
                                            }
                                        },
                                        onDragEnd = {
                                            refreshStudents() // Sync list on gesture end
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            val initials = "${student.lastName.take(1)}${student.firstName.take(1)}".uppercase()
                            Text(
                                text = initials,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
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
    }
}