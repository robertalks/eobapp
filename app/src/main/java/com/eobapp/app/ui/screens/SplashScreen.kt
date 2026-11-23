package com.eobapp.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eobapp.app.ui.theme.LocalEobColors

// Spark positions: (x offset from flame centre, base y, horizontal drift)
private val SPARKS = listOf(
    Triple(-0.08f, 0.00f,  0.06f),
    Triple( 0.10f, 0.05f, -0.05f),
    Triple(-0.03f, 0.10f,  0.08f),
    Triple( 0.14f, 0.00f, -0.03f),
    Triple(-0.12f, 0.08f,  0.04f),
    Triple( 0.02f, 0.03f, -0.07f),
    Triple( 0.09f, 0.12f,  0.05f),
)

// Duration offsets so sparks don't all rise in sync
private val SPARK_DELAYS = listOf(0, 300, 600, 900, 150, 450, 750)
private val SPARK_DURATIONS = listOf(900, 1100, 800, 1000, 950, 850, 1050)

@Composable
fun AppSplashScreen() {
    val c = LocalEobColors.current

    // One animator per spark, staggered
    val infinite = rememberInfiniteTransition(label = "sparks")
    val sparkProgress = SPARKS.indices.map { i ->
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = SPARK_DURATIONS[i],
                    delayMillis = SPARK_DELAYS[i],
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Restart,
            ),
            label = "spark$i",
        )
    }

    // Flame flicker: scale the flame body slightly
    val flicker by infinite.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "flicker",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(c.ground),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF1F1E1C)),
                contentAlignment = Alignment.Center,
            ) {
                val progValues = sparkProgress.map { it.value }
                Canvas(Modifier.size(90.dp)) {
                    drawHeaterIcon(flicker, progValues)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "EOBApp",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = c.ink,
            )
        }
    }
}

/**
 * Draws the water-heater tank outline, the static flame body, and rising spark particles.
 * Coordinate system matches ic_launcher_foreground: 108×108 viewport mapped to [size].
 */
private fun DrawScope.drawHeaterIcon(flicker: Float, sparkProgress: List<Float>) {
    val s = size.width / 108f   // scale factor

    val stroke = Stroke(width = 3f * s, cap = StrokeCap.Round)
    val outlineColor = Color(0xFFECEBE7)
    val flameOrange = Color(0xFFFB923C)
    val flameYellow = Color(0xFFFDE68A)
    val sparkColor   = Color(0xFFFBBF24)

    // Tank body
    val tankPath = Path().apply {
        moveTo(40f * s, 30f * s)
        quadraticBezierTo(40f * s, 26f * s, 44f * s, 26f * s)
        lineTo(64f * s, 26f * s)
        quadraticBezierTo(68f * s, 26f * s, 68f * s, 30f * s)
        lineTo(68f * s, 76f * s)
        quadraticBezierTo(68f * s, 80f * s, 64f * s, 80f * s)
        lineTo(44f * s, 80f * s)
        quadraticBezierTo(40f * s, 80f * s, 40f * s, 76f * s)
        close()
    }
    drawPath(tankPath, color = outlineColor, style = stroke)

    // Pipes top
    drawLine(outlineColor, Offset(48f * s, 26f * s), Offset(48f * s, 21f * s), strokeWidth = 3f * s, cap = StrokeCap.Round)
    drawLine(outlineColor, Offset(60f * s, 26f * s), Offset(60f * s, 21f * s), strokeWidth = 3f * s, cap = StrokeCap.Round)

    // Legs bottom
    drawLine(outlineColor, Offset(45f * s, 80f * s), Offset(45f * s, 85f * s), strokeWidth = 3f * s, cap = StrokeCap.Round)
    drawLine(outlineColor, Offset(63f * s, 80f * s), Offset(63f * s, 85f * s), strokeWidth = 3f * s, cap = StrokeCap.Round)

    // Flame body (flickering scale around its centre at 54,53)
    val flameCx = 54f * s
    val flameCy = 53f * s
    withTransform({ scale(1f, flicker, pivot = Offset(flameCx, flameCy)) }) {
        val flamePath = Path().apply {
            moveTo(54f * s, 40f * s)
            cubicTo(58f * s, 46f * s, 62f * s, 50f * s, 62f * s, 57f * s)
            cubicTo(62f * s, 62f * s, 58.5f * s, 66f * s, 54f * s, 66f * s)
            cubicTo(49.5f * s, 66f * s, 46f * s, 62f * s, 46f * s, 57f * s)
            cubicTo(46f * s, 53f * s, 48f * s, 50.5f * s, 50f * s, 48.5f * s)
            cubicTo(50f * s, 52f * s, 51.5f * s, 54f * s, 53.5f * s, 54.5f * s)
            cubicTo(53f * s, 50f * s, 52.5f * s, 45f * s, 54f * s, 40f * s)
            close()
        }
        drawPath(flamePath, color = flameOrange)

        val innerPath = Path().apply {
            moveTo(54f * s, 56f * s)
            cubicTo(56f * s, 58.5f * s, 57.5f * s, 60f * s, 57.5f * s, 62f * s)
            cubicTo(57.5f * s, 64f * s, 56f * s, 65.5f * s, 54f * s, 65.5f * s)
            cubicTo(52f * s, 65.5f * s, 50.5f * s, 64f * s, 50.5f * s, 62f * s)
            cubicTo(50.5f * s, 60f * s, 52f * s, 58.5f * s, 54f * s, 56f * s)
            close()
        }
        drawPath(innerPath, color = flameYellow)
    }

    // Rising sparks — each travels from just above the flame tip upward
    val sparkBaseX = 54f * s
    val sparkBaseY = 42f * s   // near the flame tip
    val riseRange  = 18f * s   // how far up each spark travels

    SPARKS.forEachIndexed { i, (dxFrac, startFrac, driftFrac) ->
        val p = sparkProgress.getOrElse(i) { 0f }
        // Ease in start so sparks don't all pop at y=base simultaneously
        val adjusted = (p - startFrac).coerceAtLeast(0f) / (1f - startFrac).coerceAtLeast(0.01f)

        val x = sparkBaseX + dxFrac * riseRange + driftFrac * riseRange * adjusted
        val y = sparkBaseY - adjusted * riseRange

        // Alpha: fade in quickly, linger, then fade out near the top
        val alpha = when {
            adjusted < 0.15f -> adjusted / 0.15f
            adjusted > 0.7f  -> 1f - (adjusted - 0.7f) / 0.3f
            else             -> 1f
        }.coerceIn(0f, 1f)

        // Radius shrinks as spark rises (cools down)
        val radius = (2.4f - 1.4f * adjusted) * s

        if (alpha > 0f && radius > 0f) {
            drawCircle(
                color = sparkColor.copy(alpha = alpha),
                radius = radius,
                center = Offset(x, y),
            )
        }
    }
}
