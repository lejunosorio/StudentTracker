package dev.soloistdev.studenttracker.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource // Resolved: Explicit resource accessor import [1]
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.R // Resolved: Explicit R file import [1]
import dev.soloistdev.studenttracker.data.FormTemplateEntity
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentImportScreen(
    tempStudent: StudentEntity,
    onDismiss: () -> Unit,
    onNavigateToEdit: (Int) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { StudentRepository(context) }
    val scope = rememberCoroutineScope()

    var existingStudentId by remember { mutableIntStateOf(-1) }
    var activeTemplates by remember { mutableStateOf<List<FormTemplateEntity>>(emptyList()) }
    var isLoaded by remember { mutableStateOf(false) }

    val importSuccessMessage = stringResource(R.string.toast_student_imported)

    LaunchedEffect(tempStudent) {
        val activeRoster = repository.getAllActiveStudents()
        // Identity validation check comparing first name, last name, and birthday
        val match = activeRoster.find {
            it.firstName.equals(tempStudent.firstName, ignoreCase = true) &&
                    it.lastName.equals(tempStudent.lastName, ignoreCase = true) &&
                    it.birthday == tempStudent.birthday
        }
        if (match != null) {
            existingStudentId = match.id
        }
        activeTemplates = repository.getAllFormTemplates()
        isLoaded = true
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.import_student_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        },
        bottomBar = {
            if (isLoaded) {
                Surface(
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (existingStudentId != -1) {
                            // Student is already registered: show Edit & Close [1]
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(stringResource(R.string.action_cancel))
                            }
                            Button(
                                onClick = { onNavigateToEdit(existingStudentId) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                // Resolved: Corrected resource key to action_edit [1]
                                Text(stringResource(R.string.action_edit))
                            }
                        } else {
                            // Student is new: show Save & Close [1]
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(stringResource(R.string.action_cancel))
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        repository.saveStudent(tempStudent)
                                        Toast.makeText(context, importSuccessMessage, Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(stringResource(R.string.action_save))
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (isLoaded) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Info Card Header
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (existingStudentId != -1) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = if (existingStudentId != -1) stringResource(R.string.import_student_existing_desc) else stringResource(R.string.import_student_new_desc),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(16.dp),
                        color = if (existingStudentId != -1) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Text(
                    text = "${tempStudent.firstName} ${tempStudent.lastName}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
                val bdayFormatted = sdf.format(Date(tempStudent.birthday))
                val age = dev.soloistdev.studenttracker.data.AgeCalculator.ageInYears(tempStudent.birthday)
                val genderFull = if (tempStudent.gender == "F") stringResource(R.string.gender_female) else stringResource(R.string.gender_male)

                Text(
                    text = "$genderFull | Age: $age | $bdayFormatted",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                ProfileInfoCard(
                    label = stringResource(R.string.label_address),
                    value = tempStudent.address
                )

                if (tempStudent.contactNumber.isNotBlank()) {
                    ProfileInfoCard(
                        label = stringResource(R.string.label_contact_number),
                        value = tempStudent.contactNumber
                    )
                }

                // Custom data filtration check
                val customJson = remember(tempStudent.customDataJson) {
                    try { org.json.JSONObject(tempStudent.customDataJson) } catch (_: Exception) { org.json.JSONObject() }
                }
                val activeTemplateKeys = remember(activeTemplates) { activeTemplates.map { it.fieldName } }

                val keys = customJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = customJson.optString(key, "")
                    if (value.isNotEmpty() && key != "Gender" && activeTemplateKeys.contains(key)) {
                        val label = key.replace("_", " ")
                        ProfileInfoCard(label = label, value = value)
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}