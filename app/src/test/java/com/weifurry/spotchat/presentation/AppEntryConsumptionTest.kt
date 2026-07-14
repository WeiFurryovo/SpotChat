package com.weifurry.spotchat.presentation

import com.weifurry.spotchat.entry.claimRecentAppEntryEvent
import com.weifurry.spotchat.entry.newAppEntryEventId
import com.weifurry.spotchat.entry.normalizeAppEntryEventId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppEntryConsumptionTest {
    @Test
    fun nonCandidateIsIgnoredWithoutCreatingPendingEntry() {
        val coordinator = coordinator()

        assertSame(AppEntryOffer.Ignored, coordinator.observeActivityIntent(LAUNCHER))
        assertNull(coordinator.pending)
        assertEquals(emptyList<String>(), coordinator.restorableValues())
    }

    @Test
    fun candidateCanBeClaimedAndCompletedOnlyOnce() {
        val coordinator = coordinator()
        val entry = coordinator.accept(ENTRY_A)

        assertEquals(1L, entry.id)
        assertEquals(entry, coordinator.pending)
        assertEquals(listOf(ENTRY_A), coordinator.restorableValues())
        assertFalse(coordinator.complete(entry.id))
        assertEquals(entry, coordinator.pending)
        assertNull(coordinator.claim(entry.id + 1))

        val claim = coordinator.claim(entry.id)

        assertEquals(entry, claim?.entry)
        assertEquals(true, claim?.shouldNeutralizeActivityIntent)
        assertEquals(emptyList<String>(), coordinator.restorableValues())
        assertNull(coordinator.claim(entry.id))
        assertFalse(coordinator.complete(entry.id + 1))
        assertTrue(coordinator.complete(entry.id))
        assertNull(coordinator.pending)
        assertEquals(emptyList<String>(), coordinator.restorableValues())
        assertFalse(coordinator.complete(entry.id))
    }

    @Test
    fun candidatesQueueInArrivalOrderWithoutDroppingEarlierCommands() {
        val coordinator = coordinator()
        val oldEntry = coordinator.accept(ENTRY_A)
        val newEntry = coordinator.accept(ENTRY_B)

        assertNotEquals(oldEntry.id, newEntry.id)
        assertEquals(oldEntry, coordinator.pending)
        assertEquals(listOf(ENTRY_A, ENTRY_B), coordinator.restorableValues())
        assertNull(coordinator.claim(newEntry.id))
        val oldClaim = coordinator.claim(oldEntry.id)
        assertEquals(oldEntry, oldClaim?.entry)
        assertEquals(false, oldClaim?.shouldNeutralizeActivityIntent)
        assertTrue(coordinator.complete(oldEntry.id))
        assertEquals(newEntry, coordinator.pending)
        val newClaim = coordinator.claim(newEntry.id)
        assertEquals(newEntry, newClaim?.entry)
        assertEquals(true, newClaim?.shouldNeutralizeActivityIntent)
    }

    @Test
    fun staleCompletionCannotClearCandidateThatArrivedDuringExecution() {
        val coordinator = coordinator()
        val oldEntry = coordinator.accept(ENTRY_A)
        assertNotNull(coordinator.claim(oldEntry.id))

        val newEntry = coordinator.accept(ENTRY_B)

        assertFalse(coordinator.complete(newEntry.id))
        assertTrue(coordinator.complete(oldEntry.id))
        assertEquals(newEntry, coordinator.pending)
        val newClaim = coordinator.claim(newEntry.id)
        assertFalse(coordinator.complete(oldEntry.id))
        assertEquals(newEntry, coordinator.pending)
        assertEquals(newEntry, newClaim?.entry)
        assertEquals(true, newClaim?.shouldNeutralizeActivityIntent)
    }

    @Test
    fun launcherDoesNotReplacePendingEntryOrGetNeutralizedByItsClaim() {
        val coordinator = coordinator()
        val entry = coordinator.accept(ENTRY_A)

        assertSame(AppEntryOffer.Ignored, coordinator.observeActivityIntent(LAUNCHER))
        assertEquals(entry, coordinator.pending)

        val claim = coordinator.claim(entry.id)

        assertEquals(entry, claim?.entry)
        assertEquals(false, claim?.shouldNeutralizeActivityIntent)
    }

    @Test
    fun restoringPendingValuesCreatesAClaimableFifoQueue() {
        val coordinator = coordinator()

        val restored = coordinator.restorePending(listOf(ENTRY_A, ENTRY_B))

        assertEquals(restored, coordinator.pending)
        assertEquals(listOf(ENTRY_A, ENTRY_B), coordinator.restorableValues())
        val claim = coordinator.claim(requireNotNull(restored).id)
        assertEquals(restored, claim?.entry)
        assertEquals(false, claim?.shouldNeutralizeActivityIntent)
        assertEquals(listOf(ENTRY_B), coordinator.restorableValues())
        assertTrue(coordinator.complete(restored.id))
        assertEquals(ENTRY_B, coordinator.pending?.value)
        assertEquals(
            true,
            coordinator.claim(requireNotNull(coordinator.pending).id)?.shouldNeutralizeActivityIntent
        )
    }

    @Test
    fun restoringEmptyListClearsExistingPendingAndClaimState() {
        val coordinator = coordinator()
        val entry = coordinator.accept(ENTRY_A)
        assertNotNull(coordinator.claim(entry.id))

        assertNull(coordinator.restorePending(emptyList()))
        assertNull(coordinator.pending)
        assertEquals(emptyList<String>(), coordinator.restorableValues())
        assertNull(coordinator.claim(entry.id))
        assertFalse(coordinator.complete(entry.id))
    }

    @Test
    fun restoredEntriesReceiveFreshIdsThatRejectPreRestoreTickets() {
        val coordinator = coordinator()
        val oldEntry = coordinator.accept(ENTRY_A)

        val restored = requireNotNull(coordinator.restorePending(listOf(ENTRY_A)))

        assertNotEquals(oldEntry.id, restored.id)
        assertNull(coordinator.claim(oldEntry.id))
        assertFalse(coordinator.complete(oldEntry.id))
        assertEquals(restored, coordinator.pending)
    }

    @Test
    fun boundedQueueRetainsClaimedWorkAndNewestCandidates() {
        val coordinator =
            SingleUseAppEntryCoordinator<String>(
                isCandidate = { value -> value.startsWith("entry:") },
                maxPendingEntries = 2
            )
        val claimed = coordinator.accept(ENTRY_A)
        assertNotNull(coordinator.claim(claimed.id))
        coordinator.accept(ENTRY_B)

        coordinator.accept(ENTRY_C)

        assertEquals(listOf(ENTRY_C), coordinator.restorableValues())
        assertTrue(coordinator.complete(claimed.id))
        assertEquals(ENTRY_C, coordinator.pending?.value)
    }

    @Test
    fun boundedQueueWithoutAClaimEvictsTheOldestEntry() {
        val coordinator =
            SingleUseAppEntryCoordinator<String>(
                isCandidate = { value -> value.startsWith("entry:") },
                maxPendingEntries = 2
            )
        val oldest = coordinator.accept(ENTRY_A)
        coordinator.accept(ENTRY_B)

        coordinator.accept(ENTRY_C)

        assertEquals(listOf(ENTRY_B, ENTRY_C), coordinator.restorableValues())
        assertNull(coordinator.claim(oldest.id))
        assertFalse(coordinator.complete(oldest.id))
    }

    @Test
    fun launcherStopsEveryQueuedEntryFromNeutralizingTheCurrentIntent() {
        val coordinator = coordinator()
        val first = coordinator.accept(ENTRY_A)
        val second = coordinator.accept(ENTRY_B)

        assertSame(AppEntryOffer.Ignored, coordinator.observeActivityIntent(LAUNCHER))

        assertEquals(false, coordinator.claim(first.id)?.shouldNeutralizeActivityIntent)
        assertTrue(coordinator.complete(first.id))
        assertEquals(false, coordinator.claim(second.id)?.shouldNeutralizeActivityIntent)
    }

    @Test(expected = IllegalArgumentException::class)
    fun coordinatorRejectsCapacityBelowTwo() {
        SingleUseAppEntryCoordinator<String>(
            isCandidate = { true },
            maxPendingEntries = 1
        )
    }

    @Test
    fun freshLaunchAlwaysObservesIncomingValue() {
        assertEquals(
            AppEntryLaunchDecision.Observe(ENTRY_A),
            decideAppEntryLaunch(
                hasSavedState = false,
                savedPendingValues = listOf(ENTRY_B),
                incomingValue = ENTRY_A
            )
        )
    }

    @Test
    fun consumedRestorationDoesNotFallBackToOriginalIncomingEntry() {
        assertEquals(
            AppEntryLaunchDecision.Restore<String>(pendingValues = emptyList()),
            decideAppEntryLaunch(
                hasSavedState = true,
                savedPendingValues = emptyList(),
                incomingValue = ENTRY_A
            )
        )
    }

    @Test
    fun pendingRestorationUsesTheSavedFifo() {
        assertEquals(
            AppEntryLaunchDecision.Restore(listOf(ENTRY_A, ENTRY_B)),
            decideAppEntryLaunch(
                hasSavedState = true,
                savedPendingValues = listOf(ENTRY_A, ENTRY_B),
                incomingValue = ENTRY_B
            )
        )
    }

    @Test
    fun restoredStateNeverTreatsTheActivityRecordsOriginalIntentAsNew() {
        assertEquals(
            AppEntryLaunchDecision.Restore(listOf(ENTRY_B)),
            decideAppEntryLaunch(
                hasSavedState = true,
                savedPendingValues = listOf(ENTRY_B),
                incomingValue = ENTRY_A
            )
        )
    }

    @Test
    fun eventIdsAreTrimmedLowercasedAndStrictlyCanonical() {
        assertEquals(EVENT_A, normalizeAppEntryEventId("  ${EVENT_A.uppercase()}  "))
        assertNull(normalizeAppEntryEventId(null))
        assertNull(normalizeAppEntryEventId("   "))
        assertNull(normalizeAppEntryEventId("not-a-uuid"))
        assertNull(normalizeAppEntryEventId("1-1-1-1-1"))

        val generated = newAppEntryEventId()
        assertEquals(generated, normalizeAppEntryEventId(generated))
    }

    @Test
    fun duplicateEventIsRejectedAfterNormalization() {
        val claim =
            claimRecentAppEntryEvent(
                retainedEventIds = listOf(" ${EVENT_A.uppercase()} ", EVENT_A),
                rawEventId = EVENT_A,
                maxEntries = 4
            )

        assertFalse(claim.accepted)
        assertEquals(listOf(EVENT_A), claim.retainedEventIds)
    }

    @Test
    fun eventLedgerIsNormalizedDistinctAndEvictsOldestEntries() {
        val claim =
            claimRecentAppEntryEvent(
                retainedEventIds =
                    listOf(
                        "invalid",
                        EVENT_A,
                        EVENT_A.uppercase(),
                        EVENT_B,
                        EVENT_C
                    ),
                rawEventId = EVENT_D,
                maxEntries = 3
            )

        assertTrue(claim.accepted)
        assertEquals(listOf(EVENT_B, EVENT_C, EVENT_D), claim.retainedEventIds)

        val reclaimedEvicted =
            claimRecentAppEntryEvent(
                retainedEventIds = claim.retainedEventIds,
                rawEventId = EVENT_A,
                maxEntries = 3
            )
        assertTrue(reclaimedEvicted.accepted)
        assertEquals(listOf(EVENT_C, EVENT_D, EVENT_A), reclaimedEvicted.retainedEventIds)
    }

    @Test
    fun legacyNullEventIdIsAcceptedWithoutAddingLedgerEntry() {
        val claim =
            claimRecentAppEntryEvent(
                retainedEventIds = listOf("invalid", EVENT_A, EVENT_A.uppercase(), EVENT_B),
                rawEventId = null,
                maxEntries = 4
            )

        assertTrue(claim.accepted)
        assertEquals(listOf(EVENT_A, EVENT_B), claim.retainedEventIds)
    }

    @Test
    fun blankAndInvalidNonNullEventIdsFailClosed() {
        listOf("", "   ", "not-a-uuid").forEach { eventId ->
            val claim =
                claimRecentAppEntryEvent(
                    retainedEventIds = listOf(EVENT_A),
                    rawEventId = eventId,
                    maxEntries = 4
                )

            assertFalse(claim.accepted)
            assertEquals(listOf(EVENT_A), claim.retainedEventIds)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun eventLedgerRejectsNonPositiveCapacity() {
        claimRecentAppEntryEvent(
            retainedEventIds = emptyList(),
            rawEventId = EVENT_A,
            maxEntries = 0
        )
    }

    private fun coordinator(): SingleUseAppEntryCoordinator<String> =
        SingleUseAppEntryCoordinator(isCandidate = { value -> value.startsWith("entry:") })

    private fun SingleUseAppEntryCoordinator<String>.accept(value: String): PendingAppEntry<String> =
        (observeActivityIntent(value) as AppEntryOffer.Accepted).entry

    private companion object {
        private const val LAUNCHER = "launcher"
        private const val ENTRY_A = "entry:a"
        private const val ENTRY_B = "entry:b"
        private const val ENTRY_C = "entry:c"
        private const val EVENT_A = "00000000-0000-0000-0000-000000000001"
        private const val EVENT_B = "00000000-0000-0000-0000-000000000002"
        private const val EVENT_C = "00000000-0000-0000-0000-000000000003"
        private const val EVENT_D = "00000000-0000-0000-0000-000000000004"
    }
}
