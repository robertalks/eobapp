package com.eobapp.app.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eobapp.app.network.DailyRuntime
import com.eobapp.app.network.Reading
import com.eobapp.app.ui.theme.LocalEobColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** 1, 2, 5 × 10ⁿ — round axis steps. */
private fun niceStep(raw: Double): Double {
    if (raw <= 0.0) return 1.0
    val m = 10.0.pow(floor(log10(raw)))
    val f = raw / m
    return (if (f <= 1) 1.0 else if (f <= 2) 2.0 else if (f <= 5) 5.0 else 10.0) * m
}

private fun fmtAxis(v: Double): String =
    if (v == floor(v)) v.toLong().toString() else "%.1f".format(v)

/**
 * Temperature over the chosen range: measured (solid), target (dashed, hidden while the
 * scheduler has the thermostat forced off), heater-on periods and low-tariff periods as bands.
 * Same drawing rules as the web dashboard.
 */
@Composable
fun TemperatureChart(
    readings: List<Reading>,
    interval: Int,
    hours: Int,
    modifier: Modifier = Modifier,
) {
    val c = LocalEobColors.current
    val tm = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = c.mute)
    // "Now" is pinned per data set, so the chart doesn't drift between refreshes.
    val now = remember(readings, hours) { System.currentTimeMillis() / 1000 }
    val hourFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dayFmt = remember { SimpleDateFormat("EEE d", Locale.getDefault()) }

    Canvas(modifier.fillMaxWidth().height(220.dp)) {
        val left = 34.dp.toPx()
        val right = 6.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = 22.dp.toPx()
        val w = size.width
        val h = size.height
        val t0 = now - hours * 3600L

        if (readings.isEmpty()) {
            val lay = tm.measure("No readings in this range", labelStyle)
            drawText(lay, topLeft = Offset((w - lay.size.width) / 2f, (h - lay.size.height) / 2f))
            return@Canvas
        }

        var lo = Double.MAX_VALUE
        var hi = -Double.MAX_VALUE
        for (r in readings) {
            lo = min(lo, r.measured); hi = max(hi, r.measured)
            if (r.target > 0) { lo = min(lo, r.target); hi = max(hi, r.target) }
        }
        val step = niceStep((hi - lo + 2) / 5)
        lo = floor((lo - 1) / step) * step
        hi = ceil((hi + 1) / step) * step

        val span = (now - t0).toFloat()
        fun x(t: Long): Float = left + (t - t0).toFloat() / span * (w - left - right)
        fun y(v: Double): Float = top + ((hi - v) / (hi - lo)).toFloat() * (h - top - bottom)
        val gap = 3L * interval

        // Bands first, so lines and grid sit on top of them.
        fun bands(flag: (Reading) -> Boolean, color: Color) {
            var k = 0
            while (k < readings.size) {
                if (!flag(readings[k])) { k++; continue }
                var j = k
                while (j + 1 < readings.size && flag(readings[j + 1]) &&
                    readings[j + 1].ts - readings[j].ts <= gap
                ) j++
                val x0 = x(max(readings[k].ts, t0))
                val x1 = x(min(readings[j].ts + interval, now))
                drawRect(color, topLeft = Offset(x0, top), size = Size(max(1f, x1 - x0), h - top - bottom))
                k = j + 1
            }
        }
        bands({ it.lowTariff == true }, c.lowBand)
        bands({ it.relay }, c.onBand)

        // Horizontal grid + temperature labels.
        var v = lo
        while (v <= hi + 1e-9) {
            val yy = y(v)
            drawLine(c.line, Offset(left, yy), Offset(w - right, yy), 1.dp.toPx())
            val lay = tm.measure(fmtAxis(v), labelStyle)
            drawText(lay, topLeft = Offset(left - 5.dp.toPx() - lay.size.width, yy - lay.size.height / 2f))
            v += step
        }

        // Time labels on local clock boundaries (every 1 h / 3–6 h / day).
        val narrow = w < 600.dp.toPx()
        val stepHours = when {
            hours <= 6 -> 1
            hours <= 24 -> if (narrow) 6 else 3
            else -> 24
        }
        val cal = Calendar.getInstance().apply {
            timeInMillis = t0 * 1000
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (stepHours == 24) {
                set(Calendar.HOUR_OF_DAY, 0); add(Calendar.DAY_OF_MONTH, 1)
            } else {
                add(Calendar.HOUR_OF_DAY, 1)
                while (get(Calendar.HOUR_OF_DAY) % stepHours != 0) add(Calendar.HOUR_OF_DAY, 1)
            }
        }
        while (cal.timeInMillis / 1000 < now) {
            val tt = cal.timeInMillis / 1000
            val text = (if (stepHours == 24) dayFmt else hourFmt).format(Date(cal.timeInMillis))
            val lay = tm.measure(text, labelStyle)
            val lx = (x(tt) - lay.size.width / 2f).coerceIn(0f, w - lay.size.width)
            drawText(lay, topLeft = Offset(lx, h - lay.size.height))
            if (stepHours == 24) cal.add(Calendar.DAY_OF_MONTH, 1) else cal.add(Calendar.HOUR_OF_DAY, stepHours)
        }

        // Lines: break wherever readings are missing for more than a few intervals.
        fun path(include: (Reading) -> Boolean, value: (Reading) -> Double): Path {
            val p = Path()
            var prev: Long? = null
            for (r in readings) {
                if (!include(r)) { prev = null; continue }
                val px = x(r.ts); val py = y(value(r))
                if (prev == null || r.ts - prev > gap) p.moveTo(px, py) else p.lineTo(px, py)
                prev = r.ts
            }
            return p
        }
        drawPath(
            path({ it.target > 0 }, { it.target }),
            color = c.target,
            style = Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
            )
        )
        drawPath(
            path({ true }, { it.measured }),
            color = c.temp,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Heater runtime per day as stacked bars: low tariff (green), high tariff (red), unknown (grey).
 */
@Composable
fun RuntimeChart(days: List<DailyRuntime>, modifier: Modifier = Modifier) {
    val c = LocalEobColors.current
    val tm = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = c.mute)

    Canvas(modifier.fillMaxWidth().height(180.dp)) {
        val left = 30.dp.toPx()
        val right = 6.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = 22.dp.toPx()
        val w = size.width
        val h = size.height
        val n = days.size
        if (n == 0) return@Canvas

        val maxTotal = max(60, days.maxOf { it.total })
        val topMinutes = ceil(maxTotal / 60.0) * 60.0
        val bw = (w - left - right) / n
        fun y(m: Double): Float = top + (1 - m / topMinutes).toFloat() * (h - top - bottom)

        val gridHours = max(1, ceil(topMinutes / 60.0 / 4.0).toInt())
        var hh = 0
        while (hh * 60 <= topMinutes) {
            val yy = y(hh * 60.0)
            drawLine(c.line, Offset(left, yy), Offset(w - right, yy), 1.dp.toPx())
            val lay = tm.measure("${hh}h", labelStyle)
            drawText(lay, topLeft = Offset(left - 5.dp.toPx() - lay.size.width, yy - lay.size.height / 2f))
            hh += gridHours
        }

        val showEvery = if (bw >= 34.dp.toPx()) 1 else 2
        days.forEachIndexed { i, d ->
            var base = 0.0
            for ((minutes, color) in listOf(d.low to c.good, d.high to c.bad, d.unknown to c.unknown)) {
                if (minutes <= 0) continue
                val yTop = y(base + minutes)
                drawRect(
                    color,
                    topLeft = Offset(left + i * bw + bw * 0.18f, yTop),
                    size = Size(bw * 0.64f, y(base) - yTop)
                )
                base += minutes
            }
            if ((n - 1 - i) % showEvery == 0 && d.day.length >= 10) {
                val text = "${d.day.substring(8, 10).toInt()}.${d.day.substring(5, 7).toInt()}."
                val lay = tm.measure(text, labelStyle)
                drawText(lay, topLeft = Offset(left + i * bw + (bw - lay.size.width) / 2f, h - lay.size.height))
            }
        }
    }
}

/** A row of colour swatches with captions, under a chart. */
@Composable
fun Legend(items: List<Pair<Color, String>>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        for ((color, label) in items) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 12.dp, height = 10.dp)
                        .background(color, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(5.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, color = LocalEobColors.current.mute)
            }
        }
    }
}
