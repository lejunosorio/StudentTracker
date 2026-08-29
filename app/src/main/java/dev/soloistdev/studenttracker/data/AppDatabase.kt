package dev.soloistdev.studenttracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.soloistdev.studenttracker.MemoryHelper
import dev.soloistdev.studenttracker.security.SecurityHelper
// net.zetetic.database.sqlcipher, not the legacy net.sqlcipher. The old artifact
// (net.zetetic:android-database-sqlcipher) stopped at 4.5.4 and ships a libsqlcipher.so whose LOAD
// segments are 4 KB-aligned, which Android 15 and later refuse to load on a 16 KB-page device.
// The replacement is the same SQLCipher 4 on-disk format, so existing databases open unchanged.
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
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
        AssessmentCategoryEntity::class,
        RubricEntity::class,
        RubricLevelEntity::class,
        ParticipationCountEntity::class,
        ContactLogEntity::class
    ],
    version = 20, // Version 20: meeting days, incident follow-up, due dates, contact log
    // Exported so each version is checked in and future migrations can be validated against a
    // known-good schema instead of by inspection.
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Rebuilds `students` and the two tables that reference it, to whatever state they are in.
         *
         * Two different releases of the 15-to-16 migration left databases that Room refuses to
         * open, and neither is reachable by re-running that migration - the version counter has
         * already moved past it, so an affected install crashes on every launch, permanently:
         *
         *  - one release added the JSON columns with ALTER TABLE and left `className`, `seatingX`
         *    and `seatingY` behind, so `students` has three columns Room does not expect;
         *  - the current release renames `students` aside, which modern SQLite treats as a reason
         *    to repoint every foreign key referencing it, leaving `behavior_incidents` and
         *    `assessment_scores` pointing at a `students_old` that is then dropped.
         *
         * Rebuilding all three unconditionally fixes both, and is a no-op in effect for a database
         * that was already correct. Student data in the legacy columns is folded into the JSON
         * ones on the way through rather than dropped.
         */
        private fun rebuildStudentsAndChildren(db: SupportSQLiteDatabase) {
            SchemaRepair.statements(
                tables = tableNames(db),
                studentColumns = columnNames(db, "students"),
                incidentColumns = columnNames(db, "behavior_incidents"),
                scoreColumns = columnNames(db, "assessment_scores")
            ).forEach { db.execSQL(it) }
        }

        /** Tables currently in the schema, including any left behind by a half-finished upgrade. */
        private fun tableNames(db: SupportSQLiteDatabase): Set<String> {
            val names = mutableSetOf<String>()
            db.query("SELECT `name` FROM `sqlite_master` WHERE `type` = 'table'").use { cursor ->
                while (cursor.moveToNext()) {
                    names.add(cursor.getString(0))
                }
            }
            return names
        }

        /** Column names actually present on [table], so a repair can adapt to either damaged shape. */
        private fun columnNames(db: SupportSQLiteDatabase, table: String): Set<String> {
            val names = mutableSetOf<String>()
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex == -1) return names
                while (cursor.moveToNext()) {
                    names.add(cursor.getString(nameIndex))
                }
            }
            return names
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
                // Re-check inside the lock: two callers can both see a null INSTANCE outside it.
                INSTANCE?.let { return it }

                val passphrase = SecurityHelper.getDatabasePassphrase(context)
                // clearPassphrase = false. The factory otherwise zeroes the key bytes as soon as
                // the database is first opened, so any reopen of the helper - which Room does on
                // its own after a close - fails with "file is not a database".
                val factory = SupportOpenHelperFactory(
                    passphrase.map { it.code.toByte() }.toByteArray(),
                    null,
                    false
                )

                val instance = try {
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        SecurityHelper.DATABASE_NAME
                    )
                        .openHelperFactory(factory)
                        // No migrations are registered while the app is pre-release, so a schema
                        // change has to be allowed to recreate the database rather than throwing
                        // "a migration from N to N+1 was required but not found" on every launch.
                        // This DISCARDS local data on a version bump - fine before release, and
                        // the thing to replace with real migrations before anyone else installs it.
                        .fallbackToDestructiveMigration(dropAllTables = true)
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

                // Only the CharArray copy is wiped. The factory keeps the byte[] it was handed,
                // deliberately, so the database can be reopened for the life of the process.
                MemoryHelper.zeroMemory(passphrase)
                INSTANCE = instance
                instance
            }
        }
    }
}