package com.anchor.launcher

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AnchorViewModel, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") }, navigationIcon = {
                TextButton(onClick = onBack) { Text("Back") }
            })
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(24.dp)) {
            Text("Information Density", style = MaterialTheme.typography.titleMedium)
            DensityMode.values().forEach { mode ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                    RadioButton(selected = viewModel.densityMode == mode, onClick = { viewModel.densityMode = mode })
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            Text("Current Space: ${viewModel.currentSpace.name}")
            Text("Version 1.0.0 (MVP)", style = MaterialTheme.typography.bodySmall)
        }
    }
}
