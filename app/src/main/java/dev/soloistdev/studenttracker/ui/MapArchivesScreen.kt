package dev.soloistdev.studenttracker.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.soloistdev.studenttracker.data.MapArchiveEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

// SPRINT 7 VIEW MODEL: Handles secure map file copying and Zip-Slip audits
class MapArchivesViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val repository = StudentRepository(application)
    private val _archives = MutableStateFlow<List<MapArchiveEntity>>(emptyList())
    val archives: StateFlow<List<MapArchiveEntity>> = _archives

    init {
        loadArchives()
    }

    fun loadArchives() {
        viewModelScope.launch {
            _archives.value = repository.getAllMapArchives()
        }
    }

    fun deleteArchive(archive: MapArchiveEntity) {
        viewModelScope.launch {
            repository.deleteMapArchive(archive.id)
            try {
                File(archive.filePath).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            loadArchives()
        }
    }

    fun toggleActive(archiveId: Int) {
        viewModelScope.launch {
            repository.setActiveMapArchive(archiveId)
            loadArchives()
        }
    }

    // SECURE ZIP-SLIP EXTRACTION ENGINE
    fun importMapArchive(uri: Uri, context: android.content.Context, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentResolver = context.contentResolver
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val fileName = getFileNameFromUri(uri, context) ?: "imported_map.mbtiles"

                val mapsDir = File(context.filesDir, "offline_maps").apply { mkdirs() }
                val targetFile = File(mapsDir, fileName)

                if (fileName.endsWith(".zip")) {
                    // Extracting ZIP (Requires security checks!)
                    val zipInputStream = ZipInputStream(inputStream)
                    var entry: ZipEntry? = zipInputStream.nextEntry
                    var extractionSuccess = false

                    while (entry != null) {
                        val outFile = File(mapsDir, entry.name)

                        // CRITICAL VETERAN SECURITY STEP: Neutralize the ZIP SLIP exploit
                        if (!outFile.canonicalPath.startsWith(mapsDir.canonicalPath)) {
                            throw SecurityException("Malicious path traversal attempt detected!")
                        }

                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            val fos = FileOutputStream(outFile)
                            zipInputStream.copyTo(fos)
                            fos.close()

                            // Save file record to the database
                            withContext(Dispatchers.Main) {
                                repository.insertMapArchive(
                                    MapArchiveEntity(
                                        fileName = entry.name,
                                        filePath = outFile.absolutePath
                                    )
                                )
                            }
                            extractionSuccess = true
                        }
                        zipInputStream.closeEntry()
                        entry = zipInputStream.nextEntry
                    }
                    zipInputStream.close()
                    withContext(Dispatchers.Main) {
                        loadArchives()
                        onResult(extractionSuccess)
                    }
                } else {
                    // Directly copy raw .mbtiles / .gemf files
                    val fos = FileOutputStream(targetFile)
                    inputStream?.copyTo(fos)
                    fos.close()
                    inputStream?.close()

                    withContext(Dispatchers.Main) {
                        repository.insertMapArchive(
                            MapArchiveEntity(
                                fileName = fileName,
                                filePath = targetFile.absolutePath
                            )
                        )
                        loadArchives()
                        onResult(true)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    onResult(false)
                }
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri, context: android.content.Context): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) name = it.getString(index)
            }
        }
        return name
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapArchivesScreen(
    onBack: () -> Unit,
    viewModel: MapArchivesViewModel = viewModel()
) {
    val archives by viewModel.archives.collectAsState()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.importMapArchive(it, context) { success ->
                if (success) {
                    Toast.makeText(context, "Map archive imported successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Error importing. Path Traversal blocked or corrupted file.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Offline Maps Manager", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { filePickerLauncher.launch("*/*") }, // Launches generic file selector
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Import Map File")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                Text(
                    text = "Loaded Map Packages (.mbtiles / .gemf)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(archives) { archive ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).clickable { viewModel.toggleActive(archive.id) }) {
                            Text(
                                text = archive.fileName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Path: App Private Storage\nStatus: ${if (archive.isActive) "Active Provider" else "Tap to Enable"}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                lineHeight = 16.sp
                            )
                        }
                        IconButton(onClick = { viewModel.deleteArchive(archive) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}