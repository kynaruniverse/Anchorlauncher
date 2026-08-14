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

@Composable
fun BatteryWidget() {
    val context = LocalContext.current
    val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
        context.registerReceiver(null, ifilter)
    }
    val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val batteryPct = (level / scale.toFloat() * 100).toInt()

    Text("Battery: $batteryPct%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
}

@Composable
fun FocusWidget(viewModel: AnchorViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (viewModel.focusModeActive) 
                MaterialTheme.colorScheme.primaryContainer 
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (viewModel.focusModeActive) {
                val mins = viewModel.focusTimeRemaining / 60
                val secs = viewModel.focusTimeRemaining % 60
                Text("Focus Active: ${mins}m ${secs}s")
                TextButton(onClick = { viewModel.focusModeActive = false }) { Text("Stop") }
            } else {
                Text("Start Focus Session")
                Row {
                    listOf(10, 20, 60).forEach { mins ->
                        OutlinedButton(onClick = { viewModel.startFocus(mins) }, modifier = Modifier.padding(end = 4.dp)) { Text("${mins}m") }
                    }
                }
            }
        }
    }
}
