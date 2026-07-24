package dev.soloistdev.studenttracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.soloistdev.studenttracker.MemoryHelper
import dev.soloistdev.studenttracker.security.SecurityHelper
import net.sqlcipher.database.SupportFactory

// Increment version to 6 and append new attendance entities
@Database(
    entities = [
        StudentEntity::class,
        FormTemplateEntity::class,
        SavedFilterEntity::class,
        AttendanceRecordEntity::class,
        AttendanceLogEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

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
                        .fallbackToDestructiveMigration()
                        .build().also {
                            // Trigger a quick database write connection to verify key decryption is successful [1]
                            it.openHelper.writableDatabase
                        }
                } catch (_: Exception) {
                    // SELF-HEALING RECOVERY: Delete the corrupted database file on disk and rebuild cleanly [1]
                    context.deleteDatabase("student_tracker_secure_db")
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "student_tracker_secure_db"
                    )
                        .openHelperFactory(factory)
                        .fallbackToDestructiveMigration()
                        .build()
                }

                MemoryHelper.zeroMemory(passphrase)

                INSTANCE = instance
                instance
            }
        }
    }
}