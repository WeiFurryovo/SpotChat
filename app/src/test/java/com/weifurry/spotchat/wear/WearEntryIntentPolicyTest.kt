package com.weifurry.spotchat.wear

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearEntryIntentPolicyTest {
    private val tokenValidator: (String?) -> Boolean = { token -> token == VALID_TOKEN }

    @Test
    fun ordinaryLaunchIsNotTreatedAsWearEntry() {
        assertEquals(
            WearEntryIntentResolution.NotWearEntry,
            resolveWearEntryIntent(WearEntryIntentFields(), tokenValidator)
        )
    }

    @Test
    fun recentChatsEntryRequiresValidToken() {
        val fields =
            WearEntryIntentFields(
                recentChatsOpen = true,
                token = VALID_TOKEN,
                conversationId = "group:nearby"
            )

        assertEquals(
            WearEntryIntentResolution.Accepted(
                WearEntryRequest.RecentChats("group:nearby")
            ),
            resolveWearEntryIntent(fields, tokenValidator)
        )
        assertEquals(
            WearEntryIntentResolution.Rejected,
            resolveWearEntryIntent(fields.copy(token = null), tokenValidator)
        )
        assertEquals(
            WearEntryIntentResolution.Rejected,
            resolveWearEntryIntent(fields.copy(token = "wrong"), tokenValidator)
        )
    }

    @Test
    fun quickVoiceEntryPreservesOptionalConversation() {
        assertEquals(
            WearEntryIntentResolution.Accepted(
                WearEntryRequest.QuickVoice(conversationId = null)
            ),
            resolveWearEntryIntent(
                WearEntryIntentFields(
                    quickVoiceOpen = true,
                    token = VALID_TOKEN
                ),
                tokenValidator
            )
        )
    }

    @Test
    fun quickTextReplyEntryPreservesPayload() {
        assertEquals(
            WearEntryIntentResolution.Accepted(
                WearEntryRequest.QuickTextReply(
                    conversationId = "direct:peer",
                    replyText = "马上到"
                )
            ),
            resolveWearEntryIntent(
                WearEntryIntentFields(
                    quickTextReplyOpen = true,
                    token = VALID_TOKEN,
                    conversationId = "direct:peer",
                    replyText = "马上到"
                ),
                tokenValidator
            )
        )
    }

    @Test
    fun conflictingMarkersAreRejectedEvenWithValidToken() {
        assertEquals(
            WearEntryIntentResolution.Rejected,
            resolveWearEntryIntent(
                WearEntryIntentFields(
                    recentChatsOpen = true,
                    quickVoiceOpen = true,
                    token = VALID_TOKEN
                ),
                tokenValidator
            )
        )
    }

    @Test
    fun everyProtectedWearEntryProducerAttachesToken() {
        val producers =
            listOf(
                listOf(
                    "RecentChatsTileService.kt",
                    "private fun openAppAction",
                    "private fun text",
                    "EXTRA_TILE_OPEN"
                ),
                listOf(
                    "QuickVoiceTileService.kt",
                    "private fun openVoiceAction",
                    "private fun text",
                    "EXTRA_VOICE_TILE_OPEN"
                ),
                listOf(
                    "QuickTextReplyTileService.kt",
                    "private fun openReplyAction",
                    "private fun text",
                    "EXTRA_TEXT_REPLY_TILE_OPEN"
                ),
                listOf(
                    "UnreadThreadsComplicationDataSourceService.kt",
                    "private fun openAppPendingIntent",
                    "private fun pendingIntentFlags",
                    "RecentChatsTileService.EXTRA_TILE_OPEN"
                )
            )

        producers.forEach { (fileName, startMarker, endMarker, entryMarker) ->
            val actionSource =
                wearSource(fileName)
                    .readText()
                    .substringAfter(startMarker)
                    .substringBefore(endMarker)
            assertTrue(
                "$fileName must mark its protected Wear entry",
                actionSource.contains(entryMarker)
            )
            assertTrue(
                "$fileName must attach an internal intent token",
                actionSource.contains("EXTRA_INTENT_TOKEN")
            )
        }
    }

    private fun wearSource(fileName: String): File {
        val userDirectory = checkNotNull(System.getProperty("user.dir"))
        var directory: File? = File(userDirectory).canonicalFile
        while (directory != null) {
            val candidates =
                listOf(
                    File(directory, "src/main/java/com/weifurry/spotchat/wear/$fileName"),
                    File(directory, "app/src/main/java/com/weifurry/spotchat/wear/$fileName")
                )
            candidates.firstOrNull(File::isFile)?.let { return it }
            directory = directory.parentFile
        }
        error("Unable to locate Wear source: $fileName")
    }

    private companion object {
        private const val VALID_TOKEN = "valid-token"
    }
}
