package com.anchor.launcher

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.provider.CalendarContract
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

@Composable
fun BatteryWidget() {
    val context = LocalContext.current
    val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
        context.registerReceiver(null, ifilter)
    }
    val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val batteryPct = (level / scale.toFloat() * 100).toInt()

    Text(
        text = "BATTERY · $batteryPct%",
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.secondary
    )
}

@Composable
fun CalendarWidget() {
    val context = LocalContext.current
    var nextEvent by remember { mutableStateOf("No upcoming events") }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            try {
                val projection = arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART)
                val cursor = context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    "${CalendarContract.Events.DTSTART} >= ?",
                    arrayOf(System.currentTimeMillis().toString()),
                    "${CalendarContract.Events.DTSTART} ASC LIMIT 1"
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val title = it.getString(0)
                        val startTime = it.getLong(1)
                        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(startTime))
                        nextEvent = "$timeStr · $title"
                    }
                }
            } catch (e: Exception) {
                nextEvent = "Calendar unavailable"
            }
        }
    }

    if (hasPermission) {
        Text(
            text = "NEXT · $nextEvent",
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.secondary
        )
    } else {
        TextButton(onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) }) {
            Text("ENABLE CALENDAR WIDGET", fontSize = 10.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
fun ScreenTimeWidget(viewModel: AnchorViewModel) {
    val hours = viewModel.screenTimeMinutes / 60
    val minutes = viewModel.screenTimeMinutes % 60
    Text(
        text = "SCREEN TIME · ${hours}h ${minutes}m",
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.secondary
    )
}

@Composable
fun WeatherWidget() {
    Text(
        text = "WEATHER · 22° · LONDON",
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.secondary
    )
}

@Composable
fun ReflectionWidget(viewModel: AnchorViewModel) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = "ONE THING",
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (viewModel.oneThingReflection.isBlank()) "What matters today?" else viewModel.oneThingReflection,
            fontSize = 16.sp,
            color = if (viewModel.oneThingReflection.isBlank()) MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Light
        )
    }
}

@Composable
fun BreathingWidget() {
    var isRunning by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableIntStateOf(60) }
    val view = LocalView.current
    val infiniteTransition = rememberInfiniteTransition()
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (timeLeft > 0) {
                delay(1000)
                timeLeft--
            }
            isRunning = false
            timeLeft = 60
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("BREATHING", fontSize = 10.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isRunning) if (scale > 1.05f) "Inhale deeply..." else "Exhale slowly..." else "1-Minute Mindfulness", 
                    fontSize = 14.sp, 
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            if (isRunning) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                    val color = MaterialTheme.colorScheme.primary
                    Canvas(modifier = Modifier.size(32.dp * scale)) {
                        drawCircle(color = color, style = Stroke(width = 2.dp.toPx()))
                    }
                    Text("$timeLeft", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            } else {
                TextButton(onClick = { 
                    isRunning = true 
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }) {
                    Text("START", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
fun FocusWidget(viewModel: AnchorViewModel) {
    val view = LocalView.current
    if (viewModel.focusModeActive) {
        val mins = viewModel.focusTimeRemaining / 60
        val secs = viewModel.focusTimeRemaining % 60
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "FOCUS ACTIVE · ${mins}m ${secs}s",
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = { 
                viewModel.focusModeActive = false 
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }) {
                Text("END SESSION", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            listOf(10, 25, 60).forEach { mins ->
                TextButton(onClick = { 
                    viewModel.startFocus(mins)
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }) {
                    Text("FOCUS ${mins}M", fontSize = 10.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}
