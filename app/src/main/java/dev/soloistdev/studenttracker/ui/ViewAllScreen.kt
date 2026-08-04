package dev.soloistdev.studenttracker.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult // RESOLVED: Launcher import
import androidx.activity.result.contract.ActivityResultContracts // RESOLVED: Contracts import
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ManageSearch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.ImportResult // RESOLVED: ImportResult import
import dev.soloistdev.studenttracker.data.JsonSyncEngine // RESOLVED: Sync engine import
import dev.soloistdev.studenttracker.data.StudentRepository // RESOLVED: Repository import
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
    onOpenSettings: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenAttendanceWithArgs: (Int, Long) -> Unit,
    onOpenGradebook: () -> Unit,
    onOpenClassrooms: () -> Unit,
    onOpenQueryBuilder: () -> Unit,
    onOpenSeatingChart: (String) -> Unit,
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
    val repository = remember { StudentRepository(context) }

    // ==========================================
    // CLASSROOMS BOARD CONTROLLERS & DATA MERGES (DECLARED FIRST) [1]
    // ==========================================
    // null = State A (Classrooms Board), non-null = State B (Directory List)
    var selectedClassroomForView by remember { mutableStateOf<String?>(null) }

    // Dynamically compile a distinct, sorted list of registered classrooms from the student database
    val distinctClassrooms = remember(students) {
        students.map { it.student.className }.filter { it.isNotBlank() }.distinct().sorted()
    }

    fun getStudentCountForClass(className: String): Int {
        return students.count { it.student.className == className }
    }

    val filteredDirectoryStudents = remember(students, selectedClassroomForView) {
        if (selectedClassroomForView == null || selectedClassroomForView == "All") {
            students
        } else {
            students.filter { it.student.className == selectedClassroomForView }
        }
    }

    // ==========================================
    // STARTUP EMPTY ONBOARDING DIALOG STATES (ADDED) [1]
    // ==========================================
    var showOnboardingDialog by remember { mutableStateOf(false) }
    var hasCheckedOnboarding by remember { mutableStateOf(false) }

    // Collects initial database loading indicator state flow [1]
    val isInitialLoadCompleted by viewModel.isInitialLoadCompleted.collectAsState()

    // Dynamic loading popups
    var showLoadingPopup by remember { mutableStateOf(false) }
    var isImportDone by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var loadingStatusText by remember { mutableStateOf("") }

    // Check on startup if database roster is empty (RESOLVED: direct database query prevents race conditions) [1]
    LaunchedEffect(isInitialLoadCompleted) {
        if (isInitialLoadCompleted && !hasCheckedOnboarding) {
            val dbStudents = repository.getAllActiveStudents() // Direct, safe background query
            if (dbStudents.isEmpty() && !isSelectionMode && selectedClassroomForView == null) {
                showOnboardingDialog = true
            }
            hasCheckedOnboarding = true
        }
    }

    // Direct local JSON file picker [1]
    val onboardingFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            showOnboardingDialog = false
            isImportDone = false
            loadingStatusText = "Restoring roster entities..."
            showLoadingPopup = true // Reveals dynamic restoration progress popup

            scope.launch {
                val result = JsonSyncEngine.importUnencryptedBackup(context, selectedUri, repository)
                importResult = result
                isImportDone = true
                viewModel.loadData() // Reloads directory layout on complete
            }
        }
    }

    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showBulkDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Attendance creation states
    var showCreateAttendanceDialog by remember { mutableStateOf(false) }
    var attendanceRecordName by remember { mutableStateOf("") }
    var startDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var endDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val isDateRangeInvalid = startDateMillis > endDateMillis

    // Gradebook creation states
    var showCreateGradebookDialog by remember { mutableStateOf(false) }
    var gradebookRecordName by remember { mutableStateOf("") }
    var gradebookMaxPoints by remember { mutableStateOf("100.0") }
    var gradebookExamDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var gradebookCheckDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showExamDatePicker by remember { mutableStateOf(false) }
    var showCheckDatePicker by remember { mutableStateOf(false) }

    val bulkDeleteConfirmMsg = stringResource(R.string.delete_members_bulk_confirmation, selectedStudentIds.size)
    val bulkDeleteSuccessMsg = stringResource(R.string.toast_moved_to_recycle_bin, stringResource(R.string.menu_students))

    var showBulkEditSheet by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                drawerTonalElevation = 4.dp,
                drawerShape = RectangleShape,
                modifier = Modifier
                    .width(280.dp)
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, top = 16.dp, bottom = 12.dp)
                    ) {
                        Text(stringResource(R.string.drawer_proctor_portal), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.drawer_school_name), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    val drawerItemColors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // GROUP 1: DAILY PORTAL
                    Text(
                        text = "DAILY PORTAL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.People, contentDescription = null) },
                        label = { Text(stringResource(R.string.menu_student_directory)) },
                        selected = true,
                        onClick = { scope.launch { drawerState.close() } },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.School, contentDescription = null) },
                        label = { Text(stringResource(R.string.menu_classrooms)) },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                onOpenClassrooms()
                            }
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.EventAvailable, contentDescription = null) },
                        label = { Text(stringResource(R.string.menu_attendance_system)) },
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
                        icon = { Icon(Icons.Default.Book, contentDescription = null) },
                        label = { Text(stringResource(R.string.menu_gradebook_matrix)) },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                onOpenGradebook()
                            }
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // GROUP 2: CUSTOMIZATION
                    Text(
                        text = "CUSTOMIZATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Build, contentDescription = null) },
                        label = { Text(stringResource(R.string.menu_template_manager)) },
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
                        icon = { Icon(Icons.Default.Bookmarks, contentDescription = null) },
                        label = { Text(stringResource(R.string.menu_saved_filters)) },
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
                        icon = { Icon(Icons.AutoMirrored.Filled.ManageSearch, contentDescription = null) },
                        label = { Text(stringResource(R.string.menu_query_builder)) },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                onOpenQueryBuilder()
                            }
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // GROUP 3: SYSTEM UTILITIES & APP SETTINGS
                    Text(
                        text = "SYSTEM & SETTINGS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.menu_app_settings)) },
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

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        label = { Text(stringResource(R.string.menu_recycle_bin)) },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                onOpenRecycleBin()
                            }
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                if (isSelectionMode) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.label_selected_count, selectedStudentIds.size), fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
                            }
                        },
                        actions = {
                            IconButton(onClick = { showBulkEditSheet = true }) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = stringResource(R.string.bulk_edit_title),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { showCreateGradebookDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Book,
                                    contentDescription = "Create Gradebook Sheet",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { showCreateAttendanceDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.EventAvailable,
                                    contentDescription = stringResource(R.string.attendance_new_record_title),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { showBulkDeleteConfirmDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Action Delete",
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
                        title = {
                            Text(
                                text = if (selectedClassroomForView != null) {
                                    if (selectedClassroomForView == "All") "All Classrooms" else selectedClassroomForView!!
                                } else {
                                    stringResource(R.string.menu_student_directory)
                                },
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                if (selectedClassroomForView != null) {
                                    selectedClassroomForView = null
                                } else {
                                    scope.launch { drawerState.open() }
                                }
                            }) {
                                Icon(
                                    imageVector = if (selectedClassroomForView != null) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Menu,
                                    contentDescription = stringResource(R.string.drawer_menu)
                                )
                            }
                        },
                        actions = {
                            if (selectedClassroomForView != null && selectedClassroomForView != "All") {
                                IconButton(onClick = { onOpenSeatingChart(selectedClassroomForView!!) }) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsBus,
                                        contentDescription = "Open Seating Chart Planner",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(onClick = { showCreateGradebookDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Book,
                                    contentDescription = "Create Gradebook Sheet",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { showCreateAttendanceDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.EventAvailable,
                                    contentDescription = "Create Attendance Record",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { viewModel.loadData() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                if (!isSelectionMode && selectedClassroomForView == null) {
                    FloatingActionButton(
                        onClick = { onAddStudent(-1) },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.People, contentDescription = stringResource(R.string.menu_students)) },
                        label = { Text(stringResource(R.string.menu_students)) },
                        selected = true,
                        onClick = {}
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Bookmarks, contentDescription = stringResource(R.string.menu_filters)) },
                        label = { Text(stringResource(R.string.menu_filters)) },
                        selected = false,
                        onClick = onOpenMap
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.menu_settings)) },
                        label = { Text(stringResource(R.string.menu_settings)) },
                        selected = false,
                        onClick = onOpenSettings
                    )
                }
            }
        ) { paddingValues ->
            if (selectedClassroomForView == null) {
                // ==========================================
                // STATE A: CLASSROOMS BOARD CARD MATRIX
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    Text(
                        text = "Dynamic Classrooms Board",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedClassroomForView = "All" },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Row(
                                    modifier = Modifier.padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("All Students", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Enrolled: ${students.size} students", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                                    }
                                    Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                }
                            }
                        }

                        item {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }

                        if (distinctClassrooms.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No distinct classrooms found.\nAssign a student to a Class to begin.",
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        } else {
                            items(distinctClassrooms) { className ->
                                val count = getStudentCountForClass(className)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedClassroomForView = className },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(className, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("$count Matched Students", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                        }
                                        Icon(Icons.Default.Book, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ==========================================
                // STATE B: STUDENT LIST DIRECTORY VIEW
                // ==========================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
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
                            placeholder = { Text(stringResource(R.string.action_search_placeholder), color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search_placeholder), tint = MaterialTheme.colorScheme.onSecondaryContainer) },
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val allChipLabel = if (selectedClassroomForView == "All" || selectedClassroomForView == null) {
                            stringResource(R.string.action_all)
                        } else {
                            selectedClassroomForView!!
                        }

                        FilterChip(
                            selected = activeFilter == null,
                            onClick = { viewModel.clearActiveFilter() },
                            label = { Text(allChipLabel) },
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

                    if (filteredDirectoryStudents.isEmpty() && searchQuery.isEmpty() && activeFilter == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.directory_empty_state),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    } else {
                        // Swipe-to-dismiss Student List Directory
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(
                                items = filteredDirectoryStudents,
                                key = { studentState -> studentState.student.id }
                            ) { studentState ->
                                val currentOnStudentClick = rememberUpdatedState(onStudentClick)
                                val currentOnAddStudent = rememberUpdatedState(onAddStudent)
                                val isStudentSelected = selectedStudentIds.contains(studentState.student.id)

                                var showDeleteConfirmDialog by remember { mutableStateOf(false) }
                                val deleteSuccessMsg = stringResource(R.string.toast_moved_to_recycle_bin, "${studentState.student.firstName} ${studentState.student.lastName}")

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
                                        title = { Text(stringResource(R.string.delete_member_title), fontWeight = FontWeight.Bold) },
                                        text = { Text(stringResource(R.string.delete_member_confirmation, "${studentState.student.firstName} ${studentState.student.lastName}")) },
                                        confirmButton = {
                                            Button(
                                                onClick = {
                                                    showDeleteConfirmDialog = false
                                                    viewModel.softDeleteStudent(studentState.student.id)
                                                    Toast.makeText(context, deleteSuccessMsg, Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                            ) {
                                                Text(stringResource(R.string.action_delete))
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showDeleteConfirmDialog = false }) {
                                                Text(stringResource(R.string.action_cancel))
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
        }

        if (showBulkDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showBulkDeleteConfirmDialog = false },
                title = { Text(stringResource(R.string.delete_members_bulk_title), fontWeight = FontWeight.Bold) },
                text = { Text(bulkDeleteConfirmMsg) },
                confirmButton = {
                    Button(
                        onClick = {
                            showBulkDeleteConfirmDialog = false
                            viewModel.deleteSelectedStudents()
                            Toast.makeText(context, bulkDeleteSuccessMsg, Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBulkDeleteConfirmDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
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
                onDismiss = { showFilterSheet = false },
                hideClassroomFilter = selectedClassroomForView != null
            )
        }

        if (showSortSheet) {
            SortBottomSheet(
                sortOrder = sortOrder,
                onSortSelected = { viewModel.updateSortOrder(it) },
                onDismiss = { showSortSheet = false }
            )
        }

        if (showBulkEditSheet) {
            BulkEditBottomSheet(
                selectedCount = selectedStudentIds.size,
                availableTemplates = availableTemplates,
                onApplyChanges = { fieldName, newValue ->
                    viewModel.updateCustomFieldForSelected(fieldName, newValue)
                    Toast.makeText(context, R.string.toast_bulk_edit_success, Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showBulkEditSheet = false }
            )
        }

        // ATTENDANCE CREATION DIALOG (UPDATED WITH SELECTION ESTIMATE LABELS) [1]
        if (showCreateAttendanceDialog) {
            val m3TextFieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val activeTargetRosterIds = remember(filteredDirectoryStudents, selectedStudentIds, isSelectionMode) {
                if (isSelectionMode) selectedStudentIds.toList() else filteredDirectoryStudents.map { it.student.id }
            }

            AlertDialog(
                onDismissRequest = {
                    showCreateAttendanceDialog = false
                    attendanceRecordName = ""
                    startDateMillis = System.currentTimeMillis()
                    endDateMillis = System.currentTimeMillis()
                },
                title = { Text(stringResource(R.string.attendance_new_record_title), fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .imePadding(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val attendanceNoticeText = if (isSelectionMode) {
                            "Notice: ${activeTargetRosterIds.size} selected students will be added to this attendance record."
                        } else {
                            "Notice: All ${activeTargetRosterIds.size} students in this view will be added to this attendance record."
                        }
                        Text(
                            text = attendanceNoticeText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        OutlinedTextField(
                            value = attendanceRecordName,
                            onValueChange = { attendanceRecordName = it },
                            label = { Text(stringResource(R.string.attendance_record_name_label)) },
                            colors = m3TextFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(stringResource(R.string.attendance_select_date_range), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

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
                                val formattedStart = sdf.format(Date(startDateMillis))
                                Text(stringResource(R.string.attendance_start_date_label, formattedStart), color = MaterialTheme.colorScheme.onSurface)
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
                                val formattedEnd = sdf.format(Date(endDateMillis))
                                Text(stringResource(R.string.attendance_end_date_label, formattedEnd), color = MaterialTheme.colorScheme.onSurface)
                                Icon(Icons.Default.CalendarToday, contentDescription = "Select End Date", tint = if (isDateRangeInvalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            }
                        }

                        if (isDateRangeInvalid) {
                            Text(
                                text = stringResource(R.string.attendance_date_range_error),
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
                                    selectedIds = activeTargetRosterIds,
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
                        Text(stringResource(R.string.action_create))
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
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

        // CREATE GRADEBOOK SHEET DIALOG (ADDED WITH SELECTION ESTIMATE LABELS) [1]
        if (showCreateGradebookDialog) {
            val m3TextFieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val activeTargetRosterIds = remember(filteredDirectoryStudents, selectedStudentIds, isSelectionMode) {
                if (isSelectionMode) selectedStudentIds.toList() else filteredDirectoryStudents.map { it.student.id }
            }

            AlertDialog(
                onDismissRequest = {
                    showCreateGradebookDialog = false
                    gradebookRecordName = ""
                    gradebookMaxPoints = "100.0"
                },
                title = { Text("New Grading Sheet", fontWeight = FontWeight.Bold) },
                text = {
                    val gradebookNoticeText = if (isSelectionMode) {
                        "Notice: ${activeTargetRosterIds.size} selected students will be added to this gradebook sheet."
                    } else {
                        "Notice: All ${activeTargetRosterIds.size} students in this view will be added to this gradebook sheet."
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .imePadding(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = gradebookNoticeText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        OutlinedTextField(
                            value = gradebookRecordName,
                            onValueChange = { gradebookRecordName = it },
                            label = { Text("Sheet Name * (e.g. Midterm)") },
                            colors = m3TextFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = gradebookMaxPoints,
                            onValueChange = { gradebookMaxPoints = it },
                            label = { Text("Max Points *") },
                            colors = m3TextFieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        val sdf = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }

                        // Exam Date selection button
                        Text(text = "Exam Date *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                                Text(sdf.format(Date(gradebookExamDate)), color = MaterialTheme.colorScheme.onSurface)
                                Icon(Icons.Default.CalendarToday, contentDescription = "Select Exam Date", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        // Evaluation Checking Date selection button
                        Text(text = "Checking Date *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                                Text(sdf.format(Date(gradebookCheckDate)), color = MaterialTheme.colorScheme.onSurface)
                                Icon(Icons.Default.CalendarToday, contentDescription = "Select Check Date", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (gradebookRecordName.isNotBlank()) {
                                val limit = gradebookMaxPoints.toDoubleOrNull() ?: 100.0
                                viewModel.createManualGradebookRecord(
                                    name = gradebookRecordName,
                                    selectedIds = activeTargetRosterIds,
                                    maxPoints = limit,
                                    examDate = gradebookExamDate,
                                    checkDate = gradebookCheckDate
                                ) { _ ->
                                    showCreateGradebookDialog = false
                                    gradebookRecordName = ""
                                    gradebookMaxPoints = "100.0"
                                    onOpenGradebook()
                                }
                            }
                        },
                        enabled = gradebookRecordName.isNotBlank()
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showCreateGradebookDialog = false
                            gradebookRecordName = ""
                            gradebookMaxPoints = "100.0"
                        }
                    ) {
                        Text(stringResource(R.string.action_cancel))
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
                    }) { Text(stringResource(R.string.action_ok)) }
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
                    }) { Text(stringResource(R.string.action_ok)) }
                }
            ) { DatePicker(state = pickerState, showModeToggle = false) }
        }

        if (showExamDatePicker) {
            val pickerState = rememberDatePickerState(initialSelectedDateMillis = gradebookExamDate)
            DatePickerDialog(
                onDismissRequest = { showExamDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let { gradebookExamDate = it }
                        showExamDatePicker = false
                    }) { Text(stringResource(R.string.action_ok)) }
                }
            ) { DatePicker(state = pickerState, showModeToggle = false) }
        }

        if (showCheckDatePicker) {
            val pickerState = rememberDatePickerState(initialSelectedDateMillis = gradebookCheckDate)
            DatePickerDialog(
                onDismissRequest = { showCheckDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.let { gradebookCheckDate = it }
                        showCheckDatePicker = false
                    }) { Text(stringResource(R.string.action_ok)) }
                }
            ) { DatePicker(state = pickerState, showModeToggle = false) }
        }

        // ON-LAUNCH EMPTY STATE ONBOARDING DIALOG [1]
        if (showOnboardingDialog) {
            AlertDialog(
                onDismissRequest = { showOnboardingDialog = false },
                title = { Text("Welcome to Student Tracker", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("No student records found in your secure local database. Choose an action to begin setting up your roster:")

                        Button(
                            onClick = { onboardingFilePicker.launch("application/json") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Import JSON Backup")
                        }

                        Button(
                            onClick = {
                                showOnboardingDialog = false
                                onAddStudent(-1) // Redirects to add student form
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Add Student manually")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showOnboardingDialog = false }) {
                        Text("Just Browse App")
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

        // DATABASE ONBOARDING RESTORATION PROGRESS POPUP [1]
        if (showLoadingPopup) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(if (isImportDone) "Restoration Complete" else "Importing Data", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!isImportDone) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        } else {
                            val res = importResult
                            if (res != null) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text("Classrooms Loaded: ${res.classroomsCount}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text("Students Loaded: ${res.studentsCount}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text("Saved Filters Loaded: ${res.filtersCount}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text("Attendance Sheets: ${res.attendanceCount}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text("Gradebooks Loaded: ${res.gradebookCount}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                            } else {
                                Text("Failed to parse the backup payload. Verify your JSON schema.", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    if (isImportDone) {
                        Button(
                            onClick = {
                                showLoadingPopup = false
                                importResult = null
                                isImportDone = false
                            }
                        ) {
                            Text("Done")
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}