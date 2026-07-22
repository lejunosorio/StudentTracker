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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import dev.soloistdev.studenttracker.security.ImageCompressor
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.material.icons.filled.Map
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker



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

    // SPRINT 6 UPDATE: Dialog states for collecting a new guardian dynamically
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

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val privatePath = ImageCompressor.compressAndSaveImage(context, it)
            if (privatePath != null) {
                viewModel.picturePath = privatePath
            } else {
                Toast.makeText(context, "Error saving compressed image.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var showMapPicker by remember { mutableStateOf(false) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(viewModel.latitude ?: 14.5547, viewModel.longitude ?: 121.0509),
            13f
        )
    }
    val markerState = rememberMarkerState(
        position = LatLng(viewModel.latitude ?: 14.5547, viewModel.longitude ?: 121.0509)
    )

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
                title = { Text(if (studentId == -1) "Add Student" else "Edit Student", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (viewModel.firstName.isBlank() || viewModel.lastName.isBlank() ||
                            viewModel.birthday == null || viewModel.guardiansStateList.isEmpty()) {
                            Toast.makeText(context, "Please fill in all required fields (*) and add at least 1 Guardian", Toast.LENGTH_LONG).show()
                        } else {
                            showSaveDialog = true
                        }
                    }) {
                        Text("Save", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                                Icon(Icons.Default.CameraAlt, contentDescription = "Add Photo", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Add Photo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    )
                }
            }

            // LAST NAME
            OutlinedTextField(
                value = viewModel.lastName,
                onValueChange = { viewModel.lastName = it },
                label = { Text("Last Name *") },
                colors = m3TextFieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            // FIRST NAME
            OutlinedTextField(
                value = viewModel.firstName,
                onValueChange = { viewModel.firstName = it },
                label = { Text("First Name *") },
                colors = m3TextFieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            // GENDER
            Text("Gender *", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

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
                    label = { Text("Female") },
                    colors = chipColors
                )
                FilterChip(
                    selected = viewModel.gender == "M",
                    onClick = { viewModel.gender = "M" },
                    label = { Text("Male") },
                    colors = chipColors
                )
            }

            // BIRTHDAY PICKER
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            val birthdayText = viewModel.birthday?.let { sdf.format(Date(it)) } ?: "Select Birthday *"

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
                    Icon(Icons.Default.CalendarToday, contentDescription = "Select Date", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // ADDRESS
            OutlinedTextField(
                value = viewModel.address,
                onValueChange = { viewModel.address = it },
                label = { Text("Address / Location") },
                colors = m3TextFieldColors,
                trailingIcon = {
                    IconButton(onClick = { showMapPicker = true }) {
                        Icon(
                            imageVector = Icons.Default.Map, // Ensure import androidx.compose.material.icons.filled.Map
                            contentDescription = "Select location on Map"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            // SPRINT 6 DYNAMIC GUARDIANS SECTION (Matches Screen 3 Mockups exactly!)
            Text("Guardians *", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            // 1. Loop and draw cards for already added guardians
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
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // 2. Outlined Add Guardian button (Matches your exact mockup!)
            OutlinedButton(
                onClick = { showAddGuardianDialog = true },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Guardian", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }

            // DYNAMIC CUSTOM FIELDS
            if (viewModel.customDataMap.isNotEmpty()) {
                Text("Custom Schema Fields", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

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

        // SPRINT 9 M3 SAVE CONFIRMATION DIALOG
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text("Save Changes?", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to save this student's profile details to the secure local directory?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showSaveDialog = false
                            viewModel.saveStudent()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }

        // SPRINT 6 DYNAMIC ADD GUARDIAN INPUT DIALOG (Collects details on button click!)
        if (showAddGuardianDialog) {
            AlertDialog(
                onDismissRequest = { showAddGuardianDialog = false },
                title = { Text("Add New Guardian", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newGuardianName,
                            onValueChange = { newGuardianName = it },
                            label = { Text("Name *") },
                            colors = m3TextFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newGuardianRelationship,
                            onValueChange = { newGuardianRelationship = it },
                            label = { Text("Relationship (e.g. Mother) *") },
                            colors = m3TextFieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newGuardianContact,
                            onValueChange = { newGuardianContact = it },
                            label = { Text("Contact Number *") },
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
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddGuardianDialog = false }) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }
    }

    if (showMapPicker) {
        AlertDialog(
            onDismissRequest = { showMapPicker = false },
            title = { Text("Select Address on Map", fontWeight = FontWeight.Bold) },
            text = {
                Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    GoogleMap(
                        cameraPositionState = cameraPositionState,
                        onMapClick = { latLng ->
                            markerState.position = latLng // Tap updates marker placement
                        }
                    ) {
                        Marker(state = markerState)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val selectedPos = markerState.position
                        viewModel.latitude = selectedPos.latitude
                        viewModel.longitude = selectedPos.longitude

                        // Perform reverse geocoding via standard Geocoder API
                        try {
                            val geocoder = android.location.Geocoder(context, Locale.getDefault())
                            val addresses = geocoder.getFromLocation(selectedPos.latitude, selectedPos.longitude, 1)
                            if (!addresses.isNullOrEmpty()) {
                                viewModel.address = addresses[0].getAddressLine(0) ?: ""
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            // Fallback to raw coordinate decimals if geocoding fails offline
                            viewModel.address = "${selectedPos.latitude}, ${selectedPos.longitude}"
                        }
                        showMapPicker = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Confirm Location")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMapPicker = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    if (showDatePicker) {
        WheelDatePickerDialog(
            initialDateMillis = viewModel.birthday,
            onDismiss = { showDatePicker = false },
            onConfirm = { selectedMillis ->
                viewModel.birthday = selectedMillis
                showDatePicker = false
            }
        )
    }
}