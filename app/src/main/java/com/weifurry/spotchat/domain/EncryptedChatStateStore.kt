package com.weifurry.spotchat.domain

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json

/** Authenticated ciphertext returned by a [ChatStateCipher]. */
class ChatStateCipherPayload(initializationVector: ByteArray, ciphertext: ByteArray) {
    val initializationVector: ByteArray = initializationVector.copyOf()
    val ciphertext: ByteArray = ciphertext.copyOf()

    override fun equals(other: Any?): Boolean =
        other is ChatStateCipherPayload &&
            initializationVector.contentEquals(other.initializationVector) &&
            ciphertext.contentEquals(other.ciphertext)

    override fun hashCode(): Int =
        31 * initializationVector.contentHashCode() + ciphertext.contentHashCode()
}

/** Pluggable authenticated encryption boundary used by [EncryptedChatStateStore]. */
interface ChatStateCipher {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ChatStateCipherPayload
    fun decrypt(payload: ChatStateCipherPayload, associatedData: ByteArray): ByteArray
}

/** Platform-neutral AES-GCM implementation. Every encryption uses a fresh random 96-bit IV. */
class JvmAesGcmChatStateCipher(
    private val secretKey: SecretKey,
    private val secureRandom: SecureRandom = SecureRandom()
) : ChatStateCipher {
    constructor(
        keyBytes: ByteArray,
        secureRandom: SecureRandom = SecureRandom()
    ) : this(secretKeyFromBytes(keyBytes), secureRandom)

    init {
        require(secretKey.algorithm.equals(AES_ALGORITHM, ignoreCase = true)) {
            "Chat-state key must use AES"
        }
    }

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ChatStateCipherPayload {
        val initializationVector = ByteArray(GCM_IV_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            secretKey,
            GCMParameterSpec(GCM_TAG_BITS, initializationVector)
        )
        cipher.updateAAD(associatedData)
        return ChatStateCipherPayload(initializationVector, cipher.doFinal(plaintext))
    }

    override fun decrypt(payload: ChatStateCipherPayload, associatedData: ByteArray): ByteArray {
        require(payload.initializationVector.size == GCM_IV_BYTES) {
            "Unexpected chat-state IV length"
        }
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey,
            GCMParameterSpec(GCM_TAG_BITS, payload.initializationVector)
        )
        cipher.updateAAD(associatedData)
        return cipher.doFinal(payload.ciphertext)
    }

    companion object {
        private const val AES_ALGORITHM = "AES"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128

        private fun secretKeyFromBytes(keyBytes: ByteArray): SecretKey {
            require(keyBytes.size == 16 || keyBytes.size == 24 || keyBytes.size == 32) {
                "AES key must contain 16, 24, or 32 bytes"
            }
            return SecretKeySpec(keyBytes.copyOf(), AES_ALGORITHM)
        }
    }
}

/** Android production cipher backed by a non-exportable, dedicated Android Keystore key. */
class AndroidKeystoreChatStateCipher : ChatStateCipher {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ChatStateCipherPayload {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        // Keystore chooses the IV because randomized encryption is required for this key.
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext)
        val initializationVector = cipher.iv
        check(initializationVector.size == GCM_IV_BYTES) {
            "Android Keystore returned an unexpected GCM IV length"
        }
        return ChatStateCipherPayload(initializationVector, ciphertext)
    }

    override fun decrypt(payload: ChatStateCipherPayload, associatedData: ByteArray): ByteArray {
        require(payload.initializationVector.size == GCM_IV_BYTES) {
            "Unexpected chat-state IV length"
        }
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_BITS, payload.initializationVector)
        )
        cipher.updateAAD(associatedData)
        return cipher.doFinal(payload.ciphertext)
    }

    private fun getOrCreateKey(): SecretKey = synchronized(KEY_CREATION_LOCK) {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            return@synchronized keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
                ?: error("Chat-state Keystore alias is not an AES secret key")
        }
        val keyGenerator =
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val keySpec =
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
        keyGenerator.init(keySpec)
        keyGenerator.generateKey()
    }

    companion object {
        const val KEYSTORE_ALIAS = "spotchat_chat_state_key_v1"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private val KEY_CREATION_LOCK = Any()
    }
}

sealed interface ChatStateLoadResult {
    data object Missing : ChatStateLoadResult
    data class Loaded(val state: PersistedChatState) : ChatStateLoadResult
    /** The original encrypted file is deliberately retained for diagnosis or recovery. */
    data class Unreadable(val cause: Throwable) : ChatStateLoadResult
}

class ChatStateStoreLockedException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/**
 * Authenticated persistence for chat history, drafts, queues, and receipt bookkeeping. Atomic same-directory replacement is preferred when supported.
 * An unreadable target is fail-closed and is never silently overwritten.
 */
class EncryptedChatStateStore(
    val storageFile: File,
    private val ownerFingerprint: String,
    private val cipher: ChatStateCipher,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val temporaryFile: File
        get() = File(storageFile.absoluteFile.parentFile, storageFile.name + TEMP_FILE_SUFFIX)
    private val pathLock: Any = pathLocks.computeIfAbsent(lockKey(storageFile)) { Any() }

    private var health: StoreHealth = StoreHealth.Unchecked
    private var unreadableCause: Throwable? = null

    init {
        require(ownerFingerprint.isNotBlank()) { "Owner fingerprint must not be blank" }
        require(ownerFingerprint.length <= MAX_OWNER_FINGERPRINT_CHARS) {
            "Owner fingerprint is too long"
        }
    }

    @Synchronized
    fun load(): ChatStateLoadResult = synchronized(pathLock) {
        loadLocked()
    }

    private fun loadLocked(): ChatStateLoadResult {
        deleteStaleTemporaryFile(strict = false)
        if (!storageFile.exists()) {
            health = StoreHealth.Missing
            unreadableCause = null
            return ChatStateLoadResult.Missing
        }

        return try {
            require(storageFile.isFile) { "Chat-state path is not a regular file" }
            val payload = decodeFileEnvelope(readBoundedFile(storageFile))
            val plaintext = cipher.decrypt(payload, associatedData())
            try {
                require(plaintext.size <= MAX_PLAINTEXT_BYTES) {
                    "Decrypted chat state exceeds the plaintext limit"
                }
                val decoded =
                    json.decodeFromString(PersistedChatState.serializer(), plaintext.toString(Charsets.UTF_8))
                require(decoded.version == CHAT_STATE_SCHEMA_VERSION) {
                    "Unsupported chat-state schema version ${decoded.version}"
                }
                require(decoded.ownerFingerprint == ownerFingerprint) {
                    "Chat-state owner does not match the current identity"
                }
                val normalized =
                    normalizeForStorage(
                        state = decoded,
                        nowEpochMillis = clock(),
                        savedAtEpochMillis = decoded.savedAtEpochMillis
                    )
                health = StoreHealth.Readable
                unreadableCause = null
                ChatStateLoadResult.Loaded(normalized)
            } finally {
                plaintext.fill(0)
            }
        } catch (error: Exception) {
            health = StoreHealth.Unreadable
            unreadableCause = error
            ChatStateLoadResult.Unreadable(error)
        }
    }

    /** Saves and returns the exact normalized snapshot that was encrypted. */
    @Synchronized
    fun save(state: PersistedChatState): PersistedChatState = synchronized(pathLock) {
        ensureWritableTarget()
        deleteStaleTemporaryFile(strict = true)
        require(state.ownerFingerprint == ownerFingerprint) {
            "Cannot save chat state owned by a different identity"
        }
        require(state.version == CHAT_STATE_SCHEMA_VERSION) {
            "Cannot save unsupported chat-state schema version ${state.version}"
        }

        val normalized =
            normalizeForStorage(
                state = state,
                nowEpochMillis = clock(),
                savedAtEpochMillis = clock()
            )
        val plaintext =
            json.encodeToString(PersistedChatState.serializer(), normalized).toByteArray(Charsets.UTF_8)
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) {
            "Normalized chat state exceeds the plaintext limit"
        }
        val encodedFile =
            try {
                encodeFileEnvelope(cipher.encrypt(plaintext, associatedData()))
            } finally {
                plaintext.fill(0)
            }
        require(encodedFile.size <= MAX_FILE_BYTES) {
            "Encrypted chat state exceeds the file limit"
        }

        writeAtomically(encodedFile)
        health = StoreHealth.Readable
        unreadableCause = null
        normalized
    }

    private fun ensureWritableTarget() {
        if (health == StoreHealth.Unreadable) {
            if (!storageFile.exists()) {
                health = StoreHealth.Missing
                unreadableCause = null
            } else {
                throw ChatStateStoreLockedException(
                    "Refusing to overwrite unreadable encrypted chat state",
                    unreadableCause
                )
            }
        }
        if (health == StoreHealth.Missing && storageFile.exists()) health = StoreHealth.Unchecked
        if (storageFile.exists()) {
            // Authenticate the exact target on every overwrite attempt. A file may be damaged or
            // replaced after an earlier successful load/save, and fail-closed means that even the
            // already-readable store instance must never erase that evidence.
            when (val result = loadLocked()) {
                ChatStateLoadResult.Missing -> Unit
                is ChatStateLoadResult.Loaded -> Unit
                is ChatStateLoadResult.Unreadable ->
                    throw ChatStateStoreLockedException(
                        "Refusing to overwrite unreadable encrypted chat state",
                        result.cause
                    )
            }
        }
    }

    private fun normalizeForStorage(
        state: PersistedChatState,
        nowEpochMillis: Long,
        savedAtEpochMillis: Long
    ): PersistedChatState {
        val expiredMessageIds =
            buildSet {
                state.messagesByConversation.values
                    .asSequence()
                    .flatten()
                    .filter { message ->
                        message.deliveryState != PersistedDeliveryState.System &&
                            message.expiresAtEpochMillis?.let { it <= nowEpochMillis } == true
                    }
                    .mapNotNullTo(this) { message ->
                        message.messageId?.let(::normalizeIdentifier)
                    }
                state.pendingTextOutbox
                    .asSequence()
                    .filter { item ->
                        item.expiresAtEpochMillis?.let { it <= nowEpochMillis } == true
                    }
                    .mapNotNullTo(this) { item -> normalizeIdentifier(item.displayMessageId) }
                state.pendingVoiceOutbox
                    .asSequence()
                    .filter { item ->
                        item.expiresAtEpochMillis?.let { it <= nowEpochMillis } == true
                    }
                    .mapNotNullTo(this) { item -> normalizeIdentifier(item.displayMessageId) }
            }
        val stateForNormalization =
            state.copy(
                pendingTextOutbox =
                    state.pendingTextOutbox.filterNot { item ->
                        item.expiresAtEpochMillis?.let { it <= nowEpochMillis } == true ||
                            normalizeIdentifier(item.displayMessageId) in expiredMessageIds
                    },
                pendingVoiceOutbox =
                    state.pendingVoiceOutbox.filterNot { item ->
                        item.expiresAtEpochMillis?.let { it <= nowEpochMillis } == true ||
                            normalizeIdentifier(item.displayMessageId) in expiredMessageIds
                    },
                outgoingEnvelopes =
                    state.outgoingEnvelopes.filterValues { envelope ->
                        normalizeIdentifier(envelope.displayMessageId) !in expiredMessageIds
                    },
                expectedRecipientsByMessage =
                    state.expectedRecipientsByMessage.filterKeys { messageId ->
                        normalizeIdentifier(messageId) !in expiredMessageIds
                    },
                deliveredRecipientsByMessage =
                    state.deliveredRecipientsByMessage.filterKeys { messageId ->
                        normalizeIdentifier(messageId) !in expiredMessageIds
                    },
                readRecipientsByMessage =
                    state.readRecipientsByMessage.filterKeys { messageId ->
                        normalizeIdentifier(messageId) !in expiredMessageIds
                    }
            )
        require(stateForNormalization.pendingTextOutbox.size <= MAX_TEXT_OUTBOX_MESSAGES) {
            "Refusing to process over-capacity text outbox"
        }
        require(stateForNormalization.pendingVoiceOutbox.size <= MAX_VOICE_OUTBOX_MESSAGES) {
            "Refusing to process over-capacity voice outbox"
        }
        require(stateForNormalization.outgoingEnvelopes.size <= MAX_OUTGOING_ENVELOPES) {
            "Refusing to process over-capacity outgoing envelopes"
        }
        val textOutboxCandidates =
            stateForNormalization.pendingTextOutbox
                .asSequence()
                .mapNotNull { item -> normalizeTextOutbox(item, nowEpochMillis) }
                .distinctBy(PersistedTextOutboxMessage::displayMessageId)
                .sortedWith(
                    compareByDescending<PersistedTextOutboxMessage> { it.createdAtEpochMillis }
                        .thenBy { it.displayMessageId }
                )
                .take(MAX_TEXT_OUTBOX_MESSAGES)
                .toList()
        val voiceOutboxCandidates =
            stateForNormalization.pendingVoiceOutbox
                .asSequence()
                .mapNotNull { item -> normalizeVoiceOutbox(item, nowEpochMillis) }
                .distinctBy { it.message.displayMessageId }
                .sortedWith(
                    compareByDescending<NormalizedVoiceOutbox> { it.message.createdAtEpochMillis }
                        .thenBy { it.message.displayMessageId }
                )
                .take(MAX_VOICE_OUTBOX_MESSAGES)
                .toList()

        val normalizedDraftCandidates =
            stateForNormalization.draftsByConversation.entries.mapNotNull { (rawConversationId, draft) ->
                val conversationId = normalizeIdentifier(rawConversationId) ?: return@mapNotNull null
                val text = draft.text.take(MAX_DRAFT_CHARS)
                if (text.isBlank()) {
                    null
                } else {
                    conversationId to
                        PersistedConversationDraft(
                            text = text,
                            updatedAtEpochMillis = draft.updatedAtEpochMillis.coerceAtLeast(0L)
                        )
                }
            }

        val normalizedUpdateTimes = linkedMapOf<String, Long>()
        stateForNormalization.conversationUpdateOrder.forEach { (rawConversationId, updateOrder) ->
            normalizeIdentifier(rawConversationId)?.let { conversationId ->
                normalizedUpdateTimes[conversationId] =
                    maxOf(normalizedUpdateTimes[conversationId] ?: Long.MIN_VALUE, updateOrder)
            }
        }

        val outboxConversationIds =
            (textOutboxCandidates.asSequence().map { it.conversationId } +
                voiceOutboxCandidates.asSequence().map { it.message.conversationId })
                .toSet()
        val allConversationIds = linkedSetOf<String>()
        allConversationIds += outboxConversationIds
        stateForNormalization.messagesByConversation.keys.mapNotNullTo(allConversationIds, ::normalizeIdentifier)
        normalizedDraftCandidates.mapTo(allConversationIds) { it.first }
        allConversationIds += normalizedUpdateTimes.keys

        val latestActivity = normalizedUpdateTimes.toMutableMap()
        stateForNormalization.messagesByConversation.forEach { (rawConversationId, messages) ->
            val conversationId = normalizeIdentifier(rawConversationId) ?: return@forEach
            messages.maxOfOrNull(PersistedChatMessage::createdAtEpochMillis)?.let { timestamp ->
                latestActivity[conversationId] =
                    maxOf(latestActivity[conversationId] ?: Long.MIN_VALUE, timestamp)
            }
        }
        normalizedDraftCandidates.forEach { (conversationId, draft) ->
            latestActivity[conversationId] =
                maxOf(latestActivity[conversationId] ?: Long.MIN_VALUE, draft.updatedAtEpochMillis)
        }
        textOutboxCandidates.forEach { item ->
            latestActivity[item.conversationId] =
                maxOf(latestActivity[item.conversationId] ?: Long.MIN_VALUE, item.createdAtEpochMillis)
        }
        voiceOutboxCandidates.forEach { item ->
            latestActivity[item.message.conversationId] =
                maxOf(
                    latestActivity[item.message.conversationId] ?: Long.MIN_VALUE,
                    item.message.createdAtEpochMillis
                )
        }

        val retainedConversationIds =
            allConversationIds
                .sortedWith(
                    compareByDescending<String> { it in outboxConversationIds }
                        .thenByDescending { latestActivity[it] ?: Long.MIN_VALUE }
                        .thenBy { it }
                )
                .take(MAX_CONVERSATIONS)
                .toCollection(linkedSetOf())

        val textOutbox =
            textOutboxCandidates.filter { it.conversationId in retainedConversationIds }
        var remainingVoiceBytes = MAX_TOTAL_VOICE_BYTES
        val voiceOutbox = mutableListOf<PersistedVoiceOutboxMessage>()
        voiceOutboxCandidates.forEach { candidate ->
            if (
                candidate.message.conversationId in retainedConversationIds &&
                candidate.decodedBytes <= remainingVoiceBytes
            ) {
                voiceOutbox += candidate.message
                remainingVoiceBytes -= candidate.decodedBytes
            }
        }

        val outboxDisplayMessageIds =
            (textOutbox.asSequence().map { it.displayMessageId } +
                voiceOutbox.asSequence().map { it.displayMessageId })
                .toSet()
        val messages =
            normalizeMessages(
                rawMessages = stateForNormalization.messagesByConversation,
                retainedConversationIds = retainedConversationIds,
                prioritizedMessageIds = outboxDisplayMessageIds,
                nowEpochMillis = nowEpochMillis,
                initialVoiceBudgetBytes = remainingVoiceBytes
            )

        val drafts =
            normalizedDraftCandidates
                .asSequence()
                .filter { (conversationId, _) -> conversationId in retainedConversationIds }
                .sortedWith(
                    compareByDescending<Pair<String, PersistedConversationDraft>> {
                        it.second.updatedAtEpochMillis
                    }.thenBy { it.first }
                )
                .take(MAX_DRAFTS)
                .associateTo(linkedMapOf()) { it }

        val outgoingEnvelopes =
            normalizeOutgoingEnvelopes(
                source = stateForNormalization.outgoingEnvelopes,
                prioritizedMessageIds = outboxDisplayMessageIds
            )
        val criticalReceiptMessageIds =
            buildSet {
                addAll(outboxDisplayMessageIds)
                outgoingEnvelopes.values.forEach { add(it.displayMessageId) }
            }
        require(criticalReceiptMessageIds.size <= MAX_RECEIPT_TRACKED_MESSAGES) {
            "Chat state contains too many pending messages requiring receipt tracking"
        }
        val relevantMessageIds =
            buildSet {
                addAll(criticalReceiptMessageIds)
                messages.values.flatten().mapNotNullTo(this) { it.messageId }
            }

        val normalizedState =
            PersistedChatState(
                ownerFingerprint = ownerFingerprint,
                version = CHAT_STATE_SCHEMA_VERSION,
                savedAtEpochMillis = savedAtEpochMillis.coerceAtLeast(0L),
                messagesByConversation = messages,
                draftsByConversation = drafts,
                pendingTextOutbox = textOutbox,
                pendingVoiceOutbox = voiceOutbox,
                outgoingEnvelopes = outgoingEnvelopes,
                expectedRecipientsByMessage =
                    normalizeReceiptMap(
                        source = stateForNormalization.expectedRecipientsByMessage,
                        relevantMessageIds = relevantMessageIds,
                        criticalMessageIds = criticalReceiptMessageIds
                    ),
                deliveredRecipientsByMessage =
                    normalizeReceiptMap(
                        source = stateForNormalization.deliveredRecipientsByMessage,
                        relevantMessageIds = relevantMessageIds,
                        criticalMessageIds = criticalReceiptMessageIds
                    ),
                readRecipientsByMessage =
                    normalizeReceiptMap(
                        source = stateForNormalization.readRecipientsByMessage,
                        relevantMessageIds = relevantMessageIds,
                        criticalMessageIds = criticalReceiptMessageIds
                    ),
                sentReadReceipts =
                    stateForNormalization.sentReadReceipts
                        .asSequence()
                        .mapNotNull { normalizeBoundedToken(it, MAX_SENT_RECEIPT_ID_CHARS) }
                        .distinct()
                        .toList()
                        .takeLast(MAX_SENT_READ_RECEIPTS)
                        .toCollection(linkedSetOf()),
                conversationUpdateOrder =
                    retainedConversationIds
                        .asSequence()
                        .mapNotNull { conversationId ->
                            normalizedUpdateTimes[conversationId]?.let { conversationId to it }
                        }
                        .associateTo(linkedMapOf()) { it }
            )
        requirePendingDataPreserved(stateForNormalization, normalizedState)
        requireCriticalReceiptDataPreserved(
            source = stateForNormalization,
            normalized = normalizedState,
            criticalMessageIds = criticalReceiptMessageIds
        )
        return normalizedState
    }

    private fun requirePendingDataPreserved(
        source: PersistedChatState,
        normalized: PersistedChatState
    ) {
        requireLosslessPendingList(
            label = "text outbox",
            source = source.pendingTextOutbox,
            normalized = normalized.pendingTextOutbox,
            key = PersistedTextOutboxMessage::displayMessageId
        )
        requireLosslessPendingList(
            label = "voice outbox",
            source = source.pendingVoiceOutbox,
            normalized = normalized.pendingVoiceOutbox,
            key = PersistedVoiceOutboxMessage::displayMessageId
        )
        require(source.outgoingEnvelopes == normalized.outgoingEnvelopes) {
            "Chat state contains invalid or over-capacity outgoing envelopes"
        }
    }

    private fun requireCriticalReceiptDataPreserved(
        source: PersistedChatState,
        normalized: PersistedChatState,
        criticalMessageIds: Set<String>
    ) {
        fun requireMapPreserved(
            label: String,
            sourceMap: Map<String, Set<String>>,
            normalizedMap: Map<String, Set<String>>
        ) {
            val criticalSource = sourceMap.filterKeys { it in criticalMessageIds }
            val criticalNormalized = normalizedMap.filterKeys { it in criticalMessageIds }
            require(criticalSource == criticalNormalized) {
                "Chat state contains invalid or over-capacity $label for pending messages"
            }
        }

        requireMapPreserved(
            label = "expected recipients",
            sourceMap = source.expectedRecipientsByMessage,
            normalizedMap = normalized.expectedRecipientsByMessage
        )
        requireMapPreserved(
            label = "delivered recipients",
            sourceMap = source.deliveredRecipientsByMessage,
            normalizedMap = normalized.deliveredRecipientsByMessage
        )
        requireMapPreserved(
            label = "read recipients",
            sourceMap = source.readRecipientsByMessage,
            normalizedMap = normalized.readRecipientsByMessage
        )
    }

    private fun <T> requireLosslessPendingList(
        label: String,
        source: List<T>,
        normalized: List<T>,
        key: (T) -> String
    ) {
        val sourceByKey = source.associateBy(key)
        val normalizedByKey = normalized.associateBy(key)
        require(sourceByKey.size == source.size && normalizedByKey.size == normalized.size) {
            "Chat state contains duplicate $label message identifiers"
        }
        require(sourceByKey == normalizedByKey) {
            "Chat state contains invalid or over-capacity $label data"
        }
    }

    private fun normalizeTextOutbox(
        item: PersistedTextOutboxMessage,
        nowEpochMillis: Long
    ): PersistedTextOutboxMessage? {
        if (item.expiresAtEpochMillis?.let { it <= nowEpochMillis } == true) return null
        val conversationId = normalizeIdentifier(item.conversationId) ?: return null
        val displayMessageId = normalizeIdentifier(item.displayMessageId) ?: return null
        val text = item.text.take(MAX_OUTBOX_TEXT_CHARS)
        val recipients = normalizeRecipients(item.remainingTargetFingerprints)
        if (text.isBlank() || recipients.isEmpty()) return null
        return PersistedTextOutboxMessage(
            conversationId = conversationId,
            text = text,
            displayMessageId = displayMessageId,
            remainingTargetFingerprints = recipients,
            quotedMessage = normalizeQuotedMessage(item.quotedMessage),
            forwarded = item.forwarded,
            forwardCount = item.forwardCount.coerceIn(0, MAX_FORWARD_COUNT),
            createdAtEpochMillis = item.createdAtEpochMillis.coerceAtLeast(0L),
            expiresAtEpochMillis = item.expiresAtEpochMillis
        )
    }

    private fun normalizeVoiceOutbox(
        item: PersistedVoiceOutboxMessage,
        nowEpochMillis: Long
    ): NormalizedVoiceOutbox? {
        if (item.expiresAtEpochMillis?.let { it <= nowEpochMillis } == true) return null
        val conversationId = normalizeIdentifier(item.conversationId) ?: return null
        val displayMessageId = normalizeIdentifier(item.displayMessageId) ?: return null
        val recipients = normalizeRecipients(item.remainingTargetFingerprints)
        if (recipients.isEmpty() || item.durationMs <= 0L) return null
        val audio = decodeVoiceAudio(item.audioBase64) ?: return null
        return NormalizedVoiceOutbox(
            message =
                PersistedVoiceOutboxMessage(
                    conversationId = conversationId,
                    displayMessageId = displayMessageId,
                    remainingTargetFingerprints = recipients,
                    durationMs = item.durationMs.coerceIn(0L, MAX_VOICE_DURATION_MS),
                    audioBase64 = Base64.getEncoder().encodeToString(audio),
                    createdAtEpochMillis = item.createdAtEpochMillis.coerceAtLeast(0L),
                    expiresAtEpochMillis = item.expiresAtEpochMillis
                ),
            decodedBytes = audio.size
        )
    }

    private fun normalizeMessages(
        rawMessages: Map<String, List<PersistedChatMessage>>,
        retainedConversationIds: Set<String>,
        prioritizedMessageIds: Set<String>,
        nowEpochMillis: Long,
        initialVoiceBudgetBytes: Int
    ): Map<String, List<PersistedChatMessage>> {
        val byConversation = linkedMapOf<String, MutableList<MessageCandidate>>()
        var inputOrder = 0L
        rawMessages.forEach { (rawConversationId, conversationMessages) ->
            val conversationId = normalizeIdentifier(rawConversationId) ?: return@forEach
            if (conversationId !in retainedConversationIds) return@forEach
            conversationMessages.forEach messageLoop@{ message ->
                val normalized = normalizeMessage(message, nowEpochMillis) ?: return@messageLoop
                byConversation
                    .getOrPut(conversationId, ::mutableListOf)
                    .add(
                        MessageCandidate(
                            conversationId = conversationId,
                            inputOrder = inputOrder++,
                            message = normalized.message,
                            voiceBytes = normalized.voiceBytes
                        )
                    )
            }
        }

        val perConversationLimited =
            byConversation.values.flatMap { messages ->
                messages
                    .sortedWith(
                        compareByDescending<MessageCandidate> {
                            it.message.messageId in prioritizedMessageIds
                        }
                            .thenByDescending { it.message.createdAtEpochMillis }
                            .thenByDescending { it.inputOrder }
                    )
                    .take(MAX_MESSAGES_PER_CONVERSATION)
            }
        val selected = mutableListOf<MessageCandidate>()
        var remainingVoiceBytes = initialVoiceBudgetBytes
        perConversationLimited
            .sortedWith(
                compareByDescending<MessageCandidate> {
                    it.message.messageId in prioritizedMessageIds
                }
                    .thenByDescending { it.message.createdAtEpochMillis }
                    .thenByDescending { it.inputOrder }
            )
            .forEach { candidate ->
                if (selected.size >= MAX_MESSAGES_TOTAL) return@forEach
                if (candidate.voiceBytes > remainingVoiceBytes) return@forEach
                selected += candidate
                remainingVoiceBytes -= candidate.voiceBytes
            }

        return selected
            .groupBy(MessageCandidate::conversationId)
            .toSortedMap()
            .mapValuesTo(linkedMapOf()) { (_, candidates) ->
                candidates.sortedBy(MessageCandidate::inputOrder).map(MessageCandidate::message)
            }
    }

    private fun normalizeMessage(
        message: PersistedChatMessage,
        nowEpochMillis: Long
    ): NormalizedMessage? {
        if (message.deliveryState == PersistedDeliveryState.System) return null
        if (message.expiresAtEpochMillis?.let { it <= nowEpochMillis } == true) return null

        val normalizedText = message.text.take(MAX_MESSAGE_TEXT_CHARS)
        val normalizedVoice =
            if (message.kind == PersistedChatMessageKind.Voice) {
                decodeVoiceAudio(message.voiceAudioBase64 ?: return null) ?: return null
            } else {
                null
            }
        if (message.kind == PersistedChatMessageKind.Text && normalizedText.isBlank()) return null

        val reactions = linkedMapOf<String, String>()
        message.reactions.forEach { (rawFingerprint, rawReaction) ->
            if (reactions.size >= MAX_REACTIONS_PER_MESSAGE) return@forEach
            val fingerprint = normalizeFingerprint(rawFingerprint) ?: return@forEach
            val reaction = normalizeBoundedString(rawReaction, MAX_REACTION_CHARS) ?: return@forEach
            reactions.putIfAbsent(fingerprint, reaction)
        }

        return NormalizedMessage(
            message =
                PersistedChatMessage(
                    text = normalizedText,
                    mine = message.mine,
                    encrypted = message.encrypted,
                    timestamp = message.timestamp.take(MAX_TIMESTAMP_CHARS),
                    senderName = normalizeOptionalString(message.senderName, MAX_SENDER_NAME_CHARS),
                    senderFingerprint = message.senderFingerprint?.let(::normalizeFingerprint),
                    messageId = message.messageId?.let(::normalizeIdentifier),
                    receiptMessageId = message.receiptMessageId?.let(::normalizeIdentifier),
                    deliveryState = message.deliveryState,
                    kind = message.kind,
                    quotedMessage = normalizeQuotedMessage(message.quotedMessage),
                    voiceDurationMs =
                        if (normalizedVoice != null) {
                            (message.voiceDurationMs ?: 0L).coerceIn(0L, MAX_VOICE_DURATION_MS)
                        } else {
                            null
                        },
                    voiceAudioBase64 =
                        normalizedVoice?.let { Base64.getEncoder().encodeToString(it) },
                    createdAtEpochMillis = message.createdAtEpochMillis.coerceAtLeast(0L),
                    expiresAtEpochMillis = message.expiresAtEpochMillis,
                    reactions = reactions,
                    forwarded = message.forwarded,
                    forwardCount = message.forwardCount.coerceIn(0, MAX_FORWARD_COUNT)
                ),
            voiceBytes = normalizedVoice?.size ?: 0
        )
    }

    private fun normalizeOutgoingEnvelopes(
        source: Map<String, PersistedOutgoingEnvelope>,
        prioritizedMessageIds: Set<String>
    ): Map<String, PersistedOutgoingEnvelope> =
        // Receipt correlation can outlive bounded message history. A valid active envelope must not
        // disappear merely because its conversation fell outside the retained history window.
        source.entries
            .asSequence()
            .mapNotNull { (rawEnvelopeId, envelope) ->
                val envelopeId = normalizeIdentifier(rawEnvelopeId) ?: return@mapNotNull null
                val conversationId = normalizeIdentifier(envelope.conversationId) ?: return@mapNotNull null
                val displayMessageId = normalizeIdentifier(envelope.displayMessageId) ?: return@mapNotNull null
                val recipient = normalizeFingerprint(envelope.recipientFingerprint) ?: return@mapNotNull null
                val expectedRecipients = normalizeRecipientSet(envelope.expectedRecipients)
                if (expectedRecipients.isEmpty() || recipient !in expectedRecipients) {
                    return@mapNotNull null
                }
                envelopeId to
                    PersistedOutgoingEnvelope(
                        conversationId = conversationId,
                        displayMessageId = displayMessageId,
                        recipientFingerprint = recipient,
                        expectedRecipients = expectedRecipients,
                        createdAtEpochMillis = envelope.createdAtEpochMillis.coerceAtLeast(0L)
                    )
            }
            .distinctBy { it.first }
            .sortedWith(
                compareByDescending<Pair<String, PersistedOutgoingEnvelope>> {
                    it.second.displayMessageId in prioritizedMessageIds
                }
                    .thenByDescending { it.second.createdAtEpochMillis }
                    .thenBy { it.first }
            )
            .take(MAX_OUTGOING_ENVELOPES)
            .associateTo(linkedMapOf()) { it }

    private fun normalizeReceiptMap(
        source: Map<String, Set<String>>,
        relevantMessageIds: Set<String>,
        criticalMessageIds: Set<String>
    ): Map<String, Set<String>> =
        source.entries
            .asSequence()
            .mapNotNull { (rawMessageId, rawRecipients) ->
                val messageId = normalizeIdentifier(rawMessageId) ?: return@mapNotNull null
                val recipients = normalizeRecipientSet(rawRecipients)
                if (recipients.isEmpty()) null else messageId to recipients
            }
            .distinctBy { it.first }
            .sortedWith(
                compareByDescending<Pair<String, Set<String>>> { it.first in criticalMessageIds }
                    .thenByDescending { it.first in relevantMessageIds }
                    .thenBy { it.first }
            )
            .take(MAX_RECEIPT_TRACKED_MESSAGES)
            .associateTo(linkedMapOf()) { it }

    private fun normalizeQuotedMessage(message: PersistedQuotedMessage?): PersistedQuotedMessage? {
        message ?: return null
        val messageId = normalizeIdentifier(message.messageId) ?: return null
        return PersistedQuotedMessage(
            messageId = messageId,
            senderName = message.senderName.take(MAX_SENDER_NAME_CHARS),
            text = message.text.take(MAX_QUOTED_TEXT_CHARS)
        )
    }

    private fun normalizeRecipients(recipients: Iterable<String>): List<String> =
        recipients
            .asSequence()
            .mapNotNull(::normalizeFingerprint)
            .distinct()
            .take(MAX_RECIPIENTS)
            .toList()

    private fun normalizeRecipientSet(recipients: Iterable<String>): Set<String> =
        normalizeRecipients(recipients).toCollection(linkedSetOf())

    private fun normalizeIdentifier(value: String): String? =
        normalizeBoundedToken(value, MAX_IDENTIFIER_CHARS)

    private fun normalizeFingerprint(value: String): String? =
        normalizeBoundedToken(value, MAX_FINGERPRINT_CHARS)

    private fun normalizeOptionalString(value: String?, maximumChars: Int): String? =
        value?.take(maximumChars)?.takeIf(String::isNotBlank)

    private fun normalizeBoundedToken(value: String, maximumChars: Int): String? =
        value.trim().takeIf { it.isNotBlank() && it.length <= maximumChars }

    private fun normalizeBoundedString(value: String, maximumChars: Int): String? =
        value.trim().take(maximumChars).takeIf(String::isNotBlank)

    private fun decodeVoiceAudio(encoded: String): ByteArray? {
        if (encoded.isBlank() || encoded.length > MAX_SINGLE_VOICE_BASE64_CHARS) return null
        return try {
            Base64.getDecoder().decode(encoded).takeIf {
                it.isNotEmpty() && it.size <= MAX_SINGLE_VOICE_BYTES
            }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun associatedData(): ByteArray =
        "spotchat-chat-state|file=$FILE_FORMAT_VERSION|schema=$CHAT_STATE_SCHEMA_VERSION|owner=$ownerFingerprint"
            .toByteArray(StandardCharsets.UTF_8)

    private fun encodeFileEnvelope(payload: ChatStateCipherPayload): ByteArray {
        require(payload.initializationVector.size == GCM_IV_BYTES) {
            "Chat-state cipher returned an invalid IV"
        }
        require(payload.ciphertext.size >= GCM_TAG_BYTES) {
            "Chat-state cipher returned an invalid ciphertext"
        }
        return ByteArrayOutputStream().use { byteStream ->
            DataOutputStream(byteStream).use { output ->
                output.write(FILE_MAGIC)
                output.writeInt(FILE_FORMAT_VERSION)
                output.writeInt(payload.initializationVector.size)
                output.writeInt(payload.ciphertext.size)
                output.write(payload.initializationVector)
                output.write(payload.ciphertext)
            }
            byteStream.toByteArray()
        }
    }

    private fun decodeFileEnvelope(encoded: ByteArray): ChatStateCipherPayload {
        require(encoded.size >= FILE_HEADER_BYTES + GCM_IV_BYTES + GCM_TAG_BYTES) {
            "Chat-state file is truncated"
        }
        return DataInputStream(ByteArrayInputStream(encoded)).use { input ->
            val magic = ByteArray(FILE_MAGIC.size).also(input::readFully)
            require(magic.contentEquals(FILE_MAGIC)) { "Chat-state file has an invalid magic header" }
            val fileVersion = input.readInt()
            require(fileVersion == FILE_FORMAT_VERSION) {
                "Unsupported chat-state file version $fileVersion"
            }
            val ivSize = input.readInt()
            val ciphertextSize = input.readInt()
            require(ivSize == GCM_IV_BYTES) { "Chat-state file has an invalid IV length" }
            require(ciphertextSize >= GCM_TAG_BYTES) { "Chat-state file has invalid ciphertext" }
            val expectedSize = FILE_HEADER_BYTES.toLong() + ivSize.toLong() + ciphertextSize.toLong()
            require(expectedSize == encoded.size.toLong()) {
                "Chat-state file length does not match its header"
            }
            val initializationVector = ByteArray(ivSize).also(input::readFully)
            val ciphertext = ByteArray(ciphertextSize).also(input::readFully)
            require(input.read() == -1) { "Chat-state file contains trailing data" }
            ChatStateCipherPayload(initializationVector, ciphertext)
        }
    }

    private fun readBoundedFile(file: File): ByteArray {
        val declaredLength = file.length()
        require(declaredLength in 1..MAX_FILE_BYTES.toLong()) {
            "Chat-state file exceeds the file limit or is empty"
        }
        return file.inputStream().buffered().use { input ->
            val output = ByteArrayOutputStream(declaredLength.toInt())
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_FILE_BYTES) { "Chat-state file grew beyond the file limit" }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    private fun writeAtomically(encodedFile: ByteArray) {
        val parent = checkNotNull(storageFile.absoluteFile.parentFile)
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("Unable to create chat-state directory")
        }
        if (!parent.isDirectory) {
            throw IOException("Chat-state parent path is not a directory")
        }
        deleteStaleTemporaryFile(strict = true)

        var completed = false
        try {
            FileOutputStream(temporaryFile, false).use { output ->
                output.write(encodedFile)
                output.flush()
                output.fd.sync()
            }
            try {
                Files.move(
                    temporaryFile.toPath(),
                    storageFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporaryFile.toPath(),
                    storageFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
            completed = true
        } finally {
            if (!completed || temporaryFile.exists()) temporaryFile.delete()
        }
    }

    private fun deleteStaleTemporaryFile(strict: Boolean) {
        if (!temporaryFile.exists()) return
        if (!temporaryFile.delete() && strict) {
            throw IOException("Unable to remove stale chat-state temporary file")
        }
    }

    private enum class StoreHealth { Unchecked, Missing, Readable, Unreadable }

    private data class NormalizedVoiceOutbox(
        val message: PersistedVoiceOutboxMessage,
        val decodedBytes: Int
    )

    private data class NormalizedMessage(
        val message: PersistedChatMessage,
        val voiceBytes: Int
    )

    private data class MessageCandidate(
        val conversationId: String,
        val inputOrder: Long,
        val message: PersistedChatMessage,
        val voiceBytes: Int
    )

    companion object {
        const val DIRECTORY_NAME = "spotchat-chat-state"
        const val FILE_NAME = "spotchat_state.bin"
        const val TEMP_FILE_SUFFIX = ".tmp"

        const val MAX_FILE_BYTES = 16 * 1024 * 1024
        const val MAX_PLAINTEXT_BYTES = 15 * 1024 * 1024
        const val MAX_CONVERSATIONS = 64
        const val MAX_MESSAGES_PER_CONVERSATION = 100
        const val MAX_MESSAGES_TOTAL = 500
        const val MAX_DRAFTS = 64
        const val MAX_TEXT_OUTBOX_MESSAGES = 128
        const val MAX_VOICE_OUTBOX_MESSAGES = 128
        const val MAX_OUTGOING_ENVELOPES = 256
        const val MAX_RECEIPT_TRACKED_MESSAGES = 256
        const val MAX_SENT_READ_RECEIPTS = 512
        const val MAX_RECIPIENTS = 64
        const val MAX_TOTAL_VOICE_BYTES = 4 * 1024 * 1024
        const val MAX_SINGLE_VOICE_BYTES = 256 * 1024

        private const val MAX_OWNER_FINGERPRINT_CHARS = 256
        private const val MAX_IDENTIFIER_CHARS = 160
        private const val MAX_FINGERPRINT_CHARS = 160
        private const val MAX_MESSAGE_TEXT_CHARS = 1_024
        private const val MAX_OUTBOX_TEXT_CHARS = 1_024
        private const val MAX_DRAFT_CHARS = 1_024
        private const val MAX_QUOTED_TEXT_CHARS = 512
        private const val MAX_SENDER_NAME_CHARS = 96
        private const val MAX_TIMESTAMP_CHARS = 64
        private const val MAX_REACTION_CHARS = 32
        private const val MAX_REACTIONS_PER_MESSAGE = 16
        private const val MAX_SENT_RECEIPT_ID_CHARS = 320
        private const val MAX_FORWARD_COUNT = 1_000
        private const val MAX_VOICE_DURATION_MS = 10L * 60L * 1_000L
        private const val MAX_SINGLE_VOICE_BASE64_CHARS =
            ((MAX_SINGLE_VOICE_BYTES + 2) / 3) * 4
        private const val FILE_FORMAT_VERSION = 1
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BYTES = 16
        private val FILE_MAGIC = byteArrayOf(0x53, 0x50, 0x43, 0x48, 0x53, 0x54, 0x41, 0x54)
        private val FILE_HEADER_BYTES = FILE_MAGIC.size + Int.SIZE_BYTES * 3
        private val pathLocks = ConcurrentHashMap<String, Any>()

        private fun lockKey(file: File): String =
            runCatching { file.canonicalPath }.getOrElse {
                file.absoluteFile.toPath().normalize().toString()
            }

        fun create(context: Context, ownerFingerprint: String): EncryptedChatStateStore {
            val directory = File(context.noBackupFilesDir, DIRECTORY_NAME)
            return EncryptedChatStateStore(
                storageFile = File(directory, FILE_NAME),
                ownerFingerprint = ownerFingerprint,
                cipher = AndroidKeystoreChatStateCipher()
            )
        }
    }
}
