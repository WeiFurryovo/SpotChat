package com.weifurry.spotchat.presentation

import com.weifurry.spotchat.notifications.SpotChatNotificationIntents
import com.weifurry.spotchat.protocol.MAX_CHAT_PAYLOAD_TEXT_CHARS
import com.weifurry.spotchat.wear.WearEntryIntentFields
import com.weifurry.spotchat.wear.WearEntryIntentResolution
import com.weifurry.spotchat.wear.WearEntryRequest
import com.weifurry.spotchat.wear.resolveWearEntryIntent

internal data class AppEntryIntentFields(
    val action: String? = null,
    val token: String? = null,
    val conversationId: String? = null,
    val quickReplyText: String? = null,
    val recentChatsOpen: Boolean = false,
    val quickVoiceOpen: Boolean = false,
    val quickTextReplyOpen: Boolean = false,
    val tileReplyText: String? = null
)

internal enum class NotificationEntryAction {
    OpenConversation,
    Reply,
    QuickReply,
    MarkRead,
    MuteEightHours,
    Dismiss
}

internal sealed interface AppEntryRequest {
    data class Wear(val request: WearEntryRequest) : AppEntryRequest

    data class Notification(
        val action: NotificationEntryAction,
        val conversationId: String,
        val replyText: String = ""
    ) : AppEntryRequest
}

internal sealed interface AppEntryResolution {
    data object NotEntry : AppEntryResolution

    data object Rejected : AppEntryResolution

    data class Accepted(val request: AppEntryRequest) : AppEntryResolution
}

internal enum class AppEntrySource(
    val label: String
) {
    Notification("通知"),
    RecentChatsTile("最近聊天 Tile"),
    VoiceTile("语音 Tile"),
    QuickReplyTile("快捷回复 Tile")
}

internal enum class AppEntryAction(
    val label: String
) {
    OpenChat("打开聊天"),
    Reply("回复"),
    QuickReply("快捷回复"),
    Voice("开始语音"),
    MarkRead("标为已读"),
    Mute("静音"),
    Dismiss("清除通知")
}

internal enum class AppEntryReplyKind(
    val source: AppEntrySource,
    val action: AppEntryAction
) {
    NotificationReply(
        source = AppEntrySource.Notification,
        action = AppEntryAction.Reply
    ),
    NotificationQuickReply(
        source = AppEntrySource.Notification,
        action = AppEntryAction.QuickReply
    ),
    WearQuickReply(
        source = AppEntrySource.QuickReplyTile,
        action = AppEntryAction.QuickReply
    )
}

internal sealed interface AppEntryPlan {
    data object Ignore : AppEntryPlan

    data class Recover(
        val source: AppEntrySource,
        val targetConversationId: String,
        val action: AppEntryAction
    ) : AppEntryPlan

    data class ShowConversationList(val action: AppEntryAction) : AppEntryPlan

    data class OpenConversation(
        val conversationId: String,
        val source: AppEntrySource,
        val action: AppEntryAction
    ) : AppEntryPlan

    data class MuteEightHours(val conversationId: String) : AppEntryPlan

    data class DismissNotification(val conversationId: String) : AppEntryPlan

    data class MarkRead(val conversationId: String) : AppEntryPlan

    data class Reply(
        val conversationId: String,
        val kind: AppEntryReplyKind,
        val text: String
    ) : AppEntryPlan
}

internal fun resolveAppEntryIntent(
    fields: AppEntryIntentFields,
    isTokenValid: (String?) -> Boolean
): AppEntryResolution {
    val wearResolution =
        resolveWearEntryIntent(
            fields =
                WearEntryIntentFields(
                    recentChatsOpen = fields.recentChatsOpen,
                    quickVoiceOpen = fields.quickVoiceOpen,
                    quickTextReplyOpen = fields.quickTextReplyOpen,
                    token = fields.token,
                    conversationId = fields.conversationId,
                    replyText = fields.tileReplyText
                ),
            isTokenValid = isTokenValid
        )
    when (wearResolution) {
        is WearEntryIntentResolution.Accepted ->
            return AppEntryResolution.Accepted(
                AppEntryRequest.Wear(wearResolution.request)
            )

        WearEntryIntentResolution.Rejected -> return AppEntryResolution.Rejected
        WearEntryIntentResolution.NotWearEntry -> Unit
    }

    val action = notificationEntryAction(fields.action) ?: return AppEntryResolution.NotEntry
    if (!isTokenValid(fields.token)) {
        return AppEntryResolution.Rejected
    }
    val conversationId = fields.conversationId ?: return AppEntryResolution.Rejected
    val replyText =
        when (action) {
            NotificationEntryAction.QuickReply -> normalizeAppEntryReply(fields.quickReplyText)
            else -> ""
        }
    return AppEntryResolution.Accepted(
        AppEntryRequest.Notification(
            action = action,
            conversationId = conversationId,
            replyText = replyText
        )
    )
}

internal fun planAppEntry(
    resolution: AppEntryResolution,
    conversationExists: (String) -> Boolean,
    remoteReplyTextProvider: () -> String? = { null }
): AppEntryPlan =
    when (resolution) {
        AppEntryResolution.NotEntry,
        AppEntryResolution.Rejected -> AppEntryPlan.Ignore

        is AppEntryResolution.Accepted ->
            when (val request = resolution.request) {
                is AppEntryRequest.Notification ->
                    planNotificationEntry(
                        request = request,
                        conversationExists = conversationExists,
                        remoteReplyTextProvider = remoteReplyTextProvider
                    )

                is AppEntryRequest.Wear -> planWearEntry(request.request, conversationExists)
            }
    }

private fun notificationEntryAction(action: String?): NotificationEntryAction? =
    when (action) {
        SpotChatNotificationIntents.ACTION_OPEN_CONVERSATION ->
            NotificationEntryAction.OpenConversation

        SpotChatNotificationIntents.ACTION_REPLY -> NotificationEntryAction.Reply
        SpotChatNotificationIntents.ACTION_QUICK_REPLY -> NotificationEntryAction.QuickReply
        SpotChatNotificationIntents.ACTION_MARK_READ -> NotificationEntryAction.MarkRead
        SpotChatNotificationIntents.ACTION_MUTE_8H -> NotificationEntryAction.MuteEightHours
        SpotChatNotificationIntents.ACTION_NOTIFICATION_DISMISSED ->
            NotificationEntryAction.Dismiss

        else -> null
    }

private fun normalizeAppEntryReply(text: String?): String =
    text
        ?.trim()
        ?.take(MAX_CHAT_PAYLOAD_TEXT_CHARS)
        .orEmpty()

private fun planNotificationEntry(
    request: AppEntryRequest.Notification,
    conversationExists: (String) -> Boolean,
    remoteReplyTextProvider: () -> String?
): AppEntryPlan {
    if (!conversationExists(request.conversationId)) {
        return AppEntryPlan.Recover(
            source = AppEntrySource.Notification,
            targetConversationId = request.conversationId,
            action = request.action.recoveryAction()
        )
    }
    return when (request.action) {
        NotificationEntryAction.OpenConversation ->
            AppEntryPlan.OpenConversation(
                conversationId = request.conversationId,
                source = AppEntrySource.Notification,
                action = AppEntryAction.OpenChat
            )

        NotificationEntryAction.Reply ->
            AppEntryPlan.Reply(
                conversationId = request.conversationId,
                kind = AppEntryReplyKind.NotificationReply,
                text = normalizeAppEntryReply(remoteReplyTextProvider())
            )

        NotificationEntryAction.QuickReply ->
            AppEntryPlan.Reply(
                conversationId = request.conversationId,
                kind = AppEntryReplyKind.NotificationQuickReply,
                text = request.replyText
            )

        NotificationEntryAction.MarkRead -> AppEntryPlan.MarkRead(request.conversationId)
        NotificationEntryAction.MuteEightHours ->
            AppEntryPlan.MuteEightHours(request.conversationId)

        NotificationEntryAction.Dismiss ->
            AppEntryPlan.DismissNotification(request.conversationId)
    }
}

private fun planWearEntry(
    request: WearEntryRequest,
    conversationExists: (String) -> Boolean
): AppEntryPlan =
    when (request) {
        is WearEntryRequest.RecentChats -> {
            val conversationId = request.conversationId ?: return AppEntryPlan.Ignore
            knownConversationPlan(
                conversationId = conversationId,
                source = AppEntrySource.RecentChatsTile,
                action = AppEntryAction.OpenChat,
                conversationExists = conversationExists
            )
        }

        is WearEntryRequest.QuickVoice -> {
            val conversationId =
                request.conversationId
                    ?: return AppEntryPlan.ShowConversationList(AppEntryAction.Voice)
            knownConversationPlan(
                conversationId = conversationId,
                source = AppEntrySource.VoiceTile,
                action = AppEntryAction.Voice,
                conversationExists = conversationExists
            )
        }

        is WearEntryRequest.QuickTextReply -> {
            val conversationId =
                request.conversationId
                    ?: return AppEntryPlan.ShowConversationList(AppEntryAction.QuickReply)
            if (!conversationExists(conversationId)) {
                AppEntryPlan.Recover(
                    source = AppEntrySource.QuickReplyTile,
                    targetConversationId = conversationId,
                    action = AppEntryAction.QuickReply
                )
            } else {
                AppEntryPlan.Reply(
                    conversationId = conversationId,
                    kind = AppEntryReplyKind.WearQuickReply,
                    text = normalizeAppEntryReply(request.replyText)
                )
            }
        }
    }

private fun knownConversationPlan(
    conversationId: String,
    source: AppEntrySource,
    action: AppEntryAction,
    conversationExists: (String) -> Boolean
): AppEntryPlan =
    if (conversationExists(conversationId)) {
        AppEntryPlan.OpenConversation(
            conversationId = conversationId,
            source = source,
            action = action
        )
    } else {
        AppEntryPlan.Recover(
            source = source,
            targetConversationId = conversationId,
            action = action
        )
    }

private fun NotificationEntryAction.recoveryAction(): AppEntryAction =
    when (this) {
        NotificationEntryAction.OpenConversation -> AppEntryAction.OpenChat
        NotificationEntryAction.Reply,
        NotificationEntryAction.QuickReply -> AppEntryAction.Reply

        NotificationEntryAction.MarkRead -> AppEntryAction.MarkRead
        NotificationEntryAction.MuteEightHours -> AppEntryAction.Mute
        NotificationEntryAction.Dismiss -> AppEntryAction.Dismiss
    }
