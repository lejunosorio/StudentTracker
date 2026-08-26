package dev.soloistdev.studenttracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.ParticipationCountEntity
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.launch

/**
 * Equity-aware cold-call picker.
 *
 * Picks uniformly at random from whoever has been called the fewest times, rather than from the
 * whole class. That guarantees a full rotation before anyone is asked twice - the point being
 * that teachers reliably believe their questioning is more evenly spread than it actually is,
 * and a plain shuffle would preserve the imbalance it is meant to fix.
 */
@Composable
fun ColdCallDialog(
    className: String,
    students: List<StudentEntity>,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { StudentRepository(context) }
    val scope = rememberCoroutineScope()

    var counts by remember { mutableStateOf<List<ParticipationCountEntity>>(emptyList()) }
    var picked by remember { mutableStateOf<StudentEntity?>(null) }
    var showRoster by remember { mutableStateOf(false) }

    suspend fun refresh() {
        counts = repository.getParticipationForClass(className)
    }

    LaunchedEffect(className) { refresh() }

    val countByStudent = remember(counts) { counts.associateBy { it.studentId } }
    val eligible = students.filter { it.getClassNamesList().contains(className) || className.isBlank() }

    fun callsFor(student: StudentEntity): Int = countByStudent[student.id]?.timesCalled ?: 0

    fun pickNext() {
        if (eligible.isEmpty()) return
        val fewest = eligible.minOf { callsFor(it) }
        val pool = eligible.filter { callsFor(it) == fewest }
        val choice = pool.random()
        picked = choice
        scope.launch {
            repository.recordParticipation(choice.id, className)
            refresh()
        }
    }

    val roundsComplete = if (eligible.isEmpty()) 0 else eligible.minOf { callsFor(it) }
    val remainingThisRound = eligible.count { callsFor(it) == roundsComplete }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cold Call", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (eligible.isEmpty()) {
                    Text("No students in this class.", fontSize = 13.sp)
                    return@Column
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = picked?.let { "${it.firstName} ${it.lastName}" } ?: "Tap pick to start",
                            fontSize = if (picked != null) 22.sp else 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        picked?.let { student ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Called ${callsFor(student)} time(s)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Text(
                    text = "$remainingThisRound of ${eligible.size} still to be asked this round.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { pickNext() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.s_pick_a_student))
                }

                TextButton(onClick = { showRoster = !showRoster }) {
                    Text(if (showRoster) "Hide tally" else "Show tally", fontSize = 12.sp)
                }

                if (showRoster) {
                    eligible.sortedWith(compareBy({ callsFor(it) }, { it.lastName.lowercase() })).forEach { student ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${student.lastName}, ${student.firstName}", fontSize = 12.sp)
                            Text(callsFor(student).toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    TextButton(onClick = {
                        scope.launch {
                            repository.resetParticipationForClass(className)
                            picked = null
                            refresh()
                        }
                    }) {
                        Text("Reset tally", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.s_done)) } },
        shape = RoundedCornerShape(28.dp)
    )
}
