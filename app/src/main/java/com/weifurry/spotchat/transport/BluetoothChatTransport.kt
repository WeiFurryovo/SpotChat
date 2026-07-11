package com.weifurry.spotchat.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@SuppressLint("MissingPermission")
class BluetoothChatTransport(
    context: Context,
    private val serviceUuid: UUID = SPOTCHAT_SERVICE_UUID
) : SpotChatTransport {
    private val bluetoothAdapter: BluetoothAdapter? =
        context
            .applicationContext
            .getSystemService(BluetoothManager::class.java)
            ?.adapter
    private val mutableEvents = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 64)
    private val lifecycleMutex = Mutex()
    private val incomingConnectionPermits = Semaphore(MAX_INCOMING_CONNECTIONS)
    private val acceptedSockets = ConcurrentHashMap.newKeySet<BluetoothSocket>()
    private var supervisor: Job? = null
    private var serverSocket: BluetoothServerSocket? = null

    override val events: Flow<TransportEvent> = mutableEvents

    fun bondedPeers(): List<TransportPeer> =
        bluetoothAdapter
            ?.bondedDevices
            .orEmpty()
            .map { device -> device.toTransportPeer() }

    override suspend fun start() {
        lifecycleMutex.withLock {
            if (supervisor?.isActive == true) {
                return@withLock
            }
            val adapter = bluetoothAdapter
            if (adapter == null || !adapter.isEnabled) {
                throw IllegalStateException("蓝牙不可用或尚未开启")
            }

            val job = SupervisorJob()
            supervisor = job
            val scope = CoroutineScope(job + Dispatchers.IO)
            try {
                val listeningSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, serviceUuid)
                serverSocket = listeningSocket
                scope.launch { acceptLoop(scope, listeningSocket) }
                mutableEvents.emit(TransportEvent.StateChanged("蓝牙监听已启动"))
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
                            "蓝牙后台任务未及时停止",
                            SocketTimeoutException("Bluetooth transport stop timed out")
                        )
                    )
                }
            }
        }
        mutableEvents.emit(TransportEvent.StateChanged("蓝牙传输已停止"))
    }

    override suspend fun send(
        peer: TransportPeer,
        frame: ByteArray
    ) {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            val error = IllegalStateException("蓝牙不可用或尚未开启")
            mutableEvents.emit(TransportEvent.Failure(error.message.orEmpty(), error))
            throw error
        }

        withContext(Dispatchers.IO) {
            val device = adapter.getRemoteDevice(peer.address)
            val socket = device.createRfcommSocketToServiceRecord(serviceUuid)
            socket.use {
                runWithSocketTimeout(it, CONNECT_TIMEOUT_MS, "Bluetooth connection") {
                    it.connect()
                }
                runWithSocketTimeout(it, WRITE_TIMEOUT_MS, "Bluetooth frame write") {
                    FrameIo.writeFrame(it.outputStream, frame)
                }
            }
        }
    }

    private suspend fun acceptLoop(
        scope: CoroutineScope,
        listeningSocket: BluetoothServerSocket
    ) {
        while (currentCoroutineContext().isActive) {
            try {
                val socket = listeningSocket.accept()
                if (!incomingConnectionPermits.tryAcquire()) {
                    socket.closeQuietly()
                    continue
                }
                try {
                    acceptedSockets += socket
                    scope
                        .launch { handleSocket(socket) }
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
                if (currentCoroutineContext().isActive) {
                    mutableEvents.emit(TransportEvent.Failure("蓝牙接收失败", error))
                }
                break
            }
        }
    }

    private suspend fun handleSocket(socket: BluetoothSocket) {
        try {
            socket.use {
                val peer = socket.remoteDevice.toTransportPeer()
                while (currentCoroutineContext().isActive) {
                    val frame =
                        runWithSocketTimeout(socket, READ_TIMEOUT_MS, "Bluetooth frame read") {
                            FrameIo.readFrame(socket.inputStream)
                        } ?: break
                    mutableEvents.emit(TransportEvent.FrameReceived(peer, frame))
                }
            }
        } catch (error: Throwable) {
            if (currentCoroutineContext().isActive) {
                mutableEvents.emit(TransportEvent.Failure("蓝牙帧接收失败", error))
            }
        }
    }

    private fun BluetoothDevice.toTransportPeer(): TransportPeer =
        TransportPeer(
            id = "bluetooth:$address",
            name = name ?: "蓝牙设备",
            address = address,
            kind = TransportKind.BLUETOOTH
        )

    private suspend fun <T> runWithSocketTimeout(
        socket: BluetoothSocket,
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
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptedSockets.forEach { it.closeQuietly() }
    }

    private fun BluetoothSocket?.closeQuietly() {
        runCatching { this?.close() }
    }

    private data class CompletedSocketOperation<T>(
        val value: T
    )

    companion object {
        private const val SERVICE_NAME = "SpotChat"
        private const val MAX_INCOMING_CONNECTIONS = 4
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val READ_TIMEOUT_MS = 30_000L
        private const val WRITE_TIMEOUT_MS = 30_000L
        private const val STOP_JOIN_TIMEOUT_MS = 3_000L
        val SPOTCHAT_SERVICE_UUID: UUID =
            UUID.fromString("6d5d4dc0-8b6b-4bb1-9124-f9a9d89941d1")
    }
}
