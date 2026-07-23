package dev.soloistdev.studenttracker.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.util.UUID
import androidx.core.content.edit

object SecurityHelper {
    private const val PREFS_FILE = "secure_prefs"
    private const val KEY_DB_PASSPHRASE = "db_passphrase"

    fun getDatabasePassphrase(context: Context): CharArray {
        // Generate/retrieve the Master Key from the Android KeyStore
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        // Open the hardware-encrypted shared preferences file
        val sharedPreferences = EncryptedSharedPreferences.create(
            PREFS_FILE,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        var passphrase = sharedPreferences.getString(KEY_DB_PASSPHRASE, null)
        if (passphrase == null) {
            // Generate a secure 256-bit random passphrase if it doesn't exist yet
            passphrase = UUID.randomUUID().toString() + UUID.randomUUID().toString()
            sharedPreferences.edit { putString(KEY_DB_PASSPHRASE, passphrase) }
        }

        return passphrase.toCharArray()
    }
}