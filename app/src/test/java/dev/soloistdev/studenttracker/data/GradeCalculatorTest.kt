package dev.soloistdev.studenttracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeCalculatorTest {

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
    fun `total points mode averages everything earned over everything possible`() {
        val grade = GradeCalculator.computeForStudent(
            studentId = 1,
            columns = listOf(column(1), column(2, maxPoints = 50.0)),
            scores = listOf(score(1, 1, "80"), score(2, 1, "40")),
            categories = emptyList()
        )
        assertEquals(80.0, grade.percent!!, 0.001) // 120 of 150
        assertEquals(2, grade.gradedCount)
    }

    @Test
    fun `blank and qualitative marks are excluded rather than counted as zero`() {
        val grade = GradeCalculator.computeForStudent(
            studentId = 1,
            columns = listOf(column(1), column(2), column(3)),
            scores = listOf(score(1, 1, "90"), score(2, 1, ""), score(3, 1, "Outstanding")),
            categories = emptyList()
        )
        assertEquals(90.0, grade.percent!!, 0.001)
        assertEquals(1, grade.gradedCount)
        assertEquals(100.0, grade.possible, 0.001)
    }

    @Test
    fun `a student with nothing marked has no percentage`() {
        val grade = GradeCalculator.computeForStudent(
            studentId = 1,
            columns = listOf(column(1)),
            scores = emptyList(),
            categories = emptyList()
        )
        assertNull(grade.percent)
        assertEquals("--", grade.display)
    }

    @Test
    fun `weights are renormalised across only the categories holding graded work`() {
        // Exams are worth 60 and quizzes 40, but nothing has been marked in exams yet. The
        // quiz result should read as the running grade rather than being scaled down to 40%.
        val grade = GradeCalculator.computeForStudent(
            studentId = 1,
            columns = listOf(column(1, categoryId = 10), column(2, categoryId = 20)),
            scores = listOf(score(2, 1, "75")),
            categories = listOf(category(10, 60.0), category(20, 40.0))
        )
        assertTrue(grade.isWeighted)
        assertEquals(75.0, grade.percent!!, 0.001)
    }

    @Test
    fun `weighted categories contribute in proportion once both are marked`() {
        val grade = GradeCalculator.computeForStudent(
            studentId = 1,
            columns = listOf(column(1, categoryId = 10), column(2, categoryId = 20)),
            scores = listOf(score(1, 1, "50"), score(2, 1, "100")),
            categories = listOf(category(10, 60.0), category(20, 40.0))
        )
        assertEquals(70.0, grade.percent!!, 0.001) // 50*0.6 + 100*0.4
    }

    @Test
    fun `uncategorised work carries the leftover weight`() {
        // One category claims 60. The remaining 40 belongs to work in no category.
        val grade = GradeCalculator.computeForStudent(
            studentId = 1,
            columns = listOf(column(1, categoryId = 10), column(2, categoryId = 0)),
            scores = listOf(score(1, 1, "50"), score(2, 1, "100")),
            categories = listOf(category(10, 60.0))
        )
        assertEquals(70.0, grade.percent!!, 0.001)
    }

    @Test
    fun `work pointing at a deleted category still counts`() {
        // Regression: an unresolvable categoryId resolved to zero weight, and the weighted
        // branch averages only weighted categories - so the assessment silently vanished from
        // the grade. It now falls into the uncategorised bucket and its leftover weight.
        val grade = GradeCalculator.computeForStudent(
            studentId = 1,
            columns = listOf(column(1, categoryId = 10), column(2, categoryId = 99)),
            scores = listOf(score(1, 1, "50"), score(2, 1, "100")),
            categories = listOf(category(10, 60.0)) // category 99 has been deleted
        )
        assertEquals(70.0, grade.percent!!, 0.001)
        assertEquals(2, grade.gradedCount)
    }

    @Test
    fun `a term restricts the calculation to its own assessments`() {
        val columns = listOf(column(1, termId = 1), column(2, termId = 2))
        val scores = listOf(score(1, 1, "60"), score(2, 1, "90"))

        assertEquals(60.0, GradeCalculator.computeForStudent(1, columns, scores, emptyList(), termId = 1).percent!!, 0.001)
        assertEquals(90.0, GradeCalculator.computeForStudent(1, columns, scores, emptyList(), termId = 2).percent!!, 0.001)
        assertEquals(75.0, GradeCalculator.computeForStudent(1, columns, scores, emptyList(), termId = 0).percent!!, 0.001)
    }

    @Test
    fun `a category belonging to another term does not apply`() {
        // Category 20 is scoped to term 2, so it must not weight term 1's assessments.
        val grade = GradeCalculator.computeForStudent(
            studentId = 1,
            columns = listOf(column(1, termId = 1, categoryId = 10)),
            scores = listOf(score(1, 1, "80")),
            categories = listOf(category(10, 50.0, termId = 1), category(20, 50.0, termId = 2)),
            termId = 1
        )
        assertEquals(80.0, grade.percent!!, 0.001)
    }

    @Test
    fun `scores belonging to other students are ignored`() {
        val grade = GradeCalculator.computeForStudent(
            studentId = 1,
            columns = listOf(column(1)),
            scores = listOf(score(1, 1, "70"), score(1, 2, "10")),
            categories = emptyList()
        )
        assertEquals(70.0, grade.percent!!, 0.001)
    }

    @Test
    fun `class average counts only students who have been marked`() {
        val graded = GradeCalculator.computeForStudent(1, listOf(column(1)), listOf(score(1, 1, "80")), emptyList())
        val unmarked = GradeCalculator.computeForStudent(2, listOf(column(1)), emptyList(), emptyList())

        assertEquals(80.0, GradeCalculator.classAverage(listOf(graded, unmarked))!!, 0.001)
        assertNull(GradeCalculator.classAverage(listOf(unmarked)))
    }
}
