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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource // Resolved: Explicit resource accessor import [1]
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.soloistdev.studenttracker.R // Resolved: Explicit R file import [1]
import dev.soloistdev.studenttracker.security.ImageCompressor
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditStudentScreen(
    studentId: Int,
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

    // Resource strings collected safely for verification checks
    val validationErrorMessage = stringResource(R.string.validation_error_fields)
    val errorSavingImageMessage = stringResource(R.string.error_saving_image)

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val privatePath = ImageCompressor.compressAndSaveImage(context, it)
            if (privatePath != null) {
                viewModel.picturePath = privatePath
            } else {
                Toast.makeText(context, errorSavingImageMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadStudentForEditing(studentId)
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
                        if (viewModel.firstName.isBlank() || viewModel.lastName.isBlank() ||
                            viewModel.birthday == null || viewModel.guardiansStateList.isEmpty()) {
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
                        contentDescription = "Student Photo",
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
                    onClick = { viewModel.gender = "F" },
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
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                            .verticalScroll(rememberScrollState()) // Resolved: Scrollable layout prevents visual cutoffs [1]
                            .imePadding() // Resolved: Dynamic padding adjustment based on system IME/Keyboard [1]
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
                                Toast.makeText(context, "Name and Contact are required.", Toast.LENGTH_SHORT).show()
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