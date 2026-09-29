package se.eldebosh.nastastopp.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Layer 1 of the design system: every raw colour of the identity, named by what it is, never by
 * what it is used for. Only the colour roles ([DayColors], [NightColors]) pick from here; screens
 * and the floating panel never do. Change a value here and every role that uses it follows.
 */
object Palette {
    // YouDrive's own colours, sampled from the dispatch list.
    val YouDriveGreen = Color(0xFF9CD39C)
    val YouDriveGrey = Color(0xFFCACACA)
    val Ink = Color(0xFF17171A)
    val White = Color(0xFFFFFFFF)

    // Day greys.
    val Paper = Color(0xFFF4F5F7)
    val Mist = Color(0xFFEFF1F4)
    val Cloud = Color(0xFFE6E8EC)
    val Line = Color(0xFFE1E3E6)
    val Pebble = Color(0xFF9AA0A6)
    val Slate = Color(0xFF5F6368)

    // Accents: taxi yellow (the next action) and the first design's light blue.
    val TaxiYellow = Color(0xFFFFC61A)
    val Butter = Color(0xFFFFF1C2)
    val Sky = Color(0xFF6EA8FF)
    val SkyInk = Color(0xFF2563EB)
    val SkyMist = Color(0xFFE3EEFF)
    val Navy = Color(0xFF06142B)
    val DeepSky = Color(0xFF16305A)
    val Honey = Color(0xFF3D3100)

    // Night: the first design's calm blue-grey, easy on the eyes in the dark.
    val Night = Color(0xFF0B0F17)
    val NightCard = Color(0xFF131A26)
    val NightRaised = Color(0xFF1A2231)
    val NightHigh = Color(0xFF222B3D)
    val NightLine = Color(0xFF243044)
    val NightOutline = Color(0xFF3A475E)
    val Moon = Color(0xFFEEF2F8)
    val Dusk = Color(0xFF9AA6BA)
    val Haze = Color(0xFFAEB8C9)
    val NightGreen = Color(0xFF1F4A2B)
    val NightGrey = Color(0xFF30353D)

    // Status: dark shades read on white, light shades read on the night cards.
    val Green700 = Color(0xFF1E7A34)
    val Amber800 = Color(0xFFA04A07)
    val Red700 = Color(0xFFC62828)
    val Green400 = Color(0xFF4ADE80)
    val Amber400 = Color(0xFFFBBF24)
    val Red400 = Color(0xFFF87171)
    val RedMist = Color(0xFFFDE2E1)
    val RedDeep = Color(0xFF5F1412)
    val RedNight = Color(0xFF4A1417)
    val RedPale = Color(0xFFFFDAD8)
}
