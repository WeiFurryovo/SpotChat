package com.weifurry.spotchat.wear

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class WearConversationSummary(
    val id: String,
    val title: String,
    val subtitle: String,
    val unreadCount: Int,
    val mentionCount: Int = 0,
    val updatedAtEpochMillis: Long,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false
)

@Serializable
data class WearChatSnapshot(
    val conversations: List<WearConversationSummary> = emptyList(),
    val updatedAtEpochMillis: Long = 0L
) {
    val unreadThreadCount: Int =
        conversations.count { conversation -> conversation.unreadCount > 0 }

    val totalUnreadCount: Int =
        conversations.sumOf { conversation -> conversation.unreadCount }

    val mentionThreadCount: Int =
        conversations.count { conversation -> conversation.mentionCount > 0 }

    val totalMentionCount: Int =
        conversations.sumOf { conversation -> conversation.mentionCount }

    val hasUnread: Boolean =
        unreadThreadCount > 0

    val hasMentions: Boolean =
        mentionThreadCount > 0

    val latestUnreadConversation: WearConversationSummary? =
        conversations
            .filter { conversation -> conversation.unreadCount > 0 }
            .maxByOrNull { conversation -> conversation.updatedAtEpochMillis }

    val latestMentionConversation: WearConversationSummary? =
        conversations
            .filter { conversation -> conversation.mentionCount > 0 }
            .maxByOrNull { conversation -> conversation.updatedAtEpochMillis }
}

class SpotChatWearStateStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
                                conversation.mentionCount > 0
                            }.thenByDescending { conversation ->
                                conversation.unreadCount > 0
                            }.thenByDescending { conversation -> conversation.updatedAtEpochMillis }
                        )
            )
        val encodedSnapshot = json.encodeToString(normalized)
        val previousSnapshot = prefs.getString(KEY_SNAPSHOT, null)
        if (previousSnapshot == encodedSnapshot) {
            return
        }
        prefs.edit().putString(KEY_SNAPSHOT, encodedSnapshot).apply()
        requestTileUpdates()
        requestUnreadComplicationUpdate()
    }

    fun clearConversationAlerts(conversationId: String) {
        val snapshot = load()
        var changed = false
        val updatedConversations =
            snapshot.conversations.map { conversation ->
                if (
                    conversation.id == conversationId &&
                    (conversation.unreadCount > 0 || conversation.mentionCount > 0)
                ) {
                    changed = true
                    conversation.copy(
                        unreadCount = 0,
                        mentionCount = 0
                    )
                } else {
                    conversation
                }
            }
        if (!changed) {
            return
        }
        save(
            snapshot.copy(
                conversations = updatedConversations,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        )
    }

    private fun requestTileUpdates() {
        runCatching {
            val updater = TileService.getUpdater(appContext)
            updater.requestUpdate(RecentChatsTileService::class.java)
            updater.requestUpdate(QuickVoiceTileService::class.java)
        }
    }

    private fun requestUnreadComplicationUpdate() {
        runCatching {
            ComplicationDataSourceUpdateRequester
                .create(
                    appContext,
                    ComponentName(appContext, UnreadThreadsComplicationDataSourceService::class.java)
                )
                .requestUpdateAll()
        }
    }

    companion object {
        const val MAX_TILE_CONVERSATIONS = 3
        private const val PREFS_NAME = "spotchat_wear_state"
        private const val KEY_SNAPSHOT = "snapshot"
    }
}
