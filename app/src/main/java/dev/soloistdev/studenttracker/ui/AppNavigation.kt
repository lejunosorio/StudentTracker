package dev.soloistdev.studenttracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.soloistdev.studenttracker.data.StudentRepository
import dev.soloistdev.studenttracker.security.PdfGeneratorHelper
import kotlinx.coroutines.launch

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val repository = remember { StudentRepository(context) }

    NavHost(navController = navController, startDestination = "security_gate") {

        // 1. Security Gate / Unlock (Onboarding setup or Verify PIN)
        composable("security_gate") {
            SecurityGateScreen(
                onUnlockSuccess = {
                    navController.navigate("view_all") {
                        popUpTo("security_gate") { inclusive = true }
                    }
                }
            )
        }

        // 2. View All Student Directory
        composable("view_all") {
            ViewAllScreen(
                onAddStudent = { id ->
                    if (navController.currentDestination?.route == "view_all") {
                        navController.navigate("add_edit/$id")
                    }
                },
                onStudentClick = { id ->
                    if (navController.currentDestination?.route == "view_all") {
                        navController.navigate("profile/$id")
                    }
                },
                onOpenTemplates = {
                    if (navController.currentDestination?.route == "view_all") {
                        navController.navigate("templates")
                    }
                },
                onOpenMap = {
                    if (navController.currentDestination?.route == "view_all") {
                        navController.navigate("saved_filters")
                    }
                },
                onOpenRecycleBin = {
                    if (navController.currentDestination?.route == "view_all") {
                        navController.navigate("recycle_bin")
                    }
                },
                onOpenSync = {
                    if (navController.currentDestination?.route == "view_all") {
                        navController.navigate("sync")
                    }
                },
                onOpenSettings = {
                    if (navController.currentDestination?.route == "view_all") {
                        navController.navigate("app_settings")
                    }
                },
                onOpenBiometrics = {
                    if (navController.currentDestination?.route == "view_all") {
                        navController.navigate("biometrics_privacy")
                    }
                },
                onOpenAttendance = {
                    if (navController.currentDestination?.route == "view_all") {
                        navController.navigate("attendance")
                    }
                },
                onOpenAttendanceWithArgs = { recordId, dateMillis ->
                    if (navController.currentDestination?.route == "view_all") {
                        navController.navigate("attendance?recordId=$recordId&dateMillis=$dateMillis")
                    }
                }
            )
        }

        // 3. Student Profile Screen (Read-Only)
        composable(
            route = "profile/{studentId}",
            arguments = listOf(navArgument("studentId") { type = NavType.IntType })
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getInt("studentId") ?: -1
            StudentProfileScreen(
                studentId = studentId,
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                },
                onEdit = { id ->
                    if (navController.currentDestination?.route == "profile/{studentId}") {
                        navController.navigate("add_edit/$id") {
                            popUpTo("profile/$studentId") { inclusive = true }
                        }
                    }
                },
                onSharePdf = { studentEntity ->
                    PdfGeneratorHelper.generateAndShareStudentPdf(context, studentEntity)
                },
                onDeleteStudent = { id ->
                    kotlinx.coroutines.MainScope().launch {
                        repository.softDeleteStudent(id)
                        navController.navigate("view_all") {
                            popUpTo("view_all") { inclusive = true }
                        }
                    }
                }
            )
        }

        // 4. Add / Edit Student Form Screen
        composable(
            route = "add_edit/{studentId}",
            arguments = listOf(navArgument("studentId") { type = NavType.IntType })
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getInt("studentId") ?: -1
            AddEditStudentScreen(
                studentId = studentId,
                onBack = {
                    navController.navigate("view_all") {
                        popUpTo("view_all") { inclusive = true }
                    }
                }
            )
        }

        // 5. Template Manager Screen
        composable("templates") {
            TemplateManagerScreen(
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                }
            )
        }

        // 7. Recycle Bin (Soft Deleted) Screen
        composable("recycle_bin") {
            RecycleBinScreen(
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                }
            )
        }

        // 8. Saved Filters Screen
        composable("saved_filters") {
            SavedFiltersScreen(
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                },
                onStudentClick = { id ->
                    navController.navigate("profile/$id")
                }
            )
        }

        // 9. Biometrics & Privacy Screen
        composable("biometrics_privacy") {
            BiometricsPrivacyScreen(
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                }
            )
        }

        // 10. Backup & Sync Screen
        composable("sync") {
            SyncScreen(
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                }
            )
        }

        // 11. Attendance System Screen (Supporting optional deep-link redirection parameters)
        composable(
            route = "attendance?recordId={recordId}&dateMillis={dateMillis}",
            arguments = listOf(
                navArgument("recordId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("dateMillis") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStackEntry ->
            val recordId = backStackEntry.arguments?.getInt("recordId") ?: -1
            val dateMillis = backStackEntry.arguments?.getLong("dateMillis") ?: -1L
            AttendanceScreen(
                initialRecordId = recordId,
                initialDateMillis = dateMillis,
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                },
                onRedirectToFilters = {
                    navController.navigate("saved_filters") {
                        popUpTo("attendance") { inclusive = true }
                    }
                }
            )
        }

        // 12. App Settings Screen
        composable("app_settings") {
            AppSettingsScreen(
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                }
            )
        }
    }
}