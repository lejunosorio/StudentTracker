package dev.soloistdev.studenttracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.soloistdev.studenttracker.security.PdfGeneratorHelper

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current

    val securityViewModel: SecurityViewModel = viewModel()
    val isUnlocked by securityViewModel.isUnlocked.collectAsState()

    LaunchedEffect(isUnlocked) {
        if (isUnlocked) {
            navController.navigate(ScreenRoute.VIEW_ALL) {
                popUpTo(ScreenRoute.SECURITY_GATE) { inclusive = true }
            }
        } else {
            navController.navigate(ScreenRoute.SECURITY_GATE) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = ScreenRoute.SECURITY_GATE) {

        composable(ScreenRoute.SECURITY_GATE) {
            SecurityGateScreen(
                onUnlockSuccess = {
                    navController.navigate(ScreenRoute.VIEW_ALL) {
                        popUpTo(ScreenRoute.SECURITY_GATE) { inclusive = true }
                    }
                },
                viewModel = securityViewModel
            )
        }

        composable(ScreenRoute.VIEW_ALL) {
            ViewAllScreen(
                onAddStudent = { id ->
                    if (navController.currentDestination?.route == ScreenRoute.VIEW_ALL) {
                        navController.navigate("add_edit/$id")
                    }
                },
                onStudentClick = { id ->
                    if (navController.currentDestination?.route == ScreenRoute.VIEW_ALL) {
                        navController.navigate("profile/$id")
                    }
                },
                onOpenTemplates = {
                    if (navController.currentDestination?.route == ScreenRoute.VIEW_ALL) {
                        navController.navigate(ScreenRoute.TEMPLATES)
                    }
                },
                onOpenMap = {
                    if (navController.currentDestination?.route == ScreenRoute.VIEW_ALL) {
                        navController.navigate(ScreenRoute.SAVED_FILTERS)
                    }
                },
                onOpenRecycleBin = {
                    if (navController.currentDestination?.route == ScreenRoute.VIEW_ALL) {
                        navController.navigate(ScreenRoute.RECYCLE_BIN)
                    }
                },
                onOpenSync = {
                    if (navController.currentDestination?.route == ScreenRoute.VIEW_ALL) {
                        navController.navigate(ScreenRoute.SYNC)
                    }
                },
                onOpenSettings = {
                    if (navController.currentDestination?.route == ScreenRoute.VIEW_ALL) {
                        navController.navigate(ScreenRoute.APP_SETTINGS)
                    }
                },
                onOpenBiometrics = {
                    if (navController.currentDestination?.route == ScreenRoute.VIEW_ALL) {
                        navController.navigate(ScreenRoute.BIOMETRICS_PRIVACY)
                    }
                },
                onOpenAttendance = {
                    if (navController.currentDestination?.route == ScreenRoute.VIEW_ALL) {
                        navController.navigate(ScreenRoute.ATTENDANCE)
                    }
                },
                onOpenAttendanceWithArgs = { recordId, dateMillis ->
                    if (navController.currentDestination?.route == ScreenRoute.VIEW_ALL) {
                        navController.navigate("attendance?recordId=$recordId&dateMillis=$dateMillis")
                    }
                }
            )
        }

        composable(
            route = ScreenRoute.PROFILE,
            arguments = listOf(navArgument("studentId") { type = NavType.IntType })
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getInt("studentId") ?: -1

            // Retrieve directory ViewModel inside the graph node to safely utilize its viewModelScope [1]
            val listViewModel: StudentListViewModel = viewModel()

            StudentProfileScreen(
                studentId = studentId,
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                },
                onEdit = { id ->
                    if (navController.currentDestination?.route == ScreenRoute.PROFILE) {
                        navController.navigate("add_edit/$id") {
                            popUpTo("profile/$studentId") { inclusive = true }
                        }
                    }
                },
                onSharePdf = { studentEntity ->
                    PdfGeneratorHelper.generateAndShareStudentPdf(context, studentEntity)
                },
                onDeleteStudent = { id ->
                    // Resolved: Delegate deletion safely to listViewModel's lifecycle-bound scope [1]
                    listViewModel.softDeleteStudent(id)
                    navController.navigate(ScreenRoute.VIEW_ALL) {
                        popUpTo(ScreenRoute.VIEW_ALL) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = ScreenRoute.ADD_EDIT,
            arguments = listOf(navArgument("studentId") { type = NavType.IntType })
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getInt("studentId") ?: -1
            AddEditStudentScreen(
                studentId = studentId,
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(ScreenRoute.VIEW_ALL) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(ScreenRoute.TEMPLATES) {
            TemplateManagerScreen(
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable(ScreenRoute.RECYCLE_BIN) {
            RecycleBinScreen(
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable(ScreenRoute.SAVED_FILTERS) {
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

        composable(ScreenRoute.BIOMETRICS_PRIVACY) {
            BiometricsPrivacyScreen(
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable(ScreenRoute.SYNC) {
            SyncScreen(
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable(
            route = ScreenRoute.ATTENDANCE,
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
                    navController.navigate(ScreenRoute.SAVED_FILTERS) {
                        popUpTo(ScreenRoute.ATTENDANCE) { inclusive = true }
                    }
                }
            )
        }

        composable(ScreenRoute.APP_SETTINGS) {
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

object ScreenRoute {
    const val SECURITY_GATE = "security_gate"
    const val VIEW_ALL = "view_all"
    const val PROFILE = "profile/{studentId}"
    const val ADD_EDIT = "add_edit/{studentId}"
    const val TEMPLATES = "templates"
    const val RECYCLE_BIN = "recycle_bin"
    const val SAVED_FILTERS = "saved_filters"
    const val BIOMETRICS_PRIVACY = "biometrics_privacy"
    const val SYNC = "sync"
    const val ATTENDANCE = "attendance?recordId={recordId}&dateMillis={dateMillis}"
    const val APP_SETTINGS = "app_settings"
}