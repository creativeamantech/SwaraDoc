package com.swarapulse.presentation.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.swarapulse.presentation.components.CountUpText
import com.swarapulse.data.db.entity.Appointment
import com.swarapulse.data.db.entity.Visit
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToPatientList: () -> Unit,
    onNavigateToNewVisit: () -> Unit,
    onNavigateToAppointments: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SwaraPulse") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToNewVisit) {
                Icon(Icons.Default.Add, contentDescription = "New Visit")
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item { GreetingCard() }
                item { StatsStrip(uiState.patientCount, uiState.visitCount, uiState.monthVisits) }
                item { TodaysAppointmentsRow(uiState.todaysAppointments, onNavigateToAppointments) }
                item { FollowupTimeline(uiState.upcomingFollowups) }
                item { RecentActivityList(uiState.recentVisits) }
            }
        }
    }
}

@Composable
fun GreetingCard() {
    val currentHour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    val greeting = when (currentHour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        else -> "Good Evening"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = greeting, style = MaterialTheme.typography.headlineSmall)
            Text(text = "Doctor", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun StatsStrip(patientCount: Int, visitCount: Int, monthVisits: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StatItem("Patients", patientCount)
        StatItem("Visits", visitCount)
        StatItem("This Month", monthVisits)
    }
}

@Composable
fun StatItem(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CountUpText(
            targetValue = value,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun TodaysAppointmentsRow(appointments: List<Appointment>, onNavigateToAppointments: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Today's Appointments", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onNavigateToAppointments) {
                Text("See All")
            }
        }
        if (appointments.isEmpty()) {
            Text("No appointments today.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(appointments) { appointment ->
                    Card(modifier = Modifier.width(200.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "Patient ID: ${appointment.patientId}", style = MaterialTheme.typography.bodyLarge)
                            Text(text = appointment.purpose ?: "Checkup", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FollowupTimeline(followups: List<Visit>) {
    Column {
        Text(text = "Upcoming Followups", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        if (followups.isEmpty()) {
            Text("No followups scheduled.", style = MaterialTheme.typography.bodyMedium)
        } else {
            followups.forEach { visit ->
                Text(text = "Visit ID: ${visit.id} - Patient ID: ${visit.patientId}", modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
fun RecentActivityList(recentVisits: List<Visit>) {
    Column {
        Text(text = "Recent Activity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        if (recentVisits.isEmpty()) {
             Text("No recent activity.", style = MaterialTheme.typography.bodyMedium)
        } else {
            recentVisits.forEach { visit ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                     Text(text = "Visit ID: ${visit.id} - Patient ID: ${visit.patientId}", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
