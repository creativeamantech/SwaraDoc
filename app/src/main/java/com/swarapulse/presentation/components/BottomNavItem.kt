package com.swarapulse.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(val route: String, val icon: ImageVector, val label: String) {
    object Dashboard : BottomNavItem("dashboard", Icons.Default.Home, "Dashboard")
    object Patients : BottomNavItem("patients", Icons.Default.People, "Patients")
    object Appointments : BottomNavItem("appointments", Icons.Default.CalendarToday, "Appointments")
    object Analytics : BottomNavItem("analytics", Icons.Default.BarChart, "Analytics")
    object Settings : BottomNavItem("settings", Icons.Default.Settings, "Settings")
}
