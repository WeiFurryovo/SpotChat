package com.weifurry.spotchat.presentation

import com.weifurry.spotchat.domain.EncryptedChatStateStore

internal enum class OutboxCapacityIssue {
    MessageCount,
    RecipientCount,
    VoiceSize,
    VoiceBudget,
    CriticalConversationCount,
    EnvelopeCount,
    ReceiptTrackingCount
}

private fun validateSharedOutboxCapacity(
    recipientCount: Int,
    currentPendingConversationIds: Set<String>,
    conversationId: String
): OutboxCapacityIssue? =
    when {
        recipientCount !in 1..EncryptedChatStateStore.MAX_RECIPIENTS ->
            OutboxCapacityIssue.RecipientCount
        conversationId !in currentPendingConversationIds &&
            currentPendingConversationIds.size >= EncryptedChatStateStore.MAX_CONVERSATIONS ->
            OutboxCapacityIssue.CriticalConversationCount
        else -> null
    }

internal fun validateTextOutboxCapacity(
    currentMessageIds: Set<String>,
    messageId: String,
    recipientCount: Int,
    currentPendingConversationIds: Set<String>,
    conversationId: String
): OutboxCapacityIssue? =
    validateSharedOutboxCapacity(
        recipientCount = recipientCount,
        currentPendingConversationIds = currentPendingConversationIds,
        conversationId = conversationId
    ) ?: when {
        messageId !in currentMessageIds &&
            currentMessageIds.size >= EncryptedChatStateStore.MAX_TEXT_OUTBOX_MESSAGES ->
            OutboxCapacityIssue.MessageCount
        else -> null
    }

internal fun validateVoiceOutboxCapacity(
    currentAudioBytesByMessage: Map<String, Int>,
    messageId: String,
    audioByteCount: Int,
    recipientCount: Int,
    currentPendingConversationIds: Set<String>,
    conversationId: String
): OutboxCapacityIssue? {
    validateSharedOutboxCapacity(
        recipientCount = recipientCount,
        currentPendingConversationIds = currentPendingConversationIds,
        conversationId = conversationId
    )?.let { return it }
    val existingByteCount = currentAudioBytesByMessage[messageId] ?: 0
    val resultingTotalBytes =
        currentAudioBytesByMessage.values.sumOf(Int::toLong) -
            existingByteCount.toLong() +
            audioByteCount.toLong()
    return when {
        audioByteCount !in 1..EncryptedChatStateStore.MAX_SINGLE_VOICE_BYTES ->
            OutboxCapacityIssue.VoiceSize
        messageId !in currentAudioBytesByMessage &&
            currentAudioBytesByMessage.size >= EncryptedChatStateStore.MAX_VOICE_OUTBOX_MESSAGES ->
            OutboxCapacityIssue.MessageCount
        resultingTotalBytes > EncryptedChatStateStore.MAX_TOTAL_VOICE_BYTES.toLong() ->
            OutboxCapacityIssue.VoiceBudget
        else -> null
    }
}

internal fun validateOutgoingEnvelopeCapacity(
    currentEnvelopeIds: Set<String>,
    envelopeId: String,
    recipientCount: Int
): OutboxCapacityIssue? =
    when {
        recipientCount !in 1..EncryptedChatStateStore.MAX_RECIPIENTS ->
            OutboxCapacityIssue.RecipientCount
        envelopeId !in currentEnvelopeIds &&
            currentEnvelopeIds.size >= EncryptedChatStateStore.MAX_OUTGOING_ENVELOPES ->
            OutboxCapacityIssue.EnvelopeCount
        else -> null
    }

internal fun validateReceiptTrackingCapacity(
    currentTrackedMessageIds: Set<String>,
    messageId: String
): OutboxCapacityIssue? =
    if (
        messageId !in currentTrackedMessageIds &&
            currentTrackedMessageIds.size >= EncryptedChatStateStore.MAX_RECEIPT_TRACKED_MESSAGES
    ) {
        OutboxCapacityIssue.ReceiptTrackingCount
    } else {
        null
    }
