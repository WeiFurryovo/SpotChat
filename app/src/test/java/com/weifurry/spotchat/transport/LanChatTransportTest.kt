package com.weifurry.spotchat.transport

import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class LanChatTransportTest {
    @Test
    fun rejectsInvalidServicePorts() {
        assertThrows(IllegalArgumentException::class.java) {
            LanChatTransport(servicePort = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LanChatTransport(servicePort = 65_536)
        }
    }

    @Test
    fun rejectsInvalidDiscoveryPorts() {
        assertThrows(IllegalArgumentException::class.java) {
            LanChatTransport(discoveryPort = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LanChatTransport(discoveryPort = 65_536)
        }
    }


    @Test
    fun discoveryBeaconUsesGenericDeviceName() {
        val payload =
            LanChatTransport.discoveryBeaconPayload(
                instanceId = "instance-id",
                servicePort = LanChatTransport.DEFAULT_SERVICE_PORT
            )
        val parts = payload.split('|')
        val decodedName =
            String(Base64.getUrlDecoder().decode(parts[2]), Charsets.UTF_8)

        assertEquals(4, parts.size)
        assertEquals("SPOTCHAT_V1", parts[0])
        assertEquals("instance-id", parts[1])
        assertEquals("SpotChat 设备", decodedName)
        assertEquals(LanChatTransport.DEFAULT_SERVICE_PORT.toString(), parts[3])
        assertFalse(payload.contains("Pixel"))
        assertFalse(payload.contains("Alice"))
    }

    @Test
    fun rejectsInvalidPeerPortBeforeConnecting() {
        val transport = LanChatTransport()
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
