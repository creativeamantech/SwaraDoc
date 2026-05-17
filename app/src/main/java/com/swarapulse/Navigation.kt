package com.swarapulse

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.swarapulse.presentation.analytics.AnalyticsScreen
import com.swarapulse.presentation.appointments.AppointmentsScreen
import com.swarapulse.presentation.auth.AuthScreen
import com.swarapulse.presentation.dashboard.DashboardScreen
import com.swarapulse.presentation.patients.PatientDetailScreen
import com.swarapulse.presentation.patients.PatientListScreen
import com.swarapulse.presentation.settings.SettingsScreen
import com.swarapulse.presentation.visit.VisitFormScreen

@Composable
fun SwaraPulseNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "auth") {
        composable("auth") {
            AuthScreen(onNavigateToDashboard = {
                navController.navigate("dashboard") {
                    popUpTo("auth") { inclusive = true }
                }
            })
        }

        composable("dashboard") {
            DashboardScreen(
                onNavigateToPatientList = { navController.navigate("patients") },
                onNavigateToNewVisit = { navController.navigate("visit_form/-1") },
                onNavigateToAppointments = { navController.navigate("appointments") }
            )
        }

        composable("patients") {
            PatientListScreen(
                onNavigateToPatientDetail = { id -> navController.navigate("patient_detail/$id") },
                onNavigateToNewPatient = { /* TODO Add new patient dialog/screen */ }
            )
        }

        composable(
            route = "patient_detail/{patientId}",
            arguments = listOf(navArgument("patientId") { type = NavType.LongType })
        ) {
            PatientDetailScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = "visit_form/{patientId}",
            arguments = listOf(navArgument("patientId") { type = NavType.LongType })
        ) {
            VisitFormScreen(onNavigateBack = { navController.popBackStack() })
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
