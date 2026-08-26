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
import dev.soloistdev.studenttracker.data.BackupScheduler
import dev.soloistdev.studenttracker.data.StudentRepository
import dev.soloistdev.studenttracker.security.IntegrityChecker
import dev.soloistdev.studenttracker.ui.AppNavigation
import dev.soloistdev.studenttracker.ui.theme.StudentTrackerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {

    /** Applies the chosen language before any resource in this activity is resolved. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root detection warns; it does not lock the teacher out of their own gradebook.
        //
        // Closing the app was easy to defeat by anyone actually attacking it, false-positives on
        // custom ROMs and many perfectly ordinary devices, and the only person it reliably shut
        // out was the owner - who then had no way to reach their data or export a backup. The
        // warning gives them the information and lets them decide.
        val integrityChecker = IntegrityChecker(this)
        lifecycleScope.launch(Dispatchers.IO) {
            val isRooted = integrityChecker.isDeviceRooted()
            if (isRooted) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.warning_rooted_device),
                        Toast.LENGTH_LONG
                    ).show()
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

    /**
     * Backing up as the app leaves the foreground catches the data the teacher just entered.
     * BackupScheduler throttles internally, so this is cheap on every backgrounding and only
     * actually writes once the configured interval has elapsed.
     */
    override fun onStop() {
        super.onStop()
        val appContext = applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                BackupScheduler.maybeAutoBackup(appContext, StudentRepository(appContext))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}