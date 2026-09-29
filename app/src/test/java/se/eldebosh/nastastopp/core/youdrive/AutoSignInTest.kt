package se.eldebosh.nastastopp.core.youdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** YouDrive's automatic sign-in (1.6): when Login is pressed, and that saved values stay data. */
class AutoSignInTest {

    @Test
    fun pressesLoginOnlyWhileTheFormShowsAndOnlyTwiceInARow() {
        val a = AutoSignIn()
        assertFalse("switched off", a.onReading(loginForm = true, enabled = false, nowMs = 0))
        assertFalse("no login form", a.onReading(loginForm = false, enabled = true, nowMs = 0))
        assertTrue(a.onReading(loginForm = true, enabled = true, nowMs = 1_000))
        assertFalse("not again at once", a.onReading(loginForm = true, enabled = true, nowMs = 5_000))
        assertTrue("a second try later", a.onReading(loginForm = true, enabled = true, nowMs = 1_000 + AutoSignIn.RETRY_MS))
        assertFalse(a.onReading(loginForm = true, enabled = true, nowMs = 1_000 + 2 * AutoSignIn.RETRY_MS))
        assertTrue("gives up: a wrong password is not tried over and over", a.gaveUp)
        assertFalse(a.onReading(loginForm = true, enabled = true, nowMs = 10 * AutoSignIn.RETRY_MS))
        // Signed in (the form went away): a later login form is tried again.
        assertFalse(a.onReading(loginForm = false, enabled = true, nowMs = 11 * AutoSignIn.RETRY_MS))
        assertFalse(a.gaveUp)
        assertTrue(a.onReading(loginForm = true, enabled = true, nowMs = 12 * AutoSignIn.RETRY_MS))
    }

    @Test
    fun afterLoggingOutItWaitsUntilTheDriverSignedInHimself() {
        val a = AutoSignIn()
        a.pause()
        assertFalse(a.onReading(loginForm = true, enabled = true, nowMs = 0))
        assertFalse(a.onReading(loginForm = true, enabled = true, nowMs = 60_000))
        assertFalse(a.onReading(loginForm = false, enabled = true, nowMs = 90_000)) // he signed in
        assertTrue(a.onReading(loginForm = true, enabled = true, nowMs = 120_000))
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
