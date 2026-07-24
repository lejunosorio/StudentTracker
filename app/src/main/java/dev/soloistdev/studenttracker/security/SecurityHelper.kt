@file:Suppress("DEPRECATION")

package dev.soloistdev.studenttracker.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey // Use MasterKey instead of obsolete MasterKeys [1]
import java.io.File // Required for secure file purging [1]
import java.util.UUID
import androidx.core.content.edit

object SecurityHelper {
    private const val PREFS_FILE = "secure_prefs"
    private const val KEY_DB_PASSPHRASE = "db_passphrase"

    fun getDatabasePassphrase(context: Context): CharArray {
        // Build a modern MasterKey instance securely from the Android KeyStore [1]
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        // Call the modern creation overload of EncryptedSharedPreferences [1]
        val sharedPreferences = try {
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            // SELF-HEALING RECOVERY: Delete the corrupted preferences file on disk [1]
            try {
                val sharedPrefsDir = File(context.filesDir.parent, "shared_prefs")
                val corruptedFile = File(sharedPrefsDir, "$PREFS_FILE.xml")
                if (corruptedFile.exists()) {
                    corruptedFile.delete()
                }
            } catch (_: Exception) {
                // Suppressed
            }

            // Re-create a clean, un-corrupted file automatically [1]
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        var passphrase = sharedPreferences.getString(KEY_DB_PASSPHRASE, null)
        if (passphrase == null) {
            // Generate a secure 256-bit random passphrase if it doesn't exist yet
            passphrase = UUID.randomUUID().toString() + UUID.randomUUID().toString()
            sharedPreferences.edit { putString(KEY_DB_PASSPHRASE, passphrase) }
        }

        return passphrase.toCharArray()
    }
}