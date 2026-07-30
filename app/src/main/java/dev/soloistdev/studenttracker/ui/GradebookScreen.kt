package dev.soloistdev.studenttracker.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradebookScreen(
    onBack: () -> Unit,
    viewModel: GradebookViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val columns by viewModel.columns.collectAsState()
    val savedFilters by viewModel.savedFilters.collectAsState()
    val students by viewModel.students.collectAsState()
    val scores by viewModel.scores.collectAsState()

    var activeColumn by remember { mutableStateOf<AssessmentColumnEntity?>(null) }
    val inputScores = remember { mutableStateMapOf<Int, String>() }

    var showCreateSheetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    // Dynamic state listener mapping scores on column transitions
    LaunchedEffect(activeColumn, scores) {
        activeColumn?.let { col ->
            val matchingScores = scores.filter { it.columnId == col.id }
            inputScores.clear()
            matchingScores.forEach { inputScores[it.studentId] = it.score }
        }
    }

    val displayDateSdf = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (activeColumn != null) activeColumn!!.name else stringResource(R.string.menu_gradebook_matrix),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (activeColumn != null) {
                            activeColumn = null // Returns back to master view
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (activeColumn != null && students.isNotEmpty()) {
                        IconButton(onClick = {
                            val activeScores = scores.filter { it.columnId == activeColumn!!.id }
                            val rosterIds = activeScores.map { it.studentId }.distinct()
                            val roster = students.filter { it.id in rosterIds }

                            scope.launch {
                                GradebookExportEngine.exportGradebookToCsv(context, roster, listOf(activeColumn!!), activeScores)
                            }
                        }) {
                            Icon(Icons.Default.Download, contentDescription = stringResource(R.string.gradebook_action_export), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (activeColumn == null) {
                FloatingActionButton(
                    onClick = { showCreateSheetDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
                }
            }
        }
    ) { paddingValues ->
        if (activeColumn == null) {
            // ==========================================
            // VIEW 1: MASTER LIST OF GRADING SHEETS
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Text(
                    text = "Roster Grading Sheets",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                if (columns.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.gradebook_empty_state),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(columns) { col ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeColumn = col }, // Drill down into View 2
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = col.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Max Threshold: ${col.maxPoints} pts",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Exam: ${displayDateSdf.format(Date(col.examDate))} • Checked: ${displayDateSdf.format(Date(col.checkDate))}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                    IconButton(onClick = {
                                        viewModel.softDeleteColumn(col.id)
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ==========================================
            // VIEW 2: GRADING SHEET SCORES EDITOR
            // ==========================================
            val currentColumn = activeColumn!!
            val associatedScores = scores.filter { it.columnId == currentColumn.id }
            val rosterStudentIds = associatedScores.map { it.studentId }.distinct()
            val filteredRoster = students.filter { it.id in rosterStudentIds }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Evaluating: ${currentColumn.name}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Max Threshold: ${currentColumn.maxPoints} pts",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Exam: ${displayDateSdf.format(Date(currentColumn.examDate))} • Checked: ${displayDateSdf.format(Date(currentColumn.checkDate))}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        Button(
                            onClick = {
                                viewModel.saveRosterScores(currentColumn.id, inputScores)
                                Toast.makeText(context, R.string.gradebook_toast_scores_saved, Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(stringResource(R.string.gradebook_action_save_scores))
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.gradebook_scores_header) + " (${filteredRoster.size} Members)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )

                if (filteredRoster.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Roster selection is empty.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(filteredRoster) { student ->
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
                }
            }
        }

        // CREATE DISPATCH GRADING SHEET DIALOG (Attendance layout model)
        if (showCreateSheetDialog) {
            var colName by remember { mutableStateOf("") }
            var maxPointsInput by remember { mutableStateOf("100.0") }

            var selectedFilterId by remember { mutableIntStateOf(0) } // 0 indicates "All active students"
            var dropdownExpanded by remember { mutableStateOf(false) }

            var examDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
            var checkDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
            var showExamDatePicker by remember { mutableStateOf(false) }
            var showCheckDatePicker by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showCreateSheetDialog = false },
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

                        // Roster Filter Dropdown
                        Text(
                            text = stringResource(R.string.gradebook_select_roster_filter) + " *",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val activeFilterLabel = if (selectedFilterId == 0) stringResource(R.string.gradebook_all_students) else savedFilters.find { it.id == selectedFilterId }?.filterName ?: ""
                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = activeFilterLabel,
                                onValueChange = {},
                                readOnly = true,
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
                                    text = { Text(stringResource(R.string.gradebook_all_students)) },
                                    onClick = {
                                        selectedFilterId = 0
                                        dropdownExpanded = false
                                    }
                                )
                                savedFilters.forEach { filter ->
                                    DropdownMenuItem(
                                        text = { Text(filter.filterName) },
                                        onClick = {
                                            selectedFilterId = filter.id
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Exam Date selection button
                        Text(
                            text = "Exam Date Selection *",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(
                            onClick = { showExamDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(displayDateSdf.format(Date(examDate)), color = MaterialTheme.colorScheme.onSurface)
                                Icon(Icons.Default.CalendarToday, contentDescription = "Select Exam Date", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        // Evaluation Checking Date selection button
                        Text(
                            text = "Checking Date Selection *",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(
                            onClick = { showCheckDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(displayDateSdf.format(Date(checkDate)), color = MaterialTheme.colorScheme.onSurface)
                                Icon(Icons.Default.CalendarToday, contentDescription = "Select Check Date", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (colName.isNotBlank()) {
                                val limit = maxPointsInput.toDoubleOrNull() ?: 100.0
                                viewModel.createGradingSheet(
                                    name = colName.trim(),
                                    maxPoints = limit,
                                    examDate = examDate,
                                    checkDate = checkDate,
                                    filterId = selectedFilterId
                                )
                                showCreateSheetDialog = false
                                colName = ""
                                maxPointsInput = "100.0"
                                Toast.makeText(context, R.string.gradebook_toast_column_created, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = colName.isNotBlank()
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateSheetDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )

            // Dynamic date pickers
            if (showExamDatePicker) {
                WheelDatePickerDialog(
                    initialDateMillis = examDate,
                    onDismiss = { showExamDatePicker = false },
                    onConfirm = { selectedMillis ->
                        examDate = selectedMillis
                        showExamDatePicker = false
                    }
                )
            }

            if (showCheckDatePicker) {
                WheelDatePickerDialog(
                    initialDateMillis = checkDate,
                    onDismiss = { showCheckDatePicker = false },
                    onConfirm = { selectedMillis ->
                        checkDate = selectedMillis
                        showCheckDatePicker = false
                    }
                )
            }
        }
    }
}