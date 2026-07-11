package com.weifurry.spotchat.transport

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class LanChatTransport(
    private val deviceName: String,
    context: Context? = null,
    private val servicePort: Int = DEFAULT_SERVICE_PORT,
    private val discoveryPort: Int = DEFAULT_DISCOVERY_PORT
) : SpotChatTransport {
    private val wifiManager =
        context
            ?.applicationContext
            ?.getSystemService(WifiManager::class.java)
    private val mutableEvents = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 64)
    private val instanceId = UUID.randomUUID().toString()
    private val lifecycleMutex = Mutex()
    private val incomingConnectionPermits = Semaphore(MAX_INCOMING_CONNECTIONS)
    private val acceptedSockets = ConcurrentHashMap.newKeySet<Socket>()
    private var supervisor: Job? = null
    private var serverSocket: ServerSocket? = null
    private var discoverySocket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    init {
        require(servicePort in VALID_PORT_RANGE) {
            "Service port must be in $VALID_PORT_RANGE"
        }
        require(discoveryPort in VALID_PORT_RANGE) {
            "Discovery port must be in $VALID_PORT_RANGE"
        }
    }

    override val events: Flow<TransportEvent> = mutableEvents

    override suspend fun start() {
        lifecycleMutex.withLock {
            if (supervisor?.isActive == true) {
                return@withLock
            }
            val job = SupervisorJob()
            supervisor = job
            val scope = CoroutineScope(job + Dispatchers.IO)
            try {
                startServer(scope)
                startDiscovery(scope)
                mutableEvents.emit(TransportEvent.StateChanged("局域网发现已启动"))
            } catch (error: Throwable) {
                job.cancel()
                supervisor = null
                closeSockets()
                withContext(NonCancellable) {
                    withTimeoutOrNull(STOP_JOIN_TIMEOUT_MS) { job.join() }
                }
                throw error
            }
        }
    }

    override suspend fun stop() {
        withContext(NonCancellable) {
            lifecycleMutex.withLock {
                val job = supervisor
                supervisor = null
                job?.cancel()
                closeSockets()
                if (job != null && withTimeoutOrNull(STOP_JOIN_TIMEOUT_MS) { job.join() } == null) {
                    mutableEvents.tryEmit(
                        TransportEvent.Failure(
                            "局域网后台任务未及时停止",
                            SocketTimeoutException("LAN transport stop timed out")
                        )
                    )
                }
            }
        }
        mutableEvents.emit(TransportEvent.StateChanged("局域网传输已停止"))
    }

    override suspend fun send(
        peer: TransportPeer,
        frame: ByteArray
    ) {
        val port = peer.port ?: servicePort
        require(port in VALID_PORT_RANGE) {
            "Peer port must be in $VALID_PORT_RANGE"
        }
        withContext(Dispatchers.IO) {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(peer.address, port), CONNECT_TIMEOUT_MS)
                runWithSocketTimeout(socket, WRITE_TIMEOUT_MS, "LAN frame write") {
                    FrameIo.writeFrame(socket.getOutputStream(), frame)
                }
            }
        }
    }

    private fun startServer(scope: CoroutineScope) {
        val listeningSocket = ServerSocket(servicePort)
        serverSocket = listeningSocket
        scope.launch {
            while (isActive) {
                try {
                    val socket = listeningSocket.accept()
                    if (!incomingConnectionPermits.tryAcquire()) {
                        socket.closeQuietly()
                        continue
                    }
                    try {
                        socket.soTimeout = READ_TIMEOUT_MS
                        acceptedSockets += socket
                        scope
                            .launch { handleIncomingSocket(socket) }
                            .invokeOnCompletion {
                                acceptedSockets.remove(socket)
                                socket.closeQuietly()
                                incomingConnectionPermits.release()
                            }
                    } catch (error: Throwable) {
                        acceptedSockets.remove(socket)
                        socket.closeQuietly()
                        incomingConnectionPermits.release()
                        throw error
                    }
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
        acquireMulticastLock()
        val socket =
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(discoveryPort))
            }
        discoverySocket = socket
        scope.launch { receiveDiscoveryPackets(socket) }
        scope.launch { broadcastPresence(socket) }
    }

    private suspend fun handleIncomingSocket(socket: Socket) {
        try {
            socket.use {
                val peer =
                    TransportPeer(
                        id = "lan:${socket.inetAddress.hostAddress}",
                        name = socket.inetAddress.hostAddress ?: "局域网设备",
                        address = socket.inetAddress.hostAddress.orEmpty(),
                        port = servicePort,
                        kind = TransportKind.LAN
                    )
                while (currentCoroutineContext().isActive) {
                    val frame =
                        runWithSocketTimeout(
                            socket,
                            READ_TIMEOUT_MS.toLong(),
                            "LAN frame read"
                        ) {
                            FrameIo.readFrame(socket.getInputStream())
                        } ?: break
                    mutableEvents.emit(TransportEvent.FrameReceived(peer, frame))
                }
            }
        } catch (error: Throwable) {
            if (currentCoroutineContext().isActive) {
                mutableEvents.emit(
                    TransportEvent.Failure("局域网帧接收失败", error)
                )
            }
        }
    }

    private suspend fun receiveDiscoveryPackets(socket: DatagramSocket) {
        val buffer = ByteArray(1024)
        while (currentCoroutineContext().isActive) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                parseBeacon(packet)?.let { peer ->
                    mutableEvents.emit(TransportEvent.PeerFound(peer))
                }
            } catch (error: Throwable) {
                if (currentCoroutineContext().isActive) {
                    mutableEvents.emit(
                        TransportEvent.Failure("局域网发现失败", error)
                    )
                    delay(ERROR_RETRY_DELAY_MS)
                }
            }
        }
    }

    private suspend fun broadcastPresence(socket: DatagramSocket) {
        val address = InetAddress.getByName("255.255.255.255")
        while (currentCoroutineContext().isActive) {
            try {
                val payload = beaconPayload().toByteArray(Charsets.UTF_8)
                val packet =
                    DatagramPacket(payload, payload.size, address, discoveryPort)
                socket.send(packet)
            } catch (error: Throwable) {
                if (currentCoroutineContext().isActive) {
                    mutableEvents.emit(
                        TransportEvent.Failure("局域网广播失败", error)
                    )
                }
            }
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
        val port =
            parts[3]
                .toIntOrNull()
                ?.takeIf { it in VALID_PORT_RANGE }
                ?: return null
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

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) {
            return
        }
        val lock = wifiManager?.createMulticastLock(MULTICAST_LOCK_TAG) ?: return
        lock.setReferenceCounted(false)
        runCatching {
            lock.acquire()
            multicastLock = lock
        }.onFailure { error ->
            mutableEvents.tryEmit(
                TransportEvent.Failure("局域网多播锁获取失败", error)
            )
        }
    }

    private fun releaseMulticastLock() {
        val lock = multicastLock ?: return
        runCatching {
            if (lock.isHeld) {
                lock.release()
            }
        }
        multicastLock = null
    }

    private suspend fun <T> runWithSocketTimeout(
        socket: Socket,
        timeoutMillis: Long,
        operationName: String,
        block: () -> T
    ): T {
        val operationJob = SupervisorJob()
        val operation = CoroutineScope(operationJob + Dispatchers.IO).async { block() }
        try {
            val completed =
                withTimeoutOrNull(timeoutMillis) {
                    CompletedSocketOperation(operation.await())
                }
            if (completed == null) {
                socket.closeQuietly()
                throw SocketTimeoutException("$operationName timed out")
            }
            return completed.value
        } finally {
            if (!operation.isCompleted) {
                socket.closeQuietly()
            }
            operation.cancel()
            operationJob.cancel()
        }
    }

    private fun closeSockets() {
        serverSocket.closeQuietly()
        discoverySocket.closeQuietly()
        acceptedSockets.forEach { it.closeQuietly() }
        releaseMulticastLock()
        serverSocket = null
        discoverySocket = null
    }

    private fun ServerSocket?.closeQuietly() {
        runCatching { this?.close() }
    }

    private fun DatagramSocket?.closeQuietly() {
        runCatching { this?.close() }
    }

    private fun Socket?.closeQuietly() {
        runCatching { this?.close() }
    }

    private data class CompletedSocketOperation<T>(
        val value: T
    )

    companion object {
        const val DEFAULT_SERVICE_PORT = 38441
        const val DEFAULT_DISCOVERY_PORT = 38442
        private const val BEACON_PREFIX = "SPOTCHAT_V1"
        private const val BEACON_PARTS = 4
        private const val CONNECT_TIMEOUT_MS = 2_500
        private const val READ_TIMEOUT_MS = 15_000
        private const val WRITE_TIMEOUT_MS = 10_000L
        private const val STOP_JOIN_TIMEOUT_MS = 3_000L
        private const val ERROR_RETRY_DELAY_MS = 500L
        private const val MAX_INCOMING_CONNECTIONS = 8
        private const val DISCOVERY_INTERVAL_MS = 3_000L
        private const val MULTICAST_LOCK_TAG = "SpotChatLanDiscovery"
        private val VALID_PORT_RANGE = 1..65_535
    }
}
