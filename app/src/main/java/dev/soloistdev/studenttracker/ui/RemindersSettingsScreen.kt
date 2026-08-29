package dev.soloistdev.studenttracker.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.ReminderSettings
import dev.soloistdev.studenttracker.data.StudentRepository
import dev.soloistdev.studenttracker.data.TodayDigest
import dev.soloistdev.studenttracker.notifications.Notifier
import dev.soloistdev.studenttracker.notifications.ReminderScheduler
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * What the app is allowed to interrupt the teacher about.
 *
 * Every switch here re-arms the alarms on the way out, because a setting that only takes effect
 * after the next launch is a setting a user concludes is broken.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(ReminderSettings.isEnabled(context)) }
    var classNudge by remember { mutableStateOf(ReminderSettings.classNudgeEnabled(context)) }
    var lead by remember { mutableIntStateOf(ReminderSettings.leadMinutes(context)) }
    var digest by remember { mutableStateOf(ReminderSettings.digestEnabled(context)) }
    var digestHour by remember { mutableIntStateOf(ReminderSettings.digestHour(context)) }
    var digestMinute by remember { mutableIntStateOf(ReminderSettings.digestMinute(context)) }
    var alertStreaks by remember { mutableStateOf(ReminderSettings.alertStreaks(context)) }
    var alertAbsent by remember { mutableStateOf(ReminderSettings.alertAbsentLastSession(context)) }
    var alertConcerns by remember { mutableStateOf(ReminderSettings.alertOpenConcerns(context)) }
    var alertOverdue by remember { mutableStateOf(ReminderSettings.alertOverdueMarking(context)) }
    var showNames by remember { mutableStateOf(ReminderSettings.showNames(context)) }
    var hasPermission by remember { mutableStateOf(Notifier.hasPermission(context)) }

    var showTimePicker by remember { mutableStateOf(false) }
    var showLeadPicker by remember { mutableStateOf(false) }

    fun reschedule() {
        scope.launch { ReminderScheduler.reschedule(context) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            enabled = true
            ReminderSettings.setEnabled(context, true)
            reschedule()
        }
    }

    fun requestPermissionThenEnable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            hasPermission = true
            enabled = true
            ReminderSettings.setEnabled(context, true)
            reschedule()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.reminders_title), fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SwitchRow(
                        title = stringResource(R.string.reminders_master),
                        subtitle = stringResource(R.string.reminders_master_desc),
                        checked = enabled,
                        onCheckedChange = { wanted ->
                            if (wanted && !hasPermission) {
                                requestPermissionThenEnable()
                            } else {
                                enabled = wanted
                                ReminderSettings.setEnabled(context, wanted)
                                if (wanted) reschedule() else ReminderScheduler.cancelAll(context)
                            }
                        }
                    )

                    if (!hasPermission) {
                        Text(
                            text = stringResource(R.string.reminders_permission_needed),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = { requestPermissionThenEnable() }) {
                            Text(stringResource(R.string.reminders_grant), fontSize = 12.sp)
                        }
                    }
                }
            }

            if (enabled && hasPermission) {
                SettingsSection(title = stringResource(R.string.reminders_section_when)) {
                    SwitchRow(
                        title = stringResource(R.string.reminders_class_nudge),
                        subtitle = stringResource(R.string.reminders_class_nudge_desc),
                        checked = classNudge,
                        onCheckedChange = {
                            classNudge = it
                            ReminderSettings.setClassNudgeEnabled(context, it)
                            reschedule()
                        }
                    )

                    if (classNudge) {
                        SettingsDivider()
                        ValueRow(
                            title = stringResource(R.string.reminders_lead),
                            value = stringResource(R.string.reminders_lead_value, lead),
                            onClick = { showLeadPicker = true }
                        )
                    }

                    SettingsDivider()

                    SwitchRow(
                        title = stringResource(R.string.reminders_digest),
                        subtitle = stringResource(R.string.reminders_digest_desc),
                        checked = digest,
                        onCheckedChange = {
                            digest = it
                            ReminderSettings.setDigestEnabled(context, it)
                            reschedule()
                        }
                    )

                    if (digest) {
                        SettingsDivider()
                        ValueRow(
                            title = stringResource(R.string.reminders_digest_time),
                            value = formatTime(digestHour, digestMinute),
                            onClick = { showTimePicker = true }
                        )
                    }

                    Text(
                        text = stringResource(R.string.reminders_inexact_note),
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                SettingsSection(title = stringResource(R.string.reminders_section_what)) {
                    SwitchRow(
                        title = stringResource(R.string.reminders_alert_streaks),
                        subtitle = stringResource(R.string.reminders_alert_streaks_desc),
                        checked = alertStreaks,
                        onCheckedChange = {
                            alertStreaks = it
                            ReminderSettings.setAlertStreaks(context, it)
                        }
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = stringResource(R.string.reminders_alert_absent),
                        subtitle = stringResource(R.string.reminders_alert_absent_desc),
                        checked = alertAbsent,
                        onCheckedChange = {
                            alertAbsent = it
                            ReminderSettings.setAlertAbsentLastSession(context, it)
                        }
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = stringResource(R.string.reminders_alert_concerns),
                        subtitle = stringResource(R.string.reminders_alert_concerns_desc),
                        checked = alertConcerns,
                        onCheckedChange = {
                            alertConcerns = it
                            ReminderSettings.setAlertOpenConcerns(context, it)
                        }
                    )
                    SettingsDivider()
                    SwitchRow(
                        title = stringResource(R.string.reminders_alert_overdue),
                        subtitle = stringResource(R.string.reminders_alert_overdue_desc),
                        checked = alertOverdue,
                        onCheckedChange = {
                            alertOverdue = it
                            ReminderSettings.setAlertOverdueMarking(context, it)
                        }
                    )
                }

                SettingsSection(title = stringResource(R.string.reminders_section_privacy)) {
                    SwitchRow(
                        title = stringResource(R.string.reminders_show_names),
                        subtitle = stringResource(R.string.reminders_show_names_desc),
                        checked = showNames,
                        onCheckedChange = {
                            showNames = it
                            ReminderSettings.setShowNames(context, it)
                        }
                    )
                }

                SettingsSection(title = stringResource(R.string.reminders_test)) {
                    SettingsActionRow(
                        title = stringResource(R.string.reminders_test),
                        subtitle = stringResource(R.string.reminders_test_desc),
                        icon = Icons.Default.NotificationsActive,
                        onClick = {
                            scope.launch {
                                val computed = TodayDigest.compute(StudentRepository(context))
                                Notifier.postDigest(context, computed)
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        if (computed.hasAnythingToFlag) R.string.reminders_test_sent
                                        else R.string.reminders_test_empty
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }

    if (showLeadPicker) {
        AlertDialog(
            onDismissRequest = { showLeadPicker = false },
            title = { Text(stringResource(R.string.reminders_lead), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    ReminderSettings.LEAD_CHOICES.forEach { choice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    lead = choice
                                    ReminderSettings.setLeadMinutes(context, choice)
                                    showLeadPicker = false
                                    reschedule()
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = lead == choice, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.reminders_lead_value, choice), fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLeadPicker = false }) {
                    Text(stringResource(R.string.s_done))
                }
            }
        )
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = digestHour,
            initialMinute = digestMinute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.reminders_digest_time), fontWeight = FontWeight.Bold) },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                Button(onClick = {
                    digestHour = timeState.hour
                    digestMinute = timeState.minute
                    ReminderSettings.setDigestTime(context, digestHour, digestMinute)
                    showTimePicker = false
                    reschedule()
                }) {
                    Text(stringResource(R.string.s_done))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.s_cancel))
                }
            }
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun formatTime(hour: Int, minute: Int): String {
    val suffix = if (hour < 12) "AM" else "PM"
    val display = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return String.format(Locale.US, "%d:%02d %s", display, minute, suffix)
}
