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
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }

            AnimatedVisibility(
                visible = showDrawer,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                AppDrawer(viewModel) { showDrawer = false }
            }
        }
    }
}

@Composable
fun TodaySurface(viewModel: AnchorViewModel) {
    val tasks by viewModel.getTasks().collectAsState(initial = emptyList())
    var time by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        while(true) {
            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            kotlinx.coroutines.delay(1000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Text(time, fontSize = 72.sp, fontWeight = FontWeight.Light)
        Text(viewModel.currentSpace.name, letterSpacing = 4.sp, color = MaterialTheme.colorScheme.primary)
        
        Spacer(modifier = Modifier.height(48.dp))

        if (viewModel.densityMode != DensityMode.QUIET) {
            Text("TODAY", style = MaterialTheme.typography.labelLarge)
            tasks.take(3).forEach { task ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = task.isCompleted, onCheckedChange = { viewModel.toggleTask(task) })
                    Text(task.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (viewModel.densityMode == DensityMode.CONTROL) {
            Spacer(modifier = Modifier.height(24.dp))
            FocusWidget(viewModel)
            BatteryWidget()
        }
    }
}

@Composable
fun AppDrawer(viewModel: AnchorViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val apps = remember { viewModel.getInstalledApps(context) }
    var query by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(24.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search apps or commands...") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = {
                        if (viewModel.executeCommand(query, context)) onClose()
                    }) { Text("Run") }
                }
            )
            LazyColumn {
                items(apps.filter { it.label.contains(query, true) }) { app ->
                    TextButton(onClick = { 
                        context.startActivity(context.packageManager.getLaunchIntentForPackage(app.packageName))
                        onClose()
                    }) {
                        Text(app.label, modifier = Modifier.fillMaxWidth(), fontSize = 18.sp)
                    }
                }
            }
        }
    }
}
