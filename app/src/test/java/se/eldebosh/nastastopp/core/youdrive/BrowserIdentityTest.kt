package se.eldebosh.nastastopp.core.youdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The YouDrive page sees the same browser identity as Chrome on Android (no "wv" app marker). */
class BrowserIdentityTest {

    @Test
    fun webViewUserAgentBecomesChromesReducedUserAgent() {
        val webView = "Mozilla/5.0 (Linux; Android 13; SM-G988B Build/TP1A.220624.014; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/141.0.7390.122 Mobile Safari/537.36"
        val ua = BrowserIdentity.chromeUserAgent(webView)
        assertEquals(
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36",
            ua,
        )
        assertFalse(ua.contains("wv"))
        assertFalse(ua.contains("Version/4.0"))
    }

    @Test
    fun unknownFormatOnlyLosesTheMarkers() {
        assertEquals("Mozilla/5.0 (Linux; Android 13) Mobile", BrowserIdentity.chromeUserAgent("Mozilla/5.0 (Linux; Android 13; wv) Version/4.0 Mobile"))
    }
}
