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

        composable("security_gate") {
            SecurityGateScreen(
                onUnlockSuccess = {
                    navController.navigate("view_all") {
                        popUpTo("security_gate") { inclusive = true }
                    }
                }
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
                // Replaces Maps with Saved Filters
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
                    navController.navigate("view_all") {
                        popUpTo("view_all") { inclusive = true }
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

        // Dedicated Saved Filters Screen replacing Map Screens
        composable("saved_filters") {
            SavedFiltersScreen(
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                },
                onStudentClick = { id -> // Enables seamless profile navigation
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