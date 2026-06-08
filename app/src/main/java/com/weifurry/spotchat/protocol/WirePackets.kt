package com.weifurry.spotchat.protocol

import kotlinx.serialization.Serializable

@Serializable
enum class PacketKind {
    HELLO,
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
data class VoiceMessagePayload(
    val codec: String,
    val durationMs: Long,
    val audioBase64: String
)

@Serializable
data class ReactionPayload(
    val targetMessageId: String,
    val emoji: String
)

@Serializable
data class WirePacket(
    val version: Int = 1,
    val kind: PacketKind,
    val hello: PeerHello? = null,
    val encryptedMessage: EncryptedChatMessage? = null,
    val ack: DeliveryAck? = null
)
