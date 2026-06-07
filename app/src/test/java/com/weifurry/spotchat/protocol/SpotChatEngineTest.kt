package com.weifurry.spotchat.protocol

import com.weifurry.spotchat.crypto.SpotChatCrypto
import com.weifurry.spotchat.domain.DuplicateMessageException
import com.weifurry.spotchat.domain.SpotChatEngine
import java.util.Base64
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.fail
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
    fun duplicateEncryptedMessagesAreRejected() {
        val watch = SpotChatEngine("手表", SpotChatCrypto.generateIdentity())
        val phone = SpotChatEngine("手机", SpotChatCrypto.generateIdentity())

        val trustedPhone =
            watch.openSession(phone.helloPacket().hello ?: error("missing phone hello"))
        phone.openSession(watch.helloPacket().hello ?: error("missing watch hello"))
        val message =
            ChatCodec
                .decode(
                    ChatCodec.encode(
                        watch.encryptTextForPeer(trustedPhone.fingerprint, "只收一次")
                    )
                )
                .encryptedMessage
                ?: error("missing encrypted message")

        assertEquals("只收一次", phone.decryptText(message).text)
        try {
            phone.decryptText(message)
            fail("duplicate encrypted messages must be rejected")
        } catch (expected: DuplicateMessageException) {
            assertEquals(message.messageId, expected.messageId)
        }
    }

    @Test
    fun repeatedMessageHeadersStillAuthenticateCiphertext() {
        val watch = SpotChatEngine("手表", SpotChatCrypto.generateIdentity())
        val phone = SpotChatEngine("手机", SpotChatCrypto.generateIdentity())

        val trustedPhone =
            watch.openSession(phone.helloPacket().hello ?: error("missing phone hello"))
        phone.openSession(watch.helloPacket().hello ?: error("missing watch hello"))
        val message =
            ChatCodec
                .decode(
                    ChatCodec.encode(
                        watch.encryptTextForPeer(trustedPhone.fingerprint, "认证优先")
                    )
                )
                .encryptedMessage
                ?: error("missing encrypted message")

        assertEquals("认证优先", phone.decryptText(message).text)

        val tamperedCiphertext = Base64.getDecoder().decode(message.ciphertext)
        tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0x01).toByte()
        val tamperedMessage =
            message.copy(
                ciphertext = Base64.getEncoder().encodeToString(tamperedCiphertext)
            )

        try {
            phone.decryptText(tamperedMessage)
            fail("tampered duplicate headers must not bypass ciphertext authentication")
        } catch (expected: AEADBadTagException) {
            // Expected authentication failure before duplicate detection.
        }
    }

    @Test
    fun encryptedAckPacketsCarryDeliveredMessageId() {
        val watch = SpotChatEngine("手表", SpotChatCrypto.generateIdentity())
        val phone = SpotChatEngine("手机", SpotChatCrypto.generateIdentity())

        watch.openSession(phone.helloPacket().hello ?: error("missing phone hello"))
        val trustedWatch = phone.openSession(watch.helloPacket().hello ?: error("missing watch hello"))
        val ackPacket = phone.encryptAckForPeer(trustedWatch.fingerprint, "message-42")
        val encryptedAck =
            ChatCodec
                .decode(ChatCodec.encode(ackPacket))
                .encryptedMessage
                ?: error("missing encrypted ack")
        val ack = watch.decryptAck(encryptedAck)

        assertEquals(PacketKind.ENCRYPTED_ACK, ackPacket.kind)
        assertEquals(phone.localFingerprint, encryptedAck.senderFingerprint)
        assertEquals("message-42", ack.messageId)
    }

    @Test
    fun encryptedVoicePacketsCarryAudioPayload() {
        val watch = SpotChatEngine("手表", SpotChatCrypto.generateIdentity())
        val phone = SpotChatEngine("手机", SpotChatCrypto.generateIdentity())

        val trustedPhone =
            watch.openSession(phone.helloPacket().hello ?: error("missing phone hello"))
        phone.openSession(watch.helloPacket().hello ?: error("missing watch hello"))
        val audioBytes = byteArrayOf(1, 3, 5, 7, 9)
        val voicePacket =
            watch.encryptVoiceForPeer(
                peerFingerprint = trustedPhone.fingerprint,
                audioBytes = audioBytes,
                durationMs = 1_250L
            )
        val encryptedVoice =
            ChatCodec
                .decode(ChatCodec.encode(voicePacket))
                .encryptedMessage
                ?: error("missing encrypted voice")
        val plain = phone.decryptVoice(encryptedVoice)

        assertEquals(PacketKind.ENCRYPTED_VOICE_MESSAGE, voicePacket.kind)
        assertEquals(watch.localFingerprint, plain.senderFingerprint)
        assertEquals(SpotChatEngine.VOICE_CODEC_AAC, plain.codec)
        assertEquals(1_250L, plain.durationMs)
        assertArrayEquals(audioBytes, plain.audioBytes)
    }
}
