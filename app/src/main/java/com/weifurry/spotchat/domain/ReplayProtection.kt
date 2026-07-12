package com.weifurry.spotchat.domain

interface ReplayProtection {
    /** Returns true only when an unexpired packet marker already exists; never records one. */
    fun hasSeen(
        senderFingerprint: String,
        messageId: String
    ): Boolean

    /** Atomically records a packet id and returns true only for the first active observation. */
    fun markIfNew(
        senderFingerprint: String,
        messageId: String
    ): Boolean
}

class ReplayProtectionCapacityException :
    IllegalStateException("Replay protection storage is full; refusing new packets")

internal object ReplayPolicy {
    const val MAX_PACKET_AGE_MS = 7L * 24L * 60L * 60L * 1000L
    const val MAX_FUTURE_SKEW_MS = 5L * 60L * 1000L
    const val ENTRY_RETENTION_MS = 8L * 24L * 60L * 60L * 1000L
    const val DEFAULT_MAX_ENTRIES = 100_000
}

class InMemoryReplayProtection(
    private val maxEntries: Int = ReplayPolicy.DEFAULT_MAX_ENTRIES,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) : ReplayProtection {
    private val seenPackets = LinkedHashMap<String, Long>()

    init {
        require(maxEntries > 0) {
            "Replay protection capacity must be positive"
        }
    }

    override fun hasSeen(
        senderFingerprint: String,
        messageId: String
    ): Boolean {
        validateReplayKey(senderFingerprint, messageId)
        return synchronized(seenPackets) {
            val seenAt = seenPackets[replayKey(senderFingerprint, messageId)]
            seenAt != null && isRetained(seenAt, nowEpochMillis())
        }
    }

    override fun markIfNew(
        senderFingerprint: String,
        messageId: String
    ): Boolean {
        validateReplayKey(senderFingerprint, messageId)
        return synchronized(seenPackets) {
            val now = nowEpochMillis()
            val expiresBefore = now - ReplayPolicy.ENTRY_RETENTION_MS
            val key = replayKey(senderFingerprint, messageId)
            val previousSeenAt = seenPackets[key]
            if (previousSeenAt != null && isRetained(previousSeenAt, now)) {
                return@synchronized false
            }
            seenPackets.remove(key)
            if (seenPackets.size >= maxEntries) {
                val iterator = seenPackets.entries.iterator()
                while (iterator.hasNext()) {
                    if (iterator.next().value <= expiresBefore) {
                        iterator.remove()
                    }
                }
            }
            if (seenPackets.size >= maxEntries) {
                throw ReplayProtectionCapacityException()
            }
            seenPackets[key] = now
            true
        }
    }

    private fun isRetained(
        seenAtEpochMillis: Long,
        nowEpochMillis: Long
    ): Boolean = seenAtEpochMillis > nowEpochMillis - ReplayPolicy.ENTRY_RETENTION_MS

    private fun replayKey(
        senderFingerprint: String,
        messageId: String
    ): String = "$senderFingerprint:$messageId"

    private fun validateReplayKey(
        senderFingerprint: String,
        messageId: String
    ) {
        require(senderFingerprint.isNotBlank()) {
            "Replay sender fingerprint cannot be blank"
        }
        require(messageId.isNotBlank()) {
            "Replay message id cannot be blank"
        }
    }
}
