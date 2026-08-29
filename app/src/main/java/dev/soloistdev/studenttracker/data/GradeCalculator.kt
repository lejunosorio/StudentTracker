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

        // Weights can differ between periods - a quarter may value exams more heavily than the
        // one before it. A category with termId 0 applies everywhere; anything else only counts
        // toward the period it belongs to.
        val scopedCategories = if (termId == 0) {
            categories
        } else {
            categories.filter { it.termId == 0 || it.termId == termId }
        }

        val categoryById = scopedCategories.associateBy { it.id }
        val assignedWeight = scopedCategories.sumOf { it.weight }
        val isWeighted = scopedCategories.any { it.weight > 0.0 }

        // Leftover weight carries the uncategorised bucket, so ungrouped work is not silently
        // dropped from a weighted term.
        val leftoverWeight = (100.0 - assignedWeight).coerceAtLeast(0.0)

        val breakdown = scopedColumns
            // A column can point at a category that has since been deleted, or at one belonging
            // to another grading period. Such a category resolves to no weight, and the weighted
            // branch below only averages categories carrying weight - so without this the
            // assessments would vanish from the running grade with nothing said. Folding them
            // into the uncategorised bucket keeps them counted under the leftover weight.
            .groupBy { if (categoryById.containsKey(it.categoryId)) it.categoryId else UNCATEGORISED_ID }
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

    // ---------------------------------------------------------------------------------------
    // Projection
    //
    // "What do I need on the final to pass?" is the single most-asked question a gradebook gets,
    // from students and from guardians on report day, and it is the one thing this class could not
    // answer: everything above runs forwards from scores to a grade, never backwards.
    //
    // The inverse is deliberately not re-derived in closed form. Weighted terms renormalise across
    // categories holding graded work, uncategorised work absorbs the leftover weight, and an
    // all-unweighted term falls back to total points - three rules that a second implementation
    // would drift from within a release, at which point the projection quietly contradicts the
    // grade printed next to it. Instead a hypothetical assessment is fed through
    // [computeForStudent] itself, so the two can never disagree by construction.
    // ---------------------------------------------------------------------------------------

    /** An assessment that has not happened yet. */
    data class Hypothetical(
        val maxPoints: Double,
        /** Which bucket it would count towards; 0 for uncategorised. */
        val categoryId: Int = UNCATEGORISED_ID
    )

    data class Projection(
        val targetPercent: Double,
        val currentPercent: Double?,
        /** Raw points needed on the hypothetical assessment, clamped to what it is worth. */
        val requiredScore: Double,
        val maxPoints: Double,
        /** The best overall grade this assessment could produce, scoring full marks. */
        val bestPossiblePercent: Double?,
        /** True when the target is already met even scoring zero. */
        val alreadySecured: Boolean,
        /** True when full marks still fall short of the target. */
        val unreachable: Boolean
    ) {
        /** Required score as a share of the assessment, for wording it as a percentage. */
        val requiredPercent: Double?
            get() = if (maxPoints > 0.0) (requiredScore / maxPoints) * 100.0 else null
    }

    /**
     * The running grade this student would hold if [hypothetical] were marked [score].
     *
     * The synthetic column is given an id far above anything Room will have issued so it cannot
     * collide with a real one and silently replace its score.
     */
    fun gradeWithHypothetical(
        studentId: Int,
        columns: List<AssessmentColumnEntity>,
        scores: List<AssessmentScoreEntity>,
        categories: List<AssessmentCategoryEntity>,
        termId: Int,
        hypothetical: Hypothetical,
        score: Double
    ): Double? {
        val syntheticId = (columns.maxOfOrNull { it.id } ?: 0) + 1_000_000

        val projectedColumns = columns + AssessmentColumnEntity(
            id = syntheticId,
            name = "",
            maxPoints = hypothetical.maxPoints,
            examDate = 0L,
            checkDate = 0L,
            // Must land inside the period being projected, or the scoping in computeForStudent
            // would drop it and the projection would report no change at all.
            termId = termId,
            categoryId = hypothetical.categoryId
        )
        val projectedScores = scores + AssessmentScoreEntity(
            id = syntheticId,
            columnId = syntheticId,
            studentId = studentId,
            score = score.toString()
        )

        return computeForStudent(studentId, projectedColumns, projectedScores, categories, termId).percent
    }

    /**
     * What this student must score on [hypothetical] to finish the period on [targetPercent].
     *
     * Solved by bisection rather than algebra, for the reason given above. The grade is monotonic
     * in the score, so sixty halvings of the interval land well inside a tenth of a point - far
     * finer than a mark a teacher can actually award.
     */
    fun scoreNeededFor(
        studentId: Int,
        columns: List<AssessmentColumnEntity>,
        scores: List<AssessmentScoreEntity>,
        categories: List<AssessmentCategoryEntity>,
        termId: Int,
        hypothetical: Hypothetical,
        targetPercent: Double
    ): Projection {
        val current = computeForStudent(studentId, columns, scores, categories, termId).percent
        val max = hypothetical.maxPoints.coerceAtLeast(0.0)

        fun gradeAt(score: Double): Double? =
            gradeWithHypothetical(studentId, columns, scores, categories, termId, hypothetical, score)

        val atZero = gradeAt(0.0)
        val atMax = gradeAt(max)

        if (atZero != null && atZero >= targetPercent) {
            return Projection(
                targetPercent = targetPercent,
                currentPercent = current,
                requiredScore = 0.0,
                maxPoints = max,
                bestPossiblePercent = atMax,
                alreadySecured = true,
                unreachable = false
            )
        }

        // Covers both "full marks is not enough" and the case where this assessment carries no
        // weight at all, so scoring it cannot move the grade in either direction.
        if (atMax == null || atMax < targetPercent) {
            return Projection(
                targetPercent = targetPercent,
                currentPercent = current,
                requiredScore = max,
                maxPoints = max,
                bestPossiblePercent = atMax,
                alreadySecured = false,
                unreachable = true
            )
        }

        var low = 0.0
        var high = max
        repeat(60) {
            val mid = (low + high) / 2.0
            val grade = gradeAt(mid)
            if (grade != null && grade >= targetPercent) high = mid else low = mid
        }

        return Projection(
            targetPercent = targetPercent,
            currentPercent = current,
            requiredScore = high,
            maxPoints = max,
            bestPossiblePercent = atMax,
            alreadySecured = false,
            unreachable = false
        )
    }
}
