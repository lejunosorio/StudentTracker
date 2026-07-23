package dev.soloistdev.studenttracker.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.soloistdev.studenttracker.data.AttendanceRecordEntity
import dev.soloistdev.studenttracker.data.SavedFilterEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    onBack: () -> Unit,
    onRedirectToFilters: () -> Unit,
    viewModel: AttendanceViewModel = viewModel()
) {
    val records by viewModel.records.collectAsState()
    val savedFilters by viewModel.savedFilters.collectAsState()
    val context = LocalContext.current

    // Screen navigation states: 0 = Records List, 1 = Dates List, 2 = Daily Sheet
    var currentSubScreen by remember { mutableIntStateOf(0) }
    var selectedRecord by remember { mutableStateOf<AttendanceRecordEntity?>(null) }
    var selectedDateMillis by remember { mutableLongStateOf(0L) }

    var showCreateDialog by remember { mutableStateOf(false) }

    val backHandler = {
        when (currentSubScreen) {
            2 -> currentSubScreen = 1
            1 -> currentSubScreen = 0
            else -> onBack()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when (currentSubScreen) {
                            2 -> {
                                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                                sdf.format(Date(selectedDateMillis))
                            }
                            1 -> selectedRecord?.name ?: "Attendance Sheet"
                            else -> "Attendance Manager"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = backHandler) {
                        // CORRECTED: AutoMirrored icon API update [2.1]
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (currentSubScreen == 2 && selectedRecord != null) {
                        IconButton(onClick = {
                            viewModel.exportSheetToCsv(context, selectedRecord!!, selectedDateMillis)
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Export CSV")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (currentSubScreen == 0) {
                FloatingActionButton(
                    onClick = {
                        if (savedFilters.isEmpty()) {
                            Toast.makeText(context, "Please create a Saved Filter first.", Toast.LENGTH_LONG).show()
                            onRedirectToFilters()
                        } else {
                            showCreateDialog = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Record")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentSubScreen) {
                0 -> {
                    if (records.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No attendance records.\nTap '+' to create a log.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(records) { record ->
                                val filterName = savedFilters.find { it.id == record.savedFilterId }?.filterName ?: "Unknown Filter"
                                val sdf = SimpleDateFormat("MMM dd", Locale.US)
                                val rangeStr = "${sdf.format(Date(record.startDate))} - ${sdf.format(Date(record.endDate))}"

                                var showDeleteDialog by remember { mutableStateOf(false) }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .clickable {
                                            selectedRecord = record
                                            viewModel.loadRecordLogs(record.id) // Trigger background logs load [1]
                                            currentSubScreen = 1
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(record.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            Text("Filter: $filterName • Date: $rangeStr", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                        }
                                        IconButton(onClick = { showDeleteDialog = true }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                if (showDeleteDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showDeleteDialog = false },
                                        title = { Text("Delete Attendance Record?") },
                                        text = { Text("Permanently delete '${record.name}'? This clears all saved attendance logs associated with this event.") },
                                        confirmButton = {
                                            Button(
                                                onClick = {
                                                    showDeleteDialog = false
                                                    viewModel.deleteRecord(record.id)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                            ) { Text("Delete") }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                                        },
                                        shape = RoundedCornerShape(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // ================= SUB-SCREEN 1: DATES LIST (BADGES INTEGRATED) =================
                    selectedRecord?.let { record ->
                        val dates = remember(record) { viewModel.generateDateList(record.startDate, record.endDate) }
                        val recordLogs by viewModel.recordLogs.collectAsState() // Observe dataset logs [1]

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(dates) { dateMillis ->
                                val sdf = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.US)

                                // Dynamically calculate attendance parameters [1]
                                val dayLogs = remember(recordLogs, dateMillis) {
                                    recordLogs.filter { it.dateMillis == dateMillis }
                                }
                                val dayPresent = dayLogs.count { it.status == "PRESENT" }
                                val dayAbsent = dayLogs.count { it.status == "ABSENT" }
                                val dayUnmarked = dayLogs.count { it.status == "NOT_SET" }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                        .clickable {
                                            selectedDateMillis = dateMillis
                                            viewModel.loadSheetData(record, dateMillis)
                                            currentSubScreen = 2
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(sdf.format(Date(dateMillis)), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            // Real-time stats Row display [1]
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Present: $dayPresent",
                                                    color = Color(0xFF4CAF50), // Standard Green
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Absent: $dayAbsent",
                                                    color = Color(0xFFE53935), // Standard Red
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Unmarked: $dayUnmarked",
                                                    color = Color(0xFFFBC02D), // Standard Yellow
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Open")
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    selectedRecord?.let { record ->
                        DailyRosterSheet(
                            record = record,
                            dateMillis = selectedDateMillis,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }

        if (showCreateDialog) {
            RecordCreateForm(
                savedFilters = savedFilters,
                onDismiss = { showCreateDialog = false },
                onSave = { name, filterId, start, end ->
                    viewModel.createRecord(name, filterId, start, end)
                    showCreateDialog = false
                    Toast.makeText(context, "Attendance Record Created!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun DailyRosterSheet(
    record: AttendanceRecordEntity,
    dateMillis: Long,
    viewModel: AttendanceViewModel
) {
    val roster by viewModel.currentRoster.collectAsState()
    val logs by viewModel.currentLogs.collectAsState()

    var activeTab by remember { mutableStateOf("ALL") }

    val unmarkedCount = roster.count { s -> logs.find { it.studentId == s.id }?.status == "NOT_SET" }
    val presentCount = roster.count { s -> logs.find { it.studentId == s.id }?.status == "PRESENT" }
    val absentCount = roster.count { s -> logs.find { it.studentId == s.id }?.status == "ABSENT" }
    val excusedCount = roster.count { s -> logs.find { it.studentId == s.id }?.status == "EXCUSED" }
    val removedCount = roster.count { s -> logs.find { it.studentId == s.id }?.status == "REMOVED" }

    val filteredRoster = remember(roster, logs, activeTab) {
        when (activeTab) {
            "PRESENT" -> roster.filter { s -> logs.find { it.studentId == s.id }?.status == "PRESENT" }
            "ABSENT" -> roster.filter { s -> logs.find { it.studentId == s.id }?.status == "ABSENT" }
            "EXCUSED" -> roster.filter { s -> logs.find { it.studentId == s.id }?.status == "EXCUSED" }
            "REMOVED" -> roster.filter { s -> logs.find { it.studentId == s.id }?.status == "REMOVED" }
            else -> roster
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Completion Overview", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Present: $presentCount | Absent: $absentCount | Unmarked: $unmarkedCount", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { viewModel.markAllUnmarkedPresent(record.id, dateMillis) },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = "Mark Unmarked Present", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    IconButton(
                        onClick = { viewModel.resetAllMarks(record.id, dateMillis) },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Icon(Icons.Default.SettingsBackupRestore, contentDescription = "Reset All", tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabChip(
                label = "All ($unmarkedCount)",
                isSelected = activeTab == "ALL",
                borderColor = Color(0xFFFBC02D),
                onClick = { activeTab = "ALL" }
            )
            TabChip(
                label = "Pres ($presentCount)",
                isSelected = activeTab == "PRESENT",
                borderColor = Color(0xFF4CAF50),
                onClick = { activeTab = "PRESENT" }
            )
            TabChip(
                label = "Abs ($absentCount)",
                isSelected = activeTab == "ABSENT",
                borderColor = Color(0xFFE53935),
                onClick = { activeTab = "ABSENT" }
            )
            TabChip(
                label = "Exc ($excusedCount)",
                isSelected = activeTab == "EXCUSED",
                borderColor = Color(0xFFFB8C00),
                onClick = { activeTab = "EXCUSED" }
            )
            TabChip(
                label = "Rem ($removedCount)",
                isSelected = activeTab == "REMOVED",
                borderColor = Color(0xFF757575),
                onClick = { activeTab = "REMOVED" }
            )
        }

        if (filteredRoster.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No roster members in this selection.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredRoster) { student ->
                    val log = logs.find { it.studentId == student.id }
                    val currentStatus = log?.status ?: "NOT_SET"

                    val cardBorderColor = when (currentStatus) {
                        "PRESENT" -> Color(0xFF4CAF50)
                        "ABSENT" -> Color(0xFFE53935)
                        "EXCUSED" -> Color(0xFFFB8C00)
                        "REMOVED" -> Color(0xFF757575)
                        else -> Color(0xFFFBC02D)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        border = BorderStroke(1.5.dp, cardBorderColor),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${student.lastName}, ${student.firstName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )

                            var statusExpanded by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(
                                    onClick = { statusExpanded = true },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = cardBorderColor),
                                    border = BorderStroke(1.dp, cardBorderColor),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text(
                                        text = when (currentStatus) {
                                            "PRESENT" -> "Present"
                                            "ABSENT" -> "Absent"
                                            "EXCUSED" -> "Excused"
                                            "REMOVED" -> "Removed"
                                            else -> "Not Set"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                DropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                                    listOf(
                                        "NOT_SET" to "Not Set",
                                        "PRESENT" to "Present",
                                        "ABSENT" to "Absent",
                                        "EXCUSED" to "Excused",
                                        "REMOVED" to "Removed"
                                    ).forEach { (statusVal, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            onClick = {
                                                viewModel.updateStatus(record.id, dateMillis, student.id, statusVal)
                                                statusExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.TabChip(
    label: String,
    isSelected: Boolean,
    borderColor: Color,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = borderColor,
            selectedBorderColor = borderColor,
            borderWidth = 1.5.dp,
            selectedBorderWidth = 2.dp
        ),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = borderColor.copy(alpha = 0.2f),
            selectedLabelColor = Color.Black,
            containerColor = Color.Transparent,
            labelColor = Color.Gray
        ),
        modifier = Modifier.weight(1f)
    )
}

// ... [Locate RecordCreateForm inside AttendanceScreen.kt and replace it]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordCreateForm(
    savedFilters: List<SavedFilterEntity>,
    onDismiss: () -> Unit,
    onSave: (String, Int, Long, Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedFilterId by remember { mutableIntStateOf(savedFilters.firstOrNull()?.id ?: 0) }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    var startDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var endDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Date Range boundary validation check [1, 2]
    val isDateRangeInvalid = startDateMillis > endDateMillis

    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Attendance Record", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Record Name *") },
                    modifier = Modifier.fillMaxWidth()
                )

                var filterExpanded by remember { mutableStateOf(false) }
                val selectedFilterName = savedFilters.find { it.id == selectedFilterId }?.filterName ?: "Select Filter"

                Box {
                    OutlinedTextField(
                        value = selectedFilterName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Roster Filter *") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.clickable { filterExpanded = true }) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                        savedFilters.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.filterName) },
                                onClick = {
                                    selectedFilterId = option.id
                                    filterExpanded = false
                                }
                            )
                        }
                    }
                }

                // Start Date selection (Highlights with error border if date range is invalid) [1, 2]
                OutlinedButton(
                    onClick = { showStartPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = if (isDateRangeInvalid) BorderStroke(1.5.dp, MaterialTheme.colorScheme.error) else ButtonDefaults.outlinedButtonBorder
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Start: ${sdf.format(Date(startDateMillis))}", color = MaterialTheme.colorScheme.onSurface)
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = if (isDateRangeInvalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                }

                // End Date selection (Highlights with error border if date range is invalid) [1, 2]
                OutlinedButton(
                    onClick = { showEndPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = if (isDateRangeInvalid) BorderStroke(1.5.dp, MaterialTheme.colorScheme.error) else ButtonDefaults.outlinedButtonBorder
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("End: ${sdf.format(Date(endDateMillis))}", color = MaterialTheme.colorScheme.onSurface)
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = if (isDateRangeInvalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                }

                // Friendly visual validation helper text [1, 2]
                if (isDateRangeInvalid) {
                    Text(
                        text = "Start date must be less than or equal to End date",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && selectedFilterId != 0 && !isDateRangeInvalid) {
                        onSave(name.trim(), selectedFilterId, startDateMillis, endDateMillis)
                    }
                },
                enabled = name.isNotBlank() && !isDateRangeInvalid // Blocks click if range is invalid [2]
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(28.dp)
    )

    // Date Range pickers cleanly supporting past and future dates (SelectableDates removed) [2]
    if (showStartPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { startDateMillis = it }
                    showStartPicker = false
                }) { Text("OK") }
            }
        ) { DatePicker(state = pickerState, showModeToggle = false) }
    }

    if (showEndPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = endDateMillis
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { endDateMillis = it }
                    showEndPicker = false
                }) { Text("OK") }
            }
        ) { DatePicker(state = pickerState, showModeToggle = false) }
    }
}