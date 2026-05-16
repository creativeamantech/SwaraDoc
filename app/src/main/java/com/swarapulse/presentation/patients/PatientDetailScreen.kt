package com.swarapulse.presentation.patients

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.swarapulse.data.db.entity.Patient
import com.swarapulse.data.db.entity.Visit
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PatientDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: PatientDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val pagerState = rememberPagerState(pageCount = { 4 })
    val coroutineScope = rememberCoroutineScope()
    val tabs = listOf("Overview", "Visits", "Trends", "Media")

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(uiState.patient?.name ?: "Patient Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Export PDF */ }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                    }
                    IconButton(onClick = { /* Share */ }) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.patient != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                PatientHeader(uiState.patient!!)

                TabRow(selectedTabIndex = pagerState.currentPage) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = { Text(title) }
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    when (page) {
                        0 -> OverviewTab(uiState.patient!!)
                        1 -> VisitsTab(uiState.visits)
                        2 -> TrendsTab()
                        3 -> MediaTab()
                    }
                }
            }
        } else {
             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Patient not found.")
            }
        }
    }
}

@Composable
fun PatientHeader(patient: Patient) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Age: ${patient.age} | Gender: ${patient.gender.name}", style = MaterialTheme.typography.bodyLarge)
            Text("Mobile: ${patient.mobile}", style = MaterialTheme.typography.bodyMedium)
            if (patient.bloodGroup != null) {
                Text("Blood Group: ${patient.bloodGroup}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun OverviewTab(patient: Patient) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Text("Medical History", style = MaterialTheme.typography.titleMedium)
            Text(patient.medicalHistory ?: "None recorded", style = MaterialTheme.typography.bodyMedium)
        }
        // Add more overview details here
    }
}

@Composable
fun VisitsTab(visits: List<Visit>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(visits) { visit ->
            // Replace with SwipeableVisitCard
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Visit on ${visit.dateTime}", style = MaterialTheme.typography.titleSmall)
                    Text("Complaint: ${visit.chiefComplaint}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun TrendsTab() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Trends Charts Coming Soon")
    }
}

@Composable
fun MediaTab() {
     Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Media Gallery Coming Soon")
    }
}
