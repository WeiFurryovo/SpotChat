package com.weifurry.spotchat.protocol

import com.weifurry.spotchat.crypto.SpotChatCrypto
import com.weifurry.spotchat.domain.DuplicateMessageException
import com.weifurry.spotchat.domain.InMemoryReplayProtection
import com.weifurry.spotchat.domain.ReplayProtectionCapacityException
import com.weifurry.spotchat.domain.SpotChatEngine
import java.util.Base64
import javax.crypto.AEADBadTagException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
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
        watch.confirmSession(trustedPhone.fingerprint)
        phone.confirmSession(trustedWatch.fingerprint)

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
    fun helloPacketsCarryProfileAboutText() {
        val watch = SpotChatEngine("手表", SpotChatCrypto.generateIdentity())
        val phone = SpotChatEngine("手机", SpotChatCrypto.generateIdentity())
        val phoneHello =
            phone.helloPacket(about = "今天方便聊天").hello ?: error("missing phone hello")

        val trustedPhone = watch.openSession(phoneHello)

        assertEquals("今天方便聊天", phoneHello.about)
        assertEquals("今天方便聊天", trustedPhone.about)
    }

    @Test
    fun sessionChallengeConfirmationAuthenticatesResponder() {
        val watch = SpotChatEngine("watch", SpotChatCrypto.generateIdentity())
        val phone = SpotChatEngine("phone", SpotChatCrypto.generateIdentity())
        val phoneHello = phone.helloPacket().hello ?: error("missing phone hello")
        val trustedPhone = watch.openSession(phoneHello)
        val challengePacket =
            watch.sessionChallengePacket(
                responderHello = phoneHello,
                challengeId = "challenge-happy-path",
                createdAtEpochMillis = 1_700_000_000_000L
            )
        val challenge = challengePacket.sessionChallenge ?: error("missing challenge")

        val trustedWatch = phone.openSession(challenge.challengerHello)
        val confirmationPacket =
            phone.encryptSessionConfirmationForPeer(
                peerFingerprint = trustedWatch.fingerprint,
                challenge = challenge,
                confirmedAtEpochMillis = 1_700_000_000_500L
            )
        val encryptedConfirmation =
            confirmationPacket.encryptedMessage ?: error("missing encrypted confirmation")
        val confirmation = watch.decryptSessionConfirmation(encryptedConfirmation)

        assertEquals(PacketKind.SESSION_CHALLENGE, challengePacket.kind)
        assertEquals(watch.localFingerprint, trustedWatch.fingerprint)
        assertEquals(phone.localFingerprint, challenge.responderFingerprint)
        assertEquals(PacketKind.ENCRYPTED_SESSION_CONFIRM, confirmationPacket.kind)
        assertEquals(challenge.challengeId, encryptedConfirmation.messageId)
        assertEquals(challenge.challengeId, confirmation.challengeId)
        assertEquals(watch.sessionChallengeBinding(challenge), confirmation.challengeBinding)
        assertNotEquals(
            watch.sessionChallengeBinding(challenge),
            watch.sessionChallengeBinding(
                challenge.copy(
                    responderHello = challenge.responderHello.copy(deviceName = "tampered")
                )
            )
        )
        assertEquals(watch.localFingerprint, confirmation.challengerFingerprint)
        assertEquals(phone.localFingerprint, confirmation.responderFingerprint)
        assertEquals(1_700_000_000_500L, confirmation.confirmedAtEpochMillis)
    }

    @Test
    fun sessionConfirmationsRejectWrongRecipientKeysAndTampering() {
        val watch = SpotChatEngine("watch", SpotChatCrypto.generateIdentity())
        val phone = SpotChatEngine("phone", SpotChatCrypto.generateIdentity())
        val phoneHello = phone.helloPacket().hello ?: error("missing phone hello")
        watch.openSession(phoneHello)
        val challenge =
            watch.sessionChallengePacket(
                responderHello = phoneHello,
                challengeId = "challenge-authentication"
            ).sessionChallenge ?: error("missing challenge")
        val trustedWatch =
            phone.openSession(challenge.challengerHello)
        val encryptedConfirmation =
            phone
                .encryptSessionConfirmationForPeer(
                    peerFingerprint = trustedWatch.fingerprint,
                    challenge = challenge
                )
                .encryptedMessage
                ?: error("missing encrypted confirmation")

        val wrongWatch = SpotChatEngine("wrong-watch", SpotChatCrypto.generateIdentity())
        wrongWatch.openSession(phone.helloPacket().hello ?: error("missing phone hello"))
        assertThrows(AEADBadTagException::class.java) {
            wrongWatch.decryptSessionConfirmation(encryptedConfirmation)
        }

        val tamperedCiphertext = Base64.getDecoder().decode(encryptedConfirmation.ciphertext)
        tamperedCiphertext[tamperedCiphertext.lastIndex] =
            (tamperedCiphertext.last().toInt() xor 0x01).toByte()
        val tamperedConfirmation =
            encryptedConfirmation.copy(
                ciphertext = Base64.getEncoder().encodeToString(tamperedCiphertext)
            )

        assertThrows(AEADBadTagException::class.java) {
            watch.decryptSessionConfirmation(tamperedConfirmation)
        }
    }

    @Test
    fun versionTwoWirePacketsRoundTripThroughCodec() {
        val watch = SpotChatEngine("watch", SpotChatCrypto.generateIdentity())
        val responder = SpotChatEngine("responder", SpotChatCrypto.generateIdentity())
        val packet =
            watch.sessionChallengePacket(
                responderHello = responder.helloPacket().hello ?: error("missing responder hello"),
                transports = listOf("lan"),
                about = "available",
                challengeId = "challenge-codec-v2",
                createdAtEpochMillis = 1_700_000_000_000L
            )

        val decoded = ChatCodec.decode(ChatCodec.encode(packet))

        assertEquals(WirePacket.CURRENT_VERSION, packet.version)
        assertEquals(2, decoded.version)
        assertEquals(packet, decoded)
    }

    @Test
    fun duplicateEncryptedMessagesAreRejected() {
        val watch = SpotChatEngine("手表", SpotChatCrypto.generateIdentity())
        val phone = SpotChatEngine("手机", SpotChatCrypto.generateIdentity())

        val trustedPhone =
            watch.openSession(phone.helloPacket().hello ?: error("missing phone hello"))
        val trustedWatch = phone.openSession(watch.helloPacket().hello ?: error("missing watch hello"))
        watch.confirmSession(trustedPhone.fingerprint)
        phone.confirmSession(trustedWatch.fingerprint)
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
    fun replayProtectionRejectsTextAfterEngineRestart() {
        val watchIdentity = SpotChatCrypto.generateIdentity()
        val replayProtection = InMemoryReplayProtection()
        val firstWatch = SpotChatEngine("watch", watchIdentity, replayProtection)
        val restartedWatch = SpotChatEngine("watch", watchIdentity, replayProtection)
        val phone = SpotChatEngine("phone", SpotChatCrypto.generateIdentity())
        val phoneHello = phone.helloPacket().hello ?: error("missing phone hello")

        val phoneForFirstWatch = firstWatch.openSession(phoneHello)
        val phoneForRestartedWatch = restartedWatch.openSession(phoneHello)
        val trustedWatch =
            phone.openSession(firstWatch.helloPacket().hello ?: error("missing watch hello"))
        firstWatch.confirmSession(phoneForFirstWatch.fingerprint)
        restartedWatch.confirmSession(phoneForRestartedWatch.fingerprint)
        phone.confirmSession(trustedWatch.fingerprint)
        val encryptedMessage =
            phone
                .encryptTextForPeer(
                    peerFingerprint = trustedWatch.fingerprint,
                    text = "survives restart",
                    messageId = "persistent-text-replay"
                )
                .encryptedMessage
                ?: error("missing encrypted message")

        assertEquals("survives restart", firstWatch.decryptText(encryptedMessage).text)
        assertThrows(DuplicateMessageException::class.java) {
            restartedWatch.decryptText(encryptedMessage)
        }
    }

    @Test
    fun replayProtectionCapacityFailsClosedUntilEntriesExpire() {
        var nowEpochMillis = 1_700_000_000_000L
        val replayProtection =
            InMemoryReplayProtection(
                maxEntries = 1,
                nowEpochMillis = { nowEpochMillis }
            )

        assertEquals(true, replayProtection.markIfNew("sender-a", "message-a"))
        assertThrows(ReplayProtectionCapacityException::class.java) {
            replayProtection.markIfNew("sender-b", "message-b")
        }
        nowEpochMillis += 9L * 24L * 60L * 60L * 1000L
        assertEquals(true, replayProtection.markIfNew("sender-b", "message-b"))
    }

    @Test
    fun encryptedPacketsOutsideReplayWindowAreRejected() {
        val watch = SpotChatEngine("watch", SpotChatCrypto.generateIdentity())
        val phone = SpotChatEngine("phone", SpotChatCrypto.generateIdentity())
        val trustedPhone = watch.openSession(phone.helloPacket().hello ?: error("missing phone hello"))
        val trustedWatch = phone.openSession(watch.helloPacket().hello ?: error("missing watch hello"))
        watch.confirmSession(trustedPhone.fingerprint)
        phone.confirmSession(trustedWatch.fingerprint)
        val staleMessage =
            watch.encryptTextForPeer(
                peerFingerprint = trustedPhone.fingerprint,
                text = "stale",
                sentAtEpochMillis = System.currentTimeMillis() - 8L * 24L * 60L * 60L * 1000L
            ).encryptedMessage ?: error("missing stale message")

        assertThrows(IllegalArgumentException::class.java) {
            phone.decryptText(staleMessage)
        }
    }

    @Test
    fun unauthenticatedSessionsCannotEvictConfirmedSessions() {
        val watch = SpotChatEngine("watch", SpotChatCrypto.generateIdentity())
        val phone = SpotChatEngine("phone", SpotChatCrypto.generateIdentity())
        val trustedPhone = watch.openSession(phone.helloPacket().hello ?: error("missing phone hello"))
        val trustedWatch = phone.openSession(watch.helloPacket().hello ?: error("missing watch hello"))
        watch.confirmSession(trustedPhone.fingerprint)
        phone.confirmSession(trustedWatch.fingerprint)

        repeat(40) { index ->
            val unverified = SpotChatEngine("unverified-$index", SpotChatCrypto.generateIdentity())
            watch.openSession(unverified.helloPacket().hello ?: error("missing unverified hello"))
        }

        val encrypted =
            watch.encryptTextForPeer(trustedPhone.fingerprint, "still confirmed")
                .encryptedMessage ?: error("missing encrypted message")
        assertEquals("still confirmed", phone.decryptText(encrypted).text)
    }

    @Test
    fun repeatedMessageHeadersStillAuthenticateCiphertext() {
        val watch = SpotChatEngine("手表", SpotChatCrypto.generateIdentity())
        val phone = SpotChatEngine("手机", SpotChatCrypto.generateIdentity())

        val trustedPhone =
            watch.openSession(phone.helloPacket().hello ?: error("missing phone hello"))
        val trustedWatch = phone.openSession(watch.helloPacket().hello ?: error("missing watch hello"))
        watch.confirmSession(trustedPhone.fingerprint)
        phone.confirmSession(trustedWatch.fingerprint)
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

        val trustedPhone = watch.openSession(phone.helloPacket().hello ?: error("missing phone hello"))
        val trustedWatch = phone.openSession(watch.helloPacket().hello ?: error("missing watch hello"))
        watch.confirmSession(trustedPhone.fingerprint)
        phone.confirmSession(trustedWatch.fingerprint)
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
        assertEquals(DeliveryReceiptStatus.Delivered, ack.status)
    }

    @Test
    fun encryptedReadAckPacketsCarryReadStatus() {
        val watch = SpotChatEngine("手表", SpotChatCrypto.generateIdentity())
        val phone = SpotChatEngine("手机", SpotChatCrypto.generateIdentity())

        val trustedPhone = watch.openSession(phone.helloPacket().hello ?: error("missing phone hello"))
        val trustedWatch = phone.openSession(watch.helloPacket().hello ?: error("missing watch hello"))
        watch.confirmSession(trustedPhone.fingerprint)
        phone.confirmSession(trustedWatch.fingerprint)
        val ackPacket =
            phone.encryptAckForPeer(
                peerFingerprint = trustedWatch.fingerprint,
                deliveredMessageId = "message-43",
                status = DeliveryReceiptStatus.Read
            )
        val encryptedAck =
            ChatCodec
                .decode(ChatCodec.encode(ackPacket))
                .encryptedMessage
                ?: error("missing encrypted read ack")
        val ack = watch.decryptAck(encryptedAck)

        assertEquals(PacketKind.ENCRYPTED_ACK, ackPacket.kind)
        assertEquals("message-43", ack.messageId)
        assertEquals(DeliveryReceiptStatus.Read, ack.status)
    }

    @Test
    fun replayProtectionRejectsAckAfterEngineRestart() {
        val watchIdentity = SpotChatCrypto.generateIdentity()
        val replayProtection = InMemoryReplayProtection()
        val firstWatch = SpotChatEngine("watch", watchIdentity, replayProtection)
        val restartedWatch = SpotChatEngine("watch", watchIdentity, replayProtection)
        val phone = SpotChatEngine("phone", SpotChatCrypto.generateIdentity())
        val phoneHello = phone.helloPacket().hello ?: error("missing phone hello")

        val phoneForFirstWatch = firstWatch.openSession(phoneHello)
        val phoneForRestartedWatch = restartedWatch.openSession(phoneHello)
        val trustedWatch =
            phone.openSession(firstWatch.helloPacket().hello ?: error("missing watch hello"))
        firstWatch.confirmSession(phoneForFirstWatch.fingerprint)
        restartedWatch.confirmSession(phoneForRestartedWatch.fingerprint)
        phone.confirmSession(trustedWatch.fingerprint)
        val encryptedAck =
            phone
                .encryptAckForPeer(trustedWatch.fingerprint, "message-replay")
                .encryptedMessage
                ?: error("missing encrypted ack")

        firstWatch.decryptAck(encryptedAck)
        assertThrows(DuplicateMessageException::class.java) {
            restartedWatch.decryptAck(encryptedAck)
        }
    }

    @Test
    fun logicalMessageIdIsStableAcrossRecipientsWhileEnvelopeIdsAreUnique() {
        val watch = SpotChatEngine("watch", SpotChatCrypto.generateIdentity())
        val firstPhone = SpotChatEngine("first", SpotChatCrypto.generateIdentity())
        val secondPhone = SpotChatEngine("second", SpotChatCrypto.generateIdentity())
        val firstPeer = watch.openSession(firstPhone.helloPacket().hello ?: error("missing first hello"))
        val secondPeer = watch.openSession(secondPhone.helloPacket().hello ?: error("missing second hello"))
        val watchForFirst = firstPhone.openSession(watch.helloPacket().hello ?: error("missing watch hello"))
        val watchForSecond = secondPhone.openSession(watch.helloPacket().hello ?: error("missing watch hello"))
        watch.confirmSession(firstPeer.fingerprint)
        watch.confirmSession(secondPeer.fingerprint)
        firstPhone.confirmSession(watchForFirst.fingerprint)
        secondPhone.confirmSession(watchForSecond.fingerprint)

        val logicalMessageId = "logical-message-42"
        val firstPacket =
            watch.encryptTextForPeer(
                peerFingerprint = firstPeer.fingerprint,
                text = "group message",
                messageId = logicalMessageId
            )
        val secondPacket =
            watch.encryptTextForPeer(
                peerFingerprint = secondPeer.fingerprint,
                text = "group message",
                messageId = logicalMessageId
            )

        val firstEncrypted = firstPacket.encryptedMessage ?: error("missing first encrypted message")
        val secondEncrypted = secondPacket.encryptedMessage ?: error("missing second encrypted message")
        assertEquals(logicalMessageId, firstPhone.decryptText(firstEncrypted).messageId)
        assertEquals(logicalMessageId, secondPhone.decryptText(secondEncrypted).messageId)
        assertNotEquals(logicalMessageId, firstEncrypted.messageId)
        assertNotEquals(firstEncrypted.messageId, secondEncrypted.messageId)
        assertNotEquals(firstPacket.encryptedMessage?.ciphertext, secondPacket.encryptedMessage?.ciphertext)
    }

    @Test
    fun editedRetryUsesFreshEnvelopeButKeepsLogicalMessageId() {
        val watch = SpotChatEngine("watch", SpotChatCrypto.generateIdentity())
        val phone = SpotChatEngine("phone", SpotChatCrypto.generateIdentity())
        val trustedPhone = watch.openSession(phone.helloPacket().hello ?: error("missing phone hello"))
        val trustedWatch = phone.openSession(watch.helloPacket().hello ?: error("missing watch hello"))
        watch.confirmSession(trustedPhone.fingerprint)
        phone.confirmSession(trustedWatch.fingerprint)

        val original =
            watch.encryptTextForPeer(trustedPhone.fingerprint, "original", messageId = "logical-edit")
                .encryptedMessage ?: error("missing original")
        val edited =
            watch.encryptTextForPeer(trustedPhone.fingerprint, "edited", messageId = "logical-edit")
                .encryptedMessage ?: error("missing edit")

        val originalPlain = phone.decryptText(original)
        val editedPlain = phone.decryptText(edited)
        assertNotEquals(original.messageId, edited.messageId)
        assertEquals("logical-edit", originalPlain.messageId)
        assertEquals("logical-edit", editedPlain.messageId)
        assertEquals("edited", editedPlain.text)
    }

    @Test
    fun encryptedVoicePacketsCarryAudioPayload() {
        val watch = SpotChatEngine("手表", SpotChatCrypto.generateIdentity())
        val phone = SpotChatEngine("手机", SpotChatCrypto.generateIdentity())

        val trustedPhone =
            watch.openSession(phone.helloPacket().hello ?: error("missing phone hello"))
        val trustedWatch = phone.openSession(watch.helloPacket().hello ?: error("missing watch hello"))
        watch.confirmSession(trustedPhone.fingerprint)
        phone.confirmSession(trustedWatch.fingerprint)
        val audioBytes = byteArrayOf(1, 3, 5, 7, 9)
        val voicePacket =
            watch.encryptVoiceForPeer(
                peerFingerprint = trustedPhone.fingerprint,
                audioBytes = audioBytes,
                durationMs = 1_250L,
                groupId = "group:nearby",
                groupName = "Nearby"
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
        assertEquals("group:nearby", plain.groupId)
        assertEquals("Nearby", plain.groupName)
        assertArrayEquals(audioBytes, plain.audioBytes)
    }

    @Test
    fun encryptedReactionPacketsCarryTargetAndEmoji() {
        val watch = SpotChatEngine("手表", SpotChatCrypto.generateIdentity())
        val phone = SpotChatEngine("手机", SpotChatCrypto.generateIdentity())

        val trustedPhone =
            watch.openSession(phone.helloPacket().hello ?: error("missing phone hello"))
        val trustedWatch = phone.openSession(watch.helloPacket().hello ?: error("missing watch hello"))
        watch.confirmSession(trustedPhone.fingerprint)
        phone.confirmSession(trustedWatch.fingerprint)
        val reactionPacket =
            watch.encryptReactionForPeer(
                peerFingerprint = trustedPhone.fingerprint,
                targetMessageId = "message-99",
                emoji = "like"
            )
        val encryptedReaction =
            ChatCodec
                .decode(ChatCodec.encode(reactionPacket))
                .encryptedMessage
                ?: error("missing encrypted reaction")
        val plain = phone.decryptReaction(encryptedReaction)

        assertEquals(PacketKind.ENCRYPTED_REACTION, reactionPacket.kind)
        assertEquals(watch.localFingerprint, plain.senderFingerprint)
        assertEquals("message-99", plain.targetMessageId)
        assertEquals("like", plain.emoji)
    }

    @Test
    fun relayEnvelopesCarryOnlyEncryptedPacketsWithoutChangingCiphertext() {
        val watch = SpotChatEngine("手表", SpotChatCrypto.generateIdentity())
        val phone = SpotChatEngine("手机", SpotChatCrypto.generateIdentity())

        val trustedPhone =
            watch.openSession(phone.helloPacket().hello ?: error("missing phone hello"))
        val trustedWatch = phone.openSession(watch.helloPacket().hello ?: error("missing watch hello"))
        watch.confirmSession(trustedPhone.fingerprint)
        phone.confirmSession(trustedWatch.fingerprint)
        val packet =
            watch.encryptTextForPeer(
                peerFingerprint = trustedPhone.fingerprint,
                text = "通过中继也不能让服务器看到明文",
                sentAtEpochMillis = System.currentTimeMillis()
            )
        val encrypted = packet.encryptedMessage ?: error("missing encrypted message")
        val envelope =
            RelayEnvelope.fromEncryptedPacket(
                packet = packet,
                recipientFingerprint = trustedPhone.fingerprint,
                ttlMillis = 60_000L
            )

        val decodedEnvelope =
            ChatCodec.decodeRelayEnvelope(
                ChatCodec.encodeRelayEnvelope(envelope)
            )
        val relayedMessage =
            decodedEnvelope.packet.encryptedMessage ?: error("missing relayed encrypted message")
        val plain = phone.decryptText(relayedMessage)

        assertEquals(PacketKind.ENCRYPTED_MESSAGE, decodedEnvelope.packet.kind)
        assertEquals(watch.localFingerprint, decodedEnvelope.senderFingerprint)
        assertEquals(trustedPhone.fingerprint, decodedEnvelope.recipientFingerprint)
        assertEquals(encrypted.messageId, relayedMessage.messageId)
        assertEquals(encrypted.nonce, relayedMessage.nonce)
        assertEquals(encrypted.ciphertext, relayedMessage.ciphertext)
        assertEquals(
            "通过中继也不能让服务器看到明文",
            plain.text
        )
    }

    @Test
    fun relayEnvelopesRejectPlainHelloPackets() {
        val watch = SpotChatEngine("手表", SpotChatCrypto.generateIdentity())
        val helloPacket = watch.helloPacket()

        assertThrows(IllegalArgumentException::class.java) {
            RelayEnvelope(
                envelopeId = "relay:hello",
                senderFingerprint = watch.localFingerprint,
                recipientFingerprint = "phone",
                createdAtEpochMillis = 1_700_000_000_000L,
                expiresAtEpochMillis = 1_700_000_060_000L,
                packet = helloPacket
            )
        }
    }
}
