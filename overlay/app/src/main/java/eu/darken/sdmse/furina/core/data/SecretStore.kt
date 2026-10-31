package eu.darken.sdmse.furina.core.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the Gemini API key encrypted with an AES-256-GCM key held in the Android Keystore.
 * Only ciphertext + IV are written to SharedPreferences; the plaintext key is never logged.
 */
@Singleton
class SecretStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    @Synchronized
    fun saveApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            clearApiKey()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
        val encrypted = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        val ok = prefs.edit()
            .putString(KEY_IV, encode(cipher.iv))
            .putString(KEY_DATA, encode(encrypted))
            .commit()
        check(ok) { "Could not persist encrypted API key" }
    }

    @Synchronized
    fun readApiKey(): String? {
        val iv = prefs.getString(KEY_IV, null) ?: return null
        val data = prefs.getString(KEY_DATA, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, obtainKey(), GCMParameterSpec(TAG_BITS, decode(iv)))
            String(cipher.doFinal(decode(data)), Charsets.UTF_8)
        } catch (e: Exception) {
            // Keystore key lost (device restore, lock-screen reset, corrupt prefs): ciphertext is useless now.
            Log.w(TAG, "Stored API key is unreadable and was discarded: ${e.javaClass.simpleName}")
            clearApiKey()
            null
        }
    }

    fun hasApiKey(): Boolean = readApiKey() != null

    fun maskedApiKey(): String? = readApiKey()?.let { key ->
        if (key.length <= 8) "••••" else key.take(4) + "••••" + key.takeLast(4)
    }

    @Synchronized
    fun clearApiKey() {
        prefs.edit().remove(KEY_IV).remove(KEY_DATA).apply()
    }

    private fun obtainKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    private companion object {
        const val TAG = "Furina:SecretStore"
        const val PREFS_NAME = "furina_secret_store"
        const val KEY_IV = "gemini_key_iv"
        const val KEY_DATA = "gemini_key_data"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "furina_gemini_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
