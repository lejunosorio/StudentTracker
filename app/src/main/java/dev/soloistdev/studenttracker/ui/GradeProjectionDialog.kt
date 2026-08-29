package dev.soloistdev.studenttracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.soloistdev.studenttracker.R
import dev.soloistdev.studenttracker.data.AssessmentCategoryEntity
import dev.soloistdev.studenttracker.data.AssessmentColumnEntity
import dev.soloistdev.studenttracker.data.AssessmentScoreEntity
import dev.soloistdev.studenttracker.data.GradeCalculator
import dev.soloistdev.studenttracker.data.StudentEntity
import java.util.Locale

/**
 * "What do I need on the final to pass?"
 *
 * The most-asked question a gradebook gets, from students and from guardians on report day, and
 * the one thing the app could not answer - every calculation ran forwards from scores to a grade.
 * Both directions are shown, because a teacher standing in front of a student wants the target
 * number, and the student wants to know what a particular mark would actually do.
 */
@Composable
fun GradeProjectionDialog(
    student: StudentEntity,
    columns: List<AssessmentColumnEntity>,
    scores: List<AssessmentScoreEntity>,
    categories: List<AssessmentCategoryEntity>,
    termId: Int,
    onDismiss: () -> Unit
) {
    var maxPointsInput by remember { mutableStateOf("100") }
    var targetInput by remember { mutableStateOf("75") }
    var whatIfInput by remember { mutableStateOf("") }
    var categoryId by remember { mutableIntStateOf(GradeCalculator.UNCATEGORISED_ID) }
    var categoryMenuOpen by remember { mutableStateOf(false) }

    // Categories belonging to another period cannot receive this assessment, so offering them
    // would only produce a projection that reports no effect.
    val selectableCategories = remember(categories, termId) {
        if (termId == 0) categories else categories.filter { it.termId == 0 || it.termId == termId }
    }

    val maxPoints = maxPointsInput.toDoubleOrNull() ?: 0.0
    val target = targetInput.toDoubleOrNull()

    val projection = remember(student.id, columns, scores, categories, termId, maxPoints, categoryId, target) {
        if (maxPoints <= 0.0 || target == null) null
        else GradeCalculator.scoreNeededFor(
            studentId = student.id,
            columns = columns,
            scores = scores,
            categories = categories,
            termId = termId,
            hypothetical = GradeCalculator.Hypothetical(maxPoints, categoryId),
            targetPercent = target
        )
    }

    val whatIfScore = whatIfInput.toDoubleOrNull()
    val whatIfResult = remember(student.id, columns, scores, categories, termId, maxPoints, categoryId, whatIfScore) {
        if (maxPoints <= 0.0 || whatIfScore == null) null
        else GradeCalculator.gradeWithHypothetical(
            studentId = student.id,
            columns = columns,
            scores = scores,
            categories = categories,
            termId = termId,
            hypothetical = GradeCalculator.Hypothetical(maxPoints, categoryId),
            score = whatIfScore
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(stringResource(R.string.projection_title), fontWeight = FontWeight.Bold)
                Text(
                    text = "${student.firstName} ${student.lastName}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                projection?.currentPercent?.let { current ->
                    LabelledValue(
                        label = stringResource(R.string.projection_current),
                        value = String.format(Locale.US, "%.1f%%", current)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = maxPointsInput,
                        onValueChange = { maxPointsInput = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text(stringResource(R.string.projection_worth), fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = targetInput,
                        onValueChange = { targetInput = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text(stringResource(R.string.projection_target), fontSize = 11.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (selectableCategories.isNotEmpty()) {
                    val selectedName = selectableCategories.firstOrNull { it.id == categoryId }?.name
                        ?: stringResource(R.string.projection_category_none)

                    Box {
                        OutlinedButton(
                            onClick = { categoryMenuOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${stringResource(R.string.projection_category)}: $selectedName",
                                fontSize = 12.sp
                            )
                        }
                        DropdownMenu(
                            expanded = categoryMenuOpen,
                            onDismissRequest = { categoryMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.projection_category_none)) },
                                onClick = {
                                    categoryId = GradeCalculator.UNCATEGORISED_ID
                                    categoryMenuOpen = false
                                }
                            )
                            selectableCategories.forEach { category ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (category.weight > 0.0) {
                                                "${category.name} (${String.format(Locale.US, "%.0f", category.weight)}%)"
                                            } else {
                                                category.name
                                            }
                                        )
                                    },
                                    onClick = {
                                        categoryId = category.id
                                        categoryMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                projection?.let { Verdict(it) }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = stringResource(R.string.projection_whatif_heading),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = whatIfInput,
                    onValueChange = { whatIfInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(stringResource(R.string.gradebook_label_score), fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (whatIfScore != null && whatIfResult != null) {
                    Text(
                        text = stringResource(
                            R.string.projection_whatif_result,
                            String.format(Locale.US, "%.0f/%.0f", whatIfScore, maxPoints),
                            String.format(Locale.US, "%.1f%%", whatIfResult)
                        ),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = stringResource(R.string.projection_explain),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(stringResource(R.string.s_done)) } },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
private fun Verdict(projection: GradeCalculator.Projection) {
    val target = String.format(Locale.US, "%.1f%%", projection.targetPercent)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                projection.alreadySecured -> MaterialTheme.colorScheme.primaryContainer
                projection.unreachable -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            when {
                projection.alreadySecured -> Text(
                    text = stringResource(R.string.projection_secured, target),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                // A best-possible identical to the current grade means the assessment carries no
                // weight at all, which is a different problem from aiming too high and deserves
                // its own wording - otherwise the teacher retries with a lower target forever.
                projection.unreachable && projection.bestPossiblePercent != null &&
                        projection.currentPercent != null &&
                        kotlin.math.abs(projection.bestPossiblePercent - projection.currentPercent) < 0.05 ->
                    Text(
                        text = stringResource(R.string.projection_no_effect),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                projection.unreachable -> Text(
                    text = stringResource(
                        R.string.projection_unreachable,
                        projection.bestPossiblePercent?.let { String.format(Locale.US, "%.1f%%", it) } ?: "--"
                    ),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                else -> {
                    Text(
                        text = stringResource(
                            R.string.projection_needs,
                            String.format(Locale.US, "%.1f", projection.requiredScore),
                            String.format(Locale.US, "%.0f", projection.maxPoints)
                        ),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    projection.requiredPercent?.let { pct ->
                        Text(
                            text = stringResource(
                                R.string.projection_needs_percent,
                                String.format(Locale.US, "%.0f%%", pct)
                            ),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelledValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
