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
                onOpenMapArchives = {
                    if (navController.currentDestination?.route == "view_all") {
                        navController.navigate("map_archives")
                    }
                },
                onOpenMap = {
                    if (navController.currentDestination?.route == "view_all") {
                        navController.navigate("global_map")
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
                onOpenBiometrics = { // Supply navigation route callback
                    if (navController.currentDestination?.route == "view_all") {
                        navController.navigate("biometrics_privacy")
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
                onViewMap = { id ->
                    if (navController.currentDestination?.route == "profile/{studentId}") {
                        navController.navigate("student_map/$id")
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

        composable("templates") {
            TemplateManagerScreen(
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable("map_archives") {
            MapArchivesScreen(
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

        // 5. GLOBAL MAP SCREEN ROUTE (Fully Guarded against double-clicks!)
        composable("global_map") {
            StudentMapScreen(
                studentId = -1,
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                }
            )
        }

        // 6. SINGLE STUDENT MAP SCREEN ROUTE (Fully Guarded against double-clicks!)
        composable(
            route = "student_map/{studentId}",
            arguments = listOf(navArgument("studentId") { type = NavType.IntType })
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getInt("studentId") ?: -1
            StudentMapScreen(
                studentId = studentId,
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

        composable("biometrics_privacy") {
            BiometricsPrivacyScreen(
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