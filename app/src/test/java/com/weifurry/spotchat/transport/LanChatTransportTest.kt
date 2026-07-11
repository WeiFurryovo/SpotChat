package com.weifurry.spotchat.transport

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

class LanChatTransportTest {
    @Test
    fun rejectsInvalidServicePorts() {
        assertThrows(IllegalArgumentException::class.java) {
            LanChatTransport(deviceName = "test", servicePort = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LanChatTransport(deviceName = "test", servicePort = 65_536)
        }
    }

    @Test
    fun rejectsInvalidDiscoveryPorts() {
        assertThrows(IllegalArgumentException::class.java) {
            LanChatTransport(deviceName = "test", discoveryPort = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LanChatTransport(deviceName = "test", discoveryPort = 65_536)
        }
    }

    @Test
    fun rejectsInvalidPeerPortBeforeConnecting() {
        val transport = LanChatTransport(deviceName = "test")
        val peer =
            TransportPeer(
                id = "invalid",
                name = "invalid",
                address = "127.0.0.1",
                port = 65_536,
                kind = TransportKind.LAN
            )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                transport.send(peer, byteArrayOf())
            }
        }
    }
}
