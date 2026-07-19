package dev.soloistdev.studenttracker.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

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
                onOpenMapArchives = {
                    if (navController.currentDestination?.route == "view_all") {
                        navController.navigate("map_archives")
                    }
                },
                // NEW: Route to global map!
                onOpenMap = {
                    if (navController.currentDestination?.route == "view_all") {
                        navController.navigate("global_map")
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
                // NEW: Route from profile to exact coordinates!
                onViewMap = { id ->
                    if (navController.currentDestination?.route == "profile/{studentId}") {
                        navController.navigate("student_map/$id")
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

        // NEW: Global Map Screen Route
        composable("global_map") {
            StudentMapScreen(
                studentId = -1,
                onBack = { navController.popBackStack() }
            )
        }

        // NEW: Single Student Focused Map Screen Route
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