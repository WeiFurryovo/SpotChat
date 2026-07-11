package com.weifurry.spotchat.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatListFilteringTest {
    private val directConversation =
        ChatConversation(
            id = "direct:alice",
            kind = ConversationKind.Direct,
            title = "Alice",
            subtitle = "Direct"
        )
    private val groupConversation =
        ChatConversation(
            id = "group:nearby",
            kind = ConversationKind.Group,
            title = "Nearby",
            subtitle = "Group"
        )

    @Test
    fun filterConversationsAppliesTheSelectedFilter() {
        val context =
            context(
                unreadCounts = mapOf(directConversation.id to 2)
            )

        assertEquals(
            listOf(directConversation),
            filterConversations(
                conversations = listOf(directConversation, groupConversation),
                filter = ChatListFilter.Unread,
                context = context
            )
        )
        assertEquals(
            listOf(groupConversation),
            filterConversations(
                conversations = listOf(directConversation, groupConversation),
                filter = ChatListFilter.Group,
                context = context
            )
        )
    }

    @Test
    fun countConversationsByFilterReflectsConversationState() {
        val context =
            context(
                unreadCounts = mapOf(directConversation.id to 2),
                mentionCounts = mapOf(groupConversation.id to 1),
                favoriteConversationIds = mapOf(directConversation.id to true),
                pinnedConversationIds = mapOf(groupConversation.id to true),
                starredMessageIdsByConversation = mapOf(directConversation.id to setOf("message-1")),
                draftsByConversation = mapOf(groupConversation.id to ConversationDraft("draft")),
                lockedConversationIds = mapOf(groupConversation.id to true),
                disappearingModesByConversation =
                    mapOf(groupConversation.id to DisappearingMessageMode.OneHour),
                readReceiptsDisabledByConversation = mapOf(directConversation.id to true),
                mutedConversationIds = setOf(directConversation.id),
                retryableConversationIds = setOf(directConversation.id)
            )

        val counts =
            countConversationsByFilter(
                conversations = listOf(directConversation, groupConversation),
                context = context
            )

        assertEquals(2, counts[ChatListFilter.All])
        assertEquals(1, counts[ChatListFilter.Favorites])
        assertEquals(1, counts[ChatListFilter.Unread])
        assertEquals(1, counts[ChatListFilter.Pinned])
        assertEquals(1, counts[ChatListFilter.Starred])
        assertEquals(1, counts[ChatListFilter.Drafts])
        assertEquals(1, counts[ChatListFilter.Mentions])
        assertEquals(1, counts[ChatListFilter.Retryable])
        assertEquals(1, counts[ChatListFilter.Locked])
        assertEquals(1, counts[ChatListFilter.Muted])
        assertEquals(1, counts[ChatListFilter.Disappearing])
        assertEquals(1, counts[ChatListFilter.ReadReceiptsOff])
        assertEquals(1, counts[ChatListFilter.Direct])
        assertEquals(1, counts[ChatListFilter.Group])
    }

    @Test
    fun visibleChatListFiltersKeepsUsefulAndActiveFilters() {
        val visibleFilters =
            visibleChatListFilters(
                activeFilter = ChatListFilter.Pinned,
                filterCounts =
                    mapOf(
                        ChatListFilter.All to 3,
                        ChatListFilter.Unread to 1,
                        ChatListFilter.Pinned to 0,
                        ChatListFilter.Muted to 0
                    )
            )

        assertEquals(
            listOf(
                ChatListFilter.All,
                ChatListFilter.Unread,
                ChatListFilter.Pinned
            ),
            visibleFilters
        )
        assertTrue(ChatListFilter.Pinned in visibleFilters)
        assertFalse(ChatListFilter.Muted in visibleFilters)
    }

    private fun context(
        unreadCounts: Map<String, Int> = emptyMap(),
        mentionCounts: Map<String, Int> = emptyMap(),
        favoriteConversationIds: Map<String, Boolean> = emptyMap(),
        pinnedConversationIds: Map<String, Boolean> = emptyMap(),
        starredMessageIdsByConversation: Map<String, Set<String>> = emptyMap(),
        draftsByConversation: Map<String, ConversationDraft> = emptyMap(),
        lockedConversationIds: Map<String, Boolean> = emptyMap(),
        disappearingModesByConversation: Map<String, DisappearingMessageMode> = emptyMap(),
        readReceiptsDisabledByConversation: Map<String, Boolean> = emptyMap(),
        mutedConversationIds: Set<String> = emptySet(),
        retryableConversationIds: Set<String> = emptySet()
    ): ChatListFilterContext =
        ChatListFilterContext(
            unreadCounts = unreadCounts,
            mentionCounts = mentionCounts,
            favoriteConversationIds = favoriteConversationIds,
            pinnedConversationIds = pinnedConversationIds,
            starredMessageIdsByConversation = starredMessageIdsByConversation,
            draftsByConversation = draftsByConversation,
            lockedConversationIds = lockedConversationIds,
            disappearingModesByConversation = disappearingModesByConversation,
            readReceiptsDisabledByConversation = readReceiptsDisabledByConversation,
            isConversationMuted = mutedConversationIds::contains,
            hasRetryableMessages = retryableConversationIds::contains
        )
}
