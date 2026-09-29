package se.eldebosh.nastastopp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import se.eldebosh.nastastopp.core.parse.TimeLevel
import se.eldebosh.nastastopp.core.parse.TripKind

/**
 * Layer 2 of the design system: colour roles. Screens and the floating panel ask for a role
 * ("a pick-up's card", "the next action"), never for a hex value, so the day and night looks are
 * just two sets of roles ([DayColors], [NightColors]). A new look is a new set.
 */
@Immutable
data class AppColors(
    val isDark: Boolean,
    // Page and surfaces.
    val background: Color,
    val card: Color,
    val cardBorder: Color,
    /** Quiet buttons and tiles. */
    val tonal: Color,
    val tonalHigh: Color,
    // Text.
    val text: Color,
    val textMuted: Color,
    val outline: Color,
    // Actions.
    /** The main buttons (black like YouDrive's "Arrive" by day, sky blue by night). */
    val action: Color,
    val onAction: Color,
    /** Taxi yellow: the next action (Next, Start route) and the current trip's time. */
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    /** Sky blue: selection, links, section titles, information icons. */
    val info: Color,
    val onInfo: Color,
    val infoSoft: Color,
    // Trips, in YouDrive's card colours.
    val pickUp: Color,
    val dropOff: Color,
    val depot: Color,
    val onTrip: Color,
    val onTripMuted: Color,
    /** The thick border of the current trip (YouDrive's active card). */
    val currentBorder: Color,
    /** The small pill with a trip's kind ("Pick-up"). */
    val kindPill: Color,
    val onKindPill: Color,
    // Status.
    val success: Color,
    val warning: Color,
    val danger: Color,
    /** Text on a status colour (the "in 7 min" pill). */
    val onStatus: Color,
    val dangerSoft: Color,
    val onDangerSoft: Color,
    /** Trip times. */
    val time: Color,
    // Reference numbers.
    val refText: Color,
    val refPill: Color,
    val onRefPill: Color,
    // The floating panel over Maps.
    val panelCircle: Color,
    val onPanelCircle: Color,
    val onPanelCircleMuted: Color,
    val panelRing: Color,
) {
    /** A trip's card: YouDrive's green pick-up, grey depot, white (by night: dark) drop-off. */
    fun trip(kind: TripKind?): Color = when (kind) {
        TripKind.PICK_UP -> pickUp
        TripKind.PULL_OUT, TripKind.PULL_IN -> depot
        TripKind.DROP_OFF, null -> dropOff
    }

    /** On time / soon / late. */
    fun status(level: TimeLevel): Color = when (level) {
        TimeLevel.AHEAD -> success
        TimeLevel.SOON -> warning
        TimeLevel.LATE -> danger
    }
}

/** Day ("Route cards"): YouDrive's light list, black actions, taxi yellow, sky-blue details. */
val DayColors = AppColors(
    isDark = false,
    background = Palette.Paper,
    card = Palette.White,
    cardBorder = Palette.Line,
    tonal = Palette.Mist,
    tonalHigh = Palette.Cloud,
    text = Palette.Ink,
    textMuted = Palette.Slate,
    outline = Palette.Pebble,
    action = Palette.Ink,
    onAction = Palette.White,
    accent = Palette.TaxiYellow,
    onAccent = Palette.Ink,
    accentSoft = Palette.Butter,
    info = Palette.SkyInk,
    onInfo = Palette.White,
    infoSoft = Palette.SkyMist,
    pickUp = Palette.YouDriveGreen,
    dropOff = Palette.White,
    depot = Palette.YouDriveGrey,
    onTrip = Palette.Ink,
    onTripMuted = Palette.Ink.copy(alpha = 0.72f),
    currentBorder = Palette.Ink,
    kindPill = Palette.White.copy(alpha = 0.9f),
    onKindPill = Palette.Ink,
    success = Palette.Green700,
    warning = Palette.Amber800,
    danger = Palette.Red700,
    onStatus = Palette.White,
    dangerSoft = Palette.RedMist,
    onDangerSoft = Palette.RedDeep,
    time = Palette.Ink,
    refText = Color(0xFF6B7280),
    refPill = Palette.Ink.copy(alpha = 0.9f),
    onRefPill = Color(0xFFF1F2F4),
    panelCircle = Palette.Ink,
    onPanelCircle = Palette.White,
    onPanelCircleMuted = Color(0xFFC7CBD1),
    panelRing = Palette.Sky,
)

/**
 * Night: the first design's calm blue-grey with its light blue, and the same meanings. Pick-ups
 * stay green and the depot grey, only darker; yellow still marks the next action.
 */
val NightColors = AppColors(
    isDark = true,
    background = Palette.Night,
    card = Palette.NightCard,
    cardBorder = Palette.NightLine,
    tonal = Palette.NightRaised,
    tonalHigh = Palette.NightHigh,
    text = Palette.Moon,
    textMuted = Palette.Dusk,
    outline = Palette.NightOutline,
    action = Palette.Sky,
    onAction = Palette.Navy,
    accent = Palette.TaxiYellow,
    onAccent = Palette.Ink,
    accentSoft = Palette.Honey,
    info = Palette.Sky,
    onInfo = Palette.Navy,
    infoSoft = Palette.DeepSky,
    pickUp = Palette.NightGreen,
    dropOff = Palette.NightCard,
    depot = Palette.NightGrey,
    onTrip = Palette.Moon,
    onTripMuted = Palette.Haze,
    currentBorder = Palette.TaxiYellow,
    kindPill = Palette.Night.copy(alpha = 0.85f),
    onKindPill = Palette.Moon,
    success = Palette.Green400,
    warning = Palette.Amber400,
    danger = Palette.Red400,
    onStatus = Palette.Night,
    dangerSoft = Palette.RedNight,
    onDangerSoft = Palette.RedPale,
    time = Palette.Moon,
    refText = Color(0xFF7F8BA0),
    refPill = Palette.Night.copy(alpha = 0.9f),
    onRefPill = Color(0xFFB8C2D4),
    panelCircle = Palette.NightCard,
    onPanelCircle = Palette.Moon,
    onPanelCircleMuted = Palette.Dusk,
    panelRing = Palette.Sky,
)
