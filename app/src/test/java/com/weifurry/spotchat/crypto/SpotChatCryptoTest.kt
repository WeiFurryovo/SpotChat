package com.weifurry.spotchat.crypto

import javax.crypto.AEADBadTagException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test

class SpotChatCryptoTest {
    @Test
    fun derivedSessionKeysCanDecryptBothWays() {
        val alice = SpotChatCrypto.generateIdentity()
        val bob = SpotChatCrypto.generateIdentity()
        val aliceSessionKey = SpotChatCrypto.deriveSessionKey(alice, bob.public)
        val bobSessionKey = SpotChatCrypto.deriveSessionKey(bob, alice.public)
        val associatedData = "message-1".toByteArray()
        val plaintext = "你好，SpotChat".toByteArray()

        val frame =
            SpotChatCrypto.encrypt(
                sessionKey = aliceSessionKey,
                plaintext = plaintext,
                associatedData = associatedData
            )

        assertArrayEquals(
            plaintext,
            SpotChatCrypto.decrypt(
                sessionKey = bobSessionKey,
                frame = frame,
                associatedData = associatedData
            )
        )
        assertEquals(
            SpotChatCrypto.pairingCode(alice.public, bob.public, aliceSessionKey),
            SpotChatCrypto.pairingCode(bob.public, alice.public, bobSessionKey)
        )
    }

    @Test
    fun wrongAssociatedDataIsRejected() {
        val alice = SpotChatCrypto.generateIdentity()
        val bob = SpotChatCrypto.generateIdentity()
        val aliceSessionKey = SpotChatCrypto.deriveSessionKey(alice, bob.public)
        val bobSessionKey = SpotChatCrypto.deriveSessionKey(bob, alice.public)
        val frame =
            SpotChatCrypto.encrypt(
                sessionKey = aliceSessionKey,
                plaintext = "sealed".toByteArray(),
                associatedData = "right-context".toByteArray()
            )

        try {
            SpotChatCrypto.decrypt(
                sessionKey = bobSessionKey,
                frame = frame,
                associatedData = "wrong-context".toByteArray()
            )
            fail("AES-GCM must reject frames when associated data changes")
        } catch (expected: AEADBadTagException) {
            // Expected authentication failure.
        }
    }

    @Test
    fun fingerprintsChangeAcrossIdentities() {
        val alice = SpotChatCrypto.generateIdentity()
        val bob = SpotChatCrypto.generateIdentity()

        assertNotEquals(
            SpotChatCrypto.fingerprint(alice.public),
            SpotChatCrypto.fingerprint(bob.public)
        )
    }

    @Test
    fun fingerprintsKeepFullIdentityButDisplayShort() {
        val identity = SpotChatCrypto.generateIdentity()
        val fingerprint = SpotChatCrypto.fingerprint(identity.public)

        assertEquals(64, fingerprint.length)
        assertEquals(
            fingerprint.chunked(2).take(8).joinToString(separator = " "),
            SpotChatCrypto.displayFingerprint(fingerprint)
        )
    }
}
