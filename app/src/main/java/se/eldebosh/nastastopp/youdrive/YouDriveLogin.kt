package se.eldebosh.nastastopp.youdrive

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The driver's YouDrive login, only if he typed it into the app on this phone (1.6, his decision,
 * for the automatic sign-in). It is encrypted with a key that never leaves the phone's Android
 * Keystore and kept in app-private storage that is never backed up. It is only ever filled into
 * YouDrive's own login form ([se.eldebosh.nastastopp.core.youdrive.SignInScript]); it is never
 * shown again, logged or sent anywhere, and Delete removes it.
 */
class YouDriveLogin(context: Context, private val crypto: Crypto = KeystoreCrypto()) {

    /** A saved login. Its text form never shows the values. */
    class Login(val username: String, val password: String) {
        override fun toString() = "Login(***)"
    }

    /** Seals and opens the saved bytes (the Android Keystore; tests use a stand-in). */
    interface Crypto {
        fun encrypt(plain: ByteArray): ByteArray
        fun decrypt(sealed: ByteArray): ByteArray
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

    fun delete() {
        prefs.edit { remove(K_LOGIN) }
        _saved.value = false
        runCatching { crypto.forget() }
    }

    private fun Crypto.forget() {
        if (this is KeystoreCrypto) deleteKey()
    }

    private companion object {
        const val FILE = "youdrive_login"
        const val K_LOGIN = "login"
    }
}

/** AES-256-GCM with a key made inside, and never leaving, the Android Keystore. */
class KeystoreCrypto : YouDriveLogin.Crypto {

    private fun keyStore() = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun key(): SecretKey {
        (keyStore().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    fun deleteKey() {
        keyStore().deleteEntry(ALIAS)
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "youdrive_login"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
