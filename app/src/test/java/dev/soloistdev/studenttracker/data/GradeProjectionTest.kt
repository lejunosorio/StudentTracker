package dev.soloistdev.studenttracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backwards direction: from a target grade to the mark that reaches it.
 *
 * The property that matters most is agreement with the forward calculation - a projection that
 * quietly disagrees with the grade printed above it is worse than no projection, because a teacher
 * will repeat it to a student.
 */
class GradeProjectionTest {

    private fun column(
        id: Int,
        maxPoints: Double = 100.0,
        termId: Int = 0,
        categoryId: Int = 0
    ) = AssessmentColumnEntity(
        id = id,
        name = "Assessment $id",
        maxPoints = maxPoints,
        examDate = 0L,
        checkDate = 0L,
        termId = termId,
        categoryId = categoryId
    )

    private fun score(columnId: Int, studentId: Int, value: String) =
        AssessmentScoreEntity(columnId = columnId, studentId = studentId, score = value)

    private fun category(id: Int, weight: Double, termId: Int = 0) =
        AssessmentCategoryEntity(id = id, name = "Category $id", weight = weight, termId = termId)

    @Test
    fun `total points mode asks for exactly the marks the arithmetic requires`() {
        // 60 of 100 so far. To finish on 70 across 200 points, they need 140 total, so 80 more.
        val projection = GradeCalculator.scoreNeededFor(
            studentId = 1,
            columns = listOf(column(1)),
            scores = listOf(score(1, 1, "60")),
            categories = emptyList(),
            termId = 0,
            hypothetical = GradeCalculator.Hypothetical(maxPoints = 100.0),
            targetPercent = 70.0
        )

        assertFalse(projection.alreadySecured)
        assertFalse(projection.unreachable)
        assertEquals(80.0, projection.requiredScore, 0.05)
    }

    @Test
    fun `the required score really does produce the target when fed back through`() {
        val columns = listOf(column(1), column(2, maxPoints = 50.0))
        val scores = listOf(score(1, 1, "72"), score(2, 1, "31"))
        val hypothetical = GradeCalculator.Hypothetical(maxPoints = 40.0)

        val projection = GradeCalculator.scoreNeededFor(
            studentId = 1,
            columns = columns,
            scores = scores,
            categories = emptyList(),
            termId = 0,
            hypothetical = hypothetical,
            targetPercent = 75.0
        )

        val achieved = GradeCalculator.gradeWithHypothetical(
            studentId = 1,
            columns = columns,
            scores = scores,
            categories = emptyList(),
            termId = 0,
            hypothetical = hypothetical,
            score = projection.requiredScore
        )

        assertNotNull(achieved)
        assertTrue("$achieved should reach the 75% target", achieved!! >= 75.0 - 0.01)
    }

    @Test
    fun `a weighted term routes the hypothetical through its own category`() {
        // Exams carry 70, homework 30. Homework is perfect; exams sit at 50.
        val categories = listOf(category(1, weight = 70.0), category(2, weight = 30.0))
        val columns = listOf(column(1, categoryId = 1), column(2, categoryId = 2))
        val scores = listOf(score(1, 1, "50"), score(2, 1, "100"))

        val current = GradeCalculator.computeForStudent(1, columns, scores, categories).percent
        assertEquals(65.0, current!!, 0.001) // 50*0.7 + 100*0.3

        // A second exam, out of 100. Homework contributes a fixed 30, so the exam bucket has to
        // supply 45 of the 70 it carries - that is 900/14 % over 200 exam points, of which 50 is
        // already earned, leaving about 78.6.
        val projection = GradeCalculator.scoreNeededFor(
            studentId = 1,
            columns = columns,
            scores = scores,
            categories = categories,
            termId = 0,
            hypothetical = GradeCalculator.Hypothetical(maxPoints = 100.0, categoryId = 1),
            targetPercent = 75.0
        )

        assertFalse(projection.unreachable)
        assertEquals(78.57, projection.requiredScore, 0.1)
    }

    @Test
    fun `a target already met even with a zero is reported as secured`() {
        val projection = GradeCalculator.scoreNeededFor(
            studentId = 1,
            columns = listOf(column(1)),
            scores = listOf(score(1, 1, "95")),
            categories = emptyList(),
            termId = 0,
            hypothetical = GradeCalculator.Hypothetical(maxPoints = 10.0),
            targetPercent = 60.0
        )

        assertTrue(projection.alreadySecured)
        assertEquals(0.0, projection.requiredScore, 0.001)
    }

    @Test
    fun `a target full marks cannot reach is reported as out of reach`() {
        // 20 of 100 so far. Even a perfect 100 on the next paper only reaches 60.
        val projection = GradeCalculator.scoreNeededFor(
            studentId = 1,
            columns = listOf(column(1)),
            scores = listOf(score(1, 1, "20")),
            categories = emptyList(),
            termId = 0,
            hypothetical = GradeCalculator.Hypothetical(maxPoints = 100.0),
            targetPercent = 90.0
        )

        assertTrue(projection.unreachable)
        assertEquals(60.0, projection.bestPossiblePercent!!, 0.001)
    }

    @Test
    fun `an assessment in a bucket carrying no weight cannot move the grade`() {
        // Every point of weight is spoken for by category 1, so the uncategorised bucket has none.
        val categories = listOf(category(1, weight = 100.0))
        val columns = listOf(column(1, categoryId = 1))
        val scores = listOf(score(1, 1, "50"))

        val best = GradeCalculator.gradeWithHypothetical(
            studentId = 1,
            columns = columns,
            scores = scores,
            categories = categories,
            termId = 0,
            hypothetical = GradeCalculator.Hypothetical(maxPoints = 100.0, categoryId = 0),
            score = 100.0
        )

        // The UI keys its "this cannot help" wording off exactly this equality.
        assertEquals(50.0, best!!, 0.001)
    }

    @Test
    fun `the hypothetical lands inside the period being projected`() {
        val columns = listOf(column(1, termId = 2))
        val scores = listOf(score(1, 1, "40"))

        val projected = GradeCalculator.gradeWithHypothetical(
            studentId = 1,
            columns = columns,
            scores = scores,
            categories = emptyList(),
            termId = 2,
            hypothetical = GradeCalculator.Hypothetical(maxPoints = 100.0),
            score = 100.0
        )

        // Scoped to term 2, so both the real 40 and the hypothetical 100 count: 140 of 200.
        assertEquals(70.0, projected!!, 0.001)
    }

    @Test
    fun `a student with no marked work at all can still be projected`() {
        val projection = GradeCalculator.scoreNeededFor(
            studentId = 1,
            columns = emptyList(),
            scores = emptyList(),
            categories = emptyList(),
            termId = 0,
            hypothetical = GradeCalculator.Hypothetical(maxPoints = 50.0),
            targetPercent = 80.0
        )

        assertFalse(projection.unreachable)
        assertEquals(40.0, projection.requiredScore, 0.05)
    }
}
