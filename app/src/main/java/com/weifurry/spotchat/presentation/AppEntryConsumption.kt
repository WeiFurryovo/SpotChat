package com.weifurry.spotchat.presentation

internal data class PendingAppEntry<out T : Any>(
    val id: Long,
    val value: T
)

internal data class ClaimedAppEntry<out T : Any>(
    val entry: PendingAppEntry<T>,
    val shouldNeutralizeActivityIntent: Boolean
)

internal sealed interface AppEntryOffer<out T : Any> {
    data object Ignored : AppEntryOffer<Nothing>

    data class Accepted<T : Any>(val entry: PendingAppEntry<T>) : AppEntryOffer<T>
}

internal sealed interface AppEntryLaunchDecision<out T : Any> {
    data class Observe<T : Any>(val value: T) : AppEntryLaunchDecision<T>

    data class Restore<T : Any>(val pendingValues: List<T>) : AppEntryLaunchDecision<T>
}

internal class SingleUseAppEntryCoordinator<T : Any>(
    private val isCandidate: (T) -> Boolean,
    private val maxPendingEntries: Int = DEFAULT_MAX_PENDING_ENTRIES
) {
    private val entries = mutableListOf<PendingAppEntry<T>>()
    private var nextId = 0L
    private var claimedId: Long? = null
    private var activityIntentEntryId: Long? = null

    init {
        require(maxPendingEntries >= 2) { "maxPendingEntries must be at least 2" }
    }

    val pending: PendingAppEntry<T>?
        get() = entries.firstOrNull()

    fun observeActivityIntent(value: T): AppEntryOffer<T> {
        activityIntentEntryId = null
        if (!isCandidate(value)) {
            return AppEntryOffer.Ignored
        }

        if (entries.size >= maxPendingEntries) {
            val oldestUnclaimedIndex = entries.indexOfFirst { entry -> entry.id != claimedId }
            entries.removeAt(oldestUnclaimedIndex)
        }

        val entry = PendingAppEntry(id = nextEntryId(), value = value)
        entries += entry
        activityIntentEntryId = entry.id
        return AppEntryOffer.Accepted(entry)
    }

    fun restorePending(values: List<T>): PendingAppEntry<T>? {
        claimedId = null
        entries.clear()
        values.take(maxPendingEntries).forEach { restoredValue ->
            entries += PendingAppEntry(id = nextEntryId(), value = restoredValue)
        }
        activityIntentEntryId = entries.lastOrNull()?.id
        return pending
    }

    fun claim(id: Long): ClaimedAppEntry<T>? {
        val entry = pending?.takeIf { candidate -> candidate.id == id } ?: return null
        if (claimedId == id) {
            return null
        }

        claimedId = id
        val shouldNeutralize = activityIntentEntryId == id
        if (shouldNeutralize) {
            activityIntentEntryId = null
        }
        return ClaimedAppEntry(
            entry = entry,
            shouldNeutralizeActivityIntent = shouldNeutralize
        )
    }

    fun complete(id: Long): Boolean {
        if (pending?.id != id || claimedId != id) {
            return false
        }

        entries.removeAt(0)
        claimedId = null
        return true
    }

    fun restorableValues(): List<T> =
        entries
            .filterNot { entry -> claimedId == entry.id }
            .map { entry -> entry.value }

    private fun nextEntryId(): Long {
        nextId += 1
        return nextId
    }

    private companion object {
        private const val DEFAULT_MAX_PENDING_ENTRIES = 32
    }
}

internal fun <T : Any> decideAppEntryLaunch(
    hasSavedState: Boolean,
    savedPendingValues: List<T>,
    incomingValue: T
): AppEntryLaunchDecision<T> =
    if (!hasSavedState) {
        AppEntryLaunchDecision.Observe(incomingValue)
    } else {
        AppEntryLaunchDecision.Restore(savedPendingValues)
    }
