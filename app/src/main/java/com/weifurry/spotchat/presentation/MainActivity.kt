package com.weifurry.spotchat.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    private val pendingNotificationIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingNotificationIntent.value = intent
        setContent {
            SpotChatApp(
                notificationIntent = pendingNotificationIntent.value,
                onNotificationIntentHandled = { handledIntent ->
                    if (pendingNotificationIntent.value === handledIntent) {
                        pendingNotificationIntent.value = null
                    }
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationIntent.value = intent
    }
}
