package dev.soloistdev.studenttracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
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

    val securityViewModel: SecurityViewModel = viewModel()
    val isUnlocked by securityViewModel.isUnlocked.collectAsState()

    LaunchedEffect(isUnlocked) {
        if (isUnlocked) {
            navController.navigate("view_all") {
                popUpTo("security_gate") { inclusive = true }
            }
        } else {
            navController.navigate("security_gate") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = "security_gate") {

        composable("security_gate") {
            SecurityGateScreen(
                onUnlockSuccess = {
                    navController.navigate("view_all") {
                        popUpTo("security_gate") { inclusive = true }
                    }
                },
                viewModel = securityViewModel
            )
        }

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

        composable(
            route = "add_edit/{studentId}",
            arguments = listOf(navArgument("studentId") { type = NavType.IntType })
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getInt("studentId") ?: -1
            AddEditStudentScreen(
                studentId = studentId,
                onBack = {
                    // Resolved: Replaced aggressive layout-wiping navigation with standard pop [1]
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack() // Smooth natural popping backstack transition [1]
                    } else {
                        navController.navigate("view_all") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable("templates") {
            TemplateManagerScreen(
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable("recycle_bin") {
            RecycleBinScreen(
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                }
            )
        }

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

        composable("biometrics_privacy") {
            BiometricsPrivacyScreen(
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable("sync") {
            SyncScreen(
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                }
            )
        }

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