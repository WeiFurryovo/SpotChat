package com.weifurry.spotchat.wear

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.weifurry.spotchat.notifications.SpotChatNotificationIntents
import com.weifurry.spotchat.notifications.SpotChatNotificationTokenStore
import com.weifurry.spotchat.presentation.MainActivity

class UnreadThreadsComplicationDataSourceService : ComplicationDataSourceService() {
    private val wearStateStore by lazy {
        SpotChatWearStateStore(this)
    }
    private val tokenStore by lazy {
        SpotChatNotificationTokenStore(this)
    }

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        listener.onComplicationData(buildComplicationData(wearStateStore.load()))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        buildComplicationData(
            WearChatSnapshot(
                conversations =
                    listOf(
                        WearConversationSummary(
                            id = "preview",
                            title = "SpotChat",
                            subtitle = "2 条未读",
                            unreadCount = 2,
                            mentionCount = 1,
                            updatedAtEpochMillis = System.currentTimeMillis()
                        )
                    ),
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        )

    private fun buildComplicationData(snapshot: WearChatSnapshot): ComplicationData {
        val unreadThreadCount = snapshot.unreadThreadCount
        val totalUnreadCount = snapshot.totalUnreadCount
        val mentionThreadCount = snapshot.mentionThreadCount
        val totalMentionCount = snapshot.totalMentionCount
        val targetConversation =
            snapshot.latestMentionConversation ?: snapshot.latestUnreadConversation
        val shortText =
            if (mentionThreadCount > 0) {
                "@${totalMentionCount.coerceAtMost(99)}"
            } else if (unreadThreadCount > 0) {
                unreadThreadCount.coerceAtMost(99).toString()
            } else {
                "0"
            }
        val title =
            when {
                mentionThreadCount > 0 -> "$totalMentionCount 条提及"
                unreadThreadCount <= 0 -> "SpotChat"
                totalUnreadCount == 1 -> "1 条消息"
                else -> "$totalUnreadCount 条消息"
            }
        val contentDescription =
            if (mentionThreadCount > 0) {
                "SpotChat 有 $mentionThreadCount 个聊天提及你，$totalMentionCount 条提及消息"
            } else if (unreadThreadCount > 0) {
                "SpotChat 有 $unreadThreadCount 个未读聊天，$totalUnreadCount 条未读消息"
            } else {
                "SpotChat 没有未读聊天"
            }
        return ShortTextComplicationData.Builder(
            text = plainText(shortText),
            contentDescription = plainText(contentDescription)
        )
            .setTitle(plainText(title))
            .setTapAction(openAppPendingIntent(targetConversation?.id))
            .build()
    }

    private fun plainText(text: String): PlainComplicationText =
        PlainComplicationText.Builder(text).build()

    private fun openAppPendingIntent(conversationId: String?): PendingIntent =
        PendingIntent.getActivity(
            this,
            COMPLICATION_OPEN_REQUEST_CODE,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(RecentChatsTileService.EXTRA_TILE_OPEN, conversationId != null)
                .apply {
                    if (conversationId != null) {
                        putExtra(SpotChatNotificationIntents.EXTRA_CONVERSATION_ID, conversationId)
                        putExtra(
                            SpotChatNotificationIntents.EXTRA_INTENT_TOKEN,
                            tokenStore.token()
                        )
                    }
                },
            pendingIntentFlags()
        )

    private fun pendingIntentFlags(): Int {
        val mutabilityFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
        return PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
    }

    private companion object {
        private const val COMPLICATION_OPEN_REQUEST_CODE = 73_000
    }
}
