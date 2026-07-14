package com.weifurry.spotchat.entry

import java.util.UUID

internal data class AppEntryEventClaim(
    val accepted: Boolean,
    val retainedEventIds: List<String>
)

internal fun claimRecentAppEntryEvent(
    retainedEventIds: List<String>,
    rawEventId: String?,
    maxEntries: Int
): AppEntryEventClaim {
    require(maxEntries > 0) { "maxEntries must be positive" }

    val retained =
        retainedEventIds
            .mapNotNull(::normalizeAppEntryEventId)
            .distinct()
            .takeLast(maxEntries)
    if (rawEventId == null) {
        return AppEntryEventClaim(accepted = true, retainedEventIds = retained)
    }

    val eventId =
        normalizeAppEntryEventId(rawEventId)
            ?: return AppEntryEventClaim(accepted = false, retainedEventIds = retained)
    if (eventId in retained) {
        return AppEntryEventClaim(accepted = false, retainedEventIds = retained)
    }

    return AppEntryEventClaim(
        accepted = true,
        retainedEventIds = (retained + eventId).takeLast(maxEntries)
    )
}

internal fun newAppEntryEventId(): String = UUID.randomUUID().toString()

internal fun normalizeAppEntryEventId(rawEventId: String?): String? {
    val eventId = rawEventId?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val parsed = runCatching { UUID.fromString(eventId) }.getOrNull() ?: return null
    val normalized = parsed.toString()
    return normalized.takeIf { canonical -> canonical.equals(eventId, ignoreCase = true) }
}
