package dev.soloistdev.studenttracker.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort // AutoMirrored Sort [2.1]
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.soloistdev.studenttracker.data.FormTemplateEntity
import dev.soloistdev.studenttracker.data.StudentEntity
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.foundation.background
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.ui.zIndex

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

    // Dialog controllers with dual date-range state tracking
    var showCreateAttendanceDialog by remember { mutableStateOf(false) }
    var attendanceRecordName by remember { mutableStateOf("") }
    var startDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var endDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val isDateRangeInvalid = startDateMillis > endDateMillis

    val coreFields = listOf("First Name", "Last Name", "Gender", "Birthday", "Address", "Age", "Guardian Name", "Guardian Contact")

    fun getFieldType(field: String, templates: List<FormTemplateEntity>): String {
        return when (field) {
            "First Name", "Last Name", "Home Address", "Guardian Name", "Guardian Contact" -> "TEXT"
            "Gender" -> "GENDER"
            "Age" -> "NUMBER"
            "Birthday" -> "DATE"
            else -> {
                val template = templates.find { it.fieldName == field }
                template?.fieldType ?: "TEXT"
            }
        }
    }

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

                    // Restored: Drawer item for the Attendance System [1]
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
                        icon = { Icon(Icons.Default.Bookmarks, contentDescription = null) },label = { Text("Saved Filters") },
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
            val sharedPrefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
            var activeBadgeField by remember { mutableStateOf(sharedPrefs.getString("card_banner_field", "") ?: "") }

            DisposableEffect(sharedPrefs) {
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "card_banner_field") {
                        activeBadgeField = sharedPrefs.getString("card_banner_field", "") ?: ""
                    }
                }
                sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

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
                        val sdfLabel = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                        val isDate = getFieldType(pinnedFilter.field, availableTemplates) == "DATE"

                        val formattedVal1 = if (isDate) {
                            pinnedFilter.value1.toLongOrNull()?.let { sdfLabel.format(Date(it)) } ?: pinnedFilter.value1
                        } else pinnedFilter.value1

                        val formattedVal2 = if (isDate) {
                            pinnedFilter.value2.toLongOrNull()?.let { sdfLabel.format(Date(it)) } ?: pinnedFilter.value2
                        } else pinnedFilter.value2

                        val labelText = if (pinnedFilter.comparison == "In between") {
                            "${pinnedFilter.field.replace("_", " ")}: $formattedVal1 - $formattedVal2"
                        } else {
                            "${pinnedFilter.field.replace("_", " ")} ${pinnedFilter.comparison} $formattedVal1"
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

                // Empty state trigger
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
                            key = { it.id }
                        ) { student ->
                            val currentOnStudentClick = rememberUpdatedState(onStudentClick)
                            val currentOnAddStudent = rememberUpdatedState(onAddStudent)
                            val isStudentSelected = selectedStudentIds.contains(student.id)

                            var showDeleteConfirmDialog by remember { mutableStateOf(false) }

                            @Suppress("DEPRECATION")
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { dismissValue ->
                                    when (dismissValue) {
                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            currentOnAddStudent.value(student.id)
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
                                        student = student,
                                        isSelected = isStudentSelected,
                                        activeBadgeField = activeBadgeField,
                                        onClick = {
                                            if (isSelectionMode) {
                                                viewModel.toggleStudentSelection(student.id)
                                            } else {
                                                currentOnStudentClick.value(student.id)
                                            }
                                        },
                                        onLongClick = {
                                            viewModel.toggleStudentSelection(student.id)
                                        }
                                    )
                                }
                            )

                            if (showDeleteConfirmDialog) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteConfirmDialog = false },
                                    title = { Text("Delete Member?", fontWeight = FontWeight.Bold) },
                                    text = { Text("Are you sure you want to move ${student.firstName} ${student.lastName} to the Recycle Bin?") },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                showDeleteConfirmDialog = false
                                                viewModel.softDeleteStudent(student.id)
                                                Toast.makeText(context, "${student.firstName} moved to Recycle Bin.", Toast.LENGTH_SHORT).show()
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

        if (showFilterSheet) {
            var tempField by remember { mutableStateOf(activeFilter?.field ?: "Age") }
            var tempComparison by remember { mutableStateOf(activeFilter?.comparison ?: "In between") }

            var tempVal1 by remember { mutableStateOf(activeFilter?.value1 ?: "Female") }
            var tempVal2 by remember { mutableStateOf(activeFilter?.value2 ?: "") }
            var tempIsPinned by remember { mutableStateOf(activeFilter?.isPinned ?: false) }

            var showDatePicker1 by remember { mutableStateOf(false) }
            var showDatePicker2 by remember { mutableStateOf(false) }

            val fieldsList = remember {
                val list = coreFields.toMutableList()
                availableTemplates.forEach { list.add(it.fieldName) }
                list
            }

            val currentSelectedType = getFieldType(tempField, availableTemplates)

            val isRangeMode = tempComparison == "In between"
            val isGenderMode = currentSelectedType == "GENDER"
            val isBirthdayMode = tempField == "Birthday"

            val val1Num = tempVal1.toDoubleOrNull()
            val val2Num = tempVal2.toDoubleOrNull()

            val currentSystemYear = Calendar.getInstance().get(Calendar.YEAR)
            val isFutureYear1 = tempComparison == "birth_year" && (tempVal1.toIntOrNull() ?: 0) > currentSystemYear
            val isFutureYear2 = tempComparison == "birth_month_year" && (tempVal2.toIntOrNull() ?: 0) > currentSystemYear

            val isRangeError = isRangeMode && val1Num != null && val2Num != null && val1Num >= val2Num
            val isValidationError = isRangeError || isFutureYear1 || isFutureYear2

            val monthNames = remember {
                listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
            }

            val chipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = Color.Transparent,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Filter Directory", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                    var fieldExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = fieldExpanded,
                        onExpandedChange = { fieldExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = tempField.replace("_", " "),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Field") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fieldExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        )
                        ExposedDropdownMenu(
                            expanded = fieldExpanded,
                            onDismissRequest = { fieldExpanded = false }
                        ) {
                            fieldsList.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.replace("_", " ")) },
                                    onClick = {
                                        tempField = option
                                        fieldExpanded = false
                                        val newType = getFieldType(option, availableTemplates)
                                        tempComparison = when {
                                            option == "Birthday" -> "exact_birthday"
                                            newType == "NUMBER" -> "In between"
                                            newType == "GENDER" -> "equal"
                                            else -> "contains"
                                        }
                                        tempVal1 = if (newType == "GENDER") "Female" else ""
                                        tempVal2 = ""
                                    }
                                )
                            }
                        }
                    }

                    if (!isGenderMode && !isBirthdayMode) {
                        var compExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = compExpanded,
                            onExpandedChange = { compExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = tempComparison,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Select Comparison") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = compExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                            )
                            ExposedDropdownMenu(
                                expanded = compExpanded,
                                onDismissRequest = { compExpanded = false }
                            ) {
                                val operatorsList = if (currentSelectedType == "NUMBER") {
                                    listOf("equal", "greater than", "less than", "In between")
                                } else {
                                    listOf("contains", "does not contain", "equal", "not equal")
                                }

                                operatorsList.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            tempComparison = option
                                            compExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (isBirthdayMode) {
                        var typeExpanded by remember { mutableStateOf(false) }
                        val birthdayTypes = listOf(
                            "Birth year (YYYY)" to "birth_year",
                            "Birth month (MM)" to "birth_month",
                            "Birth month and Year (MM - YY)" to "birth_month_year",
                            "Exact Birthday (MM/DD/YYYY)" to "exact_birthday"
                        )
                        val selectedTypeName = birthdayTypes.find { it.second == tempComparison }?.first ?: "Exact Birthday (MM/DD/YYYY)"

                        ExposedDropdownMenuBox(
                            expanded = typeExpanded,
                            onExpandedChange = { typeExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedTypeName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Select Birthday Filter Type") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                            )
                            ExposedDropdownMenu(
                                expanded = typeExpanded,
                                onDismissRequest = { typeExpanded = false }
                            ) {
                                birthdayTypes.forEach { (label, value) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            tempComparison = value
                                            typeExpanded = false
                                            tempVal1 = if (value == "birth_month" || value == "birth_month_year") "1" else ""
                                            tempVal2 = ""
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        when (tempComparison) {
                            "birth_year" -> {
                                OutlinedTextField(
                                    value = tempVal1,
                                    onValueChange = { if (it.length <= 4) tempVal1 = it.filter { c -> c.isDigit() } },
                                    label = { Text("Birth Year (YYYY) *") },
                                    isError = isFutureYear1,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (isFutureYear1) {
                                    Text("Year cannot be in the future.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                }
                            }
                            "birth_month" -> {
                                var monthExpanded by remember { mutableStateOf(false) }
                                val monthIdx = (tempVal1.toIntOrNull() ?: 1) - 1
                                val selectedMonthName = monthNames.getOrElse(monthIdx) { "January" }

                                ExposedDropdownMenuBox(
                                    expanded = monthExpanded,
                                    onExpandedChange = { monthExpanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = selectedMonthName,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Select Birth Month *") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded) },
                                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = monthExpanded,
                                        onDismissRequest = { monthExpanded = false }
                                    ) {
                                        monthNames.forEachIndexed { idx, name ->
                                            DropdownMenuItem(
                                                text = { Text(name) },
                                                onClick = {
                                                    tempVal1 = (idx + 1).toString()
                                                    monthExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            "birth_month_year" -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    var monthExpanded by remember { mutableStateOf(false) }
                                    val monthIdx = (tempVal1.toIntOrNull() ?: 1) - 1
                                    val selectedMonthName = monthNames.getOrElse(monthIdx) { "January" }

                                    Box(modifier = Modifier.weight(1f)) {
                                        ExposedDropdownMenuBox(
                                            expanded = monthExpanded,
                                            onExpandedChange = { monthExpanded = it }
                                        ) {
                                            OutlinedTextField(
                                                value = selectedMonthName,
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text("Month *") },
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded) },
                                                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                                            )
                                            ExposedDropdownMenu(
                                                expanded = monthExpanded,
                                                onDismissRequest = { monthExpanded = false }
                                            ) {
                                                monthNames.forEachIndexed { idx, name ->
                                                    DropdownMenuItem(
                                                        text = { Text(name) },
                                                        onClick = {
                                                            tempVal1 = (idx + 1).toString()
                                                            monthExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = tempVal2,
                                        onValueChange = { if (it.length <= 4) tempVal2 = it.filter { c -> c.isDigit() } },
                                        label = { Text("Year (YYYY) *") },
                                        isError = isFutureYear2,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (isFutureYear2) {
                                    Text("Year cannot be in the future.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                }
                            }
                            "exact_birthday" -> {
                                val sdfPicker = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                                val birthday1Formatted = tempVal1.toLongOrNull()?.let { sdfPicker.format(Date(it)) } ?: "Select Birthday Date *"

                                OutlinedButton(
                                    onClick = { showDatePicker1 = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(birthday1Formatted, color = MaterialTheme.colorScheme.onSurface)
                                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    } else if (isGenderMode) {
                        Text("Select Gender *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = tempVal1 == "Female",
                                onClick = { tempVal1 = "Female" },
                                label = { Text("Female") },
                                colors = chipColors
                            )
                            FilterChip(
                                selected = tempVal1 == "Male",
                                onClick = { tempVal1 = "Male" },
                                label = { Text("Male") },
                                colors = chipColors
                            )
                        }
                    } else {
                        if (isRangeMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = tempVal1,
                                    onValueChange = { tempVal1 = it },
                                    label = { Text("Value 1 (Min) *") },
                                    isError = isValidationError,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = tempVal2,
                                    onValueChange = { tempVal2 = it },
                                    label = { Text("Value 2 (Max) *") },
                                    isError = isValidationError,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (isValidationError) {
                                Text(
                                    text = "Value 2 (Max) must be strictly greater than Value 1",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        } else {
                            val isNumeric = currentSelectedType == "NUMBER"
                            OutlinedTextField(
                                value = tempVal1,
                                onValueChange = { tempVal1 = it },
                                label = { Text("Value *") },
                                keyboardOptions = KeyboardOptions(keyboardType = if (isNumeric) KeyboardType.Number else KeyboardType.Text),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pin filter to dashboard", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Switch(
                            checked = tempIsPinned,
                            onCheckedChange = { tempIsPinned = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { viewModel.clearFilter(); showFilterSheet = false }) {
                            Text("Reset")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (!isValidationError) {
                                    viewModel.applyFilter(
                                        FilterState(
                                            field = tempField,
                                            comparison = tempComparison,
                                            value1 = tempVal1,
                                            value2 = tempVal2,
                                            isPinned = tempIsPinned
                                        )
                                    )
                                    showFilterSheet = false
                                }
                            },
                            enabled = !isValidationError,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isValidationError) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary,
                                contentColor = if (isValidationError) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(100.dp)
                        ) {
                            Text("Apply Filter")
                        }
                    }
                }
            }

            if (showDatePicker1) {
                WheelDatePickerDialog(
                    initialDateMillis = tempVal1.toLongOrNull() ?: System.currentTimeMillis(),
                    onDismiss = { showDatePicker1 = false },
                    onConfirm = { selectedMillis ->
                        tempVal1 = selectedMillis.toString()
                        showDatePicker1 = false
                    }
                )
            }


            if (showDatePicker2) {
                WheelDatePickerDialog(
                    initialDateMillis = tempVal2.toLongOrNull() ?: System.currentTimeMillis(),
                    onDismiss = { showDatePicker2 = false },
                    onConfirm = { selectedMillis ->
                        tempVal2 = selectedMillis.toString()
                        showDatePicker2 = false
                    }
                )
            }
        }

        // ================= SPRINT 9 SORT SHEET (Screen 13) =================
        if (showSortSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSortSheet = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Sort List By", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SortOptionItem(
                            label = "Last Name (A - Z)",
                            isSelected = sortOrder == "lastNameAsc",
                            onClick = { viewModel.updateSortOrder("lastNameAsc"); showSortSheet = false }
                        )
                        SortOptionItem(
                            label = "Last Name (Z - A)",
                            isSelected = sortOrder == "lastNameDesc",
                            onClick = { viewModel.updateSortOrder("lastNameDesc"); showSortSheet = false }
                        )
                        SortOptionItem(
                            label = "Age (Youngest First)",
                            isSelected = sortOrder == "ageYoungest",
                            onClick = { viewModel.updateSortOrder("ageYoungest"); showSortSheet = false }
                        )
                        SortOptionItem(
                            label = "Recently Added",
                            isSelected = sortOrder == "recentlyAdded",
                            onClick = { viewModel.updateSortOrder("recentlyAdded"); showSortSheet = false }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // NEW: M3 Naming & Date Range Dialog for Manual Attendance Records [1]
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
                        modifier = Modifier.fillMaxWidth(),
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

                        // Clickable Start Date Button with standard M3 DatePickerDialog calendar grid [1]
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

                        // Clickable End Date Button with standard M3 DatePickerDialog calendar grid [1]
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

        // CORRECTED: Restored with standard M3 DatePickerDialog calendar grid (Wheel picker removed) [1]
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

        // CORRECTED: Restored with standard M3 DatePickerDialog calendar grid (Wheel picker removed) [1]
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



@Composable
fun SortOptionItem(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StudentCard(
    student: StudentEntity,
    isSelected: Boolean,
    activeBadgeField: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
            ) {
                if (isSelected) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                } else {
                    LocalImageLoader(
                        imagePath = student.picturePath,
                        contentDescription = "Student Photo",
                        fallback = {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                val initials = if (student.firstName.isNotEmpty() && student.lastName.isNotEmpty()) {
                                    "${student.lastName.take(1)}${student.firstName.take(1)}".uppercase()
                                } else {
                                    "ST"
                                }
                                Text(
                                    text = initials,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${student.lastName}, ${student.firstName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    val dynamicBadgeValue = remember(student.customDataJson, activeBadgeField) {
                        if (activeBadgeField.isNotEmpty()) {
                            try {
                                val json = JSONObject(student.customDataJson)
                                json.optString(activeBadgeField, "").trim().ifEmpty { null }
                            } catch (e: Exception) {
                                null
                            }
                        } else null
                    }

                    if (dynamicBadgeValue != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = dynamicBadgeValue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                val formattedDate = sdf.format(Date(student.birthday))
                val age = Calendar.getInstance().get(Calendar.YEAR) - Calendar.getInstance().apply { timeInMillis = student.birthday }.get(Calendar.YEAR)
                val genderStr = if (student.gender == "F") "Female" else "Male"

                Text(
                    text = "$genderStr | Age: $age | $formattedDate\n${student.address}",
                    fontSize = 14.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}