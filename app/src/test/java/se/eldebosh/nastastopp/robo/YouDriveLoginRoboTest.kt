package se.eldebosh.nastastopp.robo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import se.eldebosh.nastastopp.util.Crypto
import se.eldebosh.nastastopp.util.KeystoreCrypto
import se.eldebosh.nastastopp.youdrive.YouDriveLogin

/**
 * The YouDrive login saved on the phone: stored only sealed, never in plain text, never in
 * its text form, and gone after Delete. (The Android Keystore does not exist in unit tests, so a
 * stand-in seals the bytes here; the real key is made on the phone.)
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class YouDriveLoginRoboTest {

    /** Reverses and flips every byte: enough to prove the stored text is not the plain text. Counts each [forget]. */
    private class FakeCrypto : Crypto {
        var forgotten = 0

        override fun encrypt(plain: ByteArray) = plain.reversedArray().map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        override fun decrypt(sealed: ByteArray) = sealed.map { (it.toInt() xor 0x5A).toByte() }.toByteArray().reversedArray()
        override fun forget() {
            forgotten++
        }
    }

    private val app: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun savedSealedLoadedAndDeleted() {
        val store = YouDriveLogin(app, FakeCrypto())
        assertFalse(store.saved.value)
        assertNull(store.load())
        assertFalse("both parts are needed", store.save("  ", "secret"))
        assertFalse(store.save("test-user", ""))

        assertTrue(store.save(" test-user ", "Exempel-lösen!9"))
        assertTrue(store.saved.value)
        val login = store.load()!!
        assertEquals("test-user", login.username)
        assertEquals("Exempel-lösen!9", login.password)
        assertEquals("Login(***)", login.toString())

        // What lies on disk is sealed: neither part can be read from it.
        val onDisk = app.getSharedPreferences("youdrive_login", Context.MODE_PRIVATE).all.values.joinToString()
        assertFalse(onDisk.contains("Exempel"))
        assertFalse(onDisk.contains("test-user"))

        // A new store on the same phone finds it; Delete removes it.
        val again = YouDriveLogin(app, FakeCrypto())
        assertTrue(again.saved.value)
        again.delete()
        assertFalse(again.saved.value)
        assertNull(YouDriveLogin(app, FakeCrypto()).load())
    }

    /**
     * Each secret has its own Keystore key: Delete (159) destroys only the login's, never the
     * tablet's pass to the company's server. Logins saved before still open with their alias.
     */
    @Test
    fun deleteForgetsOnlyTheLoginsOwnKey() {
        assertEquals("youdrive_login", KeystoreCrypto.YOUDRIVE)
        assertNotEquals(KeystoreCrypto.YOUDRIVE, KeystoreCrypto.DEVICE)
        val own = FakeCrypto()
        val device = FakeCrypto()
        val store = YouDriveLogin(app, own)
        assertTrue(store.save("test-user", "Exempel-lösen!9"))
        store.delete()
        assertEquals(1, own.forgotten)
        assertEquals(0, device.forgotten)
        // Delete with nothing saved still destroys only its own.
        YouDriveLogin(app, own).delete()
        assertEquals(2, own.forgotten)
        assertEquals(0, device.forgotten)
    }
}
