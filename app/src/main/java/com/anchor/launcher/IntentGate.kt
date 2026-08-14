package com.anchor.launcher

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IntentGate(
    appName: String,
    onProceed: () -> Unit,
    onCancel: () -> Unit
) {
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
                color = MaterialTheme.colorScheme.secondary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Is this what you intended?",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Light
            )
            
            Spacer(modifier = Modifier.height(48.dp))

            val reasons = listOf(
                "I have a specific task",
                "I'm looking for information",
                "Quick check (under 2 mins)",
                "I just want to browse"
            )

            reasons.forEach { reason ->
                OutlinedButton(
                    onClick = {
                        if (reason == "I just want to browse") {
                            onCancel()
                        } else {
                            onProceed()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(reason, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Light)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            TextButton(onClick = onCancel) {
                Text("NEVERMIND", letterSpacing = 2.sp, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
