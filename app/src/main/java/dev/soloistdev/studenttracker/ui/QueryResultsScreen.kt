package dev.soloistdev.studenttracker.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SettingsCell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueryResultsScreen(
    onBackToBuilder: () -> Unit,
    onStudentClick: (Int) -> Unit,
    onOpenAttendanceWithArgs: (Int, Long) -> Unit,
    viewModel: QueryViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { StudentRepository(context) }

    val rawStudents by viewModel.students.collectAsState()
    val queryRules = viewModel.queryRules
    val matchOperator = viewModel.matchOperator
    val messageTemplates by viewModel.messageTemplates.collectAsState()

    var showBulkSmsDialog by remember { mutableStateOf(false) }
    var bulkSmsTarget by remember { mutableStateOf("Students") }
    var showCreateAttendanceDialog by remember { mutableStateOf(false) }
    var showSaveFilterDialog by remember { mutableStateOf(false) }

    var attendanceRecordName by remember { mutableStateOf("") }
    var startDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var endDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val isDateRangeInvalid = startDateMillis > endDateMillis

    // Convert raw entities to UI State List
    val students = remember(rawStudents) {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        rawStudents.map { student ->
            val genderStr = if (student.gender == "F") "Female" else "Male"
            val bdayFormatted = sdf.format(Date(student.birthday))
            val cal = Calendar.getInstance().apply { timeInMillis = student.birthday }
            val age = currentYear - cal.get(Calendar.YEAR)
            StudentUiState(
                student = student,
                genderString = genderStr,
                formattedBirthday = bdayFormatted,
                age = age,
                customBadgeValue = null
            )
        }
    }

    // Dynamic Multi-Criteria Evaluation Engine
    val matchedRoster = remember(students, queryRules, matchOperator) {
        if (queryRules.isEmpty()) {
            students
        } else {
            students.filter { studentUi ->
                if (matchOperator == "AND") {
                    queryRules.all { rule ->
                        val fieldValue = getFieldValue(studentUi.student, rule.field)
                        evaluateCondition(fieldValue, rule.comparison, rule.value1, rule.value2)
                    }
                } else {
                    queryRules.any { rule ->
                        val fieldValue = getFieldValue(studentUi.student, rule.field)
                        evaluateCondition(fieldValue, rule.comparison, rule.value1, rule.value2)
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.query_results_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackToBuilder) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Edit Query")
                    }
                },
                actions = {
                    if (matchedRoster.isNotEmpty()) {
                        IconButton(onClick = { showSaveFilterDialog = true }) {
                            Icon(Icons.Default.Save, contentDescription = "Save Query as Filter", tint = MaterialTheme.colorScheme.primary)
                        }
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
                            Icon(Icons.Default.EventAvailable, contentDescription = "Create Attendance", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Active Query Roster", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Total matched roster size: ${matchedRoster.size} members", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                    IconButton(
                        onClick = onBackToBuilder,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Query", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            Text(
                text = "Query Results",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(matchedRoster) { studentState ->
                    StudentCard(
                        uiState = studentState,
                        isSelected = false,
                        onClick = { onStudentClick(studentState.student.id) },
                        onLongClick = {}
                    )
                }
            }
        }

        // SAVE QUERY AS FILTER DIALOG
        if (showSaveFilterDialog) {
            var filterName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showSaveFilterDialog = false },
                title = { Text(stringResource(R.string.query_results_save_dialog_title), fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.query_results_save_dialog_desc), fontSize = 13.sp)
                        OutlinedTextField(
                            value = filterName,
                            onValueChange = { filterName = it },
                            label = { Text("Filter Name *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (filterName.isNotBlank()) {
                                viewModel.saveQueryAsFilter(filterName)
                                showSaveFilterDialog = false
                                filterName = ""
                                Toast.makeText(context, R.string.query_results_toast_saved, Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = filterName.isNotBlank()
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveFilterDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

        // DYNAMIC BULK SMS HANDLER DIALOG
        if (showBulkSmsDialog && queryRules.isNotEmpty()) {
            var selectedTemplate by remember { mutableStateOf<MessageTemplateEntity?>(null) }
            var textBody by remember { mutableStateOf("") }
            var dropdownExpanded by remember { mutableStateOf(false) }

            val recipientsCount = remember(matchedRoster, bulkSmsTarget) {
                if (bulkSmsTarget == "Students") {
                    matchedRoster.map { it.student.contactNumber }.filter { it.isNotBlank() }.size
                } else {
                    matchedRoster.flatMap { s ->
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
                            text = "Recipient Pool: $recipientsCount active contacts matching the multi-criteria query requirements.",
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
                                        matchedRoster.map { it.student.contactNumber }.filter { it.isNotBlank() }
                                    } else {
                                        matchedRoster.flatMap { s ->
                                            Guardian.listFromJsonString(s.student.guardiansJson).flatMap { g -> g.phones }
                                        }.filter { it.isNotBlank() }.distinct()
                                    }

                                    if (targetPhones.isEmpty()) {
                                        Toast.makeText(context, R.string.bulk_sms_no_recipients, Toast.LENGTH_SHORT).show()
                                    } else {
                                        val separator = if (android.os.Build.MANUFACTURER.equals("Samsung", ignoreCase = true)) ";" else ","
                                        val numbers = targetPhones.joinToString(separator)
                                        val smsUri = "smsto:$numbers".toUri()
                                        val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                                            putExtra("sms_body", textBody)
                                        }
                                        try {
                                            context.startActivity(smsIntent)
                                            showBulkSmsDialog = false
                                        } catch (e: Exception) {
                                            e.printStackTrace()
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

        // CREATE ATTENDANCE SHEET RECORD DIALOG
        if (showCreateAttendanceDialog && queryRules.isNotEmpty()) {
            val m3TextFieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val activeTargetRosterIds = remember(matchedRoster) {
                matchedRoster.map { it.student.id }
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
                        Text(
                            text = "Notice: All ${activeTargetRosterIds.size} matched query students will be added to this attendance record.",
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

                                    val record = AttendanceRecordEntity(
                                        name = attendanceRecordName.trim(),
                                        savedFilterId = 0,
                                        startDate = normalizedStart,
                                        endDate = normalizedEnd
                                    )
                                    val recordId = repository.insertAttendanceRecord(record).toInt()

                                    val daysList = generateDateList(normalizedStart, normalizedEnd)
                                    daysList.forEach { date ->
                                        activeTargetRosterIds.forEach { studentId ->
                                            repository.insertAttendanceLog(
                                                AttendanceLogEntity(
                                                    recordId = recordId,
                                                    dateMillis = date,
                                                    studentId = studentId,
                                                    status = "NOT_SET"
                                                )
                                            )
                                        }
                                    }
                                    showCreateAttendanceDialog = false
                                    attendanceRecordName = ""
                                    Toast.makeText(context, R.string.toast_attendance_created, Toast.LENGTH_SHORT).show()

                                    onOpenAttendanceWithArgs(recordId, normalizedStart)
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
    }
}

private fun getFieldValue(student: StudentEntity, field: String): String {
    return when (field) {
        "First Name" -> student.firstName
        "Last Name" -> student.lastName
        "Gender" -> if (student.gender == "F") "Female" else "Male"
        "Address", "Home Address" -> student.address
        "Student Contact" -> student.contactNumber
        "Class", "Classroom" -> student.classNamesJson // Corrected to return multi-classroom array data
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

private fun evaluateCondition(fieldVal: String, operator: String, v1: String, v2: String): Boolean {
    val cleanVal = fieldVal.trim()

    // Resolves both visual queries and legacy exact operators seamlessly
    val isJsonArray = cleanVal.startsWith("[") && cleanVal.endsWith("]")
    if (isJsonArray) {
        val studentClasses = try {
            val array = JSONArray(cleanVal)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i).lowercase())
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList<String>()
        }
        val targetClass = v1.lowercase()
        return when (operator) {
            "member of", "equal", "contains" -> studentClasses.contains(targetClass)
            "not member of", "not equal", "does not contain" -> !studentClasses.contains(targetClass)
            else -> true
        }
    }

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