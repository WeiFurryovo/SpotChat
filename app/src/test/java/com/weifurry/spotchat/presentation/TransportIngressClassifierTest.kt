package com.weifurry.spotchat.presentation

import com.weifurry.spotchat.protocol.ChatCodec
import com.weifurry.spotchat.protocol.PacketKind
import com.weifurry.spotchat.protocol.PeerHello
import com.weifurry.spotchat.protocol.WirePacket
import com.weifurry.spotchat.transport.TransportEvent
import com.weifurry.spotchat.transport.TransportKind
import com.weifurry.spotchat.transport.TransportPeer
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportIngressClassifierTest {
    private val peer =
        TransportPeer(
            id = "peer-1",
            name = "Peer",
            address = "192.0.2.1",
            port = 37_777,
            kind = TransportKind.LAN
        )

    @Test
    fun malformedFrameBecomesGenericStatus() {
        val action =
            classifyTransportIngress(
                TransportEvent.FrameReceived(peer, "not-json".toByteArray())
            )

        assertEquals(
            TransportIngressAction.Status(MALFORMED_TRANSPORT_DATA_MESSAGE),
            action
        )
    }

    @Test
    fun failureDoesNotExposeCauseMessage() {
        val action =
            classifyTransportIngress(
                TransportEvent.Failure(
                    message = "局域网帧接收失败",
                    cause = IllegalStateException("attacker-controlled detail")
                )
            )

        assertEquals(
            TransportIngressAction.Status("局域网帧接收失败"),
            action
        )
        assertFalse(action.toString().contains("attacker-controlled"))
    }

    @Test
    fun blankFailureUsesGenericStatus() {
        assertEquals(
            TransportIngressAction.Status(TRANSPORT_FAILURE_MESSAGE),
            classifyTransportIngress(TransportEvent.Failure(""))
        )
    }

    @Test
    fun validFrameIsDecodedForProtocolHandling() {
        val packet =
            WirePacket(
                kind = PacketKind.HELLO,
                hello =
                    PeerHello(
                        deviceName = "Peer",
                        publicKey = "public-key"
                    )
            )

        val action =
            classifyTransportIngress(
                TransportEvent.FrameReceived(peer, ChatCodec.encode(packet))
            )

        assertEquals(
            TransportIngressAction.DecodedFrame(peer, packet),
            action
        )
    }

    @Test
    fun repeatedMalformedFramesNeverReachProtocolHandling() {
        repeat(1_000) { index ->
            val action =
                classifyTransportIngress(
                    TransportEvent.FrameReceived(
                        peer,
                        "malformed-$index".toByteArray()
                    )
                )

            assertTrue(action is TransportIngressAction.Status)
        }
    }

    @Test
    fun statusActionOnlyUpdatesTransientTrustState() {
        val source = productionSource("presentation/SpotChatApp.kt").readText()
        val statusBranch =
            source
                .substringAfter("is TransportIngressAction.Status -> {")
                .substringBefore("\n            }")

        assertTrue(statusBranch.contains("trustState = action.text"))
        assertFalse(statusBranch.contains("append", ignoreCase = true))
        assertFalse(statusBranch.contains("notify", ignoreCase = true))
        assertFalse(statusBranch.contains("persist", ignoreCase = true))
    }

    @Test
    fun preparedSendFailuresDoNotAppendIncomingMessages() {
        val source = productionSource("presentation/SpotChatApp.kt").readText()
        val textSendPath =
            source
                .substringAfter("fun sendPreparedMessage(")
                .substringBefore("fun trySendPendingOutboundMessage(")
        val voiceSendPath =
            source
                .substringAfter("fun sendPreparedVoiceMessage(")
                .substringBefore("fun trySendPendingOutboundVoiceMessage(")

        assertFalse(textSendPath.contains("appendMessage("))
        assertFalse(voiceSendPath.contains("appendMessage("))
    }

    private fun productionSource(relativePath: String): File {
        val userDirectory = checkNotNull(System.getProperty("user.dir"))
        var directory: File? = File(userDirectory).canonicalFile
        while (directory != null) {
            val candidates =
                listOf(
                    File(directory, "src/main/java/com/weifurry/spotchat/$relativePath"),
                    File(directory, "app/src/main/java/com/weifurry/spotchat/$relativePath")
                )
            candidates.firstOrNull(File::isFile)?.let { return it }
            directory = directory.parentFile
        }
        error("Unable to locate production source: $relativePath")
    }
}
