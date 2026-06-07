package com.weifurry.spotchat.wear

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.weifurry.spotchat.presentation.MainActivity

class UnreadThreadsComplicationDataSourceService : ComplicationDataSourceService() {
    private val wearStateStore by lazy {
        SpotChatWearStateStore(this)
    }

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        listener.onComplicationData(buildComplicationData(wearStateStore.load()))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        buildComplicationData(
            WearChatSnapshot(
                conversations =
                    listOf(
                        WearConversationSummary(
                            id = "preview",
                            title = "SpotChat",
                            subtitle = "2 条未读",
                            unreadCount = 2,
                            updatedAtEpochMillis = System.currentTimeMillis()
                        )
                    ),
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        )

    private fun buildComplicationData(snapshot: WearChatSnapshot): ComplicationData {
        val count = snapshot.totalUnreadCount
        val shortText =
            if (count > 0) {
                count.coerceAtMost(99).toString()
            } else {
                "0"
            }
        val contentDescription =
            if (count > 0) {
                "SpotChat 有 $count 条未读消息"
            } else {
                "SpotChat 没有未读消息"
            }
        return ShortTextComplicationData.Builder(
            text = plainText(shortText),
            contentDescription = plainText(contentDescription)
        )
            .setTitle(plainText("SpotChat"))
            .setTapAction(openAppPendingIntent())
            .build()
    }

    private fun plainText(text: String): PlainComplicationText =
        PlainComplicationText.Builder(text).build()

    private fun openAppPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            COMPLICATION_OPEN_REQUEST_CODE,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            pendingIntentFlags()
        )

    private fun pendingIntentFlags(): Int {
        val mutabilityFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
        return PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
    }

    private companion object {
        private const val COMPLICATION_OPEN_REQUEST_CODE = 73_000
    }
}
