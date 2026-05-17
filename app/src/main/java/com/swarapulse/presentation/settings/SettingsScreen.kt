package com.swarapulse.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsSection("Profile") {
                    SettingsItem(icon = Icons.Default.Person, title = "Edit Profile") {}
                }
            }
            item {
                SettingsSection("Security") {
                    SettingsItem(icon = Icons.Default.Lock, title = "Change PIN") {}
                    SettingsItem(icon = Icons.Default.Fingerprint, title = "Toggle Biometric") {}
                }
            }
            item {
                SettingsSection("Backup & Restore") {
                    SettingsItem(icon = Icons.Default.CloudUpload, title = "Backup to JSON") {}
                    SettingsItem(icon = Icons.Default.TableView, title = "Backup to Excel") {}
                    SettingsItem(icon = Icons.Default.CloudDownload, title = "Restore from JSON") {}
                }
            }
            item {
                SettingsSection("Appearance") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
                            Text("Dark Mode")
                        }
                        Switch(
                            checked = uiState.isDarkMode,
                            onCheckedChange = { viewModel.toggleDarkMode(it) }
                        )
                    }
                }
            }
            item {
                SettingsSection("Notifications") {
                    SettingsItem(icon = Icons.Default.Notifications, title = "Notification Preferences") {}
                }
            }
            item {
                SettingsSection("Data") {
                    SettingsItem(icon = Icons.Default.DeleteSweep, title = "Clear Drafts") {}
                }
            }
            item {
                SettingsSection("About") {
                    SettingsItem(icon = Icons.Default.Info, title = "App Version 1.0") {}
                }
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
        Divider(modifier = Modifier.padding(top = 16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsItem(icon: ImageVector, title: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
