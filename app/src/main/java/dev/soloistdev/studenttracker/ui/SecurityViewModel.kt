package dev.soloistdev.studenttracker.ui

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SecurityViewModel(application: Application) : AndroidViewModel(application) {

    private val masterKey = MasterKey.Builder(application)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        application,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    val isConfigured: Boolean = sharedPreferences.getBoolean("recovery_pin_configured", false)

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked

    private val _isBiometricEnabled = MutableStateFlow(sharedPreferences.getBoolean("biometric_enabled", true))
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled

    fun saveRecoveryPin(pin: String): Boolean {
        if (pin.length !in 4..6) return false

        sharedPreferences.edit {
            putString("recovery_pin_hash", pin.hashCode().toString())
            putBoolean("recovery_pin_configured", true)
        }
        _isUnlocked.value = true
        return true
    }

    fun verifyPin(pin: String): Boolean {
        val savedHash = sharedPreferences.getString("recovery_pin_hash", "")
        if (pin.hashCode().toString() == savedHash) {
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

    fun resetPin(oldPin: String, newPin: String): Boolean {
        if (newPin.length !in 4..6) return false

        val savedHash = sharedPreferences.getString("recovery_pin_hash", "")
        if (oldPin.hashCode().toString() == savedHash) {
            sharedPreferences.edit {
                putString("recovery_pin_hash", newPin.hashCode().toString())
            }
            return true
        }
        return false
    }
}