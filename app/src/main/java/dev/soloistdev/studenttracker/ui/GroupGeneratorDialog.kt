package dev.soloistdev.studenttracker.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Share
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
import dev.soloistdev.studenttracker.data.GradeCalculator
import dev.soloistdev.studenttracker.data.GroupGenerator
import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.random.Random

/**
 * Splits the class in front of the teacher into working groups.
 *
 * Pairing this with the running grades is the whole point. Grouping by hand is either alphabetical,
 * which sits the same people together all year, or a shuffle, which regularly lands four
 * struggling students on one table - and the app already knows enough to avoid both.
 */
@Composable
fun GroupGeneratorDialog(
    className: String,
    students: List<StudentEntity>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { StudentRepository(context) }
    val scope = rememberCoroutineScope()

    val eligible = remember(students, className) {
        if (className.isBlank()) students
        else students.filter { it.getClassNamesList().contains(className) }
    }

    var grades by remember { mutableStateOf<Map<Int, Double?>>(emptyMap()) }
    var gradesLoaded by remember { mutableStateOf(false) }

    var mode by remember { mutableStateOf(GroupGenerator.Mode.MIXED_ABILITY) }
    var byCount by remember { mutableStateOf(true) }
    var groupCount by remember { mutableIntStateOf(4) }
    var perGroup by remember { mutableIntStateOf(4) }
    var keepApart by remember { mutableStateOf<Set<Pair<Int, Int>>>(emptySet()) }
    var seed by remember { mutableIntStateOf(0) }
    var showKeepApart by remember { mutableStateOf(false) }

    LaunchedEffect(className) {
        // The active period is the right scope: grouping by a grade that spans the whole year
        // would keep using marks from a term the class has moved on from.
        val activeTermId = repository.getAllGradingTerms().firstOrNull { it.isActive }?.id ?: 0
        val computed = GradeCalculator.computeForRoster(
            students = eligible,
            columns = repository.getAllAssessmentColumns(),
            scores = repository.getAllAssessmentScores(),
            categories = repository.getAllAssessmentCategories(),
            termId = activeTermId
        )
        grades = computed.mapValues { it.value.percent }
        gradesLoaded = true
    }

    val effectiveCount = remember(byCount, groupCount, perGroup, eligible.size) {
        if (byCount) groupCount.coerceIn(1, eligible.size.coerceAtLeast(1))
        else GroupGenerator.groupCountFor(eligible.size, perGroup).coerceAtLeast(1)
    }

    val groups = remember(eligible, grades, mode, effectiveCount, keepApart, seed) {
        GroupGenerator.generate(
            students = eligible,
            grades = grades,
            options = GroupGenerator.Options(
                mode = mode,
                groupCount = effectiveCount,
                keepApart = keepApart
            ),
            random = Random(seed)
        )
    }

    val unsatisfied = remember(groups, keepApart) {
        GroupGenerator.violations(groups, keepApart)
    }

    val hasGrades = grades.values.any { it != null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.groups_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (eligible.isEmpty()) {
                    Text(stringResource(R.string.groups_empty), fontSize = 13.sp)
                    return@Column
                }

                ModePicker(selected = mode, onSelect = { mode = it })

                if (mode != GroupGenerator.Mode.RANDOM && gradesLoaded && !hasGrades) {
                    Text(
                        text = stringResource(R.string.groups_no_grades),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                SizePicker(
                    byCount = byCount,
                    onByCountChange = { byCount = it },
                    groupCount = groupCount,
                    onGroupCountChange = { groupCount = it },
                    perGroup = perGroup,
                    onPerGroupChange = { perGroup = it },
                    rosterSize = eligible.size
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showKeepApart = true }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.groups_keep_apart),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (keepApart.isEmpty()) {
                                stringResource(R.string.groups_keep_apart_none)
                            } else {
                                stringResource(R.string.groups_keep_apart_count, keepApart.size)
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = stringResource(R.string.action_edit),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (unsatisfied.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.groups_unsatisfied, unsatisfied.size),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                groups.forEach { group ->
                    GroupCard(group = group, grades = grades)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { seed += 1 },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.groups_reroll))
                    }
                    OutlinedButton(
                        onClick = { shareGroups(context, className, groups) },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.s_done)) } },
        shape = RoundedCornerShape(28.dp)
    )

    if (showKeepApart) {
        KeepApartDialog(
            students = eligible,
            pairs = keepApart,
            onPairsChange = { keepApart = it },
            onDismiss = { showKeepApart = false }
        )
    }
}

@Composable
private fun ModePicker(
    selected: GroupGenerator.Mode,
    onSelect: (GroupGenerator.Mode) -> Unit
) {
    val options = listOf(
        Triple(GroupGenerator.Mode.MIXED_ABILITY, R.string.groups_mode_mixed, R.string.groups_mode_mixed_desc),
        Triple(GroupGenerator.Mode.SIMILAR_ABILITY, R.string.groups_mode_similar, R.string.groups_mode_similar_desc),
        Triple(GroupGenerator.Mode.RANDOM, R.string.groups_mode_random, R.string.groups_mode_random_desc)
    )

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (mode, label, description) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(mode) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected == mode, onClick = null)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(stringResource(label), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = stringResource(description),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SizePicker(
    byCount: Boolean,
    onByCountChange: (Boolean) -> Unit,
    groupCount: Int,
    onGroupCountChange: (Int) -> Unit,
    perGroup: Int,
    onPerGroupChange: (Int) -> Unit,
    rosterSize: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = byCount,
                onClick = { onByCountChange(true) },
                label = { Text(stringResource(R.string.groups_by_count), fontSize = 11.sp) }
            )
            FilterChip(
                selected = !byCount,
                onClick = { onByCountChange(false) },
                label = { Text(stringResource(R.string.groups_by_size), fontSize = 11.sp) }
            )
        }

        val value = if (byCount) groupCount else perGroup
        val label = if (byCount) {
            stringResource(R.string.groups_count_value, value)
        } else {
            stringResource(R.string.groups_size_value, value)
        }
        // Capped at the roster size: asking for eight groups from six students produces two empty
        // tables and looks like a bug rather than a choice.
        val ceiling = rosterSize.coerceAtLeast(2)

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (byCount) onGroupCountChange((groupCount - 1).coerceAtLeast(1))
                else onPerGroupChange((perGroup - 1).coerceAtLeast(2))
            }) {
                Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = {
                if (byCount) onGroupCountChange((groupCount + 1).coerceAtMost(ceiling))
                else onPerGroupChange((perGroup + 1).coerceAtMost(ceiling))
            }) {
                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun GroupCard(group: GroupGenerator.Group, grades: Map<Int, Double?>) {
    val average = group.averageGrade(grades)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.groups_group_label, group.index + 1),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (average != null) {
                        stringResource(
                            R.string.groups_group_meta,
                            group.members.size,
                            String.format(Locale.US, "%.0f%%", average)
                        )
                    } else {
                        stringResource(R.string.groups_group_meta_nograde, group.members.size)
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            group.members.forEach { student ->
                Text(
                    text = "${student.lastName}, ${student.firstName}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun KeepApartDialog(
    students: List<StudentEntity>,
    pairs: Set<Pair<Int, Int>>,
    onPairsChange: (Set<Pair<Int, Int>>) -> Unit,
    onDismiss: () -> Unit
) {
    var firstPick by remember { mutableStateOf<StudentEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.groups_keep_apart), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.groups_keep_apart_desc),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                val byId = students.associateBy { it.id }
                pairs.forEach { (a, b) ->
                    val left = byId[a] ?: return@forEach
                    val right = byId[b] ?: return@forEach
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${left.firstName} ${left.lastName}  /  ${right.firstName} ${right.lastName}",
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onPairsChange(pairs - (a to b)) }) {
                            Text(stringResource(R.string.s_remove), fontSize = 11.sp)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                students.sortedBy { it.lastName.lowercase() }.forEach { student ->
                    val isFirst = firstPick?.id == student.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val current = firstPick
                                when {
                                    current == null -> firstPick = student
                                    current.id == student.id -> firstPick = null
                                    else -> {
                                        onPairsChange(pairs + GroupGenerator.pairKey(current.id, student.id))
                                        firstPick = null
                                    }
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isFirst, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${student.lastName}, ${student.firstName}", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.s_done)) } }
    )
}

/**
 * Hands the split to whatever the teacher already uses to put things on a board - a messaging app,
 * a notes app, a projector. Plain text on purpose: it pastes anywhere.
 */
private fun shareGroups(
    context: android.content.Context,
    className: String,
    groups: List<GroupGenerator.Group>
) {
    val body = buildString {
        if (className.isNotBlank()) appendLine(className).appendLine()
        groups.forEach { group ->
            appendLine(context.getString(R.string.groups_group_label, group.index + 1))
            group.members.forEach { appendLine("  ${it.firstName} ${it.lastName}") }
            appendLine()
        }
    }.trim()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.groups_share))
    )
}
