package com.eobapp.app.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eobapp.app.OverrideEdit
import com.eobapp.app.network.ControlInfo
import com.eobapp.app.ui.theme.LocalEobColors
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val timeFmt = DateTimeFormatter.ofPattern("EEE HH:mm")
private val timeOnlyFmt = DateTimeFormatter.ofPattern("HH:mm")

private fun LocalDateTime.toEpochSecond(): Long =
    atZone(ZoneId.systemDefault()).toEpochSecond()

private fun LocalDateTime.isToday(): Boolean {
    val now = LocalDateTime.now()
    return year == now.year && dayOfYear == now.dayOfYear
}

private fun LocalDateTime.label(): String =
    if (isToday()) format(timeOnlyFmt) else format(timeFmt)

@Composable
fun OverrideDialog(
    control: ControlInfo,
    hasToken: Boolean,
    edit: OverrideEdit?,
    onSet: (celsius: Double, startTs: Long?, endTs: Long) -> Unit,
    onClear: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalEobColors.current
    val context = LocalContext.current
    val min = control.min ?: 5.0
    val max = control.max ?: 39.0
    val step = control.step?.takeIf { it > 0 } ?: 0.5
    fun snap(v: Double): Double = ((v / step).roundToInt() * step).coerceIn(min, max)

    val defaultTemp = snap(control.override?.temp ?: control.target ?: 15.0)
    var value by remember { mutableStateOf(defaultTemp) }

    // Pre-populate from existing override if one is set.
    val existingStart = control.override?.start
    val existingEnd = control.override?.end
    val zone = ZoneId.systemDefault()

    var startNow by remember {
        mutableStateOf(existingStart == null || control.override?.active == true)
    }
    val defaultStart = if (existingStart != null && control.override?.active != true) {
        java.time.Instant.ofEpochSecond(existingStart).atZone(zone).toLocalDateTime()
    } else {
        LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
    }
    var startDt by remember { mutableStateOf(defaultStart) }

    val defaultEnd = if (existingEnd != null) {
        java.time.Instant.ofEpochSecond(existingEnd).atZone(zone).toLocalDateTime()
    } else {
        LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0)
    }
    var endDt by remember { mutableStateOf(defaultEnd) }

    val saving = edit is OverrideEdit.Saving

    if (!hasToken) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Control token needed") },
            text = {
                Text(
                    "To schedule an override, add the control token from eob_scheduler's " +
                        "config.yaml (control → token) in Settings.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = { TextButton(onClick = onOpenSettings) { Text("Open settings") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
        return
    }

    fun showDateTimePicker(initial: LocalDateTime, onPicked: (LocalDateTime) -> Unit) {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        onPicked(LocalDateTime.of(year, month + 1, day, hour, minute, 0))
                    },
                    initial.hour, initial.minute, true
                ).show()
            },
            initial.year, initial.monthValue - 1, initial.dayOfMonth
        ).show()
    }

    val summaryStart = if (startNow) "now" else startDt.label()
    val summaryEnd = endDt.label()

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (control.override != null) "Edit override" else "Schedule override") },
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

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("From", style = MaterialTheme.typography.bodyMedium, color = c.mute)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { startNow = true },
                            enabled = !saving && !startNow
                        ) { Text("Now") }
                        OutlinedButton(
                            onClick = {
                                showDateTimePicker(startDt) { startDt = it; startNow = false }
                            },
                            enabled = !saving
                        ) { Text(if (startNow) "Pick time" else startDt.label()) }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Until", style = MaterialTheme.typography.bodyMedium, color = c.mute)
                    OutlinedButton(
                        onClick = { showDateTimePicker(endDt) { endDt = it } },
                        enabled = !saving
                    ) { Text(endDt.label()) }
                }

                Text(
                    "%.1f °C from $summaryStart until $summaryEnd".format(value),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.mute
                )

                Text(
                    "Only affects low-tariff periods. The heater still stays off during high tariff.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.mute
                )

                when (edit) {
                    is OverrideEdit.Saving -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("  Sending…", style = MaterialTheme.typography.bodyMedium)
                    }
                    is OverrideEdit.Failed -> Text(
                        edit.message, color = c.bad, style = MaterialTheme.typography.bodyMedium
                    )
                    else -> {}
                }

                if (control.override != null) {
                    Spacer(Modifier.height(0.dp))
                    TextButton(onClick = onClear, enabled = !saving) {
                        Text("Clear current override")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val startTs = if (startNow) null else startDt.toEpochSecond()
                    onSet(value, startTs, endDt.toEpochSecond())
                },
                enabled = !saving
            ) { Text("Set override") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}
