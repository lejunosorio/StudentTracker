package dev.soloistdev.studenttracker.ui

import android.app.Application
import android.util.Base64
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory // Resolved: Cryptographic PBKDF2 imports [1]
import javax.crypto.spec.PBEKeySpec     // Resolved: Cryptographic PBKDF2 imports [1]

class SecurityViewModel(application: Application) : AndroidViewModel(application) {

    private val masterKey = MasterKey.Builder(application)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    // Opened synchronously, on purpose. This decides whether the lock screen is shown at all, and
    // it is read before the first frame; loading it asynchronously would mean either showing the
    // gate to someone who has turned it off and then snatching it away, or holding the app on a
    // spinner. It is one KeyStore round-trip, once per process, on a retained ViewModel.
    private val sharedPreferences = EncryptedSharedPreferences.create(
        application,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    val isConfigured: Boolean = sharedPreferences.getBoolean("recovery_pin_configured", false)

    private val _isUnlocked = MutableStateFlow(
        if (isConfigured) {
            !sharedPreferences.getBoolean("security_gate_enabled", true)
        } else {
            false
        }
    )
    val isUnlocked: StateFlow<Boolean> = _isUnlocked

    private val _isBiometricEnabled = MutableStateFlow(sharedPreferences.getBoolean("biometric_enabled", true))
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled

    private val _isSecurityGateEnabled = MutableStateFlow(sharedPreferences.getBoolean("security_gate_enabled", true))
    val isSecurityGateEnabled: StateFlow<Boolean> = _isSecurityGateEnabled

    // Generates a high-entropy 128-bit secure random salt
    private fun generateSalt(): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        return Base64.encodeToString(saltBytes, Base64.NO_WRAP)
    }

    /**
     * Compares two encoded hashes without leaking, through timing, how much of one matched.
     *
     * A plain String equality returns as soon as two characters differ, which is measurable and
     * lets an attacker recover a hash a character at a time. MessageDigest.isEqual always reads
     * both operands to the end.
     */
    private fun hashesMatch(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    // Hashes the PIN securely using PBKDF2 with HmacSHA256 and key stretching [1]
    private fun hashPin(pin: String, salt: String): String {
        val iterations = 10000 // Key stretching iterations [1]
        val keyLength = 256   // 256-bit derived key [1]
        val saltBytes = Base64.decode(salt, Base64.NO_WRAP)

        val spec = PBEKeySpec(pin.toCharArray(), saltBytes, iterations, keyLength)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hashedBytes = keyFactory.generateSecret(spec).encoded

        return Base64.encodeToString(hashedBytes, Base64.NO_WRAP)
    }

    fun saveRecoveryPin(pin: String): Boolean {
        if (pin.length !in 4..6) return false

        val salt = generateSalt()
        val hashedPin = hashPin(pin, salt) // Enforces PBKDF2 key-stretching [1]

        sharedPreferences.edit {
            putString("recovery_pin_salt", salt)
            putString("recovery_pin_hash", hashedPin)
            putBoolean("recovery_pin_configured", true)
            putBoolean("security_gate_enabled", true)
        }
        _isSecurityGateEnabled.value = true
        _isUnlocked.value = true
        return true
    }

    fun verifyPin(pin: String): Boolean {
        val salt = sharedPreferences.getString("recovery_pin_salt", "") ?: return false
        val savedHash = sharedPreferences.getString("recovery_pin_hash", "") ?: return false
        if (salt.isEmpty() || savedHash.isEmpty()) return false

        val computedHash = hashPin(pin, salt)
        if (hashesMatch(computedHash, savedHash)) {
            _isUnlocked.value = true
            return true
        }
        return false
    }

    fun setBiometricEnabled(enabled: Boolean) {
        sharedPreferences.edit {
            putBoolean("biometric_enabled", enabled)
        }
        _isBiometricEnabled.value = enabled
    }

    fun setSecurityGateEnabled(enabled: Boolean) {
        sharedPreferences.edit {
            putBoolean("security_gate_enabled", enabled)
        }
        _isSecurityGateEnabled.value = enabled
        if (!enabled) {
            _isUnlocked.value = true
        }
    }

    fun resetPin(oldPin: String, newPin: String): Boolean {
        if (newPin.length !in 4..6) return false

        val salt = sharedPreferences.getString("recovery_pin_salt", "") ?: return false
        val savedHash = sharedPreferences.getString("recovery_pin_hash", "") ?: return false
        if (salt.isEmpty() || savedHash.isEmpty()) return false

        val computedOldHash = hashPin(oldPin, salt)
        if (hashesMatch(computedOldHash, savedHash)) {
            val newSalt = generateSalt()
            val newHashedPin = hashPin(newPin, newSalt)
            sharedPreferences.edit {
                putString("recovery_pin_salt", newSalt)
                putString("recovery_pin_hash", newHashedPin)
            }
            return true
        }
        return false
    }
}