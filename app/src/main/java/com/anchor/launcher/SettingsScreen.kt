package com.anchor.launcher

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AnchorViewModel, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SETTINGS", fontSize = 14.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("BACK", color = MaterialTheme.colorScheme.secondary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(24.dp)) {
            Text("DENSITY", fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(16.dp))
            DensityMode.values().forEach { mode ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(mode.name, color = if(viewModel.densityMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                    RadioButton(
                        selected = viewModel.densityMode == mode,
                        onClick = { viewModel.setDensity(mode) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            Text("ABOUT", fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("ANCHOR LAUNCHER", fontWeight = FontWeight.Bold)
            Text("Version 1.0.0 · OLED Edition", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        }
    }
}
