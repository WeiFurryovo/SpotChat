package com.weifurry.spotchat.domain

import com.weifurry.spotchat.crypto.EncryptedFrame
import com.weifurry.spotchat.crypto.SpotChatCrypto
import com.weifurry.spotchat.protocol.DeliveryAck
import com.weifurry.spotchat.protocol.EncryptedChatMessage
import com.weifurry.spotchat.protocol.PacketKind
import com.weifurry.spotchat.protocol.PeerHello
import com.weifurry.spotchat.protocol.WirePacket
import java.security.KeyPair
import java.util.Base64
import java.util.UUID
import javax.crypto.SecretKey

data class TrustedPeer(
    val deviceName: String,
    val fingerprint: String,
    val publicKey: String,
    val pairingCode: String
)

data class PlainChatMessage(
    val messageId: String,
    val senderFingerprint: String,
    val sentAtEpochMillis: Long,
    val text: String
)

class SpotChatEngine(
    private val localDeviceName: String,
    private val localIdentity: KeyPair
) {
    val localFingerprint: String = SpotChatCrypto.fingerprint(localIdentity.public)
    private val sessions = mutableMapOf<String, SecretKey>()

    fun helloPacket(transports: List<String> = listOf("lan", "bluetooth")): WirePacket =
        WirePacket(
            kind = PacketKind.HELLO,
            hello =
                PeerHello(
                    deviceName = localDeviceName,
                    publicKey = SpotChatCrypto.encodePublicKey(localIdentity.public),
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
        val sessionKey =
            sessions[peerFingerprint]
                ?: error("No trusted session for peer $peerFingerprint")
        val messageId = UUID.randomUUID().toString()
        val associatedData = messageAssociatedData(messageId, localFingerprint)
        val frame =
            SpotChatCrypto.encrypt(
                sessionKey = sessionKey,
                plaintext = text.toByteArray(Charsets.UTF_8),
                associatedData = associatedData
            )
        return WirePacket(
            kind = PacketKind.ENCRYPTED_MESSAGE,
            encryptedMessage =
                EncryptedChatMessage(
                    messageId = messageId,
                    senderFingerprint = localFingerprint,
                    sentAtEpochMillis = sentAtEpochMillis,
                    nonce = base64(frame.nonce),
                    ciphertext = base64(frame.ciphertext)
                )
        )
    }

    fun ackPacket(
        messageId: String,
        receivedAtEpochMillis: Long = System.currentTimeMillis()
    ): WirePacket =
        WirePacket(
            kind = PacketKind.ACK,
            ack =
                DeliveryAck(
                    messageId = messageId,
                    receivedAtEpochMillis = receivedAtEpochMillis
                )
        )

    fun decryptText(message: EncryptedChatMessage): PlainChatMessage {
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
                associatedData = messageAssociatedData(message.messageId, message.senderFingerprint)
            )
        return PlainChatMessage(
            messageId = message.messageId,
            senderFingerprint = message.senderFingerprint,
            sentAtEpochMillis = message.sentAtEpochMillis,
            text = plaintext.toString(Charsets.UTF_8)
        )
    }

    private fun messageAssociatedData(
        messageId: String,
        senderFingerprint: String
    ): ByteArray =
        "SpotChat/v1/message/$messageId/$senderFingerprint".toByteArray(Charsets.UTF_8)

    private fun base64(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)
}
