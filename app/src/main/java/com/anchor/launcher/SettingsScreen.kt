package com.anchor.launcher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AnchorViewModel, onBack: () -> Unit) {
    var showAddSpaceDialog by remember { mutableStateOf(false) }
    var newSpaceName by remember { mutableStateOf("") }
    var showFrictionDialog by remember { mutableStateOf<AppInfo?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Memoized app filtering to prevent redundant collection allocations on every recomposition
    val installedApps = viewModel.installedApps
    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    // Isolated slider state to prevent full-screen recomposition cascades during rapid dragging
    var tempFontSize by remember { mutableFloatStateOf(viewModel.fontSizeMultiplier) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontSize = 14.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back), color = MaterialTheme.colorScheme.secondary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            // REFLECTION
            item {
                Text(stringResource(R.string.daily_intention_title), fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = viewModel.oneThingReflection,
                    onValueChange = { viewModel.setOneThing(it) },
                    placeholder = { Text(stringResource(R.string.one_thing_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            // SPACES MANAGEMENT
            item {
                Text(stringResource(R.string.spaces_title), fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(16.dp))
                viewModel.spaces.forEach { space ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(space.name, fontWeight = if (viewModel.currentSpace.id == space.id) FontWeight.Bold else FontWeight.Normal)
                        IconButton(onClick = { viewModel.deleteSpace(space.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_content_description), tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                TextButton(onClick = { showAddSpaceDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.add_new_space))
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // FRICTION RULES
            item {
                Text(stringResource(R.string.intentional_friction_title), fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_friction_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            items(filteredApps, key = { it.packageName }) { app ->
                val friction = viewModel.appFrictionLevels[app.packageName] ?: "OFF"
                if (friction != "OFF" || searchQuery.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(app.label, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showFrictionDialog = app }) {
                            Text(stringResource(frictionLevelLabelRes(friction)), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))

                // DENSITY
                Text(stringResource(R.string.density_title), fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(16.dp))
                DensityMode.values().forEach { mode ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(densityModeLabelRes(mode)), color = if(viewModel.densityMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                        RadioButton(selected = viewModel.densityMode == mode, onClick = { viewModel.setDensity(mode) })
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // TYPOGRAPHY
                Text(stringResource(R.string.typography_title), fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(16.dp))

                Text(stringResource(R.string.font_size_label, (tempFontSize * 100).toInt()), fontSize = 14.sp)
                Slider(
                    value = tempFontSize,
                    onValueChange = { tempFontSize = it },
                    onValueChangeFinished = {
                        viewModel.fontSizeMultiplier = tempFontSize
                        viewModel.updateSetting("font_size", tempFontSize.toString())
                    },
                    valueRange = 0.8f..1.5f
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.bold_text), fontSize = 14.sp)
                    Switch(
                        checked = viewModel.isBoldEnabled,
                        onCheckedChange = {
                            viewModel.isBoldEnabled = it
                            viewModel.updateSetting("bold_enabled", it.toString())
                        }
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))
                Text(stringResource(R.string.about_title), fontSize = 11.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.app_full_name), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.app_version), fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(48.dp))
            }
        }

        if (showAddSpaceDialog) {
            AlertDialog(
                onDismissRequest = { showAddSpaceDialog = false },
                title = { Text(stringResource(R.string.new_space_dialog_title)) },
                text = {
                    OutlinedTextField(
                        value = newSpaceName,
                        onValueChange = { newSpaceName = it },
                        label = { Text(stringResource(R.string.space_name_label)) },
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
                    }) { Text(stringResource(R.string.create)) }
                },
                dismissButton = {
                    TextButton(onClick = { showAddSpaceDialog = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        showFrictionDialog?.let { app ->
            AlertDialog(
                onDismissRequest = { showFrictionDialog = null },
                title = { Text(stringResource(R.string.friction_dialog_title, app.label)) },
                text = {
                    Column {
                        listOf("OFF", "LIGHT", "INTENT", "TIMER", "BLOCK").forEach { level ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                RadioButton(selected = (viewModel.appFrictionLevels[app.packageName] ?: "OFF") == level, onClick = { viewModel.setFrictionLevel(app.packageName, level) })
                                Text(stringResource(frictionLevelLabelRes(level)))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showFrictionDialog = null }) { Text(stringResource(R.string.done)) } }
            )
        }
    }
}
