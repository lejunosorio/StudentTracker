package dev.soloistdev.studenttracker.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DisplaySettings // Customization icon [1]
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.data.AppDatabase
import dev.soloistdev.studenttracker.data.FormTemplateEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { StudentRepository(context) }

    val sharedPrefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }

    var dynamicColorsEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("dynamic_colors", true)) }
    var darkThemeEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("force_dark_theme", true)) }

    // State managers for the dynamic card badge dialog [1]
    var showBadgeDialog by remember { mutableStateOf(false) }
    var activeBadgeField by remember { mutableStateOf(sharedPrefs.getString("card_banner_field", "") ?: "") }
    var availableTemplates by remember { mutableStateOf<List<FormTemplateEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        availableTemplates = repository.getAllFormTemplates()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("App Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
            // Appearance Category
            Text("Appearance", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dynamic M3 Coloring", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Theme matches device wallpaper style", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Switch(
                            checked = dynamicColorsEnabled,
                            onCheckedChange = { enabled ->
                                dynamicColorsEnabled = enabled
                                sharedPrefs.edit().putBoolean("dynamic_colors", enabled).apply()
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Force Dark Theme", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Override system UI appearance standards", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Switch(
                            checked = darkThemeEnabled,
                            onCheckedChange = { enabled ->
                                darkThemeEnabled = enabled
                                sharedPrefs.edit().putBoolean("force_dark_theme", enabled).apply()
                            }
                        )
                    }
                }
            }

            // NEW: Card Customization Category [1]
            Text("Card Customization", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showBadgeDialog = true } // Launches choice dialog [1]
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Set Custom Field as Card Badge", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (activeBadgeField.isEmpty()) "None" else "Active: ${activeBadgeField.replace("_", " ")}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(Icons.Default.DisplaySettings, contentDescription = "Customize Card", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // Storage and Local Maintenance Category
            Text("Storage & Database", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val mapsDir = File(context.filesDir, "offline_maps")
                                        if (mapsDir.exists()) mapsDir.deleteRecursively()
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Map cache cleared successfully.", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Clear Map Cache", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Deletes cached offline tiles • Reclaims Storage", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Icon(Icons.Default.CleaningServices, contentDescription = "Clean", tint = MaterialTheme.colorScheme.primary)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val db = AppDatabase.getDatabase(context)
                                        db.openHelper.writableDatabase.execSQL("VACUUM")
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Database compacted successfully!", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Compact Local Database", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Executes SQLite VACUUM optimization", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Icon(Icons.Default.Storage, contentDescription = "Vacuum", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Student Manager v1.0.0-Beta\nZero Licensing Costs Assured",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }

        // M3 MULTI-CHOICE BADGE DIALOG [1]
        if (showBadgeDialog) {
            AlertDialog(
                onDismissRequest = { showBadgeDialog = false },
                title = { Text("Set Card Badge Field", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Standard option: Disable Badge
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    activeBadgeField = ""
                                    sharedPrefs.edit().putString("card_banner_field", "").apply()
                                    showBadgeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("None (Disable Badge)", fontSize = 15.sp)
                            RadioButton(
                                selected = activeBadgeField.isEmpty(),
                                onClick = {
                                    activeBadgeField = ""
                                    sharedPrefs.edit().putString("card_banner_field", "").apply()
                                    showBadgeDialog = false
                                }
                            )
                        }

                        // List all templates as choices dynamically [1]
                        availableTemplates.forEach { template ->
                            val userFriendlyLabel = template.fieldName.replace("_", " ")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        activeBadgeField = template.fieldName
                                        sharedPrefs.edit().putString("card_banner_field", template.fieldName).apply()
                                        showBadgeDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(userFriendlyLabel, fontSize = 15.sp)
                                RadioButton(
                                    selected = activeBadgeField == template.fieldName,
                                    onClick = {
                                        activeBadgeField = template.fieldName
                                        sharedPrefs.edit().putString("card_banner_field", template.fieldName).apply()
                                        showBadgeDialog = false
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showBadgeDialog = false }) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}