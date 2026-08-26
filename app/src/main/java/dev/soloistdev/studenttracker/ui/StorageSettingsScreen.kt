package dev.soloistdev.studenttracker.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Maintenance on the database itself.
 *
 * Kept apart from everything else because it ends in a button that erases every student on the
 * device. Sitting six rows below a theme picker is not where that belongs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showPurgeDialog by remember { mutableStateOf(false) }
    var purgeInputText by remember { mutableStateOf("") }

    val dbCompactSuccessMsg = stringResource(R.string.settings_compact_db_success)
    val confirmWord = stringResource(R.string.settings_purge_confirm_word)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_storage_database), fontWeight = FontWeight.Bold) },
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
            SettingsSection(stringResource(R.string.settings_cat_storage_maintenance)) {
                SettingsActionRow(
                    title = stringResource(R.string.settings_compact_db),
                    subtitle = stringResource(R.string.settings_compact_db_desc),
                    icon = Icons.Default.Storage,
                    onClick = {
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
                    }
                )
            }

            SettingsSection(stringResource(R.string.settings_cat_storage_danger)) {
                SettingsActionRow(
                    title = stringResource(R.string.settings_purge_db),
                    subtitle = stringResource(R.string.settings_purge_db_desc),
                    icon = Icons.Default.Delete,
                    isDestructive = true,
                    onClick = { showPurgeDialog = true }
                )
            }
        }

        if (showPurgeDialog) {
            AlertDialog(
                onDismissRequest = {
                    showPurgeDialog = false
                    purgeInputText = ""
                },
                title = {
                    Text(
                        text = stringResource(R.string.settings_purge_dialog_title),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().imePadding(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_purge_dialog_body, confirmWord),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = purgeInputText,
                            onValueChange = { purgeInputText = it },
                            label = { Text(stringResource(R.string.settings_purge_dialog_label, confirmWord)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPurgeDialog = false
                            purgeInputText = ""
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val db = AppDatabase.getDatabase(context)
                                    db.clearAllTables()
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.toast_database_successfully_purged),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        // Typing the word is the whole safeguard, so it is matched case-insensitively
                        // against the same string the prompt shows rather than a hardcoded literal.
                        enabled = purgeInputText.trim().equals(confirmWord, ignoreCase = true),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.s_purge_all_data))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showPurgeDialog = false
                            purgeInputText = ""
                        }
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
                shape = RoundedCornerShape(28.dp)
            )
        }
    }
}
