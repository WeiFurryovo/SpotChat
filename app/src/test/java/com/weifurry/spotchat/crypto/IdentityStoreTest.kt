package com.weifurry.spotchat.crypto

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.KeyPair

/**
 * Unit tests for identity key encoding/decoding.
 *
 * IdentityStore persistence (SharedPreferences + AndroidKeyStore) requires
 * an instrumented test with a real Android Context.
 */
class IdentityStoreTest {
    @Test
    fun roundTripEncodesAndDecodesIdentity() {
        val original = SpotChatCrypto.generateIdentity()
        val encodedPublic = SpotChatCrypto.encodePublicKey(original.public)
        val encodedPrivate = SpotChatCrypto.encodePrivateKey(original.private)

        val decodedPublic = SpotChatCrypto.decodePublicKey(encodedPublic)
        val decodedPrivate = SpotChatCrypto.decodePrivateKey(encodedPrivate)
        val restored = KeyPair(decodedPublic, decodedPrivate)

        // Verify same fingerprint means same public key
        assertEquals(
            SpotChatCrypto.fingerprint(original.public),
            SpotChatCrypto.fingerprint(restored.public)
        )

        // Verify same private key by deriving the same session key
        val testPeer = SpotChatCrypto.generateIdentity()
        val sessionOriginal = SpotChatCrypto.deriveSessionKey(original, testPeer.public)
        val sessionRestored = SpotChatCrypto.deriveSessionKey(restored, testPeer.public)

        val plaintext = "test message".toByteArray()
        val frameFromOriginal = SpotChatCrypto.encrypt(sessionOriginal, plaintext)
        val decryptedWithRestored = SpotChatCrypto.decrypt(sessionRestored, frameFromOriginal)

        assertEquals(String(plaintext), String(decryptedWithRestored))
    }
}
