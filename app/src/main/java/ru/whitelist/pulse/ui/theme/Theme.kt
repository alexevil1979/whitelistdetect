package ru.whitelist.pulse.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.whitelist.pulse.domain.model.VerdictKind

private val LightColors = lightColorScheme(
    primary = PulseEmerald,
    onPrimary = Color.White,
    secondary = PulseIndigo,
    tertiary = PulseAmber,
    background = PulsePaper,
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF2F6),
    outline = Color(0xFFD5DEE7),
    onBackground = PulseInk,
    onSurface = PulseInk,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF042F2E),
    secondary = Color(0xFFA5B4FC),
    tertiary = Color(0xFFFBBF24),
    background = Color(0xFF0B1220),
    surface = Color(0xFF111827),
    surfaceVariant = Color(0xFF1F2937),
    outline = Color(0xFF334155),
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
)

val PulseShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PulseTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PulseTypography,
        shapes = PulseShapes,
        content = content,
    )
}

fun VerdictKind.accent(): Color = when (this) {
    VerdictKind.NORMAL -> PulseEmerald
    VerdictKind.WHITELIST_MODE -> PulseAmber
    VerdictKind.NO_INTERNET, VerdictKind.IDLE -> PulseGray
    VerdictKind.VPN_ACTIVE -> PulseIndigo
    VerdictKind.ABROAD_OR_BYPASS -> PulseViolet
    VerdictKind.PARTIAL, VerdictKind.SCANNING -> PulseCoral
}

fun VerdictKind.soft(): Color = when (this) {
    VerdictKind.NORMAL -> PulseEmeraldSoft
    VerdictKind.WHITELIST_MODE -> PulseAmberSoft
    VerdictKind.NO_INTERNET, VerdictKind.IDLE -> PulseGraySoft
    VerdictKind.VPN_ACTIVE -> PulseIndigoSoft
    VerdictKind.ABROAD_OR_BYPASS -> PulseVioletSoft
    VerdictKind.PARTIAL, VerdictKind.SCANNING -> PulseCoralSoft
}
