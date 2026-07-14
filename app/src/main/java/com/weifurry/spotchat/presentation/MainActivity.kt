package com.weifurry.spotchat.presentation

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.weifurry.spotchat.notifications.SpotChatNotificationTokenStore

class MainActivity : ComponentActivity() {
    private val notificationTokenStore by lazy { SpotChatNotificationTokenStore(this) }
    private val entryCoordinator =
        SingleUseAppEntryCoordinator<Intent>(
            isCandidate = { intent ->
                resolveAppEntryIntent(
                    intent = intent,
                    isTokenValid = notificationTokenStore::isValid
                ) is AppEntryResolution.Accepted
            }
        )
    private val pendingAppEntry = mutableStateOf<PendingAppEntry<Intent>?>(null)
    private val eventStore by lazy { AppEntryEventStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val savedPendingIntents = savedInstanceState?.pendingAppEntryIntents().orEmpty()
        when (
            val decision =
                decideAppEntryLaunch(
                    hasSavedState = savedInstanceState != null,
                    savedPendingValues = savedPendingIntents,
                    incomingValue = intent
                )
        ) {
            is AppEntryLaunchDecision.Observe -> observeActivityIntent(decision.value)
            is AppEntryLaunchDecision.Restore -> {
                pendingAppEntry.value = entryCoordinator.restorePending(decision.pendingValues)
                setIntent(decision.pendingValues.lastOrNull() ?: neutralActivityIntent())
            }
        }

        setContent {
            SpotChatApp(
                pendingAppEntry = pendingAppEntry.value,
                claimAppEntry = ::claimAppEntry,
                claimAppEntryEvent = { entryIntent ->
                    eventStore.claim(appEntryEventId(entryIntent))
                },
                completeAppEntry = ::completeAppEntry
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        observeActivityIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelableArrayList(
            KEY_PENDING_APP_ENTRY_INTENTS,
            ArrayList(entryCoordinator.restorableValues())
        )
        super.onSaveInstanceState(outState)
    }

    private fun observeActivityIntent(intent: Intent) {
        when (val offer = entryCoordinator.observeActivityIntent(intent)) {
            AppEntryOffer.Ignored -> {
                if (isAppEntryCandidate(intent)) {
                    setIntent(neutralActivityIntent())
                }
            }

            is AppEntryOffer.Accepted -> {
                pendingAppEntry.value = entryCoordinator.pending
            }
        }
    }

    private fun claimAppEntry(id: Long): Intent? {
        val claim = entryCoordinator.claim(id) ?: return null
        if (claim.shouldNeutralizeActivityIntent) {
            setIntent(neutralActivityIntent())
        }
        return claim.entry.value
    }

    private fun completeAppEntry(id: Long) {
        if (entryCoordinator.complete(id)) {
            pendingAppEntry.value = entryCoordinator.pending
        }
    }

    private fun neutralActivityIntent(): Intent =
        Intent(Intent.ACTION_MAIN).apply {
            setClass(this@MainActivity, MainActivity::class.java)
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

    private fun Bundle.pendingAppEntryIntents(): ArrayList<Intent>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayList(KEY_PENDING_APP_ENTRY_INTENTS, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayList(KEY_PENDING_APP_ENTRY_INTENTS)
        }

    private companion object {
        private const val KEY_PENDING_APP_ENTRY_INTENTS = "pending_app_entry_intents"
    }
}
