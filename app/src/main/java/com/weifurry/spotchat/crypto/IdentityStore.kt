package com.weifurry.spotchat.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the local P-256 identity key pair with hardware-backed encryption at rest.
 *
 * The public key remains base64-encoded in SharedPreferences (harmless to expose).
 * The private key is encrypted with an AES-GCM key stored in Android Keystore,
 * preventing extraction via backup or filesystem access.
 *
 * Migration from plaintext: on first read of a legacy plaintext private key,
 * it's re-encrypted under the Keystore key and the plaintext is removed atomically.
 */
class IdentityStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getOrCreateIdentity(): KeyPair {
        val publicKey = prefs.getString(KEY_PUBLIC, null)
        val encryptedPrivateKey = prefs.getString(KEY_PRIVATE_ENCRYPTED, null)
        val legacyPrivateKey = prefs.getString(KEY_PRIVATE, null)

        // If we have encrypted private key, decrypt and return
        if (!publicKey.isNullOrBlank() && !encryptedPrivateKey.isNullOrBlank()) {
            runCatching {
                KeyPair(
                    SpotChatCrypto.decodePublicKey(publicKey),
                    decryptPrivateKey(encryptedPrivateKey)
                )
            }.onSuccess { identity ->
                // Migrate legacy plaintext if it still exists
                if (!legacyPrivateKey.isNullOrBlank()) {
                    prefs.edit().remove(KEY_PRIVATE).apply()
                }
                return identity
            }
        }

        // Migrate legacy plaintext identity to encrypted storage
        if (!publicKey.isNullOrBlank() && !legacyPrivateKey.isNullOrBlank()) {
            runCatching {
                val publicKeyDecoded = SpotChatCrypto.decodePublicKey(publicKey)
                val privateKeyDecoded = SpotChatCrypto.decodePrivateKey(legacyPrivateKey)
                val identity = KeyPair(publicKeyDecoded, privateKeyDecoded)

                // Encrypt and store, then remove plaintext
                val encrypted = encryptPrivateKey(privateKeyDecoded)
                prefs
                    .edit()
                    .putString(KEY_PRIVATE_ENCRYPTED, encrypted)
                    .remove(KEY_PRIVATE)
                    .apply()

                return identity
            }.onSuccess { identity ->
                return identity
            }
        }

        // No valid identity found, generate fresh one with encryption
        val identity = SpotChatCrypto.generateIdentity()
        val encryptedPrivate = encryptPrivateKey(identity.private)

        prefs
            .edit()
            .putString(KEY_PUBLIC, SpotChatCrypto.encodePublicKey(identity.public))
            .putString(KEY_PRIVATE_ENCRYPTED, encryptedPrivate)
            .remove(KEY_PRIVATE) // Belt-and-suspenders: ensure no plaintext lingers
            .apply()

        return identity
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)

        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            return keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )

        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encryptPrivateKey(privateKey: java.security.PrivateKey): String {
        val keystoreKey = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey)

        val plaintext = privateKey.encoded
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv

        // Store as: base64(iv) || ":" || base64(ciphertext)
        return Base64.getEncoder().encodeToString(iv) + ":" +
               Base64.getEncoder().encodeToString(ciphertext)
    }

    private fun decryptPrivateKey(encrypted: String): java.security.PrivateKey {
        val parts = encrypted.split(":")
        require(parts.size == 2) { "Invalid encrypted private key format" }

        val iv = Base64.getDecoder().decode(parts[0])
        val ciphertext = Base64.getDecoder().decode(parts[1])

        val keystoreKey = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey, GCMParameterSpec(128, iv))

        val plaintext = cipher.doFinal(ciphertext)
        return SpotChatCrypto.decodePrivateKey(
            Base64.getEncoder().encodeToString(plaintext)
        )
    }

    companion object {
        private const val PREFS_NAME = "spotchat_identity"
        private const val KEY_PUBLIC = "public_key"
        private const val KEY_PRIVATE = "private_key" // Legacy plaintext key
        private const val KEY_PRIVATE_ENCRYPTED = "private_key_encrypted"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "spotchat_identity_wrapper_key"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
