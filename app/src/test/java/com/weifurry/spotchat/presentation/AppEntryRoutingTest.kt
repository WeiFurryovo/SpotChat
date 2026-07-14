package com.weifurry.spotchat.presentation

import com.weifurry.spotchat.notifications.SpotChatNotificationIntents
import com.weifurry.spotchat.wear.WearEntryRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppEntryRoutingTest {
    @Test
    fun ordinaryAndUnknownIntentsAreIgnoredWithoutReadingRemoteReply() {
        var remoteReplyReads = 0
        val fields =
            listOf(
                AppEntryIntentFields(),
                AppEntryIntentFields(
                    action = "com.weifurry.spotchat.action.UNKNOWN",
                    token = VALID_TOKEN,
                    conversationId = CONVERSATION_ID
                )
            )

        fields.forEach { intentFields ->
            val resolution = resolve(intentFields)

            assertEquals(AppEntryResolution.NotEntry, resolution)
            assertEquals(
                AppEntryPlan.Ignore,
                planAppEntry(
                    resolution = resolution,
                    conversationExists = { true },
                    remoteReplyTextProvider = {
                        remoteReplyReads += 1
                        "must not be read"
                    }
                )
            )
        }
        assertEquals(0, remoteReplyReads)
    }

    @Test
    fun everyNotificationActionRequiresATrustedToken() {
        notificationActions.forEach { action ->
            listOf(null, "wrong-token").forEach { token ->
                assertEquals(
                    AppEntryResolution.Rejected,
                    resolve(
                        AppEntryIntentFields(
                            action = action,
                            token = token,
                            conversationId = CONVERSATION_ID
                        )
                    )
                )
            }
        }
    }

    @Test
    fun everyNotificationActionResolvesAndPlansForAKnownConversation() {
        val cases =
            listOf(
                NotificationCase(
                    intentAction = SpotChatNotificationIntents.ACTION_OPEN_CONVERSATION,
                    entryAction = NotificationEntryAction.OpenConversation,
                    expectedPlan =
                        AppEntryPlan.OpenConversation(
                            conversationId = CONVERSATION_ID,
                            source = AppEntrySource.Notification,
                            action = AppEntryAction.OpenChat
                        )
                ),
                NotificationCase(
                    intentAction = SpotChatNotificationIntents.ACTION_REPLY,
                    entryAction = NotificationEntryAction.Reply,
                    expectedPlan =
                        AppEntryPlan.Reply(
                            conversationId = CONVERSATION_ID,
                            kind = AppEntryReplyKind.NotificationReply,
                            text = "remote reply"
                        )
                ),
                NotificationCase(
                    intentAction = SpotChatNotificationIntents.ACTION_QUICK_REPLY,
                    entryAction = NotificationEntryAction.QuickReply,
                    resolvedReplyText = "quick reply",
                    expectedPlan =
                        AppEntryPlan.Reply(
                            conversationId = CONVERSATION_ID,
                            kind = AppEntryReplyKind.NotificationQuickReply,
                            text = "quick reply"
                        )
                ),
                NotificationCase(
                    intentAction = SpotChatNotificationIntents.ACTION_MARK_READ,
                    entryAction = NotificationEntryAction.MarkRead,
                    expectedPlan = AppEntryPlan.MarkRead(CONVERSATION_ID)
                ),
                NotificationCase(
                    intentAction = SpotChatNotificationIntents.ACTION_MUTE_8H,
                    entryAction = NotificationEntryAction.MuteEightHours,
                    expectedPlan = AppEntryPlan.MuteEightHours(CONVERSATION_ID)
                ),
                NotificationCase(
                    intentAction = SpotChatNotificationIntents.ACTION_NOTIFICATION_DISMISSED,
                    entryAction = NotificationEntryAction.Dismiss,
                    expectedPlan = AppEntryPlan.DismissNotification(CONVERSATION_ID)
                )
            )

        cases.forEach { case ->
            val resolution =
                resolve(
                    AppEntryIntentFields(
                        action = case.intentAction,
                        token = VALID_TOKEN,
                        conversationId = CONVERSATION_ID,
                        quickReplyText = "  quick reply  "
                    )
                )

            assertEquals(
                AppEntryResolution.Accepted(
                    AppEntryRequest.Notification(
                        action = case.entryAction,
                        conversationId = CONVERSATION_ID,
                        replyText = case.resolvedReplyText
                    )
                ),
                resolution
            )
            assertEquals(
                case.expectedPlan,
                planAppEntry(
                    resolution = resolution,
                    conversationExists = { it == CONVERSATION_ID },
                    remoteReplyTextProvider = { "  remote reply  " }
                )
            )
        }
    }

    @Test
    fun missingConversationIdRejectsReplyBeforeReadingRemoteInput() {
        var remoteReplyReads = 0
        val resolution =
            resolve(
                AppEntryIntentFields(
                    action = SpotChatNotificationIntents.ACTION_REPLY,
                    token = VALID_TOKEN
                )
            )

        assertEquals(AppEntryResolution.Rejected, resolution)
        assertEquals(
            AppEntryPlan.Ignore,
            planAppEntry(
                resolution = resolution,
                conversationExists = { true },
                remoteReplyTextProvider = {
                    remoteReplyReads += 1
                    "must not be read"
                }
            )
        )
        assertEquals(0, remoteReplyReads)
    }

    @Test
    fun missingStoredConversationUsesTheCorrectRecoveryActionWithoutReadingRemoteInput() {
        val cases =
            listOf(
                SpotChatNotificationIntents.ACTION_OPEN_CONVERSATION to AppEntryAction.OpenChat,
                SpotChatNotificationIntents.ACTION_REPLY to AppEntryAction.Reply,
                SpotChatNotificationIntents.ACTION_QUICK_REPLY to AppEntryAction.Reply,
                SpotChatNotificationIntents.ACTION_MARK_READ to AppEntryAction.MarkRead,
                SpotChatNotificationIntents.ACTION_MUTE_8H to AppEntryAction.Mute,
                SpotChatNotificationIntents.ACTION_NOTIFICATION_DISMISSED to AppEntryAction.Dismiss
            )
        var remoteReplyReads = 0

        cases.forEach { (intentAction, expectedRecoveryAction) ->
            val resolution =
                resolve(
                    AppEntryIntentFields(
                        action = intentAction,
                        token = VALID_TOKEN,
                        conversationId = UNKNOWN_CONVERSATION_ID,
                        quickReplyText = "quick reply"
                    )
                )

            assertEquals(
                AppEntryPlan.Recover(
                    source = AppEntrySource.Notification,
                    targetConversationId = UNKNOWN_CONVERSATION_ID,
                    action = expectedRecoveryAction
                ),
                planAppEntry(
                    resolution = resolution,
                    conversationExists = { false },
                    remoteReplyTextProvider = {
                        remoteReplyReads += 1
                        "must not be read"
                    }
                )
            )
        }
        assertEquals(0, remoteReplyReads)
    }

    @Test
    fun remoteReplyIsReadOnlyAfterAuthenticationAndConversationLookupSucceed() {
        val resolution =
            resolve(
                AppEntryIntentFields(
                    action = SpotChatNotificationIntents.ACTION_REPLY,
                    token = VALID_TOKEN,
                    conversationId = CONVERSATION_ID
                )
            )
        var remoteReplyReads = 0

        assertEquals(
            AppEntryPlan.Reply(
                conversationId = CONVERSATION_ID,
                kind = AppEntryReplyKind.NotificationReply,
                text = "reply"
            ),
            planAppEntry(
                resolution = resolution,
                conversationExists = { it == CONVERSATION_ID },
                remoteReplyTextProvider = {
                    remoteReplyReads += 1
                    "  reply  "
                }
            )
        )
        assertEquals(1, remoteReplyReads)

        assertEquals(
            AppEntryPlan.Recover(
                source = AppEntrySource.Notification,
                targetConversationId = CONVERSATION_ID,
                action = AppEntryAction.Reply
            ),
            planAppEntry(
                resolution = resolution,
                conversationExists = { false },
                remoteReplyTextProvider = {
                    remoteReplyReads += 1
                    "must not be read"
                }
            )
        )
        assertEquals(1, remoteReplyReads)
    }

    @Test
    fun quickReplyNeverReadsRemoteInput() {
        var remoteReplyReads = 0
        val resolution =
            resolve(
                AppEntryIntentFields(
                    action = SpotChatNotificationIntents.ACTION_QUICK_REPLY,
                    token = VALID_TOKEN,
                    conversationId = CONVERSATION_ID,
                    quickReplyText = "  selected reply  "
                )
            )

        assertEquals(
            AppEntryPlan.Reply(
                conversationId = CONVERSATION_ID,
                kind = AppEntryReplyKind.NotificationQuickReply,
                text = "selected reply"
            ),
            planAppEntry(
                resolution = resolution,
                conversationExists = { true },
                remoteReplyTextProvider = {
                    remoteReplyReads += 1
                    "must not be read"
                }
            )
        )
        assertEquals(0, remoteReplyReads)
    }

    @Test
    fun everyReplySourceTrimsAndLimitsTextTo280Characters() {
        val oversizedReply = " \n" + "x".repeat(300) + "\n "
        val expected = "x".repeat(280)

        val remoteReplyResolution =
            resolve(
                AppEntryIntentFields(
                    action = SpotChatNotificationIntents.ACTION_REPLY,
                    token = VALID_TOKEN,
                    conversationId = CONVERSATION_ID
                )
            )
        assertEquals(
            AppEntryPlan.Reply(
                conversationId = CONVERSATION_ID,
                kind = AppEntryReplyKind.NotificationReply,
                text = expected
            ),
            planAppEntry(
                resolution = remoteReplyResolution,
                conversationExists = { true },
                remoteReplyTextProvider = { oversizedReply }
            )
        )

        val quickReplyResolution =
            resolve(
                AppEntryIntentFields(
                    action = SpotChatNotificationIntents.ACTION_QUICK_REPLY,
                    token = VALID_TOKEN,
                    conversationId = CONVERSATION_ID,
                    quickReplyText = oversizedReply
                )
            )
        assertEquals(
            expected,
            ((quickReplyResolution as AppEntryResolution.Accepted).request as AppEntryRequest.Notification)
                .replyText
        )
        assertEquals(
            expected,
            (planAppEntry(quickReplyResolution, conversationExists = { true }) as AppEntryPlan.Reply)
                .text
        )

        val wearReplyResolution =
            resolve(
                AppEntryIntentFields(
                    token = VALID_TOKEN,
                    conversationId = CONVERSATION_ID,
                    quickTextReplyOpen = true,
                    tileReplyText = oversizedReply
                )
            )
        assertEquals(
            expected,
            (planAppEntry(wearReplyResolution, conversationExists = { true }) as AppEntryPlan.Reply)
                .text
        )
    }

    @Test
    fun acceptedWearEntryTakesPriorityOverNotificationAction() {
        val resolution =
            resolve(
                AppEntryIntentFields(
                    action = SpotChatNotificationIntents.ACTION_REPLY,
                    token = VALID_TOKEN,
                    conversationId = CONVERSATION_ID,
                    recentChatsOpen = true
                )
            )
        var remoteReplyReads = 0

        assertEquals(
            AppEntryResolution.Accepted(
                AppEntryRequest.Wear(WearEntryRequest.RecentChats(CONVERSATION_ID))
            ),
            resolution
        )
        assertEquals(
            AppEntryPlan.OpenConversation(
                conversationId = CONVERSATION_ID,
                source = AppEntrySource.RecentChatsTile,
                action = AppEntryAction.OpenChat
            ),
            planAppEntry(
                resolution = resolution,
                conversationExists = { true },
                remoteReplyTextProvider = {
                    remoteReplyReads += 1
                    "must not be read"
                }
            )
        )
        assertEquals(0, remoteReplyReads)
    }

    @Test
    fun rejectedWearEntryDoesNotFallBackToValidNotificationAction() {
        val resolution =
            resolve(
                AppEntryIntentFields(
                    action = SpotChatNotificationIntents.ACTION_REPLY,
                    token = VALID_TOKEN,
                    conversationId = CONVERSATION_ID,
                    recentChatsOpen = true,
                    quickVoiceOpen = true
                )
            )
        var remoteReplyReads = 0

        assertEquals(AppEntryResolution.Rejected, resolution)
        assertEquals(
            AppEntryPlan.Ignore,
            planAppEntry(
                resolution = resolution,
                conversationExists = { true },
                remoteReplyTextProvider = {
                    remoteReplyReads += 1
                    "must not be read"
                }
            )
        )
        assertEquals(0, remoteReplyReads)
    }

    @Test
    fun recentChatsWearEntryHandlesMissingKnownAndUnknownConversations() {
        assertEquals(
            AppEntryPlan.Ignore,
            wearPlan(recentChatsOpen = true)
        )
        assertEquals(
            AppEntryPlan.OpenConversation(
                conversationId = CONVERSATION_ID,
                source = AppEntrySource.RecentChatsTile,
                action = AppEntryAction.OpenChat
            ),
            wearPlan(
                recentChatsOpen = true,
                conversationId = CONVERSATION_ID,
                existingConversationIds = setOf(CONVERSATION_ID)
            )
        )
        assertEquals(
            AppEntryPlan.Recover(
                source = AppEntrySource.RecentChatsTile,
                targetConversationId = UNKNOWN_CONVERSATION_ID,
                action = AppEntryAction.OpenChat
            ),
            wearPlan(
                recentChatsOpen = true,
                conversationId = UNKNOWN_CONVERSATION_ID
            )
        )
    }

    @Test
    fun quickVoiceWearEntryHandlesMissingKnownAndUnknownConversations() {
        assertEquals(
            AppEntryPlan.ShowConversationList(AppEntryAction.Voice),
            wearPlan(quickVoiceOpen = true)
        )
        assertEquals(
            AppEntryPlan.OpenConversation(
                conversationId = CONVERSATION_ID,
                source = AppEntrySource.VoiceTile,
                action = AppEntryAction.Voice
            ),
            wearPlan(
                quickVoiceOpen = true,
                conversationId = CONVERSATION_ID,
                existingConversationIds = setOf(CONVERSATION_ID)
            )
        )
        assertEquals(
            AppEntryPlan.Recover(
                source = AppEntrySource.VoiceTile,
                targetConversationId = UNKNOWN_CONVERSATION_ID,
                action = AppEntryAction.Voice
            ),
            wearPlan(
                quickVoiceOpen = true,
                conversationId = UNKNOWN_CONVERSATION_ID
            )
        )
    }

    @Test
    fun quickTextWearEntryHandlesMissingUnknownBlankAndNonBlankReplies() {
        assertEquals(
            AppEntryPlan.ShowConversationList(AppEntryAction.QuickReply),
            wearPlan(quickTextReplyOpen = true, replyText = "ignored without a chat")
        )
        assertEquals(
            AppEntryPlan.Recover(
                source = AppEntrySource.QuickReplyTile,
                targetConversationId = UNKNOWN_CONVERSATION_ID,
                action = AppEntryAction.QuickReply
            ),
            wearPlan(
                quickTextReplyOpen = true,
                conversationId = UNKNOWN_CONVERSATION_ID,
                replyText = "ignored for an unknown chat"
            )
        )
        assertEquals(
            AppEntryPlan.Reply(
                conversationId = CONVERSATION_ID,
                kind = AppEntryReplyKind.WearQuickReply,
                text = ""
            ),
            wearPlan(
                quickTextReplyOpen = true,
                conversationId = CONVERSATION_ID,
                replyText = "  \n ",
                existingConversationIds = setOf(CONVERSATION_ID)
            )
        )
        assertEquals(
            AppEntryPlan.Reply(
                conversationId = CONVERSATION_ID,
                kind = AppEntryReplyKind.WearQuickReply,
                text = "wear reply"
            ),
            wearPlan(
                quickTextReplyOpen = true,
                conversationId = CONVERSATION_ID,
                replyText = "  wear reply  ",
                existingConversationIds = setOf(CONVERSATION_ID)
            )
        )
    }

    @Test
    fun allWearShapesResolveToTheirTypedRequests() {
        val cases =
            listOf(
                AppEntryIntentFields(
                    token = VALID_TOKEN,
                    conversationId = CONVERSATION_ID,
                    recentChatsOpen = true
                ) to WearEntryRequest.RecentChats(CONVERSATION_ID),
                AppEntryIntentFields(
                    token = VALID_TOKEN,
                    conversationId = CONVERSATION_ID,
                    quickVoiceOpen = true
                ) to WearEntryRequest.QuickVoice(CONVERSATION_ID),
                AppEntryIntentFields(
                    token = VALID_TOKEN,
                    conversationId = CONVERSATION_ID,
                    quickTextReplyOpen = true,
                    tileReplyText = "reply"
                ) to WearEntryRequest.QuickTextReply(CONVERSATION_ID, "reply")
            )

        cases.forEach { (fields, request) ->
            assertEquals(
                AppEntryResolution.Accepted(AppEntryRequest.Wear(request)),
                resolve(fields)
            )
        }
    }

    private fun resolve(fields: AppEntryIntentFields): AppEntryResolution =
        resolveAppEntryIntent(fields) { token -> token == VALID_TOKEN }

    private fun wearPlan(
        recentChatsOpen: Boolean = false,
        quickVoiceOpen: Boolean = false,
        quickTextReplyOpen: Boolean = false,
        conversationId: String? = null,
        replyText: String? = null,
        existingConversationIds: Set<String> = emptySet()
    ): AppEntryPlan {
        val resolution =
            resolve(
                AppEntryIntentFields(
                    token = VALID_TOKEN,
                    conversationId = conversationId,
                    recentChatsOpen = recentChatsOpen,
                    quickVoiceOpen = quickVoiceOpen,
                    quickTextReplyOpen = quickTextReplyOpen,
                    tileReplyText = replyText
                )
            )
        assertTrue(resolution is AppEntryResolution.Accepted)
        return planAppEntry(
            resolution = resolution,
            conversationExists = existingConversationIds::contains
        )
    }

    private data class NotificationCase(
        val intentAction: String,
        val entryAction: NotificationEntryAction,
        val resolvedReplyText: String = "",
        val expectedPlan: AppEntryPlan
    )

    private companion object {
        const val VALID_TOKEN = "trusted-token"
        const val CONVERSATION_ID = "chat-known"
        const val UNKNOWN_CONVERSATION_ID = "chat-unknown"

        val notificationActions =
            listOf(
                SpotChatNotificationIntents.ACTION_OPEN_CONVERSATION,
                SpotChatNotificationIntents.ACTION_REPLY,
                SpotChatNotificationIntents.ACTION_QUICK_REPLY,
                SpotChatNotificationIntents.ACTION_MARK_READ,
                SpotChatNotificationIntents.ACTION_MUTE_8H,
                SpotChatNotificationIntents.ACTION_NOTIFICATION_DISMISSED
            )
    }
}
