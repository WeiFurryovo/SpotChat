package com.weifurry.spotchat.presentation

import com.weifurry.spotchat.protocol.ChatCodec
import com.weifurry.spotchat.protocol.WirePacket
import com.weifurry.spotchat.transport.TransportEvent
import com.weifurry.spotchat.transport.TransportPeer

internal sealed interface TransportIngressAction {
    data class Status(val text: String) : TransportIngressAction

    data class PeerDiscovered(val peer: TransportPeer) : TransportIngressAction

    data class DecodedFrame(
        val peer: TransportPeer,
        val packet: WirePacket
    ) : TransportIngressAction
}

internal fun classifyTransportIngress(event: TransportEvent): TransportIngressAction =
    when (event) {
        is TransportEvent.StateChanged -> TransportIngressAction.Status(event.message)
        is TransportEvent.PeerFound -> TransportIngressAction.PeerDiscovered(event.peer)
        is TransportEvent.FrameReceived ->
            try {
                TransportIngressAction.DecodedFrame(
                    peer = event.peer,
                    packet = ChatCodec.decode(event.frame)
                )
            } catch (_: Exception) {
                TransportIngressAction.Status(MALFORMED_TRANSPORT_DATA_MESSAGE)
            }
        is TransportEvent.Failure ->
            TransportIngressAction.Status(
                event.message.ifBlank { TRANSPORT_FAILURE_MESSAGE }
            )
    }

internal const val MALFORMED_TRANSPORT_DATA_MESSAGE = "已忽略无法解析的传输数据"
internal const val TRANSPORT_FAILURE_MESSAGE = "传输异常"
