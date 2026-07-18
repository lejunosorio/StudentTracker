package dev.soloistdev.studenttracker.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // Enforce Security Gate as the starting point for every app launch
    NavHost(navController = navController, startDestination = "security_gate") {
        composable("security_gate") {
            SecurityGateScreen(
                onUnlockSuccess = {
                    // Navigate to View All and clear security gate from backstack history
                    navController.navigate("view_all") {
                        popUpTo("security_gate") { inclusive = true }
                    }
                }
            )
        }
        composable("view_all") {
            ViewAllScreen()
        }
        composable("settings") {
            // Settings Screen
        }
    }
}