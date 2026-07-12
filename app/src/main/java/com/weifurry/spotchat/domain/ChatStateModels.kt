package com.weifurry.spotchat.domain

import kotlinx.serialization.Serializable

/** Current schema written inside the authenticated chat-state payload. */
const val CHAT_STATE_SCHEMA_VERSION = 1

/**
 * Complete restart-safe state for the chat surface.
 *
 * Voice recordings are represented as standard Base64 strings so the model remains JSON-friendly and
 * never exposes platform-specific serializers to the presentation layer.
 */
@Serializable
data class PersistedChatState(
    val ownerFingerprint: String,
    val version: Int = CHAT_STATE_SCHEMA_VERSION,
    val savedAtEpochMillis: Long = 0L,
    val messagesByConversation: Map<String, List<PersistedChatMessage>> = emptyMap(),
    val draftsByConversation: Map<String, PersistedConversationDraft> = emptyMap(),
    val pendingTextOutbox: List<PersistedTextOutboxMessage> = emptyList(),
    val pendingVoiceOutbox: List<PersistedVoiceOutboxMessage> = emptyList(),
    val outgoingEnvelopes: Map<String, PersistedOutgoingEnvelope> = emptyMap(),
    val expectedRecipientsByMessage: Map<String, Set<String>> = emptyMap(),
    val deliveredRecipientsByMessage: Map<String, Set<String>> = emptyMap(),
    val readRecipientsByMessage: Map<String, Set<String>> = emptyMap(),
    val sentReadReceipts: Set<String> = emptySet(),
    val conversationUpdateOrder: Map<String, Long> = emptyMap()
)

@Serializable
enum class PersistedDeliveryState {
    Received,
    Waiting,
    Sending,
    Sent,
    Delivered,
    Read,
    Failed,
    System
}

@Serializable
enum class PersistedChatMessageKind {
    Text,
    Voice
}

@Serializable
data class PersistedQuotedMessage(
    val messageId: String,
    val senderName: String,
    val text: String
)

@Serializable
data class PersistedChatMessage(
    val text: String,
    val mine: Boolean,
    val encrypted: Boolean,
    val timestamp: String,
    val senderName: String? = null,
    val senderFingerprint: String? = null,
    val messageId: String? = null,
    val receiptMessageId: String? = null,
    val deliveryState: PersistedDeliveryState = PersistedDeliveryState.Received,
    val kind: PersistedChatMessageKind = PersistedChatMessageKind.Text,
    val quotedMessage: PersistedQuotedMessage? = null,
    val voiceDurationMs: Long? = null,
    val voiceAudioBase64: String? = null,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long? = null,
    val reactions: Map<String, String> = emptyMap(),
    val forwarded: Boolean = false,
    val forwardCount: Int = 0
)

@Serializable
data class PersistedConversationDraft(
    val text: String,
    val updatedAtEpochMillis: Long
)

@Serializable
data class PersistedTextOutboxMessage(
    val conversationId: String,
    val text: String,
    val displayMessageId: String,
    val remainingTargetFingerprints: List<String>,
    val quotedMessage: PersistedQuotedMessage? = null,
    val forwarded: Boolean = false,
    val forwardCount: Int = 0,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long? = null
)

@Serializable
data class PersistedVoiceOutboxMessage(
    val conversationId: String,
    val displayMessageId: String,
    val remainingTargetFingerprints: List<String>,
    val durationMs: Long,
    val audioBase64: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long? = null
)

@Serializable
data class PersistedOutgoingEnvelope(
    val conversationId: String,
    val displayMessageId: String,
    val recipientFingerprint: String,
    val expectedRecipients: Set<String>,
    val createdAtEpochMillis: Long
)
