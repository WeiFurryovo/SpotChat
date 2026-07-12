package com.weifurry.spotchat.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChatPayloadCodecTest {
    @Test
    fun completePayloadRoundTripsWithoutLosingFields() {
        val payload =
            ChatPayload(
                kind = CHAT_PAYLOAD_KIND_GROUP,
                text = "complete message",
                groupId = NEARBY_GROUP_CONVERSATION_ID,
                groupName = "Nearby",
                quote =
                    QuotedMessage(
                        messageId = "quoted-message-id",
                        senderName = "Alice",
                        text = "quoted text"
                    ),
                forwarded = true,
                forwardCount = 3
            )

        val decoded = ChatPayloadCodec.decodeOrLegacy(ChatPayloadCodec.encode(payload))

        assertEquals(payload, decoded)
    }

    @Test
    fun wireFormatDefaultsAndUnknownFieldCompatibilityRemainStable() {
        val minimalPayload = ChatPayload(text = "hello")

        assertEquals(
            """{"version":1,"kind":"direct","text":"hello","groupId":null,"groupName":null,"quote":null,"forwarded":false,"forwardCount":0}""",
            ChatPayloadCodec.encode(minimalPayload)
        )
        assertEquals(
            minimalPayload,
            ChatPayloadCodec.decodeOrLegacy(
                """{"text":"hello","unknownField":"ignored"}"""
            )
        )
    }

    @Test
    fun malformedJsonFallsBackToLegacyDirectMessage() {
        assertLegacyFallback("{not-json")
    }

    @Test
    fun unknownVersionFallsBackToLegacyDirectMessage() {
        val encoded = ChatPayloadCodec.encode(ChatPayload(version = 2, text = "future message"))

        assertLegacyFallback(encoded)
    }

    @Test
    fun blankStructuredTextFallsBackToLegacyDirectMessage() {
        val encoded = ChatPayloadCodec.encode(ChatPayload(text = "   "))

        assertLegacyFallback(encoded)
    }

    @Test
    fun validDirectAndGroupPayloadsPassIncomingValidation() {
        ChatPayloadCodec.validateIncoming(
            ChatPayload(
                kind = CHAT_PAYLOAD_KIND_DIRECT,
                text = "d".repeat(MAX_CHAT_PAYLOAD_TEXT_CHARS)
            )
        )
        ChatPayloadCodec.validateIncoming(
            ChatPayload(
                kind = CHAT_PAYLOAD_KIND_GROUP,
                text = "group message without a name",
                groupId = NEARBY_GROUP_CONVERSATION_ID
            )
        )
        ChatPayloadCodec.validateIncoming(
            ChatPayload(
                kind = CHAT_PAYLOAD_KIND_GROUP,
                text = "group message",
                groupId = NEARBY_GROUP_CONVERSATION_ID,
                groupName = "g".repeat(64),
                quote =
                    QuotedMessage(
                        messageId = "m".repeat(128),
                        senderName = "s".repeat(96),
                        text = "q".repeat(MAX_QUOTED_MESSAGE_CHARS)
                    ),
                forwarded = true,
                forwardCount = 1_000
            )
        )
    }

    @Test
    fun directPayloadRejectsGroupMetadata() {
        assertInvalid(
            ChatPayload(
                kind = CHAT_PAYLOAD_KIND_DIRECT,
                text = "message",
                groupId = NEARBY_GROUP_CONVERSATION_ID,
                groupName = "Nearby"
            )
        )
    }

    @Test
    fun groupPayloadRejectsUnexpectedGroupId() {
        assertInvalid(
            ChatPayload(
                kind = CHAT_PAYLOAD_KIND_GROUP,
                text = "message",
                groupId = "group:unexpected",
                groupName = "Unexpected"
            )
        )
    }

    @Test
    fun payloadRejectsTextLongerThanMaximum() {
        assertInvalid(
            ChatPayload(text = "x".repeat(MAX_CHAT_PAYLOAD_TEXT_CHARS + 1))
        )
    }

    @Test
    fun payloadRejectsQuotedTextLongerThanMaximum() {
        assertInvalid(
            ChatPayload(
                text = "message",
                quote =
                    QuotedMessage(
                        messageId = "quoted-message-id",
                        senderName = "Alice",
                        text = "q".repeat(MAX_QUOTED_MESSAGE_CHARS + 1)
                    )
            )
        )
    }

    @Test
    fun payloadRejectsForwardedFlagAndCountMismatches() {
        assertInvalid(
            ChatPayload(
                text = "message",
                forwarded = true,
                forwardCount = 0
            )
        )
        assertInvalid(
            ChatPayload(
                text = "message",
                forwarded = false,
                forwardCount = 1
            )
        )
    }

    @Test
    fun payloadRejectsForwardCountAboveMaximum() {
        assertInvalid(
            ChatPayload(
                text = "message",
                forwarded = true,
                forwardCount = 1_001
            )
        )
    }

    private fun assertLegacyFallback(encoded: String) {
        assertEquals(
            ChatPayload(
                kind = CHAT_PAYLOAD_KIND_DIRECT,
                text = encoded,
                forwarded = false,
                forwardCount = 0
            ),
            ChatPayloadCodec.decodeOrLegacy(encoded)
        )
    }

    private fun assertInvalid(payload: ChatPayload) {
        assertThrows(IllegalArgumentException::class.java) {
            ChatPayloadCodec.validateIncoming(payload)
        }
    }
}
