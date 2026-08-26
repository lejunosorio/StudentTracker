package dev.soloistdev.studenttracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.RubricEntity
import dev.soloistdev.studenttracker.data.RubricLevelEntity
import dev.soloistdev.studenttracker.data.StudentRepository
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Builds reusable marking scales.
 *
 * Each level carries points, which is what lets standards-based marking coexist with a numeric
 * gradebook: the teacher taps "Proficient", the database stores the points behind it, and every
 * average keeps working without knowing a rubric was involved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RubricManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { StudentRepository(context) }

    var rubrics by remember { mutableStateOf<List<RubricEntity>>(emptyList()) }
    var levels by remember { mutableStateOf<List<RubricLevelEntity>>(emptyList()) }
    var newRubricName by remember { mutableStateOf("") }
    var expandedRubricId by remember { mutableIntStateOf(-1) }

    suspend fun refresh() {
        rubrics = repository.getAllRubrics()
        levels = repository.getAllRubricLevels()
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Rubrics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "A rubric is a set of levels with points behind them. Attach one to an assessment and marking becomes a tap instead of a number.",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            rubrics.forEach { rubric ->
                val myLevels = levels.filter { it.rubricId == rubric.id }
                RubricCard(
                    rubric = rubric,
                    levels = myLevels,
                    isExpanded = expandedRubricId == rubric.id,
                    onToggle = { expandedRubricId = if (expandedRubricId == rubric.id) -1 else rubric.id },
                    onRename = { updated -> scope.launch { repository.updateRubric(updated); refresh() } },
                    onDeleteRubric = {
                        scope.launch {
                            repository.softDeleteRubric(rubric.id)
                            refresh()
                        }
                    },
                    onAddLevel = { label, points, descriptor ->
                        scope.launch {
                            repository.insertRubricLevel(
                                RubricLevelEntity(
                                    rubricId = rubric.id,
                                    label = label,
                                    points = points,
                                    descriptor = descriptor,
                                    displayOrder = myLevels.size
                                )
                            )
                            refresh()
                        }
                    },
                    onDeleteLevel = { levelId ->
                        scope.launch {
                            repository.deleteRubricLevel(levelId)
                            refresh()
                        }
                    }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            OutlinedTextField(
                value = newRubricName,
                onValueChange = { newRubricName = it },
                label = { Text(stringResource(R.string.s_new_rubric_name)) },
                placeholder = { Text(stringResource(R.string.s_essay_rubric)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                enabled = newRubricName.isNotBlank(),
                onClick = {
                    scope.launch {
                        repository.insertRubric(RubricEntity(name = newRubricName.trim()))
                        newRubricName = ""
                        refresh()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.s_create_rubric))
            }
        }
    }
}

@Composable
private fun RubricCard(
    rubric: RubricEntity,
    levels: List<RubricLevelEntity>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onDeleteRubric: () -> Unit,
    onRename: (RubricEntity) -> Unit,
    onAddLevel: (String, Double, String) -> Unit,
    onDeleteLevel: (Int) -> Unit
) {
    var levelLabel by remember { mutableStateOf("") }
    var levelPoints by remember { mutableStateOf("") }
    var levelDescriptor by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<RubricEntity?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(rubric.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        text = if (levels.isEmpty()) {
                            "No levels yet - add at least two"
                        } else {
                            levels.joinToString("  ") { "${it.label} ${String.format(Locale.US, "%.0f", it.points)}" }
                        },
                        fontSize = 10.sp,
                        color = if (levels.isEmpty()) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = { renameTarget = rubric }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.cd_edit),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.cd_levels),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDeleteRubric, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.cd_delete_rubric),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (isExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                levels.forEach { level ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${level.label}  •  ${String.format(Locale.US, "%.1f", level.points)} pts",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (level.descriptor.isNotBlank()) {
                                Text(
                                    text = level.descriptor,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                        IconButton(onClick = { onDeleteLevel(level.id) }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_remove_level),
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = levelLabel,
                        onValueChange = { levelLabel = it },
                        label = { Text("Level", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f)
                    )
                    OutlinedTextField(
                        value = levelPoints,
                        onValueChange = { input -> levelPoints = input.filter { it.isDigit() || it == '.' } },
                        label = { Text("Points", fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = levelDescriptor,
                    onValueChange = { levelDescriptor = it },
                    label = { Text("What this looks like (optional)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    enabled = levelLabel.isNotBlank() && levelPoints.toDoubleOrNull() != null,
                    onClick = {
                        onAddLevel(levelLabel.trim(), levelPoints.toDoubleOrNull() ?: 0.0, levelDescriptor.trim())
                        levelLabel = ""
                        levelPoints = ""
                        levelDescriptor = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Add level", fontSize = 12.sp)
                }
            }
        }
    }

    renameTarget?.let { target ->
        RenameRubricDialog(
            rubric = target,
            onSave = {
                onRename(it)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }
}

/**
 * Renames a rubric.
 *
 * The save path goes through updateRubric rather than an insert-replace, because rubric_levels
 * cascade from this row: replacing it to change one string would delete every level the rubric
 * owns, which is the sort of loss you only notice at marking time.
 */
@Composable
private fun RenameRubricDialog(
    rubric: RubricEntity,
    onSave: (RubricEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(rubric.id) { mutableStateOf(rubric.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rubric_rename_title), fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.rubric_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onSave(rubric.copy(name = name.trim(), lastModified = System.currentTimeMillis())) }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp)
    )
}
