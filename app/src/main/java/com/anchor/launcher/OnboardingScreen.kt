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

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var step by remember { mutableIntStateOf(1) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (step) {
                1 -> {
                    Text("ANCHOR", fontSize = 32.sp, letterSpacing = 12.sp, fontWeight = FontWeight.ExtraLight)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Your phone. On purpose.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(64.dp))
                    Button(onClick = { step = 2 }) { Text("BEGIN") }
                }
                2 -> {
                    Text("CHOOSE DENSITY", fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(32.dp))
                    DensityOption("QUIET", "Just the time.") { step = 3 }
                    DensityOption("BALANCED", "Priorities & Quick Apps.") { step = 3 }
                    DensityOption("CONTROL", "Full command surface.") { step = 3 }
                }
                3 -> {
                    Text("READY", fontSize = 24.sp, letterSpacing = 4.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Swipe up for apps.\nSwipe sideways for Spaces.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(48.dp))
                    Button(onClick = onComplete) { Text("ENTER ANCHOR") }
                }
            }
        }
    }
}

@Composable
fun DensityOption(title: String, desc: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}
