package com.weifurry.spotchat.transport

import kotlinx.coroutines.flow.Flow

enum class TransportKind {
    LAN,
    BLUETOOTH
}

data class TransportPeer(
    val id: String,
    val name: String,
    val address: String,
    val port: Int? = null,
    val kind: TransportKind
)

sealed interface TransportEvent {
    data class StateChanged(val message: String) : TransportEvent

    data class PeerFound(val peer: TransportPeer) : TransportEvent

    data class FrameReceived(
        val peer: TransportPeer,
        val frame: ByteArray
    ) : TransportEvent

    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : TransportEvent
}

interface SpotChatTransport {
    val events: Flow<TransportEvent>

    suspend fun start()

    suspend fun stop()

    suspend fun send(
        peer: TransportPeer,
        frame: ByteArray
    )
}
