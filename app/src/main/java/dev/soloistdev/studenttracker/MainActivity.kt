package dev.soloistdev.studenttracker

import android.content.Context
import android.content.Intent // Resolved: Explicit Intent import
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
            var forceDarkThemeEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("force_dark_theme", true)) }

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

    // Resolved: Intercepts and sets incoming background deep-links so the NavHost can read them in real-time [1]
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Re-sets the active activity intent [1]
    }
}