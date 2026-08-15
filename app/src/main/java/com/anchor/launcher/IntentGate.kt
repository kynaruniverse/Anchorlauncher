package com.anchor.launcher

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Parses a duration label like "10 Minutes" -> 10, "No Limit" -> null.
 * Exposed so MainActivity/ViewModel can turn the user's choice into a real
 * timed-unlock window for TIMER-friction apps (previously this value was
 * captured and then silently discarded).
 */
fun parseDurationMinutes(duration: String): Int? {
    if (duration.equals("No Limit", ignoreCase = true)) return null
    return duration.takeWhile { it.isDigit() }.toIntOrNull()
}

@Composable
fun IntentGate(
    appName: String,
    frictionLevel: String,
    onProceed: (durationMinutes: Int?) -> Unit,
    onCancel: () -> Unit
) {
    var step by remember { mutableStateOf(if (frictionLevel == "LIGHT") 2 else 1) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = appName.uppercase(),
                fontSize = 20.sp,
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (step == 1) {
                // Step 1: Why?
                Text(
                    text = "Why are you opening this?",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))

                val reasons = listOf("Something specific", "Search for info", "Just browsing")
                reasons.forEach { reason ->
                    OutlinedButton(
                        onClick = {
                            if (frictionLevel == "INTENT") onProceed(null) else step = 2
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Text(reason.uppercase(), letterSpacing = 1.sp)
                    }
                }
            } else if (step == 2) {
                // Step 2: How long?
                Text(
                    text = if (frictionLevel == "TIMER") "How long do you need?" else "One more thing —",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))

                val durations = listOf("10 Minutes", "20 Minutes", "30 Minutes", "No Limit")
                durations.forEach { duration ->
                    OutlinedButton(
                        onClick = {
                            onProceed(parseDurationMinutes(duration))
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Text(duration.uppercase(), letterSpacing = 1.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            TextButton(onClick = onCancel) {
                Text("CANCEL", color = MaterialTheme.colorScheme.secondary, letterSpacing = 2.sp)
            }
        }
    }
}
