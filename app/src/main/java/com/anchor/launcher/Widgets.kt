package com.anchor.launcher

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
fun FocusWidget(viewModel: AnchorViewModel) {
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
            TextButton(onClick = { viewModel.focusModeActive = false }) {
                Text("END SESSION", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            listOf(10, 25, 60).forEach { mins ->
                TextButton(onClick = { viewModel.startFocus(mins) }) {
                    Text("FOCUS ${mins}M", fontSize = 10.sp, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}
