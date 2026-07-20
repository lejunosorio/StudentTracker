package dev.soloistdev.studenttracker.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.data.StudentRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentMapScreen(
    studentId: Int, // Pass -1 for global map, or a valid ID for single-student focus
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // ARCHITECTURAL CORRECTION: Use remember to instantiate the repository exactly ONCE
    // This prevents database-decryption thread freezes on recomposition
    val repository = remember { StudentRepository(context) }

    var studentsList by remember { mutableStateOf<List<StudentEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        studentsList = repository.getAllActiveStudents()
    }

    // Default center at Manila/Taguig coords (14.5547, 121.0509)
    val defaultLatLng = remember { LatLng(14.5547, 121.0509) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLatLng, 13f)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (studentId == -1) "Choir GPS Map" else "Member Location",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // NATIVE GOOGLE MAP CONTAINER
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(zoomControlsEnabled = false)
            ) {
                if (studentId == -1) {
                    // GLOBAL MODE: Plot pins dynamically
                    studentsList.forEachIndexed { index, student ->
                        val latOffset = (index % 5) * 0.003 - 0.006
                        val lngOffset = (index / 5) * 0.003 - 0.006
                        val studentPoint = LatLng(14.5547 + latOffset, 121.0509 + lngOffset)

                        Marker(
                            state = MarkerState(position = studentPoint),
                            title = "${student.lastName}, ${student.firstName}",
                            snippet = student.address
                        )
                    }
                } else {
                    // SINGLE MODE: Focus on specific student
                    val focusedStudent = studentsList.find { it.id == studentId }
                    if (focusedStudent != null) {
                        val focusPoint = remember { LatLng(14.5547 + 0.002, 121.0509 - 0.002) }

                        LaunchedEffect(focusPoint) {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newCameraPosition(
                                    CameraPosition.fromLatLngZoom(focusPoint, 16f)
                                )
                            )
                        }

                        Marker(
                            state = rememberMarkerState(position = focusPoint),
                            title = "${focusedStudent.lastName}, ${focusedStudent.firstName}",
                            snippet = focusedStudent.address
                        )
                    }
                }
            }

            // FLOATING ZOOM AND MY_LOCATION CONTROLS
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 32.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = { cameraPositionState.move(CameraUpdateFactory.zoomIn()) },
                    containerColor = Color.White,
                    contentColor = Color(0xFF49454F),
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In")
                }

                FloatingActionButton(
                    onClick = { cameraPositionState.move(CameraUpdateFactory.zoomOut()) },
                    containerColor = Color.White,
                    contentColor = Color(0xFF49454F),
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                }

                FloatingActionButton(
                    onClick = {
                        cameraPositionState.move(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.fromLatLngZoom(defaultLatLng, 14f)
                            )
                        )
                    },
                    containerColor = Color(0xFF6750A4),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Locate")
                }
            }
        }
    }
}