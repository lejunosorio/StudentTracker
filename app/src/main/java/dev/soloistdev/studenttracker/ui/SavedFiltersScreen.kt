package dev.soloistdev.studenttracker.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.SettingsCell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.FormTemplateEntity
import dev.soloistdev.studenttracker.data.Guardian
import dev.soloistdev.studenttracker.data.MessageTemplateEntity
import dev.soloistdev.studenttracker.data.SavedFilterEntity
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedFiltersScreen(
    onBack: () -> Unit,
    onStudentClick: (Int) -> Unit,
    onNavigateToTemplates: () -> Unit,
    viewModel: SavedFiltersViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { StudentRepository(context) }

    val filters by viewModel.filters.collectAsState()
    val students by viewModel.students.collectAsState()
    val messageTemplates by viewModel.messageTemplates.collectAsState()
    val templates by viewModel.templates.collectAsState()

    var selectedFilterForView by remember { mutableStateOf<SavedFilterEntity?>(null) }

    var showFilterDialog by remember { mutableStateOf(false) }
    var editingFilter by remember { mutableStateOf<SavedFilterEntity?>(null) }
    var showBulkSmsDialog by remember { mutableStateOf(false) }
    var bulkSmsTarget by remember { mutableStateOf("Students") }

    var showCreateAttendanceDialog by remember { mutableStateOf(false) }
    var attendanceRecordName by remember { mutableStateOf("") }
    var startDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var endDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val isDateRangeInvalid = startDateMillis > endDateMillis

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    val matchingStudents = remember(students, selectedFilterForView) {
        if (selectedFilterForView == null) {
            emptyList()
        } else {
            val filterState = FilterState(
                field = selectedFilterForView!!.fieldName,
                comparison = selectedFilterForView!!.comparison,
                value1 = selectedFilterForView!!.value1,
                value2 = selectedFilterForView!!.value2
            )
            students.filter { item ->
                applyComparison(getFieldValue(item.student, filterState.field), filterState)
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (selectedFilterForView != null) {
                            selectedFilterForView!!.filterName
                        } else {
                            stringResource(R.string.menu_saved_filters)
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedFilterForView != null) {
                            selectedFilterForView = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (selectedFilterForView == null) {
                        TextButton(onClick = onNavigateToTemplates) {
                            Text(
                                text = "Templates",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        IconButton(onClick = {
                            bulkSmsTarget = "Students"
                            showBulkSmsDialog = true
                        }) {
                            Icon(Icons.Default.SettingsCell, contentDescription = "SMS Students", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            bulkSmsTarget = "Guardians"
                            showBulkSmsDialog = true
                        }) {
                            Icon(Icons.Default.Group, contentDescription = "SMS Guardians", tint = MaterialTheme.colorScheme.secondary)
                        }
                        IconButton(onClick = { showCreateAttendanceDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.EventAvailable,
                                contentDescription = "Create Attendance Record",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedFilterForView == null) {
                FloatingActionButton(
                    onClick = {
                        editingFilter = null
                        showFilterDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Filter")
                }
            }
        }
    ) { paddingValues ->
        if (selectedFilterForView == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Text(
                    text = "Saved Filter Groups",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                if (filters.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.error_create_saved_filter),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filters) { filter ->
                            val criteriaDesc = if (filter.comparison == "In between") {
                                "${filter.fieldName}: ${filter.value1} - ${filter.value2}"
                            } else {
                                "${filter.fieldName} ${filter.comparison} ${filter.value1}"
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFilterForView = filter },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(filter.filterName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(criteriaDesc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = {
                                            editingFilter = filter
                                            showFilterDialog = true
                                        }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = {
                                            viewModel.deleteFilter(filter.id)
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Filtered Roster: ${selectedFilterForView!!.filterName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Matched Student Count: ${matchingStudents.size} members",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Icon(Icons.Default.Bookmarks, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                if (matchingStudents.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No students match this filter criteria.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(matchingStudents) { studentState ->
                            StudentCard(
                                uiState = studentState,
                                isSelected = false,
                                onClick = { onStudentClick(studentState.student.id) },
                                onLongClick = {}
                            )
                        }
                    }
                }
            }
        }

        // DYNAMIC BULK SMS HANDLER DIALOG
        if (showBulkSmsDialog && selectedFilterForView != null) {
            var selectedTemplate by remember { mutableStateOf<MessageTemplateEntity?>(null) }
            var textBody by remember { mutableStateOf("") }
            var dropdownExpanded by remember { mutableStateOf(false) }

            val recipientsCount = remember(matchingStudents, bulkSmsTarget) {
                if (bulkSmsTarget == "Students") {
                    matchingStudents.map { it.student.contactNumber }.filter { it.isNotBlank() }.size
                } else {
                    matchingStudents.flatMap { s ->
                        Guardian.listFromJsonString(s.student.guardiansJson).flatMap { g -> g.phones }
                    }.filter { it.isNotBlank() }.distinct().size
                }
            }

            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showBulkSmsDialog = false }
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState())
                            .imePadding(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Bulk SMS to $bulkSmsTarget",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "Recipient Pool: $recipientsCount active contacts matching the \"${selectedFilterForView?.filterName}\" directory filter parameters.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = stringResource(R.string.notify_select_template),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        val dropdownLabel = selectedTemplate?.name ?: "Custom (Empty Canvas)"
                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = dropdownLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Choose Pre-fill Template") },
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
                                    text = { Text("Custom (Blank Textarea)") },
                                    onClick = {
                                        selectedTemplate = null
                                        textBody = ""
                                        dropdownExpanded = false
                                    }
                                )

                                messageTemplates.forEach { template ->
                                    DropdownMenuItem(
                                        text = { Text(template.name) },
                                        onClick = {
                                            selectedTemplate = template
                                            textBody = template.text
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = textBody,
                            onValueChange = { textBody = it },
                            label = { Text("Message Body") },
                            placeholder = { Text(stringResource(R.string.notify_custom_placeholder)) },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showBulkSmsDialog = false }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val targetPhones = if (bulkSmsTarget == "Students") {
                                        matchingStudents.map { it.student.contactNumber }.filter { it.isNotBlank() }
                                    } else {
                                        matchingStudents.flatMap { s ->
                                            Guardian.listFromJsonString(s.student.guardiansJson).flatMap { g -> g.phones }
                                        }.filter { it.isNotBlank() }.distinct()
                                    }

                                    if (targetPhones.isEmpty()) {
                                        Toast.makeText(context, R.string.bulk_sms_no_recipients, Toast.LENGTH_SHORT).show()
                                    } else {
                                        val separator = if (android.os.Build.MANUFACTURER.equals("Samsung", ignoreCase = true)) ";" else ","
                                        val numbers = targetPhones.joinToString(separator)
                                        val smsUri = Uri.parse("smsto:$numbers")
                                        val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                                            putExtra("sms_body", textBody)
                                        }
                                        try {
                                            context.startActivity(smsIntent)
                                            showBulkSmsDialog = false
                                        } catch (e: Exception) {
                                            Toast.makeText(context, R.string.notify_error_intent_failed, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                enabled = textBody.isNotBlank() && recipientsCount > 0,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(stringResource(R.string.bulk_sms_action))
                            }
                        }
                    }
                }
            }
        }

        // CREATE ATTENDANCE RECORD DIALOG
        if (showCreateAttendanceDialog && selectedFilterForView != null) {
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
                        OutlinedTextField(
                            value = attendanceRecordName,
                            onValueChange = { attendanceRecordName = it },
                            label = { Text(stringResource(R.string.attendance_record_name_label)) },
                            colors = m3TextFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(stringResource(R.string.attendance_select_date_range), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                        val selectionSdf = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }

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
                                val formattedStart = selectionSdf.format(Date(startDateMillis))
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
                                val formattedEnd = selectionSdf.format(Date(endDateMillis))
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
                                scope.launch {
                                    val normalizedStart = Calendar.getInstance().apply {
                                        timeInMillis = startDateMillis
                                        set(Calendar.HOUR_OF_DAY, 0)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }.timeInMillis

                                    val normalizedEnd = Calendar.getInstance().apply {
                                        timeInMillis = endDateMillis
                                        set(Calendar.HOUR_OF_DAY, 0)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }.timeInMillis

                                    val record = dev.soloistdev.studenttracker.data.AttendanceRecordEntity(
                                        name = attendanceRecordName.trim(),
                                        savedFilterId = selectedFilterForView!!.id,
                                        startDate = normalizedStart,
                                        endDate = normalizedEnd
                                    )
                                    val recordId = repository.insertAttendanceRecord(record).toInt()

                                    val daysList = generateDateList(normalizedStart, normalizedEnd)
                                    daysList.forEach { date ->
                                        matchingStudents.forEach { studentState ->
                                            repository.insertAttendanceLog(
                                                dev.soloistdev.studenttracker.data.AttendanceLogEntity(
                                                    recordId = recordId,
                                                    dateMillis = date,
                                                    studentId = studentState.student.id,
                                                    status = "NOT_SET"
                                                )
                                            )
                                        }
                                    }
                                    showCreateAttendanceDialog = false
                                    attendanceRecordName = ""
                                    Toast.makeText(context, R.string.toast_attendance_created, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = attendanceRecordName.isNotBlank() && !isDateRangeInvalid
                    ) {
                        Text(stringResource(R.string.action_create))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showCreateAttendanceDialog = false
                            attendanceRecordName = ""
                        }
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

        if (showStartPicker) {
            val pickerState = rememberDatePickerState(initialSelectedDateMillis = startDateMillis)
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
            val pickerState = rememberDatePickerState(initialSelectedDateMillis = endDateMillis)
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

        if (showFilterDialog) {
            FilterDialogForm(
                templates = templates,
                existingFilter = editingFilter,
                onDismiss = { showFilterDialog = false },
                onSave = { updatedFilter ->
                    viewModel.saveFilter(updatedFilter)
                    showFilterDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDialogForm(
    templates: List<FormTemplateEntity>,
    existingFilter: SavedFilterEntity? = null,
    onDismiss: () -> Unit,
    onSave: (SavedFilterEntity) -> Unit
) {
    var name by remember { mutableStateOf(existingFilter?.filterName ?: "") }
    var field by remember { mutableStateOf(existingFilter?.fieldName ?: "Age") }
    var comparison by remember { mutableStateOf(existingFilter?.comparison ?: "In between") }
    var val1 by remember { mutableStateOf(existingFilter?.value1 ?: "") }
    var val2 by remember { mutableStateOf(existingFilter?.value2 ?: "") }
    var showDatePicker1 by remember { mutableStateOf(false) }

    val coreFields = listOf("First Name", "Last Name", "Gender", "Birthday", "Address", "Age", "classRoom")
    val fieldsList = remember {
        val list = coreFields.toMutableList()
        templates.forEach { list.add(it.fieldName) }
        list
    }

    val isBirthdayMode = field == "Birthday"
    val isGenderMode = field == "Gender"
    val isClassroomMode = field == "classRoom"
    val isRangeMode = comparison == "In between"

    val val1Num = val1.toDoubleOrNull()
    val val2Num = val2.toDoubleOrNull()

    val currentSystemYear = Calendar.getInstance().get(Calendar.YEAR)
    val isFutureYear1 = comparison == "birth_year" && (val1.toIntOrNull() ?: 0) > currentSystemYear
    val isFutureYear2 = comparison == "birth_month_year" && (val2.toIntOrNull() ?: 0) > currentSystemYear

    val isRangeError = isRangeMode && val1Num != null && val2Num != null && val1Num >= val2Num
    val isValidationError = isRangeError || isFutureYear1 || isFutureYear2

    val monthNames = remember {
        listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingFilter == null) "New Saved Filter" else "Edit Saved Filter", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Filter Group Name *") },
                    modifier = Modifier.fillMaxWidth()
                )

                var fieldExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(
                        value = field.replace("_", " "),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Field") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.clickable { fieldExpanded = true }) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = fieldExpanded, onDismissRequest = { fieldExpanded = false }) {
                        fieldsList.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.replace("_", " ")) },
                                onClick = {
                                    field = option
                                    fieldExpanded = false
                                    comparison = when (option) {
                                        "Birthday" -> "exact_birthday"
                                        "Gender" -> "equal"
                                        "classRoom" -> "equal"
                                        "Age" -> "In between"
                                        else -> "contains"
                                    }
                                    val1 = if (option == "Gender") "Female" else ""
                                    val2 = ""
                                }
                            )
                        }
                    }
                }

                if (!isGenderMode && !isBirthdayMode) {
                    var compExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedTextField(
                            value = comparison,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Comparison") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.clickable { compExpanded = true }) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(expanded = compExpanded, onDismissRequest = { compExpanded = false }) {
                            val operatorsList = when (field) {
                                "Age" -> listOf("equal", "greater than", "less than", "In between")
                                "classRoom" -> listOf("equal", "not equal", "empty", "not empty")
                                else -> listOf("contains", "does not contain", "equal", "not equal")
                            }
                            operatorsList.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        comparison = option
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
                    val selectedTypeName = birthdayTypes.find { it.second == comparison }?.first ?: "Exact Birthday (MM/DD/YYYY)"

                    Box {
                        OutlinedTextField(
                            value = selectedTypeName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Birthday Filter Type") },
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.clickable { typeExpanded = true }) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                            birthdayTypes.forEach { (label, value) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        comparison = value
                                        typeExpanded = false
                                        val1 = if (value == "birth_month" || value == "birth_month_year") "1" else ""
                                        val2 = ""
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    when (comparison) {
                        "birth_year" -> {
                            OutlinedTextField(
                                value = val1,
                                onValueChange = { if (it.length <= 4) val1 = it.filter { c -> c.isDigit() } },
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
                            val monthIdx = (val1.toIntOrNull() ?: 1) - 1
                            val selectedMonthName = monthNames.getOrElse(monthIdx) { "January" }

                            Box {
                                OutlinedTextField(
                                    value = selectedMonthName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Select Birth Month *") },
                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.clickable { monthExpanded = true }) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                DropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }) {
                                    monthNames.forEachIndexed { idx, name ->
                                        DropdownMenuItem(
                                            text = { Text(name) },
                                            onClick = {
                                                val1 = (idx + 1).toString()
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
                                val monthIdx = (val1.toIntOrNull() ?: 1) - 1
                                val selectedMonthName = monthNames.getOrElse(monthIdx) { "January" }

                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = selectedMonthName,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Month *") },
                                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.clickable { monthExpanded = true }) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    DropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }) {
                                        monthNames.forEachIndexed { idx, name ->
                                            DropdownMenuItem(
                                                text = { Text(name) },
                                                onClick = {
                                                    val1 = (idx + 1).toString()
                                                    monthExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = val2,
                                    onValueChange = { if (it.length <= 4) val2 = it.filter { c -> c.isDigit() } },
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
                            val birthday1Formatted = val1.toLongOrNull()?.let { sdfPicker.format(Date(it)) } ?: "Select Birthday Date *"

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
                            selected = val1 == "Female",
                            onClick = { val1 = "Female" },
                            label = { Text("Female") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                containerColor = Color.Transparent,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        FilterChip(
                            selected = val1 == "Male",
                            onClick = { val1 = "Male" },
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
                    val isValueRequired = comparison != "empty" && comparison != "not empty"
                    if (isValueRequired) {
                        if (isRangeMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = val1,
                                    onValueChange = { val1 = it },
                                    label = { Text("Value 1 (Min) *") },
                                    isError = isValidationError,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = val2,
                                    onValueChange = { val2 = it },
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
                            OutlinedTextField(
                                value = val1,
                                onValueChange = { val1 = it },
                                label = { Text("Value *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val isValueRequired = comparison != "empty" && comparison != "not empty"
                    val isValueValid = !isValueRequired || val1.isNotBlank()

                    if (name.isNotBlank() && isValueValid && !isValidationError) {
                        onSave(
                            SavedFilterEntity(
                                id = existingFilter?.id ?: 0,
                                filterName = name.trim(),
                                fieldName = field,
                                comparison = comparison,
                                value1 = if (isValueRequired) val1.trim() else "",
                                value2 = if (isValueRequired && isRangeMode) val2.trim() else "",
                                displayOrder = existingFilter?.displayOrder ?: 0
                            )
                        )
                    }
                },
                enabled = !isValidationError && (comparison == "empty" || comparison == "not empty" || val1.isNotBlank())
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(28.dp)
    )

    if (showDatePicker1) {
        val dateState1 = rememberDatePickerState(
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis <= System.currentTimeMillis()
                }
                override fun isSelectableYear(year: Int): Boolean {
                    return year <= Calendar.getInstance().get(Calendar.YEAR)
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker1 = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState1.selectedDateMillis?.let { val1 = it.toString() }
                    showDatePicker1 = false
                }) { Text("OK") }
            }
        ) { DatePicker(state = dateState1, showModeToggle = false) }
    }
}

private fun getFieldValue(student: StudentEntity, field: String): String {
    return when (field) {
        "First Name" -> student.firstName
        "Last Name" -> student.lastName
        "Gender" -> if (student.gender == "F") "Female" else "Male"
        "Address", "Home Address" -> student.address
        "Student Contact" -> student.contactNumber
        "Class", "classRoom" -> student.className
        "Age" -> {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val birthCal = Calendar.getInstance().apply { timeInMillis = student.birthday }
            val birthYear = birthCal.get(Calendar.YEAR)
            (currentYear - birthYear).toString()
        }
        "Birthday" -> student.birthday.toString()
        "Guardian Name" -> {
            val guardians = Guardian.listFromJsonString(student.guardiansJson)
            if (guardians.isNotEmpty()) guardians[0].name else ""
        }
        "Guardian Contact" -> {
            val guardians = Guardian.listFromJsonString(student.guardiansJson)
            if (guardians.isNotEmpty()) guardians[0].phones.firstOrNull() ?: "" else ""
        }
        else -> {
            try {
                JSONObject(student.customDataJson).optString(field, "")
            } catch (_: Exception) {
                ""
            }
        }
    }
}

private fun applyComparison(fieldValue: String, filter: FilterState): Boolean {
    return evaluateCondition(fieldValue, filter.comparison, filter.value1, filter.value2)
}

private fun evaluateCondition(fieldVal: String, operator: String, v1: String, v2: String): Boolean {
    val cleanVal = fieldVal.trim()

    if (operator in listOf("birth_year", "birth_month", "birth_month_year", "exact_birthday")) {
        val studentBirthday = cleanVal.toLongOrNull() ?: return false
        val studentCal = Calendar.getInstance().apply { timeInMillis = studentBirthday }
        return when (operator) {
            "birth_year" -> {
                val yearVal = v1.toIntOrNull() ?: return false
                studentCal.get(Calendar.YEAR) == yearVal
            }
            "birth_month" -> {
                val monthVal = v1.toIntOrNull() ?: return false
                (studentCal.get(Calendar.MONTH) + 1) == monthVal
            }
            "birth_month_year" -> {
                val monthVal = v1.toIntOrNull() ?: return false
                val yearVal = v2.toIntOrNull() ?: return false
                (studentCal.get(Calendar.MONTH) + 1) == monthVal && studentCal.get(Calendar.YEAR) == yearVal
            }
            "exact_birthday" -> {
                val targetBirthday = v1.toLongOrNull() ?: return false
                val calFilter = Calendar.getInstance().apply { timeInMillis = targetBirthday }
                studentCal.get(Calendar.YEAR) == calFilter.get(Calendar.YEAR) &&
                        studentCal.get(Calendar.DAY_OF_YEAR) == calFilter.get(Calendar.DAY_OF_YEAR)
            }
            else -> false
        }
    }

    return when (operator) {
        "contains" -> cleanVal.contains(v1, ignoreCase = true)
        "does not contain" -> !cleanVal.contains(v1, ignoreCase = true)
        "equal" -> cleanVal.equals(v1, ignoreCase = true)
        "not equal" -> !cleanVal.equals(v1, ignoreCase = true)
        "empty" -> cleanVal.isBlank()
        "not empty" -> cleanVal.isNotBlank()
        "greater than" -> {
            val numField = cleanVal.toDoubleOrNull()
            val numVal = v1.toDoubleOrNull()
            if (numField != null && numVal != null) numField > numVal else false
        }
        "less than" -> {
            val numField = cleanVal.toDoubleOrNull()
            val numVal = v1.toDoubleOrNull()
            if (numField != null && numVal != null) numField < numVal else false
        }
        "In between" -> {
            val num = cleanVal.toDoubleOrNull() ?: 0.0
            val min = v1.toDoubleOrNull() ?: 0.0
            val max = v2.toDoubleOrNull() ?: 0.0
            num in min..max
        }
        else -> true
    }
}

// Helper function to generate date lists for attendance sheets
private fun generateDateList(startDate: Long, endDate: Long): List<Long> {
    val dates = mutableListOf<Long>()
    val startCal = Calendar.getInstance().apply {
        timeInMillis = startDate
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val endCal = Calendar.getInstance().apply {
        timeInMillis = endDate
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    while (!startCal.after(endCal)) {
        dates.add(startCal.timeInMillis)
        startCal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return dates
}