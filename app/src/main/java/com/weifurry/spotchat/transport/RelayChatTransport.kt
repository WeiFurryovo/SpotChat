package com.weifurry.spotchat.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class RelayChatTransport(
    private val endpoint: String? = null
) : SpotChatTransport {
    private val mutableEvents = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 16)

    override val events: Flow<TransportEvent> = mutableEvents

    override suspend fun start() {
        mutableEvents.emit(
            TransportEvent.StateChanged(
                endpoint?.let { "中继待连接" } ?: "中继预留"
            )
        )
    }

    override suspend fun stop() {
        mutableEvents.emit(TransportEvent.StateChanged("中继已停止"))
    }

    override suspend fun send(
        peer: TransportPeer,
        frame: ByteArray
    ) {
        val error =
            IllegalStateException(
                endpoint?.let { "中继服务器尚未实现连接：$it" } ?: "尚未配置中继服务器"
            )
        mutableEvents.emit(TransportEvent.Failure(error.message.orEmpty(), error))
        throw error
    }
}
