package com.weifurry.spotchat.protocol

import kotlinx.serialization.Serializable

@Serializable
enum class PacketKind {
    HELLO,
    SESSION_CHALLENGE,
    ENCRYPTED_SESSION_CONFIRM,
    ENCRYPTED_MESSAGE,
    ENCRYPTED_VOICE_MESSAGE,
    ENCRYPTED_REACTION,
    ENCRYPTED_ACK,
    ACK
}

@Serializable
data class PeerHello(
    val deviceName: String,
    val publicKey: String,
    val about: String = "",
    val transports: List<String> = emptyList()
)

@Serializable
data class SessionChallenge(
    val challengeId: String,
    val challengerHello: PeerHello,
    val responderHello: PeerHello,
    val responderFingerprint: String,
    val createdAtEpochMillis: Long
)

@Serializable
data class SessionConfirmationPayload(
    val challengeId: String,
    val challengeBinding: String,
    val challengerFingerprint: String,
    val responderFingerprint: String,
    val confirmedAtEpochMillis: Long
)

@Serializable
data class EncryptedChatMessage(
    val messageId: String,
    val senderFingerprint: String,
    val sentAtEpochMillis: Long,
    val nonce: String,
    val ciphertext: String
)

@Serializable
enum class DeliveryReceiptStatus {
    Delivered,
    Read
}

@Serializable
data class DeliveryAck(
    val messageId: String,
    val receivedAtEpochMillis: Long,
    val status: DeliveryReceiptStatus = DeliveryReceiptStatus.Delivered
)

@Serializable
data class TextMessagePayload(
    val logicalMessageId: String,
    val text: String
)

@Serializable
data class VoiceMessagePayload(
    val logicalMessageId: String,
    val codec: String,
    val durationMs: Long,
    val audioBase64: String,
    val groupId: String? = null,
    val groupName: String? = null
)

@Serializable
data class ReactionPayload(
    val targetMessageId: String,
    val emoji: String
)

@Serializable
data class WirePacket(
    val version: Int = CURRENT_VERSION,
    val kind: PacketKind,
    val hello: PeerHello? = null,
    val sessionChallenge: SessionChallenge? = null,
    val encryptedMessage: EncryptedChatMessage? = null,
    val ack: DeliveryAck? = null
) {
    companion object {
        const val CURRENT_VERSION = 2
    }
}

@Serializable
data class RelayEnvelope(
    val version: Int = 1,
    val envelopeId: String,
    val senderFingerprint: String,
    val recipientFingerprint: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val packet: WirePacket
) {
    init {
        val encryptedMessage = packet.encryptedMessage
        require(envelopeId.isNotBlank()) {
            "Relay envelope id cannot be blank"
        }
        require(recipientFingerprint.isNotBlank()) {
            "Relay recipient fingerprint cannot be blank"
        }
        require(packet.kind.isRelayableEncryptedKind()) {
            "Relay can only carry encrypted message packets"
        }
        require(encryptedMessage != null) {
            "Relay envelope requires an encrypted message"
        }
        require(senderFingerprint == encryptedMessage.senderFingerprint) {
            "Relay sender must match encrypted packet sender"
        }
        require(expiresAtEpochMillis > createdAtEpochMillis) {
            "Relay envelope must expire after it is created"
        }
    }

    companion object {
        const val DEFAULT_TTL_MILLIS = 7L * 24L * 60L * 60L * 1000L

        fun fromEncryptedPacket(
            packet: WirePacket,
            recipientFingerprint: String,
            createdAtEpochMillis: Long = packet.encryptedMessage?.sentAtEpochMillis
                ?: System.currentTimeMillis(),
            ttlMillis: Long = DEFAULT_TTL_MILLIS
        ): RelayEnvelope {
            val encryptedMessage = packet.encryptedMessage
                ?: error("Relay envelope requires an encrypted message")
            return RelayEnvelope(
                envelopeId = "relay:${encryptedMessage.messageId}:$recipientFingerprint",
                senderFingerprint = encryptedMessage.senderFingerprint,
                recipientFingerprint = recipientFingerprint,
                createdAtEpochMillis = createdAtEpochMillis,
                expiresAtEpochMillis = createdAtEpochMillis + ttlMillis,
                packet = packet
            )
        }
    }
}

fun PacketKind.isRelayableEncryptedKind(): Boolean =
    when (this) {
        PacketKind.ENCRYPTED_MESSAGE,
        PacketKind.ENCRYPTED_VOICE_MESSAGE,
        PacketKind.ENCRYPTED_REACTION,
        PacketKind.ENCRYPTED_ACK -> true
        PacketKind.HELLO,
        PacketKind.SESSION_CHALLENGE,
        PacketKind.ENCRYPTED_SESSION_CONFIRM,
        PacketKind.ACK -> false
    }
