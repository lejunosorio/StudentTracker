package dev.soloistdev.studenttracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.FormTemplateEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    activeFilter: FilterState?,
    availableTemplates: List<FormTemplateEntity>,
    onApplyFilter: (FilterState) -> Unit,
    onResetFilter: () -> Unit,
    onDismiss: () -> Unit,
    hideClassroomFilter: Boolean = false
) {
    var tempField by remember { mutableStateOf(activeFilter?.field ?: "Age") }
    var tempComparison by remember { mutableStateOf(activeFilter?.comparison ?: "In between") }

    var tempVal1 by remember {
        mutableStateOf(
            activeFilter?.value1 ?: if (activeFilter?.field == "Gender") "Female" else ""
        )
    }
    var tempVal2 by remember { mutableStateOf(activeFilter?.value2 ?: "") }
    var tempIsPinned by remember { mutableStateOf(activeFilter?.isPinned ?: false) }

    var showDatePicker1 by remember { mutableStateOf(false) }
    var showDatePicker2 by remember { mutableStateOf(false) }

    val coreFields = remember(hideClassroomFilter) {
        if (hideClassroomFilter) {
            listOf("First Name", "Last Name", "Gender", "Birthday", "Address", "Age")
        } else {
            listOf("First Name", "Last Name", "Gender", "Birthday", "Address", "Age", "Classroom")
        }
    }

    val fieldsList = remember(coreFields) {
        val list = coreFields.toMutableList()
        availableTemplates.forEach { list.add(it.fieldName) }
        list
    }

    fun getFieldType(field: String): String {
        return when (field) {
            "First Name", "Last Name", "Home Address" -> "TEXT"
            "Classroom", "Class" -> "CLASSROOM" // Isolated field type
            "Gender" -> "GENDER"
            "Age" -> "NUMBER"
            "Birthday" -> "DATE"
            else -> {
                val template = availableTemplates.find { it.fieldName == field }
                template?.fieldType ?: "TEXT"
            }
        }
    }

    val currentSelectedType = getFieldType(tempField)
    val isRangeMode = tempComparison == "In between"
    val isGenderMode = currentSelectedType == "GENDER"
    val isClassroomMode = currentSelectedType == "CLASSROOM"
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
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()) // Keyboard-safe layout scroll
                .imePadding(), // Keyboard-safe layout height constraints
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.filter_directory_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

            var fieldExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = fieldExpanded,
                onExpandedChange = { fieldExpanded = it }
            ) {
                OutlinedTextField(
                    value = tempField.replace("_", " "),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.filter_select_field)) },
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
                                val newType = getFieldType(option)
                                tempComparison = when {
                                    option == "Birthday" -> "exact_birthday"
                                    newType == "NUMBER" -> "In between"
                                    newType == "GENDER" -> "equal"
                                    newType == "CLASSROOM" -> "member of" // Enforces multi-class membership constraints
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
                        label = { Text(stringResource(R.string.filter_comparison_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = compExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    )
                    ExposedDropdownMenu(
                        expanded = compExpanded,
                        onDismissRequest = { compExpanded = false }
                    ) {
                        val operatorsList = when {
                            currentSelectedType == "NUMBER" -> listOf("equal", "greater than", "less than", "In between")
                            isClassroomMode -> listOf("member of", "not member of") // Restricts strictly to class membership
                            else -> listOf("contains", "does not contain", "equal", "not equal")
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
                    stringResource(R.string.birthday_type_year) to "birth_year",
                    stringResource(R.string.birthday_type_month) to "birth_month",
                    stringResource(R.string.birthday_type_month_year) to "birth_month_year",
                    stringResource(R.string.birthday_type_exact) to "exact_birthday"
                )
                val selectedTypeName = birthdayTypes.find { it.second == tempComparison }?.first ?: stringResource(R.string.birthday_type_exact)

                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedTypeName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.filter_select_birthday_type)) },
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
                            label = { Text(stringResource(R.string.filter_birth_year_label)) },
                            isError = isFutureYear1,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (isFutureYear1) {
                            Text(stringResource(R.string.error_future_year), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
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
                                label = { Text(stringResource(R.string.filter_select_birth_month)) },
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
                                        label = { Text(stringResource(R.string.filter_month_label)) },
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
                                label = { Text(stringResource(R.string.filter_year_label)) },
                                isError = isFutureYear2,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (isFutureYear2) {
                            Text(stringResource(R.string.error_future_year), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                    "exact_birthday" -> {
                        val sdfPicker = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                        val birthday1Formatted = tempVal1.toLongOrNull()?.let { sdfPicker.format(Date(it)) } ?: stringResource(R.string.filter_select_birthday_date)

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
                Text(stringResource(R.string.filter_select_gender), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = tempVal1 == "Female",
                        onClick = { tempVal1 = "Female" },
                        label = { Text(stringResource(R.string.gender_female)) },
                        colors = chipColors
                    )
                    FilterChip(
                        selected = tempVal1 == "Male",
                        onClick = { tempVal1 = "Male" },
                        label = { Text(stringResource(R.string.gender_male)) },
                        colors = chipColors
                    )
                }
            } else {
                val isValueRequired = tempComparison != "empty" && tempComparison != "not empty"
                if (isValueRequired) {
                    if (isRangeMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = tempVal1,
                                onValueChange = { tempVal1 = it },
                                label = { Text(stringResource(R.string.filter_value_min)) },
                                isError = isValidationError,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = tempVal2,
                                onValueChange = { tempVal2 = it },
                                label = { Text(stringResource(R.string.filter_value_max)) },
                                isError = isValidationError,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (isValidationError) {
                            Text(
                                text = stringResource(R.string.filter_range_error),
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
                            label = { Text(stringResource(R.string.filter_value_singular)) },
                            keyboardOptions = KeyboardOptions(keyboardType = if (isNumeric) KeyboardType.Number else KeyboardType.Text),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.filter_pin_to_dashboard), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
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
                TextButton(onClick = { onResetFilter(); onDismiss() }) {
                    Text(stringResource(R.string.action_reset))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val isValueRequired = tempComparison != "empty" && tempComparison != "not empty"
                        val isValueValid = !isValueRequired || tempVal1.isNotBlank()

                        if (!isValidationError && isValueValid) {
                            onApplyFilter(
                                FilterState(
                                    field = tempField,
                                    comparison = tempComparison,
                                    value1 = if (isValueRequired) tempVal1.trim() else "",
                                    value2 = if (isValueRequired && isRangeMode) tempVal2.trim() else "",
                                    isPinned = tempIsPinned
                                )
                            )
                            onDismiss()
                        }
                    },
                    enabled = !isValidationError && (tempComparison == "empty" || tempComparison == "not empty" || tempVal1.isNotBlank()),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isValidationError) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary,
                        contentColor = if (isValidationError) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    Text(stringResource(R.string.filter_apply_button))
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