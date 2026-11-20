package com.example.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecretCipher {
    fun encrypt(plaintext: String): EncryptedPayload
    fun decrypt(payload: EncryptedPayload): String?
}

data class EncryptedPayload(
    val ciphertextBase64: String,
    val ivBase64: String
)

/**
 * Encrypts and decrypts the user's Gemini API key using an AES-256 GCM key
 * generated and hardware-backed inside AndroidKeyStore.
 */
class AndroidKeystoreCryptoManager : SecretCipher {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    override fun encrypt(plaintext: String): EncryptedPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encryptedBytes = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return EncryptedPayload(
            ciphertextBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        )
    }

    override fun decrypt(payload: EncryptedPayload): String? {
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = Base64.decode(payload.ivBase64, Base64.NO_WRAP)
            val ciphertext = Base64.decode(payload.ciphertextBase64, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "aurapilot_gemini_api_key_aes"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
