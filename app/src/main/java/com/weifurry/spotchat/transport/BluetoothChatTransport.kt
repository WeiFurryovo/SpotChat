package com.weifurry.spotchat.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private var supervisor: Job? = null
    private var serverSocket: BluetoothServerSocket? = null

    override val events: Flow<TransportEvent> = mutableEvents

    fun bondedPeers(): List<TransportPeer> =
        bluetoothAdapter
            ?.bondedDevices
            .orEmpty()
            .map { device -> device.toTransportPeer() }

    override suspend fun start() {
        if (supervisor?.isActive == true) {
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            throw IllegalStateException("蓝牙不可用或尚未开启")
        }

        val job = SupervisorJob()
        supervisor = job
        val scope = CoroutineScope(job + Dispatchers.IO)
        try {
            serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, serviceUuid)
            scope.launch { acceptLoop(scope) }
            mutableEvents.emit(TransportEvent.StateChanged("蓝牙监听已启动"))
        } catch (error: Throwable) {
            job.cancel()
            closeServerSocket()
            supervisor = null
            throw error
        }
    }

    override suspend fun stop() {
        supervisor?.cancel()
        supervisor = null
        closeServerSocket()
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
                it.connect()
                FrameIo.writeFrame(it.outputStream, frame)
            }
        }
    }

    private suspend fun acceptLoop(scope: CoroutineScope) {
        while (supervisor?.isActive == true) {
            try {
                val socket = serverSocket?.accept() ?: break
                scope.launch { handleSocket(socket) }
            } catch (error: Throwable) {
                if (supervisor?.isActive == true) {
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
                while (supervisor?.isActive == true) {
                    val frame = FrameIo.readFrame(socket.inputStream) ?: break
                    mutableEvents.emit(TransportEvent.FrameReceived(peer, frame))
                }
            }
        } catch (error: Throwable) {
            if (supervisor?.isActive == true) {
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

    private fun closeServerSocket() {
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    companion object {
        private const val SERVICE_NAME = "SpotChat"
        val SPOTCHAT_SERVICE_UUID: UUID =
            UUID.fromString("6d5d4dc0-8b6b-4bb1-9124-f9a9d89941d1")
    }
}
