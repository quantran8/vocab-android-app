package com.lamls.vocabflow.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.lamls.vocabflow.network.ApiJson
import com.lamls.vocabflow.network.AuthTokens
import com.lamls.vocabflow.network.TokenStoring
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * iOS `KeychainTokenStore`: both tokens in one encrypted item, so a refresh can never leave a mismatched pair.
 * AES-GCM with a non-exportable Android Keystore key; the ciphertext lives in private SharedPreferences, excluded
 * from backup (`data_extraction_rules.xml`) because the key can't follow it to another device.
 */
class KeystoreTokenStore(context: Context) : TokenStoring {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    override fun load(): AuthTokens? {
        val stored = prefs.getString(KEY_TOKENS, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_LENGTH)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            val json = cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH).decodeToString()
            ApiJson.decodeFromString(AuthTokens.serializer(), json)
        } catch (e: Exception) {
            // A key lost with a restore or OS update: treat as signed out rather than crash.
            null
        }
    }

    @Synchronized
    override fun save(tokens: AuthTokens) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(ApiJson.encodeToString(AuthTokens.serializer(), tokens).toByteArray())
        prefs.edit().putString(KEY_TOKENS, Base64.encodeToString(sealed, Base64.NO_WRAP)).commit()
    }

    @Synchronized
    override fun clear() {
        prefs.edit().remove(KEY_TOKENS).commit()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "com.lamls.vocabflow.auth"
        const val KEY_TOKENS = "tokens"
        const val KEY_ALIAS = "com.lamls.vocabflow.auth.key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}
