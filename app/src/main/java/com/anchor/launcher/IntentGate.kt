package com.anchor.launcher

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Parses a duration label like "10 Minutes" -> 10. Any label with no leading digits
 * (e.g. "No Limit", or its translation in another locale) returns null. Deliberately does
 * NOT compare against the literal English string "No Limit" so this keeps working once the
 * duration labels are localized -- only the digit prefix carries meaning.
 */
fun parseDurationMinutes(duration: String): Int? {
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
                .padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = appName.uppercase(),
                style = AnchorType.title,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(Spacing.xxl))

            // A soft crossfade between the "why" and "how long" steps, instead of the
            // previous instant swap, so the gate reads as one considered moment rather than
            // two screens stapled together.
            Crossfade(targetState = step, animationSpec = tween(Motion.standard), label = "gate_step") { currentStep ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (currentStep == 1) {
                        Text(
                            text = stringResource(R.string.intent_gate_why),
                            style = AnchorType.body,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(Spacing.xl))

                        val reasons = listOf(
                            R.string.intent_gate_reason_specific,
                            R.string.intent_gate_reason_search,
                            R.string.intent_gate_reason_browsing
                        )
                        reasons.forEach { reasonRes ->
                            OutlinedButton(
                                onClick = {
                                    if (frictionLevel == "INTENT") onProceed(null) else step = 2
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)
                            ) {
                                Text(stringResource(reasonRes).uppercase(), letterSpacing = 1.sp)
                            }
                        }
                    } else {
                        Text(
                            text = if (frictionLevel == "TIMER") stringResource(R.string.intent_gate_how_long) else stringResource(R.string.intent_gate_one_more_thing),
                            style = AnchorType.body,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(Spacing.xl))

                        val durations = listOf(
                            R.string.duration_10,
                            R.string.duration_20,
                            R.string.duration_30,
                            R.string.duration_no_limit
                        )
                        durations.forEach { durationRes ->
                            val durationText = stringResource(durationRes)
                            OutlinedButton(
                                onClick = {
                                    onProceed(parseDurationMinutes(durationText))
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)
                            ) {
                                Text(durationText.uppercase(), letterSpacing = 1.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.secondary, letterSpacing = 2.sp)
            }
        }
    }
}
