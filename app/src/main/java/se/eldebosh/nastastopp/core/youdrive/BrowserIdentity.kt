package se.eldebosh.nastastopp.core.youdrive

/**
 * How the in-app YouDrive page identifies itself: like Chrome on Android (the same engine),
 * without the WebView markers ("; wv", "Version/4.0") that tell a site it runs inside an app.
 */
object BrowserIdentity {
    const val WEBVIEW_BRAND = "Android WebView"
    const val CHROME_BRAND = "Google Chrome"

    private val CHROME_MAJOR = Regex("""Chrome/(\d+)""")

    /** Chrome's reduced user agent for the engine version of [webViewUserAgent]. */
    fun chromeUserAgent(webViewUserAgent: String): String {
        val major = CHROME_MAJOR.find(webViewUserAgent)?.groupValues?.get(1)
            ?: return webViewUserAgent.replace("; wv", "").replace(" Version/4.0", "")
        return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Mobile Safari/537.36"
    }
}
