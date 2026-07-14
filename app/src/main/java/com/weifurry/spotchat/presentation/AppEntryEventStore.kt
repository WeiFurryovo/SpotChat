package com.weifurry.spotchat.presentation

import android.annotation.SuppressLint
import android.content.Context
import com.weifurry.spotchat.entry.claimRecentAppEntryEvent

internal class AppEntryEventStore(context: Context) {
    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @SuppressLint("ApplySharedPref", "UseKtx")
    fun claim(rawEventId: String?): Boolean {
        if (rawEventId == null) {
            return true
        }

        synchronized(storeLock) {
            val claim =
                claimRecentAppEntryEvent(
                    retainedEventIds = storedEventIds(),
                    rawEventId = rawEventId,
                    maxEntries = MAX_RETAINED_EVENT_IDS
                )
            if (!claim.accepted) {
                return false
            }

            // The side effect runs only after its claim is durably recorded.
            return preferences
                .edit()
                .putString(KEY_EVENT_IDS, claim.retainedEventIds.joinToString(EVENT_ID_SEPARATOR))
                .commit()
        }
    }

    private fun storedEventIds(): List<String> =
        preferences
            .getString(KEY_EVENT_IDS, null)
            ?.split(EVENT_ID_SEPARATOR)
            .orEmpty()

    private companion object {
        private const val PREFERENCES_NAME = "spotchat_app_entry_events"
        private const val KEY_EVENT_IDS = "claimed_event_ids"
        private const val EVENT_ID_SEPARATOR = "\n"
        private const val MAX_RETAINED_EVENT_IDS = 128
        private val storeLock = Any()
    }
}
