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

class QuickTextReplyTileService : TileService() {
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
        val title = targetConversation?.title ?: "快捷回复"
        val subtitle =
            when {
                targetConversation == null -> "打开聊天后再回复"
                targetConversation.mentionCount > 0 -> "${targetConversation.mentionCount} 条提及"
                targetConversation.unreadCount > 0 -> "${targetConversation.unreadCount} 条未读"
                else -> "最近聊天"
            }
        val replies =
            SpotChatNotificationIntents.quickReplies
                .take(MAX_TILE_REPLIES)
                .map(CharSequence::toString)

        val column =
            LayoutElementBuilders.Column.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(spacer(10f))
                .addContent(text("文字回复", 18f, COLOR_TEXT_PRIMARY))
                .addContent(spacer(3f))
                .addContent(text(title, 13f, COLOR_ACCENT, maxLines = 1))
                .addContent(text(subtitle, 10f, COLOR_TEXT_MUTED, maxLines = 1))
                .addContent(spacer(7f))

        replies.chunked(REPLIES_PER_ROW).forEachIndexed { index, rowReplies ->
            column.addContent(replyRow(rowReplies, targetConversation?.id))
            if (index != replies.lastIndex / REPLIES_PER_ROW) {
                column.addContent(spacer(5f))
            }
        }

        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(ColorBuilders.argb(COLOR_BACKGROUND))
                            .build()
                    )
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setOnClick(openReplyAction(targetConversation?.id, null))
                            .build()
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(DimensionBuilders.dp(16f))
                            .setEnd(DimensionBuilders.dp(16f))
                            .setTop(DimensionBuilders.dp(7f))
                            .setBottom(DimensionBuilders.dp(7f))
                            .build()
                    )
                    .build()
            )
            .addContent(column.build())
            .build()
    }

    private fun replyRow(
        replies: List<String>,
        conversationId: String?
    ): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Row.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.dp(30f))
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .apply {
                replies.forEachIndexed { index, reply ->
                    addContent(replyButton(reply, conversationId))
                    if (index != replies.lastIndex) {
                        addContent(widthSpacer(5f))
                    }
                }
            }
            .build()

    private fun replyButton(
        reply: String,
        conversationId: String?
    ): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.dp(28f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(ColorBuilders.argb(COLOR_BUTTON_BACKGROUND))
                            .setCorner(
                                ModifiersBuilders.Corner.Builder()
                                    .setRadius(DimensionBuilders.dp(8f))
                                    .build()
                            )
                            .build()
                    )
                    .setBorder(
                        ModifiersBuilders.Border.Builder()
                            .setWidth(DimensionBuilders.dp(1f))
                            .setColor(ColorBuilders.argb(COLOR_BUTTON_BORDER))
                            .build()
                    )
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setOnClick(openReplyAction(conversationId, reply))
                            .build()
                    )
                    .build()
            )
            .addContent(text(reply, 12f, COLOR_TEXT_PRIMARY, maxLines = 1))
            .build()

    private fun targetConversation(snapshot: WearChatSnapshot): WearConversationSummary? =
        snapshot.latestMentionConversation
            ?: snapshot.latestUnreadConversation
            ?: snapshot.conversations.firstOrNull()

    private fun openReplyAction(
        conversationId: String?,
        replyText: String?
    ): ActionBuilders.Action =
        ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .apply {
                        addKeyToExtraMapping(
                            EXTRA_TEXT_REPLY_TILE_OPEN,
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
                        if (replyText != null) {
                            addKeyToExtraMapping(
                                EXTRA_TILE_REPLY_TEXT,
                                ActionBuilders.AndroidStringExtra.Builder()
                                    .setValue(replyText)
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

    private fun widthSpacer(widthDp: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Spacer.Builder()
            .setWidth(DimensionBuilders.dp(widthDp))
            .build()

    companion object {
        const val EXTRA_TEXT_REPLY_TILE_OPEN = "text_reply_tile_open"
        const val EXTRA_TILE_REPLY_TEXT = "tile_reply_text"
        private const val RESOURCES_VERSION = "spotchat-text-reply-tile-v1"
        private const val FRESHNESS_INTERVAL_MILLIS = 60_000L
        private const val MAX_TILE_REPLIES = 4
        private const val REPLIES_PER_ROW = 2
        private const val COLOR_BACKGROUND = 0xFF061616.toInt()
        private const val COLOR_BUTTON_BACKGROUND = 0xFF102A26.toInt()
        private const val COLOR_BUTTON_BORDER = 0xFF2A504B.toInt()
        private const val COLOR_TEXT_PRIMARY = 0xFFE9FFFA.toInt()
        private const val COLOR_TEXT_MUTED = 0xFFB6CFC9.toInt()
        private const val COLOR_ACCENT = 0xFF6CE5D4.toInt()
    }
}
