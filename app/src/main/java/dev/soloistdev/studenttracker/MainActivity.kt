package dev.soloistdev.studenttracker

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme // RESOLVED: System dark mode check import
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dev.soloistdev.studenttracker.security.IntegrityChecker
import dev.soloistdev.studenttracker.ui.AppNavigation
import dev.soloistdev.studenttracker.ui.theme.StudentTrackerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val integrityChecker = IntegrityChecker(this)
        lifecycleScope.launch(Dispatchers.IO) {
            val isRooted = integrityChecker.isDeviceRooted()
            if (isRooted) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Security Error: Rooted environment detected. App closing.", Toast.LENGTH_LONG).show()
                    finishAffinity()
                }
            }
        }

        window.decorView.filterTouchesWhenObscured = true

        setContent {
            val context = this
            val sharedPrefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }

            var dynamicColorEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("dynamic_colors", true)) }

            // UPDATED: Standardized string state defaults to "System"
            var appTheme by remember { mutableStateOf(sharedPrefs.getString("app_theme", "System") ?: "System") }

            DisposableEffect(sharedPrefs) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    when (key) {
                        "dynamic_colors" -> {
                            dynamicColorEnabled = sharedPrefs.getBoolean("dynamic_colors", true)
                        }
                        "app_theme" -> {
                            // Automatically triggers compose invalidation and redraws the UI
                            appTheme = sharedPrefs.getString("app_theme", "System") ?: "System"
                        }
                    }
                }
                sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            // RESOLVED: Maps 3-state parameters to theme configs [1]
            val darkThemeMode = when (appTheme) {
                "Light" -> false
                "Dark" -> true
                else -> isSystemInDarkTheme() // Natively follows android system configurations
            }

            StudentTrackerTheme(
                darkTheme = darkThemeMode,
                dynamicColor = dynamicColorEnabled
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}