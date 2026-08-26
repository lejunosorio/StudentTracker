package dev.soloistdev.studenttracker.data

/**
 * The SQL that rebuilds `students` and the two tables whose foreign keys reference it.
 *
 * Kept as plain statements, apart from the Migration that runs them, so the sequence can be
 * executed against a real SQLite engine in a unit test. That is not ceremony: the first draft of
 * this repair cascade-deleted every behaviour incident and every score in the database, and the
 * test is what caught it.
 *
 * The hazard is that rebuilding a parent table means dropping something children point at:
 *
 *  - renaming a table rewrites the REFERENCES clauses of every table pointing at it (whenever
 *    foreign keys are enforced), so the children follow the parent to its temporary name;
 *  - dropping a table then performs an implicit DELETE FROM, which fires ON DELETE CASCADE and
 *    empties those children.
 *
 * PRAGMA foreign_keys cannot be turned off from inside a migration, because a migration already
 * runs in a transaction and the pragma is a no-op there. So the order below never leaves a child
 * pointing at a table it is about to drop: the children are parked in foreign-key-free copies
 * first, `students` is rebuilt while nothing references it, and the children are then recreated
 * against the new table.
 */
internal object SchemaRepair {

    /** Room's expected `students`, matching the exported schema exactly. */
    private const val CREATE_STUDENTS =
        "CREATE TABLE IF NOT EXISTS `students` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `firstName` TEXT NOT NULL, " +
                "`lastName` TEXT NOT NULL, `gender` TEXT NOT NULL, `birthday` INTEGER NOT NULL, " +
                "`address` TEXT NOT NULL, `contactNumber` TEXT NOT NULL, `picturePath` TEXT NOT NULL, " +
                "`guardiansJson` TEXT NOT NULL, `customDataJson` TEXT NOT NULL, " +
                "`isDeleted` INTEGER NOT NULL, `lastModified` INTEGER NOT NULL, " +
                "`classNamesJson` TEXT NOT NULL, `seatingJson` TEXT NOT NULL)"

    private const val INCIDENT_COLUMNS =
        "`id`, `studentId`, `title`, `category`, `description`, `timestamp`, `incidentDate`, `photoPath`"

    private const val INCIDENT_BODY =
        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `studentId` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, `category` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, `incidentDate` INTEGER NOT NULL, `photoPath` TEXT NOT NULL"

    private const val SCORE_COLUMNS = "`id`, `columnId`, `studentId`, `score`, `lastModified`"

    private const val SCORE_BODY =
        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `columnId` INTEGER NOT NULL, " +
                "`studentId` INTEGER NOT NULL, `score` TEXT NOT NULL, `lastModified` INTEGER NOT NULL"

    /** Reads a column when the table has it, and substitutes [fallback] when it does not. */
    private fun read(present: Set<String>, column: String, fallback: String): String =
        if (present.contains(column)) "`$column`" else fallback

    /**
     * The full repair, ordered. Safe to run on an undamaged database, and safe to run twice.
     *
     * Column sets come from PRAGMA table_info, because the shapes being repaired genuinely differ:
     * one release left `className`/`seatingX`/`seatingY` on `students` and never added the JSON
     * columns, and a database still at version 15 has no `photoPath` on its incidents yet.
     */
    fun statements(
        tables: Set<String>,
        studentColumns: Set<String>,
        incidentColumns: Set<String>,
        scoreColumns: Set<String>
    ): List<String> {
        // A child whose foreign key names a table that no longer exists cannot be dropped while
        // foreign keys are enforced - SQLite refuses with "no such table: students_old", which is
        // precisely the wreckage being cleaned up. Standing a placeholder up for the length of the
        // repair makes the reference resolvable; it is removed again at the end. If a real
        // students_old is still lying about, it is left alone rather than assumed disposable.
        val needsStudentsOldPlaceholder = !tables.contains("students_old")
        val hasLegacyClass = studentColumns.contains("className")
        val hasLegacySeat = studentColumns.contains("seatingX") && studentColumns.contains("seatingY")

        // Carry the JSON columns across when they exist; otherwise build them from the legacy
        // ones, so a class list and a seating chart survive the repair instead of being reset.
        val classNamesExpr = when {
            studentColumns.contains("classNamesJson") -> "`classNamesJson`"
            hasLegacyClass ->
                "(CASE WHEN `className` IS NOT NULL AND `className` != '' " +
                        "THEN '[\"' || replace(`className`, '\"', '') || '\"]' ELSE '[]' END)"
            else -> "'[]'"
        }
        val seatingExpr = when {
            studentColumns.contains("seatingJson") -> "`seatingJson`"
            hasLegacyClass && hasLegacySeat ->
                "(CASE WHEN `className` IS NOT NULL AND `className` != '' AND `seatingX` >= 0 AND `seatingY` >= 0 " +
                        "THEN '{\"' || replace(`className`, '\"', '') || '\":{\"x\":' || `seatingX` || ',\"y\":' || `seatingY` || '}}' " +
                        "ELSE '{}' END)"
            else -> "'{}'"
        }

        val incidentRead = listOf(
            read(incidentColumns, "id", "NULL"),
            read(incidentColumns, "studentId", "0"),
            read(incidentColumns, "title", "''"),
            read(incidentColumns, "category", "'Neutral'"),
            read(incidentColumns, "description", "''"),
            read(incidentColumns, "timestamp", "0"),
            read(incidentColumns, "incidentDate", "0"),
            read(incidentColumns, "photoPath", "''")
        ).joinToString(", ")

        val scoreRead = listOf(
            read(scoreColumns, "id", "NULL"),
            read(scoreColumns, "columnId", "0"),
            read(scoreColumns, "studentId", "0"),
            read(scoreColumns, "score", "''"),
            read(scoreColumns, "lastModified", "0")
        ).joinToString(", ")

        return listOfNotNull(
            // Holds enforcement until commit, by which point every table has been put back.
            "PRAGMA defer_foreign_keys = TRUE",

            "CREATE TABLE IF NOT EXISTS `students_old` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)"
                .takeIf { needsStudentsOldPlaceholder },

            // 1. Park the children in copies that reference nothing, then drop the originals.
            // Dropping a child is safe - a cascade only ever runs from parent to child - and once
            // both are gone, nothing in the schema names `students`.
            "DROP TABLE IF EXISTS `behavior_incidents_repair_tmp`",
            "CREATE TABLE `behavior_incidents_repair_tmp` ($INCIDENT_BODY)",
            "INSERT INTO `behavior_incidents_repair_tmp` ($INCIDENT_COLUMNS) SELECT $incidentRead FROM `behavior_incidents`",
            "DROP TABLE `behavior_incidents`",

            "DROP TABLE IF EXISTS `assessment_scores_repair_tmp`",
            "CREATE TABLE `assessment_scores_repair_tmp` ($SCORE_BODY)",
            "INSERT INTO `assessment_scores_repair_tmp` ($SCORE_COLUMNS) SELECT $scoreRead FROM `assessment_scores`",
            "DROP TABLE `assessment_scores`",

            // 2. Rebuild `students`. With no table referencing it, the rename cannot drag a
            // foreign key along and the drop cannot cascade into anything.
            "DROP TABLE IF EXISTS `students_repair_tmp`",
            "ALTER TABLE `students` RENAME TO `students_repair_tmp`",
            CREATE_STUDENTS,
            "INSERT INTO `students` (`id`, `firstName`, `lastName`, `gender`, `birthday`, `address`, " +
                    "`contactNumber`, `picturePath`, `guardiansJson`, `customDataJson`, `isDeleted`, " +
                    "`lastModified`, `classNamesJson`, `seatingJson`) SELECT `id`, `firstName`, `lastName`, " +
                    "`gender`, `birthday`, `address`, `contactNumber`, `picturePath`, `guardiansJson`, " +
                    "`customDataJson`, `isDeleted`, `lastModified`, $classNamesExpr, $seatingExpr " +
                    "FROM `students_repair_tmp`",
            "DROP TABLE `students_repair_tmp`",

            // 3. Recreate the children against the rebuilt table. Rows whose student no longer
            // exists are left behind: carrying them would fail the foreign key check at commit and
            // put the app straight back to crashing on launch.
            "CREATE TABLE IF NOT EXISTS `behavior_incidents` ($INCIDENT_BODY, " +
                    "FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "INSERT INTO `behavior_incidents` ($INCIDENT_COLUMNS) SELECT $INCIDENT_COLUMNS " +
                    "FROM `behavior_incidents_repair_tmp` WHERE `studentId` IN (SELECT `id` FROM `students`)",
            "DROP TABLE `behavior_incidents_repair_tmp`",
            "CREATE INDEX IF NOT EXISTS `index_behavior_incidents_studentId` ON `behavior_incidents` (`studentId`)",

            "CREATE TABLE IF NOT EXISTS `assessment_scores` ($SCORE_BODY, " +
                    "FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`columnId`) REFERENCES `assessment_columns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            // Collapsed to the newest row per student per assessment. A database predating the
            // unique index can hold duplicates, and rebuilding the table recreates that index -
            // copying them across unchanged would fail to build it and abort the upgrade.
            "INSERT INTO `assessment_scores` ($SCORE_COLUMNS) SELECT $SCORE_COLUMNS " +
                    "FROM `assessment_scores_repair_tmp` WHERE `studentId` IN (SELECT `id` FROM `students`) " +
                    "AND `columnId` IN (SELECT `id` FROM `assessment_columns`) " +
                    "AND `id` IN (SELECT MAX(`id`) FROM `assessment_scores_repair_tmp` GROUP BY `columnId`, `studentId`)",
            "DROP TABLE `assessment_scores_repair_tmp`",
            "CREATE INDEX IF NOT EXISTS `index_assessment_scores_studentId` ON `assessment_scores` (`studentId`)",
            "CREATE INDEX IF NOT EXISTS `index_assessment_scores_columnId` ON `assessment_scores` (`columnId`)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_assessment_scores_columnId_studentId` ON `assessment_scores` (`columnId`, `studentId`)",

            // Nothing references the placeholder any more.
            "DROP TABLE IF EXISTS `students_old`".takeIf { needsStudentsOldPlaceholder }
        )
    }
}
