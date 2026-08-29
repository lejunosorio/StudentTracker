package dev.soloistdev.studenttracker.data

import kotlin.random.Random

/**
 * Splits a class into working groups.
 *
 * The app already knows how to pick one student fairly - the cold-call rotation - and already
 * knows every running grade. Grouping is the daily job that needed both and had neither: done by
 * hand it is either alphabetical, which puts the same people together all year, or a shuffle,
 * which regularly lands four struggling students on one table.
 *
 * Pure and seeded, so a re-roll is a new seed rather than hidden state, and the whole thing is
 * assertable in a unit test.
 */
object GroupGenerator {

    enum class Mode {
        /** No signal used - a plain shuffle. */
        RANDOM,

        /** Every group gets a spread of attainment: snake-drafted down the rank order. */
        MIXED_ABILITY,

        /** Each group sits at a similar level, for work pitched to a band. */
        SIMILAR_ABILITY
    }

    data class Group(val index: Int, val members: List<StudentEntity>) {
        fun averageGrade(grades: Map<Int, Double?>): Double? {
            val known = members.mapNotNull { grades[it.id] }
            return if (known.isEmpty()) null else known.average()
        }
    }

    data class Options(
        val mode: Mode = Mode.MIXED_ABILITY,
        val groupCount: Int = 4,
        /**
         * Pairs that must not share a group, smaller id first.
         *
         * Honoured where possible rather than guaranteed: with enough constraints and few enough
         * groups there may be no valid arrangement, and quietly returning a worse split beats
         * refusing to produce one at all. [violations] reports what could not be satisfied.
         */
        val keepApart: Set<Pair<Int, Int>> = emptySet()
    )

    /** Groups needed to seat [size] students at most [perGroup] to a table. */
    fun groupCountFor(size: Int, perGroup: Int): Int {
        if (size <= 0 || perGroup <= 0) return 0
        return ((size + perGroup - 1) / perGroup).coerceAtLeast(1)
    }

    fun generate(
        students: List<StudentEntity>,
        grades: Map<Int, Double?> = emptyMap(),
        options: Options = Options(),
        random: Random = Random.Default
    ): List<Group> {
        if (students.isEmpty()) return emptyList()

        val count = options.groupCount.coerceIn(1, students.size)

        // Every mode shuffles first. Without it, students with no grade - or a class where nobody
        // has been assessed yet - would fall back to whatever order the database returned, and the
        // "random" mode would hand back the same groups every time.
        val shuffled = students.shuffled(random)

        val ordered = when (options.mode) {
            Mode.RANDOM -> shuffled
            Mode.MIXED_ABILITY, Mode.SIMILAR_ABILITY ->
                shuffled.sortedByDescending { grades[it.id] ?: Double.NEGATIVE_INFINITY }
        }

        val buckets = when (options.mode) {
            Mode.SIMILAR_ABILITY -> chunkEvenly(ordered, count)
            else -> snakeDeal(ordered, count)
        }

        return repair(buckets, options.keepApart)
            .mapIndexed { index, members -> Group(index, members) }
    }

    /** Pairs still sharing a group after placement. Empty when every constraint was met. */
    fun violations(groups: List<Group>, keepApart: Set<Pair<Int, Int>>): Set<Pair<Int, Int>> =
        keepApart.filter { (a, b) ->
            groups.any { group ->
                group.members.any { it.id == a } && group.members.any { it.id == b }
            }
        }.toSet()

    /** Orders a pair so a constraint is stored the same way whichever student was picked first. */
    fun pairKey(a: Int, b: Int): Pair<Int, Int> = if (a <= b) a to b else b to a

    /**
     * Deals down the rank order and back up again, so group one does not collect every top
     * student and the last group every struggling one. The standard fix for exactly that.
     */
    private fun snakeDeal(ordered: List<StudentEntity>, count: Int): List<MutableList<StudentEntity>> {
        val buckets = List(count) { mutableListOf<StudentEntity>() }
        ordered.forEachIndexed { position, student ->
            val row = position / count
            val column = position % count
            val target = if (row % 2 == 0) column else count - 1 - column
            buckets[target].add(student)
        }
        return buckets
    }

    /** Consecutive slices of near-equal size, so each group sits in one attainment band. */
    private fun chunkEvenly(ordered: List<StudentEntity>, count: Int): List<MutableList<StudentEntity>> {
        val buckets = List(count) { mutableListOf<StudentEntity>() }
        val base = ordered.size / count
        val remainder = ordered.size % count

        var cursor = 0
        for (i in 0 until count) {
            // The first `remainder` groups take one extra, which keeps sizes within one of each
            // other rather than leaving a final group of one.
            val size = base + if (i < remainder) 1 else 0
            repeat(size) {
                if (cursor < ordered.size) buckets[i].add(ordered[cursor++])
            }
        }
        return buckets
    }

    /**
     * Swaps members out of groups that violate a keep-apart pair.
     *
     * A swap is only accepted when it does not create a violation somewhere else, and sizes are
     * preserved because it is always a swap rather than a move. Bounded passes: with contradictory
     * constraints this could otherwise shuffle forever, and an imperfect arrangement produced
     * instantly is worth more to a teacher than a perfect one that hangs the screen.
     */
    private fun repair(
        buckets: List<MutableList<StudentEntity>>,
        keepApart: Set<Pair<Int, Int>>
    ): List<List<StudentEntity>> {
        if (keepApart.isEmpty()) return buckets.map { it.toList() }

        repeat(buckets.size * 4) {
            val clash = findClash(buckets, keepApart) ?: return buckets.map { it.toList() }
            val (groupIndex, student) = clash

            val moved = buckets.indices
                .filter { it != groupIndex }
                .any { otherIndex ->
                    // A swap only helps when it is clean both ways: the incoming student must be
                    // able to sit where the clash was, and the clashing student must be able to
                    // sit where they came from.
                    val candidate = buckets[otherIndex].firstOrNull { other ->
                        canSit(other, buckets[groupIndex], keepApart, leaving = student) &&
                                canSit(student, buckets[otherIndex], keepApart, leaving = other)
                    } ?: return@any false

                    buckets[groupIndex].remove(student)
                    buckets[otherIndex].remove(candidate)
                    buckets[groupIndex].add(candidate)
                    buckets[otherIndex].add(student)
                    true
                }

            if (!moved) return buckets.map { it.toList() }
        }
        return buckets.map { it.toList() }
    }

    /** The first student sitting with someone they must not be with, and the group they are in. */
    private fun findClash(
        buckets: List<MutableList<StudentEntity>>,
        keepApart: Set<Pair<Int, Int>>
    ): Pair<Int, StudentEntity>? {
        buckets.forEachIndexed { index, members ->
            members.forEach { student ->
                val clashes = members.any { other ->
                    other.id != student.id && pairKey(student.id, other.id) in keepApart
                }
                if (clashes) return index to student
            }
        }
        return null
    }

    /**
     * True when [student] could join [group] without breaking a constraint.
     *
     * [leaving] is the member being swapped out in the same move, so they are not counted as
     * still being there - otherwise a straight exchange between two clashing students would
     * always look impossible.
     */
    private fun canSit(
        student: StudentEntity,
        group: List<StudentEntity>,
        keepApart: Set<Pair<Int, Int>>,
        leaving: StudentEntity
    ): Boolean = group.none { member ->
        member.id != leaving.id && member.id != student.id &&
                pairKey(student.id, member.id) in keepApart
    }
}
