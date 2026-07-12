package com.weifurry.spotchat.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val CHAT_PAYLOAD_KIND_DIRECT = "direct"
internal const val CHAT_PAYLOAD_KIND_GROUP = "group"
internal const val NEARBY_GROUP_CONVERSATION_ID = "group:nearby"
internal const val MAX_CHAT_PAYLOAD_TEXT_CHARS = 280
internal const val MAX_QUOTED_MESSAGE_CHARS = 72

private const val CHAT_PAYLOAD_VERSION = 1
private const val MAX_INCOMING_MESSAGE_ID_CHARS = 128
private const val MAX_INCOMING_SENDER_NAME_CHARS = 96
private const val MAX_INCOMING_GROUP_NAME_CHARS = 64
private const val MAX_INCOMING_FORWARD_COUNT = 1_000

@Serializable
internal data class QuotedMessage(
    val messageId: String,
    val senderName: String,
    val text: String
)

@Serializable
internal data class ChatPayload(
    val version: Int = CHAT_PAYLOAD_VERSION,
    val kind: String = CHAT_PAYLOAD_KIND_DIRECT,
    val text: String,
    val groupId: String? = null,
    val groupName: String? = null,
    val quote: QuotedMessage? = null,
    val forwarded: Boolean = false,
    val forwardCount: Int = 0
)

internal object ChatPayloadCodec {
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

    fun encode(payload: ChatPayload): String = json.encodeToString(payload)

    fun decodeOrLegacy(text: String): ChatPayload =
        runCatching { json.decodeFromString<ChatPayload>(text) }
            .getOrNull()
            ?.takeIf { payload ->
                payload.version == CHAT_PAYLOAD_VERSION && payload.text.isNotBlank()
            }
            ?: ChatPayload(
                kind = CHAT_PAYLOAD_KIND_DIRECT,
                text = text,
                forwarded = false,
                forwardCount = 0
            )

    fun validateIncoming(payload: ChatPayload) {
        require(payload.text.isNotBlank() && payload.text.length <= MAX_CHAT_PAYLOAD_TEXT_CHARS) {
            "Invalid chat message text"
        }
        when (payload.kind) {
            CHAT_PAYLOAD_KIND_DIRECT -> {
                require(payload.groupId == null && payload.groupName == null) {
                    "Invalid direct-message group metadata"
                }
            }

            CHAT_PAYLOAD_KIND_GROUP -> {
                require(
                    payload.groupId == NEARBY_GROUP_CONVERSATION_ID &&
                        (
                            payload.groupName == null ||
                                (
                                    payload.groupName.isNotBlank() &&
                                        payload.groupName.length <= MAX_INCOMING_GROUP_NAME_CHARS
                                )
                        )
                ) {
                    "Invalid group-message metadata"
                }
            }

            else -> throw IllegalArgumentException("Unsupported chat message kind")
        }
        payload.quote?.let { quoted ->
            require(
                quoted.messageId.isNotBlank() &&
                    quoted.messageId.length <= MAX_INCOMING_MESSAGE_ID_CHARS &&
                    quoted.senderName.isNotBlank() &&
                    quoted.senderName.length <= MAX_INCOMING_SENDER_NAME_CHARS &&
                    quoted.text.length <= MAX_QUOTED_MESSAGE_CHARS
            ) {
                "Invalid quoted message"
            }
        }
        require(
            payload.forwardCount in 0..MAX_INCOMING_FORWARD_COUNT &&
                (!payload.forwarded || payload.forwardCount > 0) &&
                (payload.forwarded || payload.forwardCount == 0)
        ) {
            "Invalid forwarded-message metadata"
        }
    }
}
