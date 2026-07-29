package dev.soloistdev.studenttracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
        BehaviorIncidentEntity::class
    ],
    version = 9,
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