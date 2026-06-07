package com.weifurry.spotchat.notifications

object SpotChatNotificationIntents {
    const val ACTION_OPEN_CONVERSATION = "com.weifurry.spotchat.action.OPEN_CONVERSATION"
    const val ACTION_REPLY = "com.weifurry.spotchat.action.REPLY"
    const val EXTRA_CONVERSATION_ID = "conversation_id"
    const val EXTRA_REMOTE_REPLY = "remote_reply"
    const val EXTRA_INTENT_TOKEN = "intent_token"

    val quickReplies: Array<CharSequence> = arrayOf("收到", "好", "稍等", "马上")

    fun handles(action: String?): Boolean =
        action == ACTION_OPEN_CONVERSATION || action == ACTION_REPLY
}
