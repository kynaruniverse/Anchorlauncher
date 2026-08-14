package com.anchor.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anchor.launcher.ui.AnchorTheme
import java.text.SimpleDateFormat
import java.util.*

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
    var isOnboarded by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showDrawer by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { viewModel.spaces.size })

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
                        if (dragAmount < -50) showDrawer = true
                    }
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

            // Intent Gate Overlay
            viewModel.pendingAppLaunch?.let { app ->
                IntentGate(
                    appName = app.label,
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
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    
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
        // TOP: Time & Date
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = time,
                    fontSize = 76.sp,
                    fontWeight = FontWeight.ExtraLight,
                    letterSpacing = (-2).sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = viewModel.currentSpace.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            Text(
                text = date,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        // MIDDLE: Priorities
        if (viewModel.densityMode != DensityMode.QUIET) {
            Column(modifier = Modifier.weight(1f).padding(top = 40.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TODAY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "${tasks.count { it.isCompleted }}/${tasks.size}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                if (tasks.isEmpty()) {
                    Text(
                        text = "· Add a priority for today",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                    )
                }

                tasks.forEach { task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = task.isCompleted,
                            onCheckedChange = { viewModel.toggleTask(task) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary,
                                uncheckedColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = task.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            color = if (task.isCompleted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        // BOTTOM: Control Widgets
        if (viewModel.densityMode == DensityMode.CONTROL) {
            Column {
                FocusWidget(viewModel)
                Spacer(modifier = Modifier.height(12.dp))
                BatteryWidget()
            }
        }
    }
}

@Composable
fun AppDrawer(viewModel: AnchorViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val apps = remember { viewModel.getInstalledApps(context) }
    var query by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
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

            val filteredApps = apps.filter { it.label.contains(query, true) }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredApps) { app ->
                    TextButton(
                        onClick = { 
                            viewModel.handleAppClick(app, context)
                            onClose()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = app.label,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Light,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
