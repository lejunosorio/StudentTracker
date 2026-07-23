package dev.soloistdev.studenttracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.soloistdev.studenttracker.MemoryHelper
import dev.soloistdev.studenttracker.security.SecurityHelper
import net.sqlcipher.database.SupportFactory

@Database(
    // MapArchiveEntity removed from entities list
    entities = [StudentEntity::class, FormTemplateEntity::class, SavedFilterEntity::class],
    version = 4, // Stepped down from 5 [1]
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

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "student_tracker_secure_db"
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration(false) // Auto-creates version 5 tables cleanly
                    .build()

                MemoryHelper.zeroMemory(passphrase)

                INSTANCE = instance
                instance
            }
        }
    }
}