package com.anchor.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingScreen(viewModel: AnchorViewModel, onComplete: () -> Unit) {
    var step by remember { mutableIntStateOf(1) }
    val context = LocalContext.current

    val roleRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { /* result ignored -- user either picked Anchor as default or didn't */ }

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
                    // Previously these buttons only advanced the onboarding step and never
                    // called viewModel.setDensity(), so the very first choice a new user
                    // makes was silently discarded and the app always defaulted to BALANCED.
                    DensityOption("QUIET", "Just the time.") {
                        viewModel.setDensity(DensityMode.QUIET)
                        step = 3
                    }
                    DensityOption("BALANCED", "Priorities & Quick Apps.") {
                        viewModel.setDensity(DensityMode.BALANCED)
                        step = 3
                    }
                    DensityOption("CONTROL", "Full command surface.") {
                        viewModel.setDensity(DensityMode.CONTROL)
                        step = 3
                    }
                }
                3 -> {
                    Text("READY", fontSize = 24.sp, letterSpacing = 4.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Swipe up for apps.\nSwipe sideways for Spaces.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(48.dp))
                    // The manifest declares the HOME/LAUNCHER intent filters needed for
                    // Anchor to be selectable as a default launcher, but nothing ever
                    // actually prompted the user to set it as default -- onboarding used to
                    // just end with no path to making Anchor the actual home screen.
                    Button(onClick = {
                        requestDefaultLauncher(context, roleRequestLauncher)
                        onComplete()
                    }) { Text("ENTER ANCHOR") }
                }
            }
        }
    }
}

private fun requestDefaultLauncher(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
            launcher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        }
    } else {
        // Pre-Q there's no RoleManager; the closest equivalent is opening the system
        // "Home app" picker directly.
        try {
            context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            // Some OEMs don't ship this settings screen; failing silently here is
            // acceptable since the user can still set Anchor as default the normal way
            // (pressing Home and choosing from the system chooser).
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
