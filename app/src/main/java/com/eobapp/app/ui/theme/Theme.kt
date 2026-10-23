package com.eobapp.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The same palette as the scheduler's web dashboard, so the app and the page read alike:
 * warm neutrals, one orange for temperature/heating, and green/red reserved for state —
 * heater ON and low tariff are green, OFF and high tariff are red.
 */
@Immutable
data class EobColors(
    val ground: Color,
    val card: Color,
    val ink: Color,
    val mute: Color,
    val line: Color,
    val temp: Color,
    val target: Color,
    /** Chart band: heater on. */
    val onBand: Color,
    /** Chart band: low tariff. */
    val lowBand: Color,
    val good: Color,
    val bad: Color,
    val unknown: Color,
)

private val Light = EobColors(
    ground = Color(0xFFF6F5F2),
    card = Color(0xFFFFFFFF),
    ink = Color(0xFF1D1D1B),
    mute = Color(0xFF6F6D68),
    line = Color(0xFFE3E1DC),
    temp = Color(0xFFC2410C),
    target = Color(0xFF6F6D68),
    onBand = Color(0x29C2410C),   // 16 %
    lowBand = Color(0x1415803D),  // 8 %
    good = Color(0xFF15803D),
    bad = Color(0xFFB91C1C),
    unknown = Color(0xFFA8A29E),
)

private val Dark = EobColors(
    ground = Color(0xFF141413),
    card = Color(0xFF1F1E1C),
    ink = Color(0xFFECEBE7),
    mute = Color(0xFF9B9890),
    line = Color(0xFF33312E),
    temp = Color(0xFFFB923C),
    target = Color(0xFF9B9890),
    onBand = Color(0x2EFB923C),   // 18 %
    lowBand = Color(0x1A4ADE80),  // 10 %
    good = Color(0xFF4ADE80),
    bad = Color(0xFFF87171),
    unknown = Color(0xFF6F6D68),
)

val LocalEobColors = staticCompositionLocalOf { Light }

private val AppTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    // Tile captions: "CURRENT TEMPERATURE".
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium, fontSize = 11.sp,
        lineHeight = 14.sp, letterSpacing = 0.5.sp
    ),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(16.dp),
)

@Composable
fun EobTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) Dark else Light
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = c.ink,
        onPrimary = c.ground,
        background = c.ground,
        onBackground = c.ink,
        surface = c.card,
        onSurface = c.ink,
        surfaceVariant = c.card,
        onSurfaceVariant = c.mute,
        outline = c.line,
        outlineVariant = c.line,
        error = c.bad,
        // Material 3 tints raised surfaces with these; keep cards the dashboard's flat colour.
        surfaceContainer = c.card,
        surfaceContainerHigh = c.card,
        surfaceContainerLow = c.card,
        surfaceContainerLowest = c.card,
        surfaceContainerHighest = c.card,
    )
    CompositionLocalProvider(LocalEobColors provides c) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
