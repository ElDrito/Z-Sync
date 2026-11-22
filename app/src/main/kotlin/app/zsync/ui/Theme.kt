package app.zsync.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.compositeOver
import app.zsync.Characters
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val Dark = darkColorScheme(
    primary = Color(0xFFD4F03C), onPrimary = Color(0xFF14160A),
    primaryContainer = Color(0xFF2A2F12), onPrimaryContainer = Color(0xFFE6F78F),
    secondaryContainer = Color(0xFF232326), onSecondaryContainer = Color(0xFFEDEDED),
    background = Color.Black, onBackground = Color(0xFFEDEDED),
    surface = Color.Black, onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF121214), onSurfaceVariant = Color(0xFF9B9BA3),
    surfaceContainer = Color(0xFF121214), surfaceContainerHigh = Color(0xFF19191C), surfaceContainerHighest = Color(0xFF202024),
    surfaceContainerLow = Color(0xFF0B0B0C),
    outline = Color(0xFF3A3A40), outlineVariant = Color(0xFF202024),
    error = Color(0xFFFF7A6B), onError = Color(0xFF2B0B07),
)

private val Light = lightColorScheme(
    primary = Color(0xFF4F5E00), onPrimary = Color.White,
    primaryContainer = Color(0xFFE4F29A), onPrimaryContainer = Color(0xFF1A1F00),
    secondaryContainer = Color(0xFFECECEC), onSecondaryContainer = Color(0xFF1B1B1D),
    background = Color(0xFFF7F7F5), onBackground = Color(0xFF1B1B1D),
    surface = Color(0xFFF7F7F5), onSurface = Color(0xFF1B1B1D),
    surfaceVariant = Color(0xFFFFFFFF), onSurfaceVariant = Color(0xFF66666C),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFF0F0EE), surfaceContainerHighest = Color(0xFFE9E9E7),
    surfaceContainerLow = Color(0xFFFFFFFF),
    outline = Color(0xFFC9C9C9), outlineVariant = Color(0xFFE3E3E1),
    error = Color(0xFFB3261E),
)

private val isDark @Composable get() = MaterialTheme.colorScheme.background.luminance() < 0.5f
val StatusRed @Composable get() = if (isDark) Color(0xFFFF8A7A) else Color(0xFFB3372C)
val StatusOrange @Composable get() = if (isDark) Color(0xFFFFB86B) else Color(0xFFB85C00)
val StatusYellow @Composable get() = if (isDark) Color(0xFFF5D76E) else Color(0xFF8A6A00)
val StatusGreen @Composable get() = if (isDark) Color(0xFF7EDC9A) else Color(0xFF2E7D4F)

@Composable
fun progressColor(ratio: Float): Color = when {
    ratio >= 1f -> StatusGreen
    ratio >= 2f / 3f -> StatusYellow
    ratio >= 1f / 3f -> StatusOrange
    else -> StatusRed
}

@Composable
fun batteryColor(ratio: Float): Color = when {
    ratio >= 1f -> StatusRed
    ratio >= 0.75f -> StatusOrange
    ratio >= 0.5f -> StatusYellow
    else -> StatusGreen
}

val OkColor @Composable get() = StatusGreen
val WarnColor @Composable get() = StatusOrange

private fun shadowed(dark: Boolean): Typography {
    // A soft dark shadow under every text style keeps text readable over the backdrop art.
    val sh = Shadow(Color.Black.copy(alpha = if (dark) 0.45f else 0.12f), Offset(0f, 1f), blurRadius = 4f)
    fun TextStyle.s() = copy(shadow = sh)
    return with(Typography()) {
        Typography(
            displayLarge.s(), displayMedium.s(), displaySmall.s(), headlineLarge.s(), headlineMedium.s(), headlineSmall.s(),
            titleLarge.s(), titleMedium.s(), titleSmall.s(), bodyLarge.s(), bodyMedium.s(), bodySmall.s(),
            labelLarge.s(), labelMedium.s(), labelSmall.s(),
        )
    }
}
private val DarkType = shadowed(true)
private val LightType = shadowed(false)

private val LimeFallback = Color(0xFFD4F03C)
private val LightBg = Color(0xFFF7F7F5)

private fun Color.hsl(): FloatArray {
    val mx = maxOf(red, green, blue); val mn = minOf(red, green, blue)
    val l = (mx + mn) / 2f; val d = mx - mn
    if (d == 0f) return floatArrayOf(0f, 0f, l)
    val sat = d / (1f - kotlin.math.abs(2f * l - 1f))
    val h = when (mx) {
        red -> ((green - blue) / d).mod(6f)
        green -> (blue - red) / d + 2f
        else -> (red - green) / d + 4f
    } * 60f
    return floatArrayOf(h, sat, l)
}

private fun hslColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val hp = (h.mod(360f)) / 60f
    val x = c * (1f - kotlin.math.abs(hp.mod(2f) - 1f))
    val (r, g, b) = when (hp.toInt()) {
        0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color(r + m, g + m, b + m)
}

private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance(); val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

fun accentFor(bits: LongArray, dark: Boolean): Color {
    var best: FloatArray? = null
    var bestScore = 0f
    for (b in bits) {
        val hsl = Color(b.toInt()).hsl()
        val score = hsl[1] * (1f - kotlin.math.abs(2f * hsl[2] - 1f))
        if (score > bestScore) { bestScore = score; best = hsl }
    }
    if (best == null || best[1] < 0.12f) return LimeFallback
    val h = best[0]
    val s = best[1].coerceIn(0.6f, 0.95f)
    val bg = if (dark) Color.Black else LightBg
    var l = if (dark) best[2].coerceIn(0.65f, 0.70f) else best[2].coerceIn(0.38f, 0.42f)
    var c = hslColor(h, s, l)
    while (contrast(c, bg) < 4.5f && l in 0.1f..0.9f) {
        l += if (dark) 0.01f else -0.01f
        c = hslColor(h, s, l)
    }
    return c
}

private fun onAccent(c: Color): Color {
    val dk = Color(0xFF14160A)
    return if (contrast(c, dk) >= contrast(c, Color.White)) dk else Color.White
}

private fun schemeFor(accent: Color, dark: Boolean): ColorScheme {
    val base = if (dark) Dark else Light
    val hsl = accent.hsl()
    val neutral = if (dark) Color(0xFF9B9BA3) else Color(0xFF66666C)
    val secondary = lerp(accent, neutral, 0.45f)
    val tertiary = hslColor(hsl[0] + 40f, hsl[1], hsl[2])
    return base.copy(
        primary = accent, onPrimary = onAccent(accent),
        primaryContainer = if (dark) lerp(Color.Black, accent, 0.25f) else lerp(LightBg, accent, 0.25f),
        onPrimaryContainer = if (dark) lerp(accent, Color.White, 0.55f) else lerp(accent, Color.Black, 0.65f),
        secondary = secondary, onSecondary = onAccent(secondary),
        tertiary = tertiary, onTertiary = onAccent(tertiary),
        surfaceTint = accent,
    )
}

@Composable
fun AppTheme(iconKey: String, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val target = remember(iconKey, dark) { accentFor(Characters.byKey(iconKey).bits, dark) }
    val accent by animateColorAsState(target, tween(400), label = "accent")
    MaterialTheme(colorScheme = schemeFor(accent, dark), typography = if (dark) DarkType else LightType, content = content)
}
