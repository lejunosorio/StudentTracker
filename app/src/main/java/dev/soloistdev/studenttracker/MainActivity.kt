package dev.soloistdev.studenttracker

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity // <-- IMPORT FRAGMENT ACTIVITY
import dev.soloistdev.studenttracker.security.IntegrityChecker
import dev.soloistdev.studenttracker.ui.AppNavigation
import dev.soloistdev.studenttracker.ui.theme.StudentTrackerTheme

// CHANGE BASE CLASS TO FragmentActivity
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. TAMPER/ROOT CHECK (Sprint 1 Safety)
        val integrityChecker = IntegrityChecker(this)
        if (integrityChecker.isDeviceRooted()) {
            Toast.makeText(this, "Security Error: Rooted environment detected. App closing.", Toast.LENGTH_LONG).show()
            finishAffinity()
            return
        }

        // 2. ANTI-SCREENSHOT / CAROUSEL PII BLOCK
//        window.setFlags(
//            WindowManager.LayoutParams.FLAG_SECURE,
//            WindowManager.LayoutParams.FLAG_SECURE
//        )

        // 3. ANTI-TAPJACKING OVERLAY PROTECTION
        window.decorView.filterTouchesWhenObscured = true

        setContent {
            StudentTrackerTheme {
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