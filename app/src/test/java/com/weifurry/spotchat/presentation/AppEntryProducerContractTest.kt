package com.weifurry.spotchat.presentation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AppEntryProducerContractTest {
    @Test
    fun mainActivityAuthenticatesCandidatesBeforeFifoAdmission() {
        val source = productionSource("presentation/MainActivity.kt").readText()
        val coordinatorSetup =
            source
                .substringAfter("private val entryCoordinator")
                .substringBefore("private val pendingAppEntry")
        val ignoredOffer =
            source
                .substringAfter("AppEntryOffer.Ignored ->")
                .substringBefore("is AppEntryOffer.Accepted")

        assertTrue(coordinatorSetup.contains("resolveAppEntryIntent"))
        assertTrue(coordinatorSetup.contains("notificationTokenStore::isValid"))
        assertTrue(ignoredOffer.contains("isAppEntryCandidate(intent)"))
        assertTrue(ignoredOffer.contains("neutralActivityIntent()"))
    }

    @Test
    fun notificationActivityActionsCarryFreshEventIds() {
        val source = productionSource("notifications/SpotChatNotifier.kt").readText()
        val baseIntent =
            source
                .substringAfter("private fun baseIntent(")
                .substringBefore("private fun quickReplyAction(")

        assertTrue(baseIntent.contains("SpotChatNotificationIntents.EXTRA_ENTRY_EVENT_ID"))
        assertTrue(baseIntent.contains("newAppEntryEventId()"))
        listOf(
            "ACTION_REPLY",
            "ACTION_QUICK_REPLY",
            "ACTION_MARK_READ",
            "ACTION_MUTE_8H"
        ).forEach { action ->
            assertTrue(
                "$action must use the event-producing base Intent",
                source.contains("baseIntent(SpotChatNotificationIntents.$action")
            )
        }
    }

    @Test
    fun wearQuickReplyCarriesAnEventIdForTheRenderedAction() {
        val source = productionSource("wear/QuickTextReplyTileService.kt").readText()
        val replyExtras =
            source
                .substringAfter("if (replyText != null)")
                .substringBefore("EXTRA_TILE_REPLY_TEXT")

        assertTrue(replyExtras.contains("SpotChatNotificationIntents.EXTRA_ENTRY_EVENT_ID"))
        assertTrue(replyExtras.contains("newAppEntryEventId()"))
    }

    private fun productionSource(relativePath: String): File {
        var directory: File? = File(System.getProperty("user.dir")).canonicalFile
        while (directory != null) {
            listOf(
                File(directory, "src/main/java/com/weifurry/spotchat/$relativePath"),
                File(directory, "app/src/main/java/com/weifurry/spotchat/$relativePath")
            ).firstOrNull(File::isFile)?.let { source -> return source }
            directory = directory.parentFile
        }
        error("Unable to locate production source: $relativePath")
    }
}
