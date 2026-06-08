package com.weifurry.spotchat.domain

import com.weifurry.spotchat.crypto.EncryptedFrame
import com.weifurry.spotchat.crypto.SpotChatCrypto
import com.weifurry.spotchat.protocol.DeliveryAck
import com.weifurry.spotchat.protocol.DeliveryReceiptStatus
import com.weifurry.spotchat.protocol.EncryptedChatMessage
import com.weifurry.spotchat.protocol.PacketKind
import com.weifurry.spotchat.protocol.PeerHello
import com.weifurry.spotchat.protocol.ReactionPayload
import com.weifurry.spotchat.protocol.VoiceMessagePayload
import com.weifurry.spotchat.protocol.WirePacket
import java.security.KeyPair
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
    val senderFingerprint: String,
    val sentAtEpochMillis: Long,
    val text: String
)

data class PlainVoiceMessage(
    val messageId: String,
    val senderFingerprint: String,
    val sentAtEpochMillis: Long,
    val codec: String,
    val durationMs: Long,
    val audioBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlainVoiceMessage) return false

        return messageId == other.messageId &&
            senderFingerprint == other.senderFingerprint &&
            sentAtEpochMillis == other.sentAtEpochMillis &&
            codec == other.codec &&
            durationMs == other.durationMs &&
            audioBytes.contentEquals(other.audioBytes)
    }

    override fun hashCode(): Int {
        var result = messageId.hashCode()
        result = 31 * result + senderFingerprint.hashCode()
        result = 31 * result + sentAtEpochMillis.hashCode()
        result = 31 * result + codec.hashCode()
        result = 31 * result + durationMs.hashCode()
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
    private val localIdentity: KeyPair
) {
    val localFingerprint: String = SpotChatCrypto.fingerprint(localIdentity.public)
    private val sessions = mutableMapOf<String, SecretKey>()
    private val seenMessages = LinkedHashMap<String, Unit>()
    private val seenMessagesLock = Any()
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
        val remotePublicKey = SpotChatCrypto.decodePublicKey(hello.publicKey)
        val sessionKey = SpotChatCrypto.deriveSessionKey(localIdentity, remotePublicKey)
        val fingerprint = SpotChatCrypto.fingerprint(remotePublicKey)
        sessions[fingerprint] = sessionKey
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

    fun encryptTextForPeer(
        peerFingerprint: String,
        text: String,
        sentAtEpochMillis: Long = System.currentTimeMillis()
    ): WirePacket {
        val messageId = UUID.randomUUID().toString()
        val frame =
            encryptPayloadForPeer(
                peerFingerprint = peerFingerprint,
                kind = PacketKind.ENCRYPTED_MESSAGE,
                messageId = messageId,
                plaintext = text.toByteArray(Charsets.UTF_8)
            )
        return encryptedPayloadPacket(
            kind = PacketKind.ENCRYPTED_MESSAGE,
            messageId = messageId,
            sentAtEpochMillis = sentAtEpochMillis,
            frame = frame
        )
    }

    fun encryptVoiceForPeer(
        peerFingerprint: String,
        audioBytes: ByteArray,
        durationMs: Long,
        codec: String = VOICE_CODEC_AAC,
        sentAtEpochMillis: Long = System.currentTimeMillis()
    ): WirePacket {
        require(audioBytes.isNotEmpty()) {
            "Voice message audio cannot be empty"
        }
        require(durationMs > 0L) {
            "Voice message duration must be positive"
        }
        val messageId = UUID.randomUUID().toString()
        val payload =
            VoiceMessagePayload(
                codec = codec,
                durationMs = durationMs,
                audioBase64 = base64(audioBytes)
            )
        val frame =
            encryptPayloadForPeer(
                peerFingerprint = peerFingerprint,
                kind = PacketKind.ENCRYPTED_VOICE_MESSAGE,
                messageId = messageId,
                plaintext = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
            )
        return encryptedPayloadPacket(
            kind = PacketKind.ENCRYPTED_VOICE_MESSAGE,
            messageId = messageId,
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
        require(targetMessageId.isNotBlank()) {
            "Reaction target message id cannot be blank"
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
        plaintext: ByteArray
    ): EncryptedFrame {
        val sessionKey =
            sessions[peerFingerprint]
                ?: error("No trusted session for peer $peerFingerprint")
        val associatedData =
            payloadAssociatedData(
                kind = kind,
                messageId = messageId,
                senderFingerprint = localFingerprint
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
        val sessionKey =
            sessions[peerFingerprint]
                ?: error("No trusted session for peer $peerFingerprint")
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
                        senderFingerprint = localFingerprint
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
        return PlainChatMessage(
            messageId = message.messageId,
            senderFingerprint = message.senderFingerprint,
            sentAtEpochMillis = message.sentAtEpochMillis,
            text = plaintext.toString(Charsets.UTF_8)
        )
    }

    fun decryptVoice(message: EncryptedChatMessage): PlainVoiceMessage {
        val plaintext = decryptPayload(message, PacketKind.ENCRYPTED_VOICE_MESSAGE, rememberReplay = true)
        val payload = json.decodeFromString<VoiceMessagePayload>(plaintext.toString(Charsets.UTF_8))
        return PlainVoiceMessage(
            messageId = message.messageId,
            senderFingerprint = message.senderFingerprint,
            sentAtEpochMillis = message.sentAtEpochMillis,
            codec = payload.codec,
            durationMs = payload.durationMs,
            audioBytes = Base64.getDecoder().decode(payload.audioBase64)
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
        val plaintext = decryptPayload(message, PacketKind.ENCRYPTED_ACK, rememberReplay = false)
        return json.decodeFromString(plaintext.toString(Charsets.UTF_8))
    }

    private fun decryptPayload(
        message: EncryptedChatMessage,
        kind: PacketKind,
        rememberReplay: Boolean
    ): ByteArray {
        val replayKey = replayKey(message)
        val sessionKey =
            sessions[message.senderFingerprint]
                ?: error("No trusted session for sender ${message.senderFingerprint}")
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
                        senderFingerprint = message.senderFingerprint
                    )
            )
        if (rememberReplay) {
            rememberMessage(replayKey, message)
        }
        return plaintext
    }

    private fun payloadAssociatedData(
        kind: PacketKind,
        messageId: String,
        senderFingerprint: String
    ): ByteArray =
        "SpotChat/v1/${kind.name}/$messageId/$senderFingerprint".toByteArray(Charsets.UTF_8)

    private fun base64(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    private fun replayKey(message: EncryptedChatMessage): String =
        "${message.senderFingerprint}:${message.messageId}"

    private fun rememberMessage(
        replayKey: String,
        message: EncryptedChatMessage
    ) {
        synchronized(seenMessagesLock) {
            if (seenMessages.containsKey(replayKey)) {
                throw DuplicateMessageException(message.messageId, message.senderFingerprint)
            }
            seenMessages[replayKey] = Unit
            while (seenMessages.size > MAX_SEEN_MESSAGES) {
                val oldest = seenMessages.keys.iterator()
                if (!oldest.hasNext()) {
                    break
                }
                oldest.next()
                oldest.remove()
            }
        }
    }

    companion object {
        const val VOICE_CODEC_AAC = "aac-m4a"
        private const val MAX_SEEN_MESSAGES = 512
    }
}
