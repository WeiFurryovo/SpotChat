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
import java.util.Locale

class RecentChatsTileService : TileService() {
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
        val conversations =
            if (snapshot.conversations.isEmpty()) {
                listOf(
                    WearConversationSummary(
                        id = EMPTY_CONVERSATION_ID,
                        title = "SpotChat",
                        subtitle = "等待新消息",
                        unreadCount = 0,
                        mentionCount = 0,
                        updatedAtEpochMillis = 0L
                    )
                )
            } else {
                snapshot.conversations.take(SpotChatWearStateStore.MAX_TILE_CONVERSATIONS)
            }
        val title =
            when {
                snapshot.hasMentions -> "提及 ${snapshot.totalMentionCount}"
                snapshot.hasUnread -> "未读 ${snapshot.totalUnreadCount}"
                else -> "最近聊天"
            }
        val subtitle =
            when {
                snapshot.hasMentions -> "${snapshot.mentionThreadCount} 个聊天提到了你"
                snapshot.hasUnread -> "${snapshot.unreadThreadCount} 个聊天有新消息"
                else -> "点按聊天直接打开"
            }

        val column =
            LayoutElementBuilders.Column.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(text(title, 17f, COLOR_TEXT_PRIMARY))
                .addContent(spacer(2f))
                .addContent(text(subtitle, 10f, COLOR_TEXT_MUTED, maxLines = 1))
                .addContent(spacer(7f))

        conversations.forEachIndexed { index, conversation ->
            column.addContent(conversationRow(conversation))
            if (index != conversations.lastIndex) {
                column.addContent(spacer(4f))
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
                            .setOnClick(openAppAction(null))
                            .build()
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(DimensionBuilders.dp(14f))
                            .setEnd(DimensionBuilders.dp(14f))
                            .setTop(DimensionBuilders.dp(12f))
                            .setBottom(DimensionBuilders.dp(8f))
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
        val targetConversationId =
            conversation.id.takeUnless { id -> id == EMPTY_CONVERSATION_ID }
        val statusPrefix =
            conversation
                .tileStatusLabels()
                .joinToString(separator = " · ")
        val subtitle =
            listOf(statusPrefix, conversation.subtitle)
                .filter { value -> value.isNotBlank() }
                .joinToString(separator = " · ")
        return LayoutElementBuilders.Row.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.dp(39f))
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(ColorBuilders.argb(COLOR_ROW_BACKGROUND))
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
                            .setColor(ColorBuilders.argb(rowBorderColor(conversation)))
                            .build()
                    )
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setOnClick(openAppAction(targetConversationId))
                            .build()
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(DimensionBuilders.dp(6f))
                            .setEnd(DimensionBuilders.dp(6f))
                            .build()
                    )
                    .build()
            )
            .addContent(avatar(conversation))
            .addContent(widthSpacer(7f))
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.wrap())
                    .addContent(text(conversation.title, 12f, COLOR_TEXT_PRIMARY, maxLines = 1))
                    .addContent(text(subtitle, 9f, COLOR_TEXT_MUTED, maxLines = 1))
                    .build()
            )
            .apply {
                tileBadge(conversation)?.let { badge ->
                    addContent(widthSpacer(5f))
                    addContent(badge)
                }
            }
            .build()
    }

    private fun avatar(conversation: WearConversationSummary): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.dp(28f))
            .setHeight(DimensionBuilders.dp(28f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(ColorBuilders.argb(avatarColor(conversation.id)))
                            .setCorner(
                                ModifiersBuilders.Corner.Builder()
                                    .setRadius(DimensionBuilders.dp(14f))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(text(conversation.title.tileInitial(), 13f, COLOR_AVATAR_TEXT, maxLines = 1))
            .build()

    private fun tileBadge(
        conversation: WearConversationSummary
    ): LayoutElementBuilders.LayoutElement? {
        val badgeText =
            when {
                conversation.mentionCount > 0 -> "@${conversation.mentionCount.coerceAtMost(99)}"
                conversation.unreadCount > 0 -> conversation.unreadCount.coerceAtMost(99).toString()
                conversation.isPinned -> "置顶"
                conversation.isMuted -> "静"
                else -> null
            } ?: return null
        val badgeColor =
            when {
                conversation.mentionCount > 0 -> COLOR_MENTION
                conversation.unreadCount > 0 -> COLOR_UNREAD
                else -> COLOR_MUTED_BADGE
            }
        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.dp(if (badgeText.length > 2) 31f else 24f))
            .setHeight(DimensionBuilders.dp(22f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(ColorBuilders.argb(badgeColor))
                            .setCorner(
                                ModifiersBuilders.Corner.Builder()
                                    .setRadius(DimensionBuilders.dp(11f))
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(text(badgeText, 9f, COLOR_BADGE_TEXT, maxLines = 1))
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
                        addKeyToExtraMapping(
                            SpotChatNotificationIntents.EXTRA_INTENT_TOKEN,
                            ActionBuilders.AndroidStringExtra.Builder()
                                .setValue(tokenStore.token())
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

    private fun widthSpacer(widthDp: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Spacer.Builder()
            .setWidth(DimensionBuilders.dp(widthDp))
            .build()

    private fun WearConversationSummary.tileStatusLabels(): List<String> =
        buildList {
            if (isPinned) {
                add("置顶")
            }
            if (isMuted) {
                add("静音")
            }
            if (mentionCount > 0) {
                add("提及 $mentionCount")
            } else if (unreadCount > 0) {
                add("未读 $unreadCount")
            }
        }

    private fun String.tileInitial(): String {
        val initial =
            trim()
                .firstOrNull()
                ?.toString()
                ?: "S"
        return initial.uppercase(Locale.getDefault())
    }

    private fun avatarColor(seed: String): Int {
        val index = ((seed.hashCode() xor (seed.hashCode() ushr 16)) and Int.MAX_VALUE) % AVATAR_COLORS.size
        return AVATAR_COLORS[index]
    }

    private fun rowBorderColor(conversation: WearConversationSummary): Int =
        when {
            conversation.mentionCount > 0 -> COLOR_MENTION_BORDER
            conversation.unreadCount > 0 -> COLOR_UNREAD_BORDER
            else -> COLOR_ROW_BORDER
        }

    companion object {
        const val EXTRA_TILE_OPEN = "tile_open"
        private const val EMPTY_CONVERSATION_ID = "empty"
        private const val RESOURCES_VERSION = "spotchat-tile-v1"
        private const val FRESHNESS_INTERVAL_MILLIS = 60_000L
        private const val COLOR_BACKGROUND = 0xFF061616.toInt()
        private const val COLOR_ROW_BACKGROUND = 0xFF0E2623.toInt()
        private const val COLOR_ROW_BORDER = 0xFF26433F.toInt()
        private const val COLOR_TEXT_PRIMARY = 0xFFE9FFFA.toInt()
        private const val COLOR_TEXT_MUTED = 0xFFB6CFC9.toInt()
        private const val COLOR_AVATAR_TEXT = 0xFF001F1B.toInt()
        private const val COLOR_BADGE_TEXT = 0xFF001F1B.toInt()
        private const val COLOR_MENTION = 0xFFFFCC66.toInt()
        private const val COLOR_UNREAD = 0xFF6CE5D4.toInt()
        private const val COLOR_MUTED_BADGE = 0xFF8CA6A0.toInt()
        private const val COLOR_MENTION_BORDER = 0x99FFCC66.toInt()
        private const val COLOR_UNREAD_BORDER = 0x996CE5D4.toInt()
        private val AVATAR_COLORS =
            intArrayOf(
                0xFF6CE5D4.toInt(),
                0xFFFFCC66.toInt(),
                0xFFFF9AAE.toInt(),
                0xFF9CB7FF.toInt(),
                0xFFB7F48B.toInt()
            )
    }
}
