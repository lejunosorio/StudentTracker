package dev.soloistdev.studenttracker.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll // Resolved: Explicit verticalScroll import
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.soloistdev.studenttracker.data.FormTemplateEntity
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.ui.StudentUiState
import org.json.JSONObject
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ViewAllScreen(
    onAddStudent: (Int) -> Unit,
    onStudentClick: (Int) -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBiometrics: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenAttendanceWithArgs: (Int, Long) -> Unit,
    viewModel: StudentListViewModel = viewModel()
) {
    val students by viewModel.students.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()
    val pinnedFilters by viewModel.pinnedFilters.collectAsState()
    val availableTemplates by viewModel.availableTemplates.collectAsState()

    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedStudentIds by viewModel.selectedStudentIds.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showBulkDeleteConfirmDialog by remember { mutableStateOf(false) }

    var showCreateAttendanceDialog by remember { mutableStateOf(false) }
    var attendanceRecordName by remember { mutableStateOf("") }
    var startDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var endDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val isDateRangeInvalid = startDateMillis > endDateMillis

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                drawerTonalElevation = 4.dp,
                modifier = Modifier.width(280.dp)
            ) {
                Column(modifier = Modifier.fillMaxHeight()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, top = 36.dp, bottom = 16.dp)
                    ) {
                        Text("Proctor Portal", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Springfield High School", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    val drawerItemColors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.People, contentDescription = null) },
                        label = { Text("Student Directory") },
                        selected = true,
                        onClick = { scope.launch { drawerState.close() } },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Build, contentDescription = null) },
                        label = { Text("Template Manager") },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                onOpenTemplates()
                            }
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.EventAvailable, contentDescription = null) },
                        label = { Text("Attendance System") },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                onOpenAttendance()
                            }
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Bookmarks, contentDescription = null) },
                        label = { Text("Saved Filters") },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                onOpenMap()
                            }
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        label = { Text("Backup & Sync (JSON/CSV)") },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                onOpenSync()
                            }
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )

                    Spacer(modifier = Modifier.weight(1f))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        label = { Text("Recycle Bin (Soft Deleted)") },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                onOpenRecycleBin()
                            }
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Security, contentDescription = null) },
                        label = { Text("Biometrics & Privacy") },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                onOpenBiometrics()
                            }
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("App Settings") },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                onOpenSettings()
                            }
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (isSelectionMode) {
                    TopAppBar(
                        title = { Text("Selected: ${selectedStudentIds.size}", fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel Selection")
                            }
                        },
                        actions = {
                            IconButton(onClick = { showCreateAttendanceDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.EventAvailable,
                                    contentDescription = "Create Attendance Record",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { showBulkDeleteConfirmDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Selected",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                } else {
                    CenterAlignedTopAppBar(
                        title = { Text("Student Directory", fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.loadStudents() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                if (!isSelectionMode) {
                    FloatingActionButton(
                        onClick = { onAddStudent(-1) },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Member")
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.People, contentDescription = "Students") },
                        label = { Text("Students") },
                        selected = true,
                        onClick = {}
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Bookmarks, contentDescription = "Filters") },
                        label = { Text("Filters") },
                        selected = false,
                        onClick = onOpenMap
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = false,
                        onClick = onOpenSettings
                    )
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Search Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = { Text("Search names...", color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSecondaryContainer) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = { showFilterSheet = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (activeFilter != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (activeFilter != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }

                    IconButton(
                        onClick = { showSortSheet = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                    }
                }

                // Horizontally Scrollable Workbench Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = activeFilter == null,
                        onClick = { viewModel.clearActiveFilter() },
                        label = { Text("All") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = Color.Transparent,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    pinnedFilters.forEach { pinnedFilter ->
                        val labelText = if (pinnedFilter.comparison == "In between") {
                            "${pinnedFilter.field.replace("_", " ")}: ${pinnedFilter.value1} - ${pinnedFilter.value2}"
                        } else {
                            "${pinnedFilter.field.replace("_", " ")} ${pinnedFilter.comparison} ${pinnedFilter.value1}"
                        }

                        InputChip(
                            selected = activeFilter?.id == pinnedFilter.id,
                            onClick = { viewModel.selectPinnedFilter(pinnedFilter) },
                            label = { Text(labelText) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { viewModel.removePinnedFilter(pinnedFilter) },
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(12.dp))
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

                // Empty state triggers
                if (students.isEmpty() && searchQuery.isEmpty() && activeFilter == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Directory is empty.\nTap '+' to add a member.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                } else {
                    // Student List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(
                            items = students,
                            key = { studentState -> studentState.student.id }
                        ) { studentState ->
                            val currentOnStudentClick = rememberUpdatedState(onStudentClick)
                            val currentOnAddStudent = rememberUpdatedState(onAddStudent)
                            val isStudentSelected = selectedStudentIds.contains(studentState.student.id)

                            var showDeleteConfirmDialog by remember { mutableStateOf(false) }

                            @Suppress("DEPRECATION")
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { dismissValue ->
                                    when (dismissValue) {
                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            currentOnAddStudent.value(studentState.student.id)
                                            false
                                        }
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            showDeleteConfirmDialog = true
                                            false
                                        }
                                        SwipeToDismissBoxValue.Settled -> false
                                    }
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                modifier = Modifier.animateItem().zIndex(if (isStudentSelected) 10f else 0f),
                                enableDismissFromStartToEnd = !isSelectionMode,
                                enableDismissFromEndToStart = !isSelectionMode,
                                backgroundContent = {
                                    val backgroundColor = when (dismissState.dismissDirection) {
                                        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                        else -> Color.Transparent
                                    }
                                    val alignment = when (dismissState.dismissDirection) {
                                        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                        else -> Alignment.Center
                                    }
                                    val iconVector = when (dismissState.dismissDirection) {
                                        SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Edit
                                        SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                                        else -> Icons.Default.Delete
                                    }
                                    val iconTint = when (dismissState.dismissDirection) {
                                        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.onPrimaryContainer
                                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.onErrorContainer
                                        else -> Color.Transparent
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(backgroundColor),
                                        contentAlignment = alignment
                                    ) {
                                        Icon(
                                            imageVector = iconVector,
                                            contentDescription = null,
                                            tint = iconTint,
                                            modifier = Modifier.padding(horizontal = 24.dp)
                                        )
                                    }
                                },
                                content = {
                                    StudentCard(
                                        uiState = studentState,
                                        isSelected = isStudentSelected,
                                        onClick = {
                                            if (isSelectionMode) {
                                                viewModel.toggleStudentSelection(studentState.student.id)
                                            } else {
                                                currentOnStudentClick.value(studentState.student.id)
                                            }
                                        },
                                        onLongClick = {
                                            viewModel.toggleStudentSelection(studentState.student.id)
                                        }
                                    )
                                }
                            )

                            if (showDeleteConfirmDialog) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteConfirmDialog = false },
                                    title = { Text("Delete Member?", fontWeight = FontWeight.Bold) },
                                    text = { Text("Are you sure you want to move ${studentState.student.firstName} ${studentState.student.lastName} to the Recycle Bin?") },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                showDeleteConfirmDialog = false
                                                viewModel.softDeleteStudent(studentState.student.id)
                                                Toast.makeText(context, "${studentState.student.firstName} moved to Recycle Bin.", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Text("Delete")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDeleteConfirmDialog = false }) {
                                            Text("Cancel")
                                        }
                                    },
                                    shape = RoundedCornerShape(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showBulkDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showBulkDeleteConfirmDialog = false },
                title = { Text("Delete Selected Members?", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to move these ${selectedStudentIds.size} selected members to the Recycle Bin?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showBulkDeleteConfirmDialog = false
                            viewModel.deleteSelectedStudents()
                            Toast.makeText(context, "Selected members moved to Recycle Bin.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBulkDeleteConfirmDialog = false }) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

        // Decoupled Bottom Sheet Overlays
        if (showFilterSheet) {
            FilterBottomSheet(
                activeFilter = activeFilter,
                availableTemplates = availableTemplates,
                onApplyFilter = { viewModel.applyFilter(it) },
                onResetFilter = { viewModel.clearFilter() },
                onDismiss = { showFilterSheet = false }
            )
        }

        if (showSortSheet) {
            SortBottomSheet(
                sortOrder = sortOrder,
                onSortSelected = { viewModel.updateSortOrder(it) },
                onDismiss = { showSortSheet = false }
            )
        }

        if (showCreateAttendanceDialog) {
            val m3TextFieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AlertDialog(
                onDismissRequest = {
                    showCreateAttendanceDialog = false
                    attendanceRecordName = ""
                    startDateMillis = System.currentTimeMillis()
                    endDateMillis = System.currentTimeMillis()
                },
                title = { Text("New Attendance Record", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()) // Resolved: Scrollable layout prevents visual cutoffs [1]
                            .imePadding(), // Resolved: Dynamic padding adjustment based on system IME/Keyboard [1]
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = attendanceRecordName,
                            onValueChange = { attendanceRecordName = it },
                            label = { Text("Record Name *") },
                            colors = m3TextFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Select Date Range *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                        val sdf = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }

                        OutlinedButton(
                            onClick = { showStartPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = if (isDateRangeInvalid) BorderStroke(1.5.dp, MaterialTheme.colorScheme.error) else ButtonDefaults.outlinedButtonBorder(enabled = true)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Start: ${sdf.format(Date(startDateMillis))}", color = MaterialTheme.colorScheme.onSurface)
                                Icon(Icons.Default.CalendarToday, contentDescription = "Select Start Date", tint = if (isDateRangeInvalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            }
                        }

                        OutlinedButton(
                            onClick = { showEndPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = if (isDateRangeInvalid) BorderStroke(1.5.dp, MaterialTheme.colorScheme.error) else ButtonDefaults.outlinedButtonBorder(enabled = true)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("End: ${sdf.format(Date(endDateMillis))}", color = MaterialTheme.colorScheme.onSurface)
                                Icon(Icons.Default.CalendarToday, contentDescription = "Select End Date", tint = if (isDateRangeInvalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            }
                        }

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
                            if (attendanceRecordName.isNotBlank() && !isDateRangeInvalid) {
                                viewModel.createManualAttendanceRecord(
                                    name = attendanceRecordName,
                                    selectedIds = selectedStudentIds.toList(),
                                    startDateMillis = startDateMillis,
                                    endDateMillis = endDateMillis
                                ) { recordId, normalizedStartMillis ->
                                    showCreateAttendanceDialog = false
                                    attendanceRecordName = ""
                                    startDateMillis = System.currentTimeMillis()
                                    endDateMillis = System.currentTimeMillis()
                                    onOpenAttendanceWithArgs(recordId, normalizedStartMillis)
                                }
                            }
                        },
                        enabled = attendanceRecordName.isNotBlank() && !isDateRangeInvalid,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showCreateAttendanceDialog = false
                            attendanceRecordName = ""
                            startDateMillis = System.currentTimeMillis()
                            endDateMillis = System.currentTimeMillis()
                        }
                    ) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

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
}