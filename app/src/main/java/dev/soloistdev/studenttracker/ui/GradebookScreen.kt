package dev.soloistdev.studenttracker.ui

import android.widget.Toast

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color // RESOLVED: Color import
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradebookScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { StudentRepository(context) }

    var students by remember { mutableStateOf<List<StudentEntity>>(emptyList()) }
    var columns by remember { mutableStateOf<List<AssessmentColumnEntity>>(emptyList()) }
    var activeColumn by remember { mutableStateOf<AssessmentColumnEntity?>(null) }
    var scoresList by remember { mutableStateOf<List<AssessmentScoreEntity>>(emptyList()) }

    // Roster scores input memory map
    val inputScores = remember { mutableStateMapOf<Int, String>() }

    var showAddColumnDialog by remember { mutableStateOf(false) }

    fun refreshData() {
        scope.launch {
            students = repository.getAllActiveStudents()
            columns = repository.getAllAssessmentColumns()
            if (activeColumn == null && columns.isNotEmpty()) {
                activeColumn = columns.first()
            }
            activeColumn?.let { col ->
                val scores = repository.getScoresForColumn(col.id)
                inputScores.clear()
                scores.forEach { inputScores[it.studentId] = it.score }
            }
            scoresList = repository.getAllAssessmentScores()
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    // Refresh student inputs dynamically when active column shifts
    LaunchedEffect(activeColumn) {
        activeColumn?.let { col ->
            val scores = repository.getScoresForColumn(col.id)
            inputScores.clear()
            scores.forEach { inputScores[it.studentId] = it.score }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.menu_gradebook_matrix), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (columns.isNotEmpty() && students.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                val allScores = repository.getAllAssessmentScores()
                                GradebookExportEngine.exportGradebookToCsv(context, students, columns, allScores)
                            }
                        }) {
                            Icon(Icons.Default.Download, contentDescription = stringResource(R.string.gradebook_action_export), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddColumnDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Section 1: Dynamic Horizontally Scrollable Header Column/Task selector
            Text(
                text = "Active Evaluation Column *",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            if (columns.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.gradebook_empty_state),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    columns.forEach { col ->
                        val isSelected = activeColumn?.id == col.id
                        InputChip(
                            selected = isSelected,
                            onClick = { activeColumn = col },
                            label = { Text(col.name) },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            repository.softDeleteAssessmentColumn(col.id)
                                            activeColumn = null
                                            refreshData()
                                            Toast.makeText(context, R.string.gradebook_toast_column_deleted, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(12.dp))
                                }
                            },
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                containerColor = Color.Transparent,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (activeColumn != null && students.isNotEmpty()) {
                // Section 2: Active Task Details
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Evaluating: ${activeColumn!!.name}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.gradebook_label_max_points, activeColumn!!.maxPoints),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    inputScores.forEach { (studentId, score) ->
                                        repository.insertAssessmentScore(
                                            AssessmentScoreEntity(
                                                columnId = activeColumn!!.id,
                                                studentId = studentId,
                                                score = score.trim()
                                            )
                                        )
                                    }
                                    refreshData()
                                    Toast.makeText(context, R.string.gradebook_toast_scores_saved, Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(stringResource(R.string.gradebook_action_save_scores))
                        }
                    }
                }

                // Section 3: Spreadsheet Roster Evaluation Matrix Listing
                Text(
                    text = stringResource(R.string.gradebook_scores_header),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(students) { student ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1.1f)) {
                                    Text(
                                        text = "${student.lastName}, ${student.firstName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val genderStr = if (student.gender == "F") "Female" else "Male"
                                    Text(
                                        text = "$genderStr | ${student.contactNumber.ifEmpty { "No Contact" }}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                OutlinedTextField(
                                    value = inputScores[student.id] ?: "",
                                    onValueChange = { inputScores[student.id] = it },
                                    label = { Text(stringResource(R.string.gradebook_label_score)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(0.9f)
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (students.isEmpty()) " Roster is empty. Add students to begin." else "Select an evaluation column above to record scores.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // ADD NEW GRADING COLUMN/TASK DIALOG
        if (showAddColumnDialog) {
            var colName by remember { mutableStateOf("") }
            var maxPointsInput by remember { mutableStateOf("100.0") }

            AlertDialog(
                onDismissRequest = { showAddColumnDialog = false },
                title = { Text(stringResource(R.string.gradebook_add_column), fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .imePadding(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = colName,
                            onValueChange = { colName = it },
                            label = { Text(stringResource(R.string.gradebook_column_name_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = maxPointsInput,
                            onValueChange = { maxPointsInput = it },
                            label = { Text(stringResource(R.string.gradebook_max_points_label)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (colName.isNotBlank()) {
                                scope.launch {
                                    val limit = maxPointsInput.toDoubleOrNull() ?: 100.0
                                    val col = AssessmentColumnEntity(
                                        name = colName.trim(),
                                        maxPoints = limit
                                    )
                                    repository.insertAssessmentColumn(col)
                                    refreshData()
                                    showAddColumnDialog = false
                                    Toast.makeText(context, R.string.gradebook_toast_column_created, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = colName.isNotBlank()
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddColumnDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}