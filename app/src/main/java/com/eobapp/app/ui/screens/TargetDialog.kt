package com.eobapp.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eobapp.app.TargetEdit
import com.eobapp.app.network.ControlInfo
import com.eobapp.app.ui.theme.LocalEobColors
import kotlin.math.roundToInt

/**
 * Pick a new low-tariff target with − / + in the scheduler's step (0.5 °C), within its range.
 * The scheduler decides when it takes effect: now during low tariff, at the next low tariff
 * otherwise — the dialog says so rather than pretending the heater will start.
 */
@Composable
fun TargetDialog(
    control: ControlInfo,
    hasToken: Boolean,
    edit: TargetEdit?,
    onSet: (Double) -> Unit,
    onReset: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalEobColors.current
    val min = control.min ?: 5.0
    val max = control.max ?: 39.0
    val step = control.step?.takeIf { it > 0 } ?: 0.5
    fun snap(v: Double): Double = ((v / step).roundToInt() * step).coerceIn(min, max)

    var value by remember { mutableStateOf(snap(control.target ?: 15.0)) }
    val saving = edit is TargetEdit.Saving

    if (!hasToken) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Control token needed") },
            text = {
                Text(
                    "To change the temperature, add the control token from eob_scheduler's " +
                        "config.yaml (control → token) in Settings.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = { TextButton(onClick = onOpenSettings) { Text("Open settings") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Target temperature") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { value = snap(value - step) },
                        enabled = !saving && value > min
                    ) { Text("−", fontSize = 22.sp) }
                    Text(
                        "%.1f °C".format(value),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(130.dp)
                    )
                    OutlinedButton(
                        onClick = { value = snap(value + step) },
                        enabled = !saving && value < max
                    ) { Text("+", fontSize = 22.sp) }
                }
                Text(
                    "Used for every low-tariff period. During high tariff the heater stays off " +
                        "and the new target applies from the next low tariff.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.mute
                )
                when (edit) {
                    is TargetEdit.Saving -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("  Sending…", style = MaterialTheme.typography.bodyMedium)
                    }
                    is TargetEdit.Failed -> Text(edit.message, color = c.bad, style = MaterialTheme.typography.bodyMedium)
                    else -> {}
                }
                if (control.source == "app") {
                    TextButton(onClick = onReset, enabled = !saving) {
                        Text("Reset to scheduler default")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(value) }, enabled = !saving) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}
