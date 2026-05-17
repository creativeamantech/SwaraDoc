package com.swarapulse.presentation.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics") },
                actions = {
                    IconButton(onClick = { /* Export CSV */ }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export CSV")
                    }
                }
            )
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
                item {
                    TimeFilterRow(
                        selectedFilter = uiState.selectedFilter,
                        onFilterSelected = { viewModel.setFilter(it) }
                    )
                }

                item {
                    CorrelationsCard(
                        nadiAlignment = uiState.nadiAlignmentPercentage,
                        elementAlignment = uiState.elementAlignmentPercentage
                    )
                }

                item {
                    TopComplaintsList(complaints = uiState.topComplaints)
                }

                item {
                    Text("Distributions Chart Coming Soon", style = MaterialTheme.typography.titleMedium)
                    // Vico BarChart will be here
                }

                item {
                    Text("Data Table Coming Soon", style = MaterialTheme.typography.titleMedium)
                    // Scrollable table
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeFilterRow(selectedFilter: TimeFilter, onFilterSelected: (TimeFilter) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(TimeFilter.values()) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.name) }
            )
        }
    }
}

@Composable
fun CorrelationsCard(nadiAlignment: Float, elementAlignment: Float) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Correlations", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Nadi Alignment: ${"%.1f".format(nadiAlignment)}%")
            Text("Element Alignment: ${"%.1f".format(elementAlignment)}%")
        }
    }
}

@Composable
fun TopComplaintsList(complaints: List<Pair<String, Int>>) {
    Column {
        Text("Top Complaints", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        if (complaints.isEmpty()) {
            Text("No complaints data.")
        } else {
            val maxCount = complaints.maxOfOrNull { it.second } ?: 1
            complaints.forEach { (word, count) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(word, modifier = Modifier.weight(1f))
                    LinearProgressIndicator(
                        progress = { count.toFloat() / maxCount.toFloat() },
                        modifier = Modifier.weight(2f).height(8.dp)
                    )
                    Text(count.toString(), modifier = Modifier.width(40.dp).padding(start = 8.dp))
                }
            }
        }
    }
}
