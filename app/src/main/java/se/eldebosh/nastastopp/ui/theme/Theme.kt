package se.eldebosh.nastastopp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

val Amber = Color(0xFFFFC400)
val Located = Color(0xFF66E28A)
val NotLocated = Color(0xFFFF8A65)

/** Dark, high-contrast, driving-friendly colours. */
private val DrivingColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3A2E00),
    onPrimaryContainer = Amber,
    secondary = Color(0xFF7FD3FF),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF0E3A52),
    onSecondaryContainer = Color.White,
    tertiary = Located,
    onTertiary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF101010),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1F1F1F),
    onSurfaceVariant = Color(0xFFE6E6E6),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF222222),
    surfaceContainerHighest = Color(0xFF2A2A2A),
    outline = Color(0xFFB0B0B0),
    error = Color(0xFFFF6E6E),
    onError = Color.Black,
    errorContainer = Color(0xFF5C1111),
    onErrorContainer = Color.White,
)

private val Base = Typography()

/**
 * Large text (read at a glance while driving), 10 % smaller than the first design (driver's
 * request in 1.4.6); the line height shrinks with it.
 */
private fun TextStyle.smaller(fontSize: TextUnit, lineHeight: TextUnit = TextUnit.Unspecified, fontWeight: FontWeight? = null): TextStyle {
    val height = if (lineHeight.isSpecified) lineHeight else this.lineHeight
    return copy(
        fontSize = fontSize * TEXT_SCALE,
        lineHeight = if (height.isSpecified) height * TEXT_SCALE else height,
        fontWeight = fontWeight ?: this.fontWeight,
    )
}

private const val TEXT_SCALE = 0.9f

private val DrivingTypography = Typography(
    displaySmall = Base.displaySmall.smaller(fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold),
    headlineLarge = Base.headlineLarge.smaller(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = Base.headlineMedium.smaller(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineSmall = Base.headlineSmall.smaller(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.smaller(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.smaller(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.smaller(fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = Base.bodyLarge.smaller(fontSize = 20.sp, lineHeight = 28.sp),
    bodyMedium = Base.bodyMedium.smaller(fontSize = 18.sp, lineHeight = 25.sp),
    bodySmall = Base.bodySmall.smaller(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = Base.labelLarge.smaller(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    labelMedium = Base.labelMedium.smaller(fontSize = 16.sp),
    labelSmall = Base.labelSmall.smaller(fontSize = 14.sp),
)

@Composable
fun NastaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DrivingColors, typography = DrivingTypography, content = content)
}
