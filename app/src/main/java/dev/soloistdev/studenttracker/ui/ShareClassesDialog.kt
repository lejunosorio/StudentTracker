package dev.soloistdev.studenttracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import dev.soloistdev.studenttracker.data.ClassShareEngine
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.launch

/**
 * Picks which classes to share and what goes with them.
 *
 * Two decisions are separated on purpose. Which classes is a scoping question; what travels with
 * them is a disclosure question, and the answers differ by recipient - a substitute needs names
 * and seats, a colleague taking over the class needs grades, almost nobody needs home addresses.
 * Personal details are therefore off by default and have to be turned on deliberately.
 */
@Composable
fun ShareClassesDialog(
    availableClasses: List<String>,
    onDismiss: () -> Unit,
    /** Opens the sync screen once the selection has been staged for a peer-to-peer transfer. */
    onShareViaP2p: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { StudentRepository(context) }
    val scope = rememberCoroutineScope()

    val selected = remember { mutableStateListOf<String>() }
    var options by remember { mutableStateOf(ClassShareEngine.ShareOptions()) }
    var preview by remember { mutableStateOf<ClassShareEngine.SharePreview?>(null) }
    var isSharing by remember { mutableStateOf(false) }

    // Recomputed whenever the scope or the disclosure switches change, so the summary always
    // describes what pressing Share would actually send.
    LaunchedEffect(selected.toList(), options) {
        preview = if (selected.isEmpty()) null
        else ClassShareEngine.preview(repository, selected.toSet(), options)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_classes_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (availableClasses.isEmpty()) {
                    Text(stringResource(R.string.share_classes_none), fontSize = 13.sp)
                    return@Column
                }

                Text(
                    text = stringResource(R.string.share_classes_pick),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                availableClasses.forEach { name ->
                    val isOn = selected.contains(name)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (isOn) selected.remove(name) else selected.add(name) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isOn,
                            onCheckedChange = { if (isOn) selected.remove(name) else selected.add(name) }
                        )
                        Text(name, fontSize = 13.sp)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = stringResource(R.string.share_classes_include),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                ShareToggle(stringResource(R.string.share_opt_attendance), options.includeAttendance) {
                    options = options.copy(includeAttendance = it)
                }
                ShareToggle(stringResource(R.string.share_opt_grades), options.includeGrades) {
                    options = options.copy(includeGrades = it)
                }
                ShareToggle(stringResource(R.string.share_opt_seating), options.includeSeating) {
                    options = options.copy(includeSeating = it)
                }
                ShareToggle(stringResource(R.string.share_opt_behaviour), options.includeBehaviour) {
                    options = options.copy(includeBehaviour = it)
                }
                ShareToggle(stringResource(R.string.share_opt_contact), options.includeContactDetails, sensitive = true) {
                    options = options.copy(includeContactDetails = it)
                }
                ShareToggle(stringResource(R.string.share_opt_guardians), options.includeGuardians, sensitive = true) {
                    options = options.copy(includeGuardians = it)
                }

                preview?.let { p ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(
                            text = stringResource(
                                R.string.share_classes_summary,
                                p.students, p.attendanceSheets, p.assessments, p.behaviourNotes
                            ),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            // Two destinations, not two formats: the same document either goes out through the
            // system share sheet or straight to a nearby device. Both honour the switches above.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = selected.isNotEmpty() && !isSharing,
                    onClick = {
                        isSharing = true
                        scope.launch {
                            val staged = ClassShareEngine.stageClassesForP2p(
                                context, repository, selected.toSet(), options
                            )
                            isSharing = false
                            onDismiss()
                            if (staged) onShareViaP2p()
                        }
                    }
                ) {
                    Text(stringResource(R.string.share_action_p2p), fontSize = 13.sp)
                }

                Button(
                    enabled = selected.isNotEmpty() && !isSharing,
                    onClick = {
                        isSharing = true
                        scope.launch {
                            ClassShareEngine.shareClasses(context, repository, selected.toSet(), options)
                            isSharing = false
                            onDismiss()
                        }
                    }
                ) {
                    if (isSharing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.share_classes_action), fontSize = 13.sp)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
private fun ShareToggle(
    label: String,
    checked: Boolean,
    sensitive: Boolean = false,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            // Personal data reads differently from coursework, so it looks different too
            color = if (sensitive && checked) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
