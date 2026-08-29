package dev.soloistdev.studenttracker.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.security.ImageCompressor
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditStudentScreen(
    studentId: Int,
    defaultClass: String? = null, // Supports preselected classroom mappings on student creations
    onBack: () -> Unit,
    viewModel: AddEditViewModel = viewModel()
) {
    val context = LocalContext.current
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }

    var showAddGuardianDialog by remember { mutableStateOf(false) }
    var newGuardianName by remember { mutableStateOf("") }
    var newGuardianRelationship by remember { mutableStateOf("") }
    var newGuardianContact by remember { mutableStateOf("") }

    val m3TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
    )

    val validationErrorMessage = stringResource(R.string.validation_error_fields)
    val errorSavingImageMessage = stringResource(R.string.error_saving_image)
    val imageScope = rememberCoroutineScope()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            // Off the main thread: decoding and re-encoding a camera-sized photo here froze the
            // form for as long as it took.
            imageScope.launch {
                val privatePath = ImageCompressor.compressAndSaveImage(context, it)
                if (privatePath != null) {
                    viewModel.picturePath = privatePath
                } else {
                    Toast.makeText(context, errorSavingImageMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadStudentForEditing(studentId, defaultClass)
    }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (studentId == -1) stringResource(R.string.title_add_student) else stringResource(R.string.title_edit_student),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = {
                        // Requiring at least one registered classroom group on saving
                        if (viewModel.firstName.isBlank() || viewModel.lastName.isBlank() ||
                            viewModel.birthday == null || viewModel.guardiansStateList.isEmpty() ||
                            viewModel.selectedClassrooms.isEmpty()) {
                            Toast.makeText(context, validationErrorMessage, Toast.LENGTH_LONG).show()
                        } else {
                            showSaveDialog = true
                        }
                    }) {
                        Text(
                            text = stringResource(R.string.action_save),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Photo Picker
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.CenterHorizontally)
                    .clickable { imagePickerLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    LocalImageLoader(
                        imagePath = viewModel.picturePath,
                        contentDescription = stringResource(R.string.cd_student_photo),
                        displaySize = 100.dp,
                        fallback = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = stringResource(R.string.add_photo),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.add_photo),
                                    fontSize = 12.sp,

                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    )
                }
            }

            // Said plainly, because the app carries everything else off the device and this is the
            // one thing it cannot. A restored student showing initials would otherwise look broken.
            Text(
                text = stringResource(R.string.photos_device_local),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )

            // LAST NAME
            OutlinedTextField(
                value = viewModel.lastName,
                onValueChange = { viewModel.lastName = it },
                label = { Text(stringResource(R.string.label_last_name)) },
                colors = m3TextFieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            // FIRST NAME
            OutlinedTextField(
                value = viewModel.firstName,
                onValueChange = { viewModel.firstName = it },
                label = { Text(stringResource(R.string.label_first_name)) },
                colors = m3TextFieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            val classrooms by viewModel.classrooms.collectAsState()
            var classDropdownExpanded by remember { mutableStateOf(false) }

            Text(
                text = "Classroom Cohort Selections * (Select all that apply)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            ExposedDropdownMenuBox(
                expanded = classDropdownExpanded,
                onExpandedChange = { classDropdownExpanded = it }
            ) {
                val selectedText = if (viewModel.selectedClassrooms.isEmpty()) {
                    "No Classrooms Selected"
                } else {
                    viewModel.selectedClassrooms.joinToString(", ")
                }

                OutlinedTextField(
                    value = selectedText,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classDropdownExpanded) },
                    colors = m3TextFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                )

                ExposedDropdownMenu(
                    expanded = classDropdownExpanded,
                    onDismissRequest = { classDropdownExpanded = false }
                ) {
                    if (classrooms.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.s_no_classrooms_registered_set_them_up_in_th)) },
                            onClick = { classDropdownExpanded = false }
                        )
                    } else {
                        classrooms.forEach { classroom ->
                            val isSelected = viewModel.selectedClassrooms.contains(classroom.name)
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = null // Managed dynamically via item click
                                        )
                                        Text("${classroom.name} (${classroom.startTime} - ${classroom.endTime})")
                                    }
                                },
                                onClick = {
                                    viewModel.toggleClassroomSelection(classroom.name)
                                }
                            )
                        }
                    }
                }
            }

            // GENDER
            Text(
                text = stringResource(R.string.label_gender),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            val chipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = Color.Transparent,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = viewModel.gender == "F",
                    onClick = { viewModel.gender == "F" },
                    label = { Text(stringResource(R.string.gender_female)) },
                    colors = chipColors
                )
                FilterChip(
                    selected = viewModel.gender == "M",
                    onClick = { viewModel.gender = "M" },
                    label = { Text(stringResource(R.string.gender_male)) },
                    colors = chipColors
                )
            }

            // BIRTHDAY PICKER
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            val birthdayText = viewModel.birthday?.let { sdf.format(Date(it)) } ?: stringResource(R.string.select_birthday)

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(birthdayText, color = MaterialTheme.colorScheme.onSurface)
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = stringResource(R.string.select_date),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ADDRESS FIELD
            OutlinedTextField(
                value = viewModel.address,
                onValueChange = { viewModel.address = it },
                label = { Text(stringResource(R.string.label_address)) },
                colors = m3TextFieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            // STUDENT CONTACT NUMBER
            OutlinedTextField(
                value = viewModel.contactNumber,
                onValueChange = { viewModel.contactNumber = it },
                label = { Text(stringResource(R.string.label_contact_number)) },
                colors = m3TextFieldColors,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = stringResource(R.string.label_guardians),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            viewModel.guardiansStateList.forEachIndexed { index, guardian ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(guardian.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${guardian.relationship} • ${guardian.phones.firstOrNull() ?: ""}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        IconButton(onClick = { viewModel.removeGuardian(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_remove), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { showAddGuardianDialog = true },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_add_guardian),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // DYNAMIC CUSTOM FIELDS
            if (viewModel.customDataMap.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.custom_schema_fields),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                viewModel.customDataMap.forEach { (key, value) ->
                    val userFriendlyLabel = key.replace("_", " ")
                    OutlinedTextField(
                        value = value,
                        onValueChange = { newValue ->
                            viewModel.customDataMap[key] = newValue
                        },
                        label = { Text(userFriendlyLabel) },
                        colors = m3TextFieldColors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // SAVE CONFIRMATION DIALOG
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text(stringResource(R.string.save_changes_title), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.save_changes_description)) },
                confirmButton = {
                    Button(
                        onClick = {
                            showSaveDialog = false
                            viewModel.saveStudent()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

        // DYNAMIC ADD GUARDIAN INPUT DIALOG
        if (showAddGuardianDialog) {
            AlertDialog(
                onDismissRequest = { showAddGuardianDialog = false },
                title = { Text(stringResource(R.string.add_guardian_title), fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .imePadding()
                    ) {
                        OutlinedTextField(
                            value = newGuardianName,
                            onValueChange = { newGuardianName = it },
                            label = { Text(stringResource(R.string.guardian_name)) },
                            colors = m3TextFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newGuardianRelationship,
                            onValueChange = { newGuardianRelationship = it },
                            label = { Text(stringResource(R.string.guardian_relationship)) },
                            colors = m3TextFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newGuardianContact,
                            onValueChange = { newGuardianContact = it },
                            label = { Text(stringResource(R.string.guardian_contact)) },
                            colors = m3TextFieldColors,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newGuardianName.isBlank() || newGuardianContact.isBlank()) {
                                Toast.makeText(context, context.getString(R.string.toast_name_and_contact_are_required), Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.addGuardian(newGuardianName, newGuardianRelationship, newGuardianContact)
                                newGuardianName = ""
                                newGuardianRelationship = ""
                                newGuardianContact = ""
                                showAddGuardianDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(stringResource(R.string.action_add))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddGuardianDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = viewModel.birthday,
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
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.birthday = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                showModeToggle = false
            )
        }
    }
}