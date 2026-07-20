package dev.soloistdev.studenttracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SecurityViewModel(application: Application) : AndroidViewModel(application) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val sharedPreferences = EncryptedSharedPreferences.create(
        "secure_prefs",
        masterKeyAlias,
        application,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    val isConfigured: Boolean = sharedPreferences.getBoolean("recovery_pin_configured", false)

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked

    // New: Observable biometric toggle state (defaulting to enabled)
    private val _isBiometricEnabled = MutableStateFlow(sharedPreferences.getBoolean("biometric_enabled", true))
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled

    fun saveRecoveryPin(pin: String): Boolean {
        if (pin.length < 4 || pin.length > 6) return false
        sharedPreferences.edit()
            .putString("recovery_pin_hash", pin.hashCode().toString())
            .putBoolean("recovery_pin_configured", true)
            .apply()
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

    // --- SPRINT 9 ENHANCEMENTS: BIOMETRICS & PRIVACY ACTIONS ---

    fun setBiometricEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("biometric_enabled", enabled).apply()
        _isBiometricEnabled.value = enabled
    }

    fun resetPin(oldPin: String, newPin: String): Boolean {
        if (newPin.length < 4 || newPin.length > 6) return false
        val savedHash = sharedPreferences.getString("recovery_pin_hash", "")
        if (oldPin.hashCode().toString() == savedHash) {
            sharedPreferences.edit()
                .putString("recovery_pin_hash", newPin.hashCode().toString())
                .apply()
            return true
        }
        return false
    }
}