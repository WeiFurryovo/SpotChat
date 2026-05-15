package com.weifurry.spotchat.domain

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class StoredTrustedPeer(
    val deviceName: String,
    val fingerprint: String,
    val publicKey: String,
    val pairingCode: String,
    val trustedAtEpochMillis: Long
)

class TrustedPeerStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    fun all(): List<StoredTrustedPeer> =
        prefs
            .getString(KEY_PEERS, null)
            ?.let { encoded ->
                runCatching {
                    json.decodeFromString<List<StoredTrustedPeer>>(encoded)
                }.getOrNull()
            }
            .orEmpty()
            .sortedByDescending { peer -> peer.trustedAtEpochMillis }

    fun find(fingerprint: String): StoredTrustedPeer? =
        all().firstOrNull { peer -> peer.fingerprint == fingerprint }

    fun trust(peer: TrustedPeer): StoredTrustedPeer {
        val storedPeer =
            StoredTrustedPeer(
                deviceName = peer.deviceName,
                fingerprint = peer.fingerprint,
                publicKey = peer.publicKey,
                pairingCode = peer.pairingCode,
                trustedAtEpochMillis = System.currentTimeMillis()
            )
        val updatedPeers =
            all()
                .filterNot { existing ->
                    existing.fingerprint == storedPeer.fingerprint ||
                        existing.publicKey == storedPeer.publicKey
                }
                .plus(storedPeer)
        prefs
            .edit()
            .putString(KEY_PEERS, json.encodeToString(updatedPeers))
            .apply()
        return storedPeer
    }

    fun forget(
        fingerprint: String,
        publicKey: String? = null
    ) {
        val updatedPeers =
            all().filterNot { peer ->
                peer.fingerprint == fingerprint || peer.publicKey == publicKey
            }
        prefs
            .edit()
            .putString(KEY_PEERS, json.encodeToString(updatedPeers))
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "spotchat_trusted_peers"
        private const val KEY_PEERS = "peers"
    }
}
