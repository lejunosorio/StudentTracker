package dev.soloistdev.studenttracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.soloistdev.studenttracker.MemoryHelper
import dev.soloistdev.studenttracker.security.SecurityHelper
import net.sqlcipher.database.SupportFactory
import java.io.IOException

@Database(
    entities = [
        StudentEntity::class,
        FormTemplateEntity::class,
        SavedFilterEntity::class,
        AttendanceRecordEntity::class,
        AttendanceLogEntity::class,
        BehaviorIncidentEntity::class,
        MessageTemplateEntity::class,
        AssessmentColumnEntity::class,
        AssessmentScoreEntity::class,
        ClassroomEntity::class,
        GradingTermEntity::class,
        AssessmentCategoryEntity::class
    ],
    version = 17, // Version 17: Grading periods, weighted categories, unique score rows
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // --- MIGRATION 13 TO 14 ---
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                safeAddColumn(db, "form_templates", "isDeleted", "INTEGER NOT NULL DEFAULT 0")
                safeAddColumn(db, "saved_filters", "isDeleted", "INTEGER NOT NULL DEFAULT 0")
                safeAddColumn(db, "attendance_records", "isDeleted", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        // --- MIGRATION 14 TO 15 ---
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                safeAddColumn(db, "students", "className", "TEXT NOT NULL DEFAULT ''")
                safeAddColumn(db, "students", "seatingX", "REAL NOT NULL DEFAULT -1.0")
                safeAddColumn(db, "students", "seatingY", "REAL NOT NULL DEFAULT -1.0")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `classrooms` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `startTime` TEXT NOT NULL,
                        `endTime` TEXT NOT NULL,
                        `isDeleted` INTEGER NOT NULL DEFAULT 0,
                        `lastModified` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `assessment_columns` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `maxPoints` REAL NOT NULL DEFAULT 100.0,
                        `examDate` INTEGER NOT NULL DEFAULT 0,
                        `checkDate` INTEGER NOT NULL DEFAULT 0,
                        `savedFilterId` INTEGER NOT NULL DEFAULT 0,
                        `isDeleted` INTEGER NOT NULL DEFAULT 0,
                        `lastModified` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `assessment_scores` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `columnId` INTEGER NOT NULL,
                        `studentId` INTEGER NOT NULL,
                        `score` TEXT NOT NULL,
                        `lastModified` INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(`studentId`) REFERENCES `students`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`columnId`) REFERENCES `assessment_columns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_assessment_scores_studentId` ON `assessment_scores` (`studentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_assessment_scores_columnId` ON `assessment_scores` (`columnId`)")
            }
        }

        // --- MIGRATION 15 TO 16 ---
        // Performs the SQLite Table Recreation Pattern to cleanly drop obsolete columns and back-fill JSON sets
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Rename the current dirty table
                db.execSQL("ALTER TABLE `students` RENAME TO `students_old`")

                // 2. Create the fresh table matching Room's exact expected 14-column schema
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `students` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `firstName` TEXT NOT NULL,
                        `lastName` TEXT NOT NULL,
                        `gender` TEXT NOT NULL,
                        `birthday` INTEGER NOT NULL,
                        `address` TEXT NOT NULL DEFAULT '',
                        `contactNumber` TEXT NOT NULL DEFAULT '',
                        `picturePath` TEXT NOT NULL DEFAULT '',
                        `guardiansJson` TEXT NOT NULL DEFAULT '[]',
                        `customDataJson` TEXT NOT NULL DEFAULT '{}',
                        `isDeleted` INTEGER NOT NULL DEFAULT 0,
                        `lastModified` INTEGER NOT NULL DEFAULT 0,
                        `classNamesJson` TEXT NOT NULL DEFAULT '[]',
                        `seatingJson` TEXT NOT NULL DEFAULT '{}'
                    )
                """.trimIndent())

                // 3. Migrate and back-fill data into structured JSON formats inside a single transaction
                db.execSQL("""
                    INSERT INTO `students` (
                        `id`, `firstName`, `lastName`, `gender`, `birthday`, 
                        `address`, `contactNumber`, `picturePath`, `guardiansJson`, 
                        `customDataJson`, `isDeleted`, `lastModified`, `classNamesJson`, `seatingJson`
                    )
                    SELECT 
                        `id`, `firstName`, `lastName`, `gender`, `birthday`, 
                        `address`, `contactNumber`, `picturePath`, `guardiansJson`, 
                        `customDataJson`, `isDeleted`, `lastModified`,
                        (CASE WHEN `className` IS NOT NULL AND `className` != '' THEN '["' || replace(`className`, '"', '\"') || '"]' ELSE '[]' END),
                        (CASE WHEN `className` IS NOT NULL AND `className` != '' AND `seatingX` >= 0 AND `seatingY` >= 0 THEN '{"' || replace(`className`, '"', '\"') || '":{"x":' || `seatingX` || ',"y":' || `seatingY` || '}}' ELSE '{}' END)
                    FROM `students_old`
                """.trimIndent())

                // 4. Safely drop the old table
                db.execSQL("DROP TABLE `students_old`")
            }
        }


        // --- MIGRATION 16 TO 17 ---
        // Adds grading periods and weighted categories, and enforces one score per student
        // per assessment. Existing duplicate score rows are collapsed to the newest before the
        // unique index is created, otherwise the index would fail to build.
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `grading_terms` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `startDate` INTEGER NOT NULL DEFAULT 0,
                        `endDate` INTEGER NOT NULL DEFAULT 0,
                        `isActive` INTEGER NOT NULL DEFAULT 0,
                        `isDeleted` INTEGER NOT NULL DEFAULT 0,
                        `lastModified` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `assessment_categories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `weight` REAL NOT NULL DEFAULT 0.0,
                        `termId` INTEGER NOT NULL DEFAULT 0,
                        `isDeleted` INTEGER NOT NULL DEFAULT 0,
                        `lastModified` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                safeAddColumn(db, "assessment_columns", "termId", "INTEGER NOT NULL DEFAULT 0")
                safeAddColumn(db, "assessment_columns", "categoryId", "INTEGER NOT NULL DEFAULT 0")

                // Collapse duplicates, keeping the most recently written row per pair
                db.execSQL("""
                    DELETE FROM `assessment_scores`
                    WHERE `id` NOT IN (
                        SELECT MAX(`id`) FROM `assessment_scores` GROUP BY `columnId`, `studentId`
                    )
                """.trimIndent())

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_assessment_scores_columnId_studentId` ON `assessment_scores` (`columnId`, `studentId`)")
            }
        }
        private fun safeAddColumn(db: SupportSQLiteDatabase, tableName: String, columnName: String, typeAndDefault: String) {
            try {
                db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $typeAndDefault")
            } catch (e: Exception) {
                android.util.Log.w("AppDatabase", "Column $columnName may already exist in table $tableName: ${e.message}")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val passphrase = SecurityHelper.getDatabasePassphrase(context)
                val factory = SupportFactory(passphrase.map { it.code.toByte() }.toByteArray())

                val instance = try {
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "student_tracker_secure_db"
                    )
                        .openHelperFactory(factory)
                        .addMigrations(MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
                        .build().also {
                            it.openHelper.writableDatabase
                        }
                } catch (e: Exception) {
                    MemoryHelper.zeroMemory(passphrase)
                    throw IOException(
                        "Local database decryption failed. This can happen due to transient Android KeyStore errors. Please reboot your device or verify lock screen settings.",
                        e
                    )
                }

                MemoryHelper.zeroMemory(passphrase)
                INSTANCE = instance
                instance
            }
        }
    }
}