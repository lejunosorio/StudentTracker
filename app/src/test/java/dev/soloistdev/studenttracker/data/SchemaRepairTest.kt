package dev.soloistdev.studenttracker.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs the repair against a real SQLite engine, on databases damaged the way users' were.
 *
 * The two crash reports behind this were both permanent: the version counter had already moved
 * past the migration that broke the schema, so every launch failed Room's validation and there
 * was no path back. A migration meant to rescue that has to be verified somewhere other than a
 * user's phone.
 */
class SchemaRepairTest {

    private lateinit var db: Connection

    @Before
    fun open() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        exec("PRAGMA foreign_keys = ON")
    }

    @After
    fun close() {
        db.close()
    }

    private fun exec(sql: String) = db.createStatement().use { it.execute(sql) }

    private fun query(sql: String): List<List<String?>> =
        db.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                val rows = mutableListOf<List<String?>>()
                val columns = rs.metaData.columnCount
                while (rs.next()) {
                    rows.add((1..columns).map { rs.getString(it) })
                }
                rows
            }
        }

    private fun scalar(sql: String): String? = query(sql).firstOrNull()?.firstOrNull()

    private fun columnsOf(table: String): Set<String> =
        query("PRAGMA table_info(`$table`)").map { it[1]!! }.toSet()

    /** The table each foreign key on [table] points at. */
    private fun foreignKeyTargets(table: String): List<String> =
        query("PRAGMA foreign_key_list(`$table`)").map { it[2]!! }

    private fun tableNames(): Set<String> =
        query("SELECT `name` FROM `sqlite_master` WHERE `type` = 'table'").map { it[0]!! }.toSet()

    private fun runRepair() {
        // The production path reads the live schema and runs the statements in order.
        SchemaRepair.statements(
            tables = tableNames(),
            studentColumns = columnsOf("students"),
            incidentColumns = columnsOf("behavior_incidents"),
            scoreColumns = columnsOf("assessment_scores")
        ).forEach { sql ->
            try {
                exec(sql)
            } catch (e: Exception) {
                throw AssertionError("repair failed at: $sql", e)
            }
        }
    }

    /** Inserts a row that current constraints would reject, to build a damaged starting state. */
    private fun execUnchecked(sql: String) {
        exec("PRAGMA foreign_keys = OFF")
        exec(sql)
        exec("PRAGMA foreign_keys = ON")
    }

    // --- the shapes users' databases were actually left in --------------------------------------

    /** Everything except `students`, which each scenario creates in its own damaged shape. */
    private fun createSupportingTables() {
        exec(
            "CREATE TABLE `assessment_columns` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `maxPoints` REAL NOT NULL DEFAULT 100.0, `examDate` INTEGER NOT NULL DEFAULT 0, " +
                    "`checkDate` INTEGER NOT NULL DEFAULT 0, `savedFilterId` INTEGER NOT NULL DEFAULT 0, " +
                    "`termId` INTEGER NOT NULL DEFAULT 0, `categoryId` INTEGER NOT NULL DEFAULT 0, " +
                    "`rubricId` INTEGER NOT NULL DEFAULT 0, `isDeleted` INTEGER NOT NULL DEFAULT 0, " +
                    "`lastModified` INTEGER NOT NULL DEFAULT 0)"
        )
        exec("INSERT INTO `assessment_columns` (`id`, `name`) VALUES (1, 'Quiz 1')")
    }

    private fun createChildren(studentsTableName: String) {
        exec(
            "CREATE TABLE `behavior_incidents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`studentId` INTEGER NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                    "`description` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `incidentDate` INTEGER NOT NULL, " +
                    "`photoPath` TEXT NOT NULL DEFAULT '', " +
                    "FOREIGN KEY(`studentId`) REFERENCES `$studentsTableName`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        exec(
            "CREATE TABLE `assessment_scores` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`columnId` INTEGER NOT NULL, `studentId` INTEGER NOT NULL, `score` TEXT NOT NULL, " +
                    "`lastModified` INTEGER NOT NULL DEFAULT 0, " +
                    "FOREIGN KEY(`studentId`) REFERENCES `$studentsTableName`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`columnId`) REFERENCES `assessment_columns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
    }

    private fun insertSampleChildRows() {
        // Unchecked, because in the damaged scenarios these rows predate the broken reference:
        // they were written when the parent still resolved, and only afterwards did a migration
        // repoint the foreign key at a table that no longer exists.
        execUnchecked(
            "INSERT INTO `behavior_incidents` (`id`, `studentId`, `title`, `category`, `description`, " +
                    "`timestamp`, `incidentDate`, `photoPath`) VALUES (1, 1, 'Helped a classmate', 'Positive', 'note', 10, 10, '')"
        )
        execUnchecked(
            "INSERT INTO `assessment_scores` (`id`, `columnId`, `studentId`, `score`, `lastModified`) " +
                    "VALUES (1, 1, 1, '87', 10)"
        )
    }

    /** A healthy v19 database, as a fresh install produces. */
    private fun createHealthyDatabase() {
        createSupportingTables()
        exec(
            "CREATE TABLE `students` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `firstName` TEXT NOT NULL, " +
                    "`lastName` TEXT NOT NULL, `gender` TEXT NOT NULL, `birthday` INTEGER NOT NULL, " +
                    "`address` TEXT NOT NULL, `contactNumber` TEXT NOT NULL, `picturePath` TEXT NOT NULL, " +
                    "`guardiansJson` TEXT NOT NULL, `customDataJson` TEXT NOT NULL, `isDeleted` INTEGER NOT NULL, " +
                    "`lastModified` INTEGER NOT NULL, `classNamesJson` TEXT NOT NULL, `seatingJson` TEXT NOT NULL)"
        )
        exec(
            "INSERT INTO `students` VALUES (1, 'Ana', 'Cruz', 'F', 100, 'addr', '0917', '', '[]', '{}', 0, 5, " +
                    "'[\"Grade 7 - Sampaguita\"]', '{\"Grade 7 - Sampaguita\":{\"x\":0.5,\"y\":0.5}}')"
        )
        createChildren("students")
        insertSampleChildRows()
    }

    // --- the bug itself -------------------------------------------------------------------------

    @Test
    fun renamingAParentTableRepointsItsChildren() {
        // The whole cause, in three lines. The old migration renamed `students` aside with foreign
        // keys enforced, which rewrote this reference, and then dropped the renamed table.
        createHealthyDatabase()
        assertEquals(listOf("students"), foreignKeyTargets("behavior_incidents"))

        exec("ALTER TABLE `students` RENAME TO `students_old`")

        assertEquals(
            "SQLite repoints the child at the renamed table - that is the bug",
            listOf("students_old"),
            foreignKeyTargets("behavior_incidents")
        )
    }

    @Test
    fun noPragmaPreventsTheRepoint() {
        // Worth pinning down, because it rules out both pragmas that look like they would help and
        // justifies the ordering the repair uses instead. legacy_alter_table governs triggers and
        // views rather than REFERENCES clauses; turning enforcement off does not help either, and
        // could not be used anyway - PRAGMA foreign_keys is a no-op inside the transaction a
        // migration already runs in.
        createHealthyDatabase()

        exec("PRAGMA legacy_alter_table = ON")
        exec("ALTER TABLE `students` RENAME TO `students_legacy_mode`")
        exec("PRAGMA legacy_alter_table = OFF")
        assertEquals(
            "legacy_alter_table does not protect a foreign key",
            listOf("students_legacy_mode"),
            foreignKeyTargets("behavior_incidents")
        )

        exec("PRAGMA foreign_keys = OFF")
        exec("ALTER TABLE `students_legacy_mode` RENAME TO `students_fk_off`")
        exec("PRAGMA foreign_keys = ON")
        assertEquals(
            "nor does disabling enforcement - the rename rewrites the reference regardless",
            listOf("students_fk_off"),
            foreignKeyTargets("behavior_incidents")
        )
    }

    @Test
    fun droppingTheParentCascadesTheChildrenAway() {
        // The second half of the hazard, and what the first draft of this repair did to every
        // incident and score in the database.
        createHealthyDatabase()
        assertEquals("1", scalar("SELECT COUNT(*) FROM `behavior_incidents`"))

        exec("DROP TABLE `students`")

        assertEquals(
            "dropping a parent runs an implicit DELETE FROM, and the cascade empties the child",
            "0",
            scalar("SELECT COUNT(*) FROM `behavior_incidents`")
        )
    }

    // --- repairing each damaged shape -----------------------------------------------------------

    @Test
    fun repairsChildrenLeftPointingAtStudentsOld() {
        // The shape from the current 15-to-16: children reference a students_old that is gone.
        createSupportingTables()
        exec(
            "CREATE TABLE `students` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `firstName` TEXT NOT NULL, " +
                    "`lastName` TEXT NOT NULL, `gender` TEXT NOT NULL, `birthday` INTEGER NOT NULL, " +
                    "`address` TEXT NOT NULL, `contactNumber` TEXT NOT NULL, `picturePath` TEXT NOT NULL, " +
                    "`guardiansJson` TEXT NOT NULL, `customDataJson` TEXT NOT NULL, `isDeleted` INTEGER NOT NULL, " +
                    "`lastModified` INTEGER NOT NULL, `classNamesJson` TEXT NOT NULL, `seatingJson` TEXT NOT NULL)"
        )
        exec("INSERT INTO `students` VALUES (1, 'Ana', 'Cruz', 'F', 100, 'addr', '0917', '', '[]', '{}', 0, 5, '[]', '{}')")
        createChildren("students_old") // the dangling reference
        insertSampleChildRows()

        runRepair()

        assertEquals(listOf("students"), foreignKeyTargets("behavior_incidents"))
        assertEquals(listOf("students", "assessment_columns"), foreignKeyTargets("assessment_scores").sortedDescending())
        assertEquals("1", scalar("SELECT COUNT(*) FROM `behavior_incidents`"))
        assertEquals("1", scalar("SELECT COUNT(*) FROM `assessment_scores`"))
        assertEquals("87", scalar("SELECT `score` FROM `assessment_scores` WHERE `id` = 1"))
    }

    @Test
    fun repairsStudentsLeftWithLegacyColumnsAndBackFillsTheJson() {
        // The shape from the older 15-to-16: JSON columns bolted on, legacy columns never removed.
        createSupportingTables()
        exec(
            "CREATE TABLE `students` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `firstName` TEXT NOT NULL, " +
                    "`lastName` TEXT NOT NULL, `gender` TEXT NOT NULL, `birthday` INTEGER NOT NULL, " +
                    "`address` TEXT NOT NULL, `contactNumber` TEXT NOT NULL, `picturePath` TEXT NOT NULL, " +
                    "`guardiansJson` TEXT NOT NULL, `customDataJson` TEXT NOT NULL, `isDeleted` INTEGER NOT NULL, " +
                    "`lastModified` INTEGER NOT NULL, `className` TEXT NOT NULL DEFAULT '', " +
                    "`seatingX` REAL NOT NULL DEFAULT -1.0, `seatingY` REAL NOT NULL DEFAULT -1.0)"
        )
        exec(
            "INSERT INTO `students` VALUES (1, 'Ana', 'Cruz', 'F', 100, 'addr', '0917', '', '[]', '{}', 0, 5, " +
                    "'Grade 7 - Sampaguita', 0.25, 0.75)"
        )
        createChildren("students")
        insertSampleChildRows()

        runRepair()

        val columns = columnsOf("students")
        assertFalse("legacy className should be gone", columns.contains("className"))
        assertFalse("legacy seatingX should be gone", columns.contains("seatingX"))
        assertFalse("legacy seatingY should be gone", columns.contains("seatingY"))
        assertTrue(columns.contains("classNamesJson"))
        assertTrue(columns.contains("seatingJson"))

        assertEquals(
            "the class should survive the repair as JSON",
            "[\"Grade 7 - Sampaguita\"]",
            scalar("SELECT `classNamesJson` FROM `students` WHERE `id` = 1")
        )
        assertEquals(
            "the seat should survive the repair as JSON",
            "{\"Grade 7 - Sampaguita\":{\"x\":0.25,\"y\":0.75}}",
            scalar("SELECT `seatingJson` FROM `students` WHERE `id` = 1")
        )
    }

    @Test
    fun anUnseatedStudentBackFillsToAnEmptySeatingMap() {
        createSupportingTables()
        exec(
            "CREATE TABLE `students` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `firstName` TEXT NOT NULL, " +
                    "`lastName` TEXT NOT NULL, `gender` TEXT NOT NULL, `birthday` INTEGER NOT NULL, " +
                    "`address` TEXT NOT NULL, `contactNumber` TEXT NOT NULL, `picturePath` TEXT NOT NULL, " +
                    "`guardiansJson` TEXT NOT NULL, `customDataJson` TEXT NOT NULL, `isDeleted` INTEGER NOT NULL, " +
                    "`lastModified` INTEGER NOT NULL, `className` TEXT NOT NULL DEFAULT '', " +
                    "`seatingX` REAL NOT NULL DEFAULT -1.0, `seatingY` REAL NOT NULL DEFAULT -1.0)"
        )
        exec("INSERT INTO `students` VALUES (1, 'Ana', 'Cruz', 'F', 100, '', '', '', '[]', '{}', 0, 5, '', -1.0, -1.0)")
        createChildren("students")

        runRepair()

        assertEquals("[]", scalar("SELECT `classNamesJson` FROM `students` WHERE `id` = 1"))
        assertEquals("{}", scalar("SELECT `seatingJson` FROM `students` WHERE `id` = 1"))
    }

    // --- what the repair must not do -------------------------------------------------------------

    @Test
    fun repairingAHealthyDatabaseChangesNothingThatMatters() {
        createHealthyDatabase()

        runRepair()

        assertEquals(listOf("students"), foreignKeyTargets("behavior_incidents"))
        assertEquals("1", scalar("SELECT COUNT(*) FROM `students`"))
        assertEquals("1", scalar("SELECT COUNT(*) FROM `behavior_incidents`"))
        assertEquals("1", scalar("SELECT COUNT(*) FROM `assessment_scores`"))
        assertEquals(
            "[\"Grade 7 - Sampaguita\"]",
            scalar("SELECT `classNamesJson` FROM `students` WHERE `id` = 1")
        )
    }

    @Test
    fun theRepairNeverCascadesAwayIncidentsOrScores() {
        // The danger in rebuilding a parent table: every route through it drops `students` at some
        // point, and ON DELETE CASCADE would take the child rows with it. Losing a term of grades
        // to a repair would be worse than the crash it fixes.
        createHealthyDatabase()
        exec("INSERT INTO `assessment_columns` (`id`, `name`) VALUES (2, 'Quiz 2')")
        exec(
            "INSERT INTO `behavior_incidents` (`id`, `studentId`, `title`, `category`, `description`, " +
                    "`timestamp`, `incidentDate`, `photoPath`) VALUES (2, 1, 'Late', 'Negative', 'note', 11, 11, '')"
        )
        exec("INSERT INTO `assessment_scores` (`id`, `columnId`, `studentId`, `score`, `lastModified`) VALUES (2, 2, 1, '91', 11)")

        runRepair()

        assertEquals("2", scalar("SELECT COUNT(*) FROM `behavior_incidents`"))
        assertEquals("2", scalar("SELECT COUNT(*) FROM `assessment_scores`"))
    }

    @Test
    fun orphanedChildRowsAreDroppedRatherThanFailingTheWholeRepair() {
        // A row pointing at a student who no longer exists cannot be carried across: the foreign
        // key check at commit would fail, and the app would be right back to crashing on launch.
        createHealthyDatabase()
        execUnchecked(
            "INSERT INTO `behavior_incidents` (`id`, `studentId`, `title`, `category`, `description`, " +
                    "`timestamp`, `incidentDate`, `photoPath`) VALUES (99, 4242, 'Orphan', 'Neutral', 'note', 12, 12, '')"
        )

        runRepair()

        assertEquals("1", scalar("SELECT COUNT(*) FROM `behavior_incidents`"))
        assertEquals("0", scalar("SELECT COUNT(*) FROM `behavior_incidents` WHERE `id` = 99"))
    }

    @Test
    fun duplicateScoresFromBeforeTheUniqueIndexCollapseToTheNewest() {
        // A database predating version 17 can hold several rows for one student on one assessment.
        // The rebuild recreates the unique index, so they have to be collapsed on the way through
        // or the index fails to build and the upgrade aborts - leaving the app unopenable.
        createHealthyDatabase()
        execUnchecked(
            "INSERT INTO `assessment_scores` (`id`, `columnId`, `studentId`, `score`, `lastModified`) " +
                    "VALUES (2, 1, 1, '93', 20)"
        )

        runRepair()

        assertEquals("1", scalar("SELECT COUNT(*) FROM `assessment_scores`"))
        assertEquals(
            "the most recently written row should win",
            "93",
            scalar("SELECT `score` FROM `assessment_scores` WHERE `columnId` = 1 AND `studentId` = 1")
        )
    }

    @Test
    fun aDatabaseStillAtVersionFifteenRebuildsWithoutAPhotoPathColumn() {
        // The 15-to-16 path runs this same rebuild, and behavior_incidents does not gain
        // photoPath until 17-to-18. The copy has to fill it rather than fail on a missing column.
        createSupportingTables()
        exec(
            "CREATE TABLE `students` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `firstName` TEXT NOT NULL, " +
                    "`lastName` TEXT NOT NULL, `gender` TEXT NOT NULL, `birthday` INTEGER NOT NULL, " +
                    "`address` TEXT NOT NULL, `contactNumber` TEXT NOT NULL, `picturePath` TEXT NOT NULL, " +
                    "`guardiansJson` TEXT NOT NULL, `customDataJson` TEXT NOT NULL, `isDeleted` INTEGER NOT NULL, " +
                    "`lastModified` INTEGER NOT NULL, `className` TEXT NOT NULL DEFAULT '', " +
                    "`seatingX` REAL NOT NULL DEFAULT -1.0, `seatingY` REAL NOT NULL DEFAULT -1.0)"
        )
        exec("INSERT INTO `students` VALUES (1, 'Ana', 'Cruz', 'F', 100, '', '', '', '[]', '{}', 0, 5, 'Grade 7 - Sampaguita', 0.5, 0.5)")
        exec(
            "CREATE TABLE `behavior_incidents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`studentId` INTEGER NOT NULL, `title` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                    "`description` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `incidentDate` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        exec(
            "INSERT INTO `behavior_incidents` (`id`, `studentId`, `title`, `category`, `description`, " +
                    "`timestamp`, `incidentDate`) VALUES (1, 1, 'Helped a classmate', 'Positive', 'note', 10, 10)"
        )
        exec(
            "CREATE TABLE `assessment_scores` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`columnId` INTEGER NOT NULL, `studentId` INTEGER NOT NULL, `score` TEXT NOT NULL, " +
                    "`lastModified` INTEGER NOT NULL DEFAULT 0, " +
                    "FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`columnId`) REFERENCES `assessment_columns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        exec("INSERT INTO `assessment_scores` (`id`, `columnId`, `studentId`, `score`) VALUES (1, 1, 1, '87')")

        runRepair()

        assertTrue(columnsOf("behavior_incidents").contains("photoPath"))
        assertEquals("", scalar("SELECT `photoPath` FROM `behavior_incidents` WHERE `id` = 1"))
        assertEquals("1", scalar("SELECT COUNT(*) FROM `behavior_incidents`"))
        assertEquals("87", scalar("SELECT `score` FROM `assessment_scores` WHERE `id` = 1"))
        assertEquals("[\"Grade 7 - Sampaguita\"]", scalar("SELECT `classNamesJson` FROM `students` WHERE `id` = 1"))
    }

    @Test
    fun theRepairLeavesNoTemporaryTablesBehind() {
        createHealthyDatabase()

        runRepair()

        val tables = query("SELECT `name` FROM `sqlite_master` WHERE `type` = 'table'").map { it[0] }
        assertTrue("temporary tables were left behind: $tables", tables.none { it!!.endsWith("_repair_tmp") })
    }

    @Test
    fun theRepairIsSafeToRunTwice() {
        // Belt and braces: an interrupted upgrade should not make the next attempt worse.
        createHealthyDatabase()

        runRepair()
        runRepair()

        assertEquals(listOf("students"), foreignKeyTargets("behavior_incidents"))
        assertEquals("1", scalar("SELECT COUNT(*) FROM `behavior_incidents`"))
        assertEquals("1", scalar("SELECT COUNT(*) FROM `assessment_scores`"))
    }

    @Test
    fun theRebuiltSchemaCarriesTheIndicesRoomExpects() {
        createHealthyDatabase()

        runRepair()

        val indices = query("SELECT `name` FROM `sqlite_master` WHERE `type` = 'index'").map { it[0] }
        assertTrue(indices.contains("index_behavior_incidents_studentId"))
        assertTrue(indices.contains("index_assessment_scores_studentId"))
        assertTrue(indices.contains("index_assessment_scores_columnId"))
        assertTrue(indices.contains("index_assessment_scores_columnId_studentId"))
    }

    @Test
    fun oneScorePerStudentPerAssessmentIsStillEnforced() {
        createHealthyDatabase()

        runRepair()

        val duplicate = try {
            exec("INSERT INTO `assessment_scores` (`columnId`, `studentId`, `score`, `lastModified`) VALUES (1, 1, '50', 12)")
            true
        } catch (e: Exception) {
            false
        }
        assertFalse("the unique index must survive the rebuild", duplicate)
    }
}
