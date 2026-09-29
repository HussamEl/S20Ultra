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
        check(look, "main button", c.onAction, c.action, 4.5)
        check(look, "yellow button", c.onAccent, c.accent, 4.5)
        check(look, "blue on blue", c.onInfo, c.info, 4.5)
        check(look, "blue text on card", c.info, c.card, 4.5)
        check(look, "blue text on page", c.info, c.background, 4.5)
        check(look, "circle text", c.onPanelCircle, c.panelCircle, 4.5)
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
    }

    @Test
    fun dayIsReadable() = checkLook("day", DayColors)

    @Test
    fun nightIsReadable() = checkLook("night", NightColors)

    @Test
    fun theLooksAreWhatTheyClaim() {
        assertTrue(!DayColors.isDark && DayColors.background.luminance() > 0.8f)
        assertTrue(NightColors.isDark && NightColors.background.luminance() < 0.05f)
    }
}
