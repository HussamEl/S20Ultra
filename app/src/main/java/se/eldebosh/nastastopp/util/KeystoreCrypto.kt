package se.eldebosh.nastastopp.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Seals and opens saved bytes (the Android Keystore; tests use a stand-in). */
interface Crypto {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(sealed: ByteArray): ByteArray

    /** Destroys the key, so what it sealed can never be opened again. */
    fun forget() {}
}

/**
 * AES-256-GCM (a 12-byte IV, a 128-bit tag) with a key made inside, and never leaving, the
 * Android Keystore, under [alias]. Each saved secret has its own alias ([YOUDRIVE], [DEVICE]), so
 * forgetting one never destroys another; there is no default, so no caller falls onto another's.
 */
class KeystoreCrypto(private val alias: String) : Crypto {

    private fun keyStore() = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun key(): SecretKey {
        (keyStore().getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
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

    override fun forget() = keyStore().deleteEntry(alias)

    override fun toString() = "KeystoreCrypto($alias)"

    companion object {
        /** The driver's YouDrive login ([se.eldebosh.nastastopp.youdrive.YouDriveLogin]); logins already saved open with it. */
        const val YOUDRIVE = "youdrive_login"

        /** The tablet's pass to the company's server (settings/CompanyDevice). */
        const val DEVICE = "company_device"

        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}
