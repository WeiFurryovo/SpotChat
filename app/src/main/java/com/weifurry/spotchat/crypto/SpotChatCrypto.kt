package com.weifurry.spotchat.crypto

import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedFrame(
    val nonce: ByteArray,
    val ciphertext: ByteArray
)

object SpotChatCrypto {
    private const val EC_ALGORITHM = "EC"
    private const val EC_CURVE = "secp256r1"
    private const val KEY_AGREEMENT = "ECDH"
    private const val HKDF_ALGORITHM = "HmacSHA256"
    private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
    private const val AES_ALGORITHM = "AES"
    private const val AES_KEY_BYTES = 32
    private const val GCM_TAG_BITS = 128
    private const val GCM_NONCE_BYTES = 12
    private val sessionInfo = "SpotChat/v1/session".toByteArray(Charsets.UTF_8)

    fun generateIdentity(random: SecureRandom = SecureRandom()): KeyPair {
        val generator = KeyPairGenerator.getInstance(EC_ALGORITHM)
        generator.initialize(ECGenParameterSpec(EC_CURVE), random)
        return generator.generateKeyPair()
    }

    fun deriveSessionKey(
        localIdentity: KeyPair,
        remotePublicKey: PublicKey
    ): SecretKey {
        val agreement = KeyAgreement.getInstance(KEY_AGREEMENT)
        agreement.init(localIdentity.private)
        agreement.doPhase(remotePublicKey, true)
        val sharedSecret = agreement.generateSecret()
        val salt = sha256(canonicalPublicKeyPair(localIdentity.public, remotePublicKey))
        val keyBytes = hkdfSha256(
            inputKeyingMaterial = sharedSecret,
            salt = salt,
            info = sessionInfo,
            outputLength = AES_KEY_BYTES
        )
        return SecretKeySpec(keyBytes, AES_ALGORITHM)
    }

    fun encrypt(
        sessionKey: SecretKey,
        plaintext: ByteArray,
        associatedData: ByteArray = ByteArray(0),
        random: SecureRandom = SecureRandom()
    ): EncryptedFrame {
        val nonce = ByteArray(GCM_NONCE_BYTES)
        random.nextBytes(nonce)

        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, sessionKey, GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(associatedData)
        return EncryptedFrame(
            nonce = nonce,
            ciphertext = cipher.doFinal(plaintext)
        )
    }

    fun decrypt(
        sessionKey: SecretKey,
        frame: EncryptedFrame,
        associatedData: ByteArray = ByteArray(0)
    ): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, sessionKey, GCMParameterSpec(GCM_TAG_BITS, frame.nonce))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(frame.ciphertext)
    }

    fun fingerprint(publicKey: PublicKey): String =
        sha256(publicKey.encoded)
            .joinToString(separator = "") { byte ->
                "%02X".format(Locale.US, byte.toInt() and 0xFF)
            }

    fun displayFingerprint(fingerprint: String): String =
        fingerprint
            .filter { character -> character.isLetterOrDigit() }
            .chunked(2)
            .take(8)
            .joinToString(separator = " ") { chunk -> chunk.uppercase(Locale.US) }

    fun pairingCode(
        localPublicKey: PublicKey,
        remotePublicKey: PublicKey,
        sessionKey: SecretKey
    ): String {
        val digestInput =
            canonicalPublicKeyPair(localPublicKey, remotePublicKey) + sessionKey.encoded
        return sha256(digestInput)
            .take(6)
            .joinToString(separator = "") { byte -> "%02X".format(byte) }
            .chunked(4)
            .joinToString(separator = " ")
    }

    fun encodePublicKey(publicKey: PublicKey): String =
        Base64.getEncoder().encodeToString(publicKey.encoded)

    fun encodePrivateKey(privateKey: PrivateKey): String =
        Base64.getEncoder().encodeToString(privateKey.encoded)

    fun decodePublicKey(encoded: String): PublicKey {
        val keyBytes = Base64.getDecoder().decode(encoded)
        return KeyFactory
            .getInstance(EC_ALGORITHM)
            .generatePublic(X509EncodedKeySpec(keyBytes))
    }

    fun decodePrivateKey(encoded: String): PrivateKey {
        val keyBytes = Base64.getDecoder().decode(encoded)
        return KeyFactory
            .getInstance(EC_ALGORITHM)
            .generatePrivate(PKCS8EncodedKeySpec(keyBytes))
    }

    private fun hkdfSha256(
        inputKeyingMaterial: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        outputLength: Int
    ): ByteArray {
        val extractMac = Mac.getInstance(HKDF_ALGORITHM)
        extractMac.init(SecretKeySpec(salt, HKDF_ALGORITHM))
        val pseudoRandomKey = extractMac.doFinal(inputKeyingMaterial)

        val output = ByteArrayOutputStream()
        var previousBlock = ByteArray(0)
        var counter = 1
        while (output.size() < outputLength) {
            val expandMac = Mac.getInstance(HKDF_ALGORITHM)
            expandMac.init(SecretKeySpec(pseudoRandomKey, HKDF_ALGORITHM))
            expandMac.update(previousBlock)
            expandMac.update(info)
            expandMac.update(counter.toByte())
            previousBlock = expandMac.doFinal()
            output.write(previousBlock)
            counter += 1
        }
        return output.toByteArray().copyOf(outputLength)
    }

    private fun canonicalPublicKeyPair(
        first: PublicKey,
        second: PublicKey
    ): ByteArray {
        val firstBytes = first.encoded
        val secondBytes = second.encoded
        return if (compareUnsigned(firstBytes, secondBytes) <= 0) {
            firstBytes + secondBytes
        } else {
            secondBytes + firstBytes
        }
    }

    private fun compareUnsigned(
        first: ByteArray,
        second: ByteArray
    ): Int {
        val size = minOf(first.size, second.size)
        for (index in 0 until size) {
            val left = first[index].toInt() and 0xff
            val right = second[index].toInt() and 0xff
            if (left != right) {
                return left - right
            }
        }
        return first.size - second.size
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)
}
