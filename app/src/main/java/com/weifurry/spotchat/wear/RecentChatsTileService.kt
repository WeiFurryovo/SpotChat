package com.weifurry.spotchat.wear

import android.content.Intent
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
import com.weifurry.spotchat.presentation.MainActivity
import com.weifurry.spotchat.notifications.SpotChatNotificationIntents

class RecentChatsTileService : TileService() {
    private val wearStateStore by lazy {
        SpotChatWearStateStore(this)
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
        val conversations =
            if (snapshot.conversations.isEmpty()) {
                listOf(
                    WearConversationSummary(
                        id = EMPTY_CONVERSATION_ID,
                        title = "SpotChat",
                        subtitle = "等待新消息",
                        unreadCount = 0,
                        updatedAtEpochMillis = 0L
                    )
                )
            } else {
                snapshot.conversations.take(SpotChatWearStateStore.MAX_TILE_CONVERSATIONS)
            }

        val column =
            LayoutElementBuilders.Column.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(
                    text(
                        value =
                            if (snapshot.hasUnread) {
                                "未读 ${snapshot.totalUnreadCount}"
                            } else {
                                "SpotChat"
                            },
                        sizeSp = 18f,
                        color = 0xFFE9FFFA.toInt()
                    )
                )
                .addContent(spacer(8f))

        conversations.forEach { conversation ->
            column
                .addContent(conversationRow(conversation))
                .addContent(spacer(5f))
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
                            .setOnClick(openAppAction(null))
                            .build()
                    )
                    .build()
            )
            .addContent(column.build())
            .build()
    }

    private fun conversationRow(
        conversation: WearConversationSummary
    ): LayoutElementBuilders.LayoutElement {
        val titlePrefix =
            listOfNotNull(
                "置顶".takeIf { conversation.isPinned },
                "静音".takeIf { conversation.isMuted }
            )
                .takeIf { flags -> flags.isNotEmpty() }
                ?.joinToString(separator = " · ", postfix = " · ")
                .orEmpty()
        val title =
            if (conversation.unreadCount > 0) {
                "$titlePrefix${conversation.title} · ${conversation.unreadCount}"
            } else {
                "$titlePrefix${conversation.title}"
            }
        val targetConversationId =
            conversation.id.takeUnless { id -> id == EMPTY_CONVERSATION_ID }
        return LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setOnClick(openAppAction(targetConversationId))
                            .build()
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(DimensionBuilders.dp(12f))
                            .setEnd(DimensionBuilders.dp(12f))
                            .build()
                    )
                    .build()
            )
            .addContent(text(title, 13f, 0xFF6CE5D4.toInt(), maxLines = 1))
            .addContent(text(conversation.subtitle, 11f, 0xFFB6CFC9.toInt(), maxLines = 1))
            .build()
    }

    private fun openAppAction(conversationId: String?): ActionBuilders.Action =
        ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .apply {
                        if (conversationId != null) {
                            addKeyToExtraMapping(
                                SpotChatNotificationIntents.EXTRA_CONVERSATION_ID,
                                ActionBuilders.AndroidStringExtra.Builder()
                                    .setValue(conversationId)
                                    .build()
                            )
                        }
                        addKeyToExtraMapping(
                            EXTRA_TILE_OPEN,
                            ActionBuilders.AndroidBooleanExtra.Builder()
                                .setValue(true)
                                .build()
                        )
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
        const val EXTRA_TILE_OPEN = "tile_open"
        private const val EMPTY_CONVERSATION_ID = "empty"
        private const val RESOURCES_VERSION = "spotchat-tile-v1"
        private const val FRESHNESS_INTERVAL_MILLIS = 60_000L
    }
}
