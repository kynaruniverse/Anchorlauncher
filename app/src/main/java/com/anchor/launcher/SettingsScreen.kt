package com.anchor.launcher

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** A quiet horizontal line marking a transition between Settings sections, instead of
 * relying purely on blank vertical space to imply structure. ~8% white keeps it a hint
 * rather than a hard rule, matching the app's OLED-black, low-contrast aesthetic. */
@Composable
private fun SectionDivider() {
    Divider(
        modifier = Modifier.padding(vertical = Spacing.lg),
        color = AnchorSurfaceBorder,
        thickness = 1.dp
    )
}

/**
 * A compact wrapping grid of color swatches for picking a base or accent color from
 * AnchorPalette -- chosen over a 15-row radio list, which would have made this one section
 * longer than the rest of Settings combined and worked against the app's calm, edited feel.
 */
@Composable
private fun ColorSwatchGrid(selectedId: String, onSelect: (String) -> Unit) {
    val columns = 5
    Column {
        AnchorPalette.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                row.forEach { entry ->
                    val isSelected = entry.id == selectedId
                    val label = stringResource(entry.labelRes)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(entry.color)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.tertiary else AnchorSurfaceBorder,
                                shape = CircleShape
                            )
                            .clickable { onSelect(entry.id) }
                            .semantics { contentDescription = label }
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
        }
        Text(stringResource(labelResForColorId(selectedId)), fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = AnchorType.label, color = MaterialTheme.colorScheme.secondary)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AnchorViewModel, onBack: () -> Unit) {
    var showAddSpaceDialog by remember { mutableStateOf(false) }
    var newSpaceName by remember { mutableStateOf("") }
    var showFrictionDialog by remember { mutableStateOf<AppInfo?>(null) }
    var showManageAppsDialog by remember { mutableStateOf<Space?>(null) }
    var showGestureDialog by remember { mutableStateOf<String?>(null) } // "swipe" or "double"
    var searchQuery by remember { mutableStateOf("") }

    // Memoized app filtering to prevent redundant collection allocations on every recomposition
    val installedApps = viewModel.installedApps
    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    // Isolated slider state to prevent full-screen recomposition cascades during rapid dragging
    var tempFontSize by remember { mutableFloatStateOf(viewModel.fontSizeMultiplier) }
    var tempLetterSpacing by remember { mutableFloatStateOf(viewModel.letterSpacingExtra) }
    var tempScheduleStart by remember { mutableFloatStateOf(viewModel.scheduleStartHour.toFloat()) }
    var tempScheduleEnd by remember { mutableFloatStateOf(viewModel.scheduleEndHour.toFloat()) }
    var showAddPresetDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Storage Access Framework pickers -- avoids needing WRITE_EXTERNAL_STORAGE or any
    // storage permission at all; the user explicitly picks where the file goes/comes from.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val json = viewModel.exportBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                Toast.makeText(context, context.getString(R.string.backup_exported), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    if (text != null) {
                        viewModel.importBackupJson(text)
                        Toast.makeText(context, context.getString(R.string.backup_imported), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.backup_import_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

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
                .padding(horizontal = Spacing.lg)
        ) {
            // REFLECTION
            item {
                SectionLabel(stringResource(R.string.daily_intention_title))
                Spacer(modifier = Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = viewModel.oneThingReflection,
                    onValueChange = { viewModel.setOneThing(it) },
                    placeholder = { Text(stringResource(R.string.one_thing_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                SectionDivider()
            }

            // SPACES MANAGEMENT
            item {
                SectionLabel(stringResource(R.string.spaces_title))
                Spacer(modifier = Modifier.height(Spacing.md))
                viewModel.spaces.forEach { space ->
                    val isCurrent = viewModel.currentSpace.id == space.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showManageAppsDialog = space }
                            .padding(vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            // Tertiary gold marks the currently-active space -- one of this
                            // app's three sanctioned accent moments (see Theme.kt).
                            Text(
                                space.name,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = if (space.allowedApps.isEmpty()) stringResource(R.string.all_apps_subtitle) else stringResource(R.string.apps_selected_count, space.allowedApps.size),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        IconButton(onClick = { viewModel.deleteSpace(space.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_content_description), tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                TextButton(onClick = { showAddSpaceDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.add_new_space))
                }
                SectionDivider()
            }

            // FRICTION RULES
            item {
                SectionLabel(stringResource(R.string.intentional_friction_title))
                Spacer(modifier = Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_friction_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(Spacing.md))
            }

            items(filteredApps, key = { it.packageName }) { app ->
                val friction = viewModel.appFrictionLevels[app.packageName] ?: "OFF"
                if (friction != "OFF" || searchQuery.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(app.label, modifier = Modifier.weight(1f))
                        TextButton(onClick = { showFrictionDialog = app }) {
                            Text(stringResource(frictionLevelLabelRes(friction)), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            item {
                SectionDivider()

                // SCHEDULE (used by the SCHEDULE friction level)
                SectionLabel(stringResource(R.string.schedule_title))
                Spacer(modifier = Modifier.height(Spacing.md))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.schedule_enabled_label), fontSize = 14.sp)
                    Switch(
                        checked = viewModel.scheduleEnabled,
                        onCheckedChange = { viewModel.setScheduleEnabled(it) }
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(stringResource(R.string.schedule_start_label, tempScheduleStart.toInt()), fontSize = 14.sp)
                Slider(
                    value = tempScheduleStart,
                    onValueChange = { tempScheduleStart = it },
                    onValueChangeFinished = { viewModel.setScheduleStartHour(tempScheduleStart.toInt()) },
                    valueRange = 0f..23f,
                    steps = 22
                )
                Text(stringResource(R.string.schedule_end_label, tempScheduleEnd.toInt()), fontSize = 14.sp)
                Slider(
                    value = tempScheduleEnd,
                    onValueChange = { tempScheduleEnd = it },
                    onValueChangeFinished = { viewModel.setScheduleEndHour(tempScheduleEnd.toInt()) },
                    valueRange = 0f..23f,
                    steps = 22
                )

                SectionDivider()

                // PRESETS
                SectionLabel(stringResource(R.string.presets_title))
                Spacer(modifier = Modifier.height(Spacing.md))
                viewModel.presets.forEach { preset ->
                    val spaceName = viewModel.spaces.find { it.id == preset.spaceId }?.name
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(preset.name, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (spaceName != null) stringResource(R.string.preset_summary_with_space, preset.focusMinutes, spaceName) else stringResource(R.string.preset_summary_current_space, preset.focusMinutes),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        IconButton(onClick = { viewModel.deletePreset(preset.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_content_description), tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                TextButton(onClick = { showAddPresetDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.add_preset))
                }

                SectionDivider()

                // DENSITY
                SectionLabel(stringResource(R.string.density_title))
                Spacer(modifier = Modifier.height(Spacing.md))
                DensityMode.values().forEach { mode ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(densityModeLabelRes(mode)), color = if(viewModel.densityMode == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                        RadioButton(selected = viewModel.densityMode == mode, onClick = { viewModel.setDensity(mode) })
                    }
                }

                SectionDivider()

                // COLORS
                SectionLabel(stringResource(R.string.colors_title))
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(stringResource(R.string.color_base_label), fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(Spacing.xs))
                ColorSwatchGrid(selectedId = viewModel.baseColorId, onSelect = { viewModel.setBaseColor(it) })
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(stringResource(R.string.color_accent_label), fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(Spacing.xs))
                ColorSwatchGrid(selectedId = viewModel.accentColorId, onSelect = { viewModel.setAccentColor(it) })

                SectionDivider()

                // GESTURES
                SectionLabel(stringResource(R.string.gestures_title))
                Spacer(modifier = Modifier.height(Spacing.md))
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.swipe_down_action_label), fontSize = 14.sp)
                    TextButton(onClick = { showGestureDialog = "swipe" }) {
                        Text(stringResource(gestureActionLabelRes(viewModel.swipeDownAction)), color = MaterialTheme.colorScheme.primary)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.double_tap_action_label), fontSize = 14.sp)
                    TextButton(onClick = { showGestureDialog = "double" }) {
                        Text(stringResource(gestureActionLabelRes(viewModel.doubleTapAction)), color = MaterialTheme.colorScheme.primary)
                    }
                }

                SectionDivider()

                // FEEDBACK
                SectionLabel(stringResource(R.string.feedback_title))
                Spacer(modifier = Modifier.height(Spacing.md))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.haptics_label), fontSize = 14.sp)
                    Switch(
                        checked = viewModel.hapticsEnabled,
                        onCheckedChange = { viewModel.setHapticsEnabled(it) }
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.dnd_on_focus_label), fontSize = 14.sp)
                    Switch(
                        checked = viewModel.dndOnFocusEnabled,
                        onCheckedChange = { checked ->
                            // Notification policy access is a special app-op, not a normal
                            // runtime permission dialog -- it can only be granted from this
                            // system Settings screen. The setting is still saved as "on" even
                            // if access isn't granted yet; DND simply won't engage until it is
                            // (see AnchorViewModel.setDnd()), which mirrors how the Screen
                            // Time widget handles its own special-permission gate.
                            if (checked && !viewModel.hasDndAccess()) {
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                            }
                            viewModel.setDndOnFocus(checked)
                        }
                    )
                }

                SectionDivider()

                // BACKUP
                SectionLabel(stringResource(R.string.backup_title))
                Spacer(modifier = Modifier.height(Spacing.md))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = {
                        exportLauncher.launch("anchor-backup.json")
                    }) {
                        Text(stringResource(R.string.export_backup), color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = {
                        importLauncher.launch(arrayOf("application/json"))
                    }) {
                        Text(stringResource(R.string.import_backup), color = MaterialTheme.colorScheme.primary)
                    }
                }

                SectionDivider()

                // TYPOGRAPHY
                SectionLabel(stringResource(R.string.typography_title))
                Spacer(modifier = Modifier.height(Spacing.md))

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

                Spacer(modifier = Modifier.height(Spacing.sm))

                // Previously loaded, persisted, and applied to the clock/task/app-name text
                // in three places -- but with no control anywhere to actually change it, so
                // it was permanently stuck at its default of 0.
                Text(stringResource(R.string.letter_spacing_label), fontSize = 14.sp)
                Slider(
                    value = tempLetterSpacing,
                    onValueChange = { tempLetterSpacing = it },
                    onValueChangeFinished = {
                        viewModel.letterSpacingExtra = tempLetterSpacing
                        viewModel.updateSetting("letter_spacing", tempLetterSpacing.toString())
                    },
                    valueRange = -2f..4f
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

                SectionDivider()
                SectionLabel(stringResource(R.string.about_title))
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(stringResource(R.string.app_full_name), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.app_version), fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(Spacing.xxl))
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
                                Column {
                                    Text(stringResource(frictionLevelLabelRes(level)))
                                    // Flagged as a gap since the very first pass over this
                                    // app: the friction system works, but nothing explained
                                    // what each level actually does -- BLOCK in particular is
                                    // easy to misread (it only gates the app during an active
                                    // Focus session; the rest of the time it opens instantly).
                                    Text(
                                        text = stringResource(frictionLevelDescRes(level)),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showFrictionDialog = null }) { Text(stringResource(R.string.done)) } }
            )
        }

        showGestureDialog?.let { type ->
            val current = if (type == "swipe") viewModel.swipeDownAction else viewModel.doubleTapAction
            AlertDialog(
                onDismissRequest = { showGestureDialog = null },
                title = { Text(stringResource(R.string.choose_gesture_action_title)) },
                text = {
                    Column {
                        listOf("NOTIFICATIONS", "DRAWER", "NONE").forEach { action ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                RadioButton(
                                    selected = current == action,
                                    onClick = {
                                        if (type == "swipe") viewModel.setSwipeDownAction(action) else viewModel.setDoubleTapAction(action)
                                    }
                                )
                                Text(stringResource(gestureActionLabelRes(action)))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showGestureDialog = null }) { Text(stringResource(R.string.done)) } }
            )
        }

        showManageAppsDialog?.let { space ->
            ManageSpaceAppsDialog(
                space = space,
                viewModel = viewModel,
                onDismiss = { showManageAppsDialog = null }
            )
        }

        if (showAddPresetDialog) {
            AddPresetDialog(
                viewModel = viewModel,
                onDismiss = { showAddPresetDialog = false }
            )
        }
    }
}

/**
 * Lets the user restrict which apps show in the drawer while a given Space is active.
 * Space.allowedApps previously existed as a field but was never populated or read anywhere;
 * this dialog (together with the filtering in AppDrawer) is what actually makes it do
 * something. Restricted state is keyed off whether the list is non-empty, so existing
 * unrestricted spaces are unaffected until the user opts in here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageSpaceAppsDialog(space: Space, viewModel: AnchorViewModel, onDismiss: () -> Unit) {
    var isRestricted by remember(space.id) { mutableStateOf(space.allowedApps.isNotEmpty()) }
    var selected by remember(space.id) { mutableStateOf(space.allowedApps.toSet()) }
    var query by remember(space.id) { mutableStateOf("") }

    val filteredApps = remember(viewModel.installedApps, query) {
        if (query.isBlank()) viewModel.installedApps
        else viewModel.installedApps.filter { it.label.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_space_apps_title, space.name)) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.restrict_space_toggle))
                    Switch(checked = isRestricted, onCheckedChange = { isRestricted = it })
                }

                if (isRestricted) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.search_apps_simple_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selected.contains(app.packageName),
                                    onCheckedChange = { checked ->
                                        selected = if (checked) selected + app.packageName else selected - app.packageName
                                    }
                                )
                                Text(app.label, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.setSpaceAllowedApps(space.id, if (isRestricted) selected.toList() else emptyList())
                onDismiss()
            }) { Text(stringResource(R.string.done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

/**
 * Creates a Preset -- a named shortcut bundling a Focus duration and (optionally) a Space
 * switch into one tap. Duration choices match FocusWidget's existing quick-start durations
 * (10/25/60m) for consistency rather than introducing a third way to pick a duration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPresetDialog(viewModel: AnchorViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedMinutes by remember { mutableIntStateOf(25) }
    var selectedSpaceId by remember { mutableStateOf("") } // "" = current space, don't switch

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_preset_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.preset_name_label)) },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(Spacing.md))

                Text(stringResource(R.string.preset_duration_label), fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                listOf(10, 25, 60).forEach { minutes ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = selectedMinutes == minutes, onClick = { selectedMinutes = minutes })
                        Text(stringResource(R.string.preset_minutes_format, minutes))
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.md))
                Text(stringResource(R.string.preset_space_label), fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = selectedSpaceId == "", onClick = { selectedSpaceId = "" })
                    Text(stringResource(R.string.preset_space_current))
                }
                viewModel.spaces.forEach { space ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = selectedSpaceId == space.id, onClick = { selectedSpaceId = space.id })
                        Text(space.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    viewModel.addPreset(name, selectedMinutes, selectedSpaceId)
                    onDismiss()
                }
            }) { Text(stringResource(R.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
