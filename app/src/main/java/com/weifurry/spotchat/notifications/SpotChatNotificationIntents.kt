package com.weifurry.spotchat.notifications

object SpotChatNotificationIntents {
    const val ACTION_OPEN_CONVERSATION = "com.weifurry.spotchat.action.OPEN_CONVERSATION"
    const val ACTION_REPLY = "com.weifurry.spotchat.action.REPLY"
    const val ACTION_QUICK_REPLY = "com.weifurry.spotchat.action.QUICK_REPLY"
    const val ACTION_MARK_READ = "com.weifurry.spotchat.action.MARK_READ"
    const val ACTION_MUTE_8H = "com.weifurry.spotchat.action.MUTE_8H"
    const val ACTION_NOTIFICATION_DISMISSED = "com.weifurry.spotchat.action.NOTIFICATION_DISMISSED"
    const val EXTRA_CONVERSATION_ID = "conversation_id"
    const val EXTRA_REMOTE_REPLY = "remote_reply"
    const val EXTRA_QUICK_REPLY_TEXT = "quick_reply_text"
    const val EXTRA_QUICK_REPLY_INDEX = "quick_reply_index"
    const val EXTRA_INTENT_TOKEN = "intent_token"
    const val EXTRA_ENTRY_EVENT_ID = "entry_event_id"

    val quickReplies: Array<CharSequence> = arrayOf("收到", "好", "稍等", "马上")

    fun handles(action: String?): Boolean =
        action == ACTION_OPEN_CONVERSATION ||
            action == ACTION_REPLY ||
            action == ACTION_QUICK_REPLY ||
            action == ACTION_MARK_READ ||
            action == ACTION_MUTE_8H ||
            action == ACTION_NOTIFICATION_DISMISSED
}
