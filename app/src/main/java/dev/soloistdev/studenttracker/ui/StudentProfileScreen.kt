package dev.soloistdev.studenttracker.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.ClassShareEngine
import dev.soloistdev.studenttracker.data.FormTemplateEntity
import dev.soloistdev.studenttracker.data.Guardian
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import dev.soloistdev.studenttracker.data.AssessmentColumnEntity
import dev.soloistdev.studenttracker.data.AssessmentScoreEntity
import dev.soloistdev.studenttracker.data.BehaviorIncidentEntity
import dev.soloistdev.studenttracker.data.StudentInsights
import dev.soloistdev.studenttracker.security.ImageCompressor
import dev.soloistdev.studenttracker.security.QrCodeGenerator
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentProfileScreen(
    studentId: Int,
    onBack: () -> Unit,
    onEdit: (Int) -> Unit,
    onSharePdf: (StudentEntity) -> Unit,
    /** Opens the sync screen once this student has been staged for a peer-to-peer transfer. */
    onShareViaP2p: () -> Unit = {},
    onDeleteStudent: (Int) -> Unit,
    repository: StudentRepository = StudentRepository(LocalContext.current)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var student by remember { mutableStateOf<StudentEntity?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var activeTemplates by remember { mutableStateOf<List<FormTemplateEntity>>(emptyList()) }

    // Behavior Tracking States
    var incidents by remember { mutableStateOf<List<BehaviorIncidentEntity>>(emptyList()) }

    // Academic standing. The Early Warning screen already computes this and links here, so the
    // profile showing less than the row you tapped from was the wrong way round.
    var insight by remember { mutableStateOf<StudentInsights.Insight?>(null) }
    var gradedColumns by remember { mutableStateOf<List<AssessmentColumnEntity>>(emptyList()) }
    var myScores by remember { mutableStateOf<List<AssessmentScoreEntity>>(emptyList()) }
    var behaviorExpanded by remember { mutableStateOf(false) }
    var showAddIncidentDialog by remember { mutableStateOf(false) }
    var showShareChoice by remember { mutableStateOf(false) }

    // Automated Communication States
    var showNotificationDialog by remember { mutableStateOf(false) }
    var selectedGuardianForNotification by remember { mutableStateOf<Guardian?>(null) }
    var selectedPhoneForNotification by remember { mutableStateOf("") }

    fun refreshIncidents() {
        scope.launch {
            incidents = repository.getIncidentsForStudent(studentId)
        }
    }

    LaunchedEffect(Unit) {
        val list = repository.getAllActiveStudents()
        student = list.find { studentEntity -> studentEntity.id == studentId }
        activeTemplates = repository.getAllFormTemplates()
        refreshIncidents()

        // Computed with the same engines the gradebook and Early Warning screens use, so this
        // card can never disagree with them.
        val columns = repository.getAllAssessmentColumns()
        val scores = repository.getAllAssessmentScores()
        val categories = repository.getAllAssessmentCategories()
        val logs = repository.getAllAttendanceLogs()
        val allIncidents = repository.getAllIncidents()

        myScores = scores.filter { it.studentId == studentId }
        gradedColumns = columns.filter { col -> myScores.any { it.columnId == col.id && it.score.isNotBlank() } }

        student?.let { current ->
            insight = StudentInsights.compute(
                students = listOf(current),
                logs = logs,
                columns = columns,
                scores = scores,
                categories = categories,
                incidents = allIncidents
            )[studentId]
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(studentId) }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit_profile))
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.cd_delete_student),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        student?.let { currentStudent ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    LocalImageLoader(
                        imagePath = currentStudent.picturePath,
                        contentDescription = stringResource(R.string.cd_student_photo),
                        fallback = {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                val initials = "${currentStudent.lastName.take(1)}${currentStudent.firstName.take(1)}".uppercase()
                                Text(initials, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 36.sp)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "${currentStudent.firstName} ${currentStudent.lastName}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
                val bdayFormatted = sdf.format(Date(currentStudent.birthday))
                val age = dev.soloistdev.studenttracker.data.AgeCalculator.ageInYears(currentStudent.birthday)
                val genderFull = if (currentStudent.gender == "F") "Female" else "Male"

                Text(
                    text = "Gender: $genderFull | Age: $age | $bdayFormatted",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (currentStudent.address.isNotBlank()) {
                                val intentUri = Uri.parse("geo:0,0?q=${Uri.encode(currentStudent.address)}")
                                val mapIntent = Intent(Intent.ACTION_VIEW, intentUri)
                                try {
                                    context.startActivity(mapIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.toast_no_maps_application_installed), Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, context.getString(R.string.toast_no_address_listed_for_this_student), Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Map, contentDescription = stringResource(R.string.cd_map))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.s_open_in_maps))
                        }
                    }

                    OutlinedButton(
                        onClick = { showShareChoice = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_share_pdf))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.s_share))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                ProfileInfoCard(
                    label = "Home Address",
                    value = currentStudent.address
                )

                if (currentStudent.contactNumber.isNotBlank()) {
                    ProfileInfoCard(
                        label = "Student Contact Number",
                        value = currentStudent.contactNumber,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${currentStudent.contactNumber}"))
                                    context.startActivity(intent)
                                },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = stringResource(R.string.cd_call_student),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                }


                AcademicStandingCard(
                    insight = insight,
                    gradedColumns = gradedColumns,
                    scores = myScores
                )

                Spacer(modifier = Modifier.height(16.dp))
                // EXPANDABLE DIGITAL ID PROFILE QR CODE CARD (Enforces classroom assignment and seating positions)
                var qrExpanded by remember { mutableStateOf(false) }
                val qrPayload = remember(currentStudent) {
                    val encodedFirst = Uri.encode(currentStudent.firstName)
                    val encodedLast = Uri.encode(currentStudent.lastName)
                    val encodedAddress = Uri.encode(currentStudent.address)
                    val encodedContact = Uri.encode(currentStudent.contactNumber)
                    val encodedGuardians = Uri.encode(currentStudent.guardiansJson)
                    val encodedCustom = Uri.encode(currentStudent.customDataJson)
                    val encodedClass = Uri.encode(currentStudent.classNamesJson)

                    val firstClass = currentStudent.getClassNamesList().firstOrNull() ?: ""
                    val coords = currentStudent.getSeatingCoordinates(firstClass) ?: Pair(-1f, -1f)

                    "studenttracker://student?id=${currentStudent.id}" +
                            "&first=$encodedFirst" +
                            "&last=$encodedLast" +
                            "&gender=${currentStudent.gender}" +
                            "&birthday=${currentStudent.birthday}" +
                            "&address=$encodedAddress" +
                            "&contact=$encodedContact" +
                            "&guardians=$encodedGuardians" +
                            "&custom=$encodedCustom" +
                            "&class=$encodedClass" +
                            "&seatingX=${coords.first}" +
                            "&seatingY=${coords.second}"
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { qrExpanded = !qrExpanded },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = stringResource(R.string.profile_qr_card_title), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = stringResource(R.string.profile_qr_card_desc), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            Icon(
                                imageVector = if (qrExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }

                        if (qrExpanded) {
                            Spacer(modifier = Modifier.height(16.dp))

                            val qrBitmapWithLabel = remember(qrPayload, currentStudent) {
                                QrCodeGenerator.generateQrCodeWithLabel(
                                    studentName = "${currentStudent.lastName}, ${currentStudent.firstName}",
                                    qrPayload = qrPayload,
                                    size = 512
                                )
                            }

                            if (qrBitmapWithLabel != null) {
                                Image(
                                    bitmap = qrBitmapWithLabel.asImageBitmap(),
                                    contentDescription = stringResource(R.string.cd_profile_qr_code_with_label),
                                    modifier = Modifier.size(200.dp)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val savedSuccessMsg = stringResource(R.string.toast_qr_saved_success)
                                    val savedErrorMsg = stringResource(R.string.toast_qr_saved_error)

                                    Button(
                                        onClick = {
                                            val success = QrCodeGenerator.saveQrToGallery(
                                                context,
                                                qrBitmapWithLabel,
                                                "${currentStudent.lastName}_${currentStudent.firstName}"
                                            )
                                            if (success) {
                                                Toast.makeText(context, savedSuccessMsg, Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, savedErrorMsg, Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Text(stringResource(R.string.action_download_qr), fontSize = 11.sp, maxLines = 1)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            QrCodeGenerator.shareQrCode(
                                                context,
                                                qrBitmapWithLabel,
                                                "${currentStudent.lastName}_${currentStudent.firstName}"
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Text(stringResource(R.string.action_share_qr), fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                // EXPANDABLE LOCAL BEHAVIOR LOG & MILESTONES CARD
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { behaviorExpanded = !behaviorExpanded },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.behavior_log_title),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.behavior_log_desc),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Icon(
                                imageVector = if (behaviorExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }

                        if (behaviorExpanded) {
                            Spacer(modifier = Modifier.height(12.dp))

                            if (incidents.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.behavior_log_empty),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                val logSdf = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
                                incidents.forEach { incident ->
                                    val badgeColor = when (incident.category) {
                                        "Positive" -> Color(0xFF4CAF50)
                                        "Negative" -> Color(0xFFF44336)
                                        else -> Color(0xFF9E9E9E)
                                    }

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Surface(
                                                        color = badgeColor,
                                                        shape = RoundedCornerShape(4.dp),
                                                        modifier = Modifier.padding(end = 8.dp)
                                                    ) {
                                                        Text(
                                                            text = incident.category.uppercase(),
                                                            color = Color.White,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = incident.title,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (incident.description.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = incident.description,
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                                    )
                                                }
                                                if (incident.photoPath.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    LocalImageLoader(
                                                        imagePath = incident.photoPath,
                                                        contentDescription = stringResource(R.string.cd_incident_evidence),
                                                        displaySize = 72.dp,
                                                        modifier = Modifier
                                                            .size(72.dp)
                                                            .clip(RoundedCornerShape(8.dp)),
                                                        fallback = {}
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = "Incident Date: " + logSdf.format(Date(incident.incidentDate)),
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        repository.deleteIncident(incident.id)
                                                        refreshIncidents()
                                                        Toast.makeText(context, R.string.toast_incident_deleted, Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = stringResource(R.string.cd_delete),
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = { showAddIncidentDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.behavior_action_add),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                val customJson = remember(currentStudent.customDataJson) {
                    try { JSONObject(currentStudent.customDataJson) } catch (_: Exception) { JSONObject() }
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

                Spacer(modifier = Modifier.height(24.dp))

                val guardianList = remember(currentStudent.guardiansJson) {
                    Guardian.listFromJsonString(currentStudent.guardiansJson)
                }

                if (guardianList.isNotEmpty()) {
                    Text(
                        text = "Emergency Contacts",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Start).padding(start = 8.dp, bottom = 8.dp)
                    )

                    guardianList.forEach { guardian ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = guardian.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Relationship: ${guardian.relationship}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                guardian.phones.forEach { phone ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(phone, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            IconButton(
                                                onClick = {
                                                    selectedGuardianForNotification = guardian
                                                    selectedPhoneForNotification = phone
                                                    showNotificationDialog = true
                                                },
                                                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Comment,
                                                    contentDescription = stringResource(R.string.cd_send_sms_notification),
                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${phone}"))
                                                    context.startActivity(intent)
                                                },
                                                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Call,
                                                    contentDescription = stringResource(R.string.cd_call_phone),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        if (showNotificationDialog && selectedGuardianForNotification != null && student != null) {
            val targetStudent = student!!
            val targetGuardian = selectedGuardianForNotification!!

            var selectedTemplate by remember { mutableStateOf("Absent") }
            var customMessageText by remember { mutableStateOf("") }

            val templateSdf = remember { SimpleDateFormat("MMMM dd, yyyy", Locale.US) }
            val formattedToday = remember { templateSdf.format(Date()) }

            val compiledMessage = remember(selectedTemplate, customMessageText, targetStudent, targetGuardian, incidents) {
                when (selectedTemplate) {
                    "Absent" -> {
                        context.getString(R.string.sms_template_absent, targetGuardian.name, targetStudent.firstName, formattedToday)
                    }
                    "Behavior" -> {
                        val recentIncident = incidents.firstOrNull()
                        if (recentIncident != null) {
                            val incidentDateStr = SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(Date(recentIncident.incidentDate))
                            context.getString(
                                R.string.sms_template_behavior,
                                targetGuardian.name,
                                targetStudent.firstName,
                                incidentDateStr,
                                recentIncident.title,
                                recentIncident.description.ifEmpty { "N/A" }
                            )
                        } else {
                            ""
                        }
                    }
                    else -> {
                        context.getString(R.string.sms_template_custom, targetGuardian.name, targetStudent.firstName, customMessageText)
                    }
                }
            }

            val isBehaviorTemplateDisabled = incidents.isEmpty()

            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showNotificationDialog = false }
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
                            text = stringResource(R.string.notify_dialog_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "Recipient: ${targetGuardian.name} ($selectedPhoneForNotification)",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = stringResource(R.string.notify_select_template),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Absent", "Behavior", "Custom").forEach { temp ->
                                val isSelected = selectedTemplate == temp
                                val isEnabled = !(temp == "Behavior" && isBehaviorTemplateDisabled)

                                val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

                                Surface(
                                    onClick = { if (isEnabled) selectedTemplate = temp },
                                    enabled = isEnabled,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (isEnabled) borderColor else borderColor.copy(alpha = 0.2f)),
                                    color = if (isEnabled) containerColor else Color.Transparent,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = when (temp) {
                                                "Absent" -> stringResource(R.string.notify_template_absent).substringBefore(" ")
                                                "Behavior" -> stringResource(R.string.notify_template_behavior).substringBefore(" ")
                                                else -> stringResource(R.string.notify_template_custom).substringBefore(" ")
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isEnabled) contentColor else contentColor.copy(alpha = 0.38f),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        if (selectedTemplate == "Custom") {
                            OutlinedTextField(
                                value = customMessageText,
                                onValueChange = { customMessageText = it },
                                label = { Text(stringResource(R.string.s_message_body)) },
                                placeholder = { Text(stringResource(R.string.notify_custom_placeholder)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Text(
                            text = stringResource(R.string.notify_preview_label),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (selectedTemplate == "Behavior" && isBehaviorTemplateDisabled) {
                                    stringResource(R.string.notify_error_no_behavior)
                                } else {
                                    compiledMessage
                                },
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(12.dp),
                                color = if (selectedTemplate == "Behavior" && isBehaviorTemplateDisabled) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showNotificationDialog = false }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (selectedTemplate == "Behavior" && isBehaviorTemplateDisabled) {
                                        Toast.makeText(context, R.string.notify_error_no_behavior, Toast.LENGTH_SHORT).show()
                                    } else {
                                        val smsUri = Uri.parse("smsto:$selectedPhoneForNotification")
                                        val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
                                            putExtra("sms_body", compiledMessage)
                                        }
                                        try {
                                            context.startActivity(smsIntent)
                                            showNotificationDialog = false
                                        } catch (e: Exception) {
                                            Toast.makeText(context, R.string.notify_error_intent_failed, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                enabled = !(selectedTemplate == "Behavior" && isBehaviorTemplateDisabled) &&
                                        !(selectedTemplate == "Custom" && customMessageText.isBlank()),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(stringResource(R.string.notify_action_send))
                            }
                        }
                    }
                }
            }
        }

        if (showAddIncidentDialog) {
            var incidentTitle by remember { mutableStateOf("") }
            var selectedCategory by remember { mutableStateOf("Positive") }
            var incidentDescription by remember { mutableStateOf("") }

            var incidentDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
            var showIncidentDatePicker by remember { mutableStateOf(false) }

            // Photo evidence. Compressed into app-private storage on pick, so the note carries
            // the image itself rather than a fragile reference into the gallery.
            var incidentPhotoPath by remember { mutableStateOf("") }
            var isAttachingPhoto by remember { mutableStateOf(false) }
            val incidentPhotoPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri ->
                uri ?: return@rememberLauncherForActivityResult
                isAttachingPhoto = true
                scope.launch {
                    val saved = ImageCompressor.compressAndSaveImage(context, uri)
                    isAttachingPhoto = false
                    if (saved != null) {
                        incidentPhotoPath = saved
                    } else {
                        Toast.makeText(context, context.getString(R.string.toast_could_not_attach_that_image), Toast.LENGTH_SHORT).show()
                    }
                }
            }

            val categories = listOf("Positive", "Negative", "Neutral")
            val chipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = Color.Transparent,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AlertDialog(
                onDismissRequest = { showAddIncidentDialog = false },
                title = { Text(stringResource(R.string.behavior_dialog_title), fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .imePadding(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = incidentTitle,
                            onValueChange = { incidentTitle = it },
                            label = { Text(stringResource(R.string.behavior_field_title)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = stringResource(R.string.behavior_quick_select),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val templates = listOf(
                                stringResource(R.string.behavior_quick_helpful) to "Positive",
                                stringResource(R.string.behavior_quick_excellent) to "Positive",
                                stringResource(R.string.behavior_quick_late) to "Negative",
                                stringResource(R.string.behavior_quick_disrupt) to "Negative"
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                templates.chunked(2).forEach { chunk ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        chunk.forEach { (label, cat) ->
                                            SuggestionChip(
                                                onClick = {
                                                    incidentTitle = label
                                                    selectedCategory = cat
                                                },
                                                label = { Text(label, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.behavior_field_category),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            categories.forEach { category ->
                                val labelRes = when (category) {
                                    "Positive" -> R.string.behavior_cat_positive
                                    "Negative" -> R.string.behavior_cat_negative
                                    else -> R.string.behavior_cat_neutral
                                }
                                FilterChip(
                                    selected = selectedCategory == category,
                                    onClick = { selectedCategory = category },
                                    label = { Text(stringResource(labelRes)) },
                                    colors = chipColors
                                )
                            }
                        }

                        Text(
                            text = "Incident Date *",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val selectionSdf = remember { SimpleDateFormat("MMM dd, yyyy", Locale.US) }
                        OutlinedButton(
                            onClick = { showIncidentDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(selectionSdf.format(Date(incidentDate)), color = MaterialTheme.colorScheme.onSurface)
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = stringResource(R.string.cd_select_incident_date),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        OutlinedTextField(
                            value = incidentDescription,
                            onValueChange = { incidentDescription = it },
                            label = { Text(stringResource(R.string.behavior_field_notes)) },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { incidentPhotoPicker.launch("image/*") },
                                enabled = !isAttachingPhoto,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isAttachingPhoto) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(
                                        text = if (incidentPhotoPath.isBlank()) "Attach photo" else "Replace photo",
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            if (incidentPhotoPath.isNotBlank()) {
                                LocalImageLoader(
                                    imagePath = incidentPhotoPath,
                                    contentDescription = stringResource(R.string.cd_attached_evidence),
                                    displaySize = 40.dp,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    fallback = {}
                                )
                                TextButton(onClick = { incidentPhotoPath = "" }) {
                                    Text("Remove", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (incidentTitle.isBlank()) {
                                Toast.makeText(context, R.string.error_behavior_title_required, Toast.LENGTH_SHORT).show()
                            } else {
                                scope.launch {
                                    val newIncident = BehaviorIncidentEntity(
                                        studentId = studentId,
                                        title = incidentTitle.trim(),
                                        category = selectedCategory,
                                        description = incidentDescription.trim(),
                                        incidentDate = incidentDate,
                                        photoPath = incidentPhotoPath
                                    )
                                    repository.insertIncident(newIncident)
                                    refreshIncidents()
                                    showAddIncidentDialog = false
                                    Toast.makeText(context, R.string.toast_incident_logged, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddIncidentDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )

            if (showIncidentDatePicker) {
                WheelDatePickerDialog(
                    initialDateMillis = incidentDate,
                    onDismiss = { showIncidentDatePicker = false },
                    onConfirm = { selectedMillis ->
                        incidentDate = selectedMillis
                        showIncidentDatePicker = false
                    }
                )
            }
        }

        if (showShareChoice && student != null) {
            val target = student!!
            AlertDialog(
                onDismissRequest = { showShareChoice = false },
                title = { Text(stringResource(R.string.share_student_title), fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.share_student_body), fontSize = 13.sp)

                        OutlinedButton(
                            onClick = {
                                showShareChoice = false
                                onSharePdf(target)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.s_share_pdf), fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                showShareChoice = false
                                scope.launch {
                                    // The profile record itself, so the receiving device merges a
                                    // student rather than filing away a document it cannot read.
                                    val staged = ClassShareEngine.stageStudentForP2p(
                                        context,
                                        repository,
                                        target,
                                        ClassShareEngine.ShareOptions()
                                    )
                                    if (staged) {
                                        onShareViaP2p()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.share_student_needs_class),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.share_action_p2p), fontSize = 13.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showShareChoice = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

        if (showDeleteDialog && student != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Student?", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to move ${student!!.firstName} ${student!!.lastName} to the Recycle Bin? They can be restored within 30 days.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteDialog = false
                            onDeleteStudent(studentId)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.s_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(stringResource(R.string.s_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}

@Composable
fun ProfileInfoCard(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (trailingIcon != null) {
                trailingIcon()
            }
        }
    }
}
/**
 * Academic standing at a glance: attendance, running grade, and any early-warning flags.
 *
 * Everything here comes from GradeCalculator and StudentInsights rather than being recomputed,
 * so the profile can never contradict the gradebook or the Early Warning list.
 */
@Composable
private fun AcademicStandingCard(
    insight: StudentInsights.Insight?,
    gradedColumns: List<AssessmentColumnEntity>,
    scores: List<AssessmentScoreEntity>
) {
    if (insight == null) return

    var expanded by remember { mutableStateOf(false) }
    val attendance = insight.attendance
    val hasAnything = attendance.total > 0 || insight.gradePercent != null

    if (!hasAnything) return

    val riskColor = when (insight.riskLevel) {
        StudentInsights.RiskLevel.AT_RISK -> MaterialTheme.colorScheme.error
        StudentInsights.RiskLevel.WATCH -> Color(0xFFEF6C00)
        StudentInsights.RiskLevel.NONE -> Color(0xFF2E7D32)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.profile_academic_standing),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(R.string.cd_expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ProfileStat(
                    label = stringResource(R.string.profile_stat_attendance),
                    value = attendance.attendanceRate?.let { String.format(Locale.US, "%.0f%%", it) } ?: "--"
                )
                ProfileStat(
                    label = stringResource(R.string.profile_stat_grade),
                    value = insight.gradePercent?.let { String.format(Locale.US, "%.1f%%", it) } ?: "--"
                )
                ProfileStat(
                    label = stringResource(R.string.profile_stat_absences),
                    value = attendance.absent.toString()
                )
                ProfileStat(
                    label = stringResource(R.string.profile_stat_notes),
                    value = insight.incidentCount.toString()
                )
            }

            if (insight.reasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                insight.reasons.forEach { reason ->
                    Text(
                        text = "• $reason",
                        fontSize = 11.sp,
                        color = riskColor
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                if (attendance.unmarked > 0) {
                    Text(
                        text = stringResource(R.string.profile_unmarked_note, attendance.unmarked),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (gradedColumns.isEmpty()) {
                    Text(
                        text = stringResource(R.string.profile_no_graded_work),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    gradedColumns.forEach { column ->
                        val raw = scores.firstOrNull { it.columnId == column.id }?.score.orEmpty()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(column.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "$raw / ${String.format(Locale.US, "%.0f", column.maxPoints)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
