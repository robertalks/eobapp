package com.eobapp.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.eobapp.app.MainViewModel
import com.eobapp.app.RANGES
import com.eobapp.app.TargetEdit
import com.eobapp.app.network.StatusResponse
import com.eobapp.app.ui.charts.Legend
import com.eobapp.app.ui.charts.RuntimeChart
import com.eobapp.app.ui.charts.TemperatureChart
import com.eobapp.app.ui.theme.LocalEobColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val REFRESH_MS = 60_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.home.collectAsState()
    val targetEdit by viewModel.targetEdit.collectAsState()
    val c = LocalEobColors.current
    var showTargetDialog by remember { mutableStateOf(false) }

    // A successful change closes the dialog; its message then shows on Home for a few seconds.
    LaunchedEffect(targetEdit) {
        if (targetEdit is TargetEdit.Done) {
            showTargetDialog = false
            delay(6_000)
            viewModel.clearTargetEdit()
        }
    }

    val control = state.status?.control
    if (showTargetDialog && control != null) {
        TargetDialog(
            control = control,
            hasToken = viewModel.settings.getControlToken().isNotBlank(),
            edit = targetEdit,
            onSet = { viewModel.setTarget(it) },
            onReset = { viewModel.setTarget(null) },
            onOpenSettings = {
                showTargetDialog = false
                onOpenSettings()
            },
            onDismiss = {
                showTargetDialog = false
                viewModel.clearTargetEdit()
            }
        )
    }

    // Refresh every minute, but only while the screen is actually visible — nothing runs in the
    // background, and coming back to the app refreshes straight away.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.refresh()
                delay(REFRESH_MS)
            }
        }
    }

    // Ticks so "updated 2 min ago" and "for 31 min" keep moving between refreshes.
    val nowMs by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(15_000)
            value = System.currentTimeMillis()
        }
    }

    Scaffold(
        containerColor = c.ground,
        topBar = {
            TopAppBar(
                title = { Text("Water heater · BT725", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.ground),
                actions = {
                    if (state.loading) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.error?.let { ErrorCard(it, onRetry = { viewModel.refresh() }) }

            val status = state.status
            if (status == null && state.error == null) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Tiles(
                    status, state.refreshedAt, nowMs,
                    onEditTarget = if (control?.enabled == true) {
                        { viewModel.clearTargetEdit(); showTargetDialog = true }
                    } else null
                )

                (targetEdit as? TargetEdit.Done)?.let { done ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = c.card,
                        border = BorderStroke(1.dp, c.good)
                    ) {
                        Text(
                            done.message,
                            color = c.good,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }

                Card {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Temperature", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (h in RANGES) {
                                FilterChip(
                                    selected = state.rangeHours == h,
                                    onClick = { viewModel.setRange(h) },
                                    label = { Text(if (h < 48) "$h h" else "${h / 24} d") }
                                )
                            }
                        }
                    }
                    TemperatureChart(state.readings, state.interval, state.rangeHours)
                    Legend(listOf(c.temp to "measured", c.target to "target (hidden while off)"))
                    Legend(listOf(c.onBand to "heater on", c.lowBand to "low tariff"))
                }

                Card {
                    Text("Heater runtime per day", style = MaterialTheme.typography.titleMedium)
                    val low = state.daily.sumOf { it.low }
                    val total = state.daily.sumOf { it.total }
                    Text(
                        if (total == 0) "last 14 days: no heating recorded"
                        else "last 14 days: ${duration(total * 60L)}, ${100 * low / total}% in low tariff",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.mute
                    )
                    RuntimeChart(state.daily)
                    Legend(listOf(c.good to "low tariff", c.bad to "high tariff", c.unknown to "unknown"))
                }

                state.refreshedAt?.let {
                    Text(
                        "Updated " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it)) +
                            " · " + viewModel.settings.getHost(),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.mute,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

/** The four status tiles, same wording and colours as the web dashboard. */
@Composable
private fun Tiles(s: StatusResponse?, refreshedAt: Long?, nowMs: Long, onEditTarget: (() -> Unit)?) {
    val c = LocalEobColors.current
    val hasReading = s?.ok == true && s.measured != null
    val nowSec = nowMs / 1000

    // Age of the reading = what the server said + time since we asked.
    val serverAge: Long? = s?.age
    val age: Long? = if (hasReading && serverAge != null && refreshedAt != null) {
        serverAge + (nowMs - refreshedAt) / 1000
    } else null

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile(
            label = "Current temperature",
            value = if (hasReading) "%.1f °C".format(s?.measured) else "–",
            sub = when {
                !hasReading -> "no readings yet"
                age == null -> ""
                else -> "updated ${duration(age)} ago"
            },
            subColor = if (age != null && age > 300) c.bad else c.mute,
            modifier = Modifier.weight(1f)
        )
        val target = s?.desired
        val lowTarget = s?.control?.target
        val forcedOff = hasReading && target != null && target <= 0
        Tile(
            label = "Target temperature",
            value = when {
                !hasReading || target == null -> "–"
                target > 0 -> "%.1f °C".format(target)
                else -> "OFF"
            },
            valueColor = if (forcedOff) c.bad else c.ink,
            sub = when {
                // During high tariff, say what the heater will run at when it comes back.
                forcedOff && lowTarget != null -> "low tariff: %.1f °C".format(lowTarget)
                s?.mode == "off" -> "forced off"
                s?.control?.source == "app" -> "set from app"
                else -> ""
            },
            onClick = onEditTarget,
            modifier = Modifier.weight(1f)
        )
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val relay = if (hasReading) s?.relay else null
        Tile(
            label = "Heater status",
            value = when (relay) { true -> "ON"; false -> "OFF"; null -> "–" },
            valueColor = when (relay) { true -> c.good; false -> c.bad; null -> c.ink },
            sub = if (hasReading) s?.since?.let { "for ${duration(nowSec - it)}" } ?: "" else "",
            modifier = Modifier.weight(1f)
        )
        val low = s?.lowTariff
        val nextTs: Long? = s?.nextTariffChange?.ts
        val nextToLow: Boolean? = s?.nextTariffChange?.toLow
        Tile(
            label = "Electricity tariff",
            value = when (low) { true -> "LOW"; false -> "HIGH"; null -> "–" },
            valueColor = when (low) { true -> c.good; false -> c.bad; null -> c.ink },
            sub = if (nextTs != null && nextToLow != null) {
                "${if (nextToLow) "low" else "high"} from ${clockTime(nextTs)}"
            } else "",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun Tile(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
    valueColor: Color = LocalEobColors.current.ink,
    subColor: Color = LocalEobColors.current.mute,
    onClick: (() -> Unit)? = null,
) {
    val c = LocalEobColors.current
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = c.card,
        border = BorderStroke(1.dp, c.line)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.mute,
                    modifier = Modifier.weight(1f)
                )
                if (onClick != null) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Change",
                        tint = c.mute,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = valueColor
            )
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = subColor)
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val c = LocalEobColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = c.card,
        border = BorderStroke(1.dp, c.line)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    val c = LocalEobColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = c.card,
        border = BorderStroke(1.dp, c.bad)
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = c.bad, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

/** "31 min", "2 h 5 min" — same wording as the web dashboard. */
fun duration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    return if (h > 0) "$h h $m min" else "$m min"
}

/** "20:35", or "Sat 03:36" when it isn't today. */
private fun clockTime(epochSec: Long): String {
    val then = Calendar.getInstance().apply { timeInMillis = epochSec * 1000 }
    val today = Calendar.getInstance()
    val sameDay = then.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        then.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "HH:mm" else "EEE HH:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(epochSec * 1000))
}
