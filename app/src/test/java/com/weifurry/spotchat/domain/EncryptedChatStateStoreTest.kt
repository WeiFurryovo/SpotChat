package com.weifurry.spotchat.domain

import java.io.DataOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlinx.serialization.json.Json

class EncryptedChatStateStoreTest {
    @Test
    fun roundTripRestoresCompleteStateFromANewStoreInstance() = withTemporaryFile { file ->
        val cipher = JvmAesGcmChatStateCipher(KEY_A)
        val stored = store(file, cipher).save(completeState())

        val result = store(file, cipher).load()

        assertTrue(result is ChatStateLoadResult.Loaded)
        assertEquals(stored, (result as ChatStateLoadResult.Loaded).state)
        assertEquals(SAVE_TIME, result.state.savedAtEpochMillis)
        assertEquals(PersistedDeliveryState.Read, result.state.messagesByConversation.getValue(CONVERSATION).first().deliveryState)
        assertEquals(
            voiceBase64(byteArrayOf(5, 4, 3, 2, 1)),
            result.state.messagesByConversation.getValue(CONVERSATION)[1].voiceAudioBase64
        )
        assertEquals(setOf(RECIPIENT_A, RECIPIENT_B), result.state.expectedRecipientsByMessage.getValue(MESSAGE_ID))
        assertEquals(42L, result.state.conversationUpdateOrder.getValue(CONVERSATION))
    }

    @Test
    fun missingFileReturnsMissing() = withTemporaryFile { file ->
        assertEquals(ChatStateLoadResult.Missing, store(file).load())
        assertFalse(file.exists())
    }

    @Test
    fun repeatedSavesUseRandomCiphertextAndNeverPersistPlaintext() = withTemporaryFile { file ->
        val state = completeState(text = PLAINTEXT_SENTINEL)
        val store = store(file)

        store.save(state)
        val firstCiphertext = file.readBytes()
        store.save(state)
        val secondCiphertext = file.readBytes()

        assertFalse(firstCiphertext.contentEquals(secondCiphertext))
        assertFalse(firstCiphertext.containsSubsequence(PLAINTEXT_SENTINEL.toByteArray(StandardCharsets.UTF_8)))
        assertFalse(secondCiphertext.containsSubsequence(PLAINTEXT_SENTINEL.toByteArray(StandardCharsets.UTF_8)))
    }

    @Test
    fun tamperingFailsClosedAndPreservesTheUnreadableFile() = withTemporaryFile { file ->
        store(file).save(completeState())
        val tampered = file.readBytes().also { bytes -> bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte() }
        file.writeBytes(tampered)
        val damagedStore = store(file)

        assertTrue(damagedStore.load() is ChatStateLoadResult.Unreadable)
        assertThrowsLocked { damagedStore.save(completeState(text = "must-not-overwrite")) }

        assertArrayEquals(tampered, file.readBytes())
    }

    @Test
    fun sameReadableInstanceRefusesToOverwriteFileTamperedAfterSuccessfulSave() = withTemporaryFile { file ->
        val readableStore = store(file)
        readableStore.save(completeState())
        val tampered =
            file.readBytes().also { bytes ->
                bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
            }
        file.writeBytes(tampered)

        assertThrowsLocked {
            readableStore.save(completeState(text = "same-instance-must-not-overwrite"))
        }

        assertArrayEquals(tampered, file.readBytes())
        assertTrue(readableStore.load() is ChatStateLoadResult.Unreadable)
    }

    @Test
    fun wrongKeyCannotReadOrOverwriteExistingStateEvenWithoutExplicitLoad() = withTemporaryFile { file ->
        store(file, JvmAesGcmChatStateCipher(KEY_A)).save(completeState())
        val original = file.readBytes()
        val wrongKeyStore = store(file, JvmAesGcmChatStateCipher(KEY_B))

        assertThrowsLocked { wrongKeyStore.save(completeState(text = "replacement")) }
        assertTrue(wrongKeyStore.load() is ChatStateLoadResult.Unreadable)
        assertArrayEquals(original, file.readBytes())
    }

    @Test
    fun ownerFingerprintIsAuthenticatedAndCannotOverwriteAnotherOwnersState() =
        withTemporaryFile { file ->
            store(file).save(completeState(text = "owner-bound"))
            val original = file.readBytes()
            val otherOwnerStore =
                EncryptedChatStateStore(
                    storageFile = file,
                    ownerFingerprint = "different-owner-fingerprint",
                    cipher = JvmAesGcmChatStateCipher(KEY_A),
                    clock = { SAVE_TIME }
                )

            assertTrue(otherOwnerStore.load() is ChatStateLoadResult.Unreadable)
            assertThrowsLocked {
                otherOwnerStore.save(
                    PersistedChatState(ownerFingerprint = "different-owner-fingerprint")
                )
            }
            assertArrayEquals(original, file.readBytes())
        }

    @Test
    fun deletingUnreadableTargetExplicitlyUnlocksTheSameStore() = withTemporaryFile { file ->
        store(file, JvmAesGcmChatStateCipher(KEY_A)).save(completeState())
        val wrongKeyStore = store(file, JvmAesGcmChatStateCipher(KEY_B))
        assertTrue(wrongKeyStore.load() is ChatStateLoadResult.Unreadable)

        assertTrue(file.delete())
        wrongKeyStore.save(completeState(text = "fresh-after-explicit-delete"))

        val result = wrongKeyStore.load()
        assertTrue(result is ChatStateLoadResult.Loaded)
        assertEquals(
            "fresh-after-explicit-delete",
            (result as ChatStateLoadResult.Loaded).state.messagesByConversation.getValue(CONVERSATION).first().text
        )
    }

    @Test
    fun retentionDropsExpiredAndSystemMessagesAndEnforcesHistoryAndBookkeepingCaps() = withTemporaryFile { file ->
        val recipients = (0 until 40).map { "recipient-$it" }
        val messages = linkedMapOf<String, List<PersistedChatMessage>>()
        val drafts = linkedMapOf<String, PersistedConversationDraft>()
        val updates = linkedMapOf<String, Long>()
        repeat(70) { conversationIndex ->
            val conversationId = "conversation-$conversationIndex"
            messages[conversationId] =
                buildList {
                    repeat(110) { messageIndex ->
                        add(textMessage("message-$conversationIndex-$messageIndex", conversationIndex * 1_000L + messageIndex))
                    }
                    add(textMessage("system-$conversationIndex", 20_000L, PersistedDeliveryState.System))
                    add(textMessage("expired-$conversationIndex", 20_001L, expiresAt = NOW - 1L))
                }
            drafts[conversationId] = PersistedConversationDraft("draft-$conversationIndex", conversationIndex.toLong())
            updates[conversationId] = conversationIndex.toLong()
        }
        messages[PRIORITY_CONVERSATION] = listOf(textMessage(PRIORITY_MESSAGE_ID, 1L))
        drafts[PRIORITY_CONVERSATION] = PersistedConversationDraft("priority draft", 0L)
        updates[PRIORITY_CONVERSATION] = 0L

        val textOutbox =
            listOf(
                PersistedTextOutboxMessage(
                    conversationId = PRIORITY_CONVERSATION,
                    text = "queued-priority",
                    displayMessageId = PRIORITY_MESSAGE_ID,
                    remainingTargetFingerprints = recipients,
                    createdAtEpochMillis = 139L
                )
            )
        val envelopes =
            mapOf(
                "envelope-priority" to
                    PersistedOutgoingEnvelope(
                        conversationId = PRIORITY_CONVERSATION,
                        displayMessageId = PRIORITY_MESSAGE_ID,
                        recipientFingerprint = recipients.first(),
                        expectedRecipients = recipients.toSet(),
                        createdAtEpochMillis = 299L
                    )
            )
        val receiptMap =
            (0 until 300).associate { index -> "receipt-message-$index" to recipients.toSet() }
        val saved =
            store(file).save(
                PersistedChatState(
                    ownerFingerprint = OWNER,
                    messagesByConversation = messages,
                    draftsByConversation = drafts,
                    pendingTextOutbox = textOutbox,
                    outgoingEnvelopes = envelopes,
                    expectedRecipientsByMessage = receiptMap,
                    deliveredRecipientsByMessage = receiptMap,
                    readRecipientsByMessage = receiptMap,
                    sentReadReceipts = (0 until 600).mapTo(linkedSetOf()) { "sent-$it" },
                    conversationUpdateOrder = updates
                )
            )

        val allMessages = saved.messagesByConversation.values.flatten()
        assertTrue(saved.messagesByConversation.size <= EncryptedChatStateStore.MAX_CONVERSATIONS)
        assertTrue(saved.messagesByConversation.values.all { it.size <= EncryptedChatStateStore.MAX_MESSAGES_PER_CONVERSATION })
        assertTrue(allMessages.size <= EncryptedChatStateStore.MAX_MESSAGES_TOTAL)
        assertTrue(allMessages.none { it.deliveryState == PersistedDeliveryState.System })
        assertTrue(allMessages.none { it.expiresAtEpochMillis?.let { expiry -> expiry <= NOW } == true })
        assertTrue(PRIORITY_CONVERSATION in saved.messagesByConversation)
        assertTrue(saved.draftsByConversation.size <= EncryptedChatStateStore.MAX_DRAFTS)
        assertEquals(textOutbox.toSet(), saved.pendingTextOutbox.toSet())
        assertEquals(recipients.size, saved.pendingTextOutbox.single().remainingTargetFingerprints.size)
        assertEquals(envelopes, saved.outgoingEnvelopes)
        assertEquals(EncryptedChatStateStore.MAX_RECEIPT_TRACKED_MESSAGES, saved.expectedRecipientsByMessage.size)
        assertEquals(EncryptedChatStateStore.MAX_SENT_READ_RECEIPTS, saved.sentReadReceipts.size)
        assertEquals("sent-599", saved.sentReadReceipts.last())
    }

    @Test
    fun savingPrunesRestartableStateForExpiredMessages() = withTemporaryFile { file ->
        val persistentStore = store(file)

        val saved = persistentStore.save(stateWithExpiredOutboundTracking())

        assertExpiredOutboundTrackingPruned(saved)
        val loaded = store(file).load()
        assertTrue(loaded is ChatStateLoadResult.Loaded)
        assertExpiredOutboundTrackingPruned((loaded as ChatStateLoadResult.Loaded).state)
    }

    @Test
    fun loadingExistingSnapshotPrunesRestartableStateForExpiredMessages() =
        withTemporaryFile { file ->
            writeRawEncryptedState(file, stateWithExpiredOutboundTracking())

            val loaded = store(file).load()

            assertTrue(loaded is ChatStateLoadResult.Loaded)
            assertExpiredOutboundTrackingPruned((loaded as ChatStateLoadResult.Loaded).state)
        }

    @Test
    fun expiredTextOutboxIsPrunedAfterItsHistoryWasClippedBeforeRestart() =
        withTemporaryFile { file ->
            var nowEpochMillis = NOW
            val expiresAtEpochMillis = NOW + 10L
            val pendingCount = EncryptedChatStateStore.MAX_MESSAGES_PER_CONVERSATION + 1
            val pending =
                (0 until pendingCount).map { index ->
                    val messageId = "clipped-text-$index"
                    validTextOutbox(messageId)
                        .copy(
                            createdAtEpochMillis = index.toLong(),
                            expiresAtEpochMillis =
                                expiresAtEpochMillis.takeIf { index == 0 }
                        )
                }
            val messages =
                (0 until pendingCount).map { index ->
                    textMessage(
                        messageId = "clipped-text-$index",
                        createdAt = index.toLong(),
                        state = PersistedDeliveryState.Waiting,
                        expiresAt = expiresAtEpochMillis.takeIf { index == 0 }
                    )
                }
            val targetMessageId = pending.first().displayMessageId
            val tracking = mapOf(targetMessageId to setOf(RECIPIENT_A))
            val firstStore = store(file, clock = { nowEpochMillis })

            val saved =
                firstStore.save(
                    PersistedChatState(
                        ownerFingerprint = OWNER,
                        messagesByConversation = mapOf(CONVERSATION to messages),
                        pendingTextOutbox = pending,
                        outgoingEnvelopes =
                            mapOf("clipped-text-envelope" to validEnvelope(targetMessageId)),
                        expectedRecipientsByMessage = tracking,
                        deliveredRecipientsByMessage = tracking,
                        readRecipientsByMessage = tracking,
                        conversationUpdateOrder = mapOf(CONVERSATION to 1L)
                    )
                )

            assertEquals(pendingCount, saved.pendingTextOutbox.size)
            assertEquals(
                expiresAtEpochMillis,
                saved.pendingTextOutbox.single { it.displayMessageId == targetMessageId }
                    .expiresAtEpochMillis
            )
            assertFalse(
                saved.messagesByConversation.values.flatten().any {
                    it.messageId == targetMessageId
                }
            )

            nowEpochMillis = expiresAtEpochMillis
            val loaded = store(file, clock = { nowEpochMillis }).load()

            assertTrue(loaded is ChatStateLoadResult.Loaded)
            val restarted = (loaded as ChatStateLoadResult.Loaded).state
            assertEquals(pendingCount - 1, restarted.pendingTextOutbox.size)
            assertFalse(
                restarted.pendingTextOutbox.any { it.displayMessageId == targetMessageId }
            )
            assertFalse(
                restarted.outgoingEnvelopes.values.any {
                    it.displayMessageId == targetMessageId
                }
            )
            assertFalse(targetMessageId in restarted.expectedRecipientsByMessage)
            assertFalse(targetMessageId in restarted.deliveredRecipientsByMessage)
            assertFalse(targetMessageId in restarted.readRecipientsByMessage)
        }

    @Test
    fun expiredVoiceOutboxIsPrunedAfterVoiceBudgetClippedItsHistoryBeforeRestart() =
        withTemporaryFile { file ->
            var nowEpochMillis = NOW
            val expiresAtEpochMillis = NOW + 10L
            val fullAudio =
                ByteArray(EncryptedChatStateStore.MAX_SINGLE_VOICE_BYTES) { 0x5a }
            val encodedAudio = voiceBase64(fullAudio)
            val pendingCount =
                EncryptedChatStateStore.MAX_TOTAL_VOICE_BYTES /
                    EncryptedChatStateStore.MAX_SINGLE_VOICE_BYTES
            val targetMessageId = "clipped-voice-0"
            val pending =
                (0 until pendingCount).map { index ->
                    validVoiceOutbox("clipped-voice-$index", encodedAudio)
                        .copy(
                            createdAtEpochMillis = index.toLong(),
                            expiresAtEpochMillis =
                                expiresAtEpochMillis.takeIf { index == 0 }
                        )
                }
            val targetHistory =
                PersistedChatMessage(
                    text = "queued voice",
                    mine = true,
                    encrypted = true,
                    timestamp = "10:00",
                    senderFingerprint = OWNER,
                    messageId = targetMessageId,
                    deliveryState = PersistedDeliveryState.Waiting,
                    kind = PersistedChatMessageKind.Voice,
                    voiceDurationMs = 1_000L,
                    voiceAudioBase64 = encodedAudio,
                    createdAtEpochMillis = 0L,
                    expiresAtEpochMillis = expiresAtEpochMillis
                )
            val tracking = mapOf(targetMessageId to setOf(RECIPIENT_A))
            val firstStore = store(file, clock = { nowEpochMillis })

            val saved =
                firstStore.save(
                    PersistedChatState(
                        ownerFingerprint = OWNER,
                        messagesByConversation = mapOf(CONVERSATION to listOf(targetHistory)),
                        pendingVoiceOutbox = pending,
                        outgoingEnvelopes =
                            mapOf("clipped-voice-envelope" to validEnvelope(targetMessageId)),
                        expectedRecipientsByMessage = tracking,
                        deliveredRecipientsByMessage = tracking,
                        readRecipientsByMessage = tracking,
                        conversationUpdateOrder = mapOf(CONVERSATION to 1L)
                    )
                )

            assertEquals(pendingCount, saved.pendingVoiceOutbox.size)
            assertEquals(
                expiresAtEpochMillis,
                saved.pendingVoiceOutbox.single { it.displayMessageId == targetMessageId }
                    .expiresAtEpochMillis
            )
            assertFalse(
                saved.messagesByConversation.values.flatten().any {
                    it.messageId == targetMessageId
                }
            )

            nowEpochMillis = expiresAtEpochMillis
            val loaded = store(file, clock = { nowEpochMillis }).load()

            assertTrue(loaded is ChatStateLoadResult.Loaded)
            val restarted = (loaded as ChatStateLoadResult.Loaded).state
            assertEquals(pendingCount - 1, restarted.pendingVoiceOutbox.size)
            assertFalse(
                restarted.pendingVoiceOutbox.any { it.displayMessageId == targetMessageId }
            )
            assertFalse(
                restarted.outgoingEnvelopes.values.any {
                    it.displayMessageId == targetMessageId
                }
            )
            assertFalse(targetMessageId in restarted.expectedRecipientsByMessage)
            assertFalse(targetMessageId in restarted.deliveredRecipientsByMessage)
            assertFalse(targetMessageId in restarted.readRecipientsByMessage)
        }

    @Test
    fun outgoingEnvelopeConversationDoesNotConsumePendingConversationCapacity() = withTemporaryFile { file ->
        val pending =
            (0 until EncryptedChatStateStore.MAX_CONVERSATIONS).map { index ->
                validTextOutbox(
                    displayMessageId = "pending-message-$index",
                    conversationId = "pending-conversation-$index"
                )
            }
        val envelope =
            validEnvelope("envelope-display-message").copy(
                conversationId = "envelope-only-conversation"
            )
        val state =
            PersistedChatState(
                ownerFingerprint = OWNER,
                pendingTextOutbox = pending,
                outgoingEnvelopes = mapOf("envelope-only" to envelope)
            )

        val saved = store(file).save(state)

        assertEquals(
            pending.associateBy(PersistedTextOutboxMessage::displayMessageId),
            saved.pendingTextOutbox.associateBy(PersistedTextOutboxMessage::displayMessageId)
        )
        assertEquals(mapOf("envelope-only" to envelope), saved.outgoingEnvelopes)
    }

    @Test
    fun activeOutgoingEnvelopeSurvivesHistoryConversationRetentionCap() = withTemporaryFile { file ->
        val envelopeConversation = "conversation-with-active-envelope"
        val messages =
            buildMap {
                put(
                    envelopeConversation,
                    listOf(textMessage("old-envelope-message", createdAt = 1L))
                )
                repeat(EncryptedChatStateStore.MAX_CONVERSATIONS) { index ->
                    put(
                        "recent-conversation-$index",
                        listOf(
                            textMessage(
                                messageId = "recent-message-$index",
                                createdAt = 100L + index
                            )
                        )
                    )
                }
            }
        val activeEnvelope =
            validEnvelope("active-display-message").copy(
                conversationId = envelopeConversation,
                createdAtEpochMillis = 2L
            )
        val state =
            PersistedChatState(
                ownerFingerprint = OWNER,
                messagesByConversation = messages,
                outgoingEnvelopes = mapOf("active-envelope" to activeEnvelope)
            )

        val saved = store(file).save(state)

        assertEquals(
            EncryptedChatStateStore.MAX_CONVERSATIONS,
            saved.messagesByConversation.size
        )
        assertFalse(saved.messagesByConversation.containsKey(envelopeConversation))
        assertEquals(mapOf("active-envelope" to activeEnvelope), saved.outgoingEnvelopes)
        val loaded = store(file).load()
        assertTrue(loaded is ChatStateLoadResult.Loaded)
        assertEquals(
            mapOf("active-envelope" to activeEnvelope),
            (loaded as ChatStateLoadResult.Loaded).state.outgoingEnvelopes
        )
    }

    @Test
    fun activeReceiptMappingsSurviveHistoryReceiptCapacity() = withTemporaryFile { file ->
        val historyMessages =
            (0 until EncryptedChatStateStore.MAX_RECEIPT_TRACKED_MESSAGES).map { index ->
                textMessage(
                    messageId = "history-${index.toString().padStart(3, '0')}",
                    createdAt = index.toLong() + 1L
                )
            }
        val messagesByConversation =
            historyMessages
                .chunked(EncryptedChatStateStore.MAX_MESSAGES_PER_CONVERSATION)
                .mapIndexed { index, messages -> "history-conversation-$index" to messages }
                .toMap(linkedMapOf())
        val activeMessageId = "zz-active-message"
        val allReceiptEntries =
            buildMap {
                historyMessages.forEach { message ->
                    put(requireNotNull(message.messageId), setOf(RECIPIENT_A))
                }
                put(activeMessageId, setOf(RECIPIENT_A))
            }
        val state =
            PersistedChatState(
                ownerFingerprint = OWNER,
                messagesByConversation = messagesByConversation,
                pendingTextOutbox =
                    listOf(
                        validTextOutbox(
                            displayMessageId = activeMessageId,
                            conversationId = "active-conversation"
                        )
                    ),
                expectedRecipientsByMessage = allReceiptEntries,
                deliveredRecipientsByMessage = allReceiptEntries,
                readRecipientsByMessage = allReceiptEntries
            )

        val saved = store(file).save(state)

        assertEquals(
            EncryptedChatStateStore.MAX_RECEIPT_TRACKED_MESSAGES,
            saved.expectedRecipientsByMessage.size
        )
        assertEquals(setOf(RECIPIENT_A), saved.expectedRecipientsByMessage[activeMessageId])
        assertEquals(setOf(RECIPIENT_A), saved.deliveredRecipientsByMessage[activeMessageId])
        assertEquals(setOf(RECIPIENT_A), saved.readRecipientsByMessage[activeMessageId])
    }

    @Test
    fun tooManyCriticalReceiptMessageIdsAreRejectedEvenWithoutReceiptMaps() =
        withTemporaryFile { file ->
            val textOutbox =
                (0 until EncryptedChatStateStore.MAX_TEXT_OUTBOX_MESSAGES).map { index ->
                    validTextOutbox("text-critical-$index")
                }
            val requiredEnvelopeCount =
                EncryptedChatStateStore.MAX_RECEIPT_TRACKED_MESSAGES - textOutbox.size + 1
            val envelopes =
                (0 until requiredEnvelopeCount).associate { index ->
                    val envelopeId = "envelope-critical-$index"
                    envelopeId to validEnvelope("envelope-display-critical-$index")
                }
            val state =
                PersistedChatState(
                    ownerFingerprint = OWNER,
                    pendingTextOutbox = textOutbox,
                    outgoingEnvelopes = envelopes
                )

            assertRejectedPendingData("critical receipt tracking count") {
                store(file).save(state)
            }
            assertFalse(file.exists())
        }

    @Test
    fun loadFailsClosedInsteadOfSilentlyDroppingPersistedPendingData() = withTemporaryFile { file ->
        val overCapacityState =
            PersistedChatState(
                ownerFingerprint = OWNER,
                pendingTextOutbox =
                    (0..EncryptedChatStateStore.MAX_TEXT_OUTBOX_MESSAGES).map { index ->
                        validTextOutbox("persisted-over-cap-$index")
                    }
            )
        writeRawEncryptedState(file, overCapacityState)
        val original = file.readBytes()

        assertTrue(store(file).load() is ChatStateLoadResult.Unreadable)
        assertArrayEquals(original, file.readBytes())
    }

    @Test
    fun pendingDataLossIsRejectedBeforeWriteAndPreservesExistingTarget() = withTemporaryFile { file ->
        val persistentStore = store(file)
        persistentStore.save(completeState(text = "original-state"))
        val original = file.readBytes()
        val tinyAudio = voiceBase64(byteArrayOf(0x01))
        val fullAudio =
            voiceBase64(ByteArray(EncryptedChatStateStore.MAX_SINGLE_VOICE_BYTES) { 0x5a })
        val tooManyRecipients =
            (0..EncryptedChatStateStore.MAX_RECIPIENTS).map { "recipient-$it" }

        val cases =
            listOf(
                "text outbox count" to
                    PersistedChatState(
                        ownerFingerprint = OWNER,
                        pendingTextOutbox =
                            (0..EncryptedChatStateStore.MAX_TEXT_OUTBOX_MESSAGES).map { index ->
                                validTextOutbox("text-$index")
                            }
                    ),
                "voice outbox count" to
                    PersistedChatState(
                        ownerFingerprint = OWNER,
                        pendingVoiceOutbox =
                            (0..EncryptedChatStateStore.MAX_VOICE_OUTBOX_MESSAGES).map { index ->
                                validVoiceOutbox("voice-count-$index", tinyAudio)
                            }
                    ),
                "outgoing envelope count" to
                    PersistedChatState(
                        ownerFingerprint = OWNER,
                        outgoingEnvelopes =
                            (0..EncryptedChatStateStore.MAX_OUTGOING_ENVELOPES).associate { index ->
                                "envelope-$index" to validEnvelope("display-$index")
                            },
                        conversationUpdateOrder = mapOf(CONVERSATION to 1L)
                    ),
                "text recipient cap" to
                    PersistedChatState(
                        ownerFingerprint = OWNER,
                        pendingTextOutbox =
                            listOf(validTextOutbox("too-many-text-recipients", tooManyRecipients))
                    ),
                "voice recipient cap" to
                    PersistedChatState(
                        ownerFingerprint = OWNER,
                        pendingVoiceOutbox =
                            listOf(
                                validVoiceOutbox("too-many-voice-recipients", tinyAudio)
                                    .copy(remainingTargetFingerprints = tooManyRecipients)
                            )
                    ),
                "envelope recipient cap" to
                    PersistedChatState(
                        ownerFingerprint = OWNER,
                        outgoingEnvelopes =
                            mapOf(
                                "too-many-envelope-recipients" to
                                    validEnvelope("too-many-envelope-recipients")
                                        .copy(
                                            recipientFingerprint = tooManyRecipients.first(),
                                            expectedRecipients = tooManyRecipients.toSet()
                                        )
                            ),
                        conversationUpdateOrder = mapOf(CONVERSATION to 1L)
                    ),
                "voice budget" to
                    PersistedChatState(
                        ownerFingerprint = OWNER,
                        pendingVoiceOutbox =
                            (0..16).map { index ->
                                validVoiceOutbox("voice-budget-$index", fullAudio)
                            }
                    ),
                "outbox conversation cap" to
                    PersistedChatState(
                        ownerFingerprint = OWNER,
                        pendingTextOutbox =
                            (0..EncryptedChatStateStore.MAX_CONVERSATIONS).map { index ->
                                validTextOutbox(
                                    displayMessageId = "conversation-message-$index",
                                    conversationId = "outbox-conversation-$index"
                                )
                            }
                    ),
                "truncated text outbox field" to
                    PersistedChatState(
                        ownerFingerprint = OWNER,
                        pendingTextOutbox =
                            listOf(validTextOutbox("long-text").copy(text = "x".repeat(1_025)))
                    ),
                "invalid text outbox" to
                    PersistedChatState(
                        ownerFingerprint = OWNER,
                        pendingTextOutbox = listOf(validTextOutbox("blank-text").copy(text = " "))
                    ),
                "invalid voice outbox" to
                    PersistedChatState(
                        ownerFingerprint = OWNER,
                        pendingVoiceOutbox =
                            listOf(validVoiceOutbox("zero-duration", tinyAudio).copy(durationMs = 0L))
                    ),
                "invalid outgoing envelope" to
                    PersistedChatState(
                        ownerFingerprint = OWNER,
                        outgoingEnvelopes =
                            mapOf("invalid-envelope" to validEnvelope("invalid-display").copy(expectedRecipients = emptySet())),
                        conversationUpdateOrder = mapOf(CONVERSATION to 1L)
                    )
            )

        cases.forEach { (label, candidate) ->
            assertRejectedPendingData(label) { persistentStore.save(candidate) }
            assertArrayEquals("Target changed after rejecting $label", original, file.readBytes())
        }
        assertFalse(File(file.parentFile, file.name + EncryptedChatStateStore.TEMP_FILE_SUFFIX).exists())
        assertTrue(persistentStore.load() is ChatStateLoadResult.Loaded)
    }

    @Test
    fun voiceOutboxConsumesTheSharedVoiceBudgetBeforeHistory() = withTemporaryFile { file ->
        val audio = ByteArray(EncryptedChatStateStore.MAX_SINGLE_VOICE_BYTES) { 0x5a }
        val encoded = voiceBase64(audio)
        val voiceOutbox =
            (0 until 16).map { index ->
                PersistedVoiceOutboxMessage(
                    conversationId = CONVERSATION,
                    displayMessageId = "voice-outbox-$index",
                    remainingTargetFingerprints = listOf(RECIPIENT_A),
                    durationMs = 1_000L,
                    audioBase64 = encoded,
                    createdAtEpochMillis = index.toLong()
                )
            }
        val historyVoice =
            PersistedChatMessage(
                text = "history voice",
                mine = false,
                encrypted = true,
                timestamp = "10:00",
                senderFingerprint = RECIPIENT_A,
                messageId = "history-voice",
                kind = PersistedChatMessageKind.Voice,
                voiceDurationMs = 1_000L,
                voiceAudioBase64 = encoded,
                createdAtEpochMillis = 100L
            )

        val saved =
            store(file).save(
                PersistedChatState(
                    ownerFingerprint = OWNER,
                    messagesByConversation = mapOf(CONVERSATION to listOf(historyVoice)),
                    pendingVoiceOutbox = voiceOutbox,
                    conversationUpdateOrder = mapOf(CONVERSATION to 1L)
                )
            )

        val retainedVoiceBytes =
            saved.pendingVoiceOutbox.sumOf { Base64.getDecoder().decode(it.audioBase64).size } +
                saved.messagesByConversation.values.flatten().sumOf {
                    it.voiceAudioBase64?.let(Base64.getDecoder()::decode)?.size ?: 0
                }
        assertEquals(EncryptedChatStateStore.MAX_TOTAL_VOICE_BYTES, retainedVoiceBytes)
        assertEquals(16, saved.pendingVoiceOutbox.size)
        assertFalse(saved.messagesByConversation.values.flatten().any { it.messageId == "history-voice" })
    }

    @Test
    fun staleAndFailedWriteTemporaryFilesAreCleaned() = withTemporaryFile { file ->
        val temporaryFile = File(file.parentFile, file.name + EncryptedChatStateStore.TEMP_FILE_SUFFIX)
        temporaryFile.writeText("stale")
        val store = store(file)

        assertEquals(ChatStateLoadResult.Missing, store.load())
        assertFalse(temporaryFile.exists())

        temporaryFile.writeText("stale-again")
        val failingStore =
            EncryptedChatStateStore(
                storageFile = file,
                ownerFingerprint = OWNER,
                cipher =
                    object : ChatStateCipher {
                        override fun encrypt(
                            plaintext: ByteArray,
                            associatedData: ByteArray
                        ): ChatStateCipherPayload = throw java.io.IOException("injected encryption failure")

                        override fun decrypt(
                            payload: ChatStateCipherPayload,
                            associatedData: ByteArray
                        ): ByteArray = throw AssertionError("decrypt should not be called")
                    },
                clock = { SAVE_TIME }
            )
        try {
            failingStore.save(completeState())
            fail("Expected encryption failure")
        } catch (_: java.io.IOException) {
            // Expected.
        }
        assertFalse(temporaryFile.exists())

        store.save(completeState())
        assertFalse(temporaryFile.exists())
    }

    private fun stateWithExpiredOutboundTracking(): PersistedChatState {
        val trackedMessageIds =
            setOf(
                EXPIRED_TEXT_MESSAGE_ID,
                EXPIRED_VOICE_MESSAGE_ID,
                ACTIVE_TEXT_MESSAGE_ID,
                ACTIVE_VOICE_MESSAGE_ID
            )
        val receiptMap = trackedMessageIds.associateWith { setOf(RECIPIENT_A) }
        return PersistedChatState(
            ownerFingerprint = OWNER,
            messagesByConversation =
                mapOf(
                    CONVERSATION to
                        listOf(
                            textMessage(
                                messageId = EXPIRED_TEXT_MESSAGE_ID,
                                createdAt = NOW - 4L,
                                state = PersistedDeliveryState.Waiting,
                                expiresAt = NOW
                            ),
                            textMessage(
                                messageId = EXPIRED_VOICE_MESSAGE_ID,
                                createdAt = NOW - 3L,
                                state = PersistedDeliveryState.Sending,
                                expiresAt = NOW - 1L
                            ),
                            textMessage(
                                messageId = ACTIVE_TEXT_MESSAGE_ID,
                                createdAt = NOW - 2L,
                                state = PersistedDeliveryState.Waiting,
                                expiresAt = NOW + 1L
                            ),
                            textMessage(
                                messageId = ACTIVE_VOICE_MESSAGE_ID,
                                createdAt = NOW - 1L,
                                state = PersistedDeliveryState.Waiting,
                                expiresAt = NOW + 1L
                            )
                        )
                ),
            pendingTextOutbox =
                listOf(
                    validTextOutbox(EXPIRED_TEXT_MESSAGE_ID),
                    validTextOutbox(ACTIVE_TEXT_MESSAGE_ID)
                ),
            pendingVoiceOutbox =
                listOf(
                    validVoiceOutbox(
                        EXPIRED_VOICE_MESSAGE_ID,
                        voiceBase64(byteArrayOf(1, 2, 3))
                    ),
                    validVoiceOutbox(
                        ACTIVE_VOICE_MESSAGE_ID,
                        voiceBase64(byteArrayOf(4, 5, 6))
                    )
                ),
            outgoingEnvelopes =
                mapOf(
                    "expired-text-envelope" to validEnvelope(EXPIRED_TEXT_MESSAGE_ID),
                    "expired-voice-envelope" to validEnvelope(EXPIRED_VOICE_MESSAGE_ID),
                    "active-envelope" to validEnvelope(ACTIVE_TEXT_MESSAGE_ID)
                ),
            expectedRecipientsByMessage = receiptMap,
            deliveredRecipientsByMessage = receiptMap,
            readRecipientsByMessage = receiptMap
        )
    }

    private fun assertExpiredOutboundTrackingPruned(state: PersistedChatState) {
        val expiredMessageIds = setOf(EXPIRED_TEXT_MESSAGE_ID, EXPIRED_VOICE_MESSAGE_ID)
        val activeMessageIds = setOf(ACTIVE_TEXT_MESSAGE_ID, ACTIVE_VOICE_MESSAGE_ID)

        assertTrue(
            state.messagesByConversation.values
                .flatten()
                .mapNotNull(PersistedChatMessage::messageId)
                .none { it in expiredMessageIds }
        )
        assertEquals(
            setOf(ACTIVE_TEXT_MESSAGE_ID),
            state.pendingTextOutbox.mapTo(linkedSetOf(), PersistedTextOutboxMessage::displayMessageId)
        )
        assertEquals(
            setOf(ACTIVE_VOICE_MESSAGE_ID),
            state.pendingVoiceOutbox.mapTo(linkedSetOf(), PersistedVoiceOutboxMessage::displayMessageId)
        )
        assertEquals(
            mapOf("active-envelope" to validEnvelope(ACTIVE_TEXT_MESSAGE_ID)),
            state.outgoingEnvelopes
        )
        assertEquals(activeMessageIds, state.expectedRecipientsByMessage.keys)
        assertEquals(activeMessageIds, state.deliveredRecipientsByMessage.keys)
        assertEquals(activeMessageIds, state.readRecipientsByMessage.keys)
    }

    private fun completeState(text: String = "hello encrypted world"): PersistedChatState {
        val quote = PersistedQuotedMessage("quoted-id", "Alice", "quoted text")
        val voice = byteArrayOf(5, 4, 3, 2, 1)
        return PersistedChatState(
            ownerFingerprint = OWNER,
            savedAtEpochMillis = 1L,
            messagesByConversation =
                mapOf(
                    CONVERSATION to
                        listOf(
                            PersistedChatMessage(
                                text = text,
                                mine = true,
                                encrypted = true,
                                timestamp = "10:01",
                                senderName = "Me",
                                senderFingerprint = OWNER,
                                messageId = MESSAGE_ID,
                                receiptMessageId = "receipt-id",
                                deliveryState = PersistedDeliveryState.Read,
                                quotedMessage = quote,
                                createdAtEpochMillis = 10L,
                                expiresAtEpochMillis = NOW + 5_000L,
                                reactions = mapOf(RECIPIENT_A to "heart"),
                                forwarded = true,
                                forwardCount = 2
                            ),
                            PersistedChatMessage(
                                text = "voice message",
                                mine = false,
                                encrypted = true,
                                timestamp = "10:02",
                                senderName = "Alice",
                                senderFingerprint = RECIPIENT_A,
                                messageId = "voice-message-id",
                                deliveryState = PersistedDeliveryState.Received,
                                kind = PersistedChatMessageKind.Voice,
                                voiceDurationMs = 1_234L,
                                voiceAudioBase64 = voiceBase64(voice),
                                createdAtEpochMillis = 11L
                            )
                        )
                ),
            draftsByConversation =
                mapOf(CONVERSATION to PersistedConversationDraft("unfinished draft", 12L)),
            pendingTextOutbox =
                listOf(
                    PersistedTextOutboxMessage(
                        conversationId = CONVERSATION,
                        text = "queued text",
                        displayMessageId = MESSAGE_ID,
                        remainingTargetFingerprints = listOf(RECIPIENT_A, RECIPIENT_B),
                        quotedMessage = quote,
                        forwarded = true,
                        forwardCount = 1,
                        createdAtEpochMillis = 13L
                    )
                ),
            pendingVoiceOutbox =
                listOf(
                    PersistedVoiceOutboxMessage(
                        conversationId = CONVERSATION,
                        displayMessageId = "queued-voice-id",
                        remainingTargetFingerprints = listOf(RECIPIENT_B),
                        durationMs = 900L,
                        audioBase64 = voiceBase64(byteArrayOf(9, 8, 7)),
                        createdAtEpochMillis = 14L
                    )
                ),
            outgoingEnvelopes =
                mapOf(
                    "envelope-id" to
                        PersistedOutgoingEnvelope(
                            conversationId = CONVERSATION,
                            displayMessageId = MESSAGE_ID,
                            recipientFingerprint = RECIPIENT_A,
                            expectedRecipients = setOf(RECIPIENT_A, RECIPIENT_B),
                            createdAtEpochMillis = 15L
                        )
                ),
            expectedRecipientsByMessage = mapOf(MESSAGE_ID to setOf(RECIPIENT_A, RECIPIENT_B)),
            deliveredRecipientsByMessage = mapOf(MESSAGE_ID to setOf(RECIPIENT_A)),
            readRecipientsByMessage = mapOf(MESSAGE_ID to setOf(RECIPIENT_A)),
            sentReadReceipts = setOf("incoming-id:$OWNER"),
            conversationUpdateOrder = mapOf(CONVERSATION to 42L)
        )
    }

    private fun writeRawEncryptedState(
        file: File,
        state: PersistedChatState,
        cipher: ChatStateCipher = JvmAesGcmChatStateCipher(KEY_A)
    ) {
        val plaintext =
            Json.encodeToString(PersistedChatState.serializer(), state)
                .toByteArray(StandardCharsets.UTF_8)
        val associatedData =
            "spotchat-chat-state|file=1|schema=$CHAT_STATE_SCHEMA_VERSION|owner=$OWNER"
                .toByteArray(StandardCharsets.UTF_8)
        val payload =
            try {
                cipher.encrypt(plaintext, associatedData)
            } finally {
                plaintext.fill(0)
            }
        DataOutputStream(file.outputStream()).use { output ->
            output.write("SPCHSTAT".toByteArray(StandardCharsets.US_ASCII))
            output.writeInt(1)
            output.writeInt(payload.initializationVector.size)
            output.writeInt(payload.ciphertext.size)
            output.write(payload.initializationVector)
            output.write(payload.ciphertext)
        }
    }

    private fun validTextOutbox(
        displayMessageId: String,
        recipients: List<String> = listOf(RECIPIENT_A),
        conversationId: String = CONVERSATION,
        expiresAtEpochMillis: Long? = null
    ): PersistedTextOutboxMessage =
        PersistedTextOutboxMessage(
            conversationId = conversationId,
            text = "queued-$displayMessageId",
            displayMessageId = displayMessageId,
            remainingTargetFingerprints = recipients,
            createdAtEpochMillis = 1L,
            expiresAtEpochMillis = expiresAtEpochMillis
        )

    private fun validVoiceOutbox(
        displayMessageId: String,
        audioBase64: String,
        expiresAtEpochMillis: Long? = null
    ): PersistedVoiceOutboxMessage =
        PersistedVoiceOutboxMessage(
            conversationId = CONVERSATION,
            displayMessageId = displayMessageId,
            remainingTargetFingerprints = listOf(RECIPIENT_A),
            durationMs = 1_000L,
            audioBase64 = audioBase64,
            createdAtEpochMillis = 1L,
            expiresAtEpochMillis = expiresAtEpochMillis
        )

    private fun validEnvelope(displayMessageId: String): PersistedOutgoingEnvelope =
        PersistedOutgoingEnvelope(
            conversationId = CONVERSATION,
            displayMessageId = displayMessageId,
            recipientFingerprint = RECIPIENT_A,
            expectedRecipients = setOf(RECIPIENT_A),
            createdAtEpochMillis = 1L
        )

    private fun assertRejectedPendingData(label: String, block: () -> Unit) {
        try {
            block()
            fail("Expected pending-data validation failure for $label")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    private fun textMessage(
        messageId: String,
        createdAt: Long,
        state: PersistedDeliveryState = PersistedDeliveryState.Received,
        expiresAt: Long? = null
    ): PersistedChatMessage =
        PersistedChatMessage(
            text = messageId,
            mine = false,
            encrypted = true,
            timestamp = createdAt.toString(),
            messageId = messageId,
            deliveryState = state,
            createdAtEpochMillis = createdAt,
            expiresAtEpochMillis = expiresAt
        )

    private fun store(
        file: File,
        cipher: ChatStateCipher = JvmAesGcmChatStateCipher(KEY_A),
        clock: () -> Long = { SAVE_TIME }
    ): EncryptedChatStateStore =
        EncryptedChatStateStore(
            storageFile = file,
            ownerFingerprint = OWNER,
            cipher = cipher,
            clock = clock
        )

    private fun voiceBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun assertThrowsLocked(block: () -> Unit) {
        try {
            block()
            fail("Expected ChatStateStoreLockedException")
        } catch (_: ChatStateStoreLockedException) {
            // Expected.
        }
    }

    private fun ByteArray.containsSubsequence(needle: ByteArray): Boolean {
        if (needle.isEmpty()) return true
        return (0..size - needle.size).any { start ->
            needle.indices.all { offset -> this[start + offset] == needle[offset] }
        }
    }

    private fun withTemporaryFile(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("spotchat-state-test").toFile()
        try {
            block(File(directory, "state.bin"))
        } finally {
            directory.deleteRecursively()
        }
    }

    companion object {
        private const val OWNER = "owner-fingerprint"
        private const val CONVERSATION = "conversation-1"
        private const val PRIORITY_CONVERSATION = "priority-conversation"
        private const val PRIORITY_MESSAGE_ID = "priority-message-id"
        private const val MESSAGE_ID = "message-id"
        private const val EXPIRED_TEXT_MESSAGE_ID = "expired-text-message"
        private const val EXPIRED_VOICE_MESSAGE_ID = "expired-voice-message"
        private const val ACTIVE_TEXT_MESSAGE_ID = "active-text-message"
        private const val ACTIVE_VOICE_MESSAGE_ID = "active-voice-message"
        private const val RECIPIENT_A = "recipient-a"
        private const val RECIPIENT_B = "recipient-b"
        private const val PLAINTEXT_SENTINEL = "SENTINEL_SPOTCHAT_MUST_NEVER_BE_PLAINTEXT"
        private const val NOW = 1_000L
        private const val SAVE_TIME = NOW
        private val KEY_A = ByteArray(32) { index -> index.toByte() }
        private val KEY_B = ByteArray(32) { index -> (index + 71).toByte() }
    }
}
