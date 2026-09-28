package se.eldebosh.nastastopp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Visual identity "Night transit" (release 1.0): a calm dark blue-grey base that is easy on the
 * eyes at night, one sky-blue brand colour for actions, warm amber for trip times, and green /
 * red only for status. Cards are soft surfaces with a hairline border; buttons are compact (48 dp).
 */

/** Brand colour: actions, selected items, the app name. */
val Brand = Color(0xFF6EA8FF)

/** Trip times (read at a glance). */
val TimeColor = Color(0xFFFFC56B)

/** Status: located / on time / done. */
val Located = Color(0xFF4ADE80)

/** Status: not found / late / problem. */
val NotLocated = Color(0xFFF87171)

/** Status: soon. */
val Warning = Color(0xFFFBBF24)

/** Hairline borders of cards. */
val Hairline = Color(0xFF243044)

private val NightColors = darkColorScheme(
    primary = Brand,
    onPrimary = Color(0xFF06142B),
    primaryContainer = Color(0xFF16305A),
    onPrimaryContainer = Color(0xFFD6E6FF),
    secondary = Color(0xFF5BE0C2),
    onSecondary = Color(0xFF00261F),
    secondaryContainer = Color(0xFF0F3A33),
    onSecondaryContainer = Color(0xFFC9FBF1),
    tertiary = TimeColor,
    onTertiary = Color(0xFF2B1A00),
    background = Color(0xFF0B0F17),
    onBackground = Color(0xFFEEF2F8),
    surface = Color(0xFF0B0F17),
    onSurface = Color(0xFFEEF2F8),
    surfaceVariant = Color(0xFF1A2231),
    onSurfaceVariant = Color(0xFF9AA6BA),
    surfaceContainerLowest = Color(0xFF080B11),
    surfaceContainerLow = Color(0xFF0F1520),
    surfaceContainer = Color(0xFF131A26),
    surfaceContainerHigh = Color(0xFF1A2231),
    surfaceContainerHighest = Color(0xFF222B3D),
    surfaceBright = Color(0xFF2A3447),
    inverseSurface = Color(0xFFEEF2F8),
    inverseOnSurface = Color(0xFF131A26),
    outline = Color(0xFF3A475E),
    outlineVariant = Hairline,
    error = Color(0xFFFF7A7A),
    onError = Color(0xFF2D0607),
    errorContainer = Color(0xFF4A1417),
    onErrorContainer = Color(0xFFFFDAD8),
    scrim = Color(0xFF000000),
)

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f) =
    TextStyle(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.sp)

/** A compact, modern type scale (smaller than the first driving design, at the driver's request). */
private val NightTypography = Typography(
    displaySmall = style(34, 40, FontWeight.Bold, -0.5f),
    headlineLarge = style(30, 36, FontWeight.Bold, -0.3f),
    headlineMedium = style(26, 32, FontWeight.Bold, -0.2f),
    headlineSmall = style(22, 28, FontWeight.SemiBold),
    titleLarge = style(20, 26, FontWeight.SemiBold),
    titleMedium = style(16, 22, FontWeight.SemiBold, 0.1f),
    titleSmall = style(14, 20, FontWeight.Medium, 0.1f),
    bodyLarge = style(16, 24),
    bodyMedium = style(14, 20, spacing = 0.1f),
    bodySmall = style(12, 16, spacing = 0.2f),
    labelLarge = style(15, 20, FontWeight.SemiBold, 0.1f),
    labelMedium = style(12, 16, FontWeight.Medium, 0.4f),
    labelSmall = style(11, 14, FontWeight.Medium, 0.4f),
)

private val NightShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun NastaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NightColors, typography = NightTypography, shapes = NightShapes, content = content)
}
