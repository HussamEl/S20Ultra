package se.eldebosh.nastastopp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

/** Large text throughout (read at a glance while driving). */
private val DrivingTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold),
    headlineLarge = Base.headlineLarge.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = Base.headlineMedium.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineSmall = Base.headlineSmall.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = Base.bodyLarge.copy(fontSize = 20.sp, lineHeight = 28.sp),
    bodyMedium = Base.bodyMedium.copy(fontSize = 18.sp, lineHeight = 25.sp),
    bodySmall = Base.bodySmall.copy(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = Base.labelLarge.copy(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    labelMedium = Base.labelMedium.copy(fontSize = 16.sp),
    labelSmall = Base.labelSmall.copy(fontSize = 14.sp),
)

@Composable
fun NastaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DrivingColors, typography = DrivingTypography, content = content)
}
