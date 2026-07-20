package dev.soloistdev.studenttracker.ui

import android.widget.Toast
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.soloistdev.studenttracker.data.FormTemplateEntity
import dev.soloistdev.studenttracker.data.StudentEntity
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewAllScreen(
    onAddStudent: (Int) -> Unit,
    onStudentClick: (Int) -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenMapArchives: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: StudentListViewModel = viewModel()
) {
    val students by viewModel.students.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()
    val pinnedFilters by viewModel.pinnedFilters.collectAsState()
    val availableTemplates by viewModel.availableTemplates.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showFilterSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }

    val coreFields = listOf("First Name", "Last Name", "Gender", "Birthday", "Address", "Age", "Guardian Name", "Guardian Contact")
    val comparisonOptions = listOf("contains", "equal", "not equal", "does not contain", "empty", "greater than", "less than", "in range")

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

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Map, contentDescription = null) },
                        label = { Text("Google Maps GPS View") },
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
                                onOpenMapArchives()
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
                CenterAlignedTopAppBar(
                    title = { Text("Choir Directory", fontWeight = FontWeight.Bold) },
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
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { onAddStudent(-1) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Member")
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
                        icon = { Icon(Icons.Default.Map, contentDescription = "Maps") },
                        label = { Text("Maps") },
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
                        Icon(Icons.Default.Sort, contentDescription = "Sort")
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

                        val labelText = if (pinnedFilter.comparison == "in range") {
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

                // Student List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(students) { student ->
                        StudentCard(
                            student = student,
                            onClick = { onStudentClick(student.id) }
                        )
                    }
                }
            }
        }

        // ================= SPRINT 9 FILTER SHEET (Screen 12 & 12B) =================
        if (showFilterSheet) {
            var tempField by remember { mutableStateOf(activeFilter?.field ?: "Age") }
            var tempComparison by remember { mutableStateOf(activeFilter?.comparison ?: "in range") }

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

            val isRangeMode = tempComparison == "in range"
            val isDateMode = currentSelectedType == "DATE"
            val isGenderMode = currentSelectedType == "GENDER"

            val val1Num = tempVal1.toDoubleOrNull()
            val val2Num = tempVal2.toDoubleOrNull()
            val isValidationError = isRangeMode && !isDateMode && val1Num != null && val2Num != null && val1Num > val2Num

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

                    // 1. Select Field Dropdown
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
                            modifier = Modifier.fillMaxWidth().menuAnchor()
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
                                        tempComparison = when (newType) {
                                            "DATE", "NUMBER" -> "in range"
                                            "GENDER" -> "equal" // Force "equal" behind the scenes for Gender
                                            else -> "contains"
                                        }
                                        tempVal1 = if (newType == "GENDER") "Female" else ""
                                        tempVal2 = ""
                                    }
                                )
                            }
                        }
                    }

                    // 2. Select Comparison Operator Dropdown (COMPLETELY HIDDEN in Gender Mode!)
                    if (!isGenderMode) {
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
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = compExpanded,
                                onDismissRequest = { compExpanded = false }
                            ) {
                                val filteredOperators = when (currentSelectedType) {
                                    "NUMBER" -> listOf("equal", "not equal", "less than", "greater than", "in range")
                                    "DATE" -> listOf("equal", "in range")
                                    else -> listOf("contains", "does not contain", "equal", "not equal", "empty")
                                }

                                filteredOperators.forEach { option ->
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

                    // 3. POLYMORPHIC VALUE FIELDS
                    if (isDateMode) {
                        // Date picker layout block (Screen 12 & 12C)
                        val sdfPicker = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                        val bday1Formatted = tempVal1.toLongOrNull()?.let { sdfPicker.format(Date(it)) } ?: "Select Start Date *"
                        val bday2Formatted = tempVal2.toLongOrNull()?.let { sdfPicker.format(Date(it)) } ?: "Select End Date *"

                        if (isRangeMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showDatePicker1 = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(bday1Formatted, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }

                                OutlinedButton(
                                    onClick = { showDatePicker2 = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(bday2Formatted, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        } else {
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
                                    Text(bday1Formatted, color = MaterialTheme.colorScheme.onSurface)
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    } else if (isGenderMode) {
                        // FIXED: Simple, high-contrast M3 choice chips instead of inputs! No comparison selected needed
                        Text("Select Gender *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = tempVal1 == "Female",
                                onClick = { tempVal1 = "Female" },
                                label = { Text("Female") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = Color.Transparent,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            FilterChip(
                                selected = tempVal1 == "Male",
                                onClick = { tempVal1 = "Male" },
                                label = { Text("Male") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = Color.Transparent,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                                    text = "Value 1 must be less than or equal to Value 2",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        } else if (tempComparison != "empty") {
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

                    // 4. Pin Toggle
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

                    // Actions Bar (Disable Apply if Validation error is active)
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

            // NESTED DATE-PICKER POPUPS (Dialog triggers verified)
            if (showDatePicker1) {
                val dateState1 = rememberDatePickerState()
                DatePickerDialog(
                    onDismissRequest = { showDatePicker1 = false },
                    confirmButton = {
                        TextButton(onClick = {
                            dateState1.selectedDateMillis?.let { tempVal1 = it.toString() }
                            showDatePicker1 = false
                        }) { Text("OK") }
                    }
                ) { DatePicker(state = dateState1) }
            }

            if (showDatePicker2) {
                val dateState2 = rememberDatePickerState()
                DatePickerDialog(
                    onDismissRequest = { showDatePicker2 = false },
                    confirmButton = {
                        TextButton(onClick = {
                            dateState2.selectedDateMillis?.let { tempVal2 = it.toString() }
                            showDatePicker2 = false
                        }) { Text("OK") }
                    }
                ) { DatePicker(state = dateState2) }
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

@Composable
fun StudentCard(student: StudentEntity, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
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
                color = MaterialTheme.colorScheme.primary
            ) {
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
                            Text(initials, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                )
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
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    val status = try {
                        JSONObject(student.customDataJson).getString("Status")
                    } catch (e: Exception) {
                        "Mang-aawit"
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        color = if (status == "Nagsasanay") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = status,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (status == "Nagsasanay") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                val formattedDate = sdf.format(Date(student.birthday))
                val age = Calendar.getInstance().get(Calendar.YEAR) - Calendar.getInstance().apply { timeInMillis = student.birthday }.get(Calendar.YEAR)
                val genderStr = if (student.gender == "F") "Female" else "Male"

                Text(
                    text = "$genderStr | Age: $age | $formattedDate\n${student.address}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}