package com.weifurry.spotchat.protocol

import kotlinx.serialization.Serializable

@Serializable
enum class PacketKind {
    HELLO,
    ENCRYPTED_MESSAGE,
    ENCRYPTED_ACK,
    ACK
}

@Serializable
data class PeerHello(
    val deviceName: String,
    val publicKey: String,
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
data class DeliveryAck(
    val messageId: String,
    val receivedAtEpochMillis: Long
)

@Serializable
data class WirePacket(
    val version: Int = 1,
    val kind: PacketKind,
    val hello: PeerHello? = null,
    val encryptedMessage: EncryptedChatMessage? = null,
    val ack: DeliveryAck? = null
)
