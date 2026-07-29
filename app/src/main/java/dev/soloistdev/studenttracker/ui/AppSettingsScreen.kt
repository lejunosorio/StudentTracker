package dev.soloistdev.studenttracker.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import dev.soloistdev.studenttracker.R
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

    var activeBadgeField by remember { mutableStateOf(sharedPrefs.getString("card_banner_field", "") ?: "") }
    var availableTemplates by remember { mutableStateOf<List<FormTemplateEntity>>(emptyList()) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val m3TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
    )

    // Pre-read system Toast resources safely inside Composable scope to prevent stale locale caches [1]
    val cardBadgeDisabledMsg = stringResource(R.string.settings_card_badge_disabled_toast)
    val clearMapsSuccessMsg = stringResource(R.string.settings_clear_maps_success)
    val dbCompactSuccessMsg = stringResource(R.string.settings_compact_db_success)

    LaunchedEffect(Unit) {
        availableTemplates = repository.getAllFormTemplates()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.menu_app_settings), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.menu_app_settings))
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
            Text(stringResource(R.string.settings_appearance), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

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
                            Text(stringResource(R.string.settings_dynamic_colors), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.settings_dynamic_colors_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Switch(
                            checked = dynamicColorsEnabled,
                            onCheckedChange = { enabled ->
                                dynamicColorsEnabled = enabled
                                sharedPrefs.edit { putBoolean("dynamic_colors", enabled) }
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
                            Text(stringResource(R.string.settings_force_dark), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.settings_force_dark_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Switch(
                            checked = darkThemeEnabled,
                            onCheckedChange = { enabled ->
                                darkThemeEnabled = enabled
                                sharedPrefs.edit { putBoolean("force_dark_theme", enabled) }
                            }
                        )
                    }
                }
            }

            // Card Customization Category
            Text(stringResource(R.string.settings_card_customization), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val currentLabel = if (activeBadgeField.isEmpty()) stringResource(R.string.settings_card_badge_none) else activeBadgeField.replace("_", " ")

                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = currentLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.settings_card_badge_label)) },
                            colors = m3TextFieldColors,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        )

                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_card_badge_none)) },
                                onClick = {
                                    activeBadgeField = ""
                                    sharedPrefs.edit { putString("card_banner_field", "") }
                                    dropdownExpanded = false
                                    Toast.makeText(context, cardBadgeDisabledMsg, Toast.LENGTH_SHORT).show()
                                }
                            )

                            availableTemplates.forEach { template ->
                                val userFriendlyLabel = template.fieldName.replace("_", " ")
                                // Resolved: Pre-reads localized format strings inside the Composable loop [1]
                                val setBadgeMsg = stringResource(R.string.settings_card_badge_set_toast, userFriendlyLabel)

                                DropdownMenuItem(
                                    text = { Text(userFriendlyLabel) },
                                    onClick = {
                                        activeBadgeField = template.fieldName
                                        sharedPrefs.edit { putString("card_banner_field", template.fieldName) }
                                        dropdownExpanded = false
                                        Toast.makeText(context, setBadgeMsg, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Storage and Local Maintenance Category
            Text(stringResource(R.string.settings_storage_database), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

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
                                            Toast.makeText(context, clearMapsSuccessMsg, Toast.LENGTH_SHORT).show()
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
                            Text(stringResource(R.string.settings_clear_maps), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.settings_clear_maps_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Icon(Icons.Default.CleaningServices, contentDescription = stringResource(R.string.settings_clear_maps), tint = MaterialTheme.colorScheme.primary)
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
                                            Toast.makeText(context, dbCompactSuccessMsg, Toast.LENGTH_SHORT).show()
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
                            Text(stringResource(R.string.settings_compact_db), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.settings_compact_db_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                        Icon(Icons.Default.Storage, contentDescription = stringResource(R.string.settings_compact_db), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.settings_footer_version),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }
    }
}