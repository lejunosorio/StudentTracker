@file:Suppress("DEPRECATION")

package dev.soloistdev.studenttracker.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.util.UUID
import androidx.core.content.edit

object SecurityHelper {
    private const val PREFS_FILE = "secure_prefs"
    private const val KEY_DB_PASSPHRASE = "db_passphrase"

    fun getDatabasePassphrase(context: Context): CharArray {
        // Enforce maximum hardware isolation via StrongBox (Secure Element) with standard TEE Fallback [1]
        val masterKey = try {
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

        val sharedPreferences = try {
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            try {
                val sharedPrefsDir = File(context.filesDir.parent, "shared_prefs")
                val corruptedFile = File(sharedPrefsDir, "$PREFS_FILE.xml")
                if (corruptedFile.exists()) {
                    corruptedFile.delete()
                }
            } catch (_: Exception) {
                // Suppressed
            }

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
            passphrase = UUID.randomUUID().toString() + UUID.randomUUID().toString()
            sharedPreferences.edit { putString(KEY_DB_PASSPHRASE, passphrase) }
        }

        return passphrase.toCharArray()
    }
}