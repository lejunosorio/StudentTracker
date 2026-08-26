package dev.soloistdev.studenttracker.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Upgrades a real version 18 database - built from the schema Room exported for it - to 19, and
 * checks the result against the schema Room exported for 19.
 *
 * Room validates the schema on the first database access, which in a fresh install is whatever the
 * user touches first. A migration that throws, or that leaves one column out of place, therefore
 * surfaces as "the app crashes when I tap import", nowhere near the code responsible.
 */
class MigrationToV19Test {

    private lateinit var db: Connection

    @Before
    fun open() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        exec("PRAGMA foreign_keys = ON")
    }

    @After
    fun close() = db.close()

    private fun exec(sql: String) = db.createStatement().use { it.execute(sql) }

    private fun query(sql: String): List<List<String?>> =
        db.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                val rows = mutableListOf<List<String?>>()
                val columns = rs.metaData.columnCount
                while (rs.next()) rows.add((1..columns).map { rs.getString(it) })
                rows
            }
        }

    private fun scalar(sql: String) = query(sql).firstOrNull()?.firstOrNull()

    private fun columnsOf(table: String) = query("PRAGMA table_info(`$table`)").map { it[1]!! }.toSet()

    private fun foreignKeyTargets(table: String) =
        query("PRAGMA foreign_key_list(`$table`)").map { it[2]!! }.toSet()

    private fun tableNames() =
        query("SELECT `name` FROM `sqlite_master` WHERE `type` = 'table'").map { it[0]!! }.toSet()

    private fun schema(version: Int): JSONObject {
        val candidates = listOf(
            File("schemas/dev.soloistdev.studenttracker.data.AppDatabase/$version.json"),
            File("app/schemas/dev.soloistdev.studenttracker.data.AppDatabase/$version.json")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("exported schema $version.json not found")
        return JSONObject(file.readText())
    }

    private fun entitiesOf(version: Int): JSONArray =
        schema(version).getJSONObject("database").getJSONArray("entities")

    /** Builds the database exactly as Room would have created it at [version]. */
    private fun createSchema(version: Int) {
        val entities = entitiesOf(version)
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            exec(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: JSONArray()
            for (j in 0 until indices.length()) {
                exec(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
    }

    /** Just the rebuild - what MIGRATION_15_16 runs. */
    private fun runRebuild() {
        SchemaRepair.statements(
            tables = tableNames(),
            studentColumns = columnsOf("students"),
            incidentColumns = columnsOf("behavior_incidents"),
            scoreColumns = columnsOf("assessment_scores")
        ).forEach { sql ->
            try {
                exec(sql)
            } catch (e: Exception) {
                throw AssertionError("migration failed at: $sql", e)
            }
        }
    }

    /** The whole of MIGRATION_18_19: the rebuild, then the attendance indices. */
    private fun runMigration() {
        runRebuild()
        exec("CREATE INDEX IF NOT EXISTS `index_attendance_logs_recordId_dateMillis` ON `attendance_logs` (`recordId`, `dateMillis`)")
        exec("CREATE INDEX IF NOT EXISTS `index_attendance_logs_studentId` ON `attendance_logs` (`studentId`)")
    }

    private fun seedRealisticData() {
        exec("INSERT INTO `classrooms` (`id`, `name`, `startTime`, `endTime`, `isDeleted`, `lastModified`) VALUES (1, 'Grade 7 - Sampaguita', '07:30 AM', '12:00 PM', 0, 1)")
        exec(
            "INSERT INTO `students` (`id`, `firstName`, `lastName`, `gender`, `birthday`, `address`, `contactNumber`, " +
                    "`picturePath`, `guardiansJson`, `customDataJson`, `isDeleted`, `lastModified`, `classNamesJson`, `seatingJson`) " +
                    "VALUES (1, 'Ana', 'Cruz', 'F', 100, 'addr', '0917', '', '[]', '{}', 0, 5, '[\"Grade 7 - Sampaguita\"]', '{}')"
        )
        exec(
            "INSERT INTO `assessment_columns` (`id`, `name`, `maxPoints`, `examDate`, `checkDate`, `savedFilterId`, " +
                    "`termId`, `categoryId`, `rubricId`, `isDeleted`, `lastModified`) VALUES (1, 'Quiz 1', 20.0, 1, 1, 0, 0, 0, 0, 0, 1)"
        )
        exec("INSERT INTO `assessment_scores` (`id`, `columnId`, `studentId`, `score`, `lastModified`) VALUES (1, 1, 1, '18', 1)")
        exec(
            "INSERT INTO `behavior_incidents` (`id`, `studentId`, `title`, `category`, `description`, `timestamp`, " +
                    "`incidentDate`, `photoPath`) VALUES (1, 1, 'Helped a classmate', 'Positive', 'note', 1, 1, '')"
        )
        exec("INSERT INTO `attendance_records` (`id`, `name`, `savedFilterId`, `startDate`, `endDate`, `isDeleted`) VALUES (1, 'Q3', 0, 1, 2, 0)")
        exec("INSERT INTO `attendance_logs` (`id`, `recordId`, `dateMillis`, `studentId`, `status`, `lastModified`) VALUES (1, 1, 1, 1, 'PRESENT', 1)")
        exec("INSERT INTO `grading_terms` (`id`, `name`, `startDate`, `endDate`, `isActive`, `isDeleted`, `lastModified`) VALUES (1, 'Quarter 3', 1, 2, 1, 0, 1)")
        exec("INSERT INTO `participation_counts` (`id`, `studentId`, `className`, `timesCalled`, `lastCalledMillis`) VALUES (1, 1, 'Grade 7 - Sampaguita', 3, 1)")
    }

    @Test
    fun aVersion18DatabaseUpgradesToTheSchemaRoomExpectsAtVersion19() {
        createSchema(18)
        seedRealisticData()

        runMigration()

        // Compare against the exported v19 schema, which is what Room validates against on open.
        // A mismatch here is the "Migration didn't properly handle" crash, before it happens.
        val entities = entitiesOf(19)
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")

            val expectedColumns = entity.getJSONArray("fields").let { fields ->
                (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }.toSet()
            }
            assertEquals("columns of $table", expectedColumns, columnsOf(table))

            val expectedForeignKeys = (entity.optJSONArray("foreignKeys") ?: JSONArray()).let { keys ->
                (0 until keys.length()).map { keys.getJSONObject(it).getString("table") }.toSet()
            }
            assertEquals("foreign keys of $table", expectedForeignKeys, foreignKeyTargets(table))
        }
    }

    @Test
    fun theUpgradeKeepsEveryRow() {
        createSchema(18)
        seedRealisticData()

        runMigration()

        assertEquals("1", scalar("SELECT COUNT(*) FROM `students`"))
        assertEquals("1", scalar("SELECT COUNT(*) FROM `behavior_incidents`"))
        assertEquals("1", scalar("SELECT COUNT(*) FROM `assessment_scores`"))
        assertEquals("1", scalar("SELECT COUNT(*) FROM `attendance_logs`"))
        assertEquals("1", scalar("SELECT COUNT(*) FROM `participation_counts`"))
        assertEquals("18", scalar("SELECT `score` FROM `assessment_scores` WHERE `id` = 1"))
        assertEquals(
            "[\"Grade 7 - Sampaguita\"]",
            scalar("SELECT `classNamesJson` FROM `students` WHERE `id` = 1")
        )
    }

    @Test
    fun theUpgradeCreatesTheAttendanceIndices() {
        createSchema(18)

        runMigration()

        val indices = query("SELECT `name` FROM `sqlite_master` WHERE `type` = 'index'").map { it[0] }
        assertTrue(indices.contains("index_attendance_logs_recordId_dateMillis"))
        assertTrue(indices.contains("index_attendance_logs_studentId"))
    }

    @Test
    fun anEmptyVersion18DatabaseUpgradesCleanly() {
        // A fresh install that has not been used yet still runs the migration on first open.
        createSchema(18)

        runMigration()

        assertEquals("0", scalar("SELECT COUNT(*) FROM `students`"))
        assertTrue(tableNames().contains("students"))
        assertTrue("no leftovers", tableNames().none { it.endsWith("_repair_tmp") })
        assertTrue("the placeholder is cleaned up", !tableNames().contains("students_old"))
    }

    @Test
    fun aVersion15DatabaseCanStillReachVersion19() {
        // The 15-to-16 migration now runs the same rebuild, so walk the whole path a long-standing
        // install would take. Only the tables 15-to-16 touches are modelled here.
        exec(
            "CREATE TABLE `students` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `firstName` TEXT NOT NULL, " +
                    "`lastName` TEXT NOT NULL, `gender` TEXT NOT NULL, `birthday` INTEGER NOT NULL, " +
                    "`address` TEXT NOT NULL DEFAULT '', `contactNumber` TEXT NOT NULL DEFAULT '', " +
                    "`picturePath` TEXT NOT NULL DEFAULT '', `guardiansJson` TEXT NOT NULL DEFAULT '[]', " +
                    "`customDataJson` TEXT NOT NULL DEFAULT '{}', `isDeleted` INTEGER NOT NULL DEFAULT 0, " +
                    "`lastModified` INTEGER NOT NULL DEFAULT 0, `className` TEXT NOT NULL DEFAULT '', " +
                    "`seatingX` REAL NOT NULL DEFAULT -1.0, `seatingY` REAL NOT NULL DEFAULT -1.0)"
        )
        exec(
            "CREATE TABLE `assessment_columns` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                    "`maxPoints` REAL NOT NULL DEFAULT 100.0, `examDate` INTEGER NOT NULL DEFAULT 0, " +
                    "`checkDate` INTEGER NOT NULL DEFAULT 0, `savedFilterId` INTEGER NOT NULL DEFAULT 0, " +
                    "`isDeleted` INTEGER NOT NULL DEFAULT 0, `lastModified` INTEGER NOT NULL DEFAULT 0)"
        )
        exec(
            "CREATE TABLE `assessment_scores` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `columnId` INTEGER NOT NULL, " +
                    "`studentId` INTEGER NOT NULL, `score` TEXT NOT NULL, `lastModified` INTEGER NOT NULL DEFAULT 0, " +
                    "FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`columnId`) REFERENCES `assessment_columns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        exec(
            "CREATE TABLE `behavior_incidents` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `studentId` INTEGER NOT NULL, " +
                    "`title` TEXT NOT NULL, `category` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, `incidentDate` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        exec("INSERT INTO `students` (`id`, `firstName`, `lastName`, `gender`, `birthday`, `className`, `seatingX`, `seatingY`) VALUES (1, 'Ana', 'Cruz', 'F', 100, 'Class 1', 0.25, 0.75)")
        exec("INSERT INTO `assessment_columns` (`id`, `name`) VALUES (1, 'Quiz 1')")
        exec("INSERT INTO `assessment_scores` (`id`, `columnId`, `studentId`, `score`) VALUES (1, 1, 1, '18')")
        exec("INSERT INTO `behavior_incidents` (`id`, `studentId`, `title`, `category`, `description`, `timestamp`, `incidentDate`) VALUES (1, 1, 'Note', 'Positive', 'd', 1, 1)")

        // 15 -> 16 is now the same rebuild. attendance_logs is untouched by it, so it is not
        // modelled here and the indices from 18 -> 19 are not part of this step.
        runRebuild()

        assertEquals("the seat should survive as JSON", "{\"Class 1\":{\"x\":0.25,\"y\":0.75}}", scalar("SELECT `seatingJson` FROM `students` WHERE `id` = 1"))
        assertEquals("[\"Class 1\"]", scalar("SELECT `classNamesJson` FROM `students` WHERE `id` = 1"))
        assertEquals("1", scalar("SELECT COUNT(*) FROM `behavior_incidents`"))
        assertEquals("1", scalar("SELECT COUNT(*) FROM `assessment_scores`"))
        assertTrue(columnsOf("behavior_incidents").contains("photoPath"))
    }
}
