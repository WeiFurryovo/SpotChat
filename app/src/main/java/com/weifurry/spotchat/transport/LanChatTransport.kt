package com.weifurry.spotchat.transport

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LanChatTransport(
    private val deviceName: String,
    private val servicePort: Int = DEFAULT_SERVICE_PORT,
    private val discoveryPort: Int = DEFAULT_DISCOVERY_PORT
) : SpotChatTransport {
    private val mutableEvents = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 64)
    private val instanceId = UUID.randomUUID().toString()
    private var supervisor: Job? = null
    private var serverSocket: ServerSocket? = null
    private var discoverySocket: DatagramSocket? = null

    override val events: Flow<TransportEvent> = mutableEvents

    override suspend fun start() {
        if (supervisor?.isActive == true) {
            return
        }
        val job = SupervisorJob()
        supervisor = job
        val scope = CoroutineScope(job + Dispatchers.IO)
        startServer(scope)
        startDiscovery(scope)
        mutableEvents.emit(TransportEvent.StateChanged("局域网发现已启动"))
    }

    override suspend fun stop() {
        supervisor?.cancel()
        supervisor = null
        serverSocket.closeQuietly()
        discoverySocket.closeQuietly()
        serverSocket = null
        discoverySocket = null
        mutableEvents.emit(TransportEvent.StateChanged("局域网传输已停止"))
    }

    override suspend fun send(
        peer: TransportPeer,
        frame: ByteArray
    ) {
        val port = peer.port ?: servicePort
        withContext(Dispatchers.IO) {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(peer.address, port), CONNECT_TIMEOUT_MS)
                FrameIo.writeFrame(socket.getOutputStream(), frame)
            }
        }
    }

    private fun startServer(scope: CoroutineScope) {
        serverSocket = ServerSocket(servicePort)
        scope.launch {
            while (isActive) {
                try {
                    val socket = serverSocket?.accept() ?: break
                    launch { handleIncomingSocket(socket) }
                } catch (error: Throwable) {
                    if (isActive) {
                        mutableEvents.emit(
                            TransportEvent.Failure("局域网接收失败", error)
                        )
                    }
                    break
                }
            }
        }
    }

    private fun startDiscovery(scope: CoroutineScope) {
        discoverySocket =
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(discoveryPort))
            }
        scope.launch { receiveDiscoveryPackets() }
        scope.launch { broadcastPresence() }
    }

    private suspend fun handleIncomingSocket(socket: Socket) {
        socket.use {
            val peer =
                TransportPeer(
                    id = "lan:${socket.inetAddress.hostAddress}",
                    name = socket.inetAddress.hostAddress ?: "局域网设备",
                    address = socket.inetAddress.hostAddress.orEmpty(),
                    port = socket.port,
                    kind = TransportKind.LAN
                )
            while (supervisor?.isActive == true) {
                val frame = FrameIo.readFrame(socket.getInputStream()) ?: break
                mutableEvents.emit(TransportEvent.FrameReceived(peer, frame))
            }
        }
    }

    private suspend fun receiveDiscoveryPackets() {
        val buffer = ByteArray(1024)
        val socket = discoverySocket ?: return
        while (supervisor?.isActive == true) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                parseBeacon(packet)?.let { peer ->
                    mutableEvents.emit(TransportEvent.PeerFound(peer))
                }
            } catch (error: Throwable) {
                if (supervisor?.isActive == true) {
                    mutableEvents.emit(
                        TransportEvent.Failure("局域网发现失败", error)
                    )
                }
            }
        }
    }

    private suspend fun broadcastPresence() {
        val socket = discoverySocket ?: return
        val address = InetAddress.getByName("255.255.255.255")
        while (supervisor?.isActive == true) {
            val payload = beaconPayload().toByteArray(Charsets.UTF_8)
            val packet =
                DatagramPacket(payload, payload.size, address, discoveryPort)
            socket.send(packet)
            delay(DISCOVERY_INTERVAL_MS)
        }
    }

    private fun parseBeacon(packet: DatagramPacket): TransportPeer? {
        val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
        val parts = text.split("|")
        if (parts.size != BEACON_PARTS || parts[0] != BEACON_PREFIX || parts[1] == instanceId) {
            return null
        }
        val name =
            runCatching {
                String(Base64.getUrlDecoder().decode(parts[2]), Charsets.UTF_8)
            }.getOrDefault("SpotChat")
        val port = parts[3].toIntOrNull() ?: return null
        val address = packet.address.hostAddress ?: return null
        return TransportPeer(
            id = "lan:${parts[1]}",
            name = name,
            address = address,
            port = port,
            kind = TransportKind.LAN
        )
    }

    private fun beaconPayload(): String {
        val encodedName =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(deviceName.toByteArray(Charsets.UTF_8))
        return "$BEACON_PREFIX|$instanceId|$encodedName|$servicePort"
    }

    private fun ServerSocket?.closeQuietly() {
        runCatching { this?.close() }
    }

    private fun DatagramSocket?.closeQuietly() {
        runCatching { this?.close() }
    }

    companion object {
        const val DEFAULT_SERVICE_PORT = 38441
        const val DEFAULT_DISCOVERY_PORT = 38442
        private const val BEACON_PREFIX = "SPOTCHAT_V1"
        private const val BEACON_PARTS = 4
        private const val CONNECT_TIMEOUT_MS = 2_500
        private const val DISCOVERY_INTERVAL_MS = 3_000L
    }
}
