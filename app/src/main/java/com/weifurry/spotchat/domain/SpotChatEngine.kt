package com.weifurry.spotchat.domain

import com.weifurry.spotchat.crypto.EncryptedFrame
import com.weifurry.spotchat.crypto.SpotChatCrypto
import com.weifurry.spotchat.protocol.DeliveryAck
import com.weifurry.spotchat.protocol.DeliveryReceiptStatus
import com.weifurry.spotchat.protocol.EncryptedChatMessage
import com.weifurry.spotchat.protocol.PacketKind
import com.weifurry.spotchat.protocol.PeerHello
import com.weifurry.spotchat.protocol.ReactionPayload
import com.weifurry.spotchat.protocol.SessionChallenge
import com.weifurry.spotchat.protocol.SessionConfirmationPayload
import com.weifurry.spotchat.protocol.TextMessagePayload
import com.weifurry.spotchat.protocol.VoiceMessagePayload
import com.weifurry.spotchat.protocol.WirePacket
import java.security.KeyPair
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey
import java.util.Base64
import java.util.LinkedHashMap
import java.util.UUID
import javax.crypto.SecretKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class TrustedPeer(
    val deviceName: String,
    val fingerprint: String,
    val publicKey: String,
    val pairingCode: String,
    val about: String = ""
)

data class PlainChatMessage(
    val messageId: String,
    val envelopeMessageId: String,
    val senderFingerprint: String,
    val sentAtEpochMillis: Long,
    val text: String
)

data class PlainVoiceMessage(
    val messageId: String,
    val envelopeMessageId: String,
    val senderFingerprint: String,
    val sentAtEpochMillis: Long,
    val codec: String,
    val durationMs: Long,
    val audioBytes: ByteArray,
    val groupId: String?,
    val groupName: String?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlainVoiceMessage) return false

        return messageId == other.messageId &&
            envelopeMessageId == other.envelopeMessageId &&
            senderFingerprint == other.senderFingerprint &&
            sentAtEpochMillis == other.sentAtEpochMillis &&
            codec == other.codec &&
            durationMs == other.durationMs &&
            groupId == other.groupId &&
            groupName == other.groupName &&
            audioBytes.contentEquals(other.audioBytes)
    }

    override fun hashCode(): Int {
        var result = messageId.hashCode()
        result = 31 * result + envelopeMessageId.hashCode()
        result = 31 * result + senderFingerprint.hashCode()
        result = 31 * result + sentAtEpochMillis.hashCode()
        result = 31 * result + codec.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + (groupId?.hashCode() ?: 0)
        result = 31 * result + (groupName?.hashCode() ?: 0)
        result = 31 * result + audioBytes.contentHashCode()
        return result
    }
}

data class PlainReaction(
    val messageId: String,
    val senderFingerprint: String,
    val sentAtEpochMillis: Long,
    val targetMessageId: String,
    val emoji: String
)

class DuplicateMessageException(
    val messageId: String,
    val senderFingerprint: String
) : IllegalStateException("Duplicate message $messageId from $senderFingerprint")

class SpotChatEngine(
    private val localDeviceName: String,
    private val localIdentity: KeyPair,
    private val replayProtection: ReplayProtection = InMemoryReplayProtection()
) {
    val localFingerprint: String = SpotChatCrypto.fingerprint(localIdentity.public)
    private val confirmedSessions = LinkedHashMap<String, SecretKey>(16, 0.75f, true)
    private val pendingSessions = LinkedHashMap<String, SecretKey>(16, 0.75f, true)
    private var awaitingTrustSession: Pair<String, SecretKey>? = null
    private val sessionsLock = Any()
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    fun helloPacket(
        transports: List<String> = listOf("lan", "bluetooth"),
        about: String = ""
    ): WirePacket =
        WirePacket(
            kind = PacketKind.HELLO,
            hello =
                PeerHello(
                    deviceName = localDeviceName,
                    publicKey = SpotChatCrypto.encodePublicKey(localIdentity.public),
                    about = about,
                    transports = transports
                )
        )

    fun openSession(hello: PeerHello): TrustedPeer {
        validateHello(hello)
        val remotePublicKey = SpotChatCrypto.decodePublicKey(hello.publicKey)
        requireMatchingCurve(remotePublicKey)
        val sessionKey = SpotChatCrypto.deriveSessionKey(localIdentity, remotePublicKey)
        val fingerprint = SpotChatCrypto.fingerprint(remotePublicKey)
        require(fingerprint != localFingerprint) {
            "Cannot open a session with the local identity"
        }
        synchronized(sessionsLock) {
            pendingSessions[fingerprint] = sessionKey
            while (pendingSessions.size > MAX_PENDING_SESSIONS) {
                val oldest = pendingSessions.keys.iterator()
                if (!oldest.hasNext()) break
                oldest.next()
                oldest.remove()
            }
        }
        return TrustedPeer(
            deviceName = hello.deviceName,
            fingerprint = fingerprint,
            publicKey = hello.publicKey,
            about = hello.about,
            pairingCode =
                SpotChatCrypto.pairingCode(
                    localPublicKey = localIdentity.public,
                    remotePublicKey = remotePublicKey,
                    sessionKey = sessionKey
                )
        )
    }

    fun sessionChallengePacket(
        responderHello: PeerHello,
        transports: List<String> = listOf("lan", "bluetooth"),
        about: String = "",
        challengeId: String = UUID.randomUUID().toString(),
        createdAtEpochMillis: Long = System.currentTimeMillis()
    ): WirePacket {
        validateHello(responderHello)
        val responderPublicKey = SpotChatCrypto.decodePublicKey(responderHello.publicKey)
        requireMatchingCurve(responderPublicKey)
        val responderFingerprint = SpotChatCrypto.fingerprint(responderPublicKey)
        require(challengeId.isNotBlank() && challengeId.length <= MAX_CHALLENGE_ID_CHARS) {
            "Invalid session challenge id"
        }
        return WirePacket(
            kind = PacketKind.SESSION_CHALLENGE,
            sessionChallenge =
                SessionChallenge(
                    challengeId = challengeId,
                    challengerHello = helloPacket(transports = transports, about = about).hello
                        ?: error("Missing local hello"),
                    responderHello = responderHello,
                    responderFingerprint = responderFingerprint,
                    createdAtEpochMillis = createdAtEpochMillis
                )
        )
    }

    fun encryptSessionConfirmationForPeer(
        peerFingerprint: String,
        challenge: SessionChallenge,
        confirmedAtEpochMillis: Long = System.currentTimeMillis()
    ): WirePacket {
        val challengeId = challenge.challengeId
        require(challengeId.isNotBlank() && challengeId.length <= MAX_CHALLENGE_ID_CHARS) {
            "Invalid session challenge id"
        }
        require(challenge.responderFingerprint == localFingerprint) {
            "Session challenge is intended for another responder"
        }
        val payload =
            SessionConfirmationPayload(
                challengeId = challengeId,
                challengeBinding = sessionChallengeBinding(challenge),
                challengerFingerprint = peerFingerprint,
                responderFingerprint = localFingerprint,
                confirmedAtEpochMillis = confirmedAtEpochMillis
            )
        val frame =
            encryptPayloadForPeer(
                peerFingerprint = peerFingerprint,
                kind = PacketKind.ENCRYPTED_SESSION_CONFIRM,
                messageId = challengeId,
                sentAtEpochMillis = confirmedAtEpochMillis,
                plaintext = json.encodeToString(payload).toByteArray(Charsets.UTF_8),
                allowPendingSession = true
            )
        return encryptedPayloadPacket(
            kind = PacketKind.ENCRYPTED_SESSION_CONFIRM,
            messageId = challengeId,
            sentAtEpochMillis = confirmedAtEpochMillis,
            frame = frame
        )
    }

    fun decryptSessionConfirmation(message: EncryptedChatMessage): SessionConfirmationPayload {
        val plaintext =
            decryptPayload(
                message = message,
                kind = PacketKind.ENCRYPTED_SESSION_CONFIRM,
                rememberReplay = false,
                allowPendingSession = true
            )
        return json.decodeFromString(plaintext.toString(Charsets.UTF_8))
    }

    fun sessionChallengeBinding(challenge: SessionChallenge): String =
        base64(
            MessageDigest.getInstance("SHA-256")
                .digest(json.encodeToString(challenge).toByteArray(Charsets.UTF_8))
        )

    fun confirmSession(peerFingerprint: String) {
        synchronized(sessionsLock) {
            val awaitingSession = awaitingTrustSession?.takeIf { (fingerprint, _) ->
                fingerprint == peerFingerprint
            }
            val sessionKey = pendingSessions.remove(peerFingerprint) ?: awaitingSession?.second
                ?: confirmedSessions[peerFingerprint]
                ?: error("No pending session for peer $peerFingerprint")
            if (awaitingSession != null) {
                awaitingTrustSession = null
            }
            confirmedSessions[peerFingerprint] = sessionKey
            while (confirmedSessions.size > MAX_CONFIRMED_SESSIONS) {
                val oldest = confirmedSessions.keys.iterator()
                if (!oldest.hasNext()) break
                oldest.next()
                oldest.remove()
            }
        }
    }

    fun protectPendingSessionForTrust(peerFingerprint: String) {
        synchronized(sessionsLock) {
            val sessionKey = pendingSessions.remove(peerFingerprint)
                ?: error("No pending session for peer $peerFingerprint")
            awaitingTrustSession = peerFingerprint to sessionKey
        }
    }

    fun rejectPendingSession(peerFingerprint: String) {
        synchronized(sessionsLock) {
            pendingSessions.remove(peerFingerprint)
            if (awaitingTrustSession?.first == peerFingerprint) {
                awaitingTrustSession = null
            }
        }
    }

    fun encryptTextForPeer(
        peerFingerprint: String,
        text: String,
        sentAtEpochMillis: Long = System.currentTimeMillis(),
        messageId: String = UUID.randomUUID().toString()
    ): WirePacket {
        require(messageId.isNotBlank() && messageId.length <= MAX_MESSAGE_ID_CHARS) {
            "Invalid logical message id"
        }
        val envelopeMessageId = UUID.randomUUID().toString()
        val payload = TextMessagePayload(logicalMessageId = messageId, text = text)
        val frame =
            encryptPayloadForPeer(
                peerFingerprint = peerFingerprint,
                kind = PacketKind.ENCRYPTED_MESSAGE,
                messageId = envelopeMessageId,
                sentAtEpochMillis = sentAtEpochMillis,
                plaintext = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
            )
        return encryptedPayloadPacket(
            kind = PacketKind.ENCRYPTED_MESSAGE,
            messageId = envelopeMessageId,
            sentAtEpochMillis = sentAtEpochMillis,
            frame = frame
        )
    }

    fun encryptVoiceForPeer(
        peerFingerprint: String,
        audioBytes: ByteArray,
        durationMs: Long,
        codec: String = VOICE_CODEC_AAC,
        groupId: String? = null,
        groupName: String? = null,
        sentAtEpochMillis: Long = System.currentTimeMillis(),
        messageId: String = UUID.randomUUID().toString()
    ): WirePacket {
        require(audioBytes.isNotEmpty()) {
            "Voice message audio cannot be empty"
        }
        require(durationMs > 0L) {
            "Voice message duration must be positive"
        }
        require(messageId.isNotBlank() && messageId.length <= MAX_MESSAGE_ID_CHARS) {
            "Invalid logical message id"
        }
        require(groupId == null || groupId.isNotBlank() && groupId.length <= MAX_GROUP_ID_CHARS) {
            "Invalid voice group id"
        }
        require(groupName == null || groupName.length <= MAX_GROUP_NAME_CHARS) {
            "Invalid voice group name"
        }
        require(groupId != null || groupName == null) {
            "Voice group name requires a group id"
        }
        val envelopeMessageId = UUID.randomUUID().toString()
        val payload =
            VoiceMessagePayload(
                logicalMessageId = messageId,
                codec = codec,
                durationMs = durationMs,
                audioBase64 = base64(audioBytes),
                groupId = groupId,
                groupName = groupName
            )
        val frame =
            encryptPayloadForPeer(
                peerFingerprint = peerFingerprint,
                kind = PacketKind.ENCRYPTED_VOICE_MESSAGE,
                messageId = envelopeMessageId,
                sentAtEpochMillis = sentAtEpochMillis,
                plaintext = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
            )
        return encryptedPayloadPacket(
            kind = PacketKind.ENCRYPTED_VOICE_MESSAGE,
            messageId = envelopeMessageId,
            sentAtEpochMillis = sentAtEpochMillis,
            frame = frame
        )
    }

    fun encryptReactionForPeer(
        peerFingerprint: String,
        targetMessageId: String,
        emoji: String,
        sentAtEpochMillis: Long = System.currentTimeMillis()
    ): WirePacket {
        require(targetMessageId.isNotBlank() && targetMessageId.length <= MAX_MESSAGE_ID_CHARS) {
            "Invalid reaction target message id"
        }
        require(emoji.isNotBlank()) {
            "Reaction emoji cannot be blank"
        }
        val messageId = UUID.randomUUID().toString()
        val payload =
            ReactionPayload(
                targetMessageId = targetMessageId,
                emoji = emoji
            )
        val frame =
            encryptPayloadForPeer(
                peerFingerprint = peerFingerprint,
                kind = PacketKind.ENCRYPTED_REACTION,
                messageId = messageId,
                sentAtEpochMillis = sentAtEpochMillis,
                plaintext = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
            )
        return encryptedPayloadPacket(
            kind = PacketKind.ENCRYPTED_REACTION,
            messageId = messageId,
            sentAtEpochMillis = sentAtEpochMillis,
            frame = frame
        )
    }

    private fun encryptPayloadForPeer(
        peerFingerprint: String,
        kind: PacketKind,
        messageId: String,
        sentAtEpochMillis: Long,
        plaintext: ByteArray,
        allowPendingSession: Boolean = false
    ): EncryptedFrame {
        val sessionKey = sessionKeyFor(peerFingerprint, allowPendingSession)
        val associatedData =
            payloadAssociatedData(
                kind = kind,
                messageId = messageId,
                senderFingerprint = localFingerprint,
                recipientFingerprint = peerFingerprint,
                sentAtEpochMillis = sentAtEpochMillis
            )
        return SpotChatCrypto.encrypt(
            sessionKey = sessionKey,
            plaintext = plaintext,
            associatedData = associatedData
        )
    }

    private fun encryptedPayloadPacket(
        kind: PacketKind,
        messageId: String,
        sentAtEpochMillis: Long,
        frame: EncryptedFrame
    ): WirePacket =
        WirePacket(
            kind = kind,
            encryptedMessage =
                EncryptedChatMessage(
                    messageId = messageId,
                    senderFingerprint = localFingerprint,
                    sentAtEpochMillis = sentAtEpochMillis,
                    nonce = base64(frame.nonce),
                    ciphertext = base64(frame.ciphertext)
                )
        )

    fun encryptAckForPeer(
        peerFingerprint: String,
        deliveredMessageId: String,
        status: DeliveryReceiptStatus = DeliveryReceiptStatus.Delivered,
        receivedAtEpochMillis: Long = System.currentTimeMillis()
    ): WirePacket {
        val sessionKey = sessionKeyFor(peerFingerprint)
        val ackEnvelopeId = UUID.randomUUID().toString()
        val ack =
            DeliveryAck(
                messageId = deliveredMessageId,
                receivedAtEpochMillis = receivedAtEpochMillis,
                status = status
            )
        val frame =
            SpotChatCrypto.encrypt(
                sessionKey = sessionKey,
                plaintext = json.encodeToString(ack).toByteArray(Charsets.UTF_8),
                associatedData =
                    payloadAssociatedData(
                        kind = PacketKind.ENCRYPTED_ACK,
                        messageId = ackEnvelopeId,
                        senderFingerprint = localFingerprint,
                        recipientFingerprint = peerFingerprint,
                        sentAtEpochMillis = receivedAtEpochMillis
                    )
            )
        return WirePacket(
            kind = PacketKind.ENCRYPTED_ACK,
            encryptedMessage =
                EncryptedChatMessage(
                    messageId = ackEnvelopeId,
                    senderFingerprint = localFingerprint,
                    sentAtEpochMillis = receivedAtEpochMillis,
                    nonce = base64(frame.nonce),
                    ciphertext = base64(frame.ciphertext)
                )
        )
    }

    fun decryptText(message: EncryptedChatMessage): PlainChatMessage {
        val plaintext = decryptPayload(message, PacketKind.ENCRYPTED_MESSAGE, rememberReplay = true)
        val payload = json.decodeFromString<TextMessagePayload>(plaintext.toString(Charsets.UTF_8))
        require(
            payload.logicalMessageId.isNotBlank() &&
                payload.logicalMessageId.length <= MAX_MESSAGE_ID_CHARS
        ) {
            "Invalid logical message id"
        }
        return PlainChatMessage(
            messageId = payload.logicalMessageId,
            envelopeMessageId = message.messageId,
            senderFingerprint = message.senderFingerprint,
            sentAtEpochMillis = message.sentAtEpochMillis,
            text = payload.text
        )
    }

    fun decryptVoice(message: EncryptedChatMessage): PlainVoiceMessage {
        val plaintext = decryptPayload(message, PacketKind.ENCRYPTED_VOICE_MESSAGE, rememberReplay = true)
        val payload = json.decodeFromString<VoiceMessagePayload>(plaintext.toString(Charsets.UTF_8))
        require(
            payload.logicalMessageId.isNotBlank() &&
                payload.logicalMessageId.length <= MAX_MESSAGE_ID_CHARS
        ) {
            "Invalid logical voice message id"
        }
        require(
            (payload.groupId == null ||
                payload.groupId.isNotBlank() && payload.groupId.length <= MAX_GROUP_ID_CHARS) &&
                (payload.groupName == null || payload.groupName.length <= MAX_GROUP_NAME_CHARS) &&
                (payload.groupId != null || payload.groupName == null)
        ) {
            "Invalid voice group metadata"
        }
        return PlainVoiceMessage(
            messageId = payload.logicalMessageId,
            envelopeMessageId = message.messageId,
            senderFingerprint = message.senderFingerprint,
            sentAtEpochMillis = message.sentAtEpochMillis,
            codec = payload.codec,
            durationMs = payload.durationMs,
            audioBytes = Base64.getDecoder().decode(payload.audioBase64),
            groupId = payload.groupId,
            groupName = payload.groupName
        )
    }

    fun decryptReaction(message: EncryptedChatMessage): PlainReaction {
        val plaintext = decryptPayload(message, PacketKind.ENCRYPTED_REACTION, rememberReplay = true)
        val payload = json.decodeFromString<ReactionPayload>(plaintext.toString(Charsets.UTF_8))
        return PlainReaction(
            messageId = message.messageId,
            senderFingerprint = message.senderFingerprint,
            sentAtEpochMillis = message.sentAtEpochMillis,
            targetMessageId = payload.targetMessageId,
            emoji = payload.emoji
        )
    }

    fun decryptAck(message: EncryptedChatMessage): DeliveryAck {
        val plaintext = decryptPayload(message, PacketKind.ENCRYPTED_ACK, rememberReplay = true)
        return json.decodeFromString(plaintext.toString(Charsets.UTF_8))
    }

    private fun decryptPayload(
        message: EncryptedChatMessage,
        kind: PacketKind,
        rememberReplay: Boolean,
        allowPendingSession: Boolean = false
    ): ByteArray {
        require(message.messageId.isNotBlank() && message.messageId.length <= MAX_MESSAGE_ID_CHARS) {
            "Invalid encrypted envelope id"
        }
        val sessionKey = sessionKeyFor(message.senderFingerprint, allowPendingSession)
        val frame =
            EncryptedFrame(
                nonce = Base64.getDecoder().decode(message.nonce),
                ciphertext = Base64.getDecoder().decode(message.ciphertext)
            )
        val plaintext =
            SpotChatCrypto.decrypt(
                sessionKey = sessionKey,
                frame = frame,
                associatedData =
                    payloadAssociatedData(
                        kind = kind,
                        messageId = message.messageId,
                        senderFingerprint = message.senderFingerprint,
                        recipientFingerprint = localFingerprint,
                        sentAtEpochMillis = message.sentAtEpochMillis
                    )
            )
        if (rememberReplay) {
            val nowEpochMillis = System.currentTimeMillis()
            require(
                message.sentAtEpochMillis > nowEpochMillis - ReplayPolicy.MAX_PACKET_AGE_MS &&
                    message.sentAtEpochMillis <= nowEpochMillis + ReplayPolicy.MAX_FUTURE_SKEW_MS
            ) {
                "Encrypted packet timestamp is outside the accepted replay window"
            }
            if (!replayProtection.markIfNew(message.senderFingerprint, message.messageId)) {
                throw DuplicateMessageException(message.messageId, message.senderFingerprint)
            }
        }
        return plaintext
    }

    private fun payloadAssociatedData(
        kind: PacketKind,
        messageId: String,
        senderFingerprint: String,
        recipientFingerprint: String,
        sentAtEpochMillis: Long
    ): ByteArray =
        "SpotChat/v2/${kind.name}/$messageId/$senderFingerprint/$recipientFingerprint/$sentAtEpochMillis"
            .toByteArray(Charsets.UTF_8)

    private fun base64(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    private fun sessionKeyFor(
        peerFingerprint: String,
        allowPendingSession: Boolean = false
    ): SecretKey =
        synchronized(sessionsLock) {
            confirmedSessions[peerFingerprint]
                ?: pendingSessions[peerFingerprint].takeIf { allowPendingSession }
        } ?: error("No session for peer $peerFingerprint")

    private fun validateHello(hello: PeerHello) {
        require(hello.deviceName.isNotBlank() && hello.deviceName.length <= MAX_DEVICE_NAME_CHARS) {
            "Invalid peer device name"
        }
        require(hello.publicKey.isNotBlank() && hello.publicKey.length <= MAX_PUBLIC_KEY_CHARS) {
            "Invalid peer public key"
        }
        require(hello.about.length <= MAX_ABOUT_CHARS) {
            "Peer about text is too long"
        }
        require(hello.transports.size <= MAX_TRANSPORT_HINTS) {
            "Too many peer transport hints"
        }
        require(hello.transports.all { hint -> hint.isNotBlank() && hint.length <= MAX_TRANSPORT_HINT_CHARS }) {
            "Invalid peer transport hint"
        }
    }

    private fun requireMatchingCurve(remotePublicKey: java.security.PublicKey) {
        val localEcKey = localIdentity.public as? ECPublicKey
            ?: error("Local identity is not an EC public key")
        val remoteEcKey = remotePublicKey as? ECPublicKey
            ?: throw IllegalArgumentException("Peer identity is not an EC public key")
        val local = localEcKey.params
        val remote = remoteEcKey.params
        require(
            local.curve == remote.curve &&
                local.generator == remote.generator &&
                local.order == remote.order &&
                local.cofactor == remote.cofactor
        ) {
            "Peer identity uses an unsupported EC curve"
        }
    }

    companion object {
        const val VOICE_CODEC_AAC = "aac-m4a"
        private const val MAX_CONFIRMED_SESSIONS = 64
        private const val MAX_PENDING_SESSIONS = 32
        private const val MAX_DEVICE_NAME_CHARS = 64
        private const val MAX_PUBLIC_KEY_CHARS = 1_024
        private const val MAX_ABOUT_CHARS = 128
        private const val MAX_TRANSPORT_HINTS = 8
        private const val MAX_TRANSPORT_HINT_CHARS = 128
        private const val MAX_CHALLENGE_ID_CHARS = 128
        private const val MAX_MESSAGE_ID_CHARS = 128
        private const val MAX_GROUP_ID_CHARS = 128
        private const val MAX_GROUP_NAME_CHARS = 64
    }
}
