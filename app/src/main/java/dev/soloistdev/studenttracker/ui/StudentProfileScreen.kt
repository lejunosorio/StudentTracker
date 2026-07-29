package dev.soloistdev.studenttracker.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
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
import androidx.compose.ui.graphics.asImageBitmap // Resolved: Explicit extension import [1]
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.FormTemplateEntity
import dev.soloistdev.studenttracker.data.Guardian
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import dev.soloistdev.studenttracker.security.QrCodeGenerator
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentProfileScreen(
    studentId: Int,
    onBack: () -> Unit,
    onEdit: (Int) -> Unit,
    onSharePdf: (StudentEntity) -> Unit,
    onDeleteStudent: (Int) -> Unit,
    repository: StudentRepository = StudentRepository(LocalContext.current)
) {
    val context = LocalContext.current
    var student by remember { mutableStateOf<StudentEntity?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Active custom field templates registry [1]
    var activeTemplates by remember { mutableStateOf<List<FormTemplateEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        val list = repository.getAllActiveStudents()
        student = list.find { studentEntity -> studentEntity.id == studentId }

        // Load active configured field keys [1]
        activeTemplates = repository.getAllFormTemplates()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(studentId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Profile")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Student",
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
                        contentDescription = "Student Photo",
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
                val age = Calendar.getInstance().get(Calendar.YEAR) - Calendar.getInstance().apply { timeInMillis = currentStudent.birthday }.get(Calendar.YEAR)
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
                                    Toast.makeText(context, "No maps application installed.", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "No address listed for this student.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Map, contentDescription = "Map")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open in Maps")
                        }
                    }

                    OutlinedButton(
                        onClick = { onSharePdf(currentStudent) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Edit, contentDescription = "Share PDF")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share PDF")
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
                                    contentDescription = "Call Student",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                }

                // EXPANDABLE DIGITAL ID PROFILE QR CODE CARD [1]
                var qrExpanded by remember { mutableStateOf(false) }
                val qrPayload = remember(currentStudent) {
                    val encodedFirst = Uri.encode(currentStudent.firstName)
                    val encodedLast = Uri.encode(currentStudent.lastName)
                    val encodedAddress = Uri.encode(currentStudent.address)
                    val encodedContact = Uri.encode(currentStudent.contactNumber)
                    val encodedGuardians = Uri.encode(currentStudent.guardiansJson)
                    val encodedCustom = Uri.encode(currentStudent.customDataJson)

                    "studenttracker://student?id=${currentStudent.id}" +
                            "&first=$encodedFirst" +
                            "&last=$encodedLast" +
                            "&gender=${currentStudent.gender}" +
                            "&birthday=${currentStudent.birthday}" +
                            "&address=$encodedAddress" +
                            "&contact=$encodedContact" +
                            "&guardians=$encodedGuardians" +
                            "&custom=$encodedCustom"
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
                                // Resolved: Invokes extension function as a member of the Bitmap instance [1]
                                Image(
                                    bitmap = qrBitmapWithLabel.asImageBitmap(), // Corrected dot-notation [1]
                                    contentDescription = "Profile QR Code with Label",
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

                                    // Local Download Button [1]
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

                                    // External Share Button [1]
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

                // Dynamic custom fields iteration restricted by template manager configuration
                val customJson = remember(currentStudent.customDataJson) {
                    try { JSONObject(currentStudent.customDataJson) } catch (_: Exception) { JSONObject() }
                }

                // Keep only keys that exist in activeTemplates list
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
                                        IconButton(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                                context.startActivity(intent)
                                            },
                                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Call,
                                                contentDescription = "Call $phone",
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
        } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
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
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
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