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

    // Checks if a Master PIN has ever been configured
    val isConfigured: Boolean = sharedPreferences.getBoolean("recovery_pin_configured", false)

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked

    // Save action (used on first-launch setup)
    fun saveRecoveryPin(pin: String): Boolean {
        if (pin.length < 4 || pin.length > 6) return false
        sharedPreferences.edit()
            .putString("recovery_pin_hash", pin.hashCode().toString())
            .putBoolean("recovery_pin_configured", true)
            .apply()
        _isUnlocked.value = true
        return true
    }

    // Verification action (used on subsequent app launches)
    fun verifyPin(pin: String): Boolean {
        val savedHash = sharedPreferences.getString("recovery_pin_hash", "")
        if (pin.hashCode().toString() == savedHash) {
            _isUnlocked.value = true
            return true
        }
        return false
    }
}