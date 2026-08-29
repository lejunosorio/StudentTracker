@file:Suppress("DEPRECATION")

package dev.soloistdev.studenttracker.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.util.UUID
import androidx.core.content.edit

/**
 * Raised when the database passphrase cannot be read and must not be regenerated.
 *
 * Regenerating it is unrecoverable: the existing SQLCipher database can only ever be opened with
 * the passphrase it was created under. A transient KeyStore fault - common right after a reboot,
 * or after a lock-screen change - therefore has to fail loudly rather than quietly resetting.
 */
class DatabaseKeyUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

object SecurityHelper {
    private const val PREFS_FILE = "secure_prefs"
    private const val KEY_DB_PASSPHRASE = "db_passphrase"

    /** Owned here because whether this file exists decides if a key reset is survivable. */
    const val DATABASE_NAME = "student_tracker_secure_db"

    fun getDatabasePassphrase(context: Context): CharArray {
        val masterKey = buildMasterKey(context)

        val sharedPreferences = try {
            openPrefs(context, masterKey)
        } catch (first: Exception) {
            // KeyStore faults are frequently transient. One retry costs nothing and covers the
            // common case of the keystore not being ready yet.
            try {
                openPrefs(context, masterKey)
            } catch (second: Exception) {
                // Only now consider discarding the store - and only when there is no database
                // whose sole key it holds. The previous behaviour deleted it unconditionally,
                // which silently made every existing student record permanently unreadable and
                // took the recovery PIN with it.
                if (databaseExists(context)) {
                    throw DatabaseKeyUnavailableException(
                        "The secure key store could not be opened, so the database was left untouched " +
                                "rather than being reset. Reboot the device and reopen the app; if this " +
                                "persists, restore from a backup.",
                        second
                    )
                }
                deleteCorruptPrefs(context)
                openPrefs(context, masterKey)
            }
        }

        val existing = sharedPreferences.getString(KEY_DB_PASSPHRASE, null)
        if (existing != null) return existing.toCharArray()

        // No passphrase yet. If a database somehow exists alongside a store that has lost its
        // passphrase, minting a new one would create an unopenable pair - say so instead.
        if (databaseExists(context)) {
            throw DatabaseKeyUnavailableException(
                "The database exists but its key is missing from the secure store. Generating a new " +
                        "key would make it permanently unreadable, so it was left alone. Restore from a backup."
            )
        }

        val generated = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        sharedPreferences.edit { putString(KEY_DB_PASSPHRASE, generated) }
        return generated.toCharArray()
    }

    /**
     * Whether the app asks for biometrics or a PIN on launch.
     *
     * Read by anything that puts student data somewhere the gate does not cover - a notification,
     * a home-screen widget - so it can fall back to counts instead of names. Fails closed: if the
     * key store will not open, assume the gate is on rather than leaking names on a guess.
     */
    fun isSecurityGateEnabled(context: Context): Boolean =
        try {
            openPrefs(context, buildMasterKey(context)).getBoolean("security_gate_enabled", true)
        } catch (_: Exception) {
            true
        }

    private fun buildMasterKey(context: Context): MasterKey =
        try {
            // Enforce maximum hardware isolation via StrongBox (Secure Element) with standard TEE Fallback
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .setRequestStrongBoxBacked(true) // Enforce StrongBox chip if present on the system
                .build()
        } catch (e: Exception) {
            e.printStackTrace()
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build() // Fallback gracefully to standard TEE isolation
        }

    private fun openPrefs(context: Context, masterKey: MasterKey) =
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    private fun databaseExists(context: Context): Boolean =
        context.getDatabasePath(DATABASE_NAME).exists()

    private fun deleteCorruptPrefs(context: Context) {
        try {
            val sharedPrefsDir = File(context.filesDir.parent, "shared_prefs")
            File(sharedPrefsDir, "$PREFS_FILE.xml").takeIf { it.exists() }?.delete()
        } catch (_: Exception) {
            // Suppressed
        }
    }
}
