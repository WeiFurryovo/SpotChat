package com.weifurry.spotchat.notifications

import android.content.Context
import java.util.UUID

class SpotChatNotificationTokenStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun token(): String =
        synchronized(tokenLock) {
            prefs.getString(KEY_TOKEN, null)?.let { existingToken ->
                if (existingToken.isNotBlank()) {
                    return@synchronized existingToken
                }
            }
            val generatedToken = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_TOKEN, generatedToken).apply()
            generatedToken
        }

    fun isValid(token: String?): Boolean =
        token != null && token == token()

    private companion object {
        private const val PREFS_NAME = "spotchat_notification_tokens"
        private const val KEY_TOKEN = "intent_token"
        private val tokenLock = Any()
    }
}
