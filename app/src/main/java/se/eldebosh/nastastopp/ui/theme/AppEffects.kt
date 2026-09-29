package se.eldebosh.nastastopp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layer 3 of the design system: effects and motion. Shadows, the press feedback and the colour
 * fades are all set here, so an effect can be tuned, added or switched off in one place
 * (0.dp, 1f or 0 ms turns it off). The components read them through [AppTheme.effects].
 */
@Immutable
data class AppEffects(
    /** Soft shadow under cards and trip cards. */
    val cardShadow: Dp,
    /** The current trip lifts a little more than the others. */
    val currentShadow: Dp,
    /** Buttons and tiles shrink this much while pressed. */
    val pressedScale: Float,
    /** A card's colour change (the next trip, a new kind) fades over this time. */
    val colorFadeMs: Int,
)

/** By day: soft shadows, like YouDrive's cards. */
val DayEffects = AppEffects(cardShadow = 1.dp, currentShadow = 6.dp, pressedScale = 0.97f, colorFadeMs = 250)

/** By night shadows do not show on the dark page, so the cards rely on their borders. */
val NightEffects = DayEffects.copy(cardShadow = 0.dp, currentShadow = 0.dp)
