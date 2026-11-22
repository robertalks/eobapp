package com.eobapp.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eobapp.app.network.PowerDailyDay
import com.eobapp.app.network.PowerDeviceDaily
import com.eobapp.app.network.PowerDeviceStatus
import com.eobapp.app.ui.theme.LocalEobColors

private val PERIODS = listOf(
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
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            // Total kWh — large, centred
            val totalStr = device.totalKwh?.let { kwh ->
                if (kwh >= 1.0) "%.2f kWh".format(kwh) else "%.0f Wh".format(kwh * 1000)
            } ?: "– kWh"
            Text(
                totalStr,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                color = c.ink,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            Text(
                "DAILY USAGE",
                style = MaterialTheme.typography.labelSmall,
                color = c.mute,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            PERIODS.forEachIndexed { i, (label, nDays) ->
                if (i > 0) HorizontalDivider(color = c.line)
                val kwh = sumDays(days, nDays)
                PeriodRow(label = label, kwh = if (days.isEmpty()) null else kwh)
            }
        }
    }
}

@Composable
private fun PeriodRow(label: String, kwh: Double?) {
    val c = LocalEobColors.current
    val valueStr = kwh?.let { "%.2f kWh".format(it) } ?: "–"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.ink)
        Text(
            valueStr,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = c.ink,
        )
    }
}

private fun sumDays(days: List<PowerDailyDay>, n: Int): Double =
    days.takeLast(n).sumOf { it.kwh }
