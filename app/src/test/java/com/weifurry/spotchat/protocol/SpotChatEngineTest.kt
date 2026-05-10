package com.weifurry.spotchat.protocol

import com.weifurry.spotchat.crypto.SpotChatCrypto
import com.weifurry.spotchat.domain.SpotChatEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SpotChatEngineTest {
    @Test
    fun helloPacketsOpenMatchedEncryptedSessions() {
        val watch = SpotChatEngine("手表", SpotChatCrypto.generateIdentity())
        val phone = SpotChatEngine("手机", SpotChatCrypto.generateIdentity())

        val watchHello = watch.helloPacket().hello ?: error("missing watch hello")
        val phoneHello = phone.helloPacket().hello ?: error("missing phone hello")
        val trustedPhone = watch.openSession(phoneHello)
        val trustedWatch = phone.openSession(watchHello)

        assertEquals(trustedPhone.pairingCode, trustedWatch.pairingCode)
        assertNotEquals(trustedPhone.fingerprint, trustedWatch.fingerprint)
        assertEquals(phoneHello.publicKey, trustedPhone.publicKey)

        val encrypted = watch.encryptTextForPeer(trustedPhone.fingerprint, "局域网可用")
        val decoded =
            ChatCodec
                .decode(ChatCodec.encode(encrypted))
                .encryptedMessage
                ?: error("missing encrypted message")
        val plain = phone.decryptText(decoded)

        assertEquals("局域网可用", plain.text)
        assertEquals(watch.localFingerprint, plain.senderFingerprint)
    }

    @Test
    fun ackPacketsCarryDeliveredMessageId() {
        val watch = SpotChatEngine("手表", SpotChatCrypto.generateIdentity())

        val ack = watch.ackPacket("message-42").ack

        assertNotNull(ack)
        assertEquals("message-42", ack?.messageId)
    }
}
