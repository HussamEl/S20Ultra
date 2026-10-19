package se.eldebosh.nastastopp.core.youdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** YouDrive's automatic sign-in: when Login is pressed, and that saved values stay data. */
class AutoSignInTest {

    @Test
    fun pressesLoginOnlyWhileTheFormShowsAndOnlyTwiceInARow() {
        val a = AutoSignIn()
        assertFalse("switched off", a.onReading(loginForm = true, signedIn = false, enabled = false, nowMs = 0))
        assertFalse("no login form", a.onReading(loginForm = false, signedIn = true, enabled = true, nowMs = 0))
        assertTrue(a.onReading(loginForm = true, signedIn = false, enabled = true, nowMs = 1_000))
        assertFalse("not again at once", a.onReading(loginForm = true, signedIn = false, enabled = true, nowMs = 5_000))
        assertTrue("a second try later", a.onReading(loginForm = true, signedIn = false, enabled = true, nowMs = 1_000 + AutoSignIn.RETRY_MS))
        assertFalse(a.onReading(loginForm = true, signedIn = false, enabled = true, nowMs = 1_000 + 2 * AutoSignIn.RETRY_MS))
        assertTrue("gives up: a wrong password is not tried over and over", a.gaveUp)
        assertFalse(a.onReading(loginForm = true, signedIn = false, enabled = true, nowMs = 10 * AutoSignIn.RETRY_MS))
        // Signed in (the form went away): a later login form is tried again.
        assertFalse(a.onReading(loginForm = false, signedIn = true, enabled = true, nowMs = 11 * AutoSignIn.RETRY_MS))
        assertFalse(a.gaveUp)
        assertTrue(a.onReading(loginForm = true, signedIn = false, enabled = true, nowMs = 12 * AutoSignIn.RETRY_MS))
    }

    @Test
    fun afterLoggingOutItWaitsUntilTheDriverSignedInHimself() {
        val a = AutoSignIn()
        a.pause()
        assertFalse(a.onReading(loginForm = true, signedIn = false, enabled = true, nowMs = 0))
        assertFalse(a.onReading(loginForm = true, signedIn = false, enabled = true, nowMs = 60_000))
        assertFalse(a.onReading(loginForm = false, signedIn = true, enabled = true, nowMs = 90_000)) // he signed in
        assertTrue(a.onReading(loginForm = true, signedIn = false, enabled = true, nowMs = 120_000))
    }

    /**
     * Logout destroys the page and builds a new one, read before it has drawn: that blank reading
     * is not "signed in", so the saved login is not pressed on the form that follows.
     */
    @Test
    fun aBlankPageAfterLoggingOutKeepsThePause() {
        val a = AutoSignIn()
        a.pause()
        assertFalse(a.onReading(loginForm = false, signedIn = false, enabled = true, nowMs = 1_200))
        assertFalse("still paused", a.onReading(loginForm = true, signedIn = false, enabled = true, nowMs = 3_000))
        assertFalse(a.onReading(loginForm = true, signedIn = false, enabled = true, nowMs = 3_000 + AutoSignIn.RETRY_MS))
    }

    /** A wrong saved login is tried twice; the blank page of each reload never gives it two more. */
    @Test
    fun aBlankPageNeverStartsTheTriesAgain() {
        val a = AutoSignIn()
        assertTrue(a.onReading(loginForm = true, signedIn = false, enabled = true, nowMs = 0))
        assertTrue(a.onReading(loginForm = true, signedIn = false, enabled = true, nowMs = AutoSignIn.RETRY_MS))
        assertFalse(a.onReading(loginForm = true, signedIn = false, enabled = true, nowMs = 2 * AutoSignIn.RETRY_MS))
        assertTrue(a.gaveUp)
        // The background reload, read while blank, then its login form again.
        assertFalse(a.onReading(loginForm = false, signedIn = false, enabled = true, nowMs = 300_000))
        assertTrue("still given up", a.gaveUp)
        assertFalse(a.onReading(loginForm = true, signedIn = false, enabled = true, nowMs = 303_000))
        assertFalse(a.onReading(loginForm = true, signedIn = false, enabled = true, nowMs = 303_000 + AutoSignIn.RETRY_MS))
        assertTrue(a.gaveUp)
    }

    @Test
    fun theScriptRunsOnlyOnYouDriveAndSavedValuesCannotBreakOut() {
        val tricky = "a'b\"c\\d</script>');alert(1);//\u2028"
        val js = SignInScript.build("user-1", tricky)
        assertTrue(js.contains("location.protocol !== 'https:' || location.hostname !== 'youdrive.regionvarmland.se'"))
        // The value is one JSON string literal: its quotes and backslash are escaped, so the call
        // ends right after it.
        assertTrue(js, js.endsWith("""("user-1", "a'b\"c\\d</script>');alert(1);//\u2028")"""))
        assertEquals(1, Regex("""alert\(1\)""").findAll(js).count())
    }

    @Test
    fun withoutASavedLoginItOnlyPressesLoginOnWhatYouDriveFilledIn() {
        val js = SignInScript.build(null, null)
        assertTrue(js.endsWith("(null, null)"))
        assertTrue(js.contains("if (!pw.value || (user && !user.value)) return 'empty';"))
    }
}
