package com.weifurry.spotchat.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Person
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import com.weifurry.spotchat.R
import com.weifurry.spotchat.presentation.MainActivity

class SpotChatNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService(NotificationManager::class.java)
    private val tokenStore = SpotChatNotificationTokenStore(appContext)

    init {
        ensureChannel()
    }

    fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun showIncomingMessage(
        conversationId: String,
        conversationTitle: String,
        senderName: String,
        messageText: String,
        unreadCount: Int
    ) {
        if (!canPostNotifications()) {
            return
        }

        val notificationId = notificationId(conversationId)
        val openIntent =
            baseIntent(SpotChatNotificationIntents.ACTION_OPEN_CONVERSATION, conversationId)
        val replyIntent =
            baseIntent(SpotChatNotificationIntents.ACTION_REPLY, conversationId)
        val markReadIntent =
            baseIntent(SpotChatNotificationIntents.ACTION_MARK_READ, conversationId)
        val muteIntent =
            baseIntent(SpotChatNotificationIntents.ACTION_MUTE_8H, conversationId)
        val dismissedIntent =
            Intent(appContext, SpotChatNotificationDismissReceiver::class.java)
                .setAction(SpotChatNotificationIntents.ACTION_NOTIFICATION_DISMISSED)
                .putExtra(SpotChatNotificationIntents.EXTRA_CONVERSATION_ID, conversationId)
                .putExtra(SpotChatNotificationIntents.EXTRA_INTENT_TOKEN, tokenStore.token())
        val quickReplyActions =
            SpotChatNotificationIntents.quickReplies
                .take(MAX_INLINE_QUICK_REPLY_ACTIONS)
                .mapIndexed { index, reply ->
                    quickReplyAction(
                        conversationId = conversationId,
                        notificationId = notificationId,
                        index = index,
                        reply = reply
                    )
                }

        val openPendingIntent =
            PendingIntent.getActivity(
                appContext,
                notificationId,
                openIntent,
                pendingIntentFlags(mutable = false)
            )
        val replyPendingIntent =
            PendingIntent.getActivity(
                appContext,
                notificationId + REPLY_REQUEST_CODE_OFFSET,
                replyIntent,
                pendingIntentFlags(mutable = true)
            )
        val markReadPendingIntent =
            PendingIntent.getActivity(
                appContext,
                notificationId + MARK_READ_REQUEST_CODE_OFFSET,
                markReadIntent,
                pendingIntentFlags(mutable = false)
            )
        val mutePendingIntent =
            PendingIntent.getActivity(
                appContext,
                notificationId + MUTE_REQUEST_CODE_OFFSET,
                muteIntent,
                pendingIntentFlags(mutable = false)
            )
        val dismissedPendingIntent =
            PendingIntent.getBroadcast(
                appContext,
                notificationId + DISMISS_REQUEST_CODE_OFFSET,
                dismissedIntent,
                pendingIntentFlags(mutable = false)
            )
        val remoteInput =
            RemoteInput.Builder(SpotChatNotificationIntents.EXTRA_REMOTE_REPLY)
                .setLabel("回复")
                .setChoices(SpotChatNotificationIntents.quickReplies)
                .setAllowFreeFormInput(true)
                .build()
        val replyAction =
            actionBuilder("回复", replyPendingIntent)
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .setSemanticActionCompat(Notification.Action.SEMANTIC_ACTION_REPLY)
                .build()
        val markReadAction =
            actionBuilder("标为已读", markReadPendingIntent)
                .setSemanticActionCompat(Notification.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .build()
        val muteAction =
            actionBuilder("静音8小时", mutePendingIntent)
                .setSemanticActionCompat(Notification.Action.SEMANTIC_ACTION_MUTE)
                .build()

        val notificationText = messageText.take(MAX_NOTIFICATION_MESSAGE_CHARS)

        val notification =
            Notification.Builder(appContext, CHANNEL_MESSAGES)
                .setSmallIcon(R.drawable.ic_spotchat)
                .setContentTitle(conversationTitle)
                .setContentText(notificationText)
                .setStyle(messageStyle(conversationTitle, senderName, notificationText))
                .setContentIntent(openPendingIntent)
                .setDeleteIntent(dismissedPendingIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setNumber(unreadCount)
                .addAction(replyAction)
                .apply {
                    quickReplyActions.forEach(::addAction)
                    addAction(markReadAction)
                    addAction(muteAction)
                }
                .build()

        notificationManager.notify(notificationId, notification)
    }

    fun clearConversation(conversationId: String) {
        notificationManager.cancel(notificationId(conversationId))
    }

    fun isTrustedNotificationIntent(intent: Intent): Boolean =
        SpotChatNotificationIntents.handles(intent.action) &&
            tokenStore.isValid(intent.getStringExtra(SpotChatNotificationIntents.EXTRA_INTENT_TOKEN))

    private fun ensureChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_MESSAGES,
                "SpotChat 消息",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "收到加密聊天消息和通知快捷回复"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        notificationManager.createNotificationChannel(channel)
    }

    private fun messageStyle(
        conversationTitle: String,
        senderName: String,
        messageText: String
    ): Notification.MessagingStyle =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val me = Person.Builder().setName("我").build()
            val sender = Person.Builder().setName(senderName).build()
            Notification.MessagingStyle(me)
                .setConversationTitle(conversationTitle)
                .setGroupConversation(conversationTitle != senderName)
                .addMessage(
                    Notification.MessagingStyle.Message(
                        messageText,
                        System.currentTimeMillis(),
                        sender
                    )
                )
        } else {
            @Suppress("DEPRECATION")
            Notification.MessagingStyle("我")
                .setConversationTitle(conversationTitle)
                .addMessage(
                    @Suppress("DEPRECATION")
                    Notification.MessagingStyle.Message(
                        messageText,
                        System.currentTimeMillis(),
                        senderName
                    )
                )
        }

    private fun baseIntent(
        action: String,
        conversationId: String
    ): Intent =
        Intent(appContext, MainActivity::class.java)
            .setAction(action)
            .putExtra(SpotChatNotificationIntents.EXTRA_CONVERSATION_ID, conversationId)
            .putExtra(SpotChatNotificationIntents.EXTRA_INTENT_TOKEN, tokenStore.token())
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private fun quickReplyAction(
        conversationId: String,
        notificationId: Int,
        index: Int,
        reply: CharSequence
    ): Notification.Action {
        val quickReplyText = reply.toString()
        val quickReplyIntent =
            baseIntent(SpotChatNotificationIntents.ACTION_QUICK_REPLY, conversationId)
                .putExtra(SpotChatNotificationIntents.EXTRA_QUICK_REPLY_TEXT, quickReplyText)
                .putExtra(SpotChatNotificationIntents.EXTRA_QUICK_REPLY_INDEX, index)
        val quickReplyPendingIntent =
            PendingIntent.getActivity(
                appContext,
                notificationId + QUICK_REPLY_REQUEST_CODE_OFFSET + index,
                quickReplyIntent,
                pendingIntentFlags(mutable = false)
            )
        return actionBuilder(quickReplyText, quickReplyPendingIntent)
            .setSemanticActionCompat(Notification.Action.SEMANTIC_ACTION_REPLY)
            .build()
    }

    private fun actionBuilder(
        title: CharSequence,
        pendingIntent: PendingIntent
    ): Notification.Action.Builder =
        Notification.Action.Builder(
            Icon.createWithResource(appContext, R.drawable.ic_spotchat),
            title,
            pendingIntent
        )

    private fun Notification.Action.Builder.setSemanticActionCompat(
        semanticAction: Int
    ): Notification.Action.Builder =
        apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setSemanticAction(semanticAction)
            }
        }

    private fun pendingIntentFlags(mutable: Boolean): Int {
        val mutabilityFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (mutable) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_IMMUTABLE
                }
            } else {
                0
            }
        return PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
    }

    private fun notificationId(conversationId: String): Int =
        NOTIFICATION_ID_BASE + (conversationId.hashCode() and NOTIFICATION_ID_MASK)

    private companion object {
        private const val CHANNEL_MESSAGES = "spotchat_messages"
        private const val NOTIFICATION_ID_BASE = 42_000
        private const val NOTIFICATION_ID_MASK = 0x000F_FFFF
        private const val REPLY_REQUEST_CODE_OFFSET = 10_000
        private const val MARK_READ_REQUEST_CODE_OFFSET = 20_000
        private const val MUTE_REQUEST_CODE_OFFSET = 30_000
        private const val QUICK_REPLY_REQUEST_CODE_OFFSET = 40_000
        private const val DISMISS_REQUEST_CODE_OFFSET = 50_000
        private const val MAX_INLINE_QUICK_REPLY_ACTIONS = 2
        private const val MAX_NOTIFICATION_MESSAGE_CHARS = 160
    }
}
