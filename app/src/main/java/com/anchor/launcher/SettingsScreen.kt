package com.anchor.launcher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AnchorViewModel, onBack: () -> Unit) {
    var showAddSpaceDialog by remember { mutableStateOf(false) }
    var newSpaceName by remember { mutableStateOf("") }
    var showFrictionDialog by remember { mutableStateOf<AppInfo?>(null) }
    val context = LocalContext.current
    val allApps = remember { viewModel.getInstalledApps(context) }

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
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // REFLECTION
            Text("DAILY INTENTION", fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = viewModel.oneThingReflection,
                onValueChange = { viewModel.setOneThing(it) },
                placeholder = { Text("What matters today?") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            // SPACES MANAGEMENT
            Text("SPACES", fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(16.dp))
            viewModel.spaces.forEach { space ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(space.name, fontWeight = if (viewModel.currentSpace.id == space.id) FontWeight.Bold else FontWeight.Normal)
                    IconButton(onClick = { viewModel.deleteSpace(space.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            TextButton(onClick = { showAddSpaceDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ADD NEW SPACE")
            }

            Spacer(modifier = Modifier.height(32.dp))

            // FRICTION RULES
            Text("INTENTIONAL FRICTION", fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(16.dp))
            val protectedApps = allApps.filter { viewModel.appFrictionLevels.containsKey(it.packageName) && viewModel.appFrictionLevels[it.packageName] != "OFF" }
            protectedApps.forEach { app ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(app.label)
                    TextButton(onClick = { showFrictionDialog = app }) {
                        Text(viewModel.appFrictionLevels[app.packageName] ?: "OFF", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            TextButton(onClick = { /* Open full app list to add friction */ }) {
                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ADD APP FRICTION")
            }

            Spacer(modifier = Modifier.height(32.dp))

            // DENSITY
            Text("DENSITY", fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(16.dp))
            DensityMode.values().forEach { mode ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(mode.name, color = if(viewModel.densityMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                    RadioButton(selected = viewModel.densityMode == mode, onClick = { viewModel.setDensity(mode) })
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            // TYPOGRAPHY
            Text("TYPOGRAPHY", fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Font Size: ${(viewModel.fontSizeMultiplier * 100).toInt()}%", fontSize = 14.sp)
            Slider(
                value = viewModel.fontSizeMultiplier,
                onValueChange = { 
                    viewModel.fontSizeMultiplier = it
                    viewModel.updateSetting("font_size", it.toString())
                },
                valueRange = 0.8f..1.5f
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Bold Text", fontSize = 14.sp)
                Switch(
                    checked = viewModel.isBoldEnabled,
                    onCheckedChange = { 
                        viewModel.isBoldEnabled = it
                        viewModel.updateSetting("bold_enabled", it.toString())
                    }
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
            Text("ABOUT", fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("ANCHOR LAUNCHER", fontWeight = FontWeight.Bold)
            Text("Version 1.0.0 · Final MVP", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
        }

        if (showAddSpaceDialog) {
            AlertDialog(
                onDismissRequest = { showAddSpaceDialog = false },
                title = { Text("New Space") },
                text = {
                    OutlinedTextField(
                        value = newSpaceName,
                        onValueChange = { newSpaceName = it },
                        label = { Text("Space Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newSpaceName.isNotBlank()) {
                            viewModel.addSpace(newSpaceName)
                            newSpaceName = ""
                            showAddSpaceDialog = false
                        }
                    }) { Text("CREATE") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddSpaceDialog = false }) { Text("CANCEL") }
                }
            )
        }

        showFrictionDialog?.let { app ->
            AlertDialog(
                onDismissRequest = { showFrictionDialog = null },
                title = { Text("Friction: ${app.label}") },
                text = {
                    Column {
                        listOf("OFF", "LIGHT", "INTENT", "TIMER", "BLOCK").forEach { level ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                RadioButton(selected = (viewModel.appFrictionLevels[app.packageName] ?: "OFF") == level, onClick = { viewModel.setFrictionLevel(app.packageName, level) })
                                Text(level)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showFrictionDialog = null }) { Text("DONE") } }
            )
        }
    }
}
