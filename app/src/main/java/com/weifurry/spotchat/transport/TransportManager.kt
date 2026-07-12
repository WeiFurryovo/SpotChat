package com.weifurry.spotchat.transport

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.Flow

internal class TransportManager internal constructor(
    private val lanTransport: SpotChatTransport,
    private val bluetoothTransport: SpotChatTransport,
    private val relayTransport: SpotChatTransport,
    private val bondedBluetoothPeersProvider: () -> List<TransportPeer> = { emptyList() },
    private val lanConnectionProvider: () -> Boolean = { false }
) {
    fun events(kind: TransportKind): Flow<TransportEvent> = transport(kind).events

    suspend fun start(kind: TransportKind) {
        transport(kind).start()
    }

    suspend fun stop(kind: TransportKind) {
        transport(kind).stop()
    }

    suspend fun send(
        kind: TransportKind,
        peer: TransportPeer,
        frame: ByteArray
    ) {
        transport(kind).send(peer, frame)
    }

    fun transportHints(kind: TransportKind): List<String> =
        when (kind) {
            TransportKind.LAN -> listOf("lan:${LanChatTransport.DEFAULT_SERVICE_PORT}")
            TransportKind.BLUETOOTH ->
                listOf("bluetooth:${BluetoothChatTransport.SPOTCHAT_SERVICE_UUID}")
            TransportKind.RELAY -> listOf("relay:encrypted-mailbox:v1")
        }

    fun bondedBluetoothPeers(): List<TransportPeer> = bondedBluetoothPeersProvider()

    fun hasLanConnection(): Boolean = lanConnectionProvider()

    private fun transport(kind: TransportKind): SpotChatTransport =
        when (kind) {
            TransportKind.LAN -> lanTransport
            TransportKind.BLUETOOTH -> bluetoothTransport
            TransportKind.RELAY -> relayTransport
        }

    companion object {
        fun create(context: Context): TransportManager {
            val appContext = context.applicationContext
            val bluetoothTransport = BluetoothChatTransport(appContext)
            return TransportManager(
                lanTransport = LanChatTransport(context = appContext),
                bluetoothTransport = bluetoothTransport,
                relayTransport = RelayChatTransport(),
                bondedBluetoothPeersProvider = bluetoothTransport::bondedPeers,
                lanConnectionProvider = { appContext.hasLanConnection() }
            )
        }

        private fun Context.hasLanConnection(): Boolean {
            val connectivityManager =
                getSystemService(ConnectivityManager::class.java) ?: return false
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
    }
}
