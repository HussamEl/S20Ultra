package se.eldebosh.nastastopp.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import se.eldebosh.nastastopp.core.parse.TripKind

/**
 * Every text/background pair of both looks meets WCAG contrast: 4.5 for text, 3 for bold status
 * text on trip cards, large text and borders. A colour change that makes something unreadable in
 * the car fails here, not on the road.
 */
class ThemeContrastTest {

    private fun contrast(fg: Color, bg: Color): Double {
        val a = fg.compositeOver(bg).luminance() + 0.05
        val b = bg.luminance() + 0.05
        return maxOf(a, b) / minOf(a, b)
    }

    private fun check(look: String, what: String, fg: Color, bg: Color, min: Double) {
        val c = contrast(fg, bg)
        assertTrue("$look: $what has contrast %.2f < %.1f".format(c, min), c >= min)
    }

    private fun checkLook(look: String, c: AppColors) {
        val trips = TripKind.entries.map { c.trip(it) }.distinct()
        check(look, "text on page", c.text, c.background, 4.5)
        check(look, "text on card", c.text, c.card, 4.5)
        check(look, "muted text on card", c.textMuted, c.card, 4.5)
        check(look, "muted text on page", c.textMuted, c.background, 4.5)
        check(look, "time on card", c.time, c.card, 4.5)
        check(look, "passenger highlight on page", c.highlight, c.background, 4.5)
        check(look, "passenger highlight on card", c.highlight, c.card, 4.5)
        // The first card's time on the card's tint while it is said: large bold text.
        check(look, "passenger highlight on a lit card", c.highlight, c.infoSoft, 3.0)
        check(look, "\"DÄREFTER\" on its chip", c.text, c.tonalHigh, 4.5)
        // A phone number in a trip card's instructions: large bold text.
        check(look, "phone number in the instructions", c.highlight, c.tonalHigh, 3.0)
        // The big time and the clock's colon, which tells how the next stop's time stands: very large.
        c.showHues.forEach { check(look, "big time", it, c.background, 3.0) }
        check(look, "colon of a trip on time", c.success, c.background, 3.0)
        check(look, "colon of a trip due soon", c.soon, c.background, 3.0)
        check(look, "colon of a late trip", c.danger, c.background, 3.0)
        check(look, "how late, beside the next stop's time", c.danger, c.background, 4.5)
        check(look, "main button", c.onAction, c.action, 4.5)
        check(look, "yellow button", c.onAccent, c.accent, 4.5)
        check(look, "blue on blue", c.onInfo, c.info, 4.5)
        check(look, "blue text on card", c.info, c.card, 4.5)
        check(look, "blue text on page", c.info, c.background, 4.5)
        for (status in listOf(c.success, c.warning, c.danger)) {
            check(look, "status pill", c.onStatus, status, 4.5)
            check(look, "status text on card", status, c.card, 4.5)
        }
        for (trip in trips) {
            check(look, "trip text", c.onTrip, trip, 4.5)
            check(look, "trip muted text", c.onTripMuted, trip, 4.5)
            check(look, "kind pill", c.onKindPill, c.kindPill.compositeOver(trip), 4.5)
            check(look, "current border", c.currentBorder, trip, 3.0)
            for (status in listOf(c.success, c.warning, c.danger)) check(look, "bold status on trip", status, trip, 3.0)
        }
        check(look, "reference numbers", c.refText, c.background, 3.0)
        check(look, "reference pill", c.onRefPill, c.refPill.compositeOver(c.card), 4.5)
        checkPanel(look, c)
    }

    /**
     * The floating panel is glass: what shows through depends on the map or wallpaper behind it.
     * Its text must read over the extremes (white and black) and over a green park and a blue
     * route line, and its edges must outline the card on both white and black.
     */
    private fun checkPanel(look: String, c: AppColors) {
        val p = c.panel
        val behind = listOf("white" to Palette.White, "black" to Palette.Black, "park" to Palette.YouDriveGreen, "route" to Palette.SkyInk)
        for ((name, bg) in behind) {
            val at = "$look panel over $name"
            val frame = p.glass.compositeOver(bg)
            val well = p.well.compositeOver(frame)
            val control = p.control.compositeOver(frame)
            check(at, "clock on the frame", p.text, frame, 4.5)
            check(at, "trip number on its pill", p.text, control, 4.5)
            check(at, "street / name / address", p.text, well, 4.5)
            check(at, "area", p.textMuted, well, 4.5)
            for (status in listOf(p.success, p.warning, p.danger)) check(at, "on-time status", status, well, 4.5)
            // The minimised capsule: its countdown on the deeper glass, in the display clock's colours.
            for (status in listOf(p.success, p.soon, p.danger)) check(at, "countdown on the capsule", status, well, 4.5)
            check(at, "– and ×", p.text, control, 4.5)
            check(at, "the yellow Next", c.accent, frame, 3.0)
            // The capsule's chevrons float on the map with only a dark halo behind them.
            check(at, "chevron on its halo", p.text, p.halo.compositeOver(bg), 3.0)
        }
        check(look, "panel edge on black", p.edgeLight, Palette.Black, 3.0)
        check(look, "panel edge on white", p.edgeDark, Palette.White, 3.0)
        for (kind in TripKind.entries) check(look, "kind stripe on the glass", p.trip(kind), p.well.compositeOver(p.glass.compositeOver(Palette.Black)), 3.0)
        check(look, "time pill / Next", c.onAccent, c.accent, 4.5)
    }

    @Test
    fun dayIsReadable() = checkLook("day", DayColors)

    @Test
    fun nightIsReadable() = checkLook("night", NightColors)

    @Test
    fun thePassengerDisplayIsReadable() {
        checkLook("passenger display", DisplayColors)
        check("passenger display", "colon without a route", DisplayColors.accent, DisplayColors.background, 3.0)
    }

    @Test
    fun theLooksAreWhatTheyClaim() {
        assertTrue(!DayColors.isDark && DayColors.background.luminance() > 0.8f)
        assertTrue(NightColors.isDark && NightColors.background.luminance() < 0.05f)
        // The passenger display is black, and its cards only a shade above it.
        assertTrue(DisplayColors.isDark && DisplayColors.background.luminance() == 0f)
        assertTrue(DisplayColors.card.luminance() < 0.01f)
    }
}
