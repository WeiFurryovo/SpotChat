package com.weifurry.spotchat.wear

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class WearConversationSummary(
    val id: String,
    val title: String,
    val subtitle: String,
    val unreadCount: Int,
    val updatedAtEpochMillis: Long,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false
)

@Serializable
data class WearChatSnapshot(
    val conversations: List<WearConversationSummary> = emptyList(),
    val updatedAtEpochMillis: Long = 0L
) {
    val totalUnreadCount: Int =
        conversations.sumOf { conversation -> conversation.unreadCount }

    val hasUnread: Boolean =
        totalUnreadCount > 0
}

class SpotChatWearStateStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    fun load(): WearChatSnapshot =
        prefs
            .getString(KEY_SNAPSHOT, null)
            ?.let { encoded ->
                runCatching {
                    json.decodeFromString<WearChatSnapshot>(encoded)
                }.getOrNull()
            }
            ?: WearChatSnapshot()

    fun save(snapshot: WearChatSnapshot) {
        val normalized =
            snapshot.copy(
                conversations =
                    snapshot.conversations
                        .sortedWith(
                            compareByDescending<WearConversationSummary> { conversation ->
                                conversation.isPinned
                            }.thenByDescending { conversation ->
                                conversation.unreadCount > 0
                            }.thenByDescending { conversation -> conversation.updatedAtEpochMillis }
                        )
                        .take(MAX_TILE_CONVERSATIONS)
            )
        prefs.edit().putString(KEY_SNAPSHOT, json.encodeToString(normalized)).apply()
    }

    companion object {
        const val MAX_TILE_CONVERSATIONS = 3
        private const val PREFS_NAME = "spotchat_wear_state"
        private const val KEY_SNAPSHOT = "snapshot"
    }
}
