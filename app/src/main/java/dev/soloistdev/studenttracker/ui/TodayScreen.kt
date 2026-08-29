package dev.soloistdev.studenttracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Upcoming
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.ClassSchedule
import dev.soloistdev.studenttracker.data.StudentEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The morning screen: what is on now, who was missing last time, what is outstanding.
 *
 * Scoped to the class in front of the teacher when one is in session, because that is the group
 * they can actually do something about in the next hour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onBack: () -> Unit,
    onOpenClass: (String) -> Unit,
    onOpenAttendance: () -> Unit,
    onStudentClick: (Int) -> Unit,
    onOpenInsights: () -> Unit,
    viewModel: TodayViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    // The ViewModel outlives this screen, so a return visit would otherwise show a stale morning.
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.today_title), fontWeight = FontWeight.Bold) },
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
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val target = state.current ?: state.next
            if (target != null) {
                ScheduleCard(
                    target = target,
                    isLive = state.current != null,
                    classSize = state.currentClassSize,
                    onOpenClass = { onOpenClass(target.classroom.name) },
                    onTakeRoll = onOpenAttendance
                )
            } else {
                EmptyNote(stringResource(R.string.today_no_classes))
            }

            state.summary?.let { summary ->
                if (summary.students > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenInsights() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.today_class_pulse),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Stat(
                                    label = stringResource(R.string.today_stat_attendance),
                                    value = summary.attendanceRate?.let {
                                        String.format(Locale.US, "%.0f%%", it)
                                    } ?: "--",
                                    modifier = Modifier.weight(1f)
                                )
                                Stat(
                                    label = stringResource(R.string.today_stat_average),
                                    value = summary.averageGrade?.let {
                                        String.format(Locale.US, "%.0f%%", it)
                                    } ?: "--",
                                    modifier = Modifier.weight(1f)
                                )
                                Stat(
                                    label = stringResource(R.string.today_stat_flagged),
                                    value = (summary.atRisk + summary.watch).toString(),
                                    modifier = Modifier.weight(1f),
                                    tint = if (summary.atRisk > 0) MaterialTheme.colorScheme.error else null
                                )
                            }
                        }
                    }
                }
            }

            if (state.onStreak.isNotEmpty()) {
                StudentListCard(
                    title = stringResource(R.string.today_absence_streak),
                    icon = Icons.Default.EventBusy,
                    tint = MaterialTheme.colorScheme.error,
                    rows = state.onStreak.map { (student, days) ->
                        student to stringResource(R.string.today_days_running, days)
                    },
                    onStudentClick = onStudentClick
                )
            }

            if (state.absentYesterday.isNotEmpty()) {
                StudentListCard(
                    title = stringResource(R.string.today_absent_last_session),
                    icon = Icons.Default.EventBusy,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    rows = state.absentYesterday.map { it to "" },
                    onStudentClick = onStudentClick
                )
            }

            if (state.openConcerns.isNotEmpty()) {
                StudentListCard(
                    title = stringResource(R.string.today_open_concerns),
                    icon = Icons.Default.Flag,
                    tint = Color(0xFFEF6C00),
                    rows = state.openConcerns.map { (student, count) ->
                        student to stringResource(R.string.today_unresolved, count)
                    },
                    onStudentClick = onStudentClick
                )
            }

            if (state.dueSoon.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Assignment,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.today_due),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        val sdf = SimpleDateFormat("MMM dd", Locale.US)
                        state.dueSoon.forEach { due ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(due.column.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = stringResource(
                                            R.string.today_outstanding,
                                            due.outstanding,
                                            sdf.format(Date(due.column.dueDate))
                                        ),
                                        fontSize = 11.sp,
                                        color = if (due.isOverdue) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val nothingToFlag = state.onStreak.isEmpty() &&
                    state.absentYesterday.isEmpty() &&
                    state.openConcerns.isEmpty() &&
                    state.dueSoon.isEmpty()
            if (nothingToFlag && state.summary != null && state.summary!!.students > 0) {
                EmptyNote(stringResource(R.string.today_all_clear))
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    target: ClassSchedule.ScheduledClass,
    isLive: Boolean,
    classSize: Int,
    onOpenClass: () -> Unit,
    onTakeRoll: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpenClass() },
        colors = CardDefaults.cardColors(
            containerColor = if (isLive) MaterialTheme.colorScheme.tertiaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isLive) Icons.Default.Schedule else Icons.Default.Upcoming,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when {
                        isLive -> stringResource(R.string.today_in_session)
                        target.isTomorrow -> stringResource(R.string.today_up_next_later)
                        else -> stringResource(R.string.today_up_next)
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(target.classroom.name, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${target.classroom.startTime} - ${target.classroom.endTime}  •  " +
                        stringResource(R.string.today_students, classSize),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onTakeRoll,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.today_take_roll), fontSize = 12.sp) }
                OutlinedButton(
                    onClick = onOpenClass,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.today_open_class), fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun Stat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = tint ?: MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun StudentListCard(
    title: String,
    icon: ImageVector,
    tint: Color,
    rows: List<Pair<StudentEntity, String>>,
    onStudentClick: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = tint)
                Spacer(Modifier.width(6.dp))
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tint)
            }
            rows.forEach { (student, detail) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStudentClick(student.id) }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${student.lastName}, ${student.firstName}",
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (detail.isNotEmpty()) {
                        Text(
                            text = detail,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}
