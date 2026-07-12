package com.weifurry.spotchat.protocol

import com.weifurry.spotchat.crypto.SpotChatCrypto
import com.weifurry.spotchat.domain.AuthenticatedPayloadDecodingException
import com.weifurry.spotchat.domain.DuplicateMessageException
import com.weifurry.spotchat.domain.EncryptedChatStateStore
import com.weifurry.spotchat.domain.InMemoryReplayProtection
import com.weifurry.spotchat.domain.ReplayProtection
import com.weifurry.spotchat.domain.ReplayProtectionCapacityException
import com.weifurry.spotchat.domain.SpotChatEngine
import java.security.KeyPair
import java.util.Base64
import javax.crypto.AEADBadTagException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    fun handshakePacketsOmitProfileAboutTextByDefault() {
        val watch = SpotChatEngine("watch", SpotChatCrypto.generateIdentity())
        val phone = SpotChatEngine("phone", SpotChatCrypto.generateIdentity())
        val phoneHello = phone.helloPacket().hello ?: error("missing phone hello")
        val watchHello = watch.helloPacket().hello ?: error("missing watch hello")
        val challenge =
            watch.sessionChallengePacket(
                responderHello = phoneHello
            ).sessionChallenge ?: error("missing challenge")

        assertEquals("", phoneHello.about)
        assertEquals("", watchHello.about)
        assertEquals("", challenge.challengerHello.about)
        assertEquals("", challenge.responderHello.about)
    }

    @Test
    fun sessionChallengeConfirmationAuthenticatesResponder() {
        val watch = SpotChatEngine("watch", SpotChatCrypto.generateIdentity())
        val phone = SpotChatEngine("phone", SpotChatCrypto.generateIdentity())
        val phoneHello = phone.helloPacket().hello ?: error("missing phone hello")
        val trustedPhone = watch.openSession(phoneHello)
        val nowEpochMillis = System.currentTimeMillis()
        val challengePacket =
            watch.sessionChallengePacket(
                responderHello = phoneHello,
                challengeId = "challenge-happy-path",
                createdAtEpochMillis = nowEpochMillis
            )
        val challenge = challengePacket.sessionChallenge ?: error("missing challenge")

        val trustedWatch = phone.openSession(challenge.challengerHello)
        val confirmationPacket =
            phone.encryptSessionConfirmationForPeer(
                peerFingerprint = trustedWatch.fingerprint,
                challenge = challenge,
                confirmedAtEpochMillis = nowEpochMillis + 500L
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
        assertEquals(nowEpochMillis + 500L, confirmation.confirmedAtEpochMillis)
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
    fun deferredReplayDecryptionDoesNotRecordUntilExplicitlyRemembered() {
        val replayProtection = RecordingReplayProtection()
        val watch = SpotChatEngine("watch", SpotChatCrypto.generateIdentity())
        val phone =
            SpotChatEngine(
                "phone",
                SpotChatCrypto.generateIdentity(),
                replayProtection
            )
        val trustedPhone = watch.openSession(phone.helloPacket().hello ?: error("missing phone hello"))
        val trustedWatch = phone.openSession(watch.helloPacket().hello ?: error("missing watch hello"))
        watch.confirmSession(trustedPhone.fingerprint)
        phone.confirmSession(trustedWatch.fingerprint)
        val message =
            watch
                .encryptTextForPeer(trustedPhone.fingerprint, "deferred replay")
                .encryptedMessage
                ?: error("missing encrypted message")

        assertEquals("deferred replay", phone.decryptText(message, rememberReplay = false).text)
        phone.ensureAuthenticatedPacketIsNew(message)
        assertEquals("deferred replay", phone.decryptText(message, rememberReplay = false).text)
        phone.ensureAuthenticatedPacketIsNew(message)

        assertEquals(0, replayProtection.markCalls)
        assertEquals(false, replayProtection.hasSeen(message.senderFingerprint, message.messageId))
    }

    @Test
    fun deferredMalformedAuthenticatedPayloadsRequireExplicitReplayCommit() {
        val replayProtection = RecordingReplayProtection()
        val watchIdentity = SpotChatCrypto.generateIdentity()
        val phoneIdentity = SpotChatCrypto.generateIdentity()
        val watch = SpotChatEngine("watch", watchIdentity)
        val phone = SpotChatEngine("phone", phoneIdentity, replayProtection)
        val trustedPhone = watch.openSession(phone.helloPacket().hello ?: error("missing phone hello"))
        val trustedWatch = phone.openSession(watch.helloPacket().hello ?: error("missing watch hello"))
        watch.confirmSession(trustedPhone.fingerprint)
        phone.confirmSession(trustedWatch.fingerprint)

        val textMessage =
            authenticatedPayload(
                senderIdentity = watchIdentity,
                recipientIdentity = phoneIdentity,
                kind = PacketKind.ENCRYPTED_MESSAGE,
                plaintext = "{",
                messageId = "malformed-text"
            )
        val voiceMessage =
            authenticatedPayload(
                senderIdentity = watchIdentity,
                recipientIdentity = phoneIdentity,
                kind = PacketKind.ENCRYPTED_VOICE_MESSAGE,
                plaintext = "{",
                messageId = "malformed-voice"
            )
        val reactionMessage =
            authenticatedPayload(
                senderIdentity = watchIdentity,
                recipientIdentity = phoneIdentity,
                kind = PacketKind.ENCRYPTED_REACTION,
                plaintext = "{",
                messageId = "malformed-reaction"
            )
        val ackMessage =
            authenticatedPayload(
                senderIdentity = watchIdentity,
                recipientIdentity = phoneIdentity,
                kind = PacketKind.ENCRYPTED_ACK,
                plaintext = "{",
                messageId = "malformed-ack"
            )

        fun assertDeferredFailure(
            message: EncryptedChatMessage,
            decrypt: () -> Unit
        ) {
            val error =
                assertThrows(AuthenticatedPayloadDecodingException::class.java) {
                    decrypt()
                }
            assertEquals(message.messageId, error.messageId)
            assertEquals(message.senderFingerprint, error.senderFingerprint)
        }

        assertDeferredFailure(textMessage) {
            phone.decryptText(textMessage, rememberReplay = false)
        }
        assertDeferredFailure(voiceMessage) {
            phone.decryptVoice(voiceMessage, rememberReplay = false)
        }
        assertDeferredFailure(reactionMessage) {
            phone.decryptReaction(reactionMessage, rememberReplay = false)
        }
        assertDeferredFailure(ackMessage) {
            phone.decryptAck(ackMessage, rememberReplay = false)
        }
        assertEquals(0, replayProtection.markCalls)

        listOf(textMessage, voiceMessage, reactionMessage, ackMessage).forEach { message ->
            phone.rememberAuthenticatedPacket(message)
            assertEquals(
                true,
                replayProtection.hasSeen(message.senderFingerprint, message.messageId)
            )
        }
        assertEquals(4, replayProtection.markCalls)
    }

    @Test
    fun tamperedAuthenticatedEnvelopeIsNotClassifiedAsPayloadDecodingFailure() {
        val replayProtection = RecordingReplayProtection()
        val watchIdentity = SpotChatCrypto.generateIdentity()
        val phoneIdentity = SpotChatCrypto.generateIdentity()
        val watch = SpotChatEngine("watch", watchIdentity)
        val phone = SpotChatEngine("phone", phoneIdentity, replayProtection)
        val trustedPhone = watch.openSession(phone.helloPacket().hello ?: error("missing phone hello"))
        val trustedWatch = phone.openSession(watch.helloPacket().hello ?: error("missing watch hello"))
        watch.confirmSession(trustedPhone.fingerprint)
        phone.confirmSession(trustedWatch.fingerprint)
        val authenticatedMessage =
            authenticatedPayload(
                senderIdentity = watchIdentity,
                recipientIdentity = phoneIdentity,
                kind = PacketKind.ENCRYPTED_MESSAGE,
                plaintext = "{",
                messageId = "tampered-malformed-text"
            )
        val tamperedCiphertext = Base64.getDecoder().decode(authenticatedMessage.ciphertext)
        tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 1).toByte()
        val tamperedMessage =
            authenticatedMessage.copy(
                ciphertext = Base64.getEncoder().encodeToString(tamperedCiphertext)
            )

        assertThrows(AEADBadTagException::class.java) {
            phone.decryptText(tamperedMessage, rememberReplay = false)
        }
        assertEquals(0, replayProtection.markCalls)
    }

    @Test
    fun protocolPayloadBoundsAcceptLimitsAndRejectOutsideValues() {
        val watch = SpotChatEngine("watch", SpotChatCrypto.generateIdentity())
        val phone = SpotChatEngine("phone", SpotChatCrypto.generateIdentity())
        val trustedPhone = watch.openSession(phone.helloPacket().hello ?: error("missing phone hello"))
        val trustedWatch = phone.openSession(watch.helloPacket().hello ?: error("missing watch hello"))
        watch.confirmSession(trustedPhone.fingerprint)
        phone.confirmSession(trustedWatch.fingerprint)
        val maximumVoiceBytes = EncryptedChatStateStore.MAX_SINGLE_VOICE_BYTES
        val maximumVoiceDurationMs = 10L * 60L * 1_000L

        val textAtLimit =
            watch
                .encryptTextForPeer(trustedPhone.fingerprint, "x".repeat(1_024))
                .encryptedMessage
                ?: error("missing text at limit")
        assertEquals(1_024, phone.decryptText(textAtLimit).text.length)

        val minimumVoice =
            watch
                .encryptVoiceForPeer(
                    peerFingerprint = trustedPhone.fingerprint,
                    audioBytes = byteArrayOf(1),
                    durationMs = 1L
                ).encryptedMessage
                ?: error("missing minimum voice")
        assertArrayEquals(byteArrayOf(1), phone.decryptVoice(minimumVoice).audioBytes)
        val maximumVoice =
            watch
                .encryptVoiceForPeer(
                    peerFingerprint = trustedPhone.fingerprint,
                    audioBytes = ByteArray(maximumVoiceBytes) { 1 },
                    durationMs = maximumVoiceDurationMs
                ).encryptedMessage
                ?: error("missing maximum voice")
        val maximumPlainVoice = phone.decryptVoice(maximumVoice)
        assertEquals(SpotChatEngine.VOICE_CODEC_AAC, maximumPlainVoice.codec)
        assertEquals(maximumVoiceDurationMs, maximumPlainVoice.durationMs)
        assertEquals(maximumVoiceBytes, maximumPlainVoice.audioBytes.size)

        val reactionAtLimit =
            watch
                .encryptReactionForPeer(
                    peerFingerprint = trustedPhone.fingerprint,
                    targetMessageId = "m".repeat(128),
                    emoji = "e".repeat(32)
                ).encryptedMessage
                ?: error("missing reaction at limit")
        assertEquals(32, phone.decryptReaction(reactionAtLimit).emoji.length)

        val ackMessageIdAtLimit = "a".repeat(128)
        val ackAtLimit =
            watch
                .encryptAckForPeer(
                    peerFingerprint = trustedPhone.fingerprint,
                    deliveredMessageId = ackMessageIdAtLimit
                ).encryptedMessage
                ?: error("missing ack at limit")
        assertEquals(ackMessageIdAtLimit, phone.decryptAck(ackAtLimit).messageId)
        assertEquals(
            PacketKind.ENCRYPTED_ACK,
            watch.encryptAckForPeer(
                peerFingerprint = trustedPhone.fingerprint,
                deliveredMessageId = "timestamp-zero",
                receivedAtEpochMillis = 0L
            ).kind
        )

        assertThrows(IllegalArgumentException::class.java) {
            watch.encryptTextForPeer(trustedPhone.fingerprint, " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            watch.encryptTextForPeer(trustedPhone.fingerprint, "x".repeat(1_025))
        }
        assertThrows(IllegalArgumentException::class.java) {
            watch.encryptVoiceForPeer(
                trustedPhone.fingerprint,
                ByteArray(0),
                durationMs = 1L
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            watch.encryptVoiceForPeer(
                trustedPhone.fingerprint,
                ByteArray(maximumVoiceBytes + 1),
                durationMs = 1L
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            watch.encryptVoiceForPeer(
                trustedPhone.fingerprint,
                byteArrayOf(1),
                durationMs = 0L
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            watch.encryptVoiceForPeer(
                trustedPhone.fingerprint,
                byteArrayOf(1),
                durationMs = maximumVoiceDurationMs + 1L
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            watch.encryptVoiceForPeer(
                trustedPhone.fingerprint,
                byteArrayOf(1),
                durationMs = 1L,
                codec = "opus"
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            watch.encryptReactionForPeer(trustedPhone.fingerprint, " ", "ok")
        }
        assertThrows(IllegalArgumentException::class.java) {
            watch.encryptReactionForPeer(trustedPhone.fingerprint, "m".repeat(129), "ok")
        }
        assertThrows(IllegalArgumentException::class.java) {
            watch.encryptReactionForPeer(trustedPhone.fingerprint, "message", " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            watch.encryptReactionForPeer(trustedPhone.fingerprint, "message", "e".repeat(33))
        }
        assertThrows(IllegalArgumentException::class.java) {
            watch.encryptAckForPeer(trustedPhone.fingerprint, " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            watch.encryptAckForPeer(trustedPhone.fingerprint, "a".repeat(129))
        }
        assertThrows(IllegalArgumentException::class.java) {
            watch.encryptAckForPeer(
                trustedPhone.fingerprint,
                "message",
                receivedAtEpochMillis = -1L
            )
        }
    }

    @Test
    fun deferredAuthenticatedPayloadBoundaryFailuresAreWrappedWithoutReplayCommit() {
        val replayProtection = RecordingReplayProtection()
        val watchIdentity = SpotChatCrypto.generateIdentity()
        val phoneIdentity = SpotChatCrypto.generateIdentity()
        val watch = SpotChatEngine("watch", watchIdentity)
        val phone = SpotChatEngine("phone", phoneIdentity, replayProtection)
        val trustedWatch = phone.openSession(watch.helloPacket().hello ?: error("missing watch hello"))
        phone.confirmSession(trustedWatch.fingerprint)
        val json = Json { encodeDefaults = true }
        val maximumVoiceBytes = EncryptedChatStateStore.MAX_SINGLE_VOICE_BYTES
        val maximumVoiceDurationMs = 10L * 60L * 1_000L
        val maximumVoiceBase64Chars = ((maximumVoiceBytes + 2) / 3) * 4
        val validAudioBase64 = Base64.getEncoder().encodeToString(byteArrayOf(1))
        val invalidPayloads =
            listOf(
                Triple(
                    "text-blank",
                    PacketKind.ENCRYPTED_MESSAGE,
                    json.encodeToString(TextMessagePayload("text-id", " "))
                ),
                Triple(
                    "text-too-long",
                    PacketKind.ENCRYPTED_MESSAGE,
                    json.encodeToString(TextMessagePayload("text-id", "x".repeat(1_025)))
                ),
                Triple(
                    "voice-codec",
                    PacketKind.ENCRYPTED_VOICE_MESSAGE,
                    json.encodeToString(
                        VoiceMessagePayload("voice-id", "opus", 1L, validAudioBase64)
                    )
                ),
                Triple(
                    "voice-duration-min",
                    PacketKind.ENCRYPTED_VOICE_MESSAGE,
                    json.encodeToString(
                        VoiceMessagePayload(
                            "voice-id",
                            SpotChatEngine.VOICE_CODEC_AAC,
                            0L,
                            validAudioBase64
                        )
                    )
                ),
                Triple(
                    "voice-duration-max",
                    PacketKind.ENCRYPTED_VOICE_MESSAGE,
                    json.encodeToString(
                        VoiceMessagePayload(
                            "voice-id",
                            SpotChatEngine.VOICE_CODEC_AAC,
                            maximumVoiceDurationMs + 1L,
                            validAudioBase64
                        )
                    )
                ),
                Triple(
                    "voice-audio-empty",
                    PacketKind.ENCRYPTED_VOICE_MESSAGE,
                    json.encodeToString(
                        VoiceMessagePayload(
                            "voice-id",
                            SpotChatEngine.VOICE_CODEC_AAC,
                            1L,
                            ""
                        )
                    )
                ),
                Triple(
                    "voice-audio-decoded-size",
                    PacketKind.ENCRYPTED_VOICE_MESSAGE,
                    json.encodeToString(
                        VoiceMessagePayload(
                            "voice-id",
                            SpotChatEngine.VOICE_CODEC_AAC,
                            1L,
                            Base64.getEncoder().encodeToString(ByteArray(maximumVoiceBytes + 1))
                        )
                    )
                ),
                Triple(
                    "voice-audio-encoded-length",
                    PacketKind.ENCRYPTED_VOICE_MESSAGE,
                    json.encodeToString(
                        VoiceMessagePayload(
                            "voice-id",
                            SpotChatEngine.VOICE_CODEC_AAC,
                            1L,
                            "!".repeat(maximumVoiceBase64Chars + 1)
                        )
                    )
                ),
                Triple(
                    "reaction-target-blank",
                    PacketKind.ENCRYPTED_REACTION,
                    json.encodeToString(ReactionPayload(" ", "ok"))
                ),
                Triple(
                    "reaction-target-too-long",
                    PacketKind.ENCRYPTED_REACTION,
                    json.encodeToString(ReactionPayload("m".repeat(129), "ok"))
                ),
                Triple(
                    "reaction-emoji-blank",
                    PacketKind.ENCRYPTED_REACTION,
                    json.encodeToString(ReactionPayload("message", " "))
                ),
                Triple(
                    "reaction-emoji-too-long",
                    PacketKind.ENCRYPTED_REACTION,
                    json.encodeToString(ReactionPayload("message", "e".repeat(33)))
                ),
                Triple(
                    "ack-id-blank",
                    PacketKind.ENCRYPTED_ACK,
                    json.encodeToString(DeliveryAck(" ", 0L))
                ),
                Triple(
                    "ack-id-too-long",
                    PacketKind.ENCRYPTED_ACK,
                    json.encodeToString(DeliveryAck("a".repeat(129), 0L))
                ),
                Triple(
                    "ack-timestamp",
                    PacketKind.ENCRYPTED_ACK,
                    json.encodeToString(DeliveryAck("message", -1L))
                )
            )

        invalidPayloads.forEach { (caseName, kind, plaintext) ->
            val message =
                authenticatedPayload(
                    senderIdentity = watchIdentity,
                    recipientIdentity = phoneIdentity,
                    kind = kind,
                    plaintext = plaintext,
                    messageId = "invalid-$caseName"
                )
            val error =
                assertThrows(AuthenticatedPayloadDecodingException::class.java) {
                    when (kind) {
                        PacketKind.ENCRYPTED_MESSAGE ->
                            phone.decryptText(message, rememberReplay = false)
                        PacketKind.ENCRYPTED_VOICE_MESSAGE ->
                            phone.decryptVoice(message, rememberReplay = false)
                        PacketKind.ENCRYPTED_REACTION ->
                            phone.decryptReaction(message, rememberReplay = false)
                        PacketKind.ENCRYPTED_ACK ->
                            phone.decryptAck(message, rememberReplay = false)
                        else -> error("unsupported test packet kind")
                    }
                }
            assertEquals(message.messageId, error.messageId)
            if (caseName == "voice-audio-encoded-length") {
                assertEquals("Invalid voice audio encoding", error.cause?.message)
            }
        }
        assertEquals(0, replayProtection.markCalls)
    }

    @Test
    fun rememberedAuthenticatedPacketRejectsDefaultDecryptAndRepeatedRemember() {
        val replayProtection = RecordingReplayProtection()
        val watch = SpotChatEngine("watch", SpotChatCrypto.generateIdentity())
        val phone =
            SpotChatEngine(
                "phone",
                SpotChatCrypto.generateIdentity(),
                replayProtection
            )
        val trustedPhone = watch.openSession(phone.helloPacket().hello ?: error("missing phone hello"))
        val trustedWatch = phone.openSession(watch.helloPacket().hello ?: error("missing watch hello"))
        watch.confirmSession(trustedPhone.fingerprint)
        phone.confirmSession(trustedWatch.fingerprint)
        val message =
            watch
                .encryptTextForPeer(trustedPhone.fingerprint, "persist before replay")
                .encryptedMessage
                ?: error("missing encrypted message")

        assertEquals(
            "persist before replay",
            phone.decryptText(message, rememberReplay = false).text
        )
        phone.rememberAuthenticatedPacket(message)

        assertEquals(1, replayProtection.markCalls)
        assertEquals(true, replayProtection.hasSeen(message.senderFingerprint, message.messageId))
        assertThrows(DuplicateMessageException::class.java) {
            phone.ensureAuthenticatedPacketIsNew(message)
        }
        assertEquals(1, replayProtection.markCalls)
        assertThrows(DuplicateMessageException::class.java) {
            phone.decryptText(message)
        }
        assertThrows(DuplicateMessageException::class.java) {
            phone.rememberAuthenticatedPacket(message)
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
    fun replayPrecheckHonorsRetentionWithoutRemovingExpiredMarkers() {
        var nowEpochMillis = 1_700_000_000_000L
        val replayProtection =
            InMemoryReplayProtection(
                nowEpochMillis = { nowEpochMillis }
            )
        val retentionMillis = 8L * 24L * 60L * 60L * 1000L

        assertEquals(true, replayProtection.markIfNew("sender-a", "message-a"))
        assertEquals(true, replayProtection.hasSeen("sender-a", "message-a"))

        nowEpochMillis += retentionMillis
        assertEquals(false, replayProtection.hasSeen("sender-a", "message-a"))

        nowEpochMillis -= 1L
        assertEquals(true, replayProtection.hasSeen("sender-a", "message-a"))
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
        nowEpochMillis += 8L * 24L * 60L * 60L * 1000L
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

    private fun authenticatedPayload(
        senderIdentity: KeyPair,
        recipientIdentity: KeyPair,
        kind: PacketKind,
        plaintext: String,
        messageId: String,
        sentAtEpochMillis: Long = System.currentTimeMillis()
    ): EncryptedChatMessage {
        val senderFingerprint = SpotChatCrypto.fingerprint(senderIdentity.public)
        val recipientFingerprint = SpotChatCrypto.fingerprint(recipientIdentity.public)
        val sessionKey = SpotChatCrypto.deriveSessionKey(senderIdentity, recipientIdentity.public)
        val associatedData =
            "SpotChat/v2/${kind.name}/$messageId/$senderFingerprint/$recipientFingerprint/$sentAtEpochMillis"
                .toByteArray(Charsets.UTF_8)
        val frame =
            SpotChatCrypto.encrypt(
                sessionKey = sessionKey,
                plaintext = plaintext.toByteArray(Charsets.UTF_8),
                associatedData = associatedData
            )
        return EncryptedChatMessage(
            messageId = messageId,
            senderFingerprint = senderFingerprint,
            sentAtEpochMillis = sentAtEpochMillis,
            nonce = Base64.getEncoder().encodeToString(frame.nonce),
            ciphertext = Base64.getEncoder().encodeToString(frame.ciphertext)
        )
    }

    private class RecordingReplayProtection : ReplayProtection {
        private val seenPackets = mutableSetOf<Pair<String, String>>()

        var markCalls: Int = 0
            private set

        override fun hasSeen(
            senderFingerprint: String,
            messageId: String
        ): Boolean = (senderFingerprint to messageId) in seenPackets

        override fun markIfNew(
            senderFingerprint: String,
            messageId: String
        ): Boolean {
            markCalls += 1
            return seenPackets.add(senderFingerprint to messageId)
        }
    }
}
