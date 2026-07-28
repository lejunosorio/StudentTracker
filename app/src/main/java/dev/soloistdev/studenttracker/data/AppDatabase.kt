package dev.soloistdev.studenttracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.soloistdev.studenttracker.MemoryHelper
import dev.soloistdev.studenttracker.security.SecurityHelper
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        StudentEntity::class,
        FormTemplateEntity::class,
        SavedFilterEntity::class,
        AttendanceRecordEntity::class,
        AttendanceLogEntity::class
    ],
    version = 7, // Incremented database version to 7 to handle the schema update [1]
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
                            it.openHelper.writableDatabase
                        }
                } catch (_: Exception) {
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