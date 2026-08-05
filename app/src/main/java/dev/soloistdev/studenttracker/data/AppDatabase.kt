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
        ClassroomEntity::class
    ],
    version = 15, // Upgraded version path matching current entity structures
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // --- MIGRATION 13 TO 14 ---
        // Safely introduces soft-deletion flags to existing operational schemas
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                safeAddColumn(db, "form_templates", "isDeleted", "INTEGER NOT NULL DEFAULT 0")
                safeAddColumn(db, "saved_filters", "isDeleted", "INTEGER NOT NULL DEFAULT 0")
                safeAddColumn(db, "attendance_records", "isDeleted", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        // --- MIGRATION 14 TO 15 ---
        // Creates classrooms table, coordinates seating matrices, and sets up grading structures
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add Classroom attributes and float-coordinate columns to student directory
                safeAddColumn(db, "students", "className", "TEXT NOT NULL DEFAULT ''")
                safeAddColumn(db, "students", "seatingX", "REAL NOT NULL DEFAULT -1.0")
                safeAddColumn(db, "students", "seatingY", "REAL NOT NULL DEFAULT -1.0")

                // 2. Build the classrooms planning table
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

                // 3. Build academic assessment column records
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

                // 4. Build relational grade evaluation score matrices with foreign constraints
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

                // 5. Establish indexing to optimize relational student queries
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_assessment_scores_studentId` ON `assessment_scores` (`studentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_assessment_scores_columnId` ON `assessment_scores` (`columnId`)")
            }
        }

        // Defensive column addition helper executing safe operations to bypass database crashes
        private fun safeAddColumn(
            db: SupportSQLiteDatabase,
            tableName: String,
            columnName: String,
            typeAndDefault: String
        ) {
            try {
                db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $typeAndDefault")
            } catch (e: Exception) {
                // Column likely exists from previous testing; logged for analysis without throwing an exception
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
                        .addMigrations(MIGRATION_13_14, MIGRATION_14_15)
                        .fallbackToDestructiveMigration() // Retained as an absolute backup if migrations fail
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