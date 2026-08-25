package dev.soloistdev.studenttracker.ui

import android.widget.Toast
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
import androidx.compose.material.icons.filled.*
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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf

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
    val terms by viewModel.terms.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedTermId by viewModel.selectedTermId.collectAsState()
    val visibleColumns by viewModel.visibleColumns.collectAsState()
    val grades by viewModel.grades.collectAsState()
    val classAverage by viewModel.classAverage.collectAsState()
    val assignedWeight by viewModel.assignedWeight.collectAsState()

    var showGradesView by remember { mutableStateOf(false) }
    var showTermEditor by remember { mutableStateOf(false) }
    var showCategoryEditor by remember { mutableStateOf(false) }
    var expandedGradeStudentId by remember { mutableIntStateOf(-1) }

    // Sheet currently open in the edit dialog
    var editingColumn by remember { mutableStateOf<AssessmentColumnEntity?>(null) }

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
                        text = when {
                            activeColumn != null -> activeColumn!!.name
                            showGradesView -> "Running Grades"
                            else -> stringResource(R.string.menu_gradebook_matrix)
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            activeColumn != null -> activeColumn = null // Returns back to master view
                            showGradesView -> showGradesView = false
                            else -> onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (activeColumn == null && !showGradesView) {
                        IconButton(onClick = { showGradesView = true }) {
                            Icon(Icons.Default.Leaderboard, contentDescription = "Running grades", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { showCategoryEditor = true }) {
                            Icon(Icons.Default.PieChart, contentDescription = "Category weights", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { showTermEditor = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Grading periods", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
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
        if (showGradesView && activeColumn == null) {
            // ==========================================
            // VIEW 3: RUNNING GRADE PER STUDENT
            // ==========================================
            GradesRoster(
                paddingValues = paddingValues,
                students = students,
                grades = grades,
                terms = terms,
                selectedTermId = selectedTermId,
                classAverage = classAverage,
                expandedStudentId = expandedGradeStudentId,
                onToggleExpand = { id -> expandedGradeStudentId = if (expandedGradeStudentId == id) -1 else id },
                onSelectTerm = { viewModel.selectTerm(it) }
            )
        } else if (activeColumn == null) {
            // ==========================================
            // VIEW 1: MASTER LIST OF GRADING SHEETS
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                TermFilterRow(
                    terms = terms,
                    selectedTermId = selectedTermId,
                    onSelectTerm = { viewModel.selectTerm(it) }
                )

                Text(
                    text = "Roster Grading Sheets",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                if (visibleColumns.isEmpty()) {
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
                        items(visibleColumns) { col ->
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
                                            text = "Max Threshold: ${col.maxPoints} pts" + (categories.find { it.id == col.categoryId }?.let { " • ${it.name}" } ?: ""),
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Exam: ${displayDateSdf.format(Date(col.examDate))} • Checked: ${displayDateSdf.format(Date(col.checkDate))}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        // Surfaced on the card so the period is visible while
                                        // browsing across all terms
                                        terms.find { it.id == col.termId }?.let { term ->
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = term.name,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    IconButton(onClick = { editingColumn = col }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit sheet", tint = MaterialTheme.colorScheme.primary)
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

        editingColumn?.let { target ->
            EditSheetDialog(
                column = target,
                terms = terms,
                categories = categories,
                onSave = { updated ->
                    viewModel.updateColumn(updated)
                    editingColumn = null
                },
                onDismiss = { editingColumn = null }
            )
        }

        if (showTermEditor) {
            TermEditorDialog(
                terms = terms,
                onSave = { viewModel.saveTerm(it) },
                onDelete = { viewModel.deleteTerm(it) },
                onSetActive = { viewModel.setActiveTerm(it) },
                onDismiss = { showTermEditor = false }
            )
        }

        if (showCategoryEditor) {
            CategoryEditorDialog(
                categories = categories,
                assignedWeight = assignedWeight,
                onSave = { viewModel.saveCategory(it) },
                onDelete = { viewModel.deleteCategory(it) },
                onDismiss = { showCategoryEditor = false }
            )
        }

        // CREATE DISPATCH GRADING SHEET DIALOG (Attendance layout model)
        if (showCreateSheetDialog) {
            var colName by remember { mutableStateOf("") }
            var maxPointsInput by remember { mutableStateOf("100.0") }

            var selectedFilterId by remember { mutableIntStateOf(0) } // 0 indicates "All active students"
            var dropdownExpanded by remember { mutableStateOf(false) }

            // Defaults to the period currently in view, so the common case needs no extra taps
            var selectedTermForSheet by remember { mutableIntStateOf(selectedTermId) }
            var selectedCategoryForSheet by remember { mutableIntStateOf(0) }

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

                        if (terms.isNotEmpty()) {
                            Text(
                                text = "Grading period",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = selectedTermForSheet == 0,
                                    onClick = { selectedTermForSheet = 0 },
                                    label = { Text("None") }
                                )
                                terms.forEach { term ->
                                    FilterChip(
                                        selected = selectedTermForSheet == term.id,
                                        onClick = { selectedTermForSheet = term.id },
                                        label = { Text(term.name) }
                                    )
                                }
                            }
                        }

                        if (categories.isNotEmpty()) {
                            Text(
                                text = "Weighted category",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = selectedCategoryForSheet == 0,
                                    onClick = { selectedCategoryForSheet = 0 },
                                    label = { Text("Uncategorised") }
                                )
                                categories.forEach { category ->
                                    FilterChip(
                                        selected = selectedCategoryForSheet == category.id,
                                        onClick = { selectedCategoryForSheet = category.id },
                                        label = { Text("${category.name} ${String.format(Locale.US, "%.0f", category.weight)}%") }
                                    )
                                }
                            }
                        }

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
                                    filterId = selectedFilterId,
                                    termId = selectedTermForSheet,
                                    categoryId = selectedCategoryForSheet
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
@Composable
private fun TermFilterRow(
    terms: List<GradingTermEntity>,
    selectedTermId: Int,
    onSelectTerm: (Int) -> Unit
) {
    if (terms.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedTermId == 0,
            onClick = { onSelectTerm(0) },
            label = { Text("All terms") }
        )
        terms.forEach { term ->
            FilterChip(
                selected = selectedTermId == term.id,
                onClick = { onSelectTerm(term.id) },
                label = { Text(term.name) },
                leadingIcon = if (term.isActive) {
                    { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp)) }
                } else null
            )
        }
    }
}

@Composable
private fun gradeColor(percent: Double?): androidx.compose.ui.graphics.Color = when {
    percent == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    percent >= 75.0 -> MaterialTheme.colorScheme.primary
    percent >= 60.0 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

/**
 * Roster of running grades, with a tap-to-expand breakdown of how each was reached. The
 * breakdown matters because a weighted grade is otherwise unauditable by the teacher.
 */
@Composable
private fun GradesRoster(
    paddingValues: PaddingValues,
    students: List<StudentEntity>,
    grades: Map<Int, GradeCalculator.StudentGrade>,
    terms: List<GradingTermEntity>,
    selectedTermId: Int,
    classAverage: Double?,
    expandedStudentId: Int,
    onToggleExpand: (Int) -> Unit,
    onSelectTerm: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        TermFilterRow(terms = terms, selectedTermId = selectedTermId, onSelectTerm = onSelectTerm)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Class average",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = classAverage?.let { String.format(Locale.US, "%.1f%%", it) } ?: "--",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = "${grades.values.count { it.gradedCount > 0 }} of ${students.size} graded",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(students) { student ->
                val grade = grades[student.id]
                val isExpanded = expandedStudentId == student.id

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleExpand(student.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${student.lastName}, ${student.firstName}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (grade == null || grade.gradedCount == 0) {
                                        "No graded work yet"
                                    } else {
                                        val mode = if (grade.isWeighted) "weighted" else "total points"
                                        "${grade.gradedCount} graded - $mode"
                                    },
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            Text(
                                text = grade?.display ?: "--",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = gradeColor(grade?.percent)
                            )
                        }

                        if (isExpanded && grade != null && grade.gradedCount > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(8.dp))

                            grade.breakdown.filter { it.gradedCount > 0 }.forEach { bucket ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (grade.isWeighted && bucket.weight > 0.0) {
                                            "${bucket.categoryName} (${String.format(Locale.US, "%.0f", bucket.weight)}%)"
                                        } else {
                                            bucket.categoryName
                                        },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${String.format(Locale.US, "%.0f", bucket.earned)}/${String.format(Locale.US, "%.0f", bucket.possible)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (grade.isWeighted && grade.breakdown.any { it.gradedCount > 0 && it.weight <= 0.0 }) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Buckets with no weight are listed but excluded from the weighted average.",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TermEditorDialog(
    terms: List<GradingTermEntity>,
    onSave: (GradingTermEntity) -> Unit,
    onDelete: (Int) -> Unit,
    onSetActive: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    val sdf = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grading Periods", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Assessments belong to a period, so a report shows the grade for that period rather than the whole year. The starred period is where new assessments land.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                terms.forEach { term ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (term.isActive) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(term.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    text = "${sdf.format(Date(term.startDate))} - ${sdf.format(Date(term.endDate))}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            IconButton(onClick = { onSetActive(term.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    if (term.isActive) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Set active",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { onDelete(term.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New period name") },
                    placeholder = { Text("Quarter 1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        // Defaults to a quarter starting today; the dates are advisory, the
                        // grouping is what the grade calculation actually uses.
                        val start = System.currentTimeMillis()
                        val end = Calendar.getInstance().apply {
                            timeInMillis = start
                            add(Calendar.MONTH, 3)
                        }.timeInMillis

                        onSave(
                            GradingTermEntity(
                                name = newName.trim(),
                                startDate = start,
                                endDate = end,
                                isActive = terms.isEmpty()
                            )
                        )
                        newName = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add period")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
private fun CategoryEditorDialog(
    categories: List<AssessmentCategoryEntity>,
    assignedWeight: Double,
    onSave: (AssessmentCategoryEntity) -> Unit,
    onDelete: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var newWeight by remember { mutableStateOf("") }

    val remaining = 100.0 - assignedWeight
    val overAllocated = assignedWeight > 100.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Category Weights", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Give each kind of work a share of the final grade. Leave this empty and the gradebook simply totals every point instead.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )

                categories.forEach { category ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = category.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = String.format(Locale.US, "%.0f%%", category.weight),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = { onDelete(category.id) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                if (categories.isNotEmpty()) {
                    Text(
                        text = when {
                            overAllocated -> "Allocated ${String.format(Locale.US, "%.0f", assignedWeight)}% - over 100%"
                            remaining > 0.0 -> "Allocated ${String.format(Locale.US, "%.0f", assignedWeight)}%. The remaining ${String.format(Locale.US, "%.0f", remaining)}% covers uncategorised work."
                            else -> "Allocated 100%"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (overAllocated) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Category name") },
                    placeholder = { Text("Quizzes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newWeight,
                    onValueChange = { input -> newWeight = input.filter { it.isDigit() || it == '.' } },
                    label = { Text("Weight %") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    enabled = newName.isNotBlank() && newWeight.toDoubleOrNull() != null,
                    onClick = {
                        onSave(
                            AssessmentCategoryEntity(
                                name = newName.trim(),
                                weight = newWeight.toDoubleOrNull() ?: 0.0
                            )
                        )
                        newName = ""
                        newWeight = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add category")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Done") }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

/**
 * Edits an existing grading sheet. Moving it to another period is the point of this dialog:
 * the running grade is scoped by term, so a sheet filed under the wrong one silently skews
 * that period until it is moved.
 */
@Composable
private fun EditSheetDialog(
    column: AssessmentColumnEntity,
    terms: List<GradingTermEntity>,
    categories: List<AssessmentCategoryEntity>,
    onSave: (AssessmentColumnEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(column.id) { mutableStateOf(column.name) }
    var maxPointsInput by remember(column.id) { mutableStateOf(column.maxPoints.toString()) }
    var termId by remember(column.id) { mutableIntStateOf(column.termId) }
    var categoryId by remember(column.id) { mutableIntStateOf(column.categoryId) }

    val parsedMax = maxPointsInput.toDoubleOrNull()
    val isValid = name.isNotBlank() && parsedMax != null && parsedMax > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Grading Sheet", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.gradebook_column_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = maxPointsInput,
                    onValueChange = { maxPointsInput = it },
                    label = { Text(stringResource(R.string.gradebook_max_points_label)) },
                    singleLine = true,
                    isError = maxPointsInput.isNotBlank() && (parsedMax == null || parsedMax <= 0.0),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Grading period",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (terms.isEmpty()) {
                    Text(
                        text = "No grading periods defined yet. Add one from the calendar icon on the gradebook.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = termId == 0,
                            onClick = { termId = 0 },
                            label = { Text("None") }
                        )
                        terms.forEach { term ->
                            FilterChip(
                                selected = termId == term.id,
                                onClick = { termId = term.id },
                                label = { Text(term.name) }
                            )
                        }
                    }
                }

                if (categories.isNotEmpty()) {
                    Text(
                        text = "Weighted category",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = categoryId == 0,
                            onClick = { categoryId = 0 },
                            label = { Text("Uncategorised") }
                        )
                        categories.forEach { category ->
                            FilterChip(
                                selected = categoryId == category.id,
                                onClick = { categoryId = category.id },
                                label = { Text("${category.name} ${String.format(Locale.US, "%.0f", category.weight)}%") }
                            )
                        }
                    }
                }

                if (parsedMax != null && parsedMax != column.maxPoints) {
                    Text(
                        text = "Changing the maximum points recalculates every grade already recorded on this sheet.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                        lineHeight = 15.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = isValid,
                onClick = {
                    onSave(
                        column.copy(
                            name = name.trim(),
                            maxPoints = parsedMax ?: column.maxPoints,
                            termId = termId,
                            categoryId = categoryId
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp)
    )
}
