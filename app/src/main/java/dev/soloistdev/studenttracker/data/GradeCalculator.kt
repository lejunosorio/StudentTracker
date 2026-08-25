package dev.soloistdev.studenttracker.data

import java.util.Locale

/**
 * Turns raw score rows into a running grade.
 *
 * Two modes, chosen automatically so a teacher who does not want weighting never has to set
 * one up:
 *  - Weighted, when any category carries a non-zero weight. Each category contributes its own
 *    percentage, and the weights are renormalised across only the categories that actually hold
 *    graded work, so a term reads correctly before every category has been assessed.
 *  - Total points otherwise: everything earned over everything possible.
 *
 * Non-numeric scores - blank, or a qualitative mark such as "Outstanding" - are excluded rather
 * than counted as zero, so an unmarked assessment never drags a grade down.
 */
object GradeCalculator {

    const val UNCATEGORISED_ID = 0
    private const val UNCATEGORISED_NAME = "Uncategorised"

    data class CategoryBreakdown(
        val categoryId: Int,
        val categoryName: String,
        val weight: Double,
        val earned: Double,
        val possible: Double,
        val gradedCount: Int
    ) {
        /** Null when this category holds no graded work yet. */
        val percent: Double? get() = if (possible > 0.0) (earned / possible) * 100.0 else null
    }

    data class StudentGrade(
        val studentId: Int,
        val percent: Double?,
        val earned: Double,
        val possible: Double,
        val isWeighted: Boolean,
        val gradedCount: Int,
        val breakdown: List<CategoryBreakdown>
    ) {
        val display: String
            get() = percent?.let { String.format(Locale.US, "%.1f%%", it) } ?: "--"
    }

    /**
     * @param termId restricts the calculation to one grading period; 0 spans every term.
     */
    fun computeForStudent(
        studentId: Int,
        columns: List<AssessmentColumnEntity>,
        scores: List<AssessmentScoreEntity>,
        categories: List<AssessmentCategoryEntity>,
        termId: Int = 0
    ): StudentGrade {
        val scopedColumns = if (termId == 0) columns else columns.filter { it.termId == termId }
        val scoreByColumn = scores.filter { it.studentId == studentId }.associateBy { it.columnId }

        val categoryById = categories.associateBy { it.id }
        val assignedWeight = categories.sumOf { it.weight }
        val isWeighted = categories.any { it.weight > 0.0 }

        // Leftover weight carries the uncategorised bucket, so ungrouped work is not silently
        // dropped from a weighted term.
        val leftoverWeight = (100.0 - assignedWeight).coerceAtLeast(0.0)

        val breakdown = scopedColumns
            .groupBy { it.categoryId }
            .map { (categoryId, columnsInCategory) ->
                var earned = 0.0
                var possible = 0.0
                var graded = 0

                columnsInCategory.forEach { column ->
                    val raw = scoreByColumn[column.id]?.score?.trim()
                    val numeric = raw?.toDoubleOrNull()
                    if (numeric != null) {
                        earned += numeric
                        possible += column.maxPoints
                        graded++
                    }
                }

                CategoryBreakdown(
                    categoryId = categoryId,
                    categoryName = categoryById[categoryId]?.name ?: UNCATEGORISED_NAME,
                    weight = if (categoryId == UNCATEGORISED_ID) leftoverWeight else (categoryById[categoryId]?.weight ?: 0.0),
                    earned = earned,
                    possible = possible,
                    gradedCount = graded
                )
            }
            .sortedByDescending { it.weight }

        val totalEarned = breakdown.sumOf { it.earned }
        val totalPossible = breakdown.sumOf { it.possible }
        val totalGraded = breakdown.sumOf { it.gradedCount }

        val percent = when {
            totalGraded == 0 -> null
            isWeighted -> {
                val contributing = breakdown.filter { it.weight > 0.0 && it.percent != null }
                val weightSum = contributing.sumOf { it.weight }
                if (weightSum > 0.0) {
                    contributing.sumOf { (it.percent ?: 0.0) * it.weight } / weightSum
                } else {
                    // Every graded category is unweighted: fall back rather than show nothing
                    if (totalPossible > 0.0) (totalEarned / totalPossible) * 100.0 else null
                }
            }
            totalPossible > 0.0 -> (totalEarned / totalPossible) * 100.0
            else -> null
        }

        return StudentGrade(
            studentId = studentId,
            percent = percent,
            earned = totalEarned,
            possible = totalPossible,
            isWeighted = isWeighted,
            gradedCount = totalGraded,
            breakdown = breakdown
        )
    }

    fun computeForRoster(
        students: List<StudentEntity>,
        columns: List<AssessmentColumnEntity>,
        scores: List<AssessmentScoreEntity>,
        categories: List<AssessmentCategoryEntity>,
        termId: Int = 0
    ): Map<Int, StudentGrade> =
        students.associate { student ->
            student.id to computeForStudent(student.id, columns, scores, categories, termId)
        }

    /** Class-wide mean of the students who have at least one graded assessment. */
    fun classAverage(grades: Collection<StudentGrade>): Double? {
        val scored = grades.mapNotNull { it.percent }
        return if (scored.isEmpty()) null else scored.average()
    }
}
