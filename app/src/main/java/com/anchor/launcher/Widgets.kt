package com.anchor.launcher

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.BatteryManager
import android.provider.CalendarContract
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Shared small label style used by nearly every widget row -- pulled out so the
 * fontSize/letterSpacing/color triplet isn't repeated (and easy to drift) five times. */
@Composable
private fun WidgetLabel(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.secondary
    )
}

@Composable
fun BatteryWidget() {
    val context = LocalContext.current
    var batteryPct by remember { mutableIntStateOf(100) }

    LaunchedEffect(Unit) {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) {
            batteryPct = (level / scale.toFloat() * 100).toInt()
        }
    }

    WidgetLabel("BATTERY · $batteryPct%")
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
            withContext(Dispatchers.IO) {
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
    }

    if (hasPermission) {
        WidgetLabel("NEXT · $nextEvent")
    } else {
        TextButton(onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) }) {
            WidgetLabel("ENABLE CALENDAR WIDGET")
        }
    }
}

/**
 * Reruns [block] every time the host Activity resumes (not just on first composition).
 * Needed for widgets that depend on a permission the user grants in a separate Settings
 * screen (usage access, location settings) -- without this, coming back from Settings
 * would leave the widget stuck on its "enable" prompt until process recreation.
 */
@Composable
private fun rememberResumeCounter(): Int {
    var resumeCount by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeCount++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return resumeCount
}

private fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun queryScreenTimeMinutesToday(context: Context): Int {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val startOfDay = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, System.currentTimeMillis())
    val totalMs = stats?.sumOf { it.totalTimeInForeground } ?: 0L
    return (totalMs / 60000L).toInt()
}

@Composable
fun ScreenTimeWidget(viewModel: AnchorViewModel) {
    val context = LocalContext.current
    val resumeSignal = rememberResumeCounter()
    var hasPermission by remember { mutableStateOf(hasUsageAccess(context)) }

    LaunchedEffect(resumeSignal) {
        hasPermission = hasUsageAccess(context)
        if (hasPermission) {
            viewModel.screenTimeMinutes = withContext(Dispatchers.IO) { queryScreenTimeMinutesToday(context) }
        }
    }

    if (hasPermission) {
        val hours = viewModel.screenTimeMinutes / 60
        val minutes = viewModel.screenTimeMinutes % 60
        WidgetLabel("SCREEN TIME · ${hours}h ${minutes}m")
    } else {
        TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }) {
            WidgetLabel("ENABLE SCREEN TIME WIDGET")
        }
    }
}

/**
 * Real weather via Open-Meteo (no API key required) using the device's last known
 * network/GPS location. Previously this was a hardcoded "22° · LONDON" string shown to
 * every user everywhere -- replaced with an honest permission-gated real value, or a
 * clear "unavailable" state rather than fabricated data.
 */
@Composable
fun WeatherWidget() {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var weatherText by remember { mutableStateOf("WEATHER · —") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            weatherText = withContext(Dispatchers.IO) {
                try {
                    @Suppress("MissingPermission")
                    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val provider = when {
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                        else -> null
                    }
                    val location = provider?.let { locationManager.getLastKnownLocation(it) }
                    if (location == null) {
                        "WEATHER · LOCATION UNAVAILABLE"
                    } else {
                        val url = "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}" +
                            "&longitude=${location.longitude}&current=temperature_2m"
                        val response = java.net.URL(url).readText()
                        val temp = JSONObject(response).getJSONObject("current").getDouble("temperature_2m")
                        "WEATHER · ${temp.toInt()}°"
                    }
                } catch (e: Exception) {
                    "WEATHER · UNAVAILABLE"
                }
            }
        }
    }

    if (hasPermission) {
        WidgetLabel(weatherText)
    } else {
        TextButton(onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) {
            WidgetLabel("ENABLE WEATHER WIDGET")
        }
    }
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
fun BreathingAnimationBox(timeLeft: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
        val color = MaterialTheme.colorScheme.primary
        Canvas(modifier = Modifier.size(32.dp * scale)) {
            drawCircle(color = color, style = Stroke(width = 2.dp.toPx()))
        }
        Text("$timeLeft", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun BreathingWidget() {
    var isRunning by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableIntStateOf(60) }
    val view = LocalView.current

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
                    text = if (isRunning) "Focus on your breath..." else "1-Minute Mindfulness",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (isRunning) {
                BreathingAnimationBox(timeLeft)
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
