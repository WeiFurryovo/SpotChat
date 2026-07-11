package com.weifurry.spotchat.domain

fun interface ReplayProtection {
    /** Atomically records a packet id and returns true only for the first observation. */
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

    override fun markIfNew(
        senderFingerprint: String,
        messageId: String
    ): Boolean =
        synchronized(seenPackets) {
            val now = nowEpochMillis()
            val expiresBefore = now - ReplayPolicy.ENTRY_RETENTION_MS
            val replayKey = "$senderFingerprint:$messageId"
            val previousSeenAt = seenPackets[replayKey]
            if (previousSeenAt != null && previousSeenAt > expiresBefore) {
                return@synchronized false
            }
            seenPackets.remove(replayKey)
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
            seenPackets[replayKey] = now
            true
        }
}
