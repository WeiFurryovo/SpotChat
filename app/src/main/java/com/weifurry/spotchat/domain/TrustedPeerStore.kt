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
    val trustedAtEpochMillis: Long,
    val about: String = "",
    val alias: String = ""
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
        val existingPeer =
            all().firstOrNull { existing ->
                existing.fingerprint == peer.fingerprint || existing.publicKey == peer.publicKey
            }
        val storedPeer =
            StoredTrustedPeer(
                deviceName = peer.deviceName,
                fingerprint = peer.fingerprint,
                publicKey = peer.publicKey,
                pairingCode = peer.pairingCode,
                trustedAtEpochMillis = existingPeer?.trustedAtEpochMillis ?: System.currentTimeMillis(),
                about = peer.about,
                alias = existingPeer?.alias.orEmpty()
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

    fun updateAlias(
        fingerprint: String,
        alias: String
    ): StoredTrustedPeer? {
        var updatedPeer: StoredTrustedPeer? = null
        val normalizedAlias = alias.replace("\n", " ").trim().take(MAX_ALIAS_CHARS)
        val updatedPeers =
            all().map { peer ->
                if (peer.fingerprint == fingerprint) {
                    peer.copy(alias = normalizedAlias).also { updated -> updatedPeer = updated }
                } else {
                    peer
                }
            }
        prefs
            .edit()
            .putString(KEY_PEERS, json.encodeToString(updatedPeers))
            .apply()
        return updatedPeer
    }

    companion object {
        const val MAX_ALIAS_CHARS = 18
        private const val PREFS_NAME = "spotchat_trusted_peers"
        private const val KEY_PEERS = "peers"
    }
}
