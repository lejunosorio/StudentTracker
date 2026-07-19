package dev.soloistdev.studenttracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.soloistdev.studenttracker.MemoryHelper
import dev.soloistdev.studenttracker.security.SecurityHelper
import net.sqlcipher.database.SupportFactory

// Increment version to 4 and add MapArchiveEntity to the list of entities
@Database(
    entities = [StudentEntity::class, FormTemplateEntity::class, MapArchiveEntity::class],
    version = 4,
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
                    .fallbackToDestructiveMigration()
                    .build()

                MemoryHelper.zeroMemory(passphrase)

                INSTANCE = instance
                instance
            }
        }
    }
}