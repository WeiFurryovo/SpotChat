package com.weifurry.spotchat.presentation

import com.weifurry.spotchat.domain.EncryptedChatStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatStateCapacityPolicyTest {
    @Test
    fun textOutboxAllowsLastSlotRejectsOverflowAndAllowsReplacement() {
        val oneSlotLeft =
            (0 until EncryptedChatStateStore.MAX_TEXT_OUTBOX_MESSAGES - 1)
                .mapTo(linkedSetOf()) { index -> "message-$index" }

        assertNull(
            validateTextOutboxCapacity(
                currentMessageIds = oneSlotLeft,
                messageId = "last-message",
                recipientCount = 1,
                currentPendingConversationIds = emptySet(),
                conversationId = "direct:alice"
            )
        )

        val full = oneSlotLeft + "last-message"
        assertEquals(
            OutboxCapacityIssue.MessageCount,
            validateTextOutboxCapacity(
                currentMessageIds = full,
                messageId = "overflow",
                recipientCount = 1,
                currentPendingConversationIds = emptySet(),
                conversationId = "direct:alice"
            )
        )
        assertNull(
            validateTextOutboxCapacity(
                currentMessageIds = full,
                messageId = "message-0",
                recipientCount = 1,
                currentPendingConversationIds = emptySet(),
                conversationId = "direct:alice"
            )
        )
    }

    @Test
    fun textOutboxEnforcesRecipientAndPendingConversationLimitsWithoutTruncation() {
        assertNull(
            validateTextOutboxCapacity(
                currentMessageIds = emptySet(),
                messageId = "message",
                recipientCount = EncryptedChatStateStore.MAX_RECIPIENTS,
                currentPendingConversationIds = emptySet(),
                conversationId = "group:nearby"
            )
        )
        assertEquals(
            OutboxCapacityIssue.RecipientCount,
            validateTextOutboxCapacity(
                currentMessageIds = emptySet(),
                messageId = "message",
                recipientCount = EncryptedChatStateStore.MAX_RECIPIENTS + 1,
                currentPendingConversationIds = emptySet(),
                conversationId = "group:nearby"
            )
        )

        val pendingConversations =
            (0 until EncryptedChatStateStore.MAX_CONVERSATIONS)
                .mapTo(linkedSetOf()) { index -> "direct:$index" }
        assertNull(
            validateTextOutboxCapacity(
                currentMessageIds = emptySet(),
                messageId = "same-conversation",
                recipientCount = 1,
                currentPendingConversationIds = pendingConversations,
                conversationId = "direct:0"
            )
        )
        assertEquals(
            OutboxCapacityIssue.CriticalConversationCount,
            validateTextOutboxCapacity(
                currentMessageIds = emptySet(),
                messageId = "new-conversation",
                recipientCount = 1,
                currentPendingConversationIds = pendingConversations,
                conversationId = "direct:new"
            )
        )
    }

    @Test
    fun voiceOutboxAllowsLastSlotRejectsOverflowAndAllowsReplacement() {
        val oneSlotLeft =
            (0 until EncryptedChatStateStore.MAX_VOICE_OUTBOX_MESSAGES - 1)
                .associate { index -> "voice-$index" to 1 }

        assertNull(
            validateVoiceOutboxCapacity(
                currentAudioBytesByMessage = oneSlotLeft,
                messageId = "last-voice",
                audioByteCount = 1,
                recipientCount = 1,
                currentPendingConversationIds = emptySet(),
                conversationId = "direct:alice"
            )
        )

        val full = oneSlotLeft + ("last-voice" to 1)
        assertEquals(
            OutboxCapacityIssue.MessageCount,
            validateVoiceOutboxCapacity(
                currentAudioBytesByMessage = full,
                messageId = "overflow",
                audioByteCount = 1,
                recipientCount = 1,
                currentPendingConversationIds = emptySet(),
                conversationId = "direct:alice"
            )
        )
        assertNull(
            validateVoiceOutboxCapacity(
                currentAudioBytesByMessage = full,
                messageId = "voice-0",
                audioByteCount = 1,
                recipientCount = 1,
                currentPendingConversationIds = emptySet(),
                conversationId = "direct:alice"
            )
        )
    }

    @Test
    fun voiceOutboxEnforcesSingleItemAndAggregateByteBudgets() {
        assertEquals(
            OutboxCapacityIssue.VoiceSize,
            validateVoiceOutboxCapacity(
                currentAudioBytesByMessage = emptyMap(),
                messageId = "empty",
                audioByteCount = 0,
                recipientCount = 1,
                currentPendingConversationIds = emptySet(),
                conversationId = "direct:alice"
            )
        )
        assertNull(
            validateVoiceOutboxCapacity(
                currentAudioBytesByMessage = emptyMap(),
                messageId = "maximum",
                audioByteCount = EncryptedChatStateStore.MAX_SINGLE_VOICE_BYTES,
                recipientCount = 1,
                currentPendingConversationIds = emptySet(),
                conversationId = "direct:alice"
            )
        )
        assertEquals(
            OutboxCapacityIssue.VoiceSize,
            validateVoiceOutboxCapacity(
                currentAudioBytesByMessage = emptyMap(),
                messageId = "too-large",
                audioByteCount = EncryptedChatStateStore.MAX_SINGLE_VOICE_BYTES + 1,
                recipientCount = 1,
                currentPendingConversationIds = emptySet(),
                conversationId = "direct:alice"
            )
        )

        val almostFull =
            buildMap {
                repeat(15) { index ->
                    put("full-$index", EncryptedChatStateStore.MAX_SINGLE_VOICE_BYTES)
                }
                put("tail", EncryptedChatStateStore.MAX_SINGLE_VOICE_BYTES - 1)
            }
        assertNull(
            validateVoiceOutboxCapacity(
                currentAudioBytesByMessage = almostFull,
                messageId = "last-byte",
                audioByteCount = 1,
                recipientCount = 1,
                currentPendingConversationIds = emptySet(),
                conversationId = "direct:alice"
            )
        )
        assertEquals(
            OutboxCapacityIssue.VoiceBudget,
            validateVoiceOutboxCapacity(
                currentAudioBytesByMessage = almostFull,
                messageId = "two-bytes",
                audioByteCount = 2,
                recipientCount = 1,
                currentPendingConversationIds = emptySet(),
                conversationId = "direct:alice"
            )
        )

        val exactlyFull = almostFull + ("last-byte" to 1)
        assertNull(
            validateVoiceOutboxCapacity(
                currentAudioBytesByMessage = exactlyFull,
                messageId = "full-0",
                audioByteCount = EncryptedChatStateStore.MAX_SINGLE_VOICE_BYTES,
                recipientCount = 1,
                currentPendingConversationIds = emptySet(),
                conversationId = "direct:alice"
            )
        )
    }

    @Test
    fun receiptTrackingAdmissionAllowsReplacementButRejectsNewOverflow() {
        val full =
            (0 until EncryptedChatStateStore.MAX_RECEIPT_TRACKED_MESSAGES)
                .mapTo(linkedSetOf()) { index -> "tracked-$index" }

        val unchanged = full.toSet()
        assertNull(
            validateReceiptTrackingCapacity(
                currentTrackedMessageIds = full,
                messageId = "tracked-0"
            )
        )
        assertEquals(
            OutboxCapacityIssue.ReceiptTrackingCount,
            validateReceiptTrackingCapacity(
                currentTrackedMessageIds = full,
                messageId = "overflow"
            )
        )
        assertEquals(unchanged, full)
    }

    @Test
    fun envelopeAdmissionChecksOnlyRecipientsAndEnvelopeCount() {
        val oneSlotLeft =
            (0 until EncryptedChatStateStore.MAX_OUTGOING_ENVELOPES - 1)
                .mapTo(linkedSetOf()) { index -> "envelope-$index" }
        assertNull(
            validateOutgoingEnvelopeCapacity(
                currentEnvelopeIds = oneSlotLeft,
                envelopeId = "last-envelope",
                recipientCount = EncryptedChatStateStore.MAX_RECIPIENTS
            )
        )

        val full = oneSlotLeft + "last-envelope"
        val unchanged = full.toSet()
        assertEquals(
            OutboxCapacityIssue.EnvelopeCount,
            validateOutgoingEnvelopeCapacity(
                currentEnvelopeIds = full,
                envelopeId = "overflow",
                recipientCount = 1
            )
        )
        assertEquals(unchanged, full)
        assertNull(
            validateOutgoingEnvelopeCapacity(
                currentEnvelopeIds = full,
                envelopeId = "envelope-10",
                recipientCount = 1
            )
        )
        assertEquals(
            OutboxCapacityIssue.RecipientCount,
            validateOutgoingEnvelopeCapacity(
                currentEnvelopeIds = emptySet(),
                envelopeId = "too-many-recipients",
                recipientCount = EncryptedChatStateStore.MAX_RECIPIENTS + 1
            )
        )
    }
}
