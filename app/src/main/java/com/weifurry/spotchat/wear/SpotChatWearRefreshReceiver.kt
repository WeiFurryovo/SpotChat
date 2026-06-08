package com.weifurry.spotchat.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SpotChatWearRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?
    ) {
        if (!handles(intent?.action)) {
            return
        }
        SpotChatWearStateStore(context).requestSurfaceUpdates()
    }

    private fun handles(action: String?): Boolean =
        action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_POWER_DISCONNECTED ||
            action == Intent.ACTION_BOOT_COMPLETED
}
