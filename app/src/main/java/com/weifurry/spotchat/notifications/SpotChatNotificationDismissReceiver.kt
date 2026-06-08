package com.weifurry.spotchat.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.weifurry.spotchat.wear.SpotChatWearStateStore

class SpotChatNotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?
    ) {
        if (intent?.action != SpotChatNotificationIntents.ACTION_NOTIFICATION_DISMISSED) {
            return
        }
        if (!SpotChatNotifier(context).isTrustedNotificationIntent(intent)) {
            return
        }

        val conversationId =
            intent.getStringExtra(SpotChatNotificationIntents.EXTRA_CONVERSATION_ID) ?: return
        SpotChatWearStateStore(context).clearConversationAlerts(conversationId)
    }
}
