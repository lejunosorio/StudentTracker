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
import kotlinx.coroutines.launch

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // SPRINT 9 INSTANTIATION: Binds repository safely using Compose Context & Memory Cache
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
                onOpenMap = {
                    if (navController.currentDestination?.route == "view_all") {
                        navController.navigate("global_map")
                    }
                },
                onOpenRecycleBin = {
                    if (navController.currentDestination?.route == "view_all") {
                        navController.navigate("recycle_bin")
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
                onViewMap = { id ->
                    if (navController.currentDestination?.route == "profile/{studentId}") {
                        navController.navigate("student_map/$id")
                    }
                },
                onDeleteStudent = { id ->
                    // Soft-deletes the student and returns back to the directory cleanly
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

        composable("global_map") {
            StudentMapScreen(
                studentId = -1,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "student_map/{studentId}",
            arguments = listOf(navArgument("studentId") { type = NavType.IntType })
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getInt("studentId") ?: -1
            StudentMapScreen(
                studentId = studentId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}