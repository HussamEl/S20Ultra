package se.eldebosh.nastastopp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.eldebosh.nastastopp.core.parse.TripKind

/*
 * Visual identity "Route cards" (1.1), taken from the YouDrive dispatch list the driver works with
 * every day: a light page, white trip cards, green pick-ups, a grey depot card and black actions
 * (YouDrive's "Arrive" button). Taxi yellow marks the one thing to act on next (Next, the current
 * trip's time). Green / amber / red are kept for status.
 */

/** Ink: text, icons and the main actions. */
val Ink = Color(0xFF17171A)

/** Brand colour: the main actions, selected items, the app name. */
val Brand = Ink

/** Taxi yellow: the next action (Next, Start route) and the current trip's time. Ink on top. */
val Accent = Color(0xFFFFC61A)

/** Trip times (read at a glance; black like YouDrive's). */
val TimeColor = Ink

/** Status: located / on time / done (dark enough to read on white). */
val Located = Color(0xFF1E7A34)

/** Status: not found / late / problem. */
val NotLocated = Color(0xFFC62828)

/** Status: soon. */
val Warning = Color(0xFFB45309)

/** Hairline borders of cards. */
val Hairline = Color(0xFFE1E3E6)

/** YouDrive's pick-up card (green). */
val PickUpGreen = Color(0xFF9CD39C)

/** YouDrive's depot card (Pull-out / Pull-in, grey). */
val DepotGrey = Color(0xFFCACACA)

/** Card colour of a trip: green pick-up, grey depot, white drop-off (and trips of unknown kind). */
fun kindColor(kind: TripKind?): Color = when (kind) {
    TripKind.PICK_UP -> PickUpGreen
    TripKind.PULL_OUT, TripKind.PULL_IN -> DepotGrey
    TripKind.DROP_OFF, null -> Color.White
}

private val RouteCardColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFF1C2),
    onPrimaryContainer = Ink,
    secondary = Accent,
    onSecondary = Ink,
    secondaryContainer = Color(0xFFFFF1C2),
    onSecondaryContainer = Ink,
    tertiary = PickUpGreen,
    onTertiary = Ink,
    tertiaryContainer = Color(0xFFDDF1DD),
    onTertiaryContainer = Ink,
    background = Color(0xFFF4F5F7),
    onBackground = Ink,
    surface = Color(0xFFF4F5F7),
    onSurface = Ink,
    surfaceVariant = Color(0xFFECEEF1),
    onSurfaceVariant = Color(0xFF5F6368),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEFF1F4),
    surfaceContainerHighest = Color(0xFFE6E8EC),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE2E4E8),
    inverseSurface = Ink,
    inverseOnSurface = Color.White,
    inversePrimary = Accent,
    outline = Color(0xFF9AA0A6),
    outlineVariant = Hairline,
    error = NotLocated,
    onError = Color.White,
    errorContainer = Color(0xFFFDE2E1),
    onErrorContainer = Color(0xFF5F1412),
    scrim = Color(0xFF000000),
)

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f) =
    TextStyle(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.sp)

/** A compact, modern type scale (smaller than the first driving design, at the driver's request). */
private val RouteTypography = Typography(
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

private val RouteShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun NastaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RouteCardColors, typography = RouteTypography, shapes = RouteShapes, content = content)
}
