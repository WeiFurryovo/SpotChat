package com.weifurry.spotchat.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportManagerTest {
    @Test
    fun lifecycleAndSendDelegateStrictlyByRequestedTransportKind() = runBlocking {
        val lan = FakeTransport()
        val bluetooth = FakeTransport()
        val relay = FakeTransport()
        val manager = manager(lan, bluetooth, relay)
        val transports =
            listOf(
                TransportKind.LAN to lan,
                TransportKind.BLUETOOTH to bluetooth,
                TransportKind.RELAY to relay
            )

        transports.forEachIndexed { index, (kind, expectedTransport) ->
            val peer =
                TransportPeer(
                    id = "peer-$index",
                    name = "Peer $index",
                    address = "route-$index",
                    port = 38_441 + index,
                    kind = TransportKind.entries[(index + 1) % TransportKind.entries.size]
                )
            val frame = byteArrayOf(index.toByte(), (index + 1).toByte())

            manager.start(kind)
            manager.stop(kind)
            manager.send(kind, peer, frame)

            assertEquals(1, expectedTransport.startCalls)
            assertEquals(1, expectedTransport.stopCalls)
            val send = expectedTransport.sends.single()
            assertSame(peer, send.peer)
            assertSame(frame, send.frame)
        }

        transports.forEach { (_, transport) ->
            assertEquals(1, transport.startCalls)
            assertEquals(1, transport.stopCalls)
            assertEquals(1, transport.sends.size)
        }
    }

    @Test
    fun eventsReturnsFlowForRequestedTransportKind() {
        val lan = FakeTransport()
        val bluetooth = FakeTransport()
        val relay = FakeTransport()
        val manager = manager(lan, bluetooth, relay)

        assertSame(lan.events, manager.events(TransportKind.LAN))
        assertSame(bluetooth.events, manager.events(TransportKind.BLUETOOTH))
        assertSame(relay.events, manager.events(TransportKind.RELAY))
    }

    @Test
    fun transportHintsRemainProtocolCompatible() {
        val manager = manager(FakeTransport(), FakeTransport(), FakeTransport())

        assertEquals(listOf("lan:38441"), manager.transportHints(TransportKind.LAN))
        assertEquals(
            listOf("bluetooth:6d5d4dc0-8b6b-4bb1-9124-f9a9d89941d1"),
            manager.transportHints(TransportKind.BLUETOOTH)
        )
        assertEquals(
            listOf("relay:encrypted-mailbox:v1"),
            manager.transportHints(TransportKind.RELAY)
        )
    }

    @Test
    fun providersAreInvokedForEveryReadAndReturnTheirValuesUnchanged() {
        val bondedPeers =
            listOf(
                TransportPeer(
                    id = "bonded-peer",
                    name = "Bonded peer",
                    address = "00:11:22:33:44:55",
                    kind = TransportKind.BLUETOOTH
                )
            )
        var bondedCalls = 0
        var lanConnectionCalls = 0
        val manager =
            TransportManager(
                lanTransport = FakeTransport(),
                bluetoothTransport = FakeTransport(),
                relayTransport = FakeTransport(),
                bondedBluetoothPeersProvider = {
                    bondedCalls += 1
                    bondedPeers
                },
                lanConnectionProvider = {
                    lanConnectionCalls += 1
                    lanConnectionCalls == 1
                }
            )

        assertSame(bondedPeers, manager.bondedBluetoothPeers())
        assertSame(bondedPeers, manager.bondedBluetoothPeers())
        assertEquals(2, bondedCalls)
        assertTrue(manager.hasLanConnection())
        assertFalse(manager.hasLanConnection())
        assertEquals(2, lanConnectionCalls)
    }

    @Test
    fun providerFailuresPropagateWithoutWrapping() {
        val bondedFailure = IllegalStateException("bonded provider failed")
        val lanFailure = IllegalArgumentException("LAN provider failed")
        var bondedCalls = 0
        var lanConnectionCalls = 0
        val manager =
            TransportManager(
                lanTransport = FakeTransport(),
                bluetoothTransport = FakeTransport(),
                relayTransport = FakeTransport(),
                bondedBluetoothPeersProvider = {
                    bondedCalls += 1
                    throw bondedFailure
                },
                lanConnectionProvider = {
                    lanConnectionCalls += 1
                    throw lanFailure
                }
            )

        assertSame(
            bondedFailure,
            assertThrows(IllegalStateException::class.java) {
                manager.bondedBluetoothPeers()
            }
        )
        assertSame(
            lanFailure,
            assertThrows(IllegalArgumentException::class.java) {
                manager.hasLanConnection()
            }
        )
        assertEquals(1, bondedCalls)
        assertEquals(1, lanConnectionCalls)
    }

    private fun manager(
        lan: SpotChatTransport,
        bluetooth: SpotChatTransport,
        relay: SpotChatTransport
    ): TransportManager =
        TransportManager(
            lanTransport = lan,
            bluetoothTransport = bluetooth,
            relayTransport = relay
        )

    private class FakeTransport : SpotChatTransport {
        override val events: Flow<TransportEvent> = MutableSharedFlow()

        var startCalls: Int = 0
            private set
        var stopCalls: Int = 0
            private set
        val sends = mutableListOf<SendCall>()

        override suspend fun start() {
            startCalls += 1
        }

        override suspend fun stop() {
            stopCalls += 1
        }

        override suspend fun send(
            peer: TransportPeer,
            frame: ByteArray
        ) {
            sends += SendCall(peer, frame)
        }
    }

    private data class SendCall(
        val peer: TransportPeer,
        val frame: ByteArray
    )
}
