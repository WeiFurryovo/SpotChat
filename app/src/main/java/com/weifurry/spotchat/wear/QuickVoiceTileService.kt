package com.weifurry.spotchat.wear

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.weifurry.spotchat.notifications.SpotChatNotificationIntents
import com.weifurry.spotchat.notifications.SpotChatNotificationTokenStore
import com.weifurry.spotchat.presentation.MainActivity

class QuickVoiceTileService : TileService() {
    private val wearStateStore by lazy {
        SpotChatWearStateStore(this)
    }
    private val tokenStore by lazy {
        SpotChatNotificationTokenStore(this)
    }

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ) =
        Futures.immediateFuture(buildTile(wearStateStore.load()))

    private fun buildTile(snapshot: WearChatSnapshot): TileBuilders.Tile =
        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_INTERVAL_MILLIS)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(tileContent(snapshot))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()

    private fun tileContent(snapshot: WearChatSnapshot): LayoutElementBuilders.LayoutElement {
        val targetConversation = targetConversation(snapshot)
        val targetTitle = targetConversation?.title ?: "选择聊天"
        val subtitle =
            when {
                targetConversation == null -> "打开后选择聊天"
                targetConversation.unreadCount > 0 -> "${targetConversation.unreadCount} 条未读 · 点开录音"
                else -> "点开后录音"
            }
        val status =
            if (snapshot.hasUnread) {
                "${snapshot.unreadThreadCount} 个未读聊天"
            } else {
                "快速语音"
            }

        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(ColorBuilders.argb(0xFF061616.toInt()))
                            .build()
                    )
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setOnClick(openVoiceAction(targetConversation?.id))
                            .build()
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(DimensionBuilders.dp(16f))
                            .setEnd(DimensionBuilders.dp(16f))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.expand())
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(spacer(12f))
                    .addContent(text("语音消息", 20f, 0xFFE9FFFA.toInt()))
                    .addContent(spacer(6f))
                    .addContent(text(targetTitle, 15f, 0xFF6CE5D4.toInt(), maxLines = 1))
                    .addContent(spacer(4f))
                    .addContent(text(subtitle, 12f, 0xFFB6CFC9.toInt(), maxLines = 1))
                    .addContent(spacer(6f))
                    .addContent(text(status, 11f, 0xFFFFCC66.toInt(), maxLines = 1))
                    .build()
            )
            .build()
    }

    private fun targetConversation(snapshot: WearChatSnapshot): WearConversationSummary? =
        snapshot.latestUnreadConversation ?: snapshot.conversations.firstOrNull()

    private fun openVoiceAction(conversationId: String?): ActionBuilders.Action =
        ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .apply {
                        addKeyToExtraMapping(
                            EXTRA_VOICE_TILE_OPEN,
                            ActionBuilders.AndroidBooleanExtra.Builder()
                                .setValue(true)
                                .build()
                        )
                        addKeyToExtraMapping(
                            SpotChatNotificationIntents.EXTRA_INTENT_TOKEN,
                            ActionBuilders.AndroidStringExtra.Builder()
                                .setValue(tokenStore.token())
                                .build()
                        )
                        if (conversationId != null) {
                            addKeyToExtraMapping(
                                SpotChatNotificationIntents.EXTRA_CONVERSATION_ID,
                                ActionBuilders.AndroidStringExtra.Builder()
                                    .setValue(conversationId)
                                    .build()
                            )
                        }
                    }
                    .build()
            )
            .build()

    private fun text(
        value: String,
        sizeSp: Float,
        color: Int,
        maxLines: Int = 1
    ): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setMaxLines(maxLines)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(sizeSp))
                    .setColor(ColorBuilders.argb(color))
                    .build()
            )
            .build()

    private fun spacer(heightDp: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Spacer.Builder()
            .setHeight(DimensionBuilders.dp(heightDp))
            .build()

    companion object {
        const val EXTRA_VOICE_TILE_OPEN = "voice_tile_open"
        private const val RESOURCES_VERSION = "spotchat-voice-tile-v1"
        private const val FRESHNESS_INTERVAL_MILLIS = 60_000L
    }
}
