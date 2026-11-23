package com.eobapp.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eobapp.app.network.PowerDailyDay
import com.eobapp.app.network.PowerDeviceDaily
import com.eobapp.app.network.PowerDeviceStatus
import com.eobapp.app.ui.charts.Legend
import com.eobapp.app.ui.charts.PowerBarChart
import com.eobapp.app.ui.theme.LocalEobColors

private val RANGES = listOf(
    "24h" to 1,
    "48h" to 2,
    "7d" to 7,
    "14d" to 14,
    "30d" to 30,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellyDetailScreen(
    device: PowerDeviceStatus,
    dailyAll: List<PowerDeviceDaily>,
    loading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    LaunchedEffect(Unit) { onRefresh() }

    val c = LocalEobColors.current
    val configName = device.name ?: "Device"
    val title = if (device.model != null) "$configName (${device.model})" else configName

    val days = dailyAll.firstOrNull { it.name == device.name }?.days.orEmpty()

    var selectedNDays by remember { mutableStateOf(7) }

    Scaffold(
        containerColor = c.ground,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.card),
                actions = {
                    if (loading) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
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
                .padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Stat tiles: live W + today's kWh
            val ageStr = if (device.age == null) "no data"
                         else "updated ${duration(device.age)} ago"
            val stale = device.ok == false
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ShellyTile(
                    label = "Now",
                    value = device.powerW?.let { "%.0f W".format(it) } ?: "–",
                    sub = if (stale) "stale" else "live",
                    subColor = if (stale) c.bad else c.mute,
                    modifier = Modifier.weight(1f)
                )
                val todayKwh = days.takeLast(1).sumOf { it.kwh }
                ShellyTile(
                    label = "Today",
                    value = if (days.isEmpty()) "–" else "%.2f kWh".format(todayKwh),
                    sub = ageStr,
                    subColor = if (stale) c.bad else c.mute,
                    modifier = Modifier.weight(1f)
                )
            }

            // Daily usage card with range chips + bar chart
            ShellyCard {
                Text("Daily usage", style = MaterialTheme.typography.titleMedium)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for ((label, nDays) in RANGES) {
                        FilterChip(
                            selected = selectedNDays == nDays,
                            onClick = { selectedNDays = nDays },
                            label = { Text(label) }
                        )
                    }
                }
                PowerBarChart(days.takeLast(selectedNDays))
                Legend(listOf(c.good to "kWh per day"))
            }
        }
    }
}

@Composable
private fun ShellyTile(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
    valueColor: Color = LocalEobColors.current.ink,
    subColor: Color = LocalEobColors.current.mute,
) {
    val c = LocalEobColors.current
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = modifier.fillMaxHeight().clip(shape),
        shape = shape,
        color = c.card,
        border = BorderStroke(1.dp, c.line)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = c.mute
            )
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
private fun ShellyCard(content: @Composable () -> Unit) {
    val c = LocalEobColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = c.card,
        border = BorderStroke(1.dp, c.line)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}
