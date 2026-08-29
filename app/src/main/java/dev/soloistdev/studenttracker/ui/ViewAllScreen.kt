package dev.soloistdev.studenttracker.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import dev.soloistdev.studenttracker.data.ClassSchedule
import dev.soloistdev.studenttracker.data.ClassroomEntity
import dev.soloistdev.studenttracker.data.OrganizationSettings
import dev.soloistdev.studenttracker.data.GradingTermEntity
import dev.soloistdev.studenttracker.data.ImportResult
import dev.soloistdev.studenttracker.data.JsonSyncEngine
import dev.soloistdev.studenttracker.data.StudentRepository
import dev.soloistdev.studenttracker.security.ClassPdfGeneratorHelper
import dev.soloistdev.studenttracker.security.ProgressSlipGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ViewAllScreen(
    onAddStudent: (Int, String?) -> Unit, // Direct classroom cohort navigation parameter mapping
    onStudentClick: (Int) -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenRubrics: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAttendance: () -> Unit,
    onOpenScanAttendance: () -> Unit,
    onOpenAttendanceWithArgs: (Int, Long) -> Unit,
    onOpenGradebook: () -> Unit,
    onOpenToday: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenClassrooms: () -> Unit,
    onOpenQueryBuilder: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenSeatingChart: (String) -> Unit,
    viewModel: StudentListViewModel = viewModel()
) {
    val students by viewModel.students.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()
    val pinnedFilters by viewModel.pinnedFilters.collectAsState()
    val availableTemplates by viewModel.availableTemplates.collectAsState()
    val classrooms by viewModel.classrooms.collectAsState()

    // Re-reads the wall clock every minute so the "teaching now" card stays truthful without
    // the teacher having to pull to refresh.
    var nowMinute by remember { mutableIntStateOf(ClassSchedule.nowMinuteOfDay()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMinute = ClassSchedule.nowMinuteOfDay()
            delay(60_000)
        }
    }

    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedStudentIds by viewModel.selectedStudentIds.collectAsState()

    // The organisation's own identity and vocabulary, and which features it has switched on.
    val orgProfile = LocalOrgProfile.current
    val t = orgProfile.terms

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val repository = remember { StudentRepository(context) }

    // null = State A (Classrooms Board), non-null = State B (Directory List)
    //
    // Saveable, not just remembered. This screen stays on the back stack while a profile is open,
    // but Navigation disposes its composition - so a plain `remember` is gone by the time the
    // teacher presses Back, and the drill-down into a classroom silently reset to the board.
    // Search, sort and filter never had the bug because they live in the ViewModel, which is
    // scoped to the back stack entry and survives on its own.
    var selectedClassroomForView by rememberSaveable { mutableStateOf<String?>(null) }

    // Dynamically compile a distinct, sorted list of registered classrooms from the student database by flatMapping classroom list
    val distinctClassrooms = remember(students) {
        students.flatMap { studentUi ->
            studentUi.student.getClassNamesList()
        }.filter { it.isNotBlank() }.distinct().sorted()
    }

    // Counts students belonging to a classroom cohort
    fun getStudentCountForClass(className: String): Int {
        return students.count { studentUi ->
            studentUi.student.getClassNamesList().contains(className)
        }
    }

    // Filters students belonging to a classroom cohort
    val filteredDirectoryStudents = remember(students, selectedClassroomForView) {
        if (selectedClassroomForView == null || selectedClassroomForView == "All") {
            students
        } else {
            students.filter { studentUi ->
                studentUi.student.getClassNamesList().contains(selectedClassroomForView)
            }
        }
    }

    var showOnboardingDialog by remember { mutableStateOf(false) }
    var hasCheckedOnboarding by remember { mutableStateOf(false) }

    val isInitialLoadCompleted by viewModel.isInitialLoadCompleted.collectAsState()

    // Dynamic loading popups
    var showLoadingPopup by remember { mutableStateOf(false) }
    var isImportDone by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var loadingStatusText by remember { mutableStateOf("") }

    LaunchedEffect(isInitialLoadCompleted) {
        if (isInitialLoadCompleted && !hasCheckedOnboarding) {
            val dbStudents = repository.getAllActiveStudents()
            if (dbStudents.isEmpty() && !isSelectionMode && selectedClassroomForView == null) {
                showOnboardingDialog = true
            }
            hasCheckedOnboarding = true
        }
    }

    val onboardingFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            showOnboardingDialog = false
            isImportDone = false
            loadingStatusText = "Restoring roster entities..."
            showLoadingPopup = true

            scope.launch {
                val result = JsonSyncEngine.importUnencryptedBackup(context, selectedUri, repository)
                importResult = result
                isImportDone = true
                viewModel.loadData()
            }
        }
    }


    // Re-read on every entry. The ViewModel is scoped to the back-stack entry and survives
    // navigation, so without this the roster is stale after adding, editing, importing or
    // scanning anywhere else in the app.
    LaunchedEffect(Unit) {
        viewModel.loadData()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showBulkDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showRemoveFromClassConfirmDialog by remember { mutableStateOf(false) }

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
    var showAddStudentsToClassDialog by remember { mutableStateOf(false) }
    var showClassroomAddOptionsDialog by remember { mutableStateOf(false) }
    var showColdCallDialog by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showProgressSlipDialog by remember { mutableStateOf(false) }
    var showShareClassesDialog by remember { mutableStateOf(false) }
    var isGeneratingSlips by remember { mutableStateOf(false) }

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
                        // The organisation's own name and logo, falling back to the built-in
                        // placeholders until they set them. This is the first thing on screen when
                        // the menu opens, so it is where "generic school app" is most obvious.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (orgProfile.logoPath.isNotBlank()) {
                                LocalImageLoader(
                                    imagePath = orgProfile.logoPath,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    displaySize = 40.dp,
                                    fallback = { Box(modifier = Modifier.size(40.dp)) }
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                            }
                            Column {
                                Text(
                                    text = orgProfile.organizationName,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2
                                )
                                Text(
                                    text = orgProfile.ownerName,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
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

                    Text(
                        text = "DAILY PORTAL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.People, contentDescription = null) },
                        label = { Text(stringResource(R.string.term_directory, t.learner)) },
                        selected = true,
                        onClick = { scope.launch { drawerState.close() } },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.School, contentDescription = null) },
                        label = { Text(t.groups) },
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

                    if (orgProfile.isEnabled(OrganizationSettings.Module.ATTENDANCE)) NavigationDrawerItem(
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

                    if (orgProfile.isEnabled(OrganizationSettings.Module.ATTENDANCE)) NavigationDrawerItem(
                        icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                        label = { Text(stringResource(R.string.s_scan_attendance)) },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                onOpenScanAttendance()
                            }
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )

                    if (orgProfile.isEnabled(OrganizationSettings.Module.GRADEBOOK)) NavigationDrawerItem(
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

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Today, contentDescription = null) },
                        label = { Text(stringResource(R.string.menu_today)) },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                onOpenToday()
                            }
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )

                    if (orgProfile.isEnabled(OrganizationSettings.Module.INSIGHTS)) NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Insights, contentDescription = null) },
                        label = { Text(stringResource(R.string.s_early_warning)) },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                onOpenInsights()
                            }
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )

                    // A heading with nothing under it is worse than no heading: it reads as a
                    // section that failed to load. Every item below is module-gated, so this whole
                    // group can empty out - it is the only one that can, since the others each
                    // hold at least one entry that is always present.
                    //
                    // The list is the union of the modules used by the items in this section. A
                    // new item here needs its module adding to it, or the heading will disappear
                    // while its own row is still showing.
                    val customizationModules = listOf(
                        OrganizationSettings.Module.MESSAGING,
                        OrganizationSettings.Module.GRADEBOOK,
                        OrganizationSettings.Module.QUERIES
                    )
                    if (customizationModules.any { orgProfile.isEnabled(it) }) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Text(
                            text = "CUSTOMIZATION",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp)
                        )
                    }

                    if (orgProfile.isEnabled(OrganizationSettings.Module.MESSAGING)) NavigationDrawerItem(
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

                    if (orgProfile.isEnabled(OrganizationSettings.Module.GRADEBOOK)) NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Rule, contentDescription = null) },
                        label = { Text(stringResource(R.string.s_rubrics)) },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                onOpenRubrics()
                            }
                        },
                        colors = drawerItemColors,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )

                    if (orgProfile.isEnabled(OrganizationSettings.Module.QUERIES)) NavigationDrawerItem(
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

                    if (orgProfile.isEnabled(OrganizationSettings.Module.QUERIES)) NavigationDrawerItem(
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            var actionsMenuExpanded by remember { mutableStateOf(false) }

                            Box {
                                IconButton(onClick = { actionsMenuExpanded = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_selection_actions))
                                }

                                DropdownMenu(
                                    expanded = actionsMenuExpanded,
                                    onDismissRequest = { actionsMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.s_create_attendance)) },
                                        leadingIcon = { Icon(Icons.Default.EventAvailable, null) },
                                        onClick = {
                                            actionsMenuExpanded = false
                                            showCreateAttendanceDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.s_create_gradebook)) },
                                        leadingIcon = { Icon(Icons.Default.Book, null) },
                                        onClick = {
                                            actionsMenuExpanded = false
                                            showCreateGradebookDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.s_edit_students)) },
                                        leadingIcon = { Icon(Icons.Default.EditNote, null) },
                                        onClick = {
                                            actionsMenuExpanded = false
                                            showBulkEditSheet = true
                                        }
                                    )
                                    if (selectedClassroomForView != null && selectedClassroomForView != "All") {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.s_remove_student_from_class)) },
                                            leadingIcon = { Icon(Icons.Default.PersonRemove, null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                actionsMenuExpanded = false
                                                showRemoveFromClassConfirmDialog = true
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.s_delete_student)) },
                                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            actionsMenuExpanded = false
                                            showBulkDeleteConfirmDialog = true
                                        }
                                    )
                                }
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
                                    if (selectedClassroomForView == "All") stringResource(R.string.term_all, t.groups) else selectedClassroomForView!!
                                } else {
                                    stringResource(R.string.term_directory, t.learner)
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
                            var normalMenuExpanded by remember { mutableStateOf(false) }
                            var isGeneratingClassReportPdf by remember { mutableStateOf(false) }

                            Box {
                                IconButton(onClick = { normalMenuExpanded = true }) {
                                    if (isGeneratingClassReportPdf) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_actions))
                                    }
                                }

                                DropdownMenu(
                                    expanded = normalMenuExpanded,
                                    onDismissRequest = { normalMenuExpanded = false }
                                ) {
                                    if (selectedClassroomForView != null && selectedClassroomForView != "All") {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.s_assign_seats)) },
                                            leadingIcon = { Icon(Icons.Default.DirectionsBus, null, tint = MaterialTheme.colorScheme.primary) },
                                            onClick = {
                                                normalMenuExpanded = false
                                                onOpenSeatingChart(selectedClassroomForView!!)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.s_export_pdf)) },
                                            leadingIcon = { Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary) },
                                            onClick = {
                                                normalMenuExpanded = false
                                                isGeneratingClassReportPdf = true
                                                scope.launch {
                                                    ClassPdfGeneratorHelper.generateAndShareClassPdf(context, selectedClassroomForView!!)
                                                    isGeneratingClassReportPdf = false
                                                }
                                            }
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.s_progress_slips)) },
                                        leadingIcon = { Icon(Icons.Default.Assignment, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = {
                                            normalMenuExpanded = false
                                            // Report day is a per-period document, so ask which
                                            // grading period the slip covers rather than always
                                            // spanning the whole year.
                                            showProgressSlipDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.s_cold_call)) },
                                        leadingIcon = { Icon(Icons.Default.Casino, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = {
                                            normalMenuExpanded = false
                                            showColdCallDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.s_make_groups)) },
                                        leadingIcon = { Icon(Icons.Default.Groups, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = {
                                            normalMenuExpanded = false
                                            showGroupDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.share_classes_title)) },
                                        leadingIcon = { Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = {
                                            normalMenuExpanded = false
                                            showShareClassesDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.s_create_gradebook)) },
                                        leadingIcon = { Icon(Icons.Default.Book, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = {
                                            normalMenuExpanded = false
                                            showCreateGradebookDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.s_create_attendance_record)) },
                                        leadingIcon = { Icon(Icons.Default.EventAvailable, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = {
                                            normalMenuExpanded = false
                                            showCreateAttendanceDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.s_refresh)) },
                                        leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                        onClick = {
                                            normalMenuExpanded = false
                                            viewModel.loadData()
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                if (!isSelectionMode) {
                    FloatingActionButton(
                        onClick = {
                            if (selectedClassroomForView == null || selectedClassroomForView == "All") {
                                onAddStudent(-1, null)
                            } else {
                                showClassroomAddOptionsDialog = true
                            }
                        },
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
                        label = { Text(t.learners) },
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
                            NowTeachingCard(
                                classrooms = classrooms,
                                nowMinute = nowMinute,
                                studentCountFor = { name -> getStudentCountForClass(name) },
                                onOpenClass = { name -> selectedClassroomForView = name },
                                onOpenSeating = { name -> onOpenSeatingChart(name) }
                            )
                        }

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
                                        Text(stringResource(R.string.term_all, t.learners), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
                            items(distinctClassrooms, key = { it }) { className ->
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
                                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cd_clear), tint = MaterialTheme.colorScheme.onSecondaryContainer)
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
                            Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.cd_filter))
                        }

                        IconButton(
                            onClick = { showSortSheet = true },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.cd_sort))
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
                            val labelText = filterSummaryLabel(pinnedFilter)

                            InputChip(
                                selected = activeFilter?.id == pinnedFilter.id,
                                onClick = { viewModel.selectPinnedFilter(pinnedFilter) },
                                label = { Text(labelText) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { viewModel.removePinnedFilter(pinnedFilter) },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cd_clear), modifier = Modifier.size(12.dp))
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
                                                currentOnAddStudent.value(studentState.student.id, null)
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
                                    modifier = Modifier.zIndex(if (isStudentSelected) 10f else 0f),
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
                                        text = {
                                            // Secure runtime string modification to bypass Android's broken format string exceptions
                                            val rawDesc = stringResource(R.string.delete_member_confirmation)
                                            val resolvedDesc = rawDesc
                                                .replace("%1\${s}", "${studentState.student.firstName} ${studentState.student.lastName}")
                                                .replace("%1\$s", "${studentState.student.firstName} ${studentState.student.lastName}")
                                            Text(resolvedDesc)
                                        },
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
                    val count = selectedStudentIds.size
                    viewModel.updateCustomFieldForSelected(fieldName, newValue)
                    // A bulk field edit overwrites a value on dozens of students with nothing
                    // kept, unlike a bulk delete which lands in the recycle bin. The offer to
                    // undo is what makes it safe to try.
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = context.getString(R.string.bulk_edit_applied, count),
                            actionLabel = context.getString(R.string.bulk_undo),
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.undoLastBulkEdit {
                                Toast.makeText(context, R.string.bulk_undone, Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            viewModel.clearUndo()
                        }
                    }
                },
                onDismiss = { showBulkEditSheet = false }
            )
        }

        // Checklist Prompt Dialog adding students specifically to target classroom layouts
        if (showAddStudentsToClassDialog && selectedClassroomForView != null && selectedClassroomForView != "All") {
            val nonClassroomStudents = remember(students, selectedClassroomForView) {
                students.filter { studentUi ->
                    !studentUi.student.getClassNamesList().contains(selectedClassroomForView)
                }
            }
            var checkedStudentIds by remember { mutableStateOf(emptySet<Int>()) }

            AlertDialog(
                onDismissRequest = {
                    showAddStudentsToClassDialog = false
                    checkedStudentIds = emptySet()
                },
                title = { Text("Enroll Students in $selectedClassroomForView", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        if (nonClassroomStudents.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "All active directory students are already enrolled in this classroom cohort.",
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(nonClassroomStudents, key = { it.student.id }) { studentUi ->
                                    val isChecked = checkedStudentIds.contains(studentUi.student.id)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                checkedStudentIds = if (isChecked) {
                                                    checkedStudentIds - studentUi.student.id
                                                } else {
                                                    checkedStudentIds + studentUi.student.id
                                                }
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                modifier = Modifier.size(36.dp).clip(CircleShape),
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                                    val initials = "${studentUi.student.lastName.take(1)}${studentUi.student.firstName.take(1)}".uppercase()
                                                    Text(initials, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Text(
                                                text = "${studentUi.student.lastName}, ${studentUi.student.firstName}",
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 15.sp
                                            )
                                        }
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = null
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.addStudentsToClassroom(checkedStudentIds.toList(), selectedClassroomForView!!)
                            showAddStudentsToClassDialog = false
                            checkedStudentIds = emptySet()
                            Toast.makeText(context, context.getString(R.string.toast_roster_successfully_enrolled), Toast.LENGTH_SHORT).show()
                        },
                        enabled = checkedStudentIds.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.s_add))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showAddStudentsToClassDialog = false
                            checkedStudentIds = emptySet()
                        }
                    ) {
                        Text(stringResource(R.string.s_close))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

        if (showCreateAttendanceDialog) {
            val m3TextFieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val activeTargetRosterIds = remember(filteredDirectoryStudents, selectedStudentIds, isSelectionMode) {
                if (isSelectionMode) selectedStudentIds.toList() else filteredDirectoryStudents.map { studentUi -> studentUi.student.id }
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
                            border = if (isDateRangeInvalid) {
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
                            } else {
                                ButtonDefaults.outlinedButtonBorder(enabled = true)
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val formattedStart = sdf.format(Date(startDateMillis))
                                Text(stringResource(R.string.attendance_start_date_label, formattedStart), color = MaterialTheme.colorScheme.onSurface)
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = stringResource(R.string.cd_select_start_date),
                                    tint = if (isDateRangeInvalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { showEndPicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = if (isDateRangeInvalid) {
                                BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
                            } else {
                                ButtonDefaults.outlinedButtonBorder(enabled = true)
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val formattedEnd = sdf.format(Date(endDateMillis))
                                Text(stringResource(R.string.attendance_end_date_label, formattedEnd), color = MaterialTheme.colorScheme.onSurface)
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = stringResource(R.string.cd_select_end_date),
                                    tint = if (isDateRangeInvalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
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

        if (showCreateGradebookDialog) {
            val m3TextFieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val activeTargetRosterIds = remember(filteredDirectoryStudents, selectedStudentIds, isSelectionMode) {
                if (isSelectionMode) selectedStudentIds.toList() else filteredDirectoryStudents.map { studentUi -> studentUi.student.id }
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
                            text = "Notice: All ${activeTargetRosterIds.size} target students will be added to this gradebook sheet.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        OutlinedTextField(
                            value = gradebookRecordName,
                            onValueChange = { gradebookRecordName = it },
                            label = { Text(stringResource(R.string.s_sheet_name_e_g_midterm)) },
                            colors = m3TextFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = gradebookMaxPoints,
                            onValueChange = { gradebookMaxPoints = it },
                            label = { Text(stringResource(R.string.s_max_points)) },
                            colors = m3TextFieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        val sdf = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }

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
                                Icon(Icons.Default.CalendarToday, contentDescription = stringResource(R.string.cd_select_exam_date), tint = MaterialTheme.colorScheme.primary)
                            }
                        }

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
                                Icon(Icons.Default.CalendarToday, contentDescription = stringResource(R.string.cd_select_check_date), tint = MaterialTheme.colorScheme.primary)
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

        if (showOnboardingDialog) {
            AlertDialog(
                onDismissRequest = { showOnboardingDialog = false },
                title = { Text("Welcome to Student Tracker", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.s_no_student_records_found_in_your_secure_lo))

                        Button(
                            onClick = { onboardingFilePicker.launch("application/json") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.s_import_json_backup))
                        }

                        Button(
                            onClick = {
                                showOnboardingDialog = false
                                onAddStudent(-1, null)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text(stringResource(R.string.s_add_student_manually))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showOnboardingDialog = false }) {
                        Text(stringResource(R.string.s_just_browse_app))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

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
                            Text(stringResource(R.string.s_done))
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }




        if (showShareClassesDialog) {
            ShareClassesDialog(
                availableClasses = distinctClassrooms,
                onDismiss = { showShareClassesDialog = false },
                onShareViaP2p = onOpenSync
            )
        }
        if (showProgressSlipDialog) {
            ProgressSlipTermDialog(
                onPick = { termId, termLabel ->
                    showProgressSlipDialog = false
                    isGeneratingSlips = true
                    val roster = filteredDirectoryStudents.map { it.student }
                    val classLabel = selectedClassroomForView ?: context.getString(R.string.term_all, t.learners)
                    val title = if (termLabel.isBlank()) classLabel else "$classLabel — $termLabel"
                    scope.launch {
                        ProgressSlipGenerator.generateAndShare(context, roster, title, termId)
                        isGeneratingSlips = false
                    }
                },
                onDismiss = { showProgressSlipDialog = false }
            )
        }
        if (showColdCallDialog) {
            ColdCallDialog(
                className = selectedClassroomForView.takeIf { it != null && it != "All" } ?: "",
                students = filteredDirectoryStudents.map { it.student },
                onDismiss = { showColdCallDialog = false }
            )
        }
        if (showGroupDialog) {
            GroupGeneratorDialog(
                className = selectedClassroomForView.takeIf { it != null && it != "All" } ?: "",
                students = filteredDirectoryStudents.map { it.student },
                onDismiss = { showGroupDialog = false }
            )
        }
        // DATABASE BULK REMOVE FROM CLASS COHORT DIALOG
        if (showRemoveFromClassConfirmDialog && selectedClassroomForView != null && selectedClassroomForView != "All") {
            AlertDialog(
                onDismissRequest = { showRemoveFromClassConfirmDialog = false },
                title = { Text("Remove from Classroom?", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to remove these ${selectedStudentIds.size} selected students from $selectedClassroomForView? This will not delete them from other classes or the directory.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showRemoveFromClassConfirmDialog = false
                            viewModel.removeStudentsFromClassroom(selectedStudentIds.toList(), selectedClassroomForView!!)
                            Toast.makeText(context, context.getString(R.string.toast_students_successfully_removed_from_classro), Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.s_remove))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRemoveFromClassConfirmDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

        // Checklist options prompt selector inside single classrooms
        if (showClassroomAddOptionsDialog && selectedClassroomForView != null && selectedClassroomForView != "All") {
            AlertDialog(
                onDismissRequest = { showClassroomAddOptionsDialog = false },
                title = { Text("Add Students to Class", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.s_would_you_like_to_enroll_an_existing_stude))

                        Button(
                            onClick = {
                                showClassroomAddOptionsDialog = false
                                showAddStudentsToClassDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.s_enroll_existing_student_to_class))
                        }

                        Button(
                            onClick = {
                                showClassroomAddOptionsDialog = false
                                onAddStudent(-1, selectedClassroomForView)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text(stringResource(R.string.s_add_new_student_to_class))
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showClassroomAddOptionsDialog = false }) {
                        Text(stringResource(R.string.s_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}
/**
 * Answers "what am I teaching right now" from the session times on each classroom.
 *
 * Those times were stored but never read anywhere in the app, so a teacher opening the app
 * mid-period still had to find their class by hand. The card collapses to nothing when no
 * classroom carries a parseable schedule, so it never occupies space it has not earned.
 */
@Composable
private fun NowTeachingCard(
    classrooms: List<ClassroomEntity>,
    nowMinute: Int,
    studentCountFor: (String) -> Int,
    onOpenClass: (String) -> Unit,
    onOpenSeating: (String) -> Unit
) {
    val current = remember(classrooms, nowMinute) { ClassSchedule.inSession(classrooms, nowMinute) }
    val next = remember(classrooms, nowMinute) { ClassSchedule.upNext(classrooms, nowMinute) }

    val target = current ?: next ?: return
    val isLive = current != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenClass(target.classroom.name) },
        colors = CardDefaults.cardColors(
            containerColor = if (isLive) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isLive) Icons.Default.Schedule else Icons.Default.Upcoming,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isLive) MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        isLive -> "IN SESSION NOW"
                        target.isTomorrow -> "UP NEXT • TOMORROW"
                        else -> "UP NEXT"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isLive) MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = target.classroom.name,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = if (isLive) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            val minutesAway = target.minutesUntilStart(nowMinute)
            Text(
                text = buildString {
                    append("${target.classroom.startTime} - ${target.classroom.endTime}")
                    append("  •  ${studentCountFor(target.classroom.name)} students")
                    // Only worth counting down when it is close enough to act on. "starts in
                    // 700m" is noise, and the header already says it is tomorrow.
                    if (!isLive && !target.isTomorrow && minutesAway in 1..180) {
                        append("  •  starts in ${minutesAway}m")
                    }
                },
                fontSize = 12.sp,
                color = (if (isLive) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onOpenClass(target.classroom.name) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Open class", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { onOpenSeating(target.classroom.name) },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Take roll", fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Chooses which grading period a batch of progress slips covers.
 *
 * The generator has always supported scoping to a term; the call site simply never asked, so
 * every slip spanned the whole year. On report day that is the wrong document.
 */
@Composable
private fun ProgressSlipTermDialog(
    onPick: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { StudentRepository(context) }
    var terms by remember { mutableStateOf<List<GradingTermEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        terms = repository.getAllGradingTerms()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.slips_pick_period), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(0, "") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        text = stringResource(R.string.slips_whole_year),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                terms.forEach { term ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(term.id, term.name) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (term.isActive) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(term.name, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp)
    )
}
