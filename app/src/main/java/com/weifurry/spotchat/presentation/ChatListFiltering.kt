package com.weifurry.spotchat.presentation

internal enum class ChatListFilter(
    val label: String
) {
    All("全部"),
    Favorites("收藏"),
    Unread("未读"),
    Pinned("置顶"),
    Starred("星标"),
    Drafts("草稿"),
    Mentions("提及"),
    Retryable("未发送"),
    Locked("锁定"),
    Muted("静音"),
    Disappearing("限时"),
    ReadReceiptsOff("回执"),
    Direct("私聊"),
    Group("群聊")
}

internal data class ChatListFilterContext(
    val unreadCounts: Map<String, Int>,
    val mentionCounts: Map<String, Int>,
    val favoriteConversationIds: Map<String, Boolean>,
    val pinnedConversationIds: Map<String, Boolean>,
    val starredMessageIdsByConversation: Map<String, Set<String>>,
    val draftsByConversation: Map<String, ConversationDraft>,
    val lockedConversationIds: Map<String, Boolean>,
    val disappearingModesByConversation: Map<String, DisappearingMessageMode>,
    val readReceiptsDisabledByConversation: Map<String, Boolean>,
    val isConversationMuted: (String) -> Boolean,
    val hasRetryableMessages: (String) -> Boolean
)

internal fun filterConversations(
    conversations: List<ChatConversation>,
    filter: ChatListFilter,
    context: ChatListFilterContext
): List<ChatConversation> =
    conversations.filter { conversation ->
        conversation.matchesFilter(filter, context)
    }

internal fun countConversationsByFilter(
    conversations: List<ChatConversation>,
    context: ChatListFilterContext
): Map<ChatListFilter, Int> =
    ChatListFilter.entries.associateWith { filter ->
        conversations.count { conversation ->
            conversation.matchesFilter(filter, context)
        }
    }

internal fun visibleChatListFilters(
    activeFilter: ChatListFilter,
    filterCounts: Map<ChatListFilter, Int>
): List<ChatListFilter> =
    ChatListFilter.entries.filter { filter ->
        filter == ChatListFilter.All ||
            filter == activeFilter ||
            filterCounts.getOrDefault(filter, 0) > 0
    }

private fun ChatConversation.matchesFilter(
    filter: ChatListFilter,
    context: ChatListFilterContext
): Boolean =
    when (filter) {
        ChatListFilter.All -> true
        ChatListFilter.Favorites -> context.favoriteConversationIds[id] == true
        ChatListFilter.Unread -> (context.unreadCounts[id] ?: 0) > 0
        ChatListFilter.Pinned -> context.pinnedConversationIds[id] == true
        ChatListFilter.Starred -> context.starredMessageIdsByConversation[id].orEmpty().isNotEmpty()
        ChatListFilter.Drafts -> context.draftsByConversation[id] != null
        ChatListFilter.Mentions -> (context.mentionCounts[id] ?: 0) > 0
        ChatListFilter.Retryable -> context.hasRetryableMessages(id)
        ChatListFilter.Locked -> context.lockedConversationIds[id] == true
        ChatListFilter.Muted -> context.isConversationMuted(id)
        ChatListFilter.Disappearing ->
            (context.disappearingModesByConversation[id] ?: DisappearingMessageMode.Off) !=
                DisappearingMessageMode.Off
        ChatListFilter.ReadReceiptsOff -> context.readReceiptsDisabledByConversation[id] == true
        ChatListFilter.Direct -> kind == ConversationKind.Direct
        ChatListFilter.Group -> kind == ConversationKind.Group
    }
