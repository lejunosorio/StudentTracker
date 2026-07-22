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
import androidx.compose.ui.viewinterop.AndroidView
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.ArchiveFileFactory
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentMapScreen(
    studentId: Int, // Pass -1 for global map, or a valid ID for single-student focus
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { StudentRepository(context) }

    var studentsList by remember { mutableStateOf<List<StudentEntity>>(emptyList()) }
    var activeArchiveFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) {
        studentsList = repository.getAllActiveStudents()
        val activeArchive = repository.getAllMapArchives().find { it.isActive }
        activeArchive?.let {
            val file = File(it.filePath)
            if (file.exists()) {
                activeArchiveFile = file
            }
        }
    }

    // Configure OSMDroid User-Agent parameter required for caching integrity
    Configuration.getInstance().userAgentValue = context.packageName

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (studentId == -1) "Choir GPS Map (Offline)" else "Member Location",
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
            var mapViewInstance by remember { mutableStateOf<MapView?>(null) }

            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setMultiTouchControls(true)

                        val archive = activeArchiveFile
                        if (archive != null) {
                            try {
                                // Load imported map package completely offline
                                val offlineProvider = OfflineTileProvider(
                                    org.osmdroid.tileprovider.util.SimpleRegisterReceiver(ctx),
                                    arrayOf(archive)
                                )
                                this.tileProvider = offlineProvider

                                val archive = activeArchiveFile
                                if (archive != null) {
                                    try {
                                        // Load imported map package completely offline
                                        val offlineProvider = OfflineTileProvider(
                                            org.osmdroid.tileprovider.util.SimpleRegisterReceiver(ctx),
                                            arrayOf(archive)
                                        )
                                        this.tileProvider = offlineProvider

                                        // CORRECTED: Changed to getArchiveFile (singular)
                                        val archiveFile = ArchiveFileFactory.getArchiveFile(archive)
                                        val tileSources = archiveFile?.tileSources // Maps to getTileSources()

                                        if (!tileSources.isNullOrEmpty()) {
                                            val tileSource = TileSourceFactory.getTileSource(tileSources.first())
                                            this.setTileSource(tileSource)
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        this.setTileSource(TileSourceFactory.MAPNIK) // Online fallback
                                    }
                                } else {
                                    this.setTileSource(TileSourceFactory.MAPNIK) // Online default
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                this.setTileSource(TileSourceFactory.MAPNIK) // Online fallback
                            }
                        } else {
                            this.setTileSource(TileSourceFactory.MAPNIK) // Online default
                        }

                        mapViewInstance = this
                    }
                },
                update = { mapView ->
                    mapView.overlays.clear()

                    // Enable user location blue dot via phone hardware sensor (requires no cellular data)
                    val locationProvider = GpsMyLocationProvider(context)
                    val myLocationOverlay = MyLocationNewOverlay(locationProvider, mapView)
                    myLocationOverlay.enableMyLocation()
                    mapView.overlays.add(myLocationOverlay)

                    // Default center coordinates (Manila/Taguig area)
                    val defaultCenter = GeoPoint(14.5547, 121.0509)
                    mapView.controller.setZoom(13.0)
                    mapView.controller.setCenter(defaultCenter)

                    if (studentId == -1) {
                        // Plot coordinates dynamically for all students
                        studentsList.forEach { student ->
                            val customJson = try { JSONObject(student.customDataJson) } catch (e: Exception) { JSONObject() }
                            val lat = customJson.optDouble("latitude", 0.0)
                            val lng = customJson.optDouble("longitude", 0.0)

                            if (lat != 0.0 && lng != 0.0) {
                                val marker = Marker(mapView).apply {
                                    position = GeoPoint(lat, lng)
                                    title = "${student.lastName}, ${student.firstName}"
                                    subDescription = student.address
                                }
                                mapView.overlays.add(marker)
                            }
                        }
                    } else {
                        // Plot coordinates for the selected student profile
                        val focusedStudent = studentsList.find { it.id == studentId }
                        focusedStudent?.let { student ->
                            val customJson = try { JSONObject(student.customDataJson) } catch (e: Exception) { JSONObject() }
                            val lat = customJson.optDouble("latitude", 0.0)
                            val lng = customJson.optDouble("longitude", 0.0)

                            if (lat != 0.0 && lng != 0.0) {
                                val studentPoint = GeoPoint(lat, lng)
                                val marker = Marker(mapView).apply {
                                    position = studentPoint
                                    title = "${student.lastName}, ${student.firstName}"
                                    subDescription = student.address
                                }
                                mapView.overlays.add(marker)
                                mapView.controller.setZoom(17.0)
                                mapView.controller.setCenter(studentPoint)
                            } else {
                                Toast.makeText(context, "No coordinates recorded for this student.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    mapView.invalidate()
                },
                modifier = Modifier.fillMaxSize()
            )

            // Zoom and GPS Locate controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 32.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = { mapViewInstance?.controller?.zoomIn() },
                    containerColor = Color.White,
                    contentColor = Color(0xFF49454F),
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In")
                }

                FloatingActionButton(
                    onClick = { mapViewInstance?.controller?.zoomOut() },
                    containerColor = Color.White,
                    contentColor = Color(0xFF49454F),
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                }

                FloatingActionButton(
                    onClick = {
                        val defaultCenter = GeoPoint(14.5547, 121.0509)
                        mapViewInstance?.controller?.animateTo(defaultCenter, 14.0, 1000L)
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