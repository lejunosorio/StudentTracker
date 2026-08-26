package dev.soloistdev.studenttracker.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import dev.soloistdev.studenttracker.LocaleHelper
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.FormTemplateEntity
import dev.soloistdev.studenttracker.data.StudentRepository

/**
 * How the app looks, and what a student card shows.
 *
 * Grouped together because they answer the same question - what do I see - even though one is
 * theming and the other reads from the roster.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { StudentRepository(context) }
    val sharedPrefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }

    var dynamicColorsEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("dynamic_colors", true)) }
    var appTheme by remember { mutableStateOf(sharedPrefs.getString("app_theme", "System") ?: "System") }
    var themeDropdownExpanded by remember { mutableStateOf(false) }

    var selectedLanguage by remember { mutableStateOf(LocaleHelper.current(context)) }
    var languageDropdownExpanded by remember { mutableStateOf(false) }

    var activeBadgeField by remember { mutableStateOf(sharedPrefs.getString("card_banner_field", "") ?: "") }
    var availableTemplates by remember { mutableStateOf<List<FormTemplateEntity>>(emptyList()) }
    var badgeDropdownExpanded by remember { mutableStateOf(false) }

    val m3TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
    )

    val cardBadgeDisabledMsg = stringResource(R.string.settings_card_badge_none)

    LaunchedEffect(Unit) {
        availableTemplates = repository.getAllFormTemplates()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_appearance), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection(stringResource(R.string.settings_cat_theme)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_dynamic_colors),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.settings_dynamic_colors_desc),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Switch(
                        checked = dynamicColorsEnabled,
                        onCheckedChange = { enabled ->
                            dynamicColorsEnabled = enabled
                            sharedPrefs.edit { putBoolean("dynamic_colors", enabled) }
                        }
                    )
                }

                SettingsDivider()

                val themeLabel = when (appTheme) {
                    "Light" -> "Light Theme"
                    "Dark" -> "Dark Theme"
                    else -> "System Default"
                }

                ExposedDropdownMenuBox(
                    expanded = themeDropdownExpanded,
                    onExpandedChange = { themeDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = themeLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.s_app_theme_configuration)) },
                        colors = m3TextFieldColors,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    )
                    ExposedDropdownMenu(
                        expanded = themeDropdownExpanded,
                        onDismissRequest = { themeDropdownExpanded = false }
                    ) {
                        listOf(
                            "System" to "System Default",
                            "Light" to "Light Theme",
                            "Dark" to "Dark Theme"
                        ).forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    appTheme = value
                                    sharedPrefs.edit { putString("app_theme", value) }
                                    themeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            SettingsSection(stringResource(R.string.settings_cat_language)) {
                // Recreating the activity is what makes the change take effect immediately: every
                // already-composed string is re-resolved against the new locale on the way back up.
                ExposedDropdownMenuBox(
                    expanded = languageDropdownExpanded,
                    onExpandedChange = { languageDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = LocaleHelper.displayName(selectedLanguage),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.settings_language_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageDropdownExpanded) },
                        colors = m3TextFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    )
                    ExposedDropdownMenu(
                        expanded = languageDropdownExpanded,
                        onDismissRequest = { languageDropdownExpanded = false }
                    ) {
                        LocaleHelper.SUPPORTED.forEach { code ->
                            DropdownMenuItem(
                                text = { Text(LocaleHelper.displayName(code)) },
                                onClick = {
                                    languageDropdownExpanded = false
                                    if (code != selectedLanguage) {
                                        selectedLanguage = code
                                        LocaleHelper.set(context, code)
                                        (context as? android.app.Activity)?.recreate()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            SettingsSection(stringResource(R.string.settings_card_customization)) {
                Text(
                    text = stringResource(R.string.settings_card_badge_help),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                val currentLabel = if (activeBadgeField.isEmpty()) {
                    stringResource(R.string.settings_card_badge_none)
                } else {
                    activeBadgeField.replace("_", " ")
                }

                ExposedDropdownMenuBox(
                    expanded = badgeDropdownExpanded,
                    onExpandedChange = { badgeDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = currentLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.settings_card_badge_label)) },
                        colors = m3TextFieldColors,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = badgeDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                    )
                    ExposedDropdownMenu(
                        expanded = badgeDropdownExpanded,
                        onDismissRequest = { badgeDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_card_badge_none)) },
                            onClick = {
                                activeBadgeField = ""
                                sharedPrefs.edit { putString("card_banner_field", "") }
                                badgeDropdownExpanded = false
                                Toast.makeText(context, cardBadgeDisabledMsg, Toast.LENGTH_SHORT).show()
                            }
                        )

                        availableTemplates.forEach { template ->
                            val userFriendlyLabel = template.fieldName.replace("_", " ")
                            val setBadgeMsg = stringResource(R.string.settings_card_badge_set_toast, userFriendlyLabel)

                            DropdownMenuItem(
                                text = { Text(userFriendlyLabel) },
                                onClick = {
                                    activeBadgeField = template.fieldName
                                    sharedPrefs.edit { putString("card_banner_field", template.fieldName) }
                                    badgeDropdownExpanded = false
                                    Toast.makeText(context, setBadgeMsg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                if (availableTemplates.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_card_badge_no_fields),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
