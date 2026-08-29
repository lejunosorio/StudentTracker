package dev.soloistdev.studenttracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.R

/**
 * The settings hub.
 *
 * Everything used to live on this one screen - theming, backups, exports, and a button that
 * erases the database - as a single scroll of near-identical cards. Nothing was findable, and the
 * destructive action sat a few centimetres from the theme picker. Each group now opens a screen of
 * its own, so this list stays short enough to read at a glance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    onBack: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToBiometrics: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToReminders: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToSync: () -> Unit,
    onNavigateToCsvImport: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.menu_app_settings), fontWeight = FontWeight.Bold) },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SettingsCategoryRow(
                title = stringResource(R.string.settings_appearance),
                subtitle = stringResource(R.string.settings_cat_appearance_desc),
                icon = Icons.Default.Palette,
                onClick = onNavigateToAppearance
            )

            SettingsCategoryRow(
                title = stringResource(R.string.menu_biometrics_privacy),
                subtitle = stringResource(R.string.settings_cat_security_desc),
                icon = Icons.Default.Security,
                onClick = onNavigateToBiometrics
            )

            SettingsCategoryRow(
                title = stringResource(R.string.settings_cat_reminders),
                subtitle = stringResource(R.string.settings_cat_reminders_desc),
                icon = Icons.Default.NotificationsActive,
                onClick = onNavigateToReminders
            )

            SettingsCategoryRow(
                title = stringResource(R.string.settings_cat_backup),
                subtitle = stringResource(R.string.settings_cat_backup_desc),
                icon = Icons.Default.Backup,
                onClick = onNavigateToBackup
            )

            SettingsCategoryRow(
                title = stringResource(R.string.settings_cat_transfer),
                subtitle = stringResource(R.string.settings_cat_transfer_desc),
                icon = Icons.Default.Wifi,
                onClick = onNavigateToSync
            )

            SettingsCategoryRow(
                title = stringResource(R.string.settings_cat_csv),
                subtitle = stringResource(R.string.settings_cat_csv_desc),
                icon = Icons.Default.TableChart,
                onClick = onNavigateToCsvImport
            )

            // Last, and on its own screen: this is where the database can be erased.
            SettingsCategoryRow(
                title = stringResource(R.string.settings_storage_database),
                subtitle = stringResource(R.string.settings_cat_storage_desc),
                icon = Icons.Default.Storage,
                onClick = onNavigateToStorage
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
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
