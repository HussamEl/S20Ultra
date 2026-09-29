package se.eldebosh.nastastopp.core.youdrive

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/**
 * When the app signs in to YouDrive by itself (the driver's choice, 1.6). YouDrive forgets its
 * login whenever its window is closed, in Chrome too, so the driver would sign in again and again.
 *
 * With the switch on, each reading that shows the login form may press Login, but only
 * [MAX_TRIES] times in a row and [RETRY_MS] apart. A login that does not work (a changed
 * password) is not tried over and over, so the account is never locked by the app. After the
 * driver logs out in the app, nothing is tried until he has signed in himself once.
 */
class AutoSignIn(private val maxTries: Int = MAX_TRIES, private val retryMs: Long = RETRY_MS) {
    private var tries = 0
    private var lastTryMs: Long? = null
    private var paused = false

    /** The tries ran out without the login form going away. */
    var gaveUp = false
        private set

    /** One reading of the page: true when Login should be pressed now. */
    fun onReading(loginForm: Boolean, enabled: Boolean, nowMs: Long): Boolean {
        if (!loginForm) {
            // Signed in (or another page): start afresh next time.
            tries = 0
            lastTryMs = null
            gaveUp = false
            paused = false
            return false
        }
        if (!enabled || paused || gaveUp) return false
        val last = lastTryMs
        if (last != null && nowMs - last < retryMs) return false
        if (tries >= maxTries) {
            gaveUp = true
            return false
        }
        tries++
        lastTryMs = nowMs
        return true
    }

    /** The driver logged out: no automatic sign-in until he has signed in himself. */
    fun pause() {
        paused = true
    }

    companion object {
        const val MAX_TRIES = 2
        const val RETRY_MS = 20_000L
        const val HOST = "youdrive.regionvarmland.se"
    }
}

/**
 * The script that signs in on YouDrive's own login form. It runs only on https://[AutoSignIn.HOST]
 * (checked again inside the page, in case it navigated). It fills a field only when it is empty,
 * so what YouDrive's own "Remember me" filled in is kept, and then presses Login. The saved values
 * go in as JSON string literals, so nothing in them can run as code. It returns only a status word
 * ("clicked", "empty", "nobutton", "noform", "host"), never a value.
 */
object SignInScript {
    fun build(username: String?, password: String?): String = """
        (function(u, p){
          if (location.protocol !== 'https:' || location.hostname !== '${AutoSignIn.HOST}') return 'host';
          var pw = document.querySelector('input[type=password]');
          if (!pw || pw.offsetParent === null) return 'noform';
          var scope = pw.form || document.body;
          var user = Array.prototype.filter.call(scope.querySelectorAll('input'), function (i) {
            var t = (i.getAttribute('type') || 'text').toLowerCase();
            return (t === 'text' || t === 'email' || t === 'tel' || t === 'number') && i.offsetParent !== null;
          })[0] || null;
          var set = function (el, v) {
            Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(el, v);
            el.dispatchEvent(new Event('input', { bubbles: true }));
            el.dispatchEvent(new Event('change', { bubbles: true }));
          };
          if (u !== null && user && !user.value) set(user, u);
          if (p !== null && !pw.value) set(pw, p);
          if (!pw.value || (user && !user.value)) return 'empty';
          var named = function (b) { return /^\s*(log\s*in|logga in|sign in)\s*$/i.test(b.innerText || b.value || ''); };
          var btn = Array.prototype.filter.call(scope.querySelectorAll('button, input[type=submit]'), named)[0] ||
            scope.querySelector('button[type=submit], input[type=submit]') ||
            Array.prototype.filter.call(document.querySelectorAll('button'), named)[0];
          if (!btn) return 'nobutton';
          btn.click();
          return 'clicked';
        })(${literal(username)}, ${literal(password)})
    """.trimIndent()

    /** A JavaScript string literal (JSON, which JavaScript reads as-is), or null. */
    private fun literal(value: String?): String =
        if (value == null) "null" else Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(value))
            .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
}
