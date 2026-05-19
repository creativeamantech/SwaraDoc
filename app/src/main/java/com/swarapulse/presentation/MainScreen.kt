package com.swarapulse.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.swarapulse.MainNavGraph
import com.swarapulse.presentation.components.BottomNavItem

@Composable
fun MainScreen(
    onNavigateToVisitForm: (Long) -> Unit,
    rootNavController: NavHostController // To pop auth if needed, or we just rely on local state
) {
    val bottomNavController = rememberNavController()

    Scaffold(
        bottomBar = {
            val navBackStackEntry by bottomNavController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            // Hide bottom bar on detail screens to act as full screen
            val bottomBarRoutes = listOf(
                BottomNavItem.Dashboard.route,
                BottomNavItem.Patients.route,
                BottomNavItem.Appointments.route,
                BottomNavItem.Analytics.route,
                BottomNavItem.Settings.route
            )

            if (currentRoute in bottomBarRoutes) {
                NavigationBar {
                    val items = listOf(
                        BottomNavItem.Dashboard,
                        BottomNavItem.Patients,
                        BottomNavItem.Appointments,
                        BottomNavItem.Analytics,
                        BottomNavItem.Settings
                    )
                    items.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = currentRoute == item.route,
                            onClick = {
                                bottomNavController.navigate(item.route) {
                                    popUpTo(bottomNavController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        MainNavGraph(
            navController = bottomNavController,
            modifier = Modifier.padding(paddingValues),
            onNavigateToVisitForm = onNavigateToVisitForm
        )
    }
}
