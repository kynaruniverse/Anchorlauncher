package com.anchor.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anchor.launcher.ui.AnchorTheme
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: AnchorViewModel = viewModel()
            AnchorTheme {
                MainScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: AnchorViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    var isOnboarded by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDrawer by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { viewModel.spaces.size })

    // Sync Pager with ViewModel
    LaunchedEffect(pagerState.currentPage) {
        viewModel.currentSpaceIndex = pagerState.currentPage
    }

    // Sync ViewModel with Pager
    LaunchedEffect(viewModel.currentSpaceIndex) {
        if (pagerState.currentPage != viewModel.currentSpaceIndex) {
            pagerState.animateScrollToPage(viewModel.currentSpaceIndex)
        }
    }

    fun handleGestureAction(action: String) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        when (action) {
            "NOTIFICATIONS" -> {
                // There is no public, stable API for a launcher to expand the notification
                // shade. This reflective call is the de-facto approach other launchers use,
                // but it requires EXPAND_STATUS_BAR (auto-granted to default launchers on
                // most, not all, OEM skins) and can be blocked entirely on some ROMs. When it
                // fails we at least distinguish "nothing happened" from "gesture worked" with
                // haptic feedback so the user isn't left wondering if the swipe registered.
                try {
                    val statusBarService = context.getSystemService("statusbar")
                    val statusBarManager = Class.forName("android.app.StatusBarManager")
                    val expandMethod = statusBarManager.getMethod("expandNotificationsPanel")
                    expandMethod.invoke(statusBarService)
                } catch (e: Exception) {
                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                }
            }
            "DRAWER" -> showDrawer = true
            "NONE" -> {}
        }
    }

    if (!isOnboarded) {
        OnboardingScreen(viewModel = viewModel, onComplete = { isOnboarded = true })
    } else if (showSettings) {
        SettingsScreen(viewModel, onBack = { showSettings = false })
    } else if (!viewModel.appsLoaded) {
        // Previously there was a blank black frame here while the DB/app list loaded on
        // cold start (pageCount == 0, drawer would show "no apps"). Explicit loading state
        // instead of an unexplained blank screen.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary, strokeWidth = 2.dp)
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -50) {
                            showDrawer = true
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        if (dragAmount > 50) handleGestureAction(viewModel.swipeDownAction)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { handleGestureAction(viewModel.doubleTapAction) },
                        onLongPress = { showSettings = true }
                    )
                }
        ) {
            HorizontalPager(state = pagerState) { page ->
                val space = viewModel.spaces.getOrNull(page)
                if (space != null) {
                    TodaySurface(viewModel, space)
                }
            }

            IconButton(
                onClick = { showSettings = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_content_description), tint = MaterialTheme.colorScheme.secondary)
            }

            AnimatedVisibility(
                visible = showDrawer,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                AppDrawer(viewModel) { showDrawer = false }
            }

            viewModel.pendingAppLaunch?.let { app ->
                val friction = viewModel.appFrictionLevels[app.packageName] ?: "OFF"
                IntentGate(
                    appName = app.label,
                    frictionLevel = friction,
                    onProceed = { minutes ->
                        if (friction == "TIMER") {
                            viewModel.launchApp(app.packageName, context, minutes)
                        } else {
                            viewModel.launchApp(app.packageName, context)
                        }
                    },
                    onCancel = { viewModel.pendingAppLaunch = null }
                )
            }
        }
    }
}

/**
 * Isolated ClockDisplay to prevent the entire TodaySurface from recomposing every second.
 */
@Composable
fun ClockDisplay(viewModel: AnchorViewModel) {
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }

    // Formatters are expensive to allocate and were previously re-created every single tick
    // (every second, per visible/pre-composed pager page). Cached once per composition instead.
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE · d MMM", Locale.getDefault()) }

    val baseFontWeight = if (viewModel.isBoldEnabled) FontWeight.Bold else FontWeight.Light
    val letterSpacingVal = viewModel.letterSpacingExtra

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            time = timeFormat.format(now)
            date = dateFormat.format(now).uppercase()
            delay(1000)
        }
    }

    Column {
        Text(
            text = time,
            fontSize = (76f * viewModel.fontSizeMultiplier).sp,
            fontWeight = baseFontWeight,
            letterSpacing = (-2f + letterSpacingVal).sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = date,
            fontSize = (12f * viewModel.fontSizeMultiplier).sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (2f + letterSpacingVal).sp,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
fun TodaySurface(viewModel: AnchorViewModel, space: Space) {
    val tasks by viewModel.getTasks(space.id).collectAsState(initial = emptyList())
    val view = LocalView.current
    val letterSpacingVal = viewModel.letterSpacingExtra

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            ClockDisplay(viewModel)
            Text(
                text = space.name,
                fontSize = (13f * viewModel.fontSizeMultiplier).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (3f + letterSpacingVal).sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (viewModel.densityMode != DensityMode.QUIET) {
            Column(modifier = Modifier.weight(1f).padding(top = 24.dp)) {
                ReflectionWidget(viewModel)

                Spacer(modifier = Modifier.height(16.dp))

                // Task Input Field
                var newTaskText by remember { mutableStateOf("") }
                TextField(
                    value = newTaskText,
                    onValueChange = { newTaskText = it },
                    placeholder = { Text(stringResource(R.string.add_priority_placeholder), color = MaterialTheme.colorScheme.secondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.secondary
                    ),
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = {
                            if (newTaskText.isNotBlank()) {
                                viewModel.addTask(newTaskText, space.id)
                                newTaskText = ""
                            }
                        }) {
                            Text(stringResource(R.string.add), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.today_title),
                        fontSize = (11f * viewModel.fontSizeMultiplier).sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (2f + letterSpacingVal).sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "${tasks.count { it.isCompleted }}/${tasks.size}",
                        fontSize = (11f * viewModel.fontSizeMultiplier).sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(tasks, key = { _, task -> task.id }) { _, task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.isCompleted,
                                onCheckedChange = {
                                    viewModel.toggleTask(task)
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = MaterialTheme.colorScheme.secondary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = task.text,
                                fontSize = (16f * viewModel.fontSizeMultiplier).sp,
                                fontWeight = if (viewModel.isBoldEnabled) FontWeight.Bold else FontWeight.Normal,
                                letterSpacing = letterSpacingVal.sp,
                                color = if (task.isCompleted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        if (viewModel.densityMode == DensityMode.CONTROL) {
            Column {
                FocusWidget(viewModel)
                Spacer(modifier = Modifier.height(8.dp))
                CalendarWidget()
                Spacer(modifier = Modifier.height(8.dp))
                ScreenTimeWidget(viewModel)
                Spacer(modifier = Modifier.height(8.dp))
                WeatherWidget()
                Spacer(modifier = Modifier.height(8.dp))
                BatteryWidget()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawer(viewModel: AnchorViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val allApps = viewModel.installedApps
    var query by remember { mutableStateOf("") }

    // Tracks which packages have already played their staggered entrance animation so that
    // recomposition (e.g. every keystroke in search, which previously caused the whole
    // visible list to visibly re-stagger/flicker) doesn't retrigger it.
    // Plain (non-Compose-state) MutableSet -- mutableStateSetOf() doesn't exist in the
    // Compose runtime API (only mutableStateListOf/mutableStateMapOf do). This doesn't need
    // to be observable anyway: it's a one-way gate mutated from the animation's
    // finishedListener callback, and is only ever read during a recomposition that's
    // already happening for some other reason (search query changing, etc).
    val animatedKeys = remember { mutableSetOf<String>() }

    val visibleApps = allApps.filter { !viewModel.hiddenApps.contains(it.packageName) }
    val filteredApps = visibleApps.filter { it.label.contains(query, true) }

    val favoriteAppsList = visibleApps.filter { viewModel.favoriteApps.contains(it.packageName) }
    val recentAppsList = viewModel.recentAppPackages.mapNotNull { pkg -> visibleApps.find { it.packageName == pkg } }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 48.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.search_apps_placeholder), color = MaterialTheme.colorScheme.secondary) },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.secondary
                        ),
                        singleLine = true
                    )
                    TextButton(onClick = {
                        if (viewModel.executeCommand(query, context)) onClose()
                    }) {
                        Text(stringResource(R.string.run), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (query.isEmpty() && favoriteAppsList.isNotEmpty()) {
                        item {
                            Text(stringResource(R.string.favorites_title), fontSize = 10.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        itemsIndexed(favoriteAppsList, key = { _, app -> "fav_${app.packageName}" }) { index, app ->
                            StaggeredAppRow(index, app, viewModel, context, onClose, animatedKeys)
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }

                    if (query.isEmpty() && recentAppsList.isNotEmpty()) {
                        item {
                            Text(stringResource(R.string.recent_title), fontSize = 10.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        itemsIndexed(recentAppsList, key = { _, app -> "recent_${app.packageName}" }) { index, app ->
                            StaggeredAppRow(index, app, viewModel, context, onClose, animatedKeys)
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }

                    if (query.isEmpty()) {
                        item {
                            Text(stringResource(R.string.all_apps_title), fontSize = 10.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    itemsIndexed(filteredApps, key = { _, app -> "all_${app.packageName}" }) { index, app ->
                        StaggeredAppRow(index, app, viewModel, context, onClose, animatedKeys)
                    }
                }
            }

            viewModel.selectedAppForMenu?.let { app ->
                AlertDialog(
                    onDismissRequest = { viewModel.selectedAppForMenu = null },
                    title = { Text(app.label) },
                    text = {
                        Column {
                            TextButton(onClick = { viewModel.toggleFavorite(app.packageName) }) {
                                Text(if (viewModel.favoriteApps.contains(app.packageName)) stringResource(R.string.remove_from_favorites) else stringResource(R.string.add_to_favorites))
                            }
                            TextButton(onClick = { viewModel.toggleHideApp(app.packageName) }) {
                                Text(stringResource(R.string.hide_app))
                            }
                            TextButton(onClick = { viewModel.uninstallApp(app.packageName, context) }) {
                                Text(stringResource(R.string.uninstall), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.selectedAppForMenu = null }) {
                            Text(stringResource(R.string.dialog_cancel))
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StaggeredAppRow(
    index: Int,
    app: AppInfo,
    viewModel: AnchorViewModel,
    context: Context,
    onClose: () -> Unit,
    animatedKeys: MutableSet<String>
) {
    val view = LocalView.current
    val alreadyAnimated = app.packageName in animatedKeys
    val clampedDelay = if (alreadyAnimated) 0 else (index * 15).coerceAtMost(300)

    val animatedAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = if (alreadyAnimated) 0 else 300, delayMillis = clampedDelay),
        label = "alpha",
        finishedListener = { animatedKeys.add(app.packageName) }
    )
    val animatedOffset by animateDpAsState(
        targetValue = 0.dp,
        animationSpec = tween(durationMillis = if (alreadyAnimated) 0 else 300, delayMillis = clampedDelay),
        label = "offset"
    )

    // Previously this was a TextButton whose own onClick AND a combinedClickable onClick
    // both fired viewModel.handleAppClick — redundant duplicate handling. Now a single
    // combinedClickable Row drives both tap and long-press, and the app icon (previously
    // never loaded at all) is shown alongside the label.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animatedAlpha
                translationY = (15.dp - animatedOffset).toPx()
            }
            .combinedClickable(
                onClick = {
                    viewModel.handleAppClick(app, context)
                    onClose()
                },
                onLongClick = {
                    viewModel.selectedAppForMenu = app
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(28.dp).clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Text(
            text = app.label,
            fontSize = (18f * viewModel.fontSizeMultiplier).sp,
            fontWeight = if (viewModel.isBoldEnabled) FontWeight.Bold else FontWeight.Light,
            letterSpacing = viewModel.letterSpacingExtra.sp,
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary
        )
    }
}
