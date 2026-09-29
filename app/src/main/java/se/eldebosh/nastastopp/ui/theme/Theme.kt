package se.eldebosh.nastastopp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.eldebosh.nastastopp.settings.Appearance

/*
 * The design system, in three layers:
 *   1. Palette.kt    raw colours, named by what they are (YouDrive green, taxi yellow, sky blue …);
 *   2. AppColors.kt  colour roles for day and night ("a pick-up's card", "the next action");
 *   3. AppEffects.kt shadows, press feedback and colour fades.
 * This file puts them together: NastaTheme picks day or night, provides the roles and effects
 * (AppTheme.colors / AppTheme.effects) and maps the roles onto Material 3, so Material's own
 * components (switches, dialogs, menus) follow too.
 */

internal val LocalAppColors = staticCompositionLocalOf { DayColors }
internal val LocalAppEffects = staticCompositionLocalOf { DayEffects }

/** The design system's entry point for screens and components. */
object AppTheme {
    /** The colour roles of the current look (day or night). */
    val colors: AppColors
        @Composable @ReadOnlyComposable
        get() = LocalAppColors.current

    /** Shadows, press feedback and colour fades of the current look. */
    val effects: AppEffects
        @Composable @ReadOnlyComposable
        get() = LocalAppEffects.current

    /** True when [appearance] means night ([systemDark] = the phone's dark mode, for AUTOMATIC). */
    fun isNight(appearance: Appearance, systemDark: Boolean): Boolean = when (appearance) {
        Appearance.DAY -> false
        Appearance.NIGHT -> true
        Appearance.AUTOMATIC -> systemDark
    }

    /** The colour roles for [appearance], also outside Compose (the floating panel is made of Views). */
    fun colorsFor(appearance: Appearance, systemDark: Boolean): AppColors =
        if (isNight(appearance, systemDark)) NightColors else DayColors
}

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, spacing: Float = 0f) =
    TextStyle(fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.sp)

/** A compact, modern type scale (smaller than the first driving design, at the driver's request). */
private val AppTypography = Typography(
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

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Material 3's colour scheme from the roles: sky blue for selection, yellow as secondary. */
private fun AppColors.toMaterial(): ColorScheme {
    val scheme = if (isDark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = info,
        onPrimary = onInfo,
        primaryContainer = infoSoft,
        onPrimaryContainer = text,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = accentSoft,
        onSecondaryContainer = text,
        tertiary = pickUp,
        onTertiary = onTrip,
        tertiaryContainer = pickUp,
        onTertiaryContainer = onTrip,
        background = background,
        onBackground = text,
        surface = background,
        onSurface = text,
        surfaceVariant = tonal,
        onSurfaceVariant = textMuted,
        surfaceTint = Color.Transparent,
        inverseSurface = text,
        inverseOnSurface = background,
        error = danger,
        onError = onStatus,
        errorContainer = dangerSoft,
        onErrorContainer = onDangerSoft,
        outline = outline,
        outlineVariant = cardBorder,
        scrim = Color.Black,
        surfaceBright = card,
        surfaceDim = tonalHigh,
        surfaceContainerLowest = card,
        surfaceContainerLow = card,
        surfaceContainer = card,
        surfaceContainerHigh = tonal,
        surfaceContainerHighest = tonalHigh,
    )
}

@Composable
fun NastaTheme(appearance: Appearance = Appearance.DAY, content: @Composable () -> Unit) {
    val night = AppTheme.isNight(appearance, isSystemInDarkTheme())
    val colors = if (night) NightColors else DayColors
    CompositionLocalProvider(LocalAppColors provides colors, LocalAppEffects provides if (night) NightEffects else DayEffects) {
        MaterialTheme(colorScheme = colors.toMaterial(), typography = AppTypography, shapes = AppShapes, content = content)
    }
}
