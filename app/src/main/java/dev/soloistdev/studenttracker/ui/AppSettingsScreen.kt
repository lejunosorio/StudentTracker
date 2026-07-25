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
import androidx.compose.material.icons.filled.CleaningServices
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

    // State managers for the dynamic card badge dropdown [1, 2]
    var activeBadgeField by remember { mutableStateOf(sharedPrefs.getString("card_banner_field", "") ?: "") }
    var availableTemplates by remember { mutableStateOf<List<FormTemplateEntity>>(emptyList()) }
    var dropdownExpanded by remember { mutableStateOf(false) } // Controls dropdown state [2]

    val m3TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
    )

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

            // Card Customization Category (Updated from popup dialog to inline ExposedDropdownMenuBox) [1, 2]
            Text("Card Customization", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val currentLabel = if (activeBadgeField.isEmpty()) "None" else activeBadgeField.replace("_", " ")

                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = currentLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Set Custom Field as Card Badge") },
                            colors = m3TextFieldColors,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            // CORRECTED: Uses modern non-deprecated menuAnchor overload [2]
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        )

                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            // Standard Option: Disable badge [2]
                            DropdownMenuItem(
                                text = { Text("None (Disable Badge)") },
                                onClick = {
                                    activeBadgeField = ""
                                    sharedPrefs.edit().putString("card_banner_field", "").apply()
                                    dropdownExpanded = false
                                    Toast.makeText(context, "Card badges disabled.", Toast.LENGTH_SHORT).show()
                                }
                            )

                            // Dynamic Option list of your custom template fields [1, 2]
                            availableTemplates.forEach { template ->
                                val userFriendlyLabel = template.fieldName.replace("_", " ")
                                DropdownMenuItem(
                                    text = { Text(userFriendlyLabel) },
                                    onClick = {
                                        activeBadgeField = template.fieldName
                                        sharedPrefs.edit().putString("card_banner_field", template.fieldName).apply()
                                        dropdownExpanded = false
                                        Toast.makeText(context, "Card badge set to $userFriendlyLabel", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
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
    }
}