package com.weifurry.spotchat.wear

internal data class WearEntryIntentFields(
    val recentChatsOpen: Boolean = false,
    val quickVoiceOpen: Boolean = false,
    val quickTextReplyOpen: Boolean = false,
    val token: String? = null,
    val conversationId: String? = null,
    val replyText: String? = null
)

internal sealed interface WearEntryRequest {
    data class RecentChats(val conversationId: String?) : WearEntryRequest

    data class QuickVoice(val conversationId: String?) : WearEntryRequest

    data class QuickTextReply(
        val conversationId: String?,
        val replyText: String?
    ) : WearEntryRequest
}

internal sealed interface WearEntryIntentResolution {
    data object NotWearEntry : WearEntryIntentResolution

    data object Rejected : WearEntryIntentResolution

    data class Accepted(val request: WearEntryRequest) : WearEntryIntentResolution
}

internal fun resolveWearEntryIntent(
    fields: WearEntryIntentFields,
    isTokenValid: (String?) -> Boolean
): WearEntryIntentResolution {
    val markerCount =
        listOf(
            fields.recentChatsOpen,
            fields.quickVoiceOpen,
            fields.quickTextReplyOpen
        ).count { marker -> marker }
    if (markerCount == 0) {
        return WearEntryIntentResolution.NotWearEntry
    }
    if (markerCount != 1 || !isTokenValid(fields.token)) {
        return WearEntryIntentResolution.Rejected
    }
    val request =
        when {
            fields.recentChatsOpen -> WearEntryRequest.RecentChats(fields.conversationId)
            fields.quickVoiceOpen -> WearEntryRequest.QuickVoice(fields.conversationId)
            else ->
                WearEntryRequest.QuickTextReply(
                    conversationId = fields.conversationId,
                    replyText = fields.replyText
                )
        }
    return WearEntryIntentResolution.Accepted(request)
}
