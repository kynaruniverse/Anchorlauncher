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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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

    fun handleGestureAction(action: String) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        when (action) {
            "NOTIFICATIONS" -> {
                try {
                    val statusBarService = context.getSystemService("statusbar")
                    val statusBarManager = Class.forName("android.app.StatusBarManager")
                    val expandMethod = statusBarManager.getMethod("expandNotificationsPanel")
                    expandMethod.invoke(statusBarService)
                } catch (e: Exception) {}
            }
            "DRAWER" -> showDrawer = true
            "NONE" -> {}
        }
    }

    if (!isOnboarded) {
        OnboardingScreen(onComplete = { isOnboarded = true })
    } else if (showSettings) {
        SettingsScreen(viewModel, onBack = { showSettings = false })
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
                TodaySurface(viewModel)
            }

            IconButton(
                onClick = { showSettings = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.secondary)
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
                    onProceed = { viewModel.launchApp(app.packageName, context) },
                    onCancel = { viewModel.pendingAppLaunch = null }
                )
            }
        }
    }
}

@Composable
fun TodaySurface(viewModel: AnchorViewModel) {
    val tasks by viewModel.getTasks().collectAsState(initial = emptyList())
    val view = LocalView.current
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    
    val baseFontWeight = if (viewModel.isBoldEnabled) FontWeight.Bold else FontWeight.Light
    val letterSpacingVal = viewModel.letterSpacingExtra

    LaunchedEffect(Unit) {
        while(true) {
            val now = Date()
            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            date = SimpleDateFormat("EEEE · d MMM", Locale.getDefault()).format(now).uppercase()
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = time,
                    fontSize = (76f * viewModel.fontSizeMultiplier).sp,
                    fontWeight = baseFontWeight,
                    letterSpacing = (-2f + letterSpacingVal).sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = viewModel.currentSpace.name,
                    fontSize = (13f * viewModel.fontSizeMultiplier).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (3f + letterSpacingVal).sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            Text(
                text = date,
                fontSize = (12f * viewModel.fontSizeMultiplier).sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (2f + letterSpacingVal).sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        if (viewModel.densityMode != DensityMode.QUIET) {
            Column(modifier = Modifier.weight(1f).padding(top = 40.dp)) {
                ReflectionWidget(viewModel)
                
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TODAY",
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
                
                Spacer(modifier = Modifier.height(16.dp))

                tasks.forEach { task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
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
    val allApps = remember { viewModel.getInstalledApps(context) }
    var query by remember { mutableStateOf("") }

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
                        placeholder = { Text("Search apps or commands...", color = MaterialTheme.colorScheme.secondary) },
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
                        Text("RUN", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (query.isEmpty() && favoriteAppsList.isNotEmpty()) {
                        item {
                            Text("FAVORITES", fontSize = 10.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        itemsIndexed(favoriteAppsList) { index, app ->
                            StaggeredAppRow(index, app, viewModel, context, onClose)
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }

                    if (query.isEmpty() && recentAppsList.isNotEmpty()) {
                        item {
                            Text("RECENT", fontSize = 10.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        itemsIndexed(recentAppsList) { index, app ->
                            StaggeredAppRow(index, app, viewModel, context, onClose)
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }

                    if (query.isEmpty()) {
                        item {
                            Text("ALL APPS", fontSize = 10.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    itemsIndexed(filteredApps) { index, app ->
                        StaggeredAppRow(index, app, viewModel, context, onClose)
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
                                Text(if (viewModel.favoriteApps.contains(app.packageName)) "Remove from Favorites" else "Add to Favorites")
                            }
                            TextButton(onClick = { viewModel.toggleHideApp(app.packageName) }) {
                                Text("Hide App")
                            }
                            TextButton(onClick = { viewModel.uninstallApp(app.packageName, context) }) {
                                Text("Uninstall", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.selectedAppForMenu = null }) {
                            Text("Cancel")
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
fun StaggeredAppRow(index: Int, app: AppInfo, viewModel: AnchorViewModel, context: Context, onClose: () -> Unit) {
    val view = LocalView.current
    val animatedAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 400, delayMillis = index * 30),
        label = "alpha"
    )
    val animatedOffset by animateDpAsState(
        targetValue = 0.dp,
        animationSpec = tween(durationMillis = 400, delayMillis = index * 30),
        label = "offset"
    )

    TextButton(
        onClick = { 
            viewModel.handleAppClick(app, context)
            onClose()
        },
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { 
                alpha = animatedAlpha
                translationY = (20.dp - animatedOffset).toPx()
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
    ) {
        Text(
            text = app.label,
            fontSize = (18f * viewModel.fontSizeMultiplier).sp,
            fontWeight = if (viewModel.isBoldEnabled) FontWeight.Bold else FontWeight.Light,
            letterSpacing = viewModel.letterSpacingExtra.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}
