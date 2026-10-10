package se.eldebosh.nastastopp.youdrive

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import se.eldebosh.nastastopp.util.Crypto
import se.eldebosh.nastastopp.util.KeystoreCrypto

/**
 * The driver's YouDrive login, only if he typed it into the app on this phone (for the
 * automatic sign-in). It is encrypted with a key that never leaves the phone's Android
 * Keystore and kept in app-private storage that is never backed up. It is only ever filled into
 * YouDrive's own login form ([se.eldebosh.nastastopp.core.youdrive.SignInScript]); it is never
 * shown again, logged or sent anywhere, and Delete removes it.
 */
class YouDriveLogin(context: Context, private val crypto: Crypto = KeystoreCrypto(KeystoreCrypto.YOUDRIVE)) {

    /** A saved login. Its text form never shows the values. */
    class Login(val username: String, val password: String) {
        override fun toString() = "Login(***)"
    }

    @Serializable
    private class Stored(val u: String, val p: String)

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _saved = MutableStateFlow(prefs.contains(K_LOGIN))

    /** Whether a login is saved (the values themselves are never exposed to the screens). */
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    /** Saves the login (both parts are needed). Returns false if it could not be encrypted. */
    fun save(username: String, password: String): Boolean {
        val user = username.trim()
        if (user.isEmpty() || password.isEmpty()) return false
        val sealed = runCatching { crypto.encrypt(Json.encodeToString(Stored.serializer(), Stored(user, password)).toByteArray()) }
            .getOrNull() ?: return false
        prefs.edit { putString(K_LOGIN, Base64.encodeToString(sealed, Base64.NO_WRAP)) }
        _saved.value = true
        return true
    }

    /** The saved login, or null (none saved, or it can no longer be opened). */
    fun load(): Login? {
        val text = prefs.getString(K_LOGIN, null) ?: return null
        return runCatching {
            val stored = Json.decodeFromString(Stored.serializer(), String(crypto.decrypt(Base64.decode(text, Base64.NO_WRAP))))
            Login(stored.u, stored.p)
        }.getOrNull()
    }

    /** Removes the login and destroys its own key (never another secret's: [KeystoreCrypto.YOUDRIVE]). */
    fun delete() {
        prefs.edit { remove(K_LOGIN) }
        _saved.value = false
        runCatching { crypto.forget() }
    }

    private companion object {
        const val FILE = "youdrive_login"
        const val K_LOGIN = "login"
    }
}
