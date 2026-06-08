package com.weifurry.spotchat.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearChatSnapshotTest {
    @Test
    fun unreadThreadStatsUseAllConversations() {
        val snapshot =
            WearChatSnapshot(
                conversations =
                    listOf(
                        WearConversationSummary(
                            id = "first",
                            title = "First",
                            subtitle = "old unread",
                            unreadCount = 3,
                            updatedAtEpochMillis = 10L
                        ),
                        WearConversationSummary(
                            id = "read",
                            title = "Read",
                            subtitle = "read",
                            unreadCount = 0,
                            updatedAtEpochMillis = 30L
                        ),
                        WearConversationSummary(
                            id = "latest",
                            title = "Latest",
                            subtitle = "new unread",
                            unreadCount = 1,
                            updatedAtEpochMillis = 20L
                        )
                    )
            )

        assertTrue(snapshot.hasUnread)
        assertEquals(2, snapshot.unreadThreadCount)
        assertEquals(4, snapshot.totalUnreadCount)
        assertEquals("latest", snapshot.latestUnreadConversation?.id)
    }
}
