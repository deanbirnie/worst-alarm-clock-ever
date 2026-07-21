package com.worstalarm.clock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sunrise palette: warm ambers, golds, and dusky roses over deep warm browns.
 * Deliberately muted — this is what you see at 6 AM with half-open eyes, so
 * nothing here should sear the retinas.
 */
private val SunriseDark = darkColorScheme(
    primary = Color(0xFFF5A05C),           // soft sunrise orange
    onPrimary = Color(0xFF3B1C02),
    primaryContainer = Color(0xFF6B3A16),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFE0B368),         // early-morning gold
    onSecondary = Color(0xFF3B2A00),
    secondaryContainer = Color(0xFF564314),
    onSecondaryContainer = Color(0xFFFDDF9E),
    tertiary = Color(0xFFD98A7B),          // dusky rose
    onTertiary = Color(0xFF44110B),
    background = Color(0xFF1E1410),        // deep warm brown, not black
    onBackground = Color(0xFFF0E0D0),
    surface = Color(0xFF271A14),
    onSurface = Color(0xFFF0E0D0),
    surfaceVariant = Color(0xFF3B2B21),
    onSurfaceVariant = Color(0xFFD6BCA8),
    outline = Color(0xFF9E8572),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val SunriseLight = lightColorScheme(
    primary = Color(0xFFAB5327),           // burnt sienna
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBC8),
    onPrimaryContainer = Color(0xFF380D00),
    secondary = Color(0xFF7A5C22),         // ochre
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFCDFA6),
    onSecondaryContainer = Color(0xFF271900),
    tertiary = Color(0xFF8E4A40),          // clay rose
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFAF0E4),        // soft warm cream, dimmed from white
    onBackground = Color(0xFF241A12),
    surface = Color(0xFFF5E8D8),
    onSurface = Color(0xFF241A12),
    surfaceVariant = Color(0xFFEEDCC6),
    onSurfaceVariant = Color(0xFF554434),
    outline = Color(0xFF877461),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

/**
 * Softly rounded corner system (v0.4 redesign): cards and dialogs get generous
 * radii so the whole app reads as warm and pillowy rather than boxy — fitting
 * for something you squint at half-asleep.
 */
private val SunriseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * Stock M3 (Roboto) type scale with a few styles given more presence: the
 * alarm-list clock time gets weight so it anchors each card, and title styles
 * firm up slightly so section headers read as structure, not just text.
 */
private val SunriseTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp
        ),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun WorstAlarmTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) SunriseDark else SunriseLight
    MaterialTheme(
        colorScheme = scheme,
        shapes = SunriseShapes,
        typography = SunriseTypography,
        content = content
    )
}
