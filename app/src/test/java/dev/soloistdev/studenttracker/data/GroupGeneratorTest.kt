package dev.soloistdev.studenttracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Grouping is used in front of a class, so the failures that matter are the visible ones: a table
 * of one, a student in two groups at once, or four struggling students sat together in the mode
 * that exists specifically to prevent that.
 */
class GroupGeneratorTest {

    private fun student(id: Int) = StudentEntity(
        id = id,
        firstName = "First$id",
        lastName = "Last$id",
        gender = "F",
        birthday = 0L
    )

    private fun roster(size: Int) = (1..size).map { student(it) }

    /** Descending grades, so student 1 is the strongest. */
    private fun rankedGrades(size: Int): Map<Int, Double?> =
        (1..size).associateWith { 100.0 - it * (50.0 / size) }

    private val seeded = Random(42)

    @Test
    fun `every student lands in exactly one group`() {
        val students = roster(23)
        val groups = GroupGenerator.generate(
            students = students,
            grades = rankedGrades(23),
            options = GroupGenerator.Options(groupCount = 5),
            random = seeded
        )

        val placed = groups.flatMap { it.members }.map { it.id }
        assertEquals(23, placed.size)
        assertEquals(students.map { it.id }.toSet(), placed.toSet())
    }

    @Test
    fun `group sizes never differ by more than one`() {
        val groups = GroupGenerator.generate(
            students = roster(23),
            grades = rankedGrades(23),
            options = GroupGenerator.Options(groupCount = 5),
            random = seeded
        )

        val sizes = groups.map { it.members.size }
        assertTrue("sizes were $sizes", sizes.max() - sizes.min() <= 1)
    }

    @Test
    fun `mixed ability keeps group averages close together`() {
        val size = 24
        val grades = rankedGrades(size)
        val groups = GroupGenerator.generate(
            students = roster(size),
            grades = grades,
            options = GroupGenerator.Options(GroupGenerator.Mode.MIXED_ABILITY, groupCount = 4),
            random = seeded
        )

        val averages = groups.mapNotNull { it.averageGrade(grades) }
        assertEquals(4, averages.size)
        // The whole point of the snake draft. A plain sequential deal would spread these by ~40.
        assertTrue("averages were $averages", averages.max() - averages.min() < 5.0)
    }

    @Test
    fun `similar ability separates the bands instead`() {
        val size = 24
        val grades = rankedGrades(size)
        val groups = GroupGenerator.generate(
            students = roster(size),
            grades = grades,
            options = GroupGenerator.Options(GroupGenerator.Mode.SIMILAR_ABILITY, groupCount = 4),
            random = seeded
        )

        val averages = groups.mapNotNull { it.averageGrade(grades) }
        assertTrue("averages were $averages", averages.max() - averages.min() > 20.0)
    }

    @Test
    fun `a keep-apart pair is honoured when there is room to honour it`() {
        val keepApart = setOf(GroupGenerator.pairKey(1, 2), GroupGenerator.pairKey(3, 4))
        val groups = GroupGenerator.generate(
            students = roster(20),
            grades = rankedGrades(20),
            options = GroupGenerator.Options(groupCount = 4, keepApart = keepApart),
            random = seeded
        )

        assertTrue(GroupGenerator.violations(groups, keepApart).isEmpty())
    }

    @Test
    fun `a keep-apart swap does not change how many students are in each group`() {
        val keepApart = setOf(GroupGenerator.pairKey(1, 2))
        val groups = GroupGenerator.generate(
            students = roster(12),
            grades = rankedGrades(12),
            options = GroupGenerator.Options(groupCount = 3, keepApart = keepApart),
            random = seeded
        )

        assertEquals(listOf(4, 4, 4), groups.map { it.members.size })
        assertEquals(12, groups.flatMap { it.members }.map { it.id }.toSet().size)
    }

    @Test
    fun `impossible constraints still return a full set of groups`() {
        // Three students who must all be apart, but only two groups to put them in. One pair has
        // to share; refusing to produce anything would be worse in front of a class.
        val keepApart = setOf(
            GroupGenerator.pairKey(1, 2),
            GroupGenerator.pairKey(1, 3),
            GroupGenerator.pairKey(2, 3)
        )
        val groups = GroupGenerator.generate(
            students = roster(4),
            options = GroupGenerator.Options(GroupGenerator.Mode.RANDOM, groupCount = 2, keepApart = keepApart),
            random = seeded
        )

        assertEquals(4, groups.flatMap { it.members }.size)
        assertTrue(GroupGenerator.violations(groups, keepApart).isNotEmpty())
    }

    @Test
    fun `asking for more groups than there are students does not produce empty tables`() {
        val groups = GroupGenerator.generate(
            students = roster(3),
            options = GroupGenerator.Options(groupCount = 8),
            random = seeded
        )

        assertEquals(3, groups.size)
        assertTrue(groups.none { it.members.isEmpty() })
    }

    @Test
    fun `an ungraded class falls back to a shuffle rather than the database order`() {
        val students = roster(12)
        val first = GroupGenerator.generate(
            students = students,
            options = GroupGenerator.Options(GroupGenerator.Mode.MIXED_ABILITY, groupCount = 3),
            random = Random(1)
        )
        val second = GroupGenerator.generate(
            students = students,
            options = GroupGenerator.Options(GroupGenerator.Mode.MIXED_ABILITY, groupCount = 3),
            random = Random(2)
        )

        assertTrue(
            "two different seeds produced identical groups",
            first.map { g -> g.members.map { it.id } } != second.map { g -> g.members.map { it.id } }
        )
    }

    @Test
    fun `the same seed always produces the same groups`() {
        val students = roster(15)
        val grades = rankedGrades(15)
        val options = GroupGenerator.Options(groupCount = 4)

        val a = GroupGenerator.generate(students, grades, options, Random(7))
        val b = GroupGenerator.generate(students, grades, options, Random(7))

        assertEquals(
            a.map { g -> g.members.map { it.id } },
            b.map { g -> g.members.map { it.id } }
        )
    }

    @Test
    fun `group count is derived from the requested table size`() {
        assertEquals(6, GroupGenerator.groupCountFor(23, 4))
        assertEquals(5, GroupGenerator.groupCountFor(20, 4))
        assertEquals(1, GroupGenerator.groupCountFor(2, 4))
        assertEquals(0, GroupGenerator.groupCountFor(0, 4))
    }

    @Test
    fun `an empty class produces no groups rather than throwing`() {
        assertTrue(GroupGenerator.generate(emptyList()).isEmpty())
    }
}
