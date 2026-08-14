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
fun OnboardingScreen(onComplete: () -> Unit) {
    var step by remember { mutableIntStateOf(1) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (step) {
                1 -> {
                    Text("ANCHOR", fontSize = 32.sp, letterSpacing = 8.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Your phone. On purpose.", textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(48.dp))
                    Button(onClick = { step = 2 }) { Text("Get Started") }
                }
                2 -> {
                    Text("Choose your Density", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(24.dp))
                    DensityOption("Quiet", "Just the time.") { step = 3 }
                    DensityOption("Balanced", "Priorities & Quick Apps.") { step = 3 }
                    DensityOption("Control", "Full command surface.") { step = 3 }
                }
                3 -> {
                    Text("Ready.", fontSize = 24.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Swipe up for apps. Swipe sideways for Spaces.", textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(onClick = onComplete) { Text("Enter Anchor") }
                }
            }
        }
    }
}

@Composable
fun DensityOption(title: String, desc: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(desc, style = MaterialTheme.typography.bodySmall)
        }
    }
}
