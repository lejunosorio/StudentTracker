package dev.soloistdev.studenttracker.ui

import dev.soloistdev.studenttracker.data.StudentEntity
import dev.soloistdev.studenttracker.security.PdfGeneratorHelper
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current

    val securityViewModel: SecurityViewModel = viewModel()
    val isUnlocked by securityViewModel.isUnlocked.collectAsState()
    val scope = rememberCoroutineScope()

    // Shared Query State ViewModel persisted across the Query sub-graph context
    val queryViewModel: QueryViewModel = viewModel()

    // Standardized session gatekeeper to run ONLY on unlock transitions to prevent navigation high-jacks
    LaunchedEffect(isUnlocked) {
        val initialRoute = navController.currentDestination?.route
        if (initialRoute?.startsWith("import_student") == true) {
            return@LaunchedEffect
        }

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
                    if (navController.currentDestination?.route == "view_all") {
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
                onOpenSettings = {
                    if (navController.currentDestination?.route == "view_all") {
                        navController.navigate(ScreenRoute.APP_SETTINGS)
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
                },
                onOpenGradebook = {
                    if (navController.currentDestination?.route == ScreenRoute.VIEW_ALL) {
                        navController.navigate(ScreenRoute.GRADEBOOK)
                    }
                },
                onOpenClassrooms = {
                    if (navController.currentDestination?.route == ScreenRoute.VIEW_ALL) {
                        navController.navigate(ScreenRoute.CLASSROOMS)
                    }
                },
                onOpenQueryBuilder = {
                    if (navController.currentDestination?.route == ScreenRoute.VIEW_ALL) {
                        navController.navigate(ScreenRoute.QUERY_BUILDER)
                    }
                },
                onOpenSeatingChart = { className ->
                    if (navController.currentDestination?.route == ScreenRoute.VIEW_ALL) {
                        navController.navigate("seating_chart/$className")
                    }
                }
            )
        }

        composable(
            route = ScreenRoute.PROFILE,
            arguments = listOf(navArgument("studentId") { type = NavType.IntType })
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getInt("studentId") ?: -1
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
                    scope.launch {
                        PdfGeneratorHelper.generateAndShareStudentPdf(context, studentEntity)
                    }
                },
                onDeleteStudent = { id ->
                    scope.launch {
                        listViewModel.softDeleteStudent(id)
                        navController.navigate(ScreenRoute.VIEW_ALL) {
                            popUpTo(ScreenRoute.VIEW_ALL) { inclusive = true }
                        }
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
                },
                onNavigateToTemplates = {
                    navController.navigate(ScreenRoute.MESSAGE_TEMPLATES)
                }
            )
        }

        composable(ScreenRoute.MESSAGE_TEMPLATES) {
            MessageTemplatesScreen(
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
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
                },
                onNavigateToBiometrics = {
                    if (navController.currentDestination?.route == ScreenRoute.APP_SETTINGS) {
                        navController.navigate(ScreenRoute.BIOMETRICS_PRIVACY)
                    }
                }
            )
        }

        composable(
            route = ScreenRoute.IMPORT_STUDENT,
            arguments = listOf(
                navArgument("id") { type = NavType.IntType; defaultValue = -1 },
                navArgument("first") { type = NavType.StringType; defaultValue = "" },
                navArgument("last") { type = NavType.StringType; defaultValue = "" },
                navArgument("gender") { type = NavType.StringType; defaultValue = "F" },
                navArgument("birthday") { type = NavType.LongType; defaultValue = 0L },
                navArgument("address") { type = NavType.StringType; defaultValue = "" },
                navArgument("contact") { type = NavType.StringType; defaultValue = "" },
                navArgument("guardians") { type = NavType.StringType; defaultValue = "[]" },
                navArgument("custom") { type = NavType.StringType; defaultValue = "{}" },
                navArgument("class") { type = NavType.StringType; defaultValue = "" },
                navArgument("seatingX") { type = NavType.FloatType; defaultValue = -1f },
                navArgument("seatingY") { type = NavType.FloatType; defaultValue = -1f }
            ),
            deepLinks = listOf(
                androidx.navigation.navDeepLink {
                    uriPattern = "studenttracker://student?id={id}&first={first}&last={last}&gender={gender}&birthday={birthday}&address={address}&contact={contact}&guardians={guardians}&custom={custom}&class={class}&seatingX={seatingX}&seatingY={seatingY}"
                }
            )
        ) { backStackEntry ->
            val first = backStackEntry.arguments?.getString("first") ?: ""
            val last = backStackEntry.arguments?.getString("last") ?: ""
            val gender = backStackEntry.arguments?.getString("gender") ?: "F"
            val birthday = backStackEntry.arguments?.getLong("birthday") ?: 0L
            val address = backStackEntry.arguments?.getString("address") ?: ""
            val contact = backStackEntry.arguments?.getString("contact") ?: ""
            val guardians = backStackEntry.arguments?.getString("guardians") ?: "[]"
            val custom = backStackEntry.arguments?.getString("custom") ?: "{}"
            val className = backStackEntry.arguments?.getString("class") ?: ""
            val seatingX = backStackEntry.arguments?.getFloat("seatingX") ?: -1f
            val seatingY = backStackEntry.arguments?.getFloat("seatingY") ?: -1f

            StudentImportScreen(
                tempStudent = StudentEntity(
                    firstName = first,
                    lastName = last,
                    gender = gender,
                    birthday = birthday,
                    address = address,
                    contactNumber = contact,
                    guardiansJson = guardians,
                    customDataJson = custom,
                    className = className,
                    seatingX = seatingX,
                    seatingY = seatingY
                ),
                onDismiss = {
                    navController.navigate(ScreenRoute.VIEW_ALL) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToEdit = { studentId ->
                    navController.navigate("add_edit/$studentId") {
                        popUpTo(ScreenRoute.VIEW_ALL) { inclusive = true }
                    }
                }
            )
        }

        composable(ScreenRoute.GRADEBOOK) {
            GradebookScreen(
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable(ScreenRoute.CLASSROOMS) {
            ClassroomsScreen(
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable(ScreenRoute.QUERY_BUILDER) {
            QueryBuilderScreen(
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                },
                onShowResults = {
                    navController.navigate(ScreenRoute.QUERY_RESULTS)
                },
                viewModel = queryViewModel
            )
        }

        composable(ScreenRoute.QUERY_RESULTS) {
            QueryResultsScreen(
                onBackToBuilder = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                },
                onStudentClick = { id ->
                    navController.navigate("profile/$id")
                },
                onOpenAttendanceWithArgs = { recordId, dateMillis ->
                    navController.navigate("attendance?recordId=$recordId&dateMillis=$dateMillis") {
                        popUpTo(ScreenRoute.QUERY_RESULTS) { inclusive = true }
                    }
                },
                viewModel = queryViewModel
            )
        }

        composable(
            route = ScreenRoute.SEATING_CHART,
            arguments = listOf(navArgument("className") { type = NavType.StringType })
        ) { backStackEntry ->
            val className = backStackEntry.arguments?.getString("className") ?: ""
            SeatingChartScreen(
                className = className,
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
    const val ATTENDANCE = "attendance?recordId={recordId}&dateMillis={dateMillis}"
    const val APP_SETTINGS = "app_settings"
    const val IMPORT_STUDENT = "import_student?id={id}&first={first}&last={last}&gender={gender}&birthday={birthday}&address={address}&contact={contact}&guardians={guardians}&custom={custom}&class={class}"

    const val MESSAGE_TEMPLATES = "message_templates"

    const val GRADEBOOK = "gradebook"

    const val CLASSROOMS = "classrooms"

    const val QUERY_BUILDER = "query_builder"
    const val QUERY_RESULTS = "query_results"

    const val SEATING_CHART = "seating_chart/{className}" // Declares the Seating Chart endpoint identifier
}