package com.swarapulse

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.swarapulse.presentation.MainScreen
import com.swarapulse.presentation.analytics.AnalyticsScreen
import com.swarapulse.presentation.appointments.AppointmentsScreen
import com.swarapulse.presentation.auth.AuthScreen
import com.swarapulse.presentation.dashboard.DashboardScreen
import com.swarapulse.presentation.patients.AddPatientScreen
import com.swarapulse.presentation.patients.PatientDetailScreen
import com.swarapulse.presentation.patients.PatientListScreen
import com.swarapulse.presentation.settings.SettingsScreen
import com.swarapulse.presentation.visit.VisitFormScreen

@Composable
fun SwaraPulseNavigation() {
    val rootNavController = rememberNavController()

    NavHost(navController = rootNavController, startDestination = "auth") {
        composable("auth") {
            AuthScreen(onNavigateToDashboard = {
                rootNavController.navigate("main") {
                    popUpTo("auth") { inclusive = true }
                    launchSingleTop = true
                }
            })
        }

        composable("main") {
            MainScreen(
                rootNavController = rootNavController,
                onNavigateToVisitForm = { patientId ->
                    rootNavController.navigate("visit_form/$patientId")
                }
            )
        }

        composable(
            route = "visit_form/{patientId}",
            arguments = listOf(navArgument("patientId") { type = NavType.LongType })
        ) {
            VisitFormScreen(onNavigateBack = { rootNavController.popBackStack() })
        }
    }
}

@Composable
fun MainNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onNavigateToVisitForm: (Long) -> Unit
) {
    NavHost(navController = navController, startDestination = "dashboard", modifier = modifier) {
        composable("dashboard") {
            DashboardScreen(
                onNavigateToPatientList = { navController.navigate("patients") },
                onNavigateToNewVisit = { onNavigateToVisitForm(-1L) },
                onNavigateToAppointments = { navController.navigate("appointments") }
            )
        }

        composable("patients") {
            PatientListScreen(
                onNavigateToPatientDetail = { id -> navController.navigate("patient_detail/$id") },
                onNavigateToNewPatient = { navController.navigate("patient_new") }
            )
        }

        composable("patient_new") {
            AddPatientScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = "patient_detail/{patientId}",
            arguments = listOf(navArgument("patientId") { type = NavType.LongType })
        ) {
            PatientDetailScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable("appointments") {
            AppointmentsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable("analytics") {
            AnalyticsScreen()
        }

        composable("settings") {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
