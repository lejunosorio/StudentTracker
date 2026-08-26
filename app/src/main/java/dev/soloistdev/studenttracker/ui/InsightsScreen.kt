package dev.soloistdev.studenttracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.StudentInsights
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    onBack: () -> Unit,
    onStudentClick: (Int) -> Unit,
    viewModel: InsightsViewModel = viewModel()
) {
    val insights by viewModel.insights.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val classFilter by viewModel.classFilter.collectAsState()
    val terms by viewModel.terms.collectAsState()
    val selectedTerm by viewModel.selectedTerm.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val ranked = remember(insights, classFilter) { viewModel.rankedStudents() }
    val classes = remember(insights) { viewModel.availableClasses() }

    var expandedStudentId by remember { mutableIntStateOf(-1) }

    // Re-read on every entry: the ViewModel outlives this composable, so returning here after
    // changing data elsewhere would otherwise show a stale snapshot.
    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    val atRisk = ranked.count { insights[it.id]?.riskLevel == StudentInsights.RiskLevel.AT_RISK }
    val watch = ranked.count { insights[it.id]?.riskLevel == StudentInsights.RiskLevel.WATCH }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Early Warning", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.cd_recalculate))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Grading period first: it decides which weeks every number below is drawn from.
            if (terms.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTerm == null,
                        onClick = { viewModel.setTerm(null) },
                        label = { Text(stringResource(R.string.insights_whole_year)) }
                    )
                    terms.forEach { term ->
                        FilterChip(
                            selected = selectedTerm?.id == term.id,
                            onClick = { viewModel.setTerm(term) },
                            label = { Text(term.name) }
                        )
                    }
                }
            }

            if (classes.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = classFilter == null,
                        onClick = { viewModel.setClassFilter(null) },
                        label = { Text(stringResource(R.string.s_all_classes)) }
                    )
                    classes.forEach { name ->
                        FilterChip(
                            selected = classFilter == name,
                            onClick = { viewModel.setClassFilter(name) },
                            label = { Text(name) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RiskTally("At risk", atRisk, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                RiskTally("Watch", watch, Color(0xFFEF6C00), Modifier.weight(1f))
                RiskTally("Steady", ranked.size - atRisk - watch, Color(0xFF2E7D32), Modifier.weight(1f))
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (ranked.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No students in scope.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ranked, key = { it.id }) { student ->
                        val insight = insights[student.id] ?: return@items
                        InsightRow(
                            name = "${student.lastName}, ${student.firstName}",
                            insight = insight,
                            // Scoped by the ViewModel, so the strip covers the same weeks the
                            // flags above it were calculated from.
                            timeline = if (expandedStudentId == student.id) {
                                viewModel.timelineFor(student.id)
                            } else emptyList(),
                            isExpanded = expandedStudentId == student.id,
                            onToggle = {
                                expandedStudentId = if (expandedStudentId == student.id) -1 else student.id
                            },
                            onOpenProfile = { onStudentClick(student.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskTally(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(count.toString(), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = color)
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InsightRow(
    name: String,
    insight: StudentInsights.Insight,
    timeline: List<Pair<Long, String>>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val riskColor = when (insight.riskLevel) {
        StudentInsights.RiskLevel.AT_RISK -> MaterialTheme.colorScheme.error
        StudentInsights.RiskLevel.WATCH -> Color(0xFFEF6C00)
        StudentInsights.RiskLevel.NONE -> Color(0xFF2E7D32)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(riskColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        text = if (insight.reasons.isEmpty()) "No flags" else insight.reasons.joinToString(" • "),
                        fontSize = 11.sp,
                        color = if (insight.reasons.isEmpty()) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        } else riskColor
                    )
                }
                Text(
                    text = insight.attendance.attendanceRate?.let {
                        String.format(Locale.US, "%.0f%%", it)
                    } ?: "--",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatCell("Present", insight.attendance.present.toString())
                    StatCell("Absent", insight.attendance.absent.toString())
                    StatCell("Excused", insight.attendance.excused.toString())
                    StatCell(
                        "Grade",
                        insight.gradePercent?.let { String.format(Locale.US, "%.0f%%", it) } ?: "--"
                    )
                    StatCell("Notes", insight.incidentCount.toString())
                }

                if (insight.attendance.unmarked > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${insight.attendance.unmarked} day(s) never marked, excluded from the rate rather than counted present.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                if (timeline.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Attendance history", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    AttendanceHeatmap(timeline)
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onOpenProfile,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Open profile", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One square per marked day, oldest first. A run of absences reads instantly here in a way that
 * a single percentage cannot convey.
 */
@Composable
private fun AttendanceHeatmap(timeline: List<Pair<Long, String>>) {
    val sdf = remember { SimpleDateFormat("MMM dd", Locale.US) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            timeline.forEach { (_, status) ->
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when (status) {
                                "PRESENT" -> Color(0xFF2E7D32)
                                "ABSENT" -> MaterialTheme.colorScheme.error
                                "EXCUSED" -> Color(0xFFEF6C00)
                                else -> Color(0xFFBDBDBD)
                            }
                        )
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${sdf.format(Date(timeline.first().first))} to ${sdf.format(Date(timeline.last().first))}",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
