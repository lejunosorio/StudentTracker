package dev.soloistdev.studenttracker

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope // Required for coroutine scope launches [1]
import dev.soloistdev.studenttracker.security.IntegrityChecker
import dev.soloistdev.studenttracker.ui.AppNavigation
import dev.soloistdev.studenttracker.ui.theme.StudentTrackerTheme
import kotlinx.coroutines.Dispatchers // Required for thread scheduling [1]
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Asynchronous Root Check to prevent main-thread ANR stutters [1]
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

        // 2. Anti-Tapjacking Overlay Protection
        window.decorView.filterTouchesWhenObscured = true

        setContent {
            val context = this
            val sharedPrefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }

            // Reactive states mapped to the unencrypted SharedPreferences XML
            var dynamicColorEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("dynamic_colors", true)) }
            var forceDarkThemeEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("force_dark_theme", true)) }

            // DisposableEffect registers/unregisters the listener cleanly withCompose Lifecycles
            DisposableEffect(sharedPrefs) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    when (key) {
                        "dynamic_colors" -> {
                            dynamicColorEnabled = sharedPrefs.getBoolean("dynamic_colors", true)
                        }
                        "force_dark_theme" -> {
                            forceDarkThemeEnabled = sharedPrefs.getBoolean("force_dark_theme", true)
                        }
                    }
                }
                sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            // Fallback rules: Force Dark Mode, or fallback dynamically to the Android system OS setting
            val darkThemeMode = if (forceDarkThemeEnabled) true else isSystemInDarkTheme()

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
}