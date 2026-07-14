package com.weifurry.spotchat.presentation

import android.app.RemoteInput
import android.content.Intent
import com.weifurry.spotchat.notifications.SpotChatNotificationIntents
import com.weifurry.spotchat.wear.QuickTextReplyTileService
import com.weifurry.spotchat.wear.QuickVoiceTileService
import com.weifurry.spotchat.wear.RecentChatsTileService

internal fun resolveAppEntryIntent(
    intent: Intent,
    isTokenValid: (String?) -> Boolean
): AppEntryResolution =
    resolveAppEntryIntent(
        fields =
            AppEntryIntentFields(
                action = intent.action,
                token =
                    intent.getStringExtra(
                        SpotChatNotificationIntents.EXTRA_INTENT_TOKEN
                    ),
                conversationId =
                    intent.getStringExtra(
                        SpotChatNotificationIntents.EXTRA_CONVERSATION_ID
                    ),
                quickReplyText =
                    intent.getStringExtra(
                        SpotChatNotificationIntents.EXTRA_QUICK_REPLY_TEXT
                    ),
                recentChatsOpen =
                    intent.getBooleanExtra(
                        RecentChatsTileService.EXTRA_TILE_OPEN,
                        false
                    ),
                quickVoiceOpen =
                    intent.getBooleanExtra(
                        QuickVoiceTileService.EXTRA_VOICE_TILE_OPEN,
                        false
                    ),
                quickTextReplyOpen =
                    intent.getBooleanExtra(
                        QuickTextReplyTileService.EXTRA_TEXT_REPLY_TILE_OPEN,
                        false
                    ),
                tileReplyText =
                    intent.getStringExtra(
                        QuickTextReplyTileService.EXTRA_TILE_REPLY_TEXT
                    )
            ),
        isTokenValid = isTokenValid
    )

internal fun notificationRemoteReplyText(intent: Intent): String? =
    RemoteInput
        .getResultsFromIntent(intent)
        ?.getCharSequence(SpotChatNotificationIntents.EXTRA_REMOTE_REPLY)
        ?.toString()
