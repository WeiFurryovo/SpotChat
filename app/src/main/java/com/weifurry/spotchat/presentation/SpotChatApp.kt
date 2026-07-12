package com.weifurry.spotchat.presentation

import android.Manifest
import android.app.Activity
import android.app.RemoteInput
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.SwipeToDismissValue
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.WearableRemoteInputExtender
import com.weifurry.spotchat.R
import com.weifurry.spotchat.crypto.IdentityStore
import com.weifurry.spotchat.crypto.SpotChatCrypto
import com.weifurry.spotchat.domain.AuthenticatedPayloadDecodingException
import com.weifurry.spotchat.domain.ChatStateLoadResult
import com.weifurry.spotchat.domain.EncryptedChatStateStore
import com.weifurry.spotchat.domain.PersistedChatMessage
import com.weifurry.spotchat.domain.PersistedChatMessageKind
import com.weifurry.spotchat.domain.PersistedChatState
import com.weifurry.spotchat.domain.PersistedConversationDraft
import com.weifurry.spotchat.domain.PersistedDeliveryState
import com.weifurry.spotchat.domain.PersistedOutgoingEnvelope
import com.weifurry.spotchat.domain.PersistedQuotedMessage
import com.weifurry.spotchat.domain.PersistedTextOutboxMessage
import com.weifurry.spotchat.domain.PersistedVoiceOutboxMessage
import com.weifurry.spotchat.domain.DuplicateMessageException
import com.weifurry.spotchat.domain.ProfileSettings
import com.weifurry.spotchat.domain.ProfileStore
import com.weifurry.spotchat.domain.SqliteReplayProtection
import com.weifurry.spotchat.domain.StoredTrustedPeer
import com.weifurry.spotchat.domain.SpotChatEngine
import com.weifurry.spotchat.domain.SessionCoordinator
import com.weifurry.spotchat.domain.SessionCoordinator.ConfirmationMatch
import com.weifurry.spotchat.domain.TrustedPeer
import com.weifurry.spotchat.domain.TrustedPeerStore
import com.weifurry.spotchat.notifications.SpotChatNotificationIntents
import com.weifurry.spotchat.notifications.SpotChatNotificationTokenStore
import com.weifurry.spotchat.notifications.SpotChatNotifier
import com.weifurry.spotchat.presentation.theme.SpotChatTheme
import com.weifurry.spotchat.protocol.ChatCodec
import com.weifurry.spotchat.protocol.CHAT_PAYLOAD_KIND_DIRECT
import com.weifurry.spotchat.protocol.CHAT_PAYLOAD_KIND_GROUP
import com.weifurry.spotchat.protocol.ChatPayload
import com.weifurry.spotchat.protocol.ChatPayloadCodec
import com.weifurry.spotchat.protocol.MAX_CHAT_PAYLOAD_TEXT_CHARS
import com.weifurry.spotchat.protocol.MAX_QUOTED_MESSAGE_CHARS
import com.weifurry.spotchat.protocol.NEARBY_GROUP_CONVERSATION_ID
import com.weifurry.spotchat.protocol.QuotedMessage
import com.weifurry.spotchat.protocol.DeliveryReceiptStatus
import com.weifurry.spotchat.protocol.EncryptedChatMessage
import com.weifurry.spotchat.protocol.PacketKind
import com.weifurry.spotchat.protocol.PeerHello
import com.weifurry.spotchat.protocol.WirePacket
import com.weifurry.spotchat.transport.requiredBluetoothRuntimePermissions
import com.weifurry.spotchat.transport.TransportEvent
import com.weifurry.spotchat.transport.TransportKind
import com.weifurry.spotchat.transport.TransportManager
import com.weifurry.spotchat.transport.TransportPeer
import com.weifurry.spotchat.voice.RecordedVoiceMessage
import com.weifurry.spotchat.voice.SpotChatVoiceRecorder
import com.weifurry.spotchat.voice.VoicePlaybackCache
import com.weifurry.spotchat.wear.QuickVoiceTileService
import com.weifurry.spotchat.wear.QuickTextReplyTileService
import com.weifurry.spotchat.wear.RecentChatsTileService
import com.weifurry.spotchat.wear.SpotChatWearStateStore
import com.weifurry.spotchat.wear.WearChatSnapshot
import com.weifurry.spotchat.wear.WearConversationSummary
import com.weifurry.spotchat.wear.WearEntryIntentFields
import com.weifurry.spotchat.wear.WearEntryIntentResolution
import com.weifurry.spotchat.wear.WearEntryRequest
import com.weifurry.spotchat.wear.resolveWearEntryIntent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class TransportMode(
    val label: String,
    val icon: ImageVector,
    val kind: TransportKind
) {
    Lan("局域网", Icons.Filled.Lan, TransportKind.LAN),
    Bluetooth("蓝牙", Icons.Filled.Bluetooth, TransportKind.BLUETOOTH),
    Relay("中继", Icons.Filled.Sync, TransportKind.RELAY)
}

private enum class DeliveryState(
    val label: String
) {
    Received("收到"),
    Waiting("待确认"),
    Sending("发送中"),
    Sent("已发送"),
    Delivered("已送达"),
    Read("已读"),
    Failed("失败"),
    System("状态")
}

private enum class AppSurface {
    ConversationList,
    ArchivedChats,
    Chat,
    ChatInfo,
    MessageActions,
    ReactionUsers,
    VoicePlayback,
    MessageSearch,
    MuteSettings,
    DisappearingSettings,
    GroupMembers,
    ChatContentMessages,
    StarredMessages,
    ForwardMessage,
    SecurityCheck,
    EntryRecovery,
    Profile
}

internal data class ConversationDraft(
    val text: String,
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)

private data class WearEntryRecoveryState(
    val source: String,
    val targetConversationId: String?,
    val actionLabel: String
)

private enum class WearEntryAction(
    val label: String,
    val ctaLabel: String
) {
    OpenChat("打开聊天", "知道了"),
    Reply("回复", "输入"),
    QuickReply("快捷回复", "输入"),
    Voice("开始语音", "录音")
}

private data class WearEntryContext(
    val id: Long,
    val conversationId: String,
    val source: String,
    val action: WearEntryAction,
    val unreadCount: Int,
    val latestPreview: String
)

private data class WatchActionConfirmation(
    val id: Long,
    val title: String,
    val detail: String
)

internal enum class ConversationKind(
    val label: String
) {
    Direct("私聊"),
    Group("群聊")
}

private enum class ContentMessageFilter(
    val label: String
) {
    All("全部"),
    Voice("语音"),
    Forwarded("转发"),
    Quoted("引用"),
    Reacted("回应"),
    Disappearing("限时"),
    Links("链接")
}

private enum class GroupMemberFilter(
    val label: String
) {
    All("全部"),
    Online("在线"),
    Recent("最近"),
    Offline("离线")
}

private enum class MutePreset(
    val label: String,
    val durationMs: Long?
) {
    EightHours("8小时", 28_800_000L),
    OneWeek("1周", 604_800_000L),
    Always("始终", null)
}

private data class MutedConversation(
    val preset: MutePreset,
    val untilEpochMillis: Long?
) {
    fun isActive(nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        untilEpochMillis == null || untilEpochMillis > nowEpochMillis
}

private enum class ChatMessageKind {
    Text,
    Voice
}

private enum class VoicePlaybackSpeed(
    val label: String,
    val speed: Float
) {
    Normal("1x", 1f),
    Fast("1.5x", 1.5f),
    Faster("2x", 2f);

    fun next(): VoicePlaybackSpeed =
        entries[(ordinal + 1) % entries.size]
}

internal enum class DisappearingMessageMode(
    val label: String,
    val durationMs: Long?,
    val profileKey: String
) {
    Off("关闭", null, ProfileStore.DEFAULT_DISAPPEARING_MODE),
    OneMinute("1分钟", 60_000L, "one_minute"),
    OneHour("1小时", 3_600_000L, "one_hour"),
    OneDay("24小时", 86_400_000L, "one_day");

    fun next(): DisappearingMessageMode =
        entries[(ordinal + 1) % entries.size]
}

private data class DefaultAvatar(
    val id: String,
    val background: Color,
    val foreground: Color,
    @param:DrawableRes val imageRes: Int? = null
)

private data class ChatBubble(
    val text: String,
    val mine: Boolean,
    val encrypted: Boolean,
    val timestamp: String,
    val senderName: String? = null,
    val senderFingerprint: String? = null,
    val messageId: String? = null,
    val receiptMessageId: String? = null,
    val deliveryState: DeliveryState = DeliveryState.Received,
    val kind: ChatMessageKind = ChatMessageKind.Text,
    val quotedMessage: QuotedMessage? = null,
    val voiceDurationMs: Long? = null,
    val voiceAudioBytes: ByteArray? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val expiresAtEpochMillis: Long? = null,
    val reactions: Map<String, String> = emptyMap(),
    val forwarded: Boolean = false,
    val forwardCount: Int = 0
)

private data class ReactionChoice(
    val code: String,
    val label: String
)

private fun ChatBubble.stableStarId(): String =
    messageId ?: "${timestamp}:${senderName.orEmpty()}:$mine:${kind.name}:${previewText()}"

internal data class ChatConversation(
    val id: String,
    val kind: ConversationKind,
    val title: String,
    val subtitle: String,
    val peerFingerprint: String? = null,
    val memberFingerprints: List<String> = emptyList(),
    val themeColor: Color? = null
)

private data class MessageReceiptSummary(
    val expectedCount: Int,
    val deliveredCount: Int,
    val readCount: Int,
    val deliveredNames: List<String>,
    val readNames: List<String>,
    val undeliveredNames: List<String>,
    val unreadNames: List<String>
)

private data class ReactionDetail(
    val senderFingerprint: String,
    val senderName: String,
    val reactionLabel: String
)

private data class ConversationContentSummary(
    val voiceCount: Int,
    val forwardedCount: Int,
    val quotedCount: Int,
    val reactedCount: Int,
    val disappearingCount: Int,
    val linkCount: Int
) {
    val hasContent: Boolean
        get() =
            voiceCount +
                forwardedCount +
                quotedCount +
                reactedCount +
                disappearingCount +
                linkCount > 0
}

private data class OutgoingMessageRef(
    val conversationId: String,
    val displayMessageId: String,
    val recipientFingerprint: String,
    val expectedRecipients: Set<String>,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

private data class PendingOutboundMessage(
    val conversationId: String,
    val text: String,
    val displayMessageId: String,
    val remainingTargetFingerprints: List<String>,
    val quotedMessage: QuotedMessage? = null,
    val forwarded: Boolean = false,
    val forwardCount: Int = 0
)

private data class PendingOutboundVoiceMessage(
    val conversationId: String,
    val displayMessageId: String,
    val remainingTargetFingerprints: List<String>,
    val durationMs: Long,
    val audioBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PendingOutboundVoiceMessage) return false

        return conversationId == other.conversationId &&
            displayMessageId == other.displayMessageId &&
            remainingTargetFingerprints == other.remainingTargetFingerprints &&
            durationMs == other.durationMs &&
            audioBytes.contentEquals(other.audioBytes)
    }

    override fun hashCode(): Int {
        var result = conversationId.hashCode()
        result = 31 * result + displayMessageId.hashCode()
        result = 31 * result + remainingTargetFingerprints.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + audioBytes.contentHashCode()
        return result
    }
}

private data class PendingDirectReply(
    val conversationId: String,
    val title: String
)

private data class PendingMessageEdit(
    val conversationId: String,
    val messageId: String
)

private fun ChatBubble.canRetry(): Boolean =
    mine &&
        messageId != null &&
        (deliveryState == DeliveryState.Waiting || deliveryState == DeliveryState.Failed) &&
        when (kind) {
            ChatMessageKind.Text -> text.isNotBlank()
            ChatMessageKind.Voice -> voiceDurationMs != null && voiceAudioBytes != null
        }

private fun invalidAuthenticatedPayload(
    encryptedMessage: EncryptedChatMessage,
    reason: String
): Nothing =
    throw AuthenticatedPayloadDecodingException(
        messageId = encryptedMessage.messageId,
        senderFingerprint = encryptedMessage.senderFingerprint,
        cause = IllegalArgumentException(reason)
    )

private fun ChatPayload.validateAuthenticatedIncoming(
    encryptedMessage: EncryptedChatMessage
) {
    try {
        ChatPayloadCodec.validateIncoming(this)
    } catch (error: IllegalArgumentException) {
        invalidAuthenticatedPayload(
            encryptedMessage = encryptedMessage,
            reason = error.message ?: "Invalid chat payload"
        )
    }
}

private val defaultAvatars =
    listOf(
        DefaultAvatar("initial", Color(0xFF6CE5D4), Color(0xFF003733)),
        DefaultAvatar("mira", Color(0xFFB6E3F4), Color.White, R.drawable.avatar_mira),
        DefaultAvatar("nova", Color(0xFFC0AEDE), Color.White, R.drawable.avatar_nova),
        DefaultAvatar("kiki", Color(0xFFFFD5DC), Color.White, R.drawable.avatar_kiki),
        DefaultAvatar("echo", Color(0xFFFFDFBF), Color.White, R.drawable.avatar_echo),
        DefaultAvatar("zed", Color(0xFFD1D4F9), Color.White, R.drawable.avatar_zed)
    )
private const val PROFILE_AVATARS_PER_ROW = 3
private const val CUSTOM_MESSAGE_REMOTE_INPUT_KEY = "spotchat_custom_message"
private const val SEARCH_MESSAGE_REMOTE_INPUT_KEY = "spotchat_search_message"
private const val ALIAS_REMOTE_INPUT_KEY = "spotchat_alias"
private const val GROUP_ABOUT_REMOTE_INPUT_KEY = "spotchat_group_about"
private const val MAX_CUSTOM_MESSAGE_CHARS = MAX_CHAT_PAYLOAD_TEXT_CHARS
private const val MAX_SEARCH_QUERY_CHARS = 48
private const val MAX_GROUP_ABOUT_CHARS = 48
private const val MAX_SEARCH_RESULTS = 12
private const val MAX_STARRED_RESULTS = 24
private const val MAX_NOTIFICATION_CONFIRMATION_PREVIEW_CHARS = 24
private const val DISAPPEARING_SWEEP_INTERVAL_MS = 15_000L
private const val MUTE_SWEEP_INTERVAL_MS = 60_000L
private const val ACTION_CONFIRMATION_VISIBLE_MS = 1_600L
private const val WEAR_ENTRY_CONTEXT_VISIBLE_MS = 4_000L
private const val NEARBY_GROUP_TITLE = "附近群聊"
private const val DIRECT_CONVERSATION_PREFIX = "direct:"
private const val FREQUENTLY_FORWARDED_THRESHOLD = 4
private const val OUTGOING_ENVELOPE_TTL_MS = 8L * 24L * 60L * 60L * 1000L
private val customMessageQuickChoices = arrayOf("收到", "马上到", "稍后联系")
private val reactionChoices =
    listOf(
        ReactionChoice("like", "赞"),
        ReactionChoice("love", "爱心"),
        ReactionChoice("laugh", "笑"),
        ReactionChoice("ok", "收到")
    )
private val chatGreen = Color(0xFF27D7B4)
private val chatGreenDark = Color(0xFF0F3835)
private val chatBlue = Color(0xFF7AA7FF)
private val chatAmber = Color(0xFFE8C75D)
private val chatRose = Color(0xFFFF7B98)
private val chatWallpaper = Color(0xFF07090D)
private val chatIncoming = Color(0xFF20262B)
private val chatRowMuted = Color(0xFFA7B3B8)
private val chatSurface = Color(0xFF111820)
private val chatSurfaceHigh = Color(0xFF1A2430)
private val chatDivider = Color(0xFF2A3540)
private val conversationThemePalette =
    listOf(
        Color(0xFF53BDEB),
        Color(0xFFFFB4C8),
        Color(0xFFFFCC66),
        Color(0xFFB6E3F4),
        Color(0xFFC0AEDE),
        Color(0xFF6CE5D4)
    )

private data class WatchSurfaceSpec(
    val isRound: Boolean,
    val compact: Boolean
) {
    val screenShape
        get() = if (isRound) CircleShape else RoundedCornerShape(8.dp)

    val appPadding: Dp
        get() = if (isRound) 6.dp else 0.dp

    val profileHorizontalPadding: Dp
        get() = if (isRound) 18.dp else 12.dp

    val profileTopPadding: Dp
        get() =
            when {
                isRound && compact -> 22.dp
                isRound -> 28.dp
                compact -> 12.dp
                else -> 14.dp
            }

    val profileBottomPadding: Dp
        get() =
            when {
                isRound && compact -> 44.dp
                isRound -> 52.dp
                compact -> 18.dp
                else -> 22.dp
            }

    val profileHeaderWidth: Float
        get() = if (isRound) {
            if (compact) 0.88f else 0.9f
        } else {
            0.96f
        }

    val profileFieldWidth: Float
        get() = if (isRound) {
            if (compact) 0.78f else 0.82f
        } else {
            0.92f
        }

    val profileAvatarRowWidth: Float
        get() = if (isRound) {
            if (compact) 0.74f else 0.72f
        } else {
            0.86f
        }

    val profileSummaryWidth: Float
        get() = if (isRound) {
            if (compact) 0.72f else 0.78f
        } else {
            0.9f
        }

    val profileAvatarSize: Dp
        get() =
            when {
                isRound && compact -> 36.dp
                isRound -> 40.dp
                compact -> 38.dp
                else -> 42.dp
            }

    val profileAvatarSpacing: Dp
        get() =
            when {
                isRound && compact -> 12.dp
                isRound -> 14.dp
                compact -> 13.dp
                else -> 16.dp
            }

    val chatHorizontalPadding: Dp
        get() = if (isRound) 18.dp else 12.dp

    val chatTopPadding: Dp
        get() = if (isRound) {
            if (compact) 18.dp else 24.dp
        } else {
            12.dp
        }

    val chatBottomPadding: Dp
        get() = if (isRound) {
            if (compact) 24.dp else 30.dp
        } else {
            12.dp
        }

    val conversationBottomPadding: Dp
        get() = if (isRound) {
            if (compact) 30.dp else 38.dp
        } else {
            14.dp
        }

    val chatHeaderWidth: Float
        get() = if (isRound) {
            if (compact) 0.86f else 0.88f
        } else {
            0.94f
        }

    val quickReplyWidth: Float
        get() = if (isRound) {
            if (compact) 0.84f else 0.86f
        } else {
            0.94f
        }

    val scrollIndicatorEndPadding: Dp
        get() = if (isRound) 4.dp else 6.dp

    fun visibleMessageCount(hasPendingPeer: Boolean): Int =
        when {
            hasPendingPeer && isRound -> 2
            hasPendingPeer -> 2
            isRound && compact -> 2
            isRound -> 3
            compact -> 3
            else -> 4
        }
}


private data class RestoredChatPresentationState(
    val messagesByConversation: Map<String, List<ChatBubble>>,
    val draftsByConversation: Map<String, ConversationDraft>,
    val pendingTextOutbox: Map<String, PendingOutboundMessage>,
    val pendingVoiceOutbox: Map<String, PendingOutboundVoiceMessage>,
    val outgoingMessages: Map<String, OutgoingMessageRef>,
    val expectedRecipientsByMessage: Map<String, Set<String>>,
    val deliveredRecipientsByMessage: Map<String, Set<String>>,
    val readRecipientsByMessage: Map<String, Set<String>>,
    val sentReadReceipts: Set<String>,
    val conversationUpdateOrder: Map<String, Long>
)

private data class DurableChatMutationSnapshot(
    val messagesByConversation: Map<String, List<ChatBubble>>,
    val draftsByConversation: Map<String, ConversationDraft>,
    val pendingTextOutbox: Map<String, PendingOutboundMessage>,
    val pendingVoiceOutbox: Map<String, PendingOutboundVoiceMessage>,
    val outgoingMessages: Map<String, OutgoingMessageRef>,
    val expectedRecipientsByMessage: Map<String, Set<String>>,
    val deliveredRecipientsByMessage: Map<String, Set<String>>,
    val readRecipientsByMessage: Map<String, Set<String>>,
    val deliveredCounts: Map<String, Int>,
    val readCounts: Map<String, Int>,
    val conversationUpdateOrder: Map<String, Long>,
    val conversationUpdateSequence: Long
)

private fun QuotedMessage.toPersisted(): PersistedQuotedMessage =
    PersistedQuotedMessage(
        messageId = messageId,
        senderName = senderName,
        text = text
    )

private fun PersistedQuotedMessage.toPresentation(): QuotedMessage =
    QuotedMessage(
        messageId = messageId,
        senderName = senderName,
        text = text
    )

private fun ChatBubble.toPersisted(): PersistedChatMessage? {
    if (deliveryState == DeliveryState.System) {
        return null
    }
    return PersistedChatMessage(
        text = text,
        mine = mine,
        encrypted = encrypted,
        timestamp = timestamp,
        senderName = senderName,
        senderFingerprint = senderFingerprint,
        messageId = messageId,
        receiptMessageId = receiptMessageId,
        deliveryState = PersistedDeliveryState.valueOf(deliveryState.name),
        kind = PersistedChatMessageKind.valueOf(kind.name),
        quotedMessage = quotedMessage?.toPersisted(),
        voiceDurationMs = voiceDurationMs,
        voiceAudioBase64 = voiceAudioBytes?.let { bytes -> Base64.getEncoder().encodeToString(bytes) },
        createdAtEpochMillis = createdAtEpochMillis,
        expiresAtEpochMillis = expiresAtEpochMillis,
        reactions = reactions,
        forwarded = forwarded,
        forwardCount = forwardCount
    )
}

private fun PersistedChatMessage.toPresentation(nowEpochMillis: Long): ChatBubble? {
    if (
        deliveryState == PersistedDeliveryState.System ||
        expiresAtEpochMillis?.let { expiresAt -> expiresAt <= nowEpochMillis } == true
    ) {
        return null
    }
    val decodedVoice =
        voiceAudioBase64?.let { encoded ->
            runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
        }
    if (
        kind == PersistedChatMessageKind.Voice &&
        (voiceDurationMs == null || decodedVoice == null)
    ) {
        return null
    }
    return ChatBubble(
        text = text,
        mine = mine,
        encrypted = encrypted,
        timestamp = timestamp,
        senderName = senderName,
        senderFingerprint = senderFingerprint,
        messageId = messageId,
        receiptMessageId = receiptMessageId,
        deliveryState = DeliveryState.valueOf(deliveryState.name),
        kind = ChatMessageKind.valueOf(kind.name),
        quotedMessage = quotedMessage?.toPresentation(),
        voiceDurationMs = voiceDurationMs,
        voiceAudioBytes = decodedVoice,
        createdAtEpochMillis = createdAtEpochMillis,
        expiresAtEpochMillis = expiresAtEpochMillis,
        reactions = reactions,
        forwarded = forwarded,
        forwardCount = forwardCount
    )
}

private fun buildPersistedChatState(
    ownerFingerprint: String,
    conversationMessages: Map<String, List<ChatBubble>>,
    draftsByConversation: Map<String, ConversationDraft>,
    pendingOutboundMessages: Map<String, PendingOutboundMessage>,
    pendingOutboundVoiceMessages: Map<String, PendingOutboundVoiceMessage>,
    outgoingMessages: Map<String, OutgoingMessageRef>,
    expectedRecipientsByMessage: Map<String, Set<String>>,
    deliveredReceiptsByMessage: Map<String, Set<String>>,
    readReceiptsByMessage: Map<String, Set<String>>,
    sentReadReceipts: Set<String>,
    conversationUpdateOrder: Map<String, Long>,
    nowEpochMillis: Long = System.currentTimeMillis()
): PersistedChatState {
    val createdAtByMessageId =
        conversationMessages.values
            .asSequence()
            .flatten()
            .mapNotNull { message -> message.messageId?.let { id -> id to message.createdAtEpochMillis } }
            .toMap()
    val expiresAtByMessageId =
        conversationMessages.values
            .asSequence()
            .flatten()
            .mapNotNull { message ->
                message.messageId?.let { id ->
                    message.expiresAtEpochMillis?.let { expiresAt -> id to expiresAt }
                }
            }
            .toMap()
    return PersistedChatState(
        ownerFingerprint = ownerFingerprint,
        savedAtEpochMillis = nowEpochMillis,
        messagesByConversation =
            conversationMessages
                .mapValues { (_, messages) -> messages.mapNotNull(ChatBubble::toPersisted) }
                .filterValues(List<PersistedChatMessage>::isNotEmpty),
        draftsByConversation =
            draftsByConversation.mapValues { (_, draft) ->
                PersistedConversationDraft(
                    text = draft.text,
                    updatedAtEpochMillis = draft.updatedAtEpochMillis
                )
            },
        pendingTextOutbox =
            pendingOutboundMessages.values.map { pending ->
                PersistedTextOutboxMessage(
                    conversationId = pending.conversationId,
                    text = pending.text,
                    displayMessageId = pending.displayMessageId,
                    remainingTargetFingerprints = pending.remainingTargetFingerprints,
                    quotedMessage = pending.quotedMessage?.toPersisted(),
                    forwarded = pending.forwarded,
                    forwardCount = pending.forwardCount,
                    createdAtEpochMillis = createdAtByMessageId[pending.displayMessageId] ?: nowEpochMillis,
                    expiresAtEpochMillis = expiresAtByMessageId[pending.displayMessageId]
                )
            },
        pendingVoiceOutbox =
            pendingOutboundVoiceMessages.values.map { pending ->
                PersistedVoiceOutboxMessage(
                    conversationId = pending.conversationId,
                    displayMessageId = pending.displayMessageId,
                    remainingTargetFingerprints = pending.remainingTargetFingerprints,
                    durationMs = pending.durationMs,
                    audioBase64 = Base64.getEncoder().encodeToString(pending.audioBytes),
                    createdAtEpochMillis = createdAtByMessageId[pending.displayMessageId] ?: nowEpochMillis,
                    expiresAtEpochMillis = expiresAtByMessageId[pending.displayMessageId]
                )
            },
        outgoingEnvelopes =
            outgoingMessages.mapValues { (_, outgoing) ->
                PersistedOutgoingEnvelope(
                    conversationId = outgoing.conversationId,
                    displayMessageId = outgoing.displayMessageId,
                    recipientFingerprint = outgoing.recipientFingerprint,
                    expectedRecipients = outgoing.expectedRecipients,
                    createdAtEpochMillis = outgoing.createdAtEpochMillis
                )
            },
        expectedRecipientsByMessage = expectedRecipientsByMessage,
        deliveredRecipientsByMessage = deliveredReceiptsByMessage,
        readRecipientsByMessage = readReceiptsByMessage,
        sentReadReceipts = sentReadReceipts,
        conversationUpdateOrder = conversationUpdateOrder
    )
}

private fun restorePersistedChatState(
    state: PersistedChatState,
    validConversationIds: Set<String>,
    trustedFingerprints: Set<String>,
    localFingerprint: String,
    nowEpochMillis: Long = System.currentTimeMillis()
): RestoredChatPresentationState {
    val validParticipantFingerprints = trustedFingerprints + localFingerprint
    val messages =
        state.messagesByConversation
            .filterKeys { conversationId -> conversationId in validConversationIds }
            .mapValues { (_, storedMessages) ->
                storedMessages.mapNotNull { stored ->
                    stored.toPresentation(nowEpochMillis)?.takeIf { message ->
                        message.senderFingerprint == null ||
                            message.senderFingerprint in validParticipantFingerprints
                    }?.let { message ->
                        message.copy(
                            reactions =
                                message.reactions.filterKeys { fingerprint ->
                                    fingerprint in validParticipantFingerprints
                                }
                        )
                    }
                }
            }
            .mapValues { (_, restoredMessages) -> restoredMessages.toMutableList() }
            .toMutableMap()

    val pendingText =
        state.pendingTextOutbox.mapNotNull { stored ->
            if (
                stored.conversationId !in validConversationIds ||
                stored.expiresAtEpochMillis?.let { expiresAt -> expiresAt <= nowEpochMillis } == true
            ) {
                return@mapNotNull null
            }
            val recipients = stored.remainingTargetFingerprints.filter { it in trustedFingerprints }.distinct()
            if (recipients.isEmpty()) {
                return@mapNotNull null
            }
            stored.displayMessageId to
                PendingOutboundMessage(
                    conversationId = stored.conversationId,
                    text = stored.text,
                    displayMessageId = stored.displayMessageId,
                    remainingTargetFingerprints = recipients,
                    quotedMessage = stored.quotedMessage?.toPresentation(),
                    forwarded = stored.forwarded,
                    forwardCount = stored.forwardCount
                )
        }.toMap()

    val pendingVoice =
        state.pendingVoiceOutbox.mapNotNull { stored ->
            if (
                stored.conversationId !in validConversationIds ||
                stored.expiresAtEpochMillis?.let { expiresAt -> expiresAt <= nowEpochMillis } == true
            ) {
                return@mapNotNull null
            }
            val recipients = stored.remainingTargetFingerprints.filter { it in trustedFingerprints }.distinct()
            val audioBytes = runCatching { Base64.getDecoder().decode(stored.audioBase64) }.getOrNull()
            if (recipients.isEmpty() || audioBytes == null) {
                return@mapNotNull null
            }
            stored.displayMessageId to
                PendingOutboundVoiceMessage(
                    conversationId = stored.conversationId,
                    displayMessageId = stored.displayMessageId,
                    remainingTargetFingerprints = recipients,
                    durationMs = stored.durationMs,
                    audioBytes = audioBytes
                )
        }.toMap()

    state.pendingTextOutbox.forEach { stored ->
        val pending = pendingText[stored.displayMessageId] ?: return@forEach
        val conversationMessages = messages.getOrPut(pending.conversationId) { mutableListOf() }
        if (conversationMessages.none { message -> message.messageId == pending.displayMessageId }) {
            conversationMessages +=
                ChatBubble(
                    text = pending.text,
                    mine = true,
                    encrypted = true,
                    timestamp = formatClockTime(stored.createdAtEpochMillis),
                    messageId = pending.displayMessageId,
                    deliveryState = DeliveryState.Waiting,
                    quotedMessage = pending.quotedMessage,
                    createdAtEpochMillis = stored.createdAtEpochMillis,
                    expiresAtEpochMillis = stored.expiresAtEpochMillis,
                    forwarded = pending.forwarded,
                    forwardCount = pending.forwardCount
                )
        }
    }
    state.pendingVoiceOutbox.forEach { stored ->
        val pending = pendingVoice[stored.displayMessageId] ?: return@forEach
        val conversationMessages = messages.getOrPut(pending.conversationId) { mutableListOf() }
        if (conversationMessages.none { message -> message.messageId == pending.displayMessageId }) {
            conversationMessages +=
                ChatBubble(
                    text = "语音消息 · ${formatDuration(pending.durationMs)}",
                    mine = true,
                    encrypted = true,
                    timestamp = formatClockTime(stored.createdAtEpochMillis),
                    messageId = pending.displayMessageId,
                    deliveryState = DeliveryState.Waiting,
                    kind = ChatMessageKind.Voice,
                    voiceDurationMs = pending.durationMs,
                    voiceAudioBytes = pending.audioBytes,
                    createdAtEpochMillis = stored.createdAtEpochMillis,
                    expiresAtEpochMillis = stored.expiresAtEpochMillis
                )
        }
    }

    val outgoing =
        state.outgoingEnvelopes.mapNotNull { (envelopeId, stored) ->
            if (
                stored.conversationId !in validConversationIds ||
                stored.recipientFingerprint !in trustedFingerprints
            ) {
                return@mapNotNull null
            }
            val expected = stored.expectedRecipients.intersect(trustedFingerprints)
                .ifEmpty { setOf(stored.recipientFingerprint) }
            envelopeId to
                OutgoingMessageRef(
                    conversationId = stored.conversationId,
                    displayMessageId = stored.displayMessageId,
                    recipientFingerprint = stored.recipientFingerprint,
                    expectedRecipients = expected,
                    createdAtEpochMillis = stored.createdAtEpochMillis
                )
        }.toMap()
    val pendingMessageIds = pendingText.keys + pendingVoice.keys
    val outgoingDisplayMessageIds = outgoing.values.mapTo(mutableSetOf()) { it.displayMessageId }
    val normalizedMessages =
        messages.mapValues { (_, conversationMessages) ->
            conversationMessages.map { message ->
                val messageId = message.messageId
                if (!message.mine || messageId == null) {
                    message
                } else {
                    val normalizedState =
                        when {
                            messageId in pendingMessageIds &&
                                message.deliveryState != DeliveryState.Delivered &&
                                message.deliveryState != DeliveryState.Read -> DeliveryState.Waiting
                            message.deliveryState == DeliveryState.Sending &&
                                messageId in outgoingDisplayMessageIds -> DeliveryState.Sent
                            message.deliveryState == DeliveryState.Sending -> DeliveryState.Failed
                            message.deliveryState == DeliveryState.Waiting &&
                                messageId !in pendingMessageIds -> DeliveryState.Failed
                            else -> message.deliveryState
                        }
                    message.copy(deliveryState = normalizedState)
                }
            }
        }

    val expectedRecipients =
        state.expectedRecipientsByMessage
            .mapValues { (_, recipients) -> recipients.intersect(trustedFingerprints) }
            .filterValues(Set<String>::isNotEmpty)
            .toMutableMap()
    pendingText.forEach { (messageId, pending) ->
        expectedRecipients[messageId] =
            expectedRecipients[messageId].orEmpty() + pending.remainingTargetFingerprints
    }
    pendingVoice.forEach { (messageId, pending) ->
        expectedRecipients[messageId] =
            expectedRecipients[messageId].orEmpty() + pending.remainingTargetFingerprints
    }
    outgoing.values.forEach { envelope ->
        expectedRecipients[envelope.displayMessageId] =
            expectedRecipients[envelope.displayMessageId].orEmpty() + envelope.expectedRecipients
    }
    val deliveredRecipients =
        state.deliveredRecipientsByMessage
            .mapValues { (messageId, recipients) ->
                recipients.intersect(expectedRecipients[messageId].orEmpty())
            }
            .filterValues(Set<String>::isNotEmpty)
    val readRecipients =
        state.readRecipientsByMessage
            .mapValues { (messageId, recipients) ->
                recipients.intersect(expectedRecipients[messageId].orEmpty())
            }
            .filterValues(Set<String>::isNotEmpty)
    val receiptPrefixes = trustedFingerprints.map { fingerprint -> "$fingerprint:" }

    return RestoredChatPresentationState(
        messagesByConversation = normalizedMessages,
        draftsByConversation =
            state.draftsByConversation
                .filterKeys { conversationId -> conversationId in validConversationIds }
                .mapValues { (_, draft) ->
                    ConversationDraft(
                        text = draft.text,
                        updatedAtEpochMillis = draft.updatedAtEpochMillis
                    )
                },
        pendingTextOutbox = pendingText,
        pendingVoiceOutbox = pendingVoice,
        outgoingMessages = outgoing,
        expectedRecipientsByMessage = expectedRecipients,
        deliveredRecipientsByMessage = deliveredRecipients,
        readRecipientsByMessage = readRecipients,
        sentReadReceipts =
            state.sentReadReceipts.filterTo(mutableSetOf()) { receiptKey ->
                receiptPrefixes.any(receiptKey::startsWith)
            },
        conversationUpdateOrder =
            state.conversationUpdateOrder.filterKeys { conversationId ->
                conversationId in validConversationIds
            }
    )
}

@Composable
internal fun SpotChatApp(
    notificationIntent: Intent? = null,
    onNotificationIntentHandled: (Intent) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val defaultDeviceName = "SpotChat Watch"
    val profileStore =
        remember(context) {
            ProfileStore(context)
        }
    var profile by remember(defaultDeviceName) {
        mutableStateOf(profileStore.load(defaultDeviceName))
    }
    val identity =
        remember(context) {
            IdentityStore(context).getOrCreateIdentity()
        }
    val deviceName =
        remember(profile.displayName, defaultDeviceName) {
            profile.displayName.trim().ifBlank { defaultDeviceName }
        }
    val localFingerprint =
        remember(identity) {
            SpotChatCrypto.fingerprint(identity.public)
        }
    val chatStateStore =
        remember(context, localFingerprint) {
            EncryptedChatStateStore.create(context, localFingerprint)
        }
    val replayProtection =
        remember(context, localFingerprint) {
            SqliteReplayProtection(context, localFingerprint)
        }
    val engine =
        remember(identity, deviceName, replayProtection) {
            SpotChatEngine(deviceName, identity, replayProtection)
        }
    val sessionCoordinator =
        remember(engine) {
            SessionCoordinator(engine)
        }
    val trustedPeerStore =
        remember(context) {
            TrustedPeerStore(context)
        }
    val transportManager =
        remember(context) {
            TransportManager.create(context)
        }
    val notifier =
        remember(context) {
            SpotChatNotifier(context)
        }
    val notificationTokenStore =
        remember(context) {
            SpotChatNotificationTokenStore(context)
        }
    val voiceRecorder =
        remember(context) {
            SpotChatVoiceRecorder(context)
        }
    val voicePlaybackCache =
        remember(context) {
            VoicePlaybackCache(context.cacheDir)
        }
    val wearStateStore =
        remember(context) {
            SpotChatWearStateStore(context)
        }
    val coroutineScope = rememberCoroutineScope()
    val chatStateDisposalScope =
        remember(chatStateStore) { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val trustedPeers =
        remember {
            mutableStateListOf<StoredTrustedPeer>().apply {
                addAll(trustedPeerStore.all())
            }
        }
    val conversationMessages =
        remember(trustedPeerStore) {
            mutableStateMapOf<String, List<ChatBubble>>().apply {
                put(
                    NEARBY_GROUP_CONVERSATION_ID,
                    listOf(
                        ChatBubble(
                            text = "等待附近设备配对",
                            mine = false,
                            encrypted = true,
                            timestamp = nowTime(),
                            deliveryState = DeliveryState.System
                        ),
                        ChatBubble(
                            text = "所有聊天内容都会先加密再发送",
                            mine = false,
                            encrypted = true,
                            timestamp = nowTime(),
                            deliveryState = DeliveryState.System
                        )
                    )
                )
                trustedPeers.forEach { peer ->
                    put(
                        directConversationId(peer.fingerprint),
                        listOf(
                            ChatBubble(
                                text = "与 ${peerDisplayName(peer)} 的私聊已准备好",
                                mine = false,
                                encrypted = true,
                                timestamp = nowTime(),
                                deliveryState = DeliveryState.System
                            )
                        )
                    )
                }
            }
        }
    val unreadCounts = remember { mutableStateMapOf<String, Int>() }
    val mentionCounts = remember { mutableStateMapOf<String, Int>() }
    val pinnedConversationIds = remember { mutableStateMapOf<String, Boolean>() }
    val favoriteConversationIds = remember { mutableStateMapOf<String, Boolean>() }
    val conversationThemeColors = remember { mutableStateMapOf<String, Color>() }
    val mutedConversations = remember { mutableStateMapOf<String, MutedConversation>() }
    val archivedConversationIds = remember { mutableStateMapOf<String, Boolean>() }
    val lockedConversationIds = remember { mutableStateMapOf<String, Boolean>() }
    val draftsByConversation = remember { mutableStateMapOf<String, ConversationDraft>() }
    val blockedPeerFingerprints = remember { mutableStateMapOf<String, Boolean>() }
    val readReceiptsDisabledByConversation = remember { mutableStateMapOf<String, Boolean>() }
    val frequentlyForwardedAllowedByConversation = remember { mutableStateMapOf<String, Boolean>() }
    val announcementOnlyByConversation = remember { mutableStateMapOf<String, Boolean>() }
    val starredMessageIdsByConversation = remember { mutableStateMapOf<String, Set<String>>() }
    val pinnedMessageIdsByConversation = remember { mutableStateMapOf<String, String>() }
    val disappearingModesByConversation = remember { mutableStateMapOf<String, DisappearingMessageMode>() }
    val outgoingMessages = remember { mutableStateMapOf<String, OutgoingMessageRef>() }
    val deliveredCounts = remember { mutableStateMapOf<String, Int>() }
    val deliveredReceiptsByMessage = remember { mutableStateMapOf<String, Set<String>>() }
    val readCounts = remember { mutableStateMapOf<String, Int>() }
    val readReceiptsByMessage = remember { mutableStateMapOf<String, Set<String>>() }
    val expectedRecipientsByMessage = remember { mutableStateMapOf<String, Set<String>>() }
    val sentReadReceipts = remember { mutableStateListOf<String>() }
    val inFlightReadReceipts = remember { mutableSetOf<String>() }
    val pendingOutboundMessages = remember { mutableStateMapOf<String, PendingOutboundMessage>() }
    val pendingOutboundVoiceMessages = remember { mutableStateMapOf<String, PendingOutboundVoiceMessage>() }
    val inFlightOutboundMessages = remember { mutableStateMapOf<String, Boolean>() }
    val inFlightOutboundVoiceMessages = remember { mutableStateMapOf<String, Boolean>() }
    val conversationUpdateOrder = remember { mutableStateMapOf<String, Long>() }
    var chatStateLoadComplete by remember(chatStateStore) { mutableStateOf(false) }
    var chatStateWritable by remember(chatStateStore) { mutableStateOf(false) }
    val chatStateSnapshotSequence = remember(chatStateStore) { AtomicLong(0L) }
    val chatStateLastWrittenSequence = remember(chatStateStore) { AtomicLong(0L) }
    val chatStateLastWrittenSnapshot =
        remember(chatStateStore) { AtomicReference<PersistedChatState?>(null) }
    val chatStateWriteLock = remember(chatStateStore) { Any() }
    val chatStateOperational = chatStateLoadComplete && chatStateWritable

    fun isConversationLocked(conversationId: String): Boolean =
        lockedConversationIds[conversationId] == true

    var conversationUpdateSequence by remember { mutableStateOf(0L) }
    var peerRouteRevision by remember { mutableStateOf(0L) }
    var recipientSetRevision by remember { mutableStateOf(0L) }
    var outboundQueueWakeRevision by remember { mutableStateOf(0L) }
    var activeConversationId by remember { mutableStateOf(NEARBY_GROUP_CONVERSATION_ID) }
    if (conversationMessages[activeConversationId] == null) {
        activeConversationId = NEARBY_GROUP_CONVERSATION_ID
    }
    var pendingQuotedMessage by remember { mutableStateOf<QuotedMessage?>(null) }
    var pendingDirectReply by remember { mutableStateOf<PendingDirectReply?>(null) }
    var transportMode by remember { mutableStateOf(TransportMode.Lan) }
    var lanDiscoveryEnabled by remember { mutableStateOf(false) }
    var trustState by remember { mutableStateOf("点按局域网开始发现") }
    var activePeer by remember { mutableStateOf<TransportPeer?>(null) }
    var activePeerFingerprint by remember { mutableStateOf<String?>(null) }
    var pendingPeer by remember { mutableStateOf<TrustedPeer?>(null) }
    var pendingPeerRoute by remember { mutableStateOf<TransportPeer?>(null) }
    var pairingCode by remember { mutableStateOf<String?>(null) }
    var appSurface by remember { mutableStateOf(AppSurface.ConversationList) }
    var messageActionsReturnSurface by remember { mutableStateOf(AppSurface.Chat) }
    var voicePlaybackReturnSurface by remember { mutableStateOf(AppSurface.MessageActions) }
    val messageActionsBackStack = remember { mutableStateListOf<ChatBubble>() }
    var selectedActionMessage by remember { mutableStateOf<ChatBubble?>(null) }
    var selectedSecurityPeerFingerprint by remember { mutableStateOf<String?>(null) }
    var wearEntryRecoveryState by remember { mutableStateOf<WearEntryRecoveryState?>(null) }
    var wearEntryContext by remember { mutableStateOf<WearEntryContext?>(null) }
    var wearEntryContextId by remember { mutableStateOf(0L) }
    var actionConfirmation by remember { mutableStateOf<WatchActionConfirmation?>(null) }
    var actionConfirmationId by remember { mutableStateOf(0L) }
    var pendingForwardMessage by remember { mutableStateOf<ChatBubble?>(null) }
    var pendingMessageEdit by remember { mutableStateOf<PendingMessageEdit?>(null) }
    var draftSaveConversationId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchTargetSurface by remember { mutableStateOf(AppSurface.MessageSearch) }
    var pendingAliasPeerFingerprint by remember { mutableStateOf<String?>(null) }
    var editingGroupAboutConversationId by remember { mutableStateOf<String?>(null) }
    var nearbyGroupAbout by remember { mutableStateOf("附近设备加密群聊") }
    var chatListFilter by remember { mutableStateOf(ChatListFilter.All) }
    var voicePlaybackSpeed by remember { mutableStateOf(VoicePlaybackSpeed.Normal) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var recordingStartedAtMillis by remember { mutableStateOf<Long?>(null) }
    var recordingElapsedMillis by remember { mutableStateOf(0L) }
    var recordingConversationId by remember { mutableStateOf<String?>(null) }
    var activePlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var activePlaybackFile by remember { mutableStateOf<File?>(null) }
    var wearChatSnapshot by remember(wearStateStore) {
        mutableStateOf(wearStateStore.load())
    }
    val knownPeersByFingerprint = remember { mutableStateMapOf<String, TransportPeer>() }
    val peerLastSeenAt = remember { mutableStateMapOf<String, Long>() }

    fun currentPersistedChatState(): PersistedChatState =
        buildPersistedChatState(
            ownerFingerprint = localFingerprint,
            conversationMessages = conversationMessages.toMap(),
            draftsByConversation = draftsByConversation.toMap(),
            pendingOutboundMessages = pendingOutboundMessages.toMap(),
            pendingOutboundVoiceMessages = pendingOutboundVoiceMessages.toMap(),
            outgoingMessages = outgoingMessages.toMap(),
            expectedRecipientsByMessage = expectedRecipientsByMessage.toMap(),
            deliveredReceiptsByMessage = deliveredReceiptsByMessage.toMap(),
            readReceiptsByMessage = readReceiptsByMessage.toMap(),
            sentReadReceipts = sentReadReceipts.toSet(),
            conversationUpdateOrder = conversationUpdateOrder.toMap()
        )

    fun captureDurableChatMutation(): DurableChatMutationSnapshot =
        DurableChatMutationSnapshot(
            messagesByConversation =
                conversationMessages.mapValues { (_, messages) -> messages.toList() },
            draftsByConversation = draftsByConversation.toMap(),
            pendingTextOutbox = pendingOutboundMessages.toMap(),
            pendingVoiceOutbox = pendingOutboundVoiceMessages.toMap(),
            outgoingMessages = outgoingMessages.toMap(),
            expectedRecipientsByMessage = expectedRecipientsByMessage.toMap(),
            deliveredRecipientsByMessage = deliveredReceiptsByMessage.toMap(),
            readRecipientsByMessage = readReceiptsByMessage.toMap(),
            deliveredCounts = deliveredCounts.toMap(),
            readCounts = readCounts.toMap(),
            conversationUpdateOrder = conversationUpdateOrder.toMap(),
            conversationUpdateSequence = conversationUpdateSequence
        )

    fun <K, V> rollbackMapChanges(
        target: MutableMap<K, V>,
        before: Map<K, V>,
        applied: Map<K, V>
    ) {
        (before.keys + applied.keys).forEach { key ->
            val beforeContains = before.containsKey(key)
            val appliedContains = applied.containsKey(key)
            if (beforeContains == appliedContains && before[key] == applied[key]) {
                return@forEach
            }
            val currentContains = target.containsKey(key)
            if (currentContains != appliedContains || currentContains && target[key] != applied[key]) {
                return@forEach
            }
            if (beforeContains) {
                @Suppress("UNCHECKED_CAST")
                target[key] = before[key] as V
            } else {
                target.remove(key)
            }
        }
    }

    fun rollbackDurableChatMutation(
        before: DurableChatMutationSnapshot,
        applied: DurableChatMutationSnapshot
    ) {
        rollbackMapChanges(conversationMessages, before.messagesByConversation, applied.messagesByConversation)
        rollbackMapChanges(draftsByConversation, before.draftsByConversation, applied.draftsByConversation)
        rollbackMapChanges(pendingOutboundMessages, before.pendingTextOutbox, applied.pendingTextOutbox)
        rollbackMapChanges(
            pendingOutboundVoiceMessages,
            before.pendingVoiceOutbox,
            applied.pendingVoiceOutbox
        )
        rollbackMapChanges(outgoingMessages, before.outgoingMessages, applied.outgoingMessages)
        rollbackMapChanges(
            expectedRecipientsByMessage,
            before.expectedRecipientsByMessage,
            applied.expectedRecipientsByMessage
        )
        rollbackMapChanges(
            deliveredReceiptsByMessage,
            before.deliveredRecipientsByMessage,
            applied.deliveredRecipientsByMessage
        )
        rollbackMapChanges(
            readReceiptsByMessage,
            before.readRecipientsByMessage,
            applied.readRecipientsByMessage
        )
        rollbackMapChanges(deliveredCounts, before.deliveredCounts, applied.deliveredCounts)
        rollbackMapChanges(readCounts, before.readCounts, applied.readCounts)
        rollbackMapChanges(
            conversationUpdateOrder,
            before.conversationUpdateOrder,
            applied.conversationUpdateOrder
        )
        if (conversationUpdateSequence == applied.conversationUpdateSequence) {
            conversationUpdateSequence = before.conversationUpdateSequence
        }
        outboundQueueWakeRevision += 1L
    }

    suspend fun persistChatStateNow(
        requiredState: ((PersistedChatState) -> Boolean)? = null
    ): Boolean {
        if (!chatStateLoadComplete || !chatStateWritable) {
            return false
        }
        val snapshotSequence = chatStateSnapshotSequence.incrementAndGet()
        val state = currentPersistedChatState()
        return try {
            withContext(Dispatchers.IO + NonCancellable) {
                synchronized(chatStateWriteLock) {
                    val persistedState =
                        if (snapshotSequence > chatStateLastWrittenSequence.get()) {
                            chatStateStore.save(state).also { savedState ->
                                chatStateLastWrittenSnapshot.set(savedState)
                                chatStateLastWrittenSequence.set(snapshotSequence)
                            }
                        } else {
                            chatStateLastWrittenSnapshot.get()
                        }
                    requiredState?.invoke(persistedState ?: return@synchronized false) ?: true
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            chatStateWritable = false
            trustState = error.readableMessage("本地聊天状态保存失败，已暂停收发")
            false
        }
    }

    fun persistDurableUserMutation(
        before: DurableChatMutationSnapshot,
        applied: DurableChatMutationSnapshot,
        requiredState: (PersistedChatState) -> Boolean,
        successState: String,
        failureState: String,
        onSuccess: () -> Unit = {},
        onRollback: () -> Unit = {}
    ) {
        trustState = "正在安全保存更改"
        coroutineScope.launch {
            if (persistChatStateNow(requiredState)) {
                onSuccess()
                trustState = successState
            } else {
                val storageFailureState = trustState
                rollbackDurableChatMutation(before, applied)
                onRollback()
                val repaired = chatStateWritable && persistChatStateNow()
                trustState =
                    if (repaired || storageFailureState == "正在安全保存更改") {
                        failureState
                    } else {
                        "$failureState；$storageFailureState"
                    }
            }
        }
    }

    fun persistChatStateSoon() {
        if (!chatStateLoadComplete || !chatStateWritable) {
            return
        }
        coroutineScope.launch {
            persistChatStateNow()
        }
    }

    fun persistOutboundRemoval(
        conversationId: String? = null,
        displayMessageIds: Set<String> = emptySet(),
        recipientFingerprint: String? = null
    ) {
        if (conversationId == null && displayMessageIds.isEmpty() && recipientFingerprint == null) {
            return
        }
        coroutineScope.launch {
            persistChatStateNow { state ->
                state.pendingTextOutbox.none { queued ->
                    queued.conversationId == conversationId ||
                        queued.displayMessageId in displayMessageIds ||
                        recipientFingerprint in queued.remainingTargetFingerprints
                } &&
                    state.pendingVoiceOutbox.none { queued ->
                        queued.conversationId == conversationId ||
                            queued.displayMessageId in displayMessageIds ||
                            recipientFingerprint in queued.remainingTargetFingerprints
                    } &&
                    state.outgoingEnvelopes.values.none { outgoing ->
                        outgoing.displayMessageId in displayMessageIds ||
                            outgoing.recipientFingerprint == recipientFingerprint ||
                            recipientFingerprint in outgoing.expectedRecipients
                    } &&
                    state.expectedRecipientsByMessage.none { (messageId, recipients) ->
                        messageId in displayMessageIds || recipientFingerprint in recipients
                    }
            }
        }
    }

    fun persistRemovedMessages(
        conversationId: String,
        removedMessages: List<ChatBubble>
    ) {
        if (removedMessages.isEmpty()) {
            return
        }
        val removedMessageIds = removedMessages.mapNotNull(ChatBubble::messageId).toSet()
        val removedFallbackKeys =
            removedMessages
                .filter { message -> message.messageId == null }
                .map { message ->
                    Triple(message.createdAtEpochMillis, message.text, message.mine)
                }
                .toSet()
        coroutineScope.launch {
            persistChatStateNow { state ->
                state.messagesByConversation[conversationId].orEmpty().none { persisted ->
                    persisted.messageId in removedMessageIds ||
                        Triple(persisted.createdAtEpochMillis, persisted.text, persisted.mine) in
                        removedFallbackKeys
                } &&
                    state.pendingTextOutbox.none { queued ->
                        queued.displayMessageId in removedMessageIds
                    } &&
                    state.pendingVoiceOutbox.none { queued ->
                        queued.displayMessageId in removedMessageIds
                    } &&
                    state.outgoingEnvelopes.values.none { outgoing ->
                        outgoing.displayMessageId in removedMessageIds
                    }
            }
        }
    }

    fun clearVoiceRecordingState() {
        isRecordingVoice = false
        recordingStartedAtMillis = null
        recordingElapsedMillis = 0L
        recordingConversationId = null
    }

    fun releaseVoicePlayback(
        player: MediaPlayer?,
        playbackFile: File?
    ) {
        runCatching { player?.release() }
        voicePlaybackCache.delete(playbackFile)
    }

    fun stopActiveVoicePlayback() {
        val player = activePlayer
        val playbackFile = activePlaybackFile
        activePlayer = null
        activePlaybackFile = null
        releaseVoicePlayback(player, playbackFile)
    }

    fun showActionConfirmation(
        title: String,
        detail: String
    ) {
        actionConfirmationId += 1
        actionConfirmation =
            WatchActionConfirmation(
                id = actionConfirmationId,
                title = title,
                detail = detail
            )
    }

    fun hasBluetoothRuntimePermissions(): Boolean =
        requiredBluetoothRuntimePermissions(Build.VERSION.SDK_INT).all { permission ->
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted =
                permissions.values.all { granted -> granted } && hasBluetoothRuntimePermissions()
            if (granted) {
                trustState = "蓝牙待连接"
                transportMode = TransportMode.Bluetooth
            } else {
                trustState = "蓝牙权限被拒绝"
            }
        }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            trustState =
                if (granted) {
                    "通知回复已开启"
                } else {
                    "通知权限被拒绝"
                }
        }

    val audioPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            trustState =
                if (granted) {
                    "可以录制语音"
                } else {
                    "录音权限被拒绝"
                }
        }

    LaunchedEffect(Unit) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun transportHints(): List<String> =
        transportManager.transportHints(transportMode.kind)

    suspend fun sendPacket(
        transport: TransportKind,
        peer: TransportPeer,
        packet: WirePacket
    ) {
        transportManager.send(transport, peer, ChatCodec.encode(packet))
    }

    fun messagesForConversation(conversationId: String): List<ChatBubble> =
        conversationMessages[conversationId].orEmpty()

    fun searchMessages(
        conversationId: String,
        query: String
    ): List<ChatBubble> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return emptyList()
        }
        return messagesForConversation(conversationId)
            .asReversed()
            .filter { message ->
                message.deliveryState != DeliveryState.System &&
                    message.searchText().contains(cleanQuery, ignoreCase = true)
            }
            .take(MAX_SEARCH_RESULTS)
    }

    fun starredMessageIds(conversationId: String): Set<String> =
        starredMessageIdsByConversation[conversationId].orEmpty()

    fun isMessageStarred(
        conversationId: String,
        message: ChatBubble
    ): Boolean =
        message.stableStarId() in starredMessageIds(conversationId)

    fun starredMessages(conversationId: String): List<ChatBubble> {
        val starredIds = starredMessageIds(conversationId)
        if (starredIds.isEmpty()) {
            return emptyList()
        }
        return messagesForConversation(conversationId)
            .asReversed()
            .filter { message ->
                message.deliveryState != DeliveryState.System &&
                    message.stableStarId() in starredIds
            }
            .take(MAX_STARRED_RESULTS)
    }

    fun pinnedMessage(conversationId: String): ChatBubble? {
        val pinnedId = pinnedMessageIdsByConversation[conversationId] ?: return null
        return messagesForConversation(conversationId)
            .firstOrNull { message -> message.stableStarId() == pinnedId }
    }

    fun isMessagePinned(
        conversationId: String,
        message: ChatBubble
    ): Boolean =
        pinnedMessageIdsByConversation[conversationId] == message.stableStarId()

    fun hasLocalReaction(message: ChatBubble): Boolean =
        localFingerprint in message.reactions

    fun messageReceiptSummary(
        conversation: ChatConversation,
        message: ChatBubble
    ): MessageReceiptSummary? {
        val messageId = message.messageId ?: return null
        if (!message.mine || message.deliveryState == DeliveryState.System) {
            return null
        }
        val expectedRecipients =
            expectedRecipientsByMessage[messageId]
                ?: conversation.memberFingerprints.toSet()
        val expectedCount = expectedRecipients.size.coerceAtLeast(1)
        val trustedByFingerprint = trustedPeers.associateBy { peer -> peer.fingerprint }
        fun displayName(fingerprint: String): String =
            trustedByFingerprint[fingerprint]?.deviceName ?: fingerprint.take(6)

        val memberNames =
            expectedRecipients.map { fingerprint ->
                displayName(fingerprint)
            }
        val deliveredFingerprints = deliveredReceiptsByMessage[messageId].orEmpty()
        val readFingerprints = readReceiptsByMessage[messageId].orEmpty()
        val deliveredReceiptNames =
            deliveredFingerprints.map(::displayName)
        val readReceiptNames =
            readFingerprints.map(::displayName)
        val deliveredCount =
            maxOf(
                deliveredCounts[messageId] ?: 0,
                if (deliveryStateRank(message.deliveryState) >= deliveryStateRank(DeliveryState.Delivered)) {
                    expectedCount
                } else {
                    0
                }
            ).coerceAtMost(expectedCount)
        val readCount =
            maxOf(
                readCounts[messageId] ?: 0,
                if (deliveryStateRank(message.deliveryState) >= deliveryStateRank(DeliveryState.Read)) {
                    expectedCount
                } else {
                    0
                }
            ).coerceAtMost(expectedCount)
        return MessageReceiptSummary(
            expectedCount = expectedCount,
            deliveredCount = deliveredCount,
            readCount = readCount,
            deliveredNames =
                if (deliveredCount >= expectedCount) {
                    memberNames
                } else {
                    deliveredReceiptNames
                },
            readNames =
                if (readCount >= expectedCount) {
                    memberNames
                } else {
                    readReceiptNames
                },
            undeliveredNames =
                if (deliveredCount >= expectedCount) {
                    emptyList()
                } else {
                    expectedRecipients
                        .filterNot { fingerprint -> fingerprint in deliveredFingerprints }
                        .map(::displayName)
                },
            unreadNames =
                if (readCount >= expectedCount) {
                    emptyList()
                } else {
                    expectedRecipients
                        .filterNot { fingerprint -> fingerprint in readFingerprints }
                        .map(::displayName)
                }
        )
    }

    fun reactionDetails(message: ChatBubble): List<ReactionDetail> {
        if (message.reactions.isEmpty()) {
            return emptyList()
        }
        val trustedByFingerprint = trustedPeers.associateBy { peer -> peer.fingerprint }
        return message.reactions.map { (senderFingerprint, reactionCode) ->
            ReactionDetail(
                senderFingerprint = senderFingerprint,
                senderName =
                    when {
                        senderFingerprint == localFingerprint -> "我"
                        trustedByFingerprint[senderFingerprint] != null ->
                            trustedByFingerprint[senderFingerprint]?.deviceName ?: senderFingerprint.take(6)
                        else -> senderFingerprint.take(6)
                    },
                reactionLabel = reactionLabel(reactionCode)
            )
        }
    }

    fun quotedMessageTarget(
        conversationId: String,
        quote: QuotedMessage?
    ): ChatBubble? =
        quote?.let { quoted ->
            val messages = messagesForConversation(conversationId)
            messages.firstOrNull { message -> message.messageId == quoted.messageId }
                ?: messages.firstOrNull { message ->
                    message.previewText() == quoted.text &&
                        (
                            (message.mine && quoted.senderName == "我") ||
                                message.senderName == quoted.senderName
                        )
                }
        }

    fun isPeerBlocked(fingerprint: String): Boolean =
        blockedPeerFingerprints[fingerprint] == true

    fun peerReachabilityText(fingerprint: String): String =
        when {
            isPeerBlocked(fingerprint) -> "已阻止"
            knownPeersByFingerprint[fingerprint] != null -> "当前可发送"
            else ->
                peerLastSeenAt[fingerprint]
                    ?.let { lastSeen -> "最近发现 ${formatClockTime(lastSeen)}" }
                    ?: "等待发现"
        }

    fun groupReachabilityText(memberFingerprints: List<String>): String {
        if (memberFingerprints.isEmpty()) {
            return "等待成员"
        }
        val allowedFingerprints = memberFingerprints.filterNot(::isPeerBlocked)
        if (allowedFingerprints.isEmpty()) {
            return "成员均已阻止"
        }
        val reachableCount = allowedFingerprints.count { fingerprint ->
            knownPeersByFingerprint[fingerprint] != null
        }
        if (reachableCount > 0) {
            return "$reachableCount/${allowedFingerprints.size} 可发送"
        }
        val lastSeenAt =
            allowedFingerprints
                .mapNotNull { fingerprint -> peerLastSeenAt[fingerprint] }
                .maxOrNull()
        return lastSeenAt
            ?.let { lastSeen -> "最近发现 ${formatClockTime(lastSeen)}" }
            ?: "等待发现"
    }

    fun allowsFrequentlyForwarded(conversationId: String): Boolean =
        frequentlyForwardedAllowedByConversation[conversationId] != false

    fun shouldBlockFrequentlyForwarded(
        conversationId: String,
        forwardCount: Int
    ): Boolean =
        conversationId == NEARBY_GROUP_CONVERSATION_ID &&
            !allowsFrequentlyForwarded(conversationId) &&
            forwardCount >= FREQUENTLY_FORWARDED_THRESHOLD

    fun frequentlyForwardedPolicyLabel(conversationId: String): String =
        if (allowsFrequentlyForwarded(conversationId)) {
            "允许多次转发"
        } else {
            "禁止多次转发"
        }

    fun isAnnouncementOnly(conversationId: String): Boolean =
        announcementOnlyByConversation[conversationId] == true

    fun groupPostingPolicyLabel(conversationId: String): String =
        if (isAnnouncementOnly(conversationId)) {
            "公告模式"
        } else {
            "成员可发言"
        }

    fun canSendToConversation(conversation: ChatConversation): Boolean =
        conversation.id != NEARBY_GROUP_CONVERSATION_ID || !isAnnouncementOnly(conversation.id)

    fun conversationById(conversationId: String): ChatConversation? {
        if (conversationId == NEARBY_GROUP_CONVERSATION_ID) {
            val memberFingerprints = trustedPeers.map { peer -> peer.fingerprint }
            val policyLabel = frequentlyForwardedPolicyLabel(NEARBY_GROUP_CONVERSATION_ID)
            val postingPolicyLabel = groupPostingPolicyLabel(NEARBY_GROUP_CONVERSATION_ID)
            return ChatConversation(
                id = NEARBY_GROUP_CONVERSATION_ID,
                kind = ConversationKind.Group,
                title = NEARBY_GROUP_TITLE,
                subtitle =
                    if (trustedPeers.isEmpty()) {
                        "$nearbyGroupAbout · 等待成员 · $postingPolicyLabel · $policyLabel"
                    } else {
                        "$nearbyGroupAbout · ${trustedPeers.size} 位成员 · ${groupReachabilityText(memberFingerprints)} · $postingPolicyLabel · $policyLabel"
                    },
                memberFingerprints = memberFingerprints,
                themeColor = conversationThemeColors[NEARBY_GROUP_CONVERSATION_ID]
            )
        }

        val peerFingerprint =
            conversationId
                .removePrefix(DIRECT_CONVERSATION_PREFIX)
                .takeIf { fingerprint -> fingerprint != conversationId && fingerprint.isNotBlank() }
                ?: return null
        val peer = trustedPeers.firstOrNull { storedPeer -> storedPeer.fingerprint == peerFingerprint }
            ?: return null
        return ChatConversation(
            id = directConversationId(peer.fingerprint),
            kind = ConversationKind.Direct,
            title = peerDisplayName(peer),
            subtitle = "${peerAbout(peer)} · ${peerReachabilityText(peer.fingerprint)}",
            peerFingerprint = peer.fingerprint,
            memberFingerprints = listOf(peer.fingerprint),
            themeColor = conversationThemeColors[directConversationId(peer.fingerprint)]
        )
    }

    fun clearConversationAlerts(conversationId: String) {
        unreadCounts[conversationId] = 0
        mentionCounts[conversationId] = 0
        notifier.clearConversation(conversationId)
    }

    fun muteState(
        conversationId: String,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): MutedConversation? {
        val mute = mutedConversations[conversationId] ?: return null
        if (mute.isActive(nowEpochMillis)) {
            return mute
        }
        mutedConversations.remove(conversationId)
        return null
    }

    fun isConversationMuted(
        conversationId: String,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): Boolean =
        muteState(conversationId, nowEpochMillis) != null

    fun shouldNotifyConversation(conversationId: String): Boolean =
        !isConversationMuted(conversationId) && archivedConversationIds[conversationId] != true

    fun muteStatusLabel(conversationId: String): String {
        val mute = muteState(conversationId) ?: return "开启"
        val untilEpochMillis = mute.untilEpochMillis ?: return "始终"
        val remainingMs = (untilEpochMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        val remainingMinutes = (remainingMs + 59_999L) / 60_000L
        return when {
            remainingMinutes <= 1L -> "1分钟"
            remainingMinutes < 60L -> "${remainingMinutes}分"
            remainingMinutes < 24L * 60L -> "${(remainingMinutes + 59L) / 60L}小时"
            else -> "${(remainingMinutes + 1_439L) / 1_440L}天"
        }
    }

    fun muteActionLabel(conversationId: String): String {
        val currentPreset = muteState(conversationId)?.preset
        val nextPreset =
            when (currentPreset) {
                null -> MutePreset.EightHours
                MutePreset.EightHours -> MutePreset.OneWeek
                MutePreset.OneWeek -> MutePreset.Always
                MutePreset.Always -> null
            }
        return nextPreset?.let { preset -> "静音${preset.label}" } ?: "恢复通知"
    }

    fun messageMentionsMe(message: ChatBubble): Boolean {
        if (message.mine || message.deliveryState == DeliveryState.System || message.text.isBlank()) {
            return false
        }
        val text = message.text.lowercase(Locale.getDefault())
        val names =
            listOf(profile.displayName, deviceName, "我")
                .map { name -> name.trim().lowercase(Locale.getDefault()) }
                .filter { name -> name.isNotBlank() }
                .distinct()
        val explicitMentions =
            names.any { name ->
                text.contains("@$name") ||
                    text.contains("＠$name") ||
                    (name.length >= 2 && text.contains(name))
            }
        return explicitMentions ||
            text.contains("@all") ||
            text.contains("＠all") ||
            text.contains("@所有人") ||
            text.contains("＠所有人")
    }

    fun pendingOutboxConversationIds(): Set<String> =
        buildSet {
            pendingOutboundMessages.values.mapTo(this) { pending -> pending.conversationId }
            pendingOutboundVoiceMessages.values.mapTo(this) { pending -> pending.conversationId }
        }

    fun pendingVoiceByteCounts(): Map<String, Int> =
        pendingOutboundVoiceMessages.mapValues { (_, pending) -> pending.audioBytes.size }

    fun pruneExpiredOutgoingEnvelopes() {
        val expiresBefore = System.currentTimeMillis() - OUTGOING_ENVELOPE_TTL_MS
        outgoingMessages
            .filterValues { outgoing -> outgoing.createdAtEpochMillis <= expiresBefore }
            .keys
            .toList()
            .forEach(outgoingMessages::remove)
    }

    fun activeReceiptTrackedMessageIds(): Set<String> {
        pruneExpiredOutgoingEnvelopes()
        return buildSet {
            addAll(pendingOutboundMessages.keys)
            addAll(pendingOutboundVoiceMessages.keys)
            outgoingMessages.values.mapTo(this) { outgoing -> outgoing.displayMessageId }
        }
    }

    fun outboxCapacityMessage(issue: OutboxCapacityIssue): String =
        when (issue) {
            OutboxCapacityIssue.MessageCount -> "待发送消息已达到本地保存上限"
            OutboxCapacityIssue.RecipientCount -> "收件人数量超过本地保存上限"
            OutboxCapacityIssue.VoiceSize -> "语音大小超过本地保存上限"
            OutboxCapacityIssue.VoiceBudget -> "待发送语音已占满本地保存空间"
            OutboxCapacityIssue.CriticalConversationCount -> "待发送聊天数量已达到本地保存上限"
            OutboxCapacityIssue.EnvelopeCount -> "正在等待回执的消息过多，请稍后重试"
            OutboxCapacityIssue.ReceiptTrackingCount -> "等待回执的消息已达到本地保存上限"
        }

    fun validateTextSendCapacity(
        displayMessageId: String,
        recipientCount: Int,
        conversationId: String,
        persistOutbox: Boolean
    ): OutboxCapacityIssue? =
        when {
            recipientCount > EncryptedChatStateStore.MAX_RECIPIENTS ->
                OutboxCapacityIssue.RecipientCount
            recipientCount == 0 -> null
            else ->
                validateReceiptTrackingCapacity(
                    currentTrackedMessageIds = activeReceiptTrackedMessageIds(),
                    messageId = displayMessageId
                ) ?: if (!persistOutbox) {
                    null
                } else {
                    validateTextOutboxCapacity(
                        currentMessageIds = pendingOutboundMessages.keys,
                        messageId = displayMessageId,
                        recipientCount = recipientCount,
                        currentPendingConversationIds = pendingOutboxConversationIds(),
                        conversationId = conversationId
                    )
                }
        }

    fun validateVoiceSendCapacity(
        displayMessageId: String,
        audioByteCount: Int,
        recipientCount: Int,
        conversationId: String,
        persistOutbox: Boolean
    ): OutboxCapacityIssue? =
        when {
            recipientCount > EncryptedChatStateStore.MAX_RECIPIENTS ->
                OutboxCapacityIssue.RecipientCount
            audioByteCount !in 1..EncryptedChatStateStore.MAX_SINGLE_VOICE_BYTES ->
                OutboxCapacityIssue.VoiceSize
            recipientCount == 0 -> null
            else ->
                validateReceiptTrackingCapacity(
                    currentTrackedMessageIds = activeReceiptTrackedMessageIds(),
                    messageId = displayMessageId
                ) ?: if (!persistOutbox) {
                    null
                } else {
                    validateVoiceOutboxCapacity(
                        currentAudioBytesByMessage = pendingVoiceByteCounts(),
                        messageId = displayMessageId,
                        audioByteCount = audioByteCount,
                        recipientCount = recipientCount,
                        currentPendingConversationIds = pendingOutboxConversationIds(),
                        conversationId = conversationId
                    )
                }
        }

    fun DeliveryState.canMoveTo(next: DeliveryState): Boolean =
        deliveryStateRank(next) >= deliveryStateRank(this)

    fun rememberOutgoingEnvelope(
        envelopeMessageId: String,
        outgoingMessage: OutgoingMessageRef
    ): Boolean {
        pruneExpiredOutgoingEnvelopes()
        val capacityIssue =
            validateReceiptTrackingCapacity(
                currentTrackedMessageIds = activeReceiptTrackedMessageIds(),
                messageId = outgoingMessage.displayMessageId
            ) ?: validateOutgoingEnvelopeCapacity(
                currentEnvelopeIds = outgoingMessages.keys,
                envelopeId = envelopeMessageId,
                recipientCount = outgoingMessage.expectedRecipients.size
            )
        if (capacityIssue != null) {
            trustState = outboxCapacityMessage(capacityIssue)
            return false
        }
        outgoingMessages[envelopeMessageId] = outgoingMessage
        return true
    }

    fun updateMessageState(
        messageId: String,
        deliveryState: DeliveryState,
        receiptSenderFingerprint: String? = null
    ) {
        val outboundMessage = outgoingMessages[messageId]
        if (
            deliveryState == DeliveryState.Delivered &&
            outboundMessage != null &&
            outboundMessage.expectedRecipients.size > 1
        ) {
            val receiptKey = receiptSenderFingerprint ?: messageId
            val deliveredReceipts = deliveredReceiptsByMessage[outboundMessage.displayMessageId].orEmpty()
            if (receiptKey in deliveredReceipts) {
                return
            }
            val updatedDeliveredReceipts = deliveredReceipts + receiptKey
            deliveredReceiptsByMessage[outboundMessage.displayMessageId] = updatedDeliveredReceipts
            deliveredCounts[outboundMessage.displayMessageId] = updatedDeliveredReceipts.size
            if (updatedDeliveredReceipts.size < outboundMessage.expectedRecipients.size) {
                return
            }
        }
        if (
            deliveryState == DeliveryState.Read &&
            outboundMessage != null &&
            outboundMessage.expectedRecipients.size > 1
        ) {
            val receiptKey = receiptSenderFingerprint ?: messageId
            val readReceipts = readReceiptsByMessage[outboundMessage.displayMessageId].orEmpty()
            if (receiptKey in readReceipts) {
                return
            }
            val updatedReadReceipts = readReceipts + receiptKey
            readReceiptsByMessage[outboundMessage.displayMessageId] = updatedReadReceipts
            readCounts[outboundMessage.displayMessageId] = updatedReadReceipts.size
            if (updatedReadReceipts.size < outboundMessage.expectedRecipients.size) {
                return
            }
        }
        val conversationIds =
            if (outboundMessage == null) {
                conversationMessages.keys.toList()
            } else {
                listOf(outboundMessage.conversationId)
            }
        val displayMessageId = outboundMessage?.displayMessageId ?: messageId
        conversationIds.forEach { conversationId ->
            val messages = messagesForConversation(conversationId)
            val index = messages.indexOfFirst { message -> message.messageId == displayMessageId }
            if (index >= 0) {
                val currentMessage = messages[index]
                if (
                    !currentMessage.deliveryState.canMoveTo(deliveryState) &&
                    deliveryState.isReceiptState()
                ) {
                    return
                }
                conversationMessages[conversationId] =
                    messages.toMutableList().also { updatedMessages ->
                        updatedMessages[index] = currentMessage.copy(deliveryState = deliveryState)
                    }
                return
            }
        }
    }

    fun removeRecipientFromOutboundTracking(
        fingerprint: String,
        persistRemoval: Boolean = true
    ) {
        recipientSetRevision += 1L
        expectedRecipientsByMessage
            .filterValues { recipients -> fingerprint in recipients }
            .keys
            .toList()
            .forEach { displayMessageId ->
                val activeRecipients = expectedRecipientsByMessage[displayMessageId].orEmpty() - fingerprint
                expectedRecipientsByMessage[displayMessageId] = activeRecipients
                outgoingMessages
                    .filterValues { message -> message.displayMessageId == displayMessageId }
                    .keys
                    .toList()
                    .forEach { envelopeId ->
                        val message = outgoingMessages[envelopeId] ?: return@forEach
                        if (message.recipientFingerprint == fingerprint) {
                            outgoingMessages.remove(envelopeId)
                        } else {
                            outgoingMessages[envelopeId] =
                                message.copy(expectedRecipients = activeRecipients)
                        }
                    }
                pendingOutboundMessages[displayMessageId]?.let { message ->
                    val remainingRecipients = message.remainingTargetFingerprints - fingerprint
                    if (remainingRecipients.isEmpty()) {
                        pendingOutboundMessages.remove(displayMessageId)
                    } else {
                        pendingOutboundMessages[displayMessageId] =
                            message.copy(remainingTargetFingerprints = remainingRecipients)
                    }
                }
                pendingOutboundVoiceMessages[displayMessageId]?.let { message ->
                    val remainingRecipients = message.remainingTargetFingerprints - fingerprint
                    if (remainingRecipients.isEmpty()) {
                        pendingOutboundVoiceMessages.remove(displayMessageId)
                    } else {
                        pendingOutboundVoiceMessages[displayMessageId] =
                            message.copy(remainingTargetFingerprints = remainingRecipients)
                    }
                }
                val deliveredRecipients =
                    deliveredReceiptsByMessage[displayMessageId].orEmpty().intersect(activeRecipients)
                val readRecipients =
                    readReceiptsByMessage[displayMessageId].orEmpty().intersect(activeRecipients)
                deliveredReceiptsByMessage[displayMessageId] = deliveredRecipients
                readReceiptsByMessage[displayMessageId] = readRecipients
                deliveredCounts[displayMessageId] = deliveredRecipients.size
                readCounts[displayMessageId] = readRecipients.size
                val currentDeliveryState =
                    conversationMessages.values
                        .asSequence()
                        .flatten()
                        .firstOrNull { message -> message.messageId == displayMessageId }
                        ?.deliveryState
                when {
                    activeRecipients.isEmpty() &&
                        currentDeliveryState != DeliveryState.Delivered &&
                        currentDeliveryState != DeliveryState.Read ->
                        updateMessageState(displayMessageId, DeliveryState.Failed)
                    activeRecipients.isNotEmpty() && readRecipients.containsAll(activeRecipients) ->
                        updateMessageState(displayMessageId, DeliveryState.Read)
                    activeRecipients.isNotEmpty() && deliveredRecipients.containsAll(activeRecipients) ->
                        updateMessageState(displayMessageId, DeliveryState.Delivered)
                    pendingOutboundMessages[displayMessageId] == null &&
                        pendingOutboundVoiceMessages[displayMessageId] == null &&
                        currentDeliveryState != DeliveryState.Delivered &&
                        currentDeliveryState != DeliveryState.Read ->
                        updateMessageState(displayMessageId, DeliveryState.Sent)
                }
            }
        if (persistRemoval) {
            persistOutboundRemoval(recipientFingerprint = fingerprint)
        }
    }

    fun clearPendingOutboundForConversation(
        conversationId: String,
        markFailed: Boolean = false,
        persistRemoval: Boolean = true
    ): Int {
        val pendingTextMessageIds =
            pendingOutboundMessages
                .filterValues { message -> message.conversationId == conversationId }
                .keys
                .toList()
        val pendingVoiceMessageIds =
            pendingOutboundVoiceMessages
                .filterValues { message -> message.conversationId == conversationId }
                .keys
                .toList()
        val pendingMessageIds = (pendingTextMessageIds + pendingVoiceMessageIds).distinct()
        if (markFailed) {
            pendingMessageIds.forEach { messageId ->
                updateMessageState(messageId, DeliveryState.Failed)
            }
        }
        pendingTextMessageIds.forEach { messageId -> pendingOutboundMessages.remove(messageId) }
        pendingVoiceMessageIds.forEach { messageId -> pendingOutboundVoiceMessages.remove(messageId) }
        if (pendingMessageIds.isNotEmpty()) {
            outgoingMessages
                .filterValues { message -> message.displayMessageId in pendingMessageIds }
                .keys
                .toList()
                .forEach { packetMessageId -> outgoingMessages.remove(packetMessageId) }
            pendingMessageIds.forEach { messageId ->
                deliveredCounts.remove(messageId)
                deliveredReceiptsByMessage.remove(messageId)
                readCounts.remove(messageId)
                readReceiptsByMessage.remove(messageId)
                expectedRecipientsByMessage.remove(messageId)
            }
        }
        if (persistRemoval) {
            persistOutboundRemoval(
                conversationId = conversationId,
                displayMessageIds = pendingMessageIds.toSet()
            )
        }
        return pendingMessageIds.size
    }

    fun notifyIncomingMessage(
        conversationId: String,
        message: ChatBubble
    ) {
        if (message.mine || message.deliveryState == DeliveryState.System) {
            return
        }
        val conversation = conversationById(conversationId) ?: return
        val senderName =
            message.senderName
                ?: conversation.title
                    .takeIf { conversation.kind == ConversationKind.Direct }
                ?: "SpotChat"
        notifier.showIncomingMessage(
            conversationId = conversationId,
            conversationTitle = conversation.title,
            senderName = senderName,
            messageText =
                if (isConversationLocked(conversationId)) {
                    if (messageMentionsMe(message)) "收到提及消息" else "收到新消息"
                } else if (messageMentionsMe(message)) {
                    "@提及你 · ${message.text}"
                } else {
                    message.text
                },
            unreadCount = unreadCounts[conversationId] ?: 1
        )
    }

    fun recordIncomingAttention(
        conversationId: String,
        message: ChatBubble
    ) {
        if (
            message.mine ||
            message.deliveryState == DeliveryState.System ||
            (appSurface == AppSurface.Chat && activeConversationId == conversationId)
        ) {
            return
        }
        unreadCounts[conversationId] = (unreadCounts[conversationId] ?: 0) + 1
        if (messageMentionsMe(message)) {
            mentionCounts[conversationId] = (mentionCounts[conversationId] ?: 0) + 1
        }
        if (shouldNotifyConversation(conversationId)) {
            notifyIncomingMessage(conversationId, message)
        }
    }

    fun appendMessage(
        conversationId: String,
        message: ChatBubble,
        recordAttention: Boolean = true
    ) {
        val disappearingMode = disappearingModesByConversation[conversationId] ?: DisappearingMessageMode.Off
        val timedMessage =
            if (
                message.deliveryState == DeliveryState.System ||
                disappearingMode.durationMs == null ||
                message.expiresAtEpochMillis != null
            ) {
                message
            } else {
                val createdAt = message.createdAtEpochMillis
                message.copy(expiresAtEpochMillis = createdAt + disappearingMode.durationMs)
            }
        conversationMessages[conversationId] = messagesForConversation(conversationId) + timedMessage
        conversationUpdateSequence += 1
        conversationUpdateOrder[conversationId] = conversationUpdateSequence
        if (recordAttention) {
            recordIncomingAttention(conversationId, timedMessage)
        }
    }

    suspend fun sendHello(
        transport: TransportKind,
        peer: TransportPeer
    ) {
        runCatching {
            // Keep profile text out of unauthenticated discovery packets.
            sendPacket(transport, peer, sessionCoordinator.helloPacket(transportHints()))
        }.onFailure {
            trustState = "握手失败"
        }
    }

    fun appendSystemMessage(
        text: String,
        encrypted: Boolean = true,
        conversationId: String = activeConversationId
    ) {
        appendMessage(
            conversationId,
            ChatBubble(
                text = text,
                mine = false,
                encrypted = encrypted,
                timestamp = nowTime(),
                deliveryState = DeliveryState.System
            )
        )
    }

    LaunchedEffect(chatStateStore, localFingerprint) {
        chatStateLoadComplete = false
        chatStateWritable = false
        val loadResult =
            runCatching {
                withContext(Dispatchers.IO) {
                    chatStateStore.load()
                }
            }.getOrElse { error ->
                ChatStateLoadResult.Unreadable(error)
            }
        when (loadResult) {
            ChatStateLoadResult.Missing -> {
                chatStateLastWrittenSnapshot.set(null)
                chatStateWritable = true
                trustState = "本地聊天状态已就绪"
            }

            is ChatStateLoadResult.Loaded -> {
                chatStateLastWrittenSnapshot.set(loadResult.state)
                val trustedFingerprints = trustedPeers.mapTo(mutableSetOf()) { peer -> peer.fingerprint }
                val validConversationIds =
                    trustedFingerprints
                        .mapTo(mutableSetOf(NEARBY_GROUP_CONVERSATION_ID)) { fingerprint ->
                            directConversationId(fingerprint)
                        }
                val restored =
                    restorePersistedChatState(
                        state = loadResult.state,
                        validConversationIds = validConversationIds,
                        trustedFingerprints = trustedFingerprints,
                        localFingerprint = localFingerprint
                    )
                val systemMessages =
                    conversationMessages.mapValues { (_, messages) ->
                        messages.filter { message -> message.deliveryState == DeliveryState.System }
                    }
                conversationMessages.keys.toList().forEach { conversationId ->
                    conversationMessages[conversationId] = systemMessages[conversationId].orEmpty()
                }
                restored.messagesByConversation.forEach { (conversationId, messages) ->
                    conversationMessages[conversationId] =
                        systemMessages[conversationId].orEmpty() + messages.sortedBy(ChatBubble::createdAtEpochMillis)
                }
                draftsByConversation.clear()
                draftsByConversation.putAll(restored.draftsByConversation)
                pendingOutboundMessages.clear()
                pendingOutboundMessages.putAll(restored.pendingTextOutbox)
                pendingOutboundVoiceMessages.clear()
                pendingOutboundVoiceMessages.putAll(restored.pendingVoiceOutbox)
                outgoingMessages.clear()
                outgoingMessages.putAll(restored.outgoingMessages)
                expectedRecipientsByMessage.clear()
                expectedRecipientsByMessage.putAll(restored.expectedRecipientsByMessage)
                deliveredReceiptsByMessage.clear()
                deliveredReceiptsByMessage.putAll(restored.deliveredRecipientsByMessage)
                deliveredCounts.clear()
                restored.deliveredRecipientsByMessage.forEach { (messageId, recipients) ->
                    deliveredCounts[messageId] = recipients.size
                }
                readReceiptsByMessage.clear()
                readReceiptsByMessage.putAll(restored.readRecipientsByMessage)
                readCounts.clear()
                restored.readRecipientsByMessage.forEach { (messageId, recipients) ->
                    readCounts[messageId] = recipients.size
                }
                sentReadReceipts.clear()
                sentReadReceipts.addAll(restored.sentReadReceipts)
                conversationUpdateOrder.clear()
                conversationUpdateOrder.putAll(restored.conversationUpdateOrder)
                conversationUpdateSequence = restored.conversationUpdateOrder.values.maxOrNull() ?: 0L
                outboundQueueWakeRevision += 1L
                chatStateWritable = true
                trustState = "已恢复本地聊天状态"
            }

            is ChatStateLoadResult.Unreadable -> {
                chatStateLastWrittenSnapshot.set(null)
                trustState = loadResult.cause.readableMessage("本地聊天状态无法验证，已暂停收发")
                appendSystemMessage(
                    text = "本地聊天状态无法验证。为避免覆盖或发送不完整数据，SpotChat 已暂停消息收发。",
                    encrypted = true,
                    conversationId = NEARBY_GROUP_CONVERSATION_ID
                )
            }
        }
        chatStateLoadComplete = true
    }

    LaunchedEffect(
        chatStateOperational,
        conversationMessages.toMap(),
        draftsByConversation.toMap(),
        pendingOutboundMessages.toMap(),
        pendingOutboundVoiceMessages.toMap(),
        outgoingMessages.toMap(),
        expectedRecipientsByMessage.toMap(),
        deliveredReceiptsByMessage.toMap(),
        readReceiptsByMessage.toMap(),
        sentReadReceipts.toList(),
        conversationUpdateOrder.toMap()
    ) {
        if (chatStateOperational) {
            persistChatStateNow()
        }
    }

    fun ensureDirectConversation(storedPeer: StoredTrustedPeer): String {
        val conversationId = directConversationId(storedPeer.fingerprint)
        if (conversationMessages[conversationId] == null) {
            val defaultDisappearingMode = profile.defaultDisappearingMode.toDisappearingMode()
            if (defaultDisappearingMode != DisappearingMessageMode.Off) {
                disappearingModesByConversation[conversationId] = defaultDisappearingMode
            }
            if (!profile.defaultReadReceiptsEnabled) {
                readReceiptsDisabledByConversation[conversationId] = true
            }
            conversationMessages[conversationId] =
                listOfNotNull(
                    ChatBubble(
                        text = "与 ${peerDisplayName(storedPeer)} 的私聊已准备好",
                        mine = false,
                        encrypted = true,
                        timestamp = nowTime(),
                        deliveryState = DeliveryState.System
                    ),
                    defaultDisappearingMode
                        .takeIf { mode -> mode != DisappearingMessageMode.Off }
                        ?.let { mode ->
                            ChatBubble(
                                text = "已套用默认限时消息，后续新消息将在${mode.label}后自动删除",
                                mine = false,
                                encrypted = true,
                                timestamp = nowTime(),
                                deliveryState = DeliveryState.System
                            )
                        },
                    profile.defaultReadReceiptsEnabled
                        .takeUnless { enabled -> enabled }
                        ?.let {
                            ChatBubble(
                                text = "已套用默认隐私设置，本聊天不会发送已读回执",
                                mine = false,
                                encrypted = true,
                                timestamp = nowTime(),
                                deliveryState = DeliveryState.System
                            )
                        }
                )
        }
        return conversationId
    }

    fun appendSecurityChangeAlert(
        storedPeer: StoredTrustedPeer,
        text: String
    ) {
        if (!profile.securityChangeAlertsEnabled) {
            return
        }
        appendSystemMessage(
            text = text,
            encrypted = true,
            conversationId = ensureDirectConversation(storedPeer)
        )
    }

    fun trustedPeer(fingerprint: String): StoredTrustedPeer? =
        trustedPeers.firstOrNull { peer -> peer.fingerprint == fingerprint }

    fun trustedPeer(peer: TrustedPeer): StoredTrustedPeer? =
        trustedPeers.firstOrNull { storedPeer ->
            storedPeer.fingerprint == peer.fingerprint || storedPeer.publicKey == peer.publicKey
        }

    fun isConversationBlocked(conversation: ChatConversation): Boolean =
        conversation.kind == ConversationKind.Direct &&
            conversation.peerFingerprint?.let(::isPeerBlocked) == true

    fun areReadReceiptsEnabled(conversationId: String): Boolean =
        readReceiptsDisabledByConversation[conversationId] != true

    fun removeTrustedPeer(storedPeer: StoredTrustedPeer) {
        trustedPeers.removeAll { existing ->
            existing.fingerprint == storedPeer.fingerprint || existing.publicKey == storedPeer.publicKey
        }
    }

    fun updateTrustedPeerAlias(
        peer: StoredTrustedPeer,
        alias: String
    ) {
        val updatedPeer = trustedPeerStore.updateAlias(peer.fingerprint, alias) ?: return
        val peerIndex = trustedPeers.indexOfFirst { storedPeer -> storedPeer.fingerprint == peer.fingerprint }
        if (peerIndex >= 0) {
            trustedPeers[peerIndex] = updatedPeer
        }
        trustState =
            if (updatedPeer.alias.isBlank()) {
                "已清除联系人备注"
            } else {
                "已备注为 ${updatedPeer.alias}"
            }
    }

    fun updateGroupAbout(
        conversation: ChatConversation,
        about: String
    ) {
        if (conversation.id != NEARBY_GROUP_CONVERSATION_ID) {
            return
        }
        val normalizedAbout =
            about
                .replace("\n", " ")
                .trim()
                .take(MAX_GROUP_ABOUT_CHARS)
                .ifBlank { "附近设备加密群聊" }
        nearbyGroupAbout = normalizedAbout
        trustState = "已更新群公告"
        appendSystemMessage(
            text = "群公告：$normalizedAbout",
            encrypted = true,
            conversationId = conversation.id
        )
    }

    fun toggleFrequentlyForwardedAllowed(conversation: ChatConversation) {
        if (conversation.id != NEARBY_GROUP_CONVERSATION_ID) {
            trustState = "仅群聊支持转发限制"
            return
        }
        val newAllowed = !allowsFrequentlyForwarded(conversation.id)
        frequentlyForwardedAllowedByConversation[conversation.id] = newAllowed
        val policyLabel = frequentlyForwardedPolicyLabel(conversation.id)
        trustState = policyLabel
        appendSystemMessage(
            text =
                if (newAllowed) {
                    "群聊已允许多次转发消息"
                } else {
                    "群聊已禁止多次转发消息"
                },
            encrypted = true,
            conversationId = conversation.id
        )
    }

    fun toggleAnnouncementOnly(conversation: ChatConversation) {
        if (conversation.id != NEARBY_GROUP_CONVERSATION_ID) {
            trustState = "仅群聊支持公告模式"
            return
        }
        val announcementOnly = !isAnnouncementOnly(conversation.id)
        if (!announcementOnly) {
            announcementOnlyByConversation[conversation.id] = false
            trustState = groupPostingPolicyLabel(conversation.id)
            appendSystemMessage(
                text = "群聊已关闭公告模式，成员可继续发言",
                encrypted = true,
                conversationId = conversation.id
            )
            return
        }

        val beforeMutation = captureDurableChatMutation()
        val previousAnnouncementPresent = announcementOnlyByConversation.containsKey(conversation.id)
        val previousAnnouncementOnly = announcementOnlyByConversation[conversation.id]
        val removedPendingMessageIds =
            buildSet {
                pendingOutboundMessages.values
                    .filter { queued -> queued.conversationId == conversation.id }
                    .mapTo(this) { queued -> queued.displayMessageId }
                pendingOutboundVoiceMessages.values
                    .filter { queued -> queued.conversationId == conversation.id }
                    .mapTo(this) { queued -> queued.displayMessageId }
            }
        announcementOnlyByConversation[conversation.id] = true
        draftsByConversation.remove(conversation.id)
        clearPendingOutboundForConversation(
            conversationId = conversation.id,
            markFailed = true,
            persistRemoval = false
        )
        appendSystemMessage(
            text = "群聊已开启公告模式，本机不会发送普通消息",
            encrypted = true,
            conversationId = conversation.id
        )
        val appliedMutation = captureDurableChatMutation()
        persistDurableUserMutation(
            before = beforeMutation,
            applied = appliedMutation,
            requiredState = { state ->
                conversation.id !in state.draftsByConversation &&
                    state.pendingTextOutbox.none { queued -> queued.conversationId == conversation.id } &&
                    state.pendingVoiceOutbox.none { queued -> queued.conversationId == conversation.id } &&
                    state.outgoingEnvelopes.values.none { outgoing ->
                        outgoing.displayMessageId in removedPendingMessageIds
                    } &&
                    state.expectedRecipientsByMessage.keys.none { messageId ->
                        messageId in removedPendingMessageIds
                    }
            },
            successState = groupPostingPolicyLabel(conversation.id),
            failureState = "公告模式未能安全开启，已恢复待发送消息",
            onSuccess = {
                if (
                    isAnnouncementOnly(conversation.id) &&
                    isRecordingVoice &&
                    recordingConversationId == conversation.id
                ) {
                    runCatching(voiceRecorder::cancel)
                    clearVoiceRecordingState()
                }
            },
            onRollback = {
                if (announcementOnlyByConversation[conversation.id] == true) {
                    if (previousAnnouncementPresent) {
                        announcementOnlyByConversation[conversation.id] = previousAnnouncementOnly ?: false
                    } else {
                        announcementOnlyByConversation.remove(conversation.id)
                    }
                }
            }
        )
    }

    fun removeConversationRuntimeState(conversationId: String) {
        val displayMessageIds =
            messagesForConversation(conversationId)
                .mapNotNull { message -> message.messageId }
                .toSet()
        pendingOutboundMessages
            .filterValues { message -> message.conversationId == conversationId }
            .keys
            .toList()
            .forEach { messageId -> pendingOutboundMessages.remove(messageId) }
        pendingOutboundVoiceMessages
            .filterValues { message -> message.conversationId == conversationId }
            .keys
            .toList()
            .forEach { messageId -> pendingOutboundVoiceMessages.remove(messageId) }
        outgoingMessages
            .filterValues { message ->
                message.conversationId == conversationId || message.displayMessageId in displayMessageIds
            }
            .keys
            .toList()
            .forEach { packetMessageId -> outgoingMessages.remove(packetMessageId) }
        displayMessageIds.forEach { messageId ->
            deliveredCounts.remove(messageId)
            deliveredReceiptsByMessage.remove(messageId)
            readCounts.remove(messageId)
            readReceiptsByMessage.remove(messageId)
            expectedRecipientsByMessage.remove(messageId)
        }
        unreadCounts.remove(conversationId)
        mentionCounts.remove(conversationId)
        draftsByConversation.remove(conversationId)
        starredMessageIdsByConversation.remove(conversationId)
        pinnedMessageIdsByConversation.remove(conversationId)
        readReceiptsDisabledByConversation.remove(conversationId)
        notifier.clearConversation(conversationId)
    }

    fun clearConversation(conversation: ChatConversation) {
        val beforeMutation = captureDurableChatMutation()
        val removedMessages = messagesForConversation(conversation.id)
        val removedMessageIds = removedMessages.mapNotNull(ChatBubble::messageId).toSet()
        val removedFallbackKeys =
            removedMessages
                .filter { message -> message.messageId == null }
                .map { message -> Triple(message.createdAtEpochMillis, message.text, message.mine) }
                .toSet()
        removeConversationRuntimeState(conversation.id)
        conversationMessages[conversation.id] =
            listOf(
                ChatBubble(
                    text = "聊天已清空",
                    mine = false,
                    encrypted = true,
                    timestamp = nowTime(),
                    deliveryState = DeliveryState.System
                )
            )
        conversationUpdateSequence += 1
        conversationUpdateOrder[conversation.id] = conversationUpdateSequence
        selectedActionMessage = null
        selectedSecurityPeerFingerprint = null
        pendingQuotedMessage = null
        pendingDirectReply = null
        pendingForwardMessage = null
        pendingMessageEdit = null
        val appliedMutation = captureDurableChatMutation()
        persistDurableUserMutation(
            before = beforeMutation,
            applied = appliedMutation,
            requiredState = { state ->
                state.messagesByConversation[conversation.id].orEmpty().none { persisted ->
                    persisted.messageId in removedMessageIds ||
                        Triple(persisted.createdAtEpochMillis, persisted.text, persisted.mine) in
                        removedFallbackKeys
                } &&
                    state.pendingTextOutbox.none { queued -> queued.conversationId == conversation.id } &&
                    state.pendingVoiceOutbox.none { queued -> queued.conversationId == conversation.id } &&
                    state.outgoingEnvelopes.values.none { outgoing ->
                        outgoing.conversationId == conversation.id ||
                            outgoing.displayMessageId in removedMessageIds
                    }
            },
            successState = "聊天已清空",
            failureState = "聊天未能安全清空，已恢复"
        )
    }

    fun forgetConversationPeer(conversation: ChatConversation) {
        val peerFingerprint = conversation.peerFingerprint ?: return
        val storedPeer = trustedPeer(peerFingerprint) ?: return
        val removedMessages = messagesForConversation(conversation.id)
        val removedOutboundIds =
            buildSet {
                pendingOutboundMessages.values
                    .filter { pending -> pending.conversationId == conversation.id }
                    .mapTo(this) { pending -> pending.displayMessageId }
                pendingOutboundVoiceMessages.values
                    .filter { pending -> pending.conversationId == conversation.id }
                    .mapTo(this) { pending -> pending.displayMessageId }
                outgoingMessages.values
                    .filter { outgoing -> outgoing.conversationId == conversation.id }
                    .mapTo(this) { outgoing -> outgoing.displayMessageId }
            }
        trustedPeerStore.forget(storedPeer.fingerprint, storedPeer.publicKey)
        removeRecipientFromOutboundTracking(storedPeer.fingerprint)
        removeTrustedPeer(storedPeer)
        knownPeersByFingerprint.remove(storedPeer.fingerprint)
        peerLastSeenAt.remove(storedPeer.fingerprint)
        if (activePeerFingerprint == storedPeer.fingerprint) {
            activePeerFingerprint = null
        }
        removeConversationRuntimeState(conversation.id)
        conversationMessages.remove(conversation.id)
        conversationUpdateOrder.remove(conversation.id)
        pinnedConversationIds.remove(conversation.id)
        favoriteConversationIds.remove(conversation.id)
        conversationThemeColors.remove(conversation.id)
        mutedConversations.remove(conversation.id)
        archivedConversationIds.remove(conversation.id)
        blockedPeerFingerprints.remove(storedPeer.fingerprint)
        lockedConversationIds.remove(conversation.id)
        pinnedMessageIdsByConversation.remove(conversation.id)
        disappearingModesByConversation.remove(conversation.id)
        readReceiptsDisabledByConversation.remove(conversation.id)
        selectedActionMessage = null
        selectedSecurityPeerFingerprint = null
        pendingQuotedMessage = null
        pendingDirectReply = null
        pendingForwardMessage = null
        pendingMessageEdit = null
        activeConversationId = NEARBY_GROUP_CONVERSATION_ID
        appSurface = AppSurface.ConversationList
        trustState = "已移除 ${peerDisplayName(storedPeer)}"
        appendSystemMessage(
            text = "已移除 ${peerDisplayName(storedPeer)} 的信任",
            encrypted = true,
            conversationId = NEARBY_GROUP_CONVERSATION_ID
        )
        persistRemovedMessages(conversation.id, removedMessages)
        persistOutboundRemoval(displayMessageIds = removedOutboundIds)
    }

    fun rememberPeerRoute(
        fingerprint: String,
        peer: TransportPeer
    ) {
        knownPeersByFingerprint[fingerprint] = peer
        peerLastSeenAt[fingerprint] = System.currentTimeMillis()
        peerRouteRevision += 1L
    }

    fun routeForPeer(fingerprint: String): TransportPeer? =
        knownPeersByFingerprint[fingerprint]

    fun hasRetryableMessages(conversationId: String): Boolean =
        messagesForConversation(conversationId).any { message -> message.canRetry() }

    fun sortConversations(conversations: List<ChatConversation>): List<ChatConversation> =
        conversations.sortedWith(
            compareByDescending<ChatConversation> { conversation ->
                pinnedConversationIds[conversation.id] == true
            }.thenByDescending { conversation ->
                hasRetryableMessages(conversation.id)
            }.thenByDescending { conversation ->
                draftsByConversation[conversation.id] != null
            }.thenByDescending { conversation ->
                (mentionCounts[conversation.id] ?: 0) > 0
            }.thenByDescending { conversation ->
                (unreadCounts[conversation.id] ?: 0) > 0
            }.thenByDescending { conversation ->
                conversationUpdateOrder[conversation.id] ?: 0L
            }.thenBy { conversation ->
                if (conversation.id == NEARBY_GROUP_CONVERSATION_ID) 0 else 1
            }.thenBy { conversation ->
                conversation.title.lowercase(Locale.getDefault())
            }
        )

    fun conversations(): List<ChatConversation> =
        buildList {
            val groupMemberFingerprints = trustedPeers.map { peer -> peer.fingerprint }
            add(
                ChatConversation(
                    id = NEARBY_GROUP_CONVERSATION_ID,
                    kind = ConversationKind.Group,
                    title = NEARBY_GROUP_TITLE,
                    subtitle =
                        if (trustedPeers.isEmpty()) {
                            "$nearbyGroupAbout · 等待成员"
                        } else {
                            "$nearbyGroupAbout · ${trustedPeers.size} 位成员 · ${groupReachabilityText(groupMemberFingerprints)}"
                        },
                    memberFingerprints = groupMemberFingerprints,
                    themeColor = conversationThemeColors[NEARBY_GROUP_CONVERSATION_ID]
                )
            )
            trustedPeers.forEach { peer ->
                add(
                    ChatConversation(
                        id = directConversationId(peer.fingerprint),
                        kind = ConversationKind.Direct,
                        title = peerDisplayName(peer),
                        subtitle = "${peerAbout(peer)} · ${peerReachabilityText(peer.fingerprint)}",
                        peerFingerprint = peer.fingerprint,
                        memberFingerprints = listOf(peer.fingerprint),
                        themeColor = conversationThemeColors[directConversationId(peer.fingerprint)]
                    )
                )
            }
        }.let(::sortConversations)

    fun visibleConversations(): List<ChatConversation> =
        conversations().filterNot { conversation -> archivedConversationIds[conversation.id] == true }

    fun activeConversation(): ChatConversation =
        conversations().firstOrNull { conversation -> conversation.id == activeConversationId }
            ?: conversations().first()

    fun updateWearStateSnapshot() {
        val summaries =
            visibleConversations().map { conversation ->
                val messages = messagesForConversation(conversation.id)
                val lastMessage =
                    messages.lastOrNull { message -> message.deliveryState != DeliveryState.System }
                val retryableCount =
                    messages.count { message -> message.canRetry() }
                WearConversationSummary(
                    id = conversation.id,
                    title = conversation.title,
                    subtitle =
                        conversationPreview(
                            conversation = conversation,
                            lastMessage = lastMessage,
                            retryableCount = retryableCount,
                            draft = draftsByConversation[conversation.id],
                            locked = isConversationLocked(conversation.id)
                    ),
                    unreadCount = unreadCounts[conversation.id] ?: 0,
                    mentionCount = mentionCounts[conversation.id] ?: 0,
                    updatedAtEpochMillis = conversationUpdateOrder[conversation.id] ?: 0L,
                    isPinned = pinnedConversationIds[conversation.id] == true,
                    isMuted = isConversationMuted(conversation.id)
                )
            }
        val snapshot =
            WearChatSnapshot(
                conversations = summaries,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        wearChatSnapshot = wearStateStore.save(snapshot)
    }

    LaunchedEffect(
        conversationMessages.toMap(),
        unreadCounts.toMap(),
        mentionCounts.toMap(),
        draftsByConversation.toMap(),
        lockedConversationIds.toMap(),
        pinnedConversationIds.toMap(),
        favoriteConversationIds.toMap(),
        conversationThemeColors.toMap(),
        mutedConversations.toMap(),
        archivedConversationIds.toMap(),
        blockedPeerFingerprints.toMap(),
        readReceiptsDisabledByConversation.toMap(),
        disappearingModesByConversation.toMap(),
        trustedPeers.size,
        knownPeersByFingerprint.toMap(),
        peerLastSeenAt.toMap(),
        nearbyGroupAbout
    ) {
        updateWearStateSnapshot()
    }

    suspend fun sendEncryptedAck(
        transport: TransportKind,
        peer: TransportPeer,
        senderFingerprint: String,
        messageId: String,
        status: DeliveryReceiptStatus = DeliveryReceiptStatus.Delivered,
        failureState: String
    ): Boolean =
        runCatching {
            sendPacket(
                transport = transport,
                peer = peer,
                packet =
                    engine.encryptAckForPeer(
                        peerFingerprint = senderFingerprint,
                        deliveredMessageId = messageId,
                        status = status
                    )
            )
        }.fold(
            onSuccess = { true },
            onFailure = {
                trustState = failureState
                false
            }
        )

    fun sendReadReceipt(
        conversationId: String,
        senderFingerprint: String,
        messageId: String,
        peer: TransportPeer? = routeForPeer(senderFingerprint)
    ) {
        if (!chatStateOperational) {
            return
        }
        if (!areReadReceiptsEnabled(conversationId)) {
            trustState = "已读回执已关闭"
            return
        }
        val replyPeer = peer ?: return
        val receiptKey = "$senderFingerprint:$messageId"
        if (receiptKey in sentReadReceipts || !inFlightReadReceipts.add(receiptKey)) {
            return
        }
        coroutineScope.launch {
            try {
                val sent =
                    sendEncryptedAck(
                        transport = transportMode.kind,
                        peer = replyPeer,
                        senderFingerprint = senderFingerprint,
                        messageId = messageId,
                        status = DeliveryReceiptStatus.Read,
                        failureState = "已读回执发送失败"
                    )
                if (!sent) {
                    return@launch
                }
                sentReadReceipts.add(receiptKey)
                persistChatStateNow()
            } finally {
                inFlightReadReceipts.remove(receiptKey)
            }
        }
    }

    fun markConversationRead(conversationId: String) {
        messagesForConversation(conversationId)
            .filter { message ->
                !message.mine &&
                    message.deliveryState != DeliveryState.System &&
                    message.messageId != null &&
                    message.senderFingerprint != null
            }
            .forEach { message ->
                sendReadReceipt(
                    conversationId = conversationId,
                    senderFingerprint = message.senderFingerprint ?: return@forEach,
                    messageId = message.receiptMessageId ?: message.messageId ?: return@forEach
                )
            }
    }

    fun toggleConversationPinned(conversation: ChatConversation) {
        val isPinned = pinnedConversationIds[conversation.id] == true
        if (isPinned) {
            pinnedConversationIds.remove(conversation.id)
        } else {
            pinnedConversationIds[conversation.id] = true
        }
        appendSystemMessage(
            text = if (isPinned) "已取消置顶" else "已置顶聊天",
            encrypted = true,
            conversationId = conversation.id
        )
    }

    fun toggleConversationFavorite(conversation: ChatConversation) {
        val isFavorite = favoriteConversationIds[conversation.id] == true
        if (isFavorite) {
            favoriteConversationIds.remove(conversation.id)
            trustState = "已取消收藏"
        } else {
            favoriteConversationIds[conversation.id] = true
            trustState = "已收藏聊天"
        }
        appendSystemMessage(
            text = if (isFavorite) "已取消收藏" else "已收藏聊天",
            encrypted = true,
            conversationId = conversation.id
        )
    }

    fun cycleConversationTheme(conversation: ChatConversation) {
        val currentColor = conversationThemeColors[conversation.id]
        val nextIndex =
            if (currentColor == null) {
                0
            } else {
                val currentIndex = conversationThemePalette.indexOfFirst { color -> color == currentColor }
                if (currentIndex < 0 || currentIndex == conversationThemePalette.lastIndex) {
                    -1
                } else {
                    currentIndex + 1
                }
            }
        if (nextIndex < 0) {
            conversationThemeColors.remove(conversation.id)
            trustState = "已恢复默认聊天颜色"
        } else {
            conversationThemeColors[conversation.id] = conversationThemePalette[nextIndex]
            trustState = "已切换聊天颜色"
        }
        appendSystemMessage(
            text = if (nextIndex < 0) "已恢复默认聊天颜色" else "已切换聊天颜色",
            encrypted = true,
            conversationId = conversation.id
        )
    }

    fun setConversationMute(
        conversation: ChatConversation,
        preset: MutePreset?
    ) {
        if (preset == null) {
            mutedConversations.remove(conversation.id)
            trustState = "已恢复通知"
        } else {
            val nowEpochMillis = System.currentTimeMillis()
            mutedConversations[conversation.id] =
                MutedConversation(
                    preset = preset,
                    untilEpochMillis = preset.durationMs?.let { duration -> nowEpochMillis + duration }
                )
            notifier.clearConversation(conversation.id)
            trustState = "静音${preset.label}"
        }
        appendSystemMessage(
            text = preset?.let { mutePreset -> "已静音${mutePreset.label}" } ?: "已恢复通知",
            encrypted = true,
            conversationId = conversation.id
        )
    }

    fun toggleConversationMuted(conversation: ChatConversation) {
        val currentPreset = muteState(conversation.id)?.preset
        val nextPreset =
            when (currentPreset) {
                null -> MutePreset.EightHours
                MutePreset.EightHours -> MutePreset.OneWeek
                MutePreset.OneWeek -> MutePreset.Always
                MutePreset.Always -> null
            }
        setConversationMute(conversation, nextPreset)
    }

    fun toggleConversationArchived(conversation: ChatConversation) {
        val isArchived = archivedConversationIds[conversation.id] == true
        if (isArchived) {
            archivedConversationIds.remove(conversation.id)
            trustState = "已取消归档"
        } else {
            archivedConversationIds[conversation.id] = true
            trustState = "已归档聊天"
        }
        appendSystemMessage(
            text = if (isArchived) "已取消归档" else "已归档聊天",
            encrypted = true,
            conversationId = conversation.id
        )
    }

    fun toggleConversationBlocked(conversation: ChatConversation) {
        val peerFingerprint = conversation.peerFingerprint ?: return
        val peerName = trustedPeer(peerFingerprint)?.deviceName ?: conversation.title
        val isBlocked = isPeerBlocked(peerFingerprint)
        if (isBlocked) {
            blockedPeerFingerprints.remove(peerFingerprint)
            trustState = "已解除阻止 $peerName"
            appendSystemMessage(
                text = "已解除阻止 $peerName",
                encrypted = true,
                conversationId = conversation.id
            )
            return
        }

        val beforeMutation = captureDurableChatMutation()
        val previousBlockedPresent = blockedPeerFingerprints.containsKey(peerFingerprint)
        val previousBlocked = blockedPeerFingerprints[peerFingerprint]
        blockedPeerFingerprints[peerFingerprint] = true
        removeRecipientFromOutboundTracking(
            fingerprint = peerFingerprint,
            persistRemoval = false
        )
        pendingOutboundMessages
            .filterValues { message -> message.conversationId == conversation.id }
            .keys
            .toList()
            .forEach { messageId -> pendingOutboundMessages.remove(messageId) }
        pendingOutboundVoiceMessages
            .filterValues { message -> message.conversationId == conversation.id }
            .keys
            .toList()
            .forEach { messageId -> pendingOutboundVoiceMessages.remove(messageId) }
        outgoingMessages
            .filterValues { outgoing ->
                outgoing.conversationId == conversation.id ||
                    outgoing.recipientFingerprint == peerFingerprint ||
                    peerFingerprint in outgoing.expectedRecipients
            }
            .keys
            .toList()
            .forEach { packetMessageId -> outgoingMessages.remove(packetMessageId) }
        appendSystemMessage(
            text = "已阻止 $peerName 的消息",
            encrypted = true,
            conversationId = conversation.id
        )
        val appliedMutation = captureDurableChatMutation()
        persistDurableUserMutation(
            before = beforeMutation,
            applied = appliedMutation,
            requiredState = { state ->
                state.pendingTextOutbox.none { queued ->
                    queued.conversationId == conversation.id ||
                        peerFingerprint in queued.remainingTargetFingerprints
                } &&
                    state.pendingVoiceOutbox.none { queued ->
                        queued.conversationId == conversation.id ||
                            peerFingerprint in queued.remainingTargetFingerprints
                    } &&
                    state.outgoingEnvelopes.values.none { outgoing ->
                        outgoing.conversationId == conversation.id ||
                            outgoing.recipientFingerprint == peerFingerprint ||
                            peerFingerprint in outgoing.expectedRecipients
                    } &&
                    state.expectedRecipientsByMessage.values.none { recipients ->
                        peerFingerprint in recipients
                    }
            },
            successState = "已阻止 $peerName",
            failureState = "未能安全阻止 $peerName，待发送消息已恢复",
            onSuccess = {
                if (isPeerBlocked(peerFingerprint)) {
                    notifier.clearConversation(conversation.id)
                }
            },
            onRollback = {
                if (blockedPeerFingerprints[peerFingerprint] == true) {
                    if (previousBlockedPresent) {
                        blockedPeerFingerprints[peerFingerprint] = previousBlocked ?: false
                    } else {
                        blockedPeerFingerprints.remove(peerFingerprint)
                    }
                }
            }
        )
    }

    fun unblockPeer(peer: StoredTrustedPeer) {
        blockedPeerFingerprints.remove(peer.fingerprint)
        trustState = "已解除阻止 ${peerDisplayName(peer)}"
        conversationById(directConversationId(peer.fingerprint))?.let { conversation ->
            appendSystemMessage(
                text = "已解除阻止 ${peerDisplayName(peer)}",
                encrypted = true,
                conversationId = conversation.id
            )
        }
    }

    fun toggleConversationLocked(conversation: ChatConversation) {
        val isLocked = isConversationLocked(conversation.id)
        if (isLocked) {
            lockedConversationIds.remove(conversation.id)
            trustState = "已解锁聊天预览"
        } else {
            lockedConversationIds[conversation.id] = true
            trustState = "已锁定聊天预览"
        }
        appendSystemMessage(
            text = if (isLocked) "聊天预览已解锁" else "聊天预览已锁定",
            encrypted = true,
            conversationId = conversation.id
        )
    }

    fun toggleConversationReadReceipts(conversation: ChatConversation) {
        val enabled = areReadReceiptsEnabled(conversation.id)
        if (enabled) {
            readReceiptsDisabledByConversation[conversation.id] = true
            sentReadReceipts.removeAll { receiptKey ->
                messagesForConversation(conversation.id).any { message ->
                    message.messageId != null &&
                        message.senderFingerprint != null &&
                        receiptKey == "${message.senderFingerprint}:${message.messageId}"
                }
            }
            trustState = "已关闭已读回执"
        } else {
            readReceiptsDisabledByConversation.remove(conversation.id)
            trustState = "已开启已读回执"
        }
        appendSystemMessage(
            text = if (enabled) "已读回执已关闭" else "已读回执已开启",
            encrypted = true,
            conversationId = conversation.id
        )
    }

    fun toggleConversationUnread(conversation: ChatConversation) {
        val unreadCount = unreadCounts[conversation.id] ?: 0
        if (unreadCount > 0) {
            clearConversationAlerts(conversation.id)
            markConversationRead(conversation.id)
            appendSystemMessage(
                text = "已标为已读",
                encrypted = true,
                conversationId = conversation.id
            )
        } else {
            unreadCounts[conversation.id] = 1
            appendSystemMessage(
                text = "已标为未读",
                encrypted = true,
                conversationId = conversation.id
            )
        }
    }

    fun markConversationsRead(
        conversations: List<ChatConversation>,
        statusText: String,
        shouldMark: (ChatConversation) -> Boolean = { conversation ->
            (unreadCounts[conversation.id] ?: 0) > 0
        }
    ) {
        val targetConversations = conversations.filter(shouldMark)
        targetConversations.forEach { conversation ->
            clearConversationAlerts(conversation.id)
            markConversationRead(conversation.id)
        }
        if (targetConversations.isNotEmpty()) {
            trustState = statusText
        }
    }

    fun setDisappearingMessages(
        conversation: ChatConversation,
        mode: DisappearingMessageMode
    ) {
        if (mode == DisappearingMessageMode.Off) {
            disappearingModesByConversation.remove(conversation.id)
        } else {
            disappearingModesByConversation[conversation.id] = mode
        }
        appendSystemMessage(
            text = disappearingSystemMessage(mode),
            encrypted = true,
            conversationId = conversation.id
        )
        trustState = "限时消息${mode.label}"
    }

    fun toggleDisappearingMessages(conversation: ChatConversation) {
        val nextMode =
            (disappearingModesByConversation[conversation.id] ?: DisappearingMessageMode.Off).next()
        setDisappearingMessages(conversation, nextMode)
    }

    fun toggleMessageStarred(
        conversation: ChatConversation,
        message: ChatBubble
    ) {
        val starId = message.stableStarId()
        val currentIds = starredMessageIds(conversation.id)
        if (starId in currentIds) {
            val updatedIds = currentIds - starId
            if (updatedIds.isEmpty()) {
                starredMessageIdsByConversation.remove(conversation.id)
            } else {
                starredMessageIdsByConversation[conversation.id] = updatedIds
            }
            trustState = "已取消星标"
        } else {
            starredMessageIdsByConversation[conversation.id] = currentIds + starId
            trustState = "已星标消息"
        }
    }

    fun toggleMessagePinned(
        conversation: ChatConversation,
        message: ChatBubble
    ) {
        val pinId = message.stableStarId()
        if (pinnedMessageIdsByConversation[conversation.id] == pinId) {
            pinnedMessageIdsByConversation.remove(conversation.id)
            trustState = "已取消置顶消息"
        } else {
            pinnedMessageIdsByConversation[conversation.id] = pinId
            trustState = "已置顶消息"
        }
    }

    fun copyMessageText(message: ChatBubble) {
        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboardManager == null) {
            trustState = "无法访问剪贴板"
            return
        }
        val copyText = message.copyText()
        if (copyText.isBlank()) {
            trustState = "没有可复制内容"
            return
        }
        clipboardManager.setPrimaryClip(
            ClipData.newPlainText("SpotChat message", copyText)
        )
        trustState = "已复制消息"
        showActionConfirmation(
            title = "已复制消息",
            detail = "内容已放入剪贴板"
        )
    }

    fun copyMessageInfo(
        conversation: ChatConversation,
        message: ChatBubble
    ) {
        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboardManager == null) {
            trustState = "无法访问剪贴板"
            return
        }
        val infoText =
            messageInfoText(
                conversation = conversation,
                message = message,
                receiptSummary = messageReceiptSummary(conversation, message),
                reactionDetails = reactionDetails(message)
            )
        if (infoText.isBlank()) {
            trustState = "没有消息信息"
            return
        }
        clipboardManager.setPrimaryClip(
            ClipData.newPlainText("SpotChat message info", infoText)
        )
        trustState = "已复制消息信息"
        showActionConfirmation(
            title = "已复制消息信息",
            detail = "可在手机或桌面继续查看"
        )
    }

    fun copySafetyCode(peer: StoredTrustedPeer) {
        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboardManager == null) {
            trustState = "无法访问剪贴板"
            return
        }
        val safetyText =
            buildString {
                appendLine("SpotChat 安全校验")
                appendLine("设备：${peer.deviceName}")
                appendLine("指纹：${SpotChatCrypto.displayFingerprint(peer.fingerprint)}")
                append("校验码：${peer.pairingCode}")
            }
        clipboardManager.setPrimaryClip(
            ClipData.newPlainText("SpotChat safety code", safetyText)
        )
        trustState = "已复制 ${peer.deviceName} 的校验码"
    }

    fun applyMessageReaction(
        conversationId: String,
        targetMessageId: String,
        senderFingerprint: String,
        reactionCode: String
    ) {
        conversationMessages[conversationId] =
            messagesForConversation(conversationId).map { message ->
                if (message.messageId == targetMessageId) {
                    message.copy(reactions = message.reactions + (senderFingerprint to reactionCode))
                } else {
                    message
                }
            }
    }

    fun removeMessageReaction(
        conversationId: String,
        targetMessageId: String,
        senderFingerprint: String
    ) {
        conversationMessages[conversationId] =
            messagesForConversation(conversationId).map { message ->
                if (message.messageId == targetMessageId) {
                    message.copy(reactions = message.reactions - senderFingerprint)
                } else {
                    message
                }
            }
        selectedActionMessage =
            selectedActionMessage?.let { actionMessage ->
                if (actionMessage.messageId == targetMessageId) {
                    actionMessage.copy(reactions = actionMessage.reactions - senderFingerprint)
                } else {
                    actionMessage
                }
            }
    }

    fun removeMessageCaches(
        conversationId: String,
        message: ChatBubble,
        removeStar: Boolean = true
    ) {
        val starId = message.stableStarId()
        val displayMessageId = message.messageId
        if (removeStar) {
            val updatedStarIds = starredMessageIds(conversationId) - starId
            if (updatedStarIds.isEmpty()) {
                starredMessageIdsByConversation.remove(conversationId)
            } else {
                starredMessageIdsByConversation[conversationId] = updatedStarIds
            }
        }
        if (pinnedMessageIdsByConversation[conversationId] == starId) {
            pinnedMessageIdsByConversation.remove(conversationId)
        }
        if (displayMessageId != null) {
            pendingOutboundMessages.remove(displayMessageId)
            pendingOutboundVoiceMessages.remove(displayMessageId)
            deliveredCounts.remove(displayMessageId)
            deliveredReceiptsByMessage.remove(displayMessageId)
            readCounts.remove(displayMessageId)
            readReceiptsByMessage.remove(displayMessageId)
            expectedRecipientsByMessage.remove(displayMessageId)
            outgoingMessages
                .filterValues { outgoingMessage ->
                    outgoingMessage.conversationId == conversationId &&
                        outgoingMessage.displayMessageId == displayMessageId
                }
                .keys
                .toList()
                .forEach { packetMessageId -> outgoingMessages.remove(packetMessageId) }
        }
        pendingQuotedMessage =
            pendingQuotedMessage?.takeUnless { quote -> quote.messageId == displayMessageId }
    }

    fun clearConversationKeepingStarred(conversation: ChatConversation) {
        val starredIds = starredMessageIds(conversation.id)
        if (starredIds.isEmpty()) {
            clearConversation(conversation)
            return
        }
        val messages = messagesForConversation(conversation.id)
        val keptMessages =
            messages.filter { message ->
                message.deliveryState != DeliveryState.System &&
                    message.stableStarId() in starredIds
            }
        val removedMessages =
            messages.filterNot { message ->
                message.deliveryState != DeliveryState.System &&
                    message.stableStarId() in starredIds
            }
        removedMessages.forEach { message ->
            removeMessageCaches(conversation.id, message, removeStar = false)
        }
        conversationMessages[conversation.id] =
            listOf(
                ChatBubble(
                    text = "已清空聊天，保留 ${keptMessages.size} 条星标消息",
                    mine = false,
                    encrypted = true,
                    timestamp = nowTime(),
                    deliveryState = DeliveryState.System
                )
            ) + keptMessages
        conversationUpdateSequence += 1
        conversationUpdateOrder[conversation.id] = conversationUpdateSequence
        unreadCounts.remove(conversation.id)
        mentionCounts.remove(conversation.id)
        val pinnedId = pinnedMessageIdsByConversation[conversation.id]
        if (pinnedId != null && pinnedId !in starredIds) {
            pinnedMessageIdsByConversation.remove(conversation.id)
        }
        notifier.clearConversation(conversation.id)
        selectedActionMessage =
            selectedActionMessage?.takeIf { message ->
                message.stableStarId() in starredIds
            }
        selectedSecurityPeerFingerprint = null
        pendingQuotedMessage =
            pendingQuotedMessage?.takeIf { quote ->
                keptMessages.any { message -> message.messageId == quote.messageId }
            }
        pendingDirectReply = null
        pendingForwardMessage =
            pendingForwardMessage?.takeIf { message ->
                message.stableStarId() in starredIds
            }
        pendingMessageEdit =
            pendingMessageEdit?.takeIf { edit ->
                keptMessages.any { message -> message.messageId == edit.messageId }
            }
        trustState = "已保留星标消息"
        persistRemovedMessages(conversation.id, removedMessages)
    }

    fun deleteMessageForMe(
        conversation: ChatConversation,
        message: ChatBubble
    ) {
        val beforeMutation = captureDurableChatMutation()
        val starId = message.stableStarId()
        conversationMessages[conversation.id] =
            messagesForConversation(conversation.id)
                .filterNot { existingMessage ->
                    existingMessage.stableStarId() == starId
                }
        removeMessageCaches(conversation.id, message)
        selectedActionMessage = null
        pendingForwardMessage =
            pendingForwardMessage?.takeUnless { pendingMessage ->
                pendingMessage.stableStarId() == message.stableStarId()
            }
        pendingMessageEdit =
            pendingMessageEdit?.takeUnless { edit ->
                edit.messageId == message.messageId
            }
        appSurface = AppSurface.Chat
        val appliedMutation = captureDurableChatMutation()
        persistDurableUserMutation(
            before = beforeMutation,
            applied = appliedMutation,
            requiredState = { state ->
                state.messagesByConversation[conversation.id].orEmpty().none { persisted ->
                    if (message.messageId != null) {
                        persisted.messageId == message.messageId
                    } else {
                        persisted.createdAtEpochMillis == message.createdAtEpochMillis &&
                            persisted.text == message.text &&
                            persisted.mine == message.mine
                    }
                } &&
                    state.pendingTextOutbox.none { queued ->
                        queued.displayMessageId == message.messageId
                    } &&
                    state.pendingVoiceOutbox.none { queued ->
                        queued.displayMessageId == message.messageId
                    } &&
                    state.outgoingEnvelopes.values.none { outgoing ->
                        outgoing.displayMessageId == message.messageId
                    }
            },
            successState = "已删除本机消息",
            failureState = "消息未能安全删除，已恢复"
        )
    }

    fun sweepExpiredMessages(nowEpochMillis: Long = System.currentTimeMillis()) {
        conversationMessages.keys.toList().forEach { conversationId ->
            val messages = messagesForConversation(conversationId)
            val expiredMessages =
                messages.filter { message ->
                    message.deliveryState != DeliveryState.System &&
                        message.expiresAtEpochMillis?.let { expiresAt -> expiresAt <= nowEpochMillis } == true
                }
            if (expiredMessages.isEmpty()) {
                return@forEach
            }
            val expiredIds = expiredMessages.map { message -> message.stableStarId() }.toSet()
            conversationMessages[conversationId] =
                messages.filterNot { message -> message.stableStarId() in expiredIds }
            expiredMessages.forEach { message ->
                removeMessageCaches(conversationId, message)
            }
            conversationUpdateSequence += 1
            conversationUpdateOrder[conversationId] = conversationUpdateSequence
            if (selectedActionMessage?.stableStarId() in expiredIds) {
                selectedActionMessage = null
                appSurface = AppSurface.Chat
            }
            notifier.clearConversation(conversationId)
            persistRemovedMessages(conversationId, expiredMessages)
        }
    }

    LaunchedEffect(
        conversationMessages.toMap(),
        disappearingModesByConversation.toMap()
    ) {
        while (true) {
            sweepExpiredMessages()
            delay(DISAPPEARING_SWEEP_INTERVAL_MS)
        }
    }

    LaunchedEffect(mutedConversations.toMap()) {
        while (mutedConversations.isNotEmpty()) {
            val nowEpochMillis = System.currentTimeMillis()
            mutedConversations
                .filterValues { mute -> !mute.isActive(nowEpochMillis) }
                .keys
                .toList()
                .forEach { conversationId -> mutedConversations.remove(conversationId) }
            delay(MUTE_SWEEP_INTERVAL_MS)
        }
    }

    fun openConversation(conversation: ChatConversation) {
        if (isRecordingVoice && recordingConversationId != conversation.id) {
            voiceRecorder.cancel()
            clearVoiceRecordingState()
            trustState = "切换聊天，已取消录音"
        }
        wearEntryRecoveryState = null
        wearEntryContext = null
        activeConversationId = conversation.id
        clearConversationAlerts(conversation.id)
        selectedActionMessage = null
        appSurface = AppSurface.Chat
        markConversationRead(conversation.id)
    }

    fun wearEntryLatestPreview(conversation: ChatConversation): String =
        messagesForConversation(conversation.id)
            .lastOrNull { message -> message.deliveryState != DeliveryState.System }
            ?.previewText()
            ?.ifBlank { "暂无消息预览" }
            ?: "暂无消息预览"

    fun notificationReplyPreview(replyText: String): String =
        replyText
            .trim()
            .replace('\n', ' ')
            .take(MAX_NOTIFICATION_CONFIRMATION_PREVIEW_CHARS)

    fun openConversationFromWearEntry(
        conversation: ChatConversation,
        source: String,
        action: WearEntryAction
    ) {
        val unreadCount = unreadCounts[conversation.id] ?: 0
        val latestPreview = wearEntryLatestPreview(conversation)
        openConversation(conversation)
        wearEntryContextId += 1
        wearEntryContext =
            WearEntryContext(
                id = wearEntryContextId,
                conversationId = conversation.id,
                source = source,
                action = action,
                unreadCount = unreadCount,
                latestPreview = latestPreview
            )
    }

    fun showWearEntryRecovery(
        source: String,
        targetConversationId: String?,
        actionLabel: String
    ) {
        wearEntryRecoveryState =
            WearEntryRecoveryState(
                source = source,
                targetConversationId = targetConversationId,
                actionLabel = actionLabel
            )
        appSurface = AppSurface.EntryRecovery
        trustState = "$source 目标已不可用"
    }

    fun mergePeerWithHello(
        eventPeer: TransportPeer,
        hello: PeerHello
    ): TransportPeer {
        val existing =
            activePeer?.takeIf { peer ->
                peer.kind == eventPeer.kind && peer.address == eventPeer.address
            }
        val hintedPort =
            if (eventPeer.kind == TransportKind.LAN) {
                hello.lanPort()
            } else {
                null
            }
        return (existing ?: eventPeer).copy(
            name = hello.deviceName,
            port = hintedPort ?: existing?.port ?: eventPeer.port
        )
    }

    fun samePeerRoute(
        first: TransportPeer,
        second: TransportPeer
    ): Boolean =
        first.kind == second.kind && first.address == second.address

    fun verifiedRouteFor(
        fingerprint: String,
        eventPeer: TransportPeer
    ): TransportPeer? {
        val verifiedRoute = routeForPeer(fingerprint) ?: return null
        if (!samePeerRoute(verifiedRoute, eventPeer)) {
            return null
        }
        peerLastSeenAt[fingerprint] = System.currentTimeMillis()
        return verifiedRoute
    }

    fun upsertIncomingMessage(
        conversationId: String,
        message: ChatBubble,
        recordAttention: Boolean = true
    ): Boolean {
        val messageId = message.messageId ?: run {
            appendMessage(conversationId, message, recordAttention = recordAttention)
            return true
        }
        val existingMessages = messagesForConversation(conversationId)
        val existingIndex =
            existingMessages.indexOfFirst { existing ->
                !existing.mine &&
                    existing.messageId == messageId &&
                    existing.senderFingerprint == message.senderFingerprint
            }
        if (existingIndex < 0) {
            appendMessage(conversationId, message, recordAttention = recordAttention)
            return true
        }
        val existing = existingMessages[existingIndex]
        val updated =
            message.copy(
                timestamp = existing.timestamp,
                createdAtEpochMillis = existing.createdAtEpochMillis,
                expiresAtEpochMillis = existing.expiresAtEpochMillis,
                reactions = existing.reactions
            )
        conversationMessages[conversationId] =
            existingMessages.toMutableList().apply { this[existingIndex] = updated }
        conversationUpdateSequence += 1
        conversationUpdateOrder[conversationId] = conversationUpdateSequence
        return false
    }

    fun incomingMessage(
        conversationId: String,
        senderFingerprint: String,
        messageId: String
    ): ChatBubble? =
        messagesForConversation(conversationId).firstOrNull { message ->
            !message.mine &&
                message.senderFingerprint == senderFingerprint &&
                message.messageId == messageId
        }

    fun restoreIncomingMessage(
        conversationId: String,
        senderFingerprint: String,
        messageId: String,
        appliedReceiptMessageId: String,
        previousMessage: ChatBubble?,
        previousConversationOrder: Long?,
        mutationConversationOrder: Long?
    ) {
        val currentMessages = messagesForConversation(conversationId)
        val currentIndex =
            currentMessages.indexOfFirst { message ->
                !message.mine &&
                    message.senderFingerprint == senderFingerprint &&
                    message.messageId == messageId
            }
        if (currentIndex < 0) {
            return
        }
        val currentMessage = currentMessages[currentIndex]
        if (currentMessage.receiptMessageId != appliedReceiptMessageId) {
            return
        }
        conversationMessages[conversationId] =
            currentMessages.toMutableList().apply {
                if (previousMessage == null) {
                    removeAt(currentIndex)
                } else {
                    this[currentIndex] = previousMessage.copy(reactions = currentMessage.reactions)
                }
            }
        if (conversationUpdateOrder[conversationId] == mutationConversationOrder) {
            if (previousConversationOrder == null) {
                conversationUpdateOrder.remove(conversationId)
            } else {
                conversationUpdateOrder[conversationId] = previousConversationOrder
            }
        }
    }

    suspend fun repairChatStateAfterIncomingRollback() {
        if (chatStateWritable) {
            persistChatStateNow()
        }
    }

    fun restoreReceiptTracking(
        receiptsByMessage: MutableMap<String, Set<String>>,
        countsByMessage: MutableMap<String, Int>,
        displayMessageId: String,
        senderFingerprint: String,
        previousRecipients: Set<String>,
        previousCountPresent: Boolean,
        previousCount: Int?,
        appliedRecipients: Set<String>
    ) {
        val currentRecipients = receiptsByMessage[displayMessageId].orEmpty()
        val restoredRecipients =
            if (currentRecipients == appliedRecipients) {
                previousRecipients
            } else if (senderFingerprint in previousRecipients) {
                currentRecipients + senderFingerprint
            } else {
                currentRecipients - senderFingerprint
            }
        if (restoredRecipients.isEmpty()) {
            receiptsByMessage.remove(displayMessageId)
        } else {
            receiptsByMessage[displayMessageId] = restoredRecipients
        }
        if (currentRecipients == appliedRecipients && previousCountPresent) {
            countsByMessage[displayMessageId] = previousCount ?: previousRecipients.size
        } else if (currentRecipients == appliedRecipients || restoredRecipients.isEmpty()) {
            countsByMessage.remove(displayMessageId)
        } else {
            countsByMessage[displayMessageId] = restoredRecipients.size
        }
    }

    suspend fun rememberMalformedAuthenticatedPacket(
        encryptedMessage: EncryptedChatMessage,
        ignoredState: String,
        duplicateState: String,
        failureState: String
    ) {
        try {
            withContext(Dispatchers.IO) {
                engine.rememberAuthenticatedPacket(encryptedMessage)
            }
            trustState = ignoredState
        } catch (error: CancellationException) {
            throw error
        } catch (_: DuplicateMessageException) {
            trustState = duplicateState
        } catch (error: Exception) {
            trustState = error.readableMessage(failureState)
        }
    }

    suspend fun sendSessionChallenge(
        transport: TransportKind,
        peer: TransportPeer,
        openedPeer: TrustedPeer,
        responderHello: PeerHello
    ) {
        val outgoing =
            sessionCoordinator.prepareChallenge(
                peer = peer,
                openedPeer = openedPeer,
                responderHello = responderHello,
                transports = transportHints()
            ) ?: return
        runCatching {
            sendPacket(transport, peer, outgoing.packet)
        }.onFailure { error ->
            sessionCoordinator.discardChallenge(outgoing.challengeId)
            trustState = error.readableMessage("设备身份验证失败")
        }
    }

    fun acceptVerifiedPeer(
        openedPeer: TrustedPeer,
        verifiedRoute: TransportPeer
    ) {
        activePeer = verifiedRoute
        pairingCode = openedPeer.pairingCode
        val storedPeer = trustedPeer(openedPeer)
        if (storedPeer != null && storedPeer.publicKey == openedPeer.publicKey) {
            sessionCoordinator.confirmSession(openedPeer.fingerprint)
            rememberPeerRoute(openedPeer.fingerprint, verifiedRoute)
            val refreshedPeer = trustedPeerStore.trust(openedPeer)
            removeTrustedPeer(refreshedPeer)
            trustedPeers.add(0, refreshedPeer)
            ensureDirectConversation(refreshedPeer)
            pendingPeer = null
            pendingPeerRoute = null
            activePeerFingerprint = openedPeer.fingerprint
            trustState = "已验证 ${refreshedPeer.deviceName}"
        } else {
            pendingPeer
                ?.takeIf { peer -> peer.fingerprint != openedPeer.fingerprint }
                ?.let { peer -> sessionCoordinator.rejectPendingSession(peer.fingerprint) }
            sessionCoordinator.protectPendingSessionForTrust(openedPeer.fingerprint)
            activePeerFingerprint = null
            pendingPeer = openedPeer
            pendingPeerRoute = verifiedRoute
            trustState = "待确认 ${openedPeer.deviceName}"
            appendSystemMessage(
                text = "已验证 ${openedPeer.deviceName} 持有身份密钥，请核对校验码",
                encrypted = true
            )
        }
    }

    suspend fun handleTransportEvent(
        transport: TransportKind,
        event: TransportEvent
    ) {
        when (val action = classifyTransportIngress(event)) {
            is TransportIngressAction.Status -> {
                trustState = action.text
            }

            is TransportIngressAction.PeerDiscovered -> {
                val discoveredPeer = action.peer
                activePeer = discoveredPeer
                trustState = "发现 ${discoveredPeer.name}"
                if (sessionCoordinator.shouldSendHello(discoveredPeer)) {
                    coroutineScope.launch {
                        sendHello(transport, discoveredPeer)
                    }
                }
            }

            is TransportIngressAction.DecodedFrame -> {
                val sourcePeer = action.peer
                val packet = action.packet
                if (packet.version != WirePacket.CURRENT_VERSION) {
                    trustState = "已忽略不兼容协议 v${packet.version}"
                    return
                }
                when (packet.kind) {
                    PacketKind.HELLO -> {
                        val hello = packet.hello ?: return
                        if (!sessionCoordinator.allowHandshake(sourcePeer)) {
                            trustState = "握手请求过于频繁，已暂时忽略"
                            return
                        }
                        val openedPeer =
                            runCatching { sessionCoordinator.openSession(hello) }
                                .getOrElse { error ->
                                    trustState = error.readableMessage("无效的设备握手")
                                    return
                                }
                        val mergedPeer = mergePeerWithHello(sourcePeer, hello)
                        activePeer = mergedPeer
                        pairingCode = openedPeer.pairingCode
                        trustState = "正在验证 ${openedPeer.deviceName}"
                        if (sessionCoordinator.shouldSendHello(mergedPeer)) {
                            coroutineScope.launch {
                                sendHello(transport, mergedPeer)
                            }
                        }
                        sendSessionChallenge(transport, mergedPeer, openedPeer, hello)
                    }

                    PacketKind.SESSION_CHALLENGE -> {
                        val challenge = packet.sessionChallenge ?: return
                        val nowEpochMillis = System.currentTimeMillis()
                        val localHello =
                            sessionCoordinator.helloPacket(
                                transports = transportHints()
                            ).hello ?: error("Missing local hello")
                        if (
                            !sessionCoordinator.isValidIncomingChallenge(
                                challenge = challenge,
                                localHello = localHello,
                                nowEpochMillis = nowEpochMillis
                            )
                        ) {
                            trustState = "已忽略无效身份挑战"
                            return
                        }
                        if (!sessionCoordinator.allowHandshake(sourcePeer)) {
                            trustState = "身份挑战过于频繁，已暂时忽略"
                            return
                        }
                        val openedPeer =
                            runCatching { sessionCoordinator.openSession(challenge.challengerHello) }
                                .getOrElse { error ->
                                    trustState = error.readableMessage("无法验证挑战设备")
                                    return
                                }
                        val mergedPeer = mergePeerWithHello(sourcePeer, challenge.challengerHello)
                        val confirmation =
                            runCatching {
                                sessionCoordinator.createConfirmation(
                                    peerFingerprint = openedPeer.fingerprint,
                                    challenge = challenge
                                )
                            }.getOrElse { error ->
                                trustState = error.readableMessage("无法创建身份确认")
                                return
                            }
                        runCatching {
                            sendPacket(transport, mergedPeer, confirmation)
                        }.onFailure { error ->
                            trustState = error.readableMessage("身份确认发送失败")
                        }
                        sendSessionChallenge(
                            transport,
                            mergedPeer,
                            openedPeer,
                            challenge.challengerHello
                        )
                    }

                    PacketKind.ENCRYPTED_SESSION_CONFIRM -> {
                        val encryptedConfirmation = packet.encryptedMessage ?: return
                        val pending =
                            when (
                                val match =
                                    sessionCoordinator.matchConfirmation(
                                        encryptedConfirmation = encryptedConfirmation,
                                        peer = sourcePeer
                                    )
                            ) {
                                ConfirmationMatch.Missing -> return
                                ConfirmationMatch.RouteMismatch -> {
                                    trustState = "已拒绝来自其他路由的身份确认"
                                    return
                                }

                                is ConfirmationMatch.Found -> match.pending
                            }
                        val confirmation =
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    sessionCoordinator.decryptConfirmation(encryptedConfirmation)
                                }
                            }
                                .getOrElse { error ->
                                    trustState = error.readableMessage("身份确认校验失败")
                                    return
                                }
                        if (
                            !sessionCoordinator.validateAndConsumeConfirmation(
                                encryptedConfirmation = encryptedConfirmation,
                                confirmation = confirmation,
                                pending = pending
                            )
                        ) {
                            trustState = "身份确认内容不匹配"
                            return
                        }
                        acceptVerifiedPeer(pending.openedPeer, pending.peer)
                    }

                    PacketKind.ENCRYPTED_MESSAGE -> {
                        val encryptedMessage = packet.encryptedMessage ?: return
                        val storedSender = trustedPeer(encryptedMessage.senderFingerprint)
                        if (storedSender == null) {
                            trustState = "拦截未确认消息"
                            return
                        }
                        if (isPeerBlocked(encryptedMessage.senderFingerprint)) {
                            trustState = "已忽略被阻止联系人"
                            return
                        }
                        val verifiedRoute =
                            verifiedRouteFor(encryptedMessage.senderFingerprint, sourcePeer)
                                ?: run {
                                    trustState = "拦截未验证路由的消息"
                                    return
                                }
                        try {
                            val plain =
                                withContext(Dispatchers.IO) {
                                    engine.decryptText(encryptedMessage, rememberReplay = false)
                                }
                            val payload =
                                decodeChatPayload(plain.text).also { decoded ->
                                    decoded.validateAuthenticatedIncoming(encryptedMessage)
                                }
                            withContext(Dispatchers.IO) {
                                engine.ensureAuthenticatedPacketIsNew(encryptedMessage)
                            }
                            val conversationId =
                                if (payload.kind == CHAT_PAYLOAD_KIND_GROUP) {
                                    NEARBY_GROUP_CONVERSATION_ID
                                } else {
                                    directConversationId(plain.senderFingerprint)
                                }
                            val payloadForwardCount = payload.normalizedForwardCount()
                            if (shouldBlockFrequentlyForwarded(conversationId, payloadForwardCount)) {
                                val durableDrop =
                                    persistChatStateNow { state ->
                                        state.messagesByConversation[conversationId].orEmpty().none { persisted ->
                                            persisted.senderFingerprint == plain.senderFingerprint &&
                                                persisted.receiptMessageId == plain.envelopeMessageId
                                        }
                                    }
                                if (!durableDrop) {
                                    trustState = "消息拦截状态未能安全保存"
                                    return
                                }
                                withContext(Dispatchers.IO) {
                                    engine.rememberAuthenticatedPacket(encryptedMessage)
                                }
                                appendSystemMessage(
                                    text = "${storedSender.deviceName} 的${forwardLabel(payloadForwardCount)}消息已被群聊设置拦截",
                                    encrypted = true,
                                    conversationId = conversationId
                                )
                                trustState = "已拦截多次转发"
                            } else {
                                val previousMessage =
                                    incomingMessage(
                                        conversationId = conversationId,
                                        senderFingerprint = plain.senderFingerprint,
                                        messageId = plain.messageId
                                    )
                                val previousConversationOrder = conversationUpdateOrder[conversationId]
                                val inserted =
                                    upsertIncomingMessage(
                                        conversationId = conversationId,
                                        message =
                                            ChatBubble(
                                                text = payload.text,
                                                mine = false,
                                                encrypted = true,
                                                timestamp = nowTime(),
                                                senderName =
                                                    if (payload.kind == CHAT_PAYLOAD_KIND_GROUP) {
                                                        storedSender.deviceName
                                                    } else {
                                                        null
                                                    },
                                                senderFingerprint = plain.senderFingerprint,
                                                messageId = plain.messageId,
                                                receiptMessageId = plain.envelopeMessageId,
                                                quotedMessage = payload.quote,
                                                deliveryState = DeliveryState.Received,
                                                forwarded = payload.forwarded,
                                                forwardCount = payloadForwardCount
                                            ),
                                        recordAttention = false
                                    )
                                val mutationConversationOrder = conversationUpdateOrder[conversationId]
                                val appliedMessage =
                                    requireNotNull(
                                        incomingMessage(
                                            conversationId = conversationId,
                                            senderFingerprint = plain.senderFingerprint,
                                            messageId = plain.messageId
                                        )
                                    )
                                val expectedPersistedMessage = requireNotNull(appliedMessage.toPersisted())
                                val durable =
                                    persistChatStateNow { state ->
                                        state.messagesByConversation[conversationId].orEmpty()
                                            .any { persisted -> persisted == expectedPersistedMessage }
                                    }
                                if (!durable) {
                                    restoreIncomingMessage(
                                        conversationId = conversationId,
                                        senderFingerprint = plain.senderFingerprint,
                                        messageId = plain.messageId,
                                        appliedReceiptMessageId = plain.envelopeMessageId,
                                        previousMessage = previousMessage,
                                        previousConversationOrder = previousConversationOrder,
                                        mutationConversationOrder = mutationConversationOrder
                                    )
                                    repairChatStateAfterIncomingRollback()
                                    trustState = "消息未能安全保存，已忽略"
                                    return
                                }
                                withContext(Dispatchers.IO) {
                                    engine.rememberAuthenticatedPacket(encryptedMessage)
                                }
                                if (inserted) {
                                    recordIncomingAttention(conversationId, appliedMessage)
                                }
                                trustState =
                                    if (inserted) "收到加密消息" else "收到消息更新"
                            }
                            sendEncryptedAck(
                                transport = transport,
                                peer = verifiedRoute,
                                senderFingerprint = plain.senderFingerprint,
                                messageId = plain.envelopeMessageId,
                                failureState = "回执发送失败"
                            )
                            if (appSurface == AppSurface.Chat && activeConversationId == conversationId) {
                                sendReadReceipt(
                                    conversationId = conversationId,
                                    senderFingerprint = plain.senderFingerprint,
                                    messageId = plain.envelopeMessageId,
                                    peer = verifiedRoute
                                )
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: AuthenticatedPayloadDecodingException) {
                            rememberMalformedAuthenticatedPacket(
                                encryptedMessage = encryptedMessage,
                                ignoredState = "认证消息内容无效，已忽略",
                                duplicateState = "重复的无效消息已忽略",
                                failureState = "无效消息记录失败"
                            )
                        } catch (error: DuplicateMessageException) {
                            trustState = "重复消息已忽略"
                            sendEncryptedAck(
                                transport = transport,
                                peer = verifiedRoute,
                                senderFingerprint = error.senderFingerprint,
                                messageId = error.messageId,
                                failureState = "重复回执发送失败"
                            )
                        } catch (_: Exception) {
                            trustState = "解密失败"
                        }
                    }

                    PacketKind.ENCRYPTED_VOICE_MESSAGE -> {
                        val encryptedMessage = packet.encryptedMessage ?: return
                        val storedSender = trustedPeer(encryptedMessage.senderFingerprint)
                        if (storedSender == null) {
                            trustState = "拦截未确认语音"
                            return
                        }
                        if (isPeerBlocked(encryptedMessage.senderFingerprint)) {
                            trustState = "已忽略被阻止语音"
                            return
                        }
                        val verifiedRoute =
                            verifiedRouteFor(encryptedMessage.senderFingerprint, sourcePeer)
                                ?: run {
                                    trustState = "拦截未验证路由的语音"
                                    return
                                }
                        try {
                            val plain =
                                withContext(Dispatchers.IO) {
                                    engine.decryptVoice(encryptedMessage, rememberReplay = false)
                                }
                            if (
                                plain.groupId != null &&
                                plain.groupId != NEARBY_GROUP_CONVERSATION_ID
                            ) {
                                invalidAuthenticatedPayload(
                                    encryptedMessage,
                                    "Unsupported voice-message group"
                                )
                            }
                            withContext(Dispatchers.IO) {
                                engine.ensureAuthenticatedPacketIsNew(encryptedMessage)
                            }
                            val conversationId =
                                if (plain.groupId != null) {
                                    NEARBY_GROUP_CONVERSATION_ID
                                } else {
                                    directConversationId(plain.senderFingerprint)
                                }
                            val previousMessage =
                                incomingMessage(
                                    conversationId = conversationId,
                                    senderFingerprint = plain.senderFingerprint,
                                    messageId = plain.messageId
                                )
                            val previousConversationOrder = conversationUpdateOrder[conversationId]
                            val inserted =
                                upsertIncomingMessage(
                                    conversationId = conversationId,
                                    message =
                                        ChatBubble(
                                            text = "语音消息 · ${formatDuration(plain.durationMs)}",
                                            mine = false,
                                            encrypted = true,
                                            timestamp = nowTime(),
                                            senderName = storedSender.deviceName.takeIf { plain.groupId != null },
                                            senderFingerprint = plain.senderFingerprint,
                                            messageId = plain.messageId,
                                            receiptMessageId = plain.envelopeMessageId,
                                            deliveryState = DeliveryState.Received,
                                            kind = ChatMessageKind.Voice,
                                            voiceDurationMs = plain.durationMs,
                                            voiceAudioBytes = plain.audioBytes
                                        ),
                                    recordAttention = false
                                )
                            val mutationConversationOrder = conversationUpdateOrder[conversationId]
                            val appliedMessage =
                                requireNotNull(
                                    incomingMessage(
                                        conversationId = conversationId,
                                        senderFingerprint = plain.senderFingerprint,
                                        messageId = plain.messageId
                                    )
                                )
                            val expectedPersistedMessage = requireNotNull(appliedMessage.toPersisted())
                            val durable =
                                persistChatStateNow { state ->
                                    state.messagesByConversation[conversationId].orEmpty()
                                        .any { persisted -> persisted == expectedPersistedMessage }
                                }
                            if (!durable) {
                                restoreIncomingMessage(
                                    conversationId = conversationId,
                                    senderFingerprint = plain.senderFingerprint,
                                    messageId = plain.messageId,
                                    appliedReceiptMessageId = plain.envelopeMessageId,
                                    previousMessage = previousMessage,
                                    previousConversationOrder = previousConversationOrder,
                                    mutationConversationOrder = mutationConversationOrder
                                )
                                repairChatStateAfterIncomingRollback()
                                trustState = "语音未能安全保存，已忽略"
                                return
                            }
                            withContext(Dispatchers.IO) {
                                engine.rememberAuthenticatedPacket(encryptedMessage)
                            }
                            if (inserted) {
                                recordIncomingAttention(conversationId, appliedMessage)
                            }
                            trustState =
                                if (inserted) "收到加密语音" else "收到语音重发"
                            sendEncryptedAck(
                                transport = transport,
                                peer = verifiedRoute,
                                senderFingerprint = plain.senderFingerprint,
                                messageId = plain.envelopeMessageId,
                                failureState = "语音回执发送失败"
                            )
                            if (appSurface == AppSurface.Chat && activeConversationId == conversationId) {
                                sendReadReceipt(
                                    conversationId = conversationId,
                                    senderFingerprint = plain.senderFingerprint,
                                    messageId = plain.envelopeMessageId,
                                    peer = verifiedRoute
                                )
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: AuthenticatedPayloadDecodingException) {
                            rememberMalformedAuthenticatedPacket(
                                encryptedMessage = encryptedMessage,
                                ignoredState = "认证语音内容无效，已忽略",
                                duplicateState = "重复的无效语音已忽略",
                                failureState = "无效语音记录失败"
                            )
                        } catch (error: DuplicateMessageException) {
                            trustState = "重复语音已忽略"
                            sendEncryptedAck(
                                transport = transport,
                                peer = verifiedRoute,
                                senderFingerprint = error.senderFingerprint,
                                messageId = error.messageId,
                                failureState = "重复语音回执发送失败"
                            )
                        } catch (_: Exception) {
                            trustState = "语音解密失败"
                        }
                    }

                    PacketKind.ENCRYPTED_REACTION -> {
                        val encryptedMessage = packet.encryptedMessage ?: return
                        val storedSender = trustedPeer(encryptedMessage.senderFingerprint)
                        if (storedSender == null) {
                            trustState = "拦截未确认回应"
                            return
                        }
                        if (isPeerBlocked(encryptedMessage.senderFingerprint)) {
                            trustState = "已忽略被阻止回应"
                            return
                        }
                        if (verifiedRouteFor(encryptedMessage.senderFingerprint, sourcePeer) == null) {
                            trustState = "拦截未验证路由的回应"
                            return
                        }
                        try {
                            val reaction =
                                withContext(Dispatchers.IO) {
                                    engine.decryptReaction(encryptedMessage, rememberReplay = false)
                                }
                            if (reactionChoices.none { choice -> choice.code == reaction.emoji }) {
                                invalidAuthenticatedPayload(
                                    encryptedMessage,
                                    "Unsupported reaction code"
                                )
                            }
                            withContext(Dispatchers.IO) {
                                engine.ensureAuthenticatedPacketIsNew(encryptedMessage)
                            }
                            val candidateConversationIds =
                                listOf(
                                    directConversationId(reaction.senderFingerprint),
                                    NEARBY_GROUP_CONVERSATION_ID
                                ).distinct()
                            val targetConversationId =
                                candidateConversationIds.firstOrNull { conversationId ->
                                    messagesForConversation(conversationId)
                                        .any { message -> message.messageId == reaction.targetMessageId }
                                }
                            if (targetConversationId == null) {
                                withContext(Dispatchers.IO) {
                                    engine.rememberAuthenticatedPacket(encryptedMessage)
                                }
                                trustState = "回应目标不存在"
                                return
                            }
                            val targetMessage =
                                requireNotNull(
                                    messagesForConversation(targetConversationId)
                                        .firstOrNull { message ->
                                            message.messageId == reaction.targetMessageId
                                        }
                                )
                            val previousReaction =
                                targetMessage.reactions[reaction.senderFingerprint]
                            applyMessageReaction(
                                conversationId = targetConversationId,
                                targetMessageId = reaction.targetMessageId,
                                senderFingerprint = reaction.senderFingerprint,
                                reactionCode = reaction.emoji
                            )
                            val durable =
                                persistChatStateNow { state ->
                                    state.messagesByConversation[targetConversationId].orEmpty()
                                        .firstOrNull { persisted ->
                                            persisted.messageId == reaction.targetMessageId
                                        }
                                        ?.reactions
                                        ?.get(reaction.senderFingerprint) == reaction.emoji
                                }
                            if (!durable) {
                                conversationMessages[targetConversationId] =
                                    messagesForConversation(targetConversationId).map { message ->
                                        if (
                                            message.messageId == reaction.targetMessageId &&
                                            message.reactions[reaction.senderFingerprint] == reaction.emoji
                                        ) {
                                            val restoredReactions =
                                                if (previousReaction == null) {
                                                    message.reactions - reaction.senderFingerprint
                                                } else {
                                                    message.reactions +
                                                        (reaction.senderFingerprint to previousReaction)
                                                }
                                            message.copy(reactions = restoredReactions)
                                        } else {
                                            message
                                        }
                                    }
                                repairChatStateAfterIncomingRollback()
                                trustState = "回应未能安全保存，已忽略"
                                return
                            }
                            withContext(Dispatchers.IO) {
                                engine.rememberAuthenticatedPacket(encryptedMessage)
                            }
                            trustState = "${storedSender.deviceName} 回应了消息"
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: AuthenticatedPayloadDecodingException) {
                            rememberMalformedAuthenticatedPacket(
                                encryptedMessage = encryptedMessage,
                                ignoredState = "认证回应内容无效，已忽略",
                                duplicateState = "重复的无效回应已忽略",
                                failureState = "无效回应记录失败"
                            )
                        } catch (_: DuplicateMessageException) {
                            trustState = "重复回应已忽略"
                        } catch (_: Exception) {
                            trustState = "回应验证失败"
                        }
                    }

                    PacketKind.ENCRYPTED_ACK -> {
                        val encryptedAck = packet.encryptedMessage ?: return
                        if (trustedPeer(encryptedAck.senderFingerprint) == null) {
                            trustState = "拦截未认证回执"
                            return
                        }
                        if (isPeerBlocked(encryptedAck.senderFingerprint)) {
                            trustState = "已忽略被阻止回执"
                            return
                        }
                        if (verifiedRouteFor(encryptedAck.senderFingerprint, sourcePeer) == null) {
                            trustState = "拦截未验证路由的回执"
                            return
                        }
                        try {
                            val ack =
                                withContext(Dispatchers.IO) {
                                    engine.decryptAck(encryptedAck, rememberReplay = false)
                                }
                            withContext(Dispatchers.IO) {
                                engine.ensureAuthenticatedPacketIsNew(encryptedAck)
                            }
                            val outboundMessage = outgoingMessages[ack.messageId]
                            if (
                                outboundMessage == null ||
                                outboundMessage.recipientFingerprint != encryptedAck.senderFingerprint
                            ) {
                                withContext(Dispatchers.IO) {
                                    engine.rememberAuthenticatedPacket(encryptedAck)
                                }
                                trustState = "已忽略未知或不匹配的回执"
                                return
                            }
                            val displayMessageId = outboundMessage.displayMessageId
                            val previousMessage =
                                messagesForConversation(outboundMessage.conversationId)
                                    .firstOrNull { message ->
                                        message.messageId == displayMessageId
                                    }
                            val previousDeliveredRecipients =
                                deliveredReceiptsByMessage[displayMessageId].orEmpty()
                            val previousDeliveredCountPresent =
                                deliveredCounts.containsKey(displayMessageId)
                            val previousDeliveredCount = deliveredCounts[displayMessageId]
                            val previousReadRecipients =
                                readReceiptsByMessage[displayMessageId].orEmpty()
                            val previousReadCountPresent = readCounts.containsKey(displayMessageId)
                            val previousReadCount = readCounts[displayMessageId]
                            val state =
                                when (ack.status) {
                                    DeliveryReceiptStatus.Delivered -> DeliveryState.Delivered
                                    DeliveryReceiptStatus.Read -> DeliveryState.Read
                                }
                            if (ack.status == DeliveryReceiptStatus.Read) {
                                updateMessageState(
                                    messageId = ack.messageId,
                                    deliveryState = DeliveryState.Delivered,
                                    receiptSenderFingerprint = encryptedAck.senderFingerprint
                                )
                            }
                            updateMessageState(
                                messageId = ack.messageId,
                                deliveryState = state,
                                receiptSenderFingerprint = encryptedAck.senderFingerprint
                            )
                            if (ack.status == DeliveryReceiptStatus.Read) {
                                outgoingMessages.remove(ack.messageId)
                            }
                            val appliedMessageState =
                                messagesForConversation(outboundMessage.conversationId)
                                    .firstOrNull { message ->
                                        message.messageId == displayMessageId
                                    }
                                    ?.deliveryState
                            val appliedDeliveredRecipients =
                                deliveredReceiptsByMessage[displayMessageId].orEmpty()
                            val appliedReadRecipients =
                                readReceiptsByMessage[displayMessageId].orEmpty()
                            val tracksRecipientReceipts =
                                outboundMessage.expectedRecipients.size > 1
                            val durable =
                                persistChatStateNow { persistedState ->
                                    val persistedMessage =
                                        persistedState.messagesByConversation[outboundMessage.conversationId]
                                            .orEmpty()
                                            .firstOrNull { message ->
                                                message.messageId == displayMessageId
                                            }
                                    when (ack.status) {
                                        DeliveryReceiptStatus.Delivered ->
                                            persistedMessage != null &&
                                                if (tracksRecipientReceipts) {
                                                    encryptedAck.senderFingerprint in
                                                        persistedState.deliveredRecipientsByMessage[
                                                            displayMessageId
                                                        ].orEmpty()
                                                } else {
                                                    persistedMessage.deliveryState ==
                                                        PersistedDeliveryState.Delivered ||
                                                        persistedMessage.deliveryState ==
                                                        PersistedDeliveryState.Read
                                                }

                                        DeliveryReceiptStatus.Read ->
                                            persistedMessage != null &&
                                                ack.messageId !in persistedState.outgoingEnvelopes &&
                                                if (tracksRecipientReceipts) {
                                                    encryptedAck.senderFingerprint in
                                                        persistedState.deliveredRecipientsByMessage[
                                                            displayMessageId
                                                        ].orEmpty() &&
                                                        encryptedAck.senderFingerprint in
                                                        persistedState.readRecipientsByMessage[
                                                            displayMessageId
                                                        ].orEmpty()
                                                } else {
                                                    persistedMessage.deliveryState ==
                                                        PersistedDeliveryState.Read
                                                }
                                    }
                                }
                            if (!durable) {
                                if (previousMessage != null && appliedMessageState != null) {
                                    conversationMessages[outboundMessage.conversationId] =
                                        messagesForConversation(outboundMessage.conversationId)
                                            .map { message ->
                                                if (
                                                    message.messageId == displayMessageId &&
                                                    message.deliveryState == appliedMessageState
                                                ) {
                                                    message.copy(
                                                        deliveryState = previousMessage.deliveryState
                                                    )
                                                } else {
                                                    message
                                                }
                                            }
                                }
                                restoreReceiptTracking(
                                    receiptsByMessage = deliveredReceiptsByMessage,
                                    countsByMessage = deliveredCounts,
                                    displayMessageId = displayMessageId,
                                    senderFingerprint = encryptedAck.senderFingerprint,
                                    previousRecipients = previousDeliveredRecipients,
                                    previousCountPresent = previousDeliveredCountPresent,
                                    previousCount = previousDeliveredCount,
                                    appliedRecipients = appliedDeliveredRecipients
                                )
                                restoreReceiptTracking(
                                    receiptsByMessage = readReceiptsByMessage,
                                    countsByMessage = readCounts,
                                    displayMessageId = displayMessageId,
                                    senderFingerprint = encryptedAck.senderFingerprint,
                                    previousRecipients = previousReadRecipients,
                                    previousCountPresent = previousReadCountPresent,
                                    previousCount = previousReadCount,
                                    appliedRecipients = appliedReadRecipients
                                )
                                if (
                                    ack.status == DeliveryReceiptStatus.Read &&
                                    outgoingMessages[ack.messageId] == null
                                ) {
                                    outgoingMessages[ack.messageId] = outboundMessage
                                }
                                repairChatStateAfterIncomingRollback()
                                trustState = "回执未能安全保存，已忽略"
                                return
                            }
                            withContext(Dispatchers.IO) {
                                engine.rememberAuthenticatedPacket(encryptedAck)
                            }
                            trustState =
                                when (ack.status) {
                                    DeliveryReceiptStatus.Delivered -> "对方已收到"
                                    DeliveryReceiptStatus.Read -> "对方已读"
                                }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: AuthenticatedPayloadDecodingException) {
                            rememberMalformedAuthenticatedPacket(
                                encryptedMessage = encryptedAck,
                                ignoredState = "认证回执内容无效，已忽略",
                                duplicateState = "重复的无效回执已忽略",
                                failureState = "无效回执记录失败"
                            )
                        } catch (_: DuplicateMessageException) {
                            trustState = "重复回执已忽略"
                        } catch (_: Exception) {
                            trustState = "回执验证失败"
                        }
                    }

                    PacketKind.ACK -> {
                        trustState = "忽略未认证回执"
                    }
                }
            }
        }
    }

    fun confirmPairing() {
        val peer = pendingPeer ?: return
        val verifiedRoute = pendingPeerRoute ?: run {
            trustState = "验证路由已失效，请重新连接"
            return
        }
        runCatching { sessionCoordinator.confirmSession(peer.fingerprint) }
            .getOrElse {
                pendingPeer = null
                pendingPeerRoute = null
                pairingCode = null
                trustState = "验证会话已过期，请重新连接"
                return
            }
        rememberPeerRoute(peer.fingerprint, verifiedRoute)
        val storedPeer = trustedPeerStore.trust(peer)
        removeTrustedPeer(storedPeer)
        trustedPeers.add(0, storedPeer)
        pendingPeer = null
        pendingPeerRoute = null
        activePeerFingerprint = storedPeer.fingerprint
        pairingCode = storedPeer.pairingCode
        trustState = "已信任 ${storedPeer.deviceName}"
        ensureDirectConversation(storedPeer)
        appendSecurityChangeAlert(
            storedPeer = storedPeer,
            text = "安全提醒：已信任 ${storedPeer.deviceName}，可以开始加密聊天"
        )
    }

    fun rejectPairing() {
        val peer = pendingPeer ?: return
        sessionCoordinator.rejectPendingSession(peer.fingerprint)
        knownPeersByFingerprint.remove(peer.fingerprint)
        peerLastSeenAt.remove(peer.fingerprint)
        trustedPeerStore.forget(peer.fingerprint, peer.publicKey)
        trustedPeers.removeAll { storedPeer ->
            storedPeer.fingerprint == peer.fingerprint || storedPeer.publicKey == peer.publicKey
        }
        pendingPeer = null
        pendingPeerRoute = null
        activePeerFingerprint = null
        pairingCode = null
        trustState = "已拒绝 ${peer.deviceName}"
        appendSystemMessage(
            text = "已拒绝 ${peer.deviceName} 的配对请求",
            encrypted = false
        )
    }

    fun selectMode(mode: TransportMode) {
        if (mode == TransportMode.Lan) {
            val shouldEnable = transportMode != TransportMode.Lan || !lanDiscoveryEnabled
            transportMode = TransportMode.Lan
            lanDiscoveryEnabled = shouldEnable
            trustState =
                if (shouldEnable) {
                    "局域网发现中"
                } else {
                    "局域网发现已关闭"
                }
            return
        }
        if (mode == TransportMode.Bluetooth && !hasBluetoothRuntimePermissions()) {
            trustState = "蓝牙需要授权"
            val permissions = requiredBluetoothRuntimePermissions(Build.VERSION.SDK_INT)
            if (permissions.isNotEmpty()) {
                permissionLauncher.launch(permissions.toTypedArray())
            }
            return
        }
        if (mode == TransportMode.Relay) {
            transportMode = mode
            trustState = "中继预留"
            appendSystemMessage(
                text = "中继只会转发端到端加密消息；服务器连接稍后再配置",
                encrypted = true
            )
            return
        }
        transportMode = mode
        trustState = "蓝牙待连接"
    }

    fun updateProfile(updated: ProfileSettings) {
        profile = profileStore.save(updated)
    }

    fun targetsForConversation(conversation: ChatConversation): List<Pair<String, TransportPeer>> =
        conversation.memberFingerprints.filterNot(::isPeerBlocked).mapNotNull { fingerprint ->
            val peer = routeForPeer(fingerprint)
            val storedPeer = trustedPeer(fingerprint)
            if (peer == null || storedPeer == null) {
                null
            } else {
                fingerprint to peer
            }
        }

    fun targetsForFingerprints(fingerprints: List<String>): List<Pair<String, TransportPeer>> =
        fingerprints.distinct().filterNot(::isPeerBlocked).mapNotNull { fingerprint ->
            val peer = routeForPeer(fingerprint)
            val storedPeer = trustedPeer(fingerprint)
            if (peer == null || storedPeer == null) {
                null
            } else {
                fingerprint to peer
            }
        }

    fun expectedRecipientsForMessage(
        displayMessageId: String,
        conversation: ChatConversation,
        queuedRecipients: List<String>? = null
    ): Set<String> {
        val currentRecipients = conversation.memberFingerprints.filterNot(::isPeerBlocked).toSet()
        val frozenRecipients =
            expectedRecipientsByMessage[displayMessageId]
                ?: queuedRecipients?.toSet()
                ?: currentRecipients
        val activeRecipients = frozenRecipients.intersect(currentRecipients)
        expectedRecipientsByMessage[displayMessageId] = activeRecipients
        outgoingMessages
            .filterValues { message -> message.displayMessageId == displayMessageId }
            .keys
            .toList()
            .forEach { envelopeId ->
                val outgoingMessage = outgoingMessages[envelopeId] ?: return@forEach
                if (outgoingMessage.recipientFingerprint !in activeRecipients) {
                    outgoingMessages.remove(envelopeId)
                } else {
                    outgoingMessages[envelopeId] =
                        outgoingMessage.copy(expectedRecipients = activeRecipients)
                }
            }
        val deliveredRecipients =
            deliveredReceiptsByMessage[displayMessageId].orEmpty().intersect(activeRecipients)
        val readRecipients =
            readReceiptsByMessage[displayMessageId].orEmpty().intersect(activeRecipients)
        deliveredReceiptsByMessage[displayMessageId] = deliveredRecipients
        readReceiptsByMessage[displayMessageId] = readRecipients
        deliveredCounts[displayMessageId] = deliveredRecipients.size
        readCounts[displayMessageId] = readRecipients.size
        if (activeRecipients.isNotEmpty() && deliveredRecipients.containsAll(activeRecipients)) {
            updateMessageState(displayMessageId, DeliveryState.Delivered)
        }
        if (activeRecipients.isNotEmpty() && readRecipients.containsAll(activeRecipients)) {
            updateMessageState(displayMessageId, DeliveryState.Read)
        }
        return activeRecipients
    }

    fun sendPreparedMessage(
        conversation: ChatConversation,
        text: String,
        displayMessageId: String,
        targets: List<Pair<String, TransportPeer>>,
        requeueOnFailure: Boolean,
        quotedMessage: QuotedMessage?,
        forwarded: Boolean,
        forwardCount: Int
    ) {
        if (!chatStateOperational) {
            updateMessageState(displayMessageId, DeliveryState.Waiting)
            trustState = "本地聊天状态未就绪，消息未发送"
            return
        }
        if (!canSendToConversation(conversation)) {
            pendingOutboundMessages.remove(displayMessageId)
            updateMessageState(displayMessageId, DeliveryState.Failed)
            trustState = "公告模式下不能发送消息"
            return
        }
        if (displayMessageId in inFlightOutboundMessages) {
            return
        }
        inFlightOutboundMessages[displayMessageId] = true
        val routeRevisionAtStart = peerRouteRevision
        val recipientRevisionAtStart = recipientSetRevision
        trustState = "正在加密发送"
        coroutineScope.launch {
            try {
            val queuedRecipients =
                pendingOutboundMessages[displayMessageId]?.remainingTargetFingerprints
            val expectedRecipients =
                expectedRecipientsForMessage(displayMessageId, conversation, queuedRecipients)
            if (expectedRecipients.isEmpty()) {
                pendingOutboundMessages.remove(displayMessageId)
                updateMessageState(displayMessageId, DeliveryState.Failed)
                trustState = "没有仍可接收这条消息的成员"
                return@launch
            }
            val attemptedRecipients =
                (queuedRecipients?.toSet() ?: expectedRecipients).intersect(expectedRecipients)
            if (attemptedRecipients.isEmpty()) {
                pendingOutboundMessages.remove(displayMessageId)
                val currentState =
                    messagesForConversation(conversation.id)
                        .firstOrNull { message -> message.messageId == displayMessageId }
                        ?.deliveryState
                if (currentState != DeliveryState.Delivered && currentState != DeliveryState.Read) {
                    updateMessageState(displayMessageId, DeliveryState.Sent)
                }
                trustState = "已移除不再接收此消息的成员"
                return@launch
            }
            val capacityIssue =
                validateTextSendCapacity(
                    displayMessageId = displayMessageId,
                    recipientCount = attemptedRecipients.size,
                    conversationId = conversation.id,
                    persistOutbox = requeueOnFailure
                )
            if (capacityIssue != null) {
                updateMessageState(
                    displayMessageId,
                    if (requeueOnFailure) DeliveryState.Waiting else DeliveryState.Failed
                )
                trustState = outboxCapacityMessage(capacityIssue)
                return@launch
            }
            if (requeueOnFailure) {
                pendingOutboundMessages[displayMessageId] =
                    PendingOutboundMessage(
                        conversationId = conversation.id,
                        text = text,
                        displayMessageId = displayMessageId,
                        remainingTargetFingerprints = attemptedRecipients.toList(),
                        quotedMessage = quotedMessage,
                        forwarded = forwarded,
                        forwardCount = forwardCount
                    )
            }
            if (
                !persistChatStateNow { persisted ->
                    if (requeueOnFailure) {
                        persisted.pendingTextOutbox.any { queued ->
                            queued.displayMessageId == displayMessageId &&
                                queued.remainingTargetFingerprints.toSet() == attemptedRecipients
                        }
                    } else {
                        persisted.messagesByConversation[conversation.id].orEmpty()
                            .any { message -> message.messageId == displayMessageId }
                    }
                }
            ) {
                updateMessageState(
                    displayMessageId,
                    if (requeueOnFailure) DeliveryState.Waiting else DeliveryState.Failed
                )
                trustState = "本地聊天状态不可用，消息未发送"
                return@launch
            }
            val attemptTargets =
                targets.filter { (fingerprint, _) -> fingerprint in attemptedRecipients }
            val remainingRecipients =
                (attemptedRecipients - attemptTargets.map { (fingerprint, _) -> fingerprint }.toSet())
                    .toMutableSet()
            fun isCurrentRecipient(fingerprint: String): Boolean {
                val queuedTargets =
                    pendingOutboundMessages[displayMessageId]?.remainingTargetFingerprints.orEmpty()
                return fingerprint in expectedRecipientsByMessage[displayMessageId].orEmpty() &&
                    !isPeerBlocked(fingerprint) &&
                    trustedPeer(fingerprint) != null &&
                    (!requeueOnFailure || fingerprint in queuedTargets)
            }
            var sentCount = 0
            var deliveryFailureState: String? = null
            attemptTargets.forEach { (peerFingerprint, peer) ->
                if (!isCurrentRecipient(peerFingerprint)) {
                    return@forEach
                }
                val payload =
                    encodeChatPayload(
                        conversation = conversation,
                        text = text,
                        quotedMessage = quotedMessage,
                        forwarded = forwarded,
                        forwardCount = forwardCount
                    )
                val packetResult =
                    runCatching {
                        engine.encryptTextForPeer(
                            peerFingerprint = peerFingerprint,
                            text = payload,
                            messageId = displayMessageId
                        )
                    }
                if (packetResult.isFailure) {
                    remainingRecipients += peerFingerprint
                    deliveryFailureState = "消息加密失败"
                    return@forEach
                }
                val packet = packetResult.getOrNull() ?: return@forEach
                val packetMessageId = packet.encryptedMessage?.messageId ?: return@forEach
                if (!isCurrentRecipient(peerFingerprint)) {
                    return@forEach
                }
                val currentExpectedRecipients = expectedRecipientsByMessage[displayMessageId].orEmpty()
                if (
                    !rememberOutgoingEnvelope(
                        packetMessageId,
                        OutgoingMessageRef(
                            conversationId = conversation.id,
                            displayMessageId = displayMessageId,
                            recipientFingerprint = peerFingerprint,
                            expectedRecipients = currentExpectedRecipients
                        )
                    )
                ) {
                    remainingRecipients += peerFingerprint
                    return@forEach
                }
                if (
                    !persistChatStateNow { persisted ->
                        persisted.outgoingEnvelopes.containsKey(packetMessageId)
                    }
                ) {
                    outgoingMessages.remove(packetMessageId)
                    remainingRecipients += peerFingerprint
                    return@forEach
                }
                val persistedEnvelope = outgoingMessages[packetMessageId]
                if (
                    persistedEnvelope?.recipientFingerprint != peerFingerprint ||
                    !isCurrentRecipient(peerFingerprint)
                ) {
                    outgoingMessages.remove(packetMessageId)
                    remainingRecipients += peerFingerprint
                    return@forEach
                }

                runCatching {
                    sendPacket(transportMode.kind, peer, packet)
                }.onSuccess {
                    sentCount += 1
                }.onFailure {
                    outgoingMessages.remove(packetMessageId)
                    deliveryFailureState = "消息发送失败"
                    if (isCurrentRecipient(peerFingerprint)) {
                        remainingRecipients += peerFingerprint
                    }
                }
            }

            val currentExpectedRecipients =
                expectedRecipientsByMessage[displayMessageId].orEmpty()
                    .filterTo(mutableSetOf(), ::isCurrentRecipient)
            remainingRecipients.retainAll(currentExpectedRecipients)
            if (recipientRevisionAtStart != recipientSetRevision && currentExpectedRecipients.isEmpty()) {
                pendingOutboundMessages.remove(displayMessageId)
                trustState = "消息目标已变更，已停止剩余发送"
                return@launch
            }
            if (
                recipientRevisionAtStart != recipientSetRevision &&
                sentCount == 0 &&
                remainingRecipients.isEmpty()
            ) {
                pendingOutboundMessages.remove(displayMessageId)
                val currentState =
                    messagesForConversation(conversation.id)
                        .firstOrNull { message -> message.messageId == displayMessageId }
                        ?.deliveryState
                if (currentState != DeliveryState.Delivered && currentState != DeliveryState.Read) {
                    updateMessageState(displayMessageId, DeliveryState.Sent)
                }
                trustState = "消息目标已更新"
                return@launch
            }

            when {
                sentCount > 0 && remainingRecipients.isEmpty() -> {
                    pendingOutboundMessages.remove(displayMessageId)
                    updateMessageState(displayMessageId, DeliveryState.Sent)
                    trustState =
                        if (conversation.kind == ConversationKind.Group) {
                            "群聊已加密发送"
                        } else {
                            "已加密发送"
                        }
                }

                sentCount > 0 -> {
                    if (requeueOnFailure) {
                        pendingOutboundMessages[displayMessageId] =
                            PendingOutboundMessage(
                                conversationId = conversation.id,
                                text = text,
                                displayMessageId = displayMessageId,
                                remainingTargetFingerprints = remainingRecipients.toList(),
                                quotedMessage = quotedMessage,
                                forwarded = forwarded,
                                forwardCount = forwardCount
                            )
                    } else {
                        pendingOutboundMessages.remove(displayMessageId)
                    }
                    updateMessageState(
                        displayMessageId,
                        if (requeueOnFailure) DeliveryState.Waiting else DeliveryState.Sent
                    )
                    trustState = "部分成员已发送"
                }

                requeueOnFailure -> {
                    pendingOutboundMessages[displayMessageId] =
                        PendingOutboundMessage(
                            conversationId = conversation.id,
                            text = text,
                            displayMessageId = displayMessageId,
                            remainingTargetFingerprints = remainingRecipients.toList(),
                            quotedMessage = quotedMessage,
                            forwarded = forwarded,
                            forwardCount = forwardCount
                        )
                    updateMessageState(displayMessageId, DeliveryState.Waiting)
                    trustState =
                        deliveryFailureState?.let { failure -> "$failure，等待重试" }
                            ?: "等待对方上线"
                }

                else -> {
                    updateMessageState(displayMessageId, DeliveryState.Failed)
                    trustState = deliveryFailureState ?: "发送失败"
                }
            }
            persistChatStateNow()
            } finally {
                inFlightOutboundMessages.remove(displayMessageId)
                if (
                    routeRevisionAtStart != peerRouteRevision ||
                    recipientRevisionAtStart != recipientSetRevision
                ) {
                    outboundQueueWakeRevision += 1L
                }
            }
        }
    }

    fun trySendPendingOutboundMessage(queuedReply: PendingOutboundMessage) {
        val conversation = conversationById(queuedReply.conversationId) ?: return
        if (isConversationBlocked(conversation)) {
            pendingOutboundMessages.remove(queuedReply.displayMessageId)
            updateMessageState(queuedReply.displayMessageId, DeliveryState.Failed)
            trustState = "已阻止此联系人"
            return
        }
        val targets = targetsForFingerprints(queuedReply.remainingTargetFingerprints)
        if (targets.isEmpty()) {
            return
        }
        updateMessageState(queuedReply.displayMessageId, DeliveryState.Sending)
        sendPreparedMessage(
            conversation = conversation,
            text = queuedReply.text,
            displayMessageId = queuedReply.displayMessageId,
            targets = targets,
            requeueOnFailure = true,
            quotedMessage = queuedReply.quotedMessage,
            forwarded = queuedReply.forwarded,
            forwardCount = queuedReply.forwardCount
        )
    }

    fun sendReactionToConversation(
        conversation: ChatConversation,
        message: ChatBubble,
        reactionCode: String
    ) {
        if (!chatStateOperational) {
            trustState = "本地聊天状态未就绪，回应未发送"
            return
        }
        if (isConversationBlocked(conversation)) {
            trustState = "已阻止此联系人"
            return
        }
        val targetMessageId = message.messageId ?: return
        applyMessageReaction(
            conversationId = conversation.id,
            targetMessageId = targetMessageId,
            senderFingerprint = localFingerprint,
            reactionCode = reactionCode
        )
        val targets = targetsForConversation(conversation)
        if (targets.isEmpty()) {
            trustState = "回应已本机保存"
            return
        }
        trustState = "正在发送回应"
        coroutineScope.launch {
            if (!persistChatStateNow()) {
                return@launch
            }
            var sentCount = 0
            targets.forEach { (peerFingerprint, peer) ->
                val packet =
                    runCatching {
                        engine.encryptReactionForPeer(
                            peerFingerprint = peerFingerprint,
                            targetMessageId = targetMessageId,
                            emoji = reactionCode
                        )
                    }.getOrElse {
                        return@forEach
                    }
                runCatching {
                    sendPacket(transportMode.kind, peer, packet)
                }.onSuccess {
                    sentCount += 1
                }
            }
            trustState =
                if (sentCount > 0) {
                    "回应已发送"
                } else {
                    "回应发送失败"
                }
        }
    }

    fun removeLocalReactionFromMessage(
        conversation: ChatConversation,
        message: ChatBubble
    ) {
        val targetMessageId = message.messageId
        if (targetMessageId == null || localFingerprint !in message.reactions) {
            trustState = "没有我的回应"
            return
        }
        removeMessageReaction(
            conversationId = conversation.id,
            targetMessageId = targetMessageId,
            senderFingerprint = localFingerprint
        )
        trustState = "已取消我的回应"
    }

    fun sendPreparedVoiceMessage(
        conversation: ChatConversation,
        displayMessageId: String,
        durationMs: Long,
        audioBytes: ByteArray,
        targets: List<Pair<String, TransportPeer>>,
        requeueOnFailure: Boolean
    ) {
        if (!chatStateOperational) {
            updateMessageState(displayMessageId, DeliveryState.Waiting)
            trustState = "本地聊天状态未就绪，语音未发送"
            return
        }
        if (!canSendToConversation(conversation)) {
            pendingOutboundVoiceMessages.remove(displayMessageId)
            updateMessageState(displayMessageId, DeliveryState.Failed)
            trustState = "公告模式下不能发送语音"
            return
        }
        if (displayMessageId in inFlightOutboundVoiceMessages) {
            return
        }
        inFlightOutboundVoiceMessages[displayMessageId] = true
        val routeRevisionAtStart = peerRouteRevision
        val recipientRevisionAtStart = recipientSetRevision
        trustState = "正在加密发送语音"
        coroutineScope.launch {
            try {
            val queuedRecipients =
                pendingOutboundVoiceMessages[displayMessageId]?.remainingTargetFingerprints
            val expectedRecipients =
                expectedRecipientsForMessage(displayMessageId, conversation, queuedRecipients)
            if (expectedRecipients.isEmpty()) {
                pendingOutboundVoiceMessages.remove(displayMessageId)
                updateMessageState(displayMessageId, DeliveryState.Failed)
                trustState = "没有仍可接收这条语音的成员"
                return@launch
            }
            val attemptedRecipients =
                (queuedRecipients?.toSet() ?: expectedRecipients).intersect(expectedRecipients)
            if (attemptedRecipients.isEmpty()) {
                pendingOutboundVoiceMessages.remove(displayMessageId)
                val currentState =
                    messagesForConversation(conversation.id)
                        .firstOrNull { message -> message.messageId == displayMessageId }
                        ?.deliveryState
                if (currentState != DeliveryState.Delivered && currentState != DeliveryState.Read) {
                    updateMessageState(displayMessageId, DeliveryState.Sent)
                }
                trustState = "已移除不再接收此语音的成员"
                return@launch
            }
            val capacityIssue =
                validateVoiceSendCapacity(
                    displayMessageId = displayMessageId,
                    audioByteCount = audioBytes.size,
                    recipientCount = attemptedRecipients.size,
                    conversationId = conversation.id,
                    persistOutbox = requeueOnFailure
                )
            if (capacityIssue != null) {
                updateMessageState(
                    displayMessageId,
                    if (requeueOnFailure) DeliveryState.Waiting else DeliveryState.Failed
                )
                trustState = outboxCapacityMessage(capacityIssue)
                return@launch
            }
            if (requeueOnFailure) {
                pendingOutboundVoiceMessages[displayMessageId] =
                    PendingOutboundVoiceMessage(
                        conversationId = conversation.id,
                        displayMessageId = displayMessageId,
                        remainingTargetFingerprints = attemptedRecipients.toList(),
                        durationMs = durationMs,
                        audioBytes = audioBytes
                    )
            }
            if (
                !persistChatStateNow { persisted ->
                    if (requeueOnFailure) {
                        persisted.pendingVoiceOutbox.any { queued ->
                            queued.displayMessageId == displayMessageId &&
                                queued.remainingTargetFingerprints.toSet() == attemptedRecipients
                        }
                    } else {
                        persisted.messagesByConversation[conversation.id].orEmpty()
                            .any { message -> message.messageId == displayMessageId }
                    }
                }
            ) {
                updateMessageState(
                    displayMessageId,
                    if (requeueOnFailure) DeliveryState.Waiting else DeliveryState.Failed
                )
                trustState = "本地聊天状态不可用，语音未发送"
                return@launch
            }
            val attemptTargets =
                targets.filter { (fingerprint, _) -> fingerprint in attemptedRecipients }
            val remainingRecipients =
                (attemptedRecipients - attemptTargets.map { (fingerprint, _) -> fingerprint }.toSet())
                    .toMutableSet()
            fun isCurrentVoiceRecipient(fingerprint: String): Boolean {
                val queuedTargets =
                    pendingOutboundVoiceMessages[displayMessageId]?.remainingTargetFingerprints.orEmpty()
                return fingerprint in expectedRecipientsByMessage[displayMessageId].orEmpty() &&
                    !isPeerBlocked(fingerprint) &&
                    trustedPeer(fingerprint) != null &&
                    (!requeueOnFailure || fingerprint in queuedTargets)
            }
            var sentCount = 0
            var deliveryFailureState: String? = null
            attemptTargets.forEach { (peerFingerprint, peer) ->
                if (!isCurrentVoiceRecipient(peerFingerprint)) {
                    return@forEach
                }
                val packetResult =
                    runCatching {
                        engine.encryptVoiceForPeer(
                            peerFingerprint = peerFingerprint,
                            audioBytes = audioBytes,
                            durationMs = durationMs,
                            groupId = conversation.id.takeIf { conversation.kind == ConversationKind.Group },
                            groupName = conversation.title.takeIf { conversation.kind == ConversationKind.Group },
                            messageId = displayMessageId
                        )
                    }
                if (packetResult.isFailure) {
                    remainingRecipients += peerFingerprint
                    deliveryFailureState = "语音加密失败"
                    return@forEach
                }
                val packet = packetResult.getOrNull() ?: return@forEach
                val packetMessageId = packet.encryptedMessage?.messageId ?: return@forEach
                if (!isCurrentVoiceRecipient(peerFingerprint)) {
                    return@forEach
                }
                val currentExpectedRecipients = expectedRecipientsByMessage[displayMessageId].orEmpty()
                if (
                    !rememberOutgoingEnvelope(
                        packetMessageId,
                        OutgoingMessageRef(
                            conversationId = conversation.id,
                            displayMessageId = displayMessageId,
                            recipientFingerprint = peerFingerprint,
                            expectedRecipients = currentExpectedRecipients
                        )
                    )
                ) {
                    remainingRecipients += peerFingerprint
                    return@forEach
                }
                if (
                    !persistChatStateNow { persisted ->
                        persisted.outgoingEnvelopes.containsKey(packetMessageId)
                    }
                ) {
                    outgoingMessages.remove(packetMessageId)
                    remainingRecipients += peerFingerprint
                    return@forEach
                }
                val persistedEnvelope = outgoingMessages[packetMessageId]
                if (
                    persistedEnvelope?.recipientFingerprint != peerFingerprint ||
                    !isCurrentVoiceRecipient(peerFingerprint)
                ) {
                    outgoingMessages.remove(packetMessageId)
                    remainingRecipients += peerFingerprint
                    return@forEach
                }

                runCatching {
                    sendPacket(transportMode.kind, peer, packet)
                }.onSuccess {
                    sentCount += 1
                }.onFailure {
                    outgoingMessages.remove(packetMessageId)
                    deliveryFailureState = "语音发送失败"
                    if (isCurrentVoiceRecipient(peerFingerprint)) {
                        remainingRecipients += peerFingerprint
                    }
                }
            }

            val currentExpectedRecipients =
                expectedRecipientsByMessage[displayMessageId].orEmpty()
                    .filterTo(mutableSetOf(), ::isCurrentVoiceRecipient)
            remainingRecipients.retainAll(currentExpectedRecipients)
            if (recipientRevisionAtStart != recipientSetRevision && currentExpectedRecipients.isEmpty()) {
                pendingOutboundVoiceMessages.remove(displayMessageId)
                trustState = "语音目标已变更，已停止剩余发送"
                return@launch
            }
            if (
                recipientRevisionAtStart != recipientSetRevision &&
                sentCount == 0 &&
                remainingRecipients.isEmpty()
            ) {
                pendingOutboundVoiceMessages.remove(displayMessageId)
                val currentState =
                    messagesForConversation(conversation.id)
                        .firstOrNull { message -> message.messageId == displayMessageId }
                        ?.deliveryState
                if (currentState != DeliveryState.Delivered && currentState != DeliveryState.Read) {
                    updateMessageState(displayMessageId, DeliveryState.Sent)
                }
                trustState = "语音目标已更新"
                return@launch
            }

            when {
                sentCount > 0 && remainingRecipients.isEmpty() -> {
                    pendingOutboundVoiceMessages.remove(displayMessageId)
                    updateMessageState(displayMessageId, DeliveryState.Sent)
                    trustState = "语音已加密发送"
                }

                sentCount > 0 -> {
                    if (requeueOnFailure) {
                        pendingOutboundVoiceMessages[displayMessageId] =
                            PendingOutboundVoiceMessage(
                                conversationId = conversation.id,
                                displayMessageId = displayMessageId,
                                remainingTargetFingerprints = remainingRecipients.toList(),
                                durationMs = durationMs,
                                audioBytes = audioBytes
                            )
                    } else {
                        pendingOutboundVoiceMessages.remove(displayMessageId)
                    }
                    updateMessageState(displayMessageId, DeliveryState.Waiting)
                    trustState = "语音部分发送"
                }

                requeueOnFailure -> {
                    pendingOutboundVoiceMessages[displayMessageId] =
                        PendingOutboundVoiceMessage(
                            conversationId = conversation.id,
                            displayMessageId = displayMessageId,
                            remainingTargetFingerprints = remainingRecipients.toList(),
                            durationMs = durationMs,
                            audioBytes = audioBytes
                        )
                    updateMessageState(displayMessageId, DeliveryState.Waiting)
                    trustState =
                        deliveryFailureState?.let { failure -> "$failure，等待重试" }
                            ?: "等待语音重发"
                }

                else -> {
                    updateMessageState(displayMessageId, DeliveryState.Failed)
                    trustState = deliveryFailureState ?: "语音发送失败"
                }
            }
            persistChatStateNow()
            } finally {
                inFlightOutboundVoiceMessages.remove(displayMessageId)
                if (
                    routeRevisionAtStart != peerRouteRevision ||
                    recipientRevisionAtStart != recipientSetRevision
                ) {
                    outboundQueueWakeRevision += 1L
                }
            }
        }
    }

    fun trySendPendingOutboundVoiceMessage(queuedVoice: PendingOutboundVoiceMessage) {
        val conversation = conversationById(queuedVoice.conversationId) ?: return
        if (isConversationBlocked(conversation)) {
            pendingOutboundVoiceMessages.remove(queuedVoice.displayMessageId)
            updateMessageState(queuedVoice.displayMessageId, DeliveryState.Failed)
            trustState = "已阻止此联系人"
            return
        }
        val targets = targetsForFingerprints(queuedVoice.remainingTargetFingerprints)
        if (targets.isEmpty()) {
            return
        }
        updateMessageState(queuedVoice.displayMessageId, DeliveryState.Sending)
        sendPreparedVoiceMessage(
            conversation = conversation,
            displayMessageId = queuedVoice.displayMessageId,
            durationMs = queuedVoice.durationMs,
            audioBytes = queuedVoice.audioBytes,
            targets = targets,
            requeueOnFailure = true
        )
    }

    fun sendVoiceToConversation(
        conversation: ChatConversation,
        recordedVoice: RecordedVoiceMessage
    ) {
        if (!chatStateOperational) {
            trustState = "本地聊天状态未就绪，语音未发送"
            recordedVoice.file.delete()
            return
        }
        if (!canSendToConversation(conversation)) {
            trustState = "公告模式下不能发送语音"
            recordedVoice.file.delete()
            return
        }
        if (isConversationBlocked(conversation)) {
            trustState = "已阻止此联系人"
            recordedVoice.file.delete()
            appendSystemMessage(
                text = "已阻止此联系人，未发送语音",
                encrypted = true,
                conversationId = conversation.id
            )
            return
        }
        if (conversation.memberFingerprints.isEmpty()) {
            trustState = if (pendingPeer == null) "等待配对" else "请先确认校验码"
            recordedVoice.file.delete()
            appendMessage(
                conversation.id,
                ChatBubble(
                    text = if (pendingPeer == null) "群聊还没有成员，请先完成配对" else "请先确认配对校验码",
                    mine = false,
                    encrypted = true,
                    timestamp = nowTime(),
                    deliveryState = DeliveryState.Waiting
                )
            )
            return
        }

        val displayMessageId = UUID.randomUUID().toString()
        val intendedRecipients = conversation.memberFingerprints.filterNot(::isPeerBlocked)
        val capacityIssue =
            validateVoiceSendCapacity(
                displayMessageId = displayMessageId,
                audioByteCount = recordedVoice.audioBytes.size,
                recipientCount = intendedRecipients.size,
                conversationId = conversation.id,
                persistOutbox = intendedRecipients.isNotEmpty()
            )
        if (capacityIssue != null) {
            trustState = outboxCapacityMessage(capacityIssue)
            recordedVoice.file.delete()
            return
        }
        val initialChatMutation = captureDurableChatMutation()
        expectedRecipientsByMessage[displayMessageId] = intendedRecipients.toSet()
        appendMessage(
            conversation.id,
            ChatBubble(
                text = "语音消息 · ${formatDuration(recordedVoice.durationMs)}",
                mine = true,
                encrypted = true,
                timestamp = nowTime(),
                messageId = displayMessageId,
                deliveryState = DeliveryState.Sending,
                kind = ChatMessageKind.Voice,
                voiceDurationMs = recordedVoice.durationMs,
                voiceAudioBytes = recordedVoice.audioBytes
            )
        )
        val queuedVoiceMessage =
            if (intendedRecipients.isNotEmpty()) {
                PendingOutboundVoiceMessage(
                    conversationId = conversation.id,
                    displayMessageId = displayMessageId,
                    remainingTargetFingerprints = intendedRecipients,
                    durationMs = recordedVoice.durationMs,
                    audioBytes = recordedVoice.audioBytes
                ).also { pending ->
                    pendingOutboundVoiceMessages[displayMessageId] = pending
                }
            } else {
                null
            }

        val targets = targetsForConversation(conversation)
        if (targets.isEmpty()) {
            val allowedTargetFingerprints = intendedRecipients
            if (allowedTargetFingerprints.isEmpty()) {
                updateMessageState(displayMessageId, DeliveryState.Failed)
            } else {
                pendingOutboundVoiceMessages[displayMessageId] =
                    PendingOutboundVoiceMessage(
                        conversationId = conversation.id,
                        displayMessageId = displayMessageId,
                        remainingTargetFingerprints = allowedTargetFingerprints,
                        durationMs = recordedVoice.durationMs,
                        audioBytes = recordedVoice.audioBytes
                    )
                updateMessageState(displayMessageId, DeliveryState.Waiting)
            }
            val offlineState =
                if (allowedTargetFingerprints.isEmpty()) {
                    "成员均已阻止"
                } else if (conversation.memberFingerprints.any(::isPeerBlocked)) {
                    "可发送成员未在线"
                } else {
                    "成员未在线"
                }
            if (queuedVoiceMessage == null) {
                trustState = offlineState
                recordedVoice.file.delete()
            } else {
                val appliedChatMutation = captureDurableChatMutation()
                val expectedPersistedMessage =
                    requireNotNull(
                        messagesForConversation(conversation.id)
                            .firstOrNull { message -> message.messageId == displayMessageId }
                            ?.toPersisted()
                    )
                trustState = "正在安全保存待发送语音"
                coroutineScope.launch {
                    val persisted =
                        persistChatStateNow { state ->
                            state.pendingVoiceOutbox.any { queued ->
                                queued.displayMessageId == displayMessageId &&
                                    queued.remainingTargetFingerprints.toSet() ==
                                    intendedRecipients.toSet() &&
                                    queued.durationMs == recordedVoice.durationMs
                            } &&
                                state.messagesByConversation[conversation.id].orEmpty().any { message ->
                                    message == expectedPersistedMessage
                                }
                        }
                    if (persisted) {
                        trustState = offlineState
                    } else {
                        rollbackDurableChatMutation(initialChatMutation, appliedChatMutation)
                        if (chatStateWritable) {
                            persistChatStateNow()
                        }
                        trustState = "语音未能安全保存，未加入待发送"
                    }
                    recordedVoice.file.delete()
                }
            }
            return
        }

        if (queuedVoiceMessage == null) {
            updateMessageState(displayMessageId, DeliveryState.Failed)
            recordedVoice.file.delete()
            return
        }
        val appliedChatMutation = captureDurableChatMutation()
        inFlightOutboundVoiceMessages[displayMessageId] = true
        coroutineScope.launch {
            val persisted =
                try {
                    persistChatStateNow { state ->
                        state.pendingVoiceOutbox.any { queued ->
                            queued.displayMessageId == displayMessageId &&
                                queued.remainingTargetFingerprints.toSet() ==
                                intendedRecipients.toSet()
                        }
                    }
                } finally {
                    inFlightOutboundVoiceMessages.remove(displayMessageId)
                }
            if (!persisted) {
                rollbackDurableChatMutation(initialChatMutation, appliedChatMutation)
                if (chatStateWritable) {
                    persistChatStateNow()
                }
                trustState = "语音未能安全保存，未发送"
                recordedVoice.file.delete()
                return@launch
            }
            recordedVoice.file.delete()
            sendPreparedVoiceMessage(
                conversation = conversation,
                displayMessageId = displayMessageId,
                durationMs = recordedVoice.durationMs,
                audioBytes = recordedVoice.audioBytes,
                targets = targets,
                requeueOnFailure = true
            )
        }
    }

    fun sendMessageToConversation(
        conversation: ChatConversation,
        text: String,
        requeueWhenOffline: Boolean = true,
        quotedMessage: QuotedMessage? = null,
        forwarded: Boolean = false,
        forwardCount: Int = 0
    ) {
        val cleanText = text.trim().take(MAX_CUSTOM_MESSAGE_CHARS)
        if (cleanText.isBlank()) {
            return
        }
        if (!chatStateOperational) {
            trustState = "本地聊天状态未就绪，消息未发送"
            return
        }
        if (!canSendToConversation(conversation)) {
            trustState = "公告模式下不能发送消息"
            return
        }
        if (isConversationBlocked(conversation)) {
            trustState = "已阻止此联系人"
            appendSystemMessage(
                text = "已阻止此联系人，未发送消息",
                encrypted = true,
                conversationId = conversation.id
            )
            return
        }

        val displayMessageId = UUID.randomUUID().toString()
        val remainingTargetFingerprints = conversation.memberFingerprints.filterNot(::isPeerBlocked)
        val capacityIssue =
            validateTextSendCapacity(
                displayMessageId = displayMessageId,
                recipientCount = remainingTargetFingerprints.size,
                conversationId = conversation.id,
                persistOutbox = requeueWhenOffline && remainingTargetFingerprints.isNotEmpty()
            )
        if (capacityIssue != null) {
            trustState = outboxCapacityMessage(capacityIssue)
            return
        }

        val initialChatMutation = captureDurableChatMutation()
        draftsByConversation.remove(conversation.id)
        expectedRecipientsByMessage[displayMessageId] = remainingTargetFingerprints.toSet()

        if (transportMode == TransportMode.Lan && !transportManager.hasLanConnection()) {
            appendMessage(
                conversation.id,
                ChatBubble(
                    text = cleanText,
                    mine = true,
                    encrypted = true,
                    timestamp = nowTime(),
                    messageId = displayMessageId,
                    quotedMessage = quotedMessage,
                    forwarded = forwarded,
                    forwardCount = forwardCount,
                    deliveryState =
                        if (requeueWhenOffline && remainingTargetFingerprints.isNotEmpty()) {
                            DeliveryState.Waiting
                        } else {
                            DeliveryState.Failed
                        }
                )
            )
            val queuedTextMessage =
                if (requeueWhenOffline && remainingTargetFingerprints.isNotEmpty()) {
                    PendingOutboundMessage(
                        conversationId = conversation.id,
                        text = cleanText,
                        displayMessageId = displayMessageId,
                        remainingTargetFingerprints = remainingTargetFingerprints,
                        quotedMessage = quotedMessage,
                        forwarded = forwarded,
                        forwardCount = forwardCount
                    ).also { pending -> pendingOutboundMessages[displayMessageId] = pending }
                } else {
                    null
                }
            if (queuedTextMessage == null) {
                trustState = "局域网未连接"
            } else {
                val appliedChatMutation = captureDurableChatMutation()
                val expectedPersistedMessage =
                    requireNotNull(
                        messagesForConversation(conversation.id)
                            .firstOrNull { message -> message.messageId == displayMessageId }
                            ?.toPersisted()
                    )
                trustState = "正在安全保存待发送消息"
                coroutineScope.launch {
                    val persisted =
                        persistChatStateNow { state ->
                            state.pendingTextOutbox.any { queued ->
                                queued.displayMessageId == displayMessageId &&
                                    queued.conversationId == conversation.id &&
                                    queued.text == cleanText &&
                                    queued.remainingTargetFingerprints.toSet() ==
                                    remainingTargetFingerprints.toSet()
                            } &&
                                state.messagesByConversation[conversation.id].orEmpty().any { message ->
                                    message == expectedPersistedMessage
                                }
                        }
                    if (persisted) {
                        trustState = "等待网络恢复"
                    } else {
                        rollbackDurableChatMutation(initialChatMutation, appliedChatMutation)
                        if (chatStateWritable) {
                            persistChatStateNow()
                        }
                        trustState = "消息未能安全保存，未加入待发送"
                    }
                }
            }
            return
        }

        if (conversation.memberFingerprints.isEmpty()) {
            trustState = if (pendingPeer == null) "等待配对" else "请先确认校验码"
            appendMessage(
                conversation.id,
                ChatBubble(
                    text = if (pendingPeer == null) "群聊还没有成员，请先完成配对" else "请先确认配对校验码",
                    mine = false,
                    encrypted = true,
                    timestamp = nowTime(),
                    deliveryState = DeliveryState.Waiting
                )
            )
            return
        }

        val targets = targetsForConversation(conversation)
        if (targets.isEmpty()) {
            var offlineState =
                if (conversation.memberFingerprints.any(::isPeerBlocked)) {
                    "可发送成员未在线"
                } else {
                    "成员未在线"
                }
            appendMessage(
                conversation.id,
                ChatBubble(
                    text = cleanText,
                    mine = true,
                    encrypted = true,
                    timestamp = nowTime(),
                    messageId = displayMessageId,
                    deliveryState = DeliveryState.Waiting,
                    quotedMessage = quotedMessage,
                    forwarded = forwarded,
                    forwardCount = forwardCount
                )
            )
            val queuedTextMessage =
                if (requeueWhenOffline && remainingTargetFingerprints.isNotEmpty()) {
                    PendingOutboundMessage(
                        conversationId = conversation.id,
                        text = cleanText,
                        displayMessageId = displayMessageId,
                        remainingTargetFingerprints = remainingTargetFingerprints,
                        quotedMessage = quotedMessage,
                        forwarded = forwarded,
                        forwardCount = forwardCount
                    ).also { pending -> pendingOutboundMessages[displayMessageId] = pending }
                } else {
                    if (requeueWhenOffline) {
                        updateMessageState(displayMessageId, DeliveryState.Failed)
                        offlineState = "成员均已阻止"
                    }
                    null
                }
            if (queuedTextMessage == null) {
                trustState = offlineState
            } else {
                val appliedChatMutation = captureDurableChatMutation()
                val expectedPersistedMessage =
                    requireNotNull(
                        messagesForConversation(conversation.id)
                            .firstOrNull { message -> message.messageId == displayMessageId }
                            ?.toPersisted()
                    )
                trustState = "正在安全保存待发送消息"
                coroutineScope.launch {
                    val persisted =
                        persistChatStateNow { state ->
                            state.pendingTextOutbox.any { queued ->
                                queued.displayMessageId == displayMessageId &&
                                    queued.conversationId == conversation.id &&
                                    queued.text == cleanText &&
                                    queued.remainingTargetFingerprints.toSet() ==
                                    remainingTargetFingerprints.toSet()
                            } &&
                                state.messagesByConversation[conversation.id].orEmpty().any { message ->
                                    message == expectedPersistedMessage
                                }
                        }
                    if (persisted) {
                        trustState = offlineState
                    } else {
                        rollbackDurableChatMutation(initialChatMutation, appliedChatMutation)
                        if (chatStateWritable) {
                            persistChatStateNow()
                        }
                        trustState = "消息未能安全保存，未加入待发送"
                    }
                }
            }
            return
        }

        appendMessage(
            conversation.id,
            ChatBubble(
                text = cleanText,
                mine = true,
                encrypted = true,
                timestamp = nowTime(),
                messageId = displayMessageId,
                deliveryState = DeliveryState.Sending,
                quotedMessage = quotedMessage,
                forwarded = forwarded,
                forwardCount = forwardCount
            )
        )

        sendPreparedMessage(
            conversation = conversation,
            text = cleanText,
            displayMessageId = displayMessageId,
            targets = targets,
            requeueOnFailure = requeueWhenOffline,
            quotedMessage = quotedMessage,
            forwarded = forwarded,
            forwardCount = forwardCount
        )
    }

    fun sendQuickReply(text: String) {
        val quote = pendingQuotedMessage
        pendingQuotedMessage = null
        val conversation = activeConversation()
        sendMessageToConversation(conversation, text, quotedMessage = quote)
    }

    fun saveDraft(
        conversation: ChatConversation,
        text: String
    ) {
        val draftText = text.trim().take(MAX_CUSTOM_MESSAGE_CHARS)
        if (draftText.isBlank()) {
            draftsByConversation.remove(conversation.id)
            trustState = "草稿已清除"
            return
        }
        if (!canSendToConversation(conversation)) {
            draftsByConversation.remove(conversation.id)
            trustState = "公告模式下不保存草稿"
            return
        }
        draftsByConversation[conversation.id] = ConversationDraft(draftText)
        conversationUpdateSequence += 1
        conversationUpdateOrder[conversation.id] = conversationUpdateSequence
        trustState = "草稿已保存"
    }

    fun sendDraft(conversation: ChatConversation) {
        val draft = draftsByConversation[conversation.id] ?: return
        if (!canSendToConversation(conversation)) {
            trustState = "公告模式下不能发送草稿"
            return
        }
        activeConversationId = conversation.id
        sendMessageToConversation(conversation, draft.text)
    }

    fun clearDraft(conversation: ChatConversation) {
        if (draftsByConversation.remove(conversation.id) != null) {
            conversationUpdateSequence += 1
            conversationUpdateOrder[conversation.id] = conversationUpdateSequence
            trustState = "草稿已清除"
        }
    }

    fun forwardMessageToConversation(
        targetConversation: ChatConversation,
        message: ChatBubble
    ) {
        val nextForwardCount = message.nextForwardCount()
        if (shouldBlockFrequentlyForwarded(targetConversation.id, nextForwardCount)) {
            trustState = "目标群聊禁止多次转发"
            return
        }
        pendingForwardMessage = null
        activeConversationId = targetConversation.id
        appSurface = AppSurface.Chat
        sendMessageToConversation(
            conversation = targetConversation,
            text = message.forwardText(),
            forwarded = true,
            forwardCount = nextForwardCount
        )
    }

    fun prepareDirectReplyToSender(
        sourceConversation: ChatConversation,
        message: ChatBubble
    ): Boolean {
        if (sourceConversation.kind != ConversationKind.Group || message.mine) {
            trustState = "只能私聊回复群消息"
            return false
        }
        val senderFingerprint = message.senderFingerprint
        val sender = senderFingerprint?.let(::trustedPeer)
        if (sender == null) {
            trustState = "发送者未信任"
            return false
        }
        val directConversationId = ensureDirectConversation(sender)
        pendingQuotedMessage = message.toQuotedMessage(sourceConversation)
        pendingDirectReply =
            PendingDirectReply(
                conversationId = directConversationId,
                title = sender.deviceName
            )
        appSurface = AppSurface.Chat
        return true
    }

    fun saveEditedRetryMessage(
        edit: PendingMessageEdit,
        editedText: String
    ) {
        val conversation = conversationById(edit.conversationId)
        val cleanText = editedText.trim().take(MAX_CUSTOM_MESSAGE_CHARS)
        if (conversation == null || cleanText.isBlank()) {
            trustState = if (cleanText.isBlank()) "编辑内容为空" else "聊天不可用"
            return
        }
        val currentMessage =
            messagesForConversation(conversation.id)
                .firstOrNull { message ->
                    message.messageId == edit.messageId &&
                        message.kind == ChatMessageKind.Text &&
                        message.canRetry()
                }
        if (currentMessage == null) {
            trustState = "消息不可编辑"
            return
        }
        if (!canSendToConversation(conversation)) {
            trustState = "公告模式下不能编辑重发"
            return
        }
        if (isConversationBlocked(conversation)) {
            trustState = "已阻止此联系人"
            return
        }

        val beforeMutation = captureDurableChatMutation()
        val beforeStarredIds = starredMessageIdsByConversation.toMap()
        val beforePinnedIds = pinnedMessageIdsByConversation.toMap()
        val beforeSelectedActionMessage = selectedActionMessage
        val queuedRecipients =
            pendingOutboundMessages[edit.messageId]?.remainingTargetFingerprints
        val expectedRecipients =
            expectedRecipientsForMessage(
                displayMessageId = edit.messageId,
                conversation = conversation,
                queuedRecipients = queuedRecipients
            )
        val intendedRecipients =
            (queuedRecipients?.toSet() ?: expectedRecipients)
                .intersect(expectedRecipients)
                .toList()
        val capacityIssue =
            validateTextSendCapacity(
                displayMessageId = edit.messageId,
                recipientCount = intendedRecipients.size,
                conversationId = conversation.id,
                persistOutbox = intendedRecipients.isNotEmpty()
            )
        if (capacityIssue != null) {
            rollbackDurableChatMutation(beforeMutation, captureDurableChatMutation())
            trustState = outboxCapacityMessage(capacityIssue)
            return
        }

        val oldStableId = currentMessage.stableStarId()
        val editedMessage = currentMessage.copy(text = cleanText)
        val newStableId = editedMessage.stableStarId()
        conversationMessages[conversation.id] =
            messagesForConversation(conversation.id).map { message ->
                if (message.messageId == edit.messageId) {
                    editedMessage
                } else {
                    message
                }
            }
        if (oldStableId != newStableId) {
            val starredIds = starredMessageIds(conversation.id)
            if (oldStableId in starredIds) {
                starredMessageIdsByConversation[conversation.id] = starredIds - oldStableId + newStableId
            }
            if (pinnedMessageIdsByConversation[conversation.id] == oldStableId) {
                pinnedMessageIdsByConversation[conversation.id] = newStableId
            }
        }
        pendingOutboundVoiceMessages.remove(edit.messageId)
        outgoingMessages
            .filterValues { outgoing -> outgoing.displayMessageId == edit.messageId }
            .keys
            .toList()
            .forEach { packetMessageId -> outgoingMessages.remove(packetMessageId) }
        deliveredReceiptsByMessage.remove(edit.messageId)
        readReceiptsByMessage.remove(edit.messageId)
        deliveredCounts.remove(edit.messageId)
        readCounts.remove(edit.messageId)
        if (intendedRecipients.isEmpty()) {
            pendingOutboundMessages.remove(edit.messageId)
            expectedRecipientsByMessage.remove(edit.messageId)
        } else {
            pendingOutboundMessages[edit.messageId] =
                PendingOutboundMessage(
                    conversationId = conversation.id,
                    text = cleanText,
                    displayMessageId = edit.messageId,
                    remainingTargetFingerprints = intendedRecipients,
                    quotedMessage = editedMessage.quotedMessage,
                    forwarded = editedMessage.forwarded,
                    forwardCount = editedMessage.forwardCount
                )
            expectedRecipientsByMessage[edit.messageId] = expectedRecipients
        }
        selectedActionMessage =
            selectedActionMessage?.let { actionMessage ->
                if (actionMessage.messageId == edit.messageId) editedMessage else actionMessage
            }
        activeConversationId = conversation.id
        appSurface = AppSurface.Chat

        val targets = targetsForFingerprints(intendedRecipients)
        val nextDeliveryState =
            when {
                intendedRecipients.isEmpty() -> DeliveryState.Failed
                targets.isEmpty() -> DeliveryState.Waiting
                else -> DeliveryState.Sending
            }
        updateMessageState(edit.messageId, nextDeliveryState)
        val appliedMutation = captureDurableChatMutation()
        val appliedStarredIds = starredMessageIdsByConversation.toMap()
        val appliedPinnedIds = pinnedMessageIdsByConversation.toMap()
        val appliedSelectedActionMessage = selectedActionMessage
        val expectedRecipientSet = intendedRecipients.toSet()
        val persistedDeliveryState = PersistedDeliveryState.valueOf(nextDeliveryState.name)
        val successState =
            when {
                intendedRecipients.isEmpty() -> "成员均已阻止"
                targets.isEmpty() -> "已编辑，等待重发"
                else -> "已编辑，正在重发"
            }
        persistDurableUserMutation(
            before = beforeMutation,
            applied = appliedMutation,
            requiredState = { state ->
                val persistedMessage =
                    state.messagesByConversation[conversation.id].orEmpty()
                        .firstOrNull { message -> message.messageId == edit.messageId }
                val textOutboxMatches =
                    if (expectedRecipientSet.isEmpty()) {
                        state.pendingTextOutbox.none { queued ->
                            queued.displayMessageId == edit.messageId
                        }
                    } else {
                        state.pendingTextOutbox.any { queued ->
                            queued.conversationId == conversation.id &&
                                queued.displayMessageId == edit.messageId &&
                                queued.text == cleanText &&
                                queued.remainingTargetFingerprints.toSet() == expectedRecipientSet &&
                                queued.quotedMessage == editedMessage.quotedMessage?.toPersisted() &&
                                queued.forwarded == editedMessage.forwarded &&
                                queued.forwardCount == editedMessage.forwardCount
                        }
                    }
                persistedMessage?.text == cleanText &&
                    persistedMessage.deliveryState == persistedDeliveryState &&
                    textOutboxMatches &&
                    state.pendingVoiceOutbox.none { queued ->
                        queued.displayMessageId == edit.messageId
                    } &&
                    state.outgoingEnvelopes.values.none { outgoing ->
                        outgoing.displayMessageId == edit.messageId
                    } &&
                    (if (expectedRecipientSet.isEmpty()) {
                        edit.messageId !in state.expectedRecipientsByMessage
                    } else {
                        state.expectedRecipientsByMessage[edit.messageId] == expectedRecipients
                    }) &&
                    edit.messageId !in state.deliveredRecipientsByMessage &&
                    edit.messageId !in state.readRecipientsByMessage
            },
            successState = successState,
            failureState = "编辑未能安全保存，已恢复原消息",
            onSuccess = {
                if (targets.isNotEmpty()) {
                    sendPreparedMessage(
                        conversation = conversation,
                        text = cleanText,
                        displayMessageId = edit.messageId,
                        targets = targets,
                        requeueOnFailure = true,
                        quotedMessage = editedMessage.quotedMessage,
                        forwarded = editedMessage.forwarded,
                        forwardCount = editedMessage.forwardCount
                    )
                }
            },
            onRollback = {
                rollbackMapChanges(
                    starredMessageIdsByConversation,
                    beforeStarredIds,
                    appliedStarredIds
                )
                rollbackMapChanges(
                    pinnedMessageIdsByConversation,
                    beforePinnedIds,
                    appliedPinnedIds
                )
                if (selectedActionMessage == appliedSelectedActionMessage) {
                    selectedActionMessage = beforeSelectedActionMessage
                }
            }
        )
    }

    fun prepareEditRetryMessage(
        conversation: ChatConversation,
        message: ChatBubble
    ): Boolean {
        val messageId = message.messageId
        if (messageId == null || message.kind != ChatMessageKind.Text || !message.canRetry()) {
            trustState = "只能编辑未发送文字"
            return false
        }
        if (!canSendToConversation(conversation)) {
            trustState = "公告模式下不能编辑重发"
            return false
        }
        pendingMessageEdit =
            PendingMessageEdit(
                conversationId = conversation.id,
                messageId = messageId
            )
        return true
    }

    fun retryMessage(
        conversation: ChatConversation,
        message: ChatBubble
    ) {
        val displayMessageId = message.messageId ?: return
        if (!canSendToConversation(conversation)) {
            trustState = "公告模式下不能重发"
            return
        }
        if (isConversationBlocked(conversation)) {
            trustState = "已阻止此联系人"
            return
        }
        if (!message.canRetry()) {
            trustState = "这条消息不需要重发"
            return
        }
        val voiceDurationMs =
            if (message.kind == ChatMessageKind.Voice) message.voiceDurationMs ?: return else null
        val voiceAudioBytes =
            if (message.kind == ChatMessageKind.Voice) message.voiceAudioBytes ?: return else null
        val beforeMutation = captureDurableChatMutation()
        val queuedRecipients =
            when (message.kind) {
                ChatMessageKind.Text ->
                    pendingOutboundMessages[displayMessageId]?.remainingTargetFingerprints
                ChatMessageKind.Voice ->
                    pendingOutboundVoiceMessages[displayMessageId]?.remainingTargetFingerprints
            }
        val expectedRecipients =
            expectedRecipientsForMessage(displayMessageId, conversation, queuedRecipients)
        val remainingRecipients =
            (queuedRecipients?.toSet() ?: expectedRecipients)
                .intersect(expectedRecipients)
                .toList()
        val capacityIssue =
            when (message.kind) {
                ChatMessageKind.Text ->
                    validateTextSendCapacity(
                        displayMessageId = displayMessageId,
                        recipientCount = remainingRecipients.size,
                        conversationId = conversation.id,
                        persistOutbox = remainingRecipients.isNotEmpty()
                    )
                ChatMessageKind.Voice ->
                    validateVoiceSendCapacity(
                        displayMessageId = displayMessageId,
                        audioByteCount = voiceAudioBytes?.size ?: return,
                        recipientCount = remainingRecipients.size,
                        conversationId = conversation.id,
                        persistOutbox = remainingRecipients.isNotEmpty()
                    )
            }
        if (capacityIssue != null) {
            rollbackDurableChatMutation(beforeMutation, captureDurableChatMutation())
            trustState = outboxCapacityMessage(capacityIssue)
            return
        }
        val targets = targetsForFingerprints(remainingRecipients)
        if (targets.isEmpty()) {
            val allowedTargetFingerprints = remainingRecipients
            if (allowedTargetFingerprints.isEmpty()) {
                pendingOutboundMessages.remove(displayMessageId)
                pendingOutboundVoiceMessages.remove(displayMessageId)
                outgoingMessages
                    .filterValues { outgoing -> outgoing.displayMessageId == displayMessageId }
                    .keys
                    .toList()
                    .forEach { packetMessageId -> outgoingMessages.remove(packetMessageId) }
                expectedRecipientsByMessage.remove(displayMessageId)
                deliveredReceiptsByMessage.remove(displayMessageId)
                readReceiptsByMessage.remove(displayMessageId)
                deliveredCounts.remove(displayMessageId)
                readCounts.remove(displayMessageId)
                updateMessageState(displayMessageId, DeliveryState.Failed)
            } else {
                expectedRecipientsByMessage[displayMessageId] = expectedRecipients
                updateMessageState(displayMessageId, DeliveryState.Waiting)
                when (message.kind) {
                    ChatMessageKind.Text -> {
                        pendingOutboundVoiceMessages.remove(displayMessageId)
                        pendingOutboundMessages[displayMessageId] =
                            PendingOutboundMessage(
                                conversationId = conversation.id,
                                text = message.text,
                                displayMessageId = displayMessageId,
                                remainingTargetFingerprints = allowedTargetFingerprints,
                                quotedMessage = message.quotedMessage,
                                forwarded = message.forwarded,
                                forwardCount = message.forwardCount
                            )
                    }

                    ChatMessageKind.Voice -> {
                        pendingOutboundMessages.remove(displayMessageId)
                        pendingOutboundVoiceMessages[displayMessageId] =
                            PendingOutboundVoiceMessage(
                                conversationId = conversation.id,
                                displayMessageId = displayMessageId,
                                remainingTargetFingerprints = allowedTargetFingerprints,
                                durationMs = voiceDurationMs ?: return,
                                audioBytes = voiceAudioBytes ?: return
                            )
                    }
                }
            }
            val appliedMutation = captureDurableChatMutation()
            val expectedRecipientSet = expectedRecipients
            val allowedRecipientSet = allowedTargetFingerprints.toSet()
            val encodedVoiceAudio =
                voiceAudioBytes?.let { audio -> Base64.getEncoder().encodeToString(audio) }
            persistDurableUserMutation(
                before = beforeMutation,
                applied = appliedMutation,
                requiredState = { state ->
                    val persistedMessage =
                        state.messagesByConversation[conversation.id].orEmpty()
                            .firstOrNull { stored -> stored.messageId == displayMessageId }
                    val queueMatches =
                        if (allowedRecipientSet.isEmpty()) {
                            state.pendingTextOutbox.none { queued ->
                                queued.displayMessageId == displayMessageId
                            } &&
                                state.pendingVoiceOutbox.none { queued ->
                                    queued.displayMessageId == displayMessageId
                                }
                        } else {
                            when (message.kind) {
                                ChatMessageKind.Text ->
                                    state.pendingTextOutbox.any { queued ->
                                        queued.conversationId == conversation.id &&
                                            queued.displayMessageId == displayMessageId &&
                                            queued.text == message.text &&
                                            queued.remainingTargetFingerprints.toSet() == allowedRecipientSet &&
                                            queued.quotedMessage == message.quotedMessage?.toPersisted() &&
                                            queued.forwarded == message.forwarded &&
                                            queued.forwardCount == message.forwardCount
                                    } &&
                                        state.pendingVoiceOutbox.none { queued ->
                                            queued.displayMessageId == displayMessageId
                                        }
                                ChatMessageKind.Voice ->
                                    state.pendingVoiceOutbox.any { queued ->
                                        queued.conversationId == conversation.id &&
                                            queued.displayMessageId == displayMessageId &&
                                            queued.remainingTargetFingerprints.toSet() == allowedRecipientSet &&
                                            queued.durationMs == voiceDurationMs &&
                                            queued.audioBase64 == encodedVoiceAudio
                                    } &&
                                        state.pendingTextOutbox.none { queued ->
                                            queued.displayMessageId == displayMessageId
                                        }
                            }
                        }
                    persistedMessage?.deliveryState ==
                        (if (allowedRecipientSet.isEmpty()) {
                            PersistedDeliveryState.Failed
                        } else {
                            PersistedDeliveryState.Waiting
                        }) &&
                        queueMatches &&
                        (if (allowedRecipientSet.isEmpty()) {
                            displayMessageId !in state.expectedRecipientsByMessage &&
                                displayMessageId !in state.deliveredRecipientsByMessage &&
                                displayMessageId !in state.readRecipientsByMessage &&
                                state.outgoingEnvelopes.values.none { outgoing ->
                                    outgoing.displayMessageId == displayMessageId
                                }
                        } else {
                            state.expectedRecipientsByMessage[displayMessageId] == expectedRecipientSet
                        })
                },
                successState =
                    if (allowedRecipientSet.isEmpty()) {
                        "成员均已阻止"
                    } else {
                        "等待对方上线"
                    },
                failureState = "重试未能安全保存，已恢复"
            )
            return
        }
        updateMessageState(displayMessageId, DeliveryState.Sending)
        when (message.kind) {
            ChatMessageKind.Text ->
                sendPreparedMessage(
                    conversation = conversation,
                    text = message.text,
                    displayMessageId = displayMessageId,
                    targets = targets,
                    requeueOnFailure = true,
                    quotedMessage = message.quotedMessage,
                    forwarded = message.forwarded,
                    forwardCount = message.forwardCount
                )

            ChatMessageKind.Voice ->
                sendPreparedVoiceMessage(
                    conversation = conversation,
                    displayMessageId = displayMessageId,
                    durationMs = voiceDurationMs ?: return,
                    audioBytes = voiceAudioBytes ?: return,
                    targets = targets,
                    requeueOnFailure = true
                )
        }
        trustState = "正在重发"
    }

    fun retryConversationMessages(conversation: ChatConversation) {
        if (!canSendToConversation(conversation)) {
            trustState = "公告模式下不能重发"
            return
        }
        val retryableMessages = messagesForConversation(conversation.id).filter { message -> message.canRetry() }
        if (retryableMessages.isEmpty()) {
            trustState = "没有可重发消息"
            return
        }
        retryableMessages.forEach { message ->
            retryMessage(conversation, message)
        }
    }

    fun clearRetryableMessages(conversation: ChatConversation): Int {
        val messages = messagesForConversation(conversation.id)
        val retryableMessages = messages.filter { message -> message.canRetry() }
        if (retryableMessages.isEmpty()) {
            return 0
        }
        val retryableIds = retryableMessages.map { message -> message.stableStarId() }.toSet()
        conversationMessages[conversation.id] =
            messages.filterNot { message -> message.stableStarId() in retryableIds }
        retryableMessages.forEach { message ->
            removeMessageCaches(conversation.id, message)
        }
        selectedActionMessage =
            selectedActionMessage?.takeUnless { message -> message.stableStarId() in retryableIds }
        pendingForwardMessage =
            pendingForwardMessage?.takeUnless { message -> message.stableStarId() in retryableIds }
        pendingMessageEdit =
            pendingMessageEdit?.takeUnless { edit ->
                retryableMessages.any { message -> message.messageId == edit.messageId }
            }
        persistRemovedMessages(conversation.id, retryableMessages)
        return retryableMessages.size
    }

    fun toggleVoiceRecording() {
        if (isRecordingVoice) {
            val conversation =
                recordingConversationId
                    ?.let(::conversationById)
                    ?: run {
                        voiceRecorder.cancel()
                        clearVoiceRecordingState()
                        trustState = "聊天不存在，已取消录音"
                        return
                    }
            runCatching {
                voiceRecorder.stop()
            }.onSuccess { recordedVoice ->
                clearVoiceRecordingState()
                if (recordedVoice == null) {
                    trustState = "语音太短"
                } else if (!canSendToConversation(conversation)) {
                    recordedVoice.file.delete()
                    trustState = "公告模式下已取消录音"
                } else {
                    sendVoiceToConversation(conversation, recordedVoice)
                }
            }.onFailure { error ->
                voiceRecorder.cancel()
                clearVoiceRecordingState()
                trustState = error.readableMessage("无法完成录音")
            }
            return
        }
        val conversation = activeConversation()
        if (!canSendToConversation(conversation)) {
            trustState = "公告模式下不能录语音"
            return
        }
        if (
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            trustState = "语音需要录音权限"
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        if (!isRecordingVoice) {
            runCatching {
                voiceRecorder.start()
            }.onSuccess {
                isRecordingVoice = true
                recordingStartedAtMillis = System.currentTimeMillis()
                recordingElapsedMillis = 0L
                recordingConversationId = conversation.id
                trustState = "正在录音"
            }.onFailure { error ->
                trustState = error.readableMessage("无法开始录音")
            }
            return
        }
    }

    fun cancelVoiceRecording() {
        if (!isRecordingVoice) {
            return
        }
        voiceRecorder.cancel()
        clearVoiceRecordingState()
        trustState = "已取消录音"
    }

    fun playVoiceMessage(message: ChatBubble) {
        val audioBytes = message.voiceAudioBytes
        if (message.kind != ChatMessageKind.Voice || audioBytes == null || message.canRetry()) {
            messageActionsBackStack.clear()
            messageActionsReturnSurface = appSurface
            selectedActionMessage = message
            appSurface = AppSurface.MessageActions
            return
        }
        stopActiveVoicePlayback()
        var playbackFile: File? = null
        var player: MediaPlayer? = null
        runCatching {
            playbackFile = voicePlaybackCache.create(audioBytes)
            player = MediaPlayer()
            val currentFile = checkNotNull(playbackFile)
            val currentPlayer = checkNotNull(player)
            currentPlayer.setDataSource(currentFile.absolutePath)
            currentPlayer.setOnCompletionListener { completedPlayer ->
                if (activePlayer === completedPlayer) {
                    activePlayer = null
                    activePlaybackFile = null
                }
                releaseVoicePlayback(completedPlayer, currentFile)
            }
            currentPlayer.setOnErrorListener { failedPlayer, _, _ ->
                val wasActive = activePlayer === failedPlayer
                if (wasActive) {
                    activePlayer = null
                    activePlaybackFile = null
                }
                releaseVoicePlayback(failedPlayer, currentFile)
                if (wasActive) {
                    trustState = "语音播放失败"
                }
                true
            }
            currentPlayer.prepare()
            currentPlayer.playbackParams =
                currentPlayer.playbackParams.setSpeed(voicePlaybackSpeed.speed)
            activePlayer = currentPlayer
            activePlaybackFile = currentFile
            currentPlayer.start()
            if (activePlayer === currentPlayer) {
                trustState = "正在播放语音 ${voicePlaybackSpeed.label}"
            }
        }.onFailure { error ->
            if (activePlayer === player) {
                activePlayer = null
                activePlaybackFile = null
            }
            releaseVoicePlayback(player, playbackFile)
            trustState = error.readableMessage("语音播放失败")
        }
    }

    fun openVoicePlayback(message: ChatBubble) {
        if (message.kind != ChatMessageKind.Voice || message.voiceAudioBytes == null || message.canRetry()) {
            messageActionsBackStack.clear()
            messageActionsReturnSurface = appSurface
            selectedActionMessage = message
            appSurface = AppSurface.MessageActions
            return
        }
        messageActionsBackStack.clear()
        selectedActionMessage = message
        voicePlaybackReturnSurface = appSurface
        appSurface = AppSurface.VoicePlayback
    }

    LaunchedEffect(
        pendingOutboundMessages.toMap(),
        pendingOutboundVoiceMessages.toMap(),
        knownPeersByFingerprint.toMap(),
        blockedPeerFingerprints.toMap(),
        peerRouteRevision,
        recipientSetRevision,
        outboundQueueWakeRevision,
        chatStateOperational
    ) {
        if (!chatStateOperational) {
            return@LaunchedEffect
        }
        pendingOutboundMessages.values
            .toList()
            .filter { queuedReply ->
                queuedReply.remainingTargetFingerprints.any { fingerprint ->
                    !isPeerBlocked(fingerprint) && knownPeersByFingerprint[fingerprint] != null
                }
            }
            .forEach { queuedReply -> trySendPendingOutboundMessage(queuedReply) }
        pendingOutboundVoiceMessages.values
            .toList()
            .filter { queuedVoice ->
                queuedVoice.remainingTargetFingerprints.any { fingerprint ->
                    !isPeerBlocked(fingerprint) && knownPeersByFingerprint[fingerprint] != null
                }
            }
            .forEach { queuedVoice -> trySendPendingOutboundVoiceMessage(queuedVoice) }
    }

    fun handleNotificationIntent(intent: Intent) {
        if (!notifier.isTrustedNotificationIntent(intent)) {
            return
        }
        val conversationId =
            intent.getStringExtra(SpotChatNotificationIntents.EXTRA_CONVERSATION_ID) ?: return
        val conversation =
            conversationById(conversationId)
                ?: return showWearEntryRecovery(
                    source = "通知",
                    targetConversationId = conversationId,
                    actionLabel =
                        when (intent.action) {
                            SpotChatNotificationIntents.ACTION_REPLY,
                            SpotChatNotificationIntents.ACTION_QUICK_REPLY -> "回复"
                            SpotChatNotificationIntents.ACTION_MARK_READ -> "标为已读"
                            SpotChatNotificationIntents.ACTION_MUTE_8H -> "静音"
                            SpotChatNotificationIntents.ACTION_NOTIFICATION_DISMISSED -> "清除通知"
                            else -> "打开聊天"
                        }
                )
        if (intent.action == SpotChatNotificationIntents.ACTION_MUTE_8H) {
            setConversationMute(conversation, MutePreset.EightHours)
            showActionConfirmation(
                title = "已静音 ${conversation.title}",
                detail = "8 小时内不再提醒"
            )
            return
        }
        clearConversationAlerts(conversation.id)
        if (intent.action == SpotChatNotificationIntents.ACTION_NOTIFICATION_DISMISSED) {
            trustState = "已清除 ${conversation.title} 的通知"
            showActionConfirmation(
                title = "已清除通知",
                detail = conversation.title
            )
            return
        }
        if (intent.action == SpotChatNotificationIntents.ACTION_MARK_READ) {
            markConversationRead(conversation.id)
            trustState = "已标为已读"
            showActionConfirmation(
                title = "已标为已读",
                detail = conversation.title
            )
            return
        }
        val notificationAction =
            when (intent.action) {
                SpotChatNotificationIntents.ACTION_REPLY -> WearEntryAction.Reply
                SpotChatNotificationIntents.ACTION_QUICK_REPLY -> WearEntryAction.QuickReply
                else -> WearEntryAction.OpenChat
            }
        openConversationFromWearEntry(
            conversation = conversation,
            source = "通知",
            action = notificationAction
        )

        if (intent.action == SpotChatNotificationIntents.ACTION_QUICK_REPLY) {
            val replyText =
                intent
                    .getStringExtra(SpotChatNotificationIntents.EXTRA_QUICK_REPLY_TEXT)
                    ?.trim()
                    ?.take(MAX_CUSTOM_MESSAGE_CHARS)
                    .orEmpty()
            if (replyText.isNotBlank()) {
                sendMessageToConversation(conversation, replyText)
                trustState = "已快捷回复 ${conversation.title}"
                showActionConfirmation(
                    title = "已快捷回复",
                    detail = "${conversation.title} · ${notificationReplyPreview(replyText)}"
                )
            }
            return
        }

        if (intent.action != SpotChatNotificationIntents.ACTION_REPLY) {
            return
        }
        val replyText =
            RemoteInput
                .getResultsFromIntent(intent)
                ?.getCharSequence(SpotChatNotificationIntents.EXTRA_REMOTE_REPLY)
                ?.toString()
                ?.trim()
                ?.take(MAX_CUSTOM_MESSAGE_CHARS)
                .orEmpty()
        if (replyText.isNotBlank()) {
            sendMessageToConversation(conversation, replyText)
            trustState = "已回复 ${conversation.title}"
            showActionConfirmation(
                title = "已发送回复",
                detail = "${conversation.title} · ${notificationReplyPreview(replyText)}"
            )
        }
    }

    fun handleRecentChatsEntry(request: WearEntryRequest.RecentChats) {
        val conversationId = request.conversationId ?: return
        val conversation =
            conversationById(conversationId)
                ?: return showWearEntryRecovery(
                    source = "最近聊天 Tile",
                    targetConversationId = conversationId,
                    actionLabel = "打开聊天"
                )
        openConversationFromWearEntry(
            conversation = conversation,
            source = "最近聊天 Tile",
            action = WearEntryAction.OpenChat
        )
    }

    fun handleQuickVoiceEntry(request: WearEntryRequest.QuickVoice) {
        val conversationId = request.conversationId
        val conversation = conversationId?.let(::conversationById)
        if (conversation == null) {
            if (conversationId == null) {
                appSurface = AppSurface.ConversationList
                trustState = "选择聊天后点麦克风录音"
                return
            }
            showWearEntryRecovery(
                source = "语音 Tile",
                targetConversationId = conversationId,
                actionLabel = "开始语音"
            )
            return
        }
        openConversationFromWearEntry(
            conversation = conversation,
            source = "语音 Tile",
            action = WearEntryAction.Voice
        )
        trustState = "点麦克风开始语音"
    }

    fun handleQuickTextReplyEntry(request: WearEntryRequest.QuickTextReply) {
        val conversationId = request.conversationId
        val conversation = conversationId?.let(::conversationById)
        if (conversation == null) {
            if (conversationId == null) {
                appSurface = AppSurface.ConversationList
                trustState = "选择聊天后再快捷回复"
                return
            }
            showWearEntryRecovery(
                source = "快捷回复 Tile",
                targetConversationId = conversationId,
                actionLabel = "快捷回复"
            )
            return
        }
        openConversationFromWearEntry(
            conversation = conversation,
            source = "快捷回复 Tile",
            action = WearEntryAction.QuickReply
        )
        val replyText =
            request.replyText
                ?.trim()
                ?.take(MAX_CUSTOM_MESSAGE_CHARS)
                .orEmpty()
        if (replyText.isBlank()) {
            trustState = "选择一句快捷回复"
            return
        }
        clearConversationAlerts(conversation.id)
        sendMessageToConversation(conversation, replyText)
        trustState = "已快捷回复 ${conversation.title}"
        showActionConfirmation(
            title = "已快捷回复",
            detail = "${conversation.title} · ${notificationReplyPreview(replyText)}"
        )
    }

    LaunchedEffect(notificationIntent, trustedPeers.size, chatStateOperational) {
        if (!chatStateOperational) {
            return@LaunchedEffect
        }
        val intent = notificationIntent ?: return@LaunchedEffect
        val wearEntryResolution =
            resolveWearEntryIntent(
                fields =
                    WearEntryIntentFields(
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
                        token =
                            intent.getStringExtra(
                                SpotChatNotificationIntents.EXTRA_INTENT_TOKEN
                            ),
                        conversationId =
                            intent.getStringExtra(
                                SpotChatNotificationIntents.EXTRA_CONVERSATION_ID
                            ),
                        replyText =
                            intent.getStringExtra(
                                QuickTextReplyTileService.EXTRA_TILE_REPLY_TEXT
                            )
                    ),
                isTokenValid = notificationTokenStore::isValid
            )
        when (wearEntryResolution) {
            is WearEntryIntentResolution.Accepted -> {
                when (val request = wearEntryResolution.request) {
                    is WearEntryRequest.RecentChats -> handleRecentChatsEntry(request)
                    is WearEntryRequest.QuickVoice -> handleQuickVoiceEntry(request)
                    is WearEntryRequest.QuickTextReply -> handleQuickTextReplyEntry(request)
                }
            }

            WearEntryIntentResolution.NotWearEntry -> handleNotificationIntent(intent)
            WearEntryIntentResolution.Rejected -> Unit
        }
        onNotificationIntentHandled(intent)
    }

    LaunchedEffect(isRecordingVoice, recordingStartedAtMillis) {
        val startedAt = recordingStartedAtMillis ?: return@LaunchedEffect
        while (isRecordingVoice) {
            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            recordingElapsedMillis = elapsed.coerceAtMost(SpotChatVoiceRecorder.MAX_RECORDING_DURATION_MS)
            delay(250L)
        }
    }

    DisposableEffect(
        replayProtection,
        voicePlaybackCache,
        chatStateStore,
        chatStateDisposalScope
    ) {
        onDispose {
            if (chatStateLoadComplete && chatStateWritable) {
                val snapshotSequence = chatStateSnapshotSequence.incrementAndGet()
                val finalState = currentPersistedChatState()
                chatStateDisposalScope.launch {
                    runCatching {
                        synchronized(chatStateWriteLock) {
                            if (snapshotSequence > chatStateLastWrittenSequence.get()) {
                                chatStateStore.save(finalState).also { savedState ->
                                    chatStateLastWrittenSnapshot.set(savedState)
                                    chatStateLastWrittenSequence.set(snapshotSequence)
                                }
                            }
                        }
                    }
                }
            }
            voiceRecorder.cancel()
            stopActiveVoicePlayback()
            voicePlaybackCache.clear()
            replayProtection.close()
        }
    }

    val messageInputLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                draftSaveConversationId = null
                pendingMessageEdit = null
                if (pendingDirectReply != null) {
                    pendingDirectReply = null
                    pendingQuotedMessage = null
                }
                return@rememberLauncherForActivityResult
            }

            val message =
                result.data
                    ?.let { intent -> RemoteInput.getResultsFromIntent(intent) }
                    ?.getCharSequence(CUSTOM_MESSAGE_REMOTE_INPUT_KEY)
                    ?.toString()
                    ?.trim()
                    ?.take(MAX_CUSTOM_MESSAGE_CHARS)
                    .orEmpty()

            val draftConversationId = draftSaveConversationId
            draftSaveConversationId = null
            if (draftConversationId != null) {
                conversationById(draftConversationId)?.let { conversation ->
                    saveDraft(conversation, message)
                }
                pendingDirectReply = null
                pendingQuotedMessage = null
                return@rememberLauncherForActivityResult
            }

            val messageEdit = pendingMessageEdit
            pendingMessageEdit = null
            if (messageEdit != null) {
                saveEditedRetryMessage(messageEdit, message)
                pendingDirectReply = null
                pendingQuotedMessage = null
                return@rememberLauncherForActivityResult
            }

            if (message.isNotBlank()) {
                val directReply = pendingDirectReply
                if (directReply == null) {
                    sendQuickReply(message)
                } else {
                    val quotedMessage = pendingQuotedMessage
                    pendingQuotedMessage = null
                    pendingDirectReply = null
                    val directConversation = conversationById(directReply.conversationId)
                    if (directConversation == null) {
                        trustState = "私聊不可用"
                    } else {
                        activeConversationId = directConversation.id
                        clearConversationAlerts(directConversation.id)
                        markConversationRead(directConversation.id)
                        appSurface = AppSurface.Chat
                        sendMessageToConversation(
                            conversation = directConversation,
                            text = message,
                            quotedMessage = quotedMessage
                        )
                        trustState = "已私聊回复 ${directReply.title}"
                    }
                }
            } else if (pendingDirectReply != null) {
                pendingDirectReply = null
                pendingQuotedMessage = null
            }
        }

    val messageSearchLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                return@rememberLauncherForActivityResult
            }

            searchQuery =
                result.data
                    ?.let { intent -> RemoteInput.getResultsFromIntent(intent) }
                    ?.getCharSequence(SEARCH_MESSAGE_REMOTE_INPUT_KEY)
                    ?.toString()
                    ?.trim()
                    ?.take(MAX_SEARCH_QUERY_CHARS)
                    .orEmpty()
            appSurface = searchTargetSurface
        }

    val aliasInputLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val peerFingerprint = pendingAliasPeerFingerprint
            pendingAliasPeerFingerprint = null
            if (result.resultCode != Activity.RESULT_OK || peerFingerprint == null) {
                return@rememberLauncherForActivityResult
            }
            val alias =
                result.data
                    ?.let { intent -> RemoteInput.getResultsFromIntent(intent) }
                    ?.getCharSequence(ALIAS_REMOTE_INPUT_KEY)
                    ?.toString()
                    ?.trim()
                    ?.take(TrustedPeerStore.MAX_ALIAS_CHARS)
                    .orEmpty()
            trustedPeer(peerFingerprint)?.let { peer ->
                updateTrustedPeerAlias(peer, alias)
            }
        }

    val groupAboutInputLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val conversationId = editingGroupAboutConversationId
            editingGroupAboutConversationId = null
            if (result.resultCode != Activity.RESULT_OK || conversationId == null) {
                return@rememberLauncherForActivityResult
            }
            val about =
                result.data
                    ?.let { intent -> RemoteInput.getResultsFromIntent(intent) }
                    ?.getCharSequence(GROUP_ABOUT_REMOTE_INPUT_KEY)
                    ?.toString()
                    ?.trim()
                    ?.take(MAX_GROUP_ABOUT_CHARS)
                    .orEmpty()
            conversationById(conversationId)?.let { conversation ->
                updateGroupAbout(conversation, about)
            }
        }

    fun openCustomMessageInput(saveAsDraft: Boolean = false) {
        val conversation = activeConversation()
        if (!saveAsDraft && pendingMessageEdit == null && pendingDirectReply == null && !canSendToConversation(conversation)) {
            trustState = "公告模式下不能输入消息"
            return
        }
        if (saveAsDraft && !canSendToConversation(conversation)) {
            trustState = "公告模式下不保存草稿"
            return
        }
        draftSaveConversationId = if (saveAsDraft) conversation.id else null
        val remoteInputBuilder =
            RemoteInput.Builder(CUSTOM_MESSAGE_REMOTE_INPUT_KEY)
                .setLabel(if (saveAsDraft) "保存草稿" else "输入消息")
                .setChoices(customMessageQuickChoices)
                .setAllowFreeFormInput(true)
        WearableRemoteInputExtender(remoteInputBuilder)
            .setEmojisAllowed(true)
            .setInputActionType(EditorInfo.IME_ACTION_SEND)

        val inputIntent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        val quotedMessage = pendingQuotedMessage
        val directReply = pendingDirectReply
        RemoteInputIntentHelper.putTitleExtra(
            inputIntent,
            if (saveAsDraft) {
                "草稿 ${conversation.title}"
            } else if (pendingMessageEdit != null) {
                "编辑消息"
            } else when {
                directReply != null && quotedMessage != null -> "私聊回复 ${directReply.title}"
                quotedMessage != null -> "回复 ${quotedMessage.senderName}"
                else -> conversation.title
            }
        )
        RemoteInputIntentHelper.putConfirmLabelExtra(
            inputIntent,
            if (saveAsDraft || pendingMessageEdit != null) "保存" else "发送"
        )
        RemoteInputIntentHelper.putCancelLabelExtra(inputIntent, "取消")
        RemoteInputIntentHelper.putRemoteInputsExtra(inputIntent, listOf(remoteInputBuilder.build()))

        val replyContext =
            messagesForConversation(activeConversationId)
                .filter { message -> !message.mine && message.deliveryState != DeliveryState.System }
                .takeLast(3)
                .map { message -> message.text }
        if (replyContext.isNotEmpty()) {
            RemoteInputIntentHelper.putSmartReplyContextExtra(inputIntent, replyContext)
        }

        messageInputLauncher.launch(inputIntent)
    }

    fun openAliasInput(peer: StoredTrustedPeer) {
        pendingAliasPeerFingerprint = peer.fingerprint
        val remoteInputBuilder =
            RemoteInput.Builder(ALIAS_REMOTE_INPUT_KEY)
                .setLabel("联系人备注")
                .setChoices(arrayOf(peer.deviceName, peer.about.ifBlank { ProfileStore.DEFAULT_ABOUT }))
                .setAllowFreeFormInput(true)
        WearableRemoteInputExtender(remoteInputBuilder)
            .setEmojisAllowed(true)
            .setInputActionType(EditorInfo.IME_ACTION_DONE)

        val inputIntent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putTitleExtra(inputIntent, "备注 ${peer.deviceName}")
        RemoteInputIntentHelper.putConfirmLabelExtra(inputIntent, "保存")
        RemoteInputIntentHelper.putCancelLabelExtra(inputIntent, "取消")
        RemoteInputIntentHelper.putRemoteInputsExtra(inputIntent, listOf(remoteInputBuilder.build()))

        aliasInputLauncher.launch(inputIntent)
    }

    fun openGroupAboutInput(conversation: ChatConversation) {
        editingGroupAboutConversationId = conversation.id
        val remoteInputBuilder =
            RemoteInput.Builder(GROUP_ABOUT_REMOTE_INPUT_KEY)
                .setLabel("群公告")
                .setChoices(arrayOf("附近设备加密群聊", "临时讨论", "只发重要消息"))
                .setAllowFreeFormInput(true)
        WearableRemoteInputExtender(remoteInputBuilder)
            .setEmojisAllowed(true)
            .setInputActionType(EditorInfo.IME_ACTION_DONE)

        val inputIntent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putTitleExtra(inputIntent, "群公告")
        RemoteInputIntentHelper.putConfirmLabelExtra(inputIntent, "保存")
        RemoteInputIntentHelper.putCancelLabelExtra(inputIntent, "取消")
        RemoteInputIntentHelper.putRemoteInputsExtra(inputIntent, listOf(remoteInputBuilder.build()))

        groupAboutInputLauncher.launch(inputIntent)
    }

    fun openMessageSearchInput() {
        searchTargetSurface = AppSurface.MessageSearch
        val remoteInputBuilder =
            RemoteInput.Builder(SEARCH_MESSAGE_REMOTE_INPUT_KEY)
                .setLabel("查找消息")
                .setAllowFreeFormInput(true)
        WearableRemoteInputExtender(remoteInputBuilder)
            .setEmojisAllowed(true)
            .setInputActionType(EditorInfo.IME_ACTION_SEARCH)

        val inputIntent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putTitleExtra(inputIntent, "查找 ${activeConversation().title}")
        RemoteInputIntentHelper.putConfirmLabelExtra(inputIntent, "查找")
        RemoteInputIntentHelper.putCancelLabelExtra(inputIntent, "取消")
        RemoteInputIntentHelper.putRemoteInputsExtra(inputIntent, listOf(remoteInputBuilder.build()))

        messageSearchLauncher.launch(inputIntent)
    }

    LaunchedEffect(transportMode, deviceName, lanDiscoveryEnabled, chatStateOperational) {
        val transportKind = transportMode.kind
        activePeer = null
        activePeerFingerprint = null
        pendingPeer?.let { peer -> sessionCoordinator.rejectPendingSession(peer.fingerprint) }
        pendingPeer = null
        pendingPeerRoute = null
        pairingCode = null
        sessionCoordinator.clearHandshakeState()
        knownPeersByFingerprint.clear()

        if (!chatStateOperational) {
            trustState =
                if (chatStateLoadComplete) {
                    "本地聊天状态不可用，已暂停收发"
                } else {
                    "正在恢复本地聊天状态"
                }
            return@LaunchedEffect
        }

        if (transportMode == TransportMode.Lan && !lanDiscoveryEnabled) {
            trustState = "点按局域网开始发现"
            return@LaunchedEffect
        }

        runCatching { transportManager.start(transportKind) }
            .onFailure { error ->
                trustState = error.readableMessage("传输启动失败")
                return@LaunchedEffect
            }

        if (transportMode == TransportMode.Relay) {
            trustState = "中继预留"
        }

        if (transportMode == TransportMode.Bluetooth) {
            runCatching { transportManager.bondedBluetoothPeers() }
                .getOrDefault(emptyList())
                .forEach { peer ->
                    activePeer = peer
                    trustState = "尝试连接 ${peer.name}"
                    if (sessionCoordinator.shouldSendHello(peer)) {
                        coroutineScope.launch {
                            sendHello(transportKind, peer)
                        }
                    }
                }
        }

        try {
            transportManager.events(transportKind).collect { event ->
                try {
                    handleTransportEvent(transportKind, event)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    trustState = error.readableMessage("已忽略无效传输数据")
                }
            }
        } finally {
            transportManager.stop(transportKind)
        }
    }

    SpotChatTheme {
        val isRoundScreen = LocalConfiguration.current.isScreenRound
        val rootDismissState = rememberSwipeToDismissBoxState()
        LaunchedEffect(appSurface) {
            if (appSurface == AppSurface.ConversationList) {
                rootDismissState.snapTo(SwipeToDismissValue.Default)
            }
        }
        AppScaffold {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .padding(WatchSurfaceSpec(isRound = isRoundScreen, compact = false).appPadding),
                contentAlignment = Alignment.Center
            ) {
                val currentConversations = conversations()
                val visibleConversationListBase =
                    currentConversations.filterNot { conversation ->
                        archivedConversationIds[conversation.id] == true
                    }
                val chatListFilterContext =
                    ChatListFilterContext(
                        unreadCounts = unreadCounts,
                        mentionCounts = mentionCounts,
                        favoriteConversationIds = favoriteConversationIds,
                        pinnedConversationIds = pinnedConversationIds,
                        starredMessageIdsByConversation = starredMessageIdsByConversation,
                        draftsByConversation = draftsByConversation,
                        lockedConversationIds = lockedConversationIds,
                        disappearingModesByConversation = disappearingModesByConversation,
                        readReceiptsDisabledByConversation = readReceiptsDisabledByConversation,
                        isConversationMuted = ::isConversationMuted,
                        hasRetryableMessages = ::hasRetryableMessages
                    )
                val visibleConversationList =
                    filterConversations(
                        conversations = visibleConversationListBase,
                        filter = chatListFilter,
                        context = chatListFilterContext
                    )
                val chatListFilterCounts =
                    countConversationsByFilter(
                        conversations = visibleConversationListBase,
                        context = chatListFilterContext
                    )
                val archivedConversationList =
                    currentConversations.filter { conversation ->
                        archivedConversationIds[conversation.id] == true
                    }
                val archivedUnreadCount =
                    archivedConversationList.count { conversation ->
                        (unreadCounts[conversation.id] ?: 0) > 0
                    }
                val selectedConversation =
                    currentConversations.firstOrNull { conversation -> conversation.id == activeConversationId }
                        ?: currentConversations.first()
                val conversationListSurface: @Composable (Boolean) -> Unit = { profileNavigationEnabled ->
                    WatchConversationListSurface(
                        isRoundScreen = isRoundScreen,
                        profile = profile,
                        conversations = visibleConversationList,
                        activeFilter = chatListFilter,
                        filterCounts = chatListFilterCounts,
                        mentionCounts = mentionCounts,
                        archivedCount = archivedConversationList.size,
                        archivedUnreadCount = archivedUnreadCount,
                        unreadCounts = unreadCounts,
                        pinnedConversationIds = pinnedConversationIds,
                        isConversationMuted = ::isConversationMuted,
                        messagesByConversation = conversationMessages,
                        draftsByConversation = draftsByConversation,
                        lockedConversationIds = lockedConversationIds,
                        transportMode = transportMode,
                        lanDiscoveryEnabled = lanDiscoveryEnabled,
                        trustState = trustState,
                        fingerprint = localFingerprint,
                        pairingCode = pairingCode,
                        pendingPeer = pendingPeer,
                        trustedPeerCount = trustedPeers.size,
                        onSelectMode = ::selectMode,
                        onConfirmPairing = ::confirmPairing,
                        onRejectPairing = ::rejectPairing,
                        onOpenConversation = ::openConversation,
                        onToggleConversationPinned = ::toggleConversationPinned,
                        onToggleConversationMuted = { conversation ->
                            setConversationMute(
                                conversation = conversation,
                                preset =
                                    if (isConversationMuted(conversation.id)) {
                                        null
                                    } else {
                                        MutePreset.EightHours
                                    }
                            )
                        },
                        onToggleConversationArchived = ::toggleConversationArchived,
                        onOpenArchivedChats = {
                            appSurface = AppSurface.ArchivedChats
                        },
                        onSelectFilter = { selectedFilter ->
                            chatListFilter = selectedFilter
                        },
                        onOpenProfile = {
                            appSurface = AppSurface.Profile
                        },
                        profileNavigationEnabled = profileNavigationEnabled
                    )
                }

                if (appSurface == AppSurface.ConversationList) {
                    SwipeToDismissBox(
                        onDismissed = {
                            activity?.finish()
                        },
                        modifier = Modifier.fillMaxSize(),
                        state = rootDismissState,
                        backgroundKey = "SpotChatRootDismissBackground",
                        contentKey = AppSurface.ConversationList
                    ) { isBackground ->
                        if (isBackground) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black)
                            )
                        } else {
                            conversationListSurface(true)
                        }
                    }
                } else {
                    conversationListSurface(false)
                }

                if (appSurface == AppSurface.ArchivedChats) {
                    SlideInOverlay(
                        onDismissed = {
                            appSurface = AppSurface.ConversationList
                        }
                    ) { dismissOverlay ->
                        WatchArchivedChatsSurface(
                            isRoundScreen = isRoundScreen,
                            conversations = archivedConversationList,
                            archivedUnreadCount = archivedUnreadCount,
                            messagesByConversation = conversationMessages,
                            unreadCounts = unreadCounts,
                            pinnedConversationIds = pinnedConversationIds,
                            isConversationMuted = ::isConversationMuted,
                            draftsByConversation = draftsByConversation,
                            lockedConversationIds = lockedConversationIds,
                            onNavigateBack = dismissOverlay,
                            onMarkAllRead = {
                                markConversationsRead(
                                    conversations = archivedConversationList,
                                    statusText = "归档聊天已读"
                                )
                            },
                            onOpenConversation = ::openConversation
                        )
                    }
                }

                if (appSurface == AppSurface.EntryRecovery) {
                    SlideInOverlay(
                        onDismissed = {
                            wearEntryRecoveryState = null
                            appSurface = AppSurface.ConversationList
                        }
                    ) { dismissOverlay ->
                        WatchEntryRecoverySurface(
                            isRoundScreen = isRoundScreen,
                            recoveryState = wearEntryRecoveryState,
                            visibleConversationCount = visibleConversationListBase.size,
                            archivedConversationCount = archivedConversationList.size,
                            onNavigateBack = dismissOverlay,
                            onOpenConversationList = {
                                wearEntryRecoveryState = null
                                appSurface = AppSurface.ConversationList
                            },
                            onRefreshWearSurfaces = {
                                updateWearStateSnapshot()
                                wearStateStore.requestSurfaceUpdates()
                                trustState = "已刷新手表入口"
                                showActionConfirmation(
                                    title = "已刷新手表入口",
                                    detail = "Tile 和表盘入口已请求更新"
                                )
                            }
                        )
                    }
                }

                if (
                    appSurface == AppSurface.Chat ||
                    appSurface == AppSurface.ChatInfo ||
                    appSurface == AppSurface.MessageActions ||
                    appSurface == AppSurface.ReactionUsers ||
                    appSurface == AppSurface.VoicePlayback ||
                    appSurface == AppSurface.MessageSearch ||
                    appSurface == AppSurface.StarredMessages ||
                    appSurface == AppSurface.ForwardMessage ||
                    appSurface == AppSurface.SecurityCheck
                ) {
                    SlideInOverlay(
                        onDismissed = {
                            appSurface = AppSurface.ConversationList
                        }
                    ) { dismissOverlay ->
                        WatchChatSurface(
                            isRoundScreen = isRoundScreen,
                            conversation = selectedConversation,
                            transportMode = transportMode,
                            trustState = trustState,
                            fingerprint = localFingerprint,
                            pairingCode = pairingCode,
                            pendingPeer = pendingPeer,
                            trustedPeerCount = trustedPeers.size,
                            messages = messagesForConversation(selectedConversation.id),
                            pinnedMessage = pinnedMessage(selectedConversation.id),
                            wearEntryContext =
                                wearEntryContext
                                    ?.takeIf { context -> context.conversationId == selectedConversation.id },
                            starredMessageIds = starredMessageIds(selectedConversation.id),
                            draft = draftsByConversation[selectedConversation.id],
                            isBlocked = isConversationBlocked(selectedConversation),
                            isAnnouncementOnly = isAnnouncementOnly(selectedConversation.id),
                            onSelectMode = ::selectMode,
                            onConfirmPairing = ::confirmPairing,
                            onRejectPairing = ::rejectPairing,
                            onSendQuickReply = ::sendQuickReply,
                            onOpenCustomMessageInput = ::openCustomMessageInput,
                            onOpenDraftInput = {
                                openCustomMessageInput(saveAsDraft = true)
                            },
                            onSendDraft = {
                                sendDraft(selectedConversation)
                            },
                            onClearDraft = {
                                clearDraft(selectedConversation)
                            },
                            onToggleVoiceRecording = ::toggleVoiceRecording,
                            onCancelVoiceRecording = ::cancelVoiceRecording,
                            isRecordingVoice = isRecordingVoice,
                            recordingElapsedMillis = recordingElapsedMillis,
                            voicePlaybackSpeed = voicePlaybackSpeed,
                            onOpenChatInfo = {
                                appSurface = AppSurface.ChatInfo
                            },
                            onOpenMessageActions = { message ->
                                wearEntryContext = null
                                if (message.kind == ChatMessageKind.Voice && message.voiceAudioBytes != null && !message.canRetry()) {
                                    openVoicePlayback(message)
                                } else {
                                    playVoiceMessage(message)
                                }
                            },
                            onPerformWearEntryAction = { context ->
                                when (context.action) {
                                    WearEntryAction.Voice -> {
                                        wearEntryContext = null
                                        if (!isRecordingVoice) {
                                            toggleVoiceRecording()
                                        }
                                    }
                                    WearEntryAction.Reply,
                                    WearEntryAction.QuickReply -> {
                                        wearEntryContext = null
                                        openCustomMessageInput()
                                    }
                                    WearEntryAction.OpenChat -> {
                                        wearEntryContext = null
                                    }
                                }
                            },
                            onDismissWearEntryContext = {
                                wearEntryContext = null
                            },
                            onNavigateBack = dismissOverlay
                        )
                    }
                }

                if (appSurface == AppSurface.ChatInfo) {
                    SlideInOverlay(
                        onDismissed = {
                            appSurface = AppSurface.Chat
                        }
                    ) { dismissOverlay ->
                        WatchChatInfoSurface(
                            isRoundScreen = isRoundScreen,
                            conversation = selectedConversation,
                            trustedPeers = trustedPeers,
                            fingerprint = localFingerprint,
                            reachability =
                                if (selectedConversation.kind == ConversationKind.Direct) {
                                    selectedConversation.peerFingerprint
                                        ?.let { fingerprint -> peerReachabilityText(fingerprint) }
                                        ?: "等待发现"
                                } else {
                                    groupReachabilityText(selectedConversation.memberFingerprints)
                            },
                            messages = messagesForConversation(selectedConversation.id),
                            pinnedMessage = pinnedMessage(selectedConversation.id),
                            groupAbout =
                                if (selectedConversation.id == NEARBY_GROUP_CONVERSATION_ID) {
                                    nearbyGroupAbout
                                } else {
                                    null
                                },
                            isPinned = pinnedConversationIds[selectedConversation.id] == true,
                            isFavorite = favoriteConversationIds[selectedConversation.id] == true,
                            isMuted = isConversationMuted(selectedConversation.id),
                            muteStatus = muteStatusLabel(selectedConversation.id),
                            muteAction = muteActionLabel(selectedConversation.id),
                            isArchived = archivedConversationIds[selectedConversation.id] == true,
                            isBlocked = isConversationBlocked(selectedConversation),
                            isLocked = isConversationLocked(selectedConversation.id),
                            readReceiptsEnabled = areReadReceiptsEnabled(selectedConversation.id),
                            allowsFrequentlyForwarded = allowsFrequentlyForwarded(selectedConversation.id),
                            isAnnouncementOnly = isAnnouncementOnly(selectedConversation.id),
                            unreadCount = unreadCounts[selectedConversation.id] ?: 0,
                            starredCount = starredMessageIds(selectedConversation.id).size,
                            retryableCount =
                                messagesForConversation(selectedConversation.id)
                                    .count { message -> message.canRetry() },
                            disappearingMode =
                                disappearingModesByConversation[selectedConversation.id]
                                    ?: DisappearingMessageMode.Off,
                            onNavigateBack = dismissOverlay,
                            onOpenSecurityCheck = {
                                selectedSecurityPeerFingerprint = null
                                appSurface = AppSurface.SecurityCheck
                            },
                            onOpenGroupMembers = {
                                appSurface = AppSurface.GroupMembers
                            },
                            onEditGroupAbout =
                                if (selectedConversation.id == NEARBY_GROUP_CONVERSATION_ID) {
                                    {
                                        openGroupAboutInput(selectedConversation)
                                    }
                                } else {
                                    null
                                },
                            onOpenStarredMessages = {
                                appSurface = AppSurface.StarredMessages
                            },
                            onOpenContentMessages = {
                                appSurface = AppSurface.ChatContentMessages
                            },
                            onOpenPinnedMessage = { message ->
                                messageActionsBackStack.clear()
                                messageActionsReturnSurface = AppSurface.ChatInfo
                                selectedActionMessage = message
                                appSurface = AppSurface.MessageActions
                            },
                            onSearchMessages = {
                                appSurface = AppSurface.MessageSearch
                                openMessageSearchInput()
                            },
                            onTogglePinned = {
                                toggleConversationPinned(selectedConversation)
                            },
                            onToggleFavorite = {
                                toggleConversationFavorite(selectedConversation)
                            },
                            onEditAlias =
                                selectedConversation.peerFingerprint
                                    ?.let { fingerprint -> trustedPeer(fingerprint) }
                                    ?.let { peer ->
                                        {
                                            openAliasInput(peer)
                                        }
                                    },
                            onCycleTheme = {
                                cycleConversationTheme(selectedConversation)
                            },
                            onToggleMuted = {
                                appSurface = AppSurface.MuteSettings
                            },
                            onToggleArchived = {
                                val wasArchived = archivedConversationIds[selectedConversation.id] == true
                                toggleConversationArchived(selectedConversation)
                                if (!wasArchived) {
                                    appSurface = AppSurface.ConversationList
                                }
                            },
                            onToggleBlocked = {
                                toggleConversationBlocked(selectedConversation)
                            },
                            onToggleLocked = {
                                toggleConversationLocked(selectedConversation)
                            },
                            onToggleReadReceipts = {
                                toggleConversationReadReceipts(selectedConversation)
                            },
                            onToggleFrequentlyForwarded = {
                                toggleFrequentlyForwardedAllowed(selectedConversation)
                            },
                            onToggleAnnouncementOnly = {
                                toggleAnnouncementOnly(selectedConversation)
                            },
                            onToggleUnread = {
                                toggleConversationUnread(selectedConversation)
                            },
                            onRetryFailedMessages = {
                                retryConversationMessages(selectedConversation)
                            },
                            onToggleDisappearingMessages = {
                                appSurface = AppSurface.DisappearingSettings
                            },
                            onClearConversation = {
                                clearConversation(selectedConversation)
                                appSurface = AppSurface.Chat
                            },
                            onForgetPeer = {
                                forgetConversationPeer(selectedConversation)
                            }
                        )
                    }
                }

                if (appSurface == AppSurface.SecurityCheck) {
                    SlideInOverlay(
                        onDismissed = {
                            appSurface = AppSurface.ChatInfo
                        }
                    ) { dismissOverlay ->
                        WatchSecurityCheckSurface(
                            isRoundScreen = isRoundScreen,
                            conversation = selectedConversation,
                            trustedPeers = trustedPeers,
                            localFingerprint = localFingerprint,
                            selectedPeerFingerprint = selectedSecurityPeerFingerprint,
                            onCopySafetyCode = ::copySafetyCode,
                            onNavigateBack = dismissOverlay
                        )
                    }
                }

                if (appSurface == AppSurface.MuteSettings) {
                    SlideInOverlay(
                        onDismissed = {
                            appSurface = AppSurface.ChatInfo
                        }
                    ) { dismissOverlay ->
                        WatchMuteSettingsSurface(
                            isRoundScreen = isRoundScreen,
                            conversation = selectedConversation,
                            currentMute = muteState(selectedConversation.id),
                            onNavigateBack = dismissOverlay,
                            onSelectMutePreset = { preset ->
                                setConversationMute(selectedConversation, preset)
                                appSurface = AppSurface.ChatInfo
                            }
                        )
                    }
                }

                if (appSurface == AppSurface.DisappearingSettings) {
                    SlideInOverlay(
                        onDismissed = {
                            appSurface = AppSurface.ChatInfo
                        }
                    ) { dismissOverlay ->
                        WatchDisappearingSettingsSurface(
                            isRoundScreen = isRoundScreen,
                            conversation = selectedConversation,
                            currentMode =
                                disappearingModesByConversation[selectedConversation.id]
                                    ?: DisappearingMessageMode.Off,
                            onNavigateBack = dismissOverlay,
                            onSelectMode = { mode ->
                                setDisappearingMessages(selectedConversation, mode)
                                appSurface = AppSurface.ChatInfo
                            }
                        )
                    }
                }

                if (appSurface == AppSurface.GroupMembers) {
                    SlideInOverlay(
                        onDismissed = {
                            appSurface = AppSurface.ChatInfo
                        }
                    ) { dismissOverlay ->
                        WatchGroupMembersSurface(
                            isRoundScreen = isRoundScreen,
                            conversation = selectedConversation,
                            trustedPeers = trustedPeers,
                            peerReachability = ::peerReachabilityText,
                            onNavigateBack = dismissOverlay,
                            onOpenDirectChat = { peer ->
                                val directConversationId = ensureDirectConversation(peer)
                                conversationById(directConversationId)?.let { directConversation ->
                                    openConversation(directConversation)
                                }
                            },
                            onOpenSecurityCheck = { peer ->
                                selectedSecurityPeerFingerprint = peer.fingerprint
                                appSurface = AppSurface.SecurityCheck
                            }
                        )
                    }
                }

                if (appSurface == AppSurface.StarredMessages) {
                    SlideInOverlay(
                        onDismissed = {
                            appSurface = AppSurface.ChatInfo
                        }
                    ) { dismissOverlay ->
                        WatchStarredMessagesSurface(
                            isRoundScreen = isRoundScreen,
                            conversation = selectedConversation,
                            messages = starredMessages(selectedConversation.id),
                            starredMessageIds = starredMessageIds(selectedConversation.id),
                            onNavigateBack = dismissOverlay,
                            onOpenMessage = { message ->
                                messageActionsBackStack.clear()
                                messageActionsReturnSurface = AppSurface.StarredMessages
                                selectedActionMessage = message
                                appSurface = AppSurface.MessageActions
                            }
                        )
                    }
                }

                if (appSurface == AppSurface.ChatContentMessages) {
                    SlideInOverlay(
                        onDismissed = {
                            appSurface = AppSurface.ChatInfo
                        }
                    ) { dismissOverlay ->
                        WatchChatContentMessagesSurface(
                            isRoundScreen = isRoundScreen,
                            conversation = selectedConversation,
                            messages = contentMessages(messagesForConversation(selectedConversation.id)),
                            starredMessageIds = starredMessageIds(selectedConversation.id),
                            onNavigateBack = dismissOverlay,
                            onOpenMessage = { message ->
                                messageActionsBackStack.clear()
                                messageActionsReturnSurface = AppSurface.ChatContentMessages
                                selectedActionMessage = message
                                appSurface = AppSurface.MessageActions
                            }
                        )
                    }
                }

                if (appSurface == AppSurface.MessageSearch) {
                    SlideInOverlay(
                        onDismissed = {
                            appSurface = AppSurface.ChatInfo
                        }
                    ) { dismissOverlay ->
                        WatchMessageSearchSurface(
                            isRoundScreen = isRoundScreen,
                            conversation = selectedConversation,
                            query = searchQuery,
                            results = searchMessages(selectedConversation.id, searchQuery),
                            starredMessageIds = starredMessageIds(selectedConversation.id),
                            onNavigateBack = dismissOverlay,
                            onSearchAgain = ::openMessageSearchInput,
                            onOpenMessage = { message ->
                                messageActionsBackStack.clear()
                                messageActionsReturnSurface = AppSurface.MessageSearch
                                selectedActionMessage = message
                                appSurface = AppSurface.MessageActions
                            }
                        )
                    }
                }

                if (appSurface == AppSurface.MessageActions) {
                    val actionMessage =
                        selectedActionMessage
                            ?: messagesForConversation(selectedConversation.id)
                                .lastOrNull { message -> message.deliveryState != DeliveryState.System }
                    if (actionMessage != null) {
                        val quotedTarget =
                            quotedMessageTarget(selectedConversation.id, actionMessage.quotedMessage)
                                ?.takeUnless { targetMessage ->
                                    targetMessage.stableStarId() == actionMessage.stableStarId()
                                }
                        SlideInOverlay(
                            onDismissed = {
                                val previousMessage = messageActionsBackStack.removeLastOrNull()
                                if (previousMessage == null) {
                                    selectedActionMessage = null
                                    appSurface = messageActionsReturnSurface
                                } else {
                                    selectedActionMessage = previousMessage
                                }
                            },
                            animationKey = actionMessage.stableStarId()
                        ) { dismissOverlay ->
                            WatchMessageActionsSurface(
                                isRoundScreen = isRoundScreen,
                                conversation = selectedConversation,
                                message = actionMessage,
                                isStarred = isMessageStarred(selectedConversation.id, actionMessage),
                                isPinned = isMessagePinned(selectedConversation.id, actionMessage),
                                hasLocalReaction = hasLocalReaction(actionMessage),
                                voicePlaybackSpeed = voicePlaybackSpeed,
                                hasQuotedMessageTarget = quotedTarget != null,
                                canReplyPrivately =
                                    selectedConversation.kind == ConversationKind.Group &&
                                        !actionMessage.mine &&
                                        actionMessage.senderFingerprint?.let(::trustedPeer) != null,
                                receiptSummary = messageReceiptSummary(selectedConversation, actionMessage),
                                reactionDetails = reactionDetails(actionMessage),
                                onNavigateBack = dismissOverlay,
                                onPlayVoiceMessage = {
                                    voicePlaybackReturnSurface = AppSurface.MessageActions
                                    appSurface = AppSurface.VoicePlayback
                                },
                                onCycleVoicePlaybackSpeed = {
                                    val nextSpeed = voicePlaybackSpeed.next()
                                    voicePlaybackSpeed = nextSpeed
                                    trustState = "语音倍速 ${nextSpeed.label}"
                                },
                                onToggleStarred = {
                                    toggleMessageStarred(selectedConversation, actionMessage)
                                },
                                onTogglePinned = {
                                    toggleMessagePinned(selectedConversation, actionMessage)
                                },
                                onCopyMessage = {
                                    copyMessageText(actionMessage)
                                },
                                onCopyMessageInfo = {
                                    copyMessageInfo(selectedConversation, actionMessage)
                                },
                                onViewInChat = {
                                    selectedActionMessage = null
                                    activeConversationId = selectedConversation.id
                                    clearConversationAlerts(selectedConversation.id)
                                    markConversationRead(selectedConversation.id)
                                    appSurface = AppSurface.Chat
                                },
                                onOpenQuotedMessage = {
                                    quotedTarget?.let { targetMessage ->
                                        messageActionsBackStack.add(actionMessage)
                                        selectedActionMessage = targetMessage
                                    }
                                },
                                onDeleteMessage = {
                                    deleteMessageForMe(selectedConversation, actionMessage)
                                },
                                onReactToMessage = { reactionCode ->
                                    sendReactionToConversation(selectedConversation, actionMessage, reactionCode)
                                    appSurface = AppSurface.Chat
                                },
                                onRemoveReaction = {
                                    removeLocalReactionFromMessage(selectedConversation, actionMessage)
                                },
                                onOpenReactionUsers = {
                                    appSurface = AppSurface.ReactionUsers
                                },
                                onOpenCustomMessageInput = {
                                    pendingQuotedMessage = null
                                    appSurface = AppSurface.Chat
                                    openCustomMessageInput()
                                },
                                onSendQuickReply = { reply ->
                                    pendingQuotedMessage = actionMessage.toQuotedMessage(selectedConversation)
                                    appSurface = AppSurface.Chat
                                    sendQuickReply(reply)
                                },
                                onReplyToMessage = {
                                    pendingQuotedMessage = actionMessage.toQuotedMessage(selectedConversation)
                                    appSurface = AppSurface.Chat
                                    openCustomMessageInput()
                                },
                                onReplyPrivately = {
                                    if (prepareDirectReplyToSender(selectedConversation, actionMessage)) {
                                        openCustomMessageInput()
                                    }
                                },
                                onForwardMessage = {
                                    pendingForwardMessage = actionMessage
                                    appSurface = AppSurface.ForwardMessage
                                },
                                onEditRetryMessage = {
                                    if (prepareEditRetryMessage(selectedConversation, actionMessage)) {
                                        appSurface = AppSurface.Chat
                                        openCustomMessageInput()
                                    }
                                },
                                onRetryMessage = {
                                    appSurface = AppSurface.Chat
                                    retryMessage(selectedConversation, actionMessage)
                                }
                            )
                        }
                    }
                }

                if (appSurface == AppSurface.VoicePlayback) {
                    val actionMessage = selectedActionMessage
                    if (actionMessage != null) {
                        SlideInOverlay(
                            onDismissed = {
                                appSurface = voicePlaybackReturnSurface
                            },
                            animationKey = "voice:${actionMessage.stableStarId()}"
                        ) { dismissOverlay ->
                            WatchVoicePlaybackSurface(
                                isRoundScreen = isRoundScreen,
                                conversation = selectedConversation,
                                message = actionMessage,
                                voicePlaybackSpeed = voicePlaybackSpeed,
                                onNavigateBack = dismissOverlay,
                                onPlayVoiceMessage = {
                                    playVoiceMessage(actionMessage)
                                },
                                onCycleVoicePlaybackSpeed = {
                                    val nextSpeed = voicePlaybackSpeed.next()
                                    voicePlaybackSpeed = nextSpeed
                                    trustState = "语音倍速 ${nextSpeed.label}"
                                },
                                onCopyMessageInfo = {
                                    copyMessageInfo(selectedConversation, actionMessage)
                                },
                                onViewInChat = {
                                    selectedActionMessage = null
                                    activeConversationId = selectedConversation.id
                                    clearConversationAlerts(selectedConversation.id)
                                    markConversationRead(selectedConversation.id)
                                    appSurface = AppSurface.Chat
                                },
                                onOpenMessageActions = {
                                    appSurface = AppSurface.MessageActions
                                }
                            )
                        }
                    }
                }

                if (appSurface == AppSurface.ReactionUsers) {
                    val actionMessage = selectedActionMessage
                    if (actionMessage != null) {
                        SlideInOverlay(
                            onDismissed = {
                                appSurface = AppSurface.MessageActions
                            },
                            animationKey = "reaction-users:${actionMessage.stableStarId()}"
                        ) { dismissOverlay ->
                            WatchReactionUsersSurface(
                                isRoundScreen = isRoundScreen,
                                conversation = selectedConversation,
                                message = actionMessage,
                                reactionDetails = reactionDetails(actionMessage),
                                onNavigateBack = dismissOverlay
                            )
                        }
                    }
                }

                if (appSurface == AppSurface.ForwardMessage) {
                    val forwardMessage = pendingForwardMessage
                    if (forwardMessage != null) {
                        SlideInOverlay(
                            onDismissed = {
                                appSurface = AppSurface.MessageActions
                            }
                        ) { dismissOverlay ->
                            WatchForwardMessageSurface(
                                isRoundScreen = isRoundScreen,
                                conversations = currentConversations,
                                sourceConversation = selectedConversation,
                                message = forwardMessage,
                                messagesByConversation = conversationMessages,
                                unreadCounts = unreadCounts,
                                pinnedConversationIds = pinnedConversationIds,
                                isConversationMuted = ::isConversationMuted,
                                allowsFrequentlyForwarded = ::allowsFrequentlyForwarded,
                                onNavigateBack = dismissOverlay,
                                onSelectConversation = { targetConversation ->
                                    forwardMessageToConversation(targetConversation, forwardMessage)
                                }
                            )
                        }
                    }
                }

                if (appSurface == AppSurface.Profile) {
                    SlideInOverlay(
                        onDismissed = {
                            appSurface = AppSurface.ConversationList
                        }
                    ) { dismissOverlay ->
                        WatchProfileSurface(
                            isRoundScreen = isRoundScreen,
                            profile = profile,
                            avatars = defaultAvatars,
                            fingerprint = localFingerprint,
                            transportMode = transportMode,
                            trustState = trustState,
                            trustedPeers = trustedPeers,
                            blockedPeerFingerprints = blockedPeerFingerprints.toMap(),
                            defaultDisappearingMode = profile.defaultDisappearingMode.toDisappearingMode(),
                            defaultReadReceiptsEnabled = profile.defaultReadReceiptsEnabled,
                            securityChangeAlertsEnabled = profile.securityChangeAlertsEnabled,
                            onNavigateBack = dismissOverlay,
                            onDefaultDisappearingModeChange = { mode ->
                                updateProfile(profile.copy(defaultDisappearingMode = mode.profileKey))
                                trustState = "默认限时消息${mode.label}"
                            },
                            onToggleDefaultReadReceipts = {
                                val enabled = !profile.defaultReadReceiptsEnabled
                                updateProfile(profile.copy(defaultReadReceiptsEnabled = enabled))
                                trustState = if (enabled) "默认已读回执开启" else "默认已读回执关闭"
                            },
                            onToggleSecurityChangeAlerts = {
                                val enabled = !profile.securityChangeAlertsEnabled
                                updateProfile(profile.copy(securityChangeAlertsEnabled = enabled))
                                trustState = if (enabled) "安全变化提醒开启" else "安全变化提醒关闭"
                            },
                            onProfileChange = ::updateProfile
                        )
                    }
                }

                actionConfirmation?.let { confirmation ->
                    LaunchedEffect(confirmation.id) {
                        delay(ACTION_CONFIRMATION_VISIBLE_MS)
                        if (actionConfirmation?.id == confirmation.id) {
                            actionConfirmation = null
                        }
                    }
                    WatchActionConfirmationBanner(
                        confirmation = confirmation,
                        isRoundScreen = isRoundScreen,
                        onDismiss = {
                            if (actionConfirmation?.id == confirmation.id) {
                                actionConfirmation = null
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchActionConfirmationBanner(
    confirmation: WatchActionConfirmation,
    isRoundScreen: Boolean,
    onDismiss: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val compact = maxWidth < 260.dp || maxHeight < 260.dp
        val bottomPadding =
            when {
                isRoundScreen && compact -> 20.dp
                isRoundScreen -> 24.dp
                else -> 12.dp
            }
        val bannerWidthFraction =
            when {
                isRoundScreen && compact -> 0.82f
                isRoundScreen -> 0.78f
                else -> 0.9f
            }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomPadding),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth(bannerWidthFraction)
                        .clip(RoundedCornerShape(8.dp))
                        .background(chatGreen.copy(alpha = 0.94f))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable(onClick = onDismiss)
                        .padding(
                            horizontal = if (compact) 9.dp else 11.dp,
                            vertical = if (compact) 7.dp else 8.dp
                        ),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.DoneAll,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(if (compact) 16.dp else 18.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = confirmation.title,
                        color = Color.White,
                        fontSize = if (compact) 11.sp else 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = confirmation.detail,
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = if (compact) 9.sp else 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SlideInOverlay(
    onDismissed: () -> Unit,
    animationKey: Any? = Unit,
    content: @Composable (dismissOverlay: () -> Unit) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx().coerceAtLeast(1f) }
        val scope = rememberCoroutineScope()
        var offsetX by remember(widthPx) { mutableStateOf(widthPx) }
        var slideJob by remember { mutableStateOf<Job?>(null) }

        fun animateProfileTo(targetOffset: Float, onFinished: (() -> Unit)? = null) {
            slideJob?.cancel()
            val target = targetOffset.coerceIn(0f, widthPx)
            val start = offsetX.coerceIn(0f, widthPx)
            slideJob =
                scope.launch {
                    animate(
                        initialValue = start,
                        targetValue = target,
                        animationSpec =
                            tween(
                                durationMillis = if (target == 0f) 220 else 180,
                                easing = FastOutSlowInEasing
                            )
                    ) { value, _ ->
                        offsetX = value
                    }
                    onFinished?.invoke()
                }
        }

        LaunchedEffect(widthPx, animationKey) {
            offsetX = widthPx
            animateProfileTo(0f)
        }

        BackHandler {
            animateProfileTo(widthPx, onDismissed)
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .pointerInput(widthPx) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                slideJob?.cancel()
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                offsetX = (offsetX + dragAmount).coerceIn(0f, widthPx)
                            },
                            onDragEnd = {
                                val shouldDismiss = offsetX > widthPx * 0.28f
                                if (shouldDismiss) {
                                    animateProfileTo(widthPx, onDismissed)
                                } else {
                                    animateProfileTo(0f)
                                }
                            },
                            onDragCancel = {
                                animateProfileTo(0f)
                            }
                        )
                    }
        ) {
            content {
                animateProfileTo(widthPx, onDismissed)
            }
        }
    }
}

@Composable
private fun WatchConversationListSurface(
    isRoundScreen: Boolean,
    profile: ProfileSettings,
    conversations: List<ChatConversation>,
    activeFilter: ChatListFilter,
    filterCounts: Map<ChatListFilter, Int>,
    mentionCounts: Map<String, Int>,
    archivedCount: Int,
    archivedUnreadCount: Int,
    unreadCounts: Map<String, Int>,
    pinnedConversationIds: Map<String, Boolean>,
    isConversationMuted: (String) -> Boolean,
    messagesByConversation: Map<String, List<ChatBubble>>,
    draftsByConversation: Map<String, ConversationDraft>,
    lockedConversationIds: Map<String, Boolean>,
    transportMode: TransportMode,
    lanDiscoveryEnabled: Boolean,
    trustState: String,
    fingerprint: String,
    pairingCode: String?,
    pendingPeer: TrustedPeer?,
    trustedPeerCount: Int,
    onSelectMode: (TransportMode) -> Unit,
    onConfirmPairing: () -> Unit,
    onRejectPairing: () -> Unit,
    onOpenConversation: (ChatConversation) -> Unit,
    onToggleConversationPinned: (ChatConversation) -> Unit,
    onToggleConversationMuted: (ChatConversation) -> Unit,
    onToggleConversationArchived: (ChatConversation) -> Unit,
    onOpenArchivedChats: () -> Unit,
    onSelectFilter: (ChatListFilter) -> Unit,
    onOpenProfile: () -> Unit,
    profileNavigationEnabled: Boolean = true
) {
    val swipeThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    val profileSwipeModifier =
        if (profileNavigationEnabled) {
            Modifier.pointerInput(swipeThreshold) {
                var dragAmount = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, delta ->
                        dragAmount += delta
                    },
                    onDragEnd = {
                        if (dragAmount < -swipeThreshold) {
                            onOpenProfile()
                        }
                        dragAmount = 0f
                    },
                    onDragCancel = {
                        dragAmount = 0f
                    }
                )
            }
        } else {
            Modifier
        }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .then(profileSwipeModifier)
    ) {
        val compact = maxWidth < 260.dp || maxHeight < 260.dp
        val surfaceSpec = WatchSurfaceSpec(isRound = isRoundScreen, compact = compact)
        val listScrollState = rememberScrollState()
        var expandedConversationActionsId by remember { mutableStateOf<String?>(null) }

        WatchFrame(
            surfaceSpec = surfaceSpec,
            accent = chatBlue,
            modifier = Modifier.padding(horizontal = surfaceSpec.chatHorizontalPadding)
        ) {
            ScreenScaffold(
                scrollState = listScrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(),
                scrollIndicator = {}
            ) { scaffoldPadding ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(listScrollState)
                            .padding(scaffoldPadding)
                            .padding(
                                top = if (surfaceSpec.isRound) {
                                    if (compact) 20.dp else 24.dp
                                } else {
                                    12.dp
                                },
                                bottom = surfaceSpec.conversationBottomPadding
                            ),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HomeBeaconHeader(
                        profile = profile,
                        transportMode = transportMode,
                        trustState = trustState,
                        surfaceSpec = surfaceSpec,
                        onOpenProfile = if (profileNavigationEnabled) onOpenProfile else null
                    )

                    TransportOrbit(
                        transportMode = transportMode,
                        lanDiscoveryEnabled = lanDiscoveryEnabled,
                        surfaceSpec = surfaceSpec,
                        onSelectMode = onSelectMode
                    )

                    SecurityStrip(
                        fingerprint = fingerprint,
                        pairingCode = pairingCode,
                        trustedPeerCount = trustedPeerCount,
                        surfaceSpec = surfaceSpec
                    )

                    if (pendingPeer != null) {
                        PairingPrompt(
                            peer = pendingPeer,
                            surfaceSpec = surfaceSpec,
                            onConfirmPairing = onConfirmPairing,
                            onRejectPairing = onRejectPairing
                        )
                    }

                    ChatListFilterStrip(
                        activeFilter = activeFilter,
                        filterCounts = filterCounts,
                        compact = compact,
                        surfaceSpec = surfaceSpec,
                        onSelectFilter = onSelectFilter
                    )

                    if (conversations.isEmpty()) {
                        EmptyFilterCapsule(
                            activeFilter = activeFilter,
                            compact = compact,
                            surfaceSpec = surfaceSpec
                        )
                    }

                    if (archivedCount > 0) {
                        ArchivedChatsCapsule(
                            archivedCount = archivedCount,
                            unreadCount = archivedUnreadCount,
                            compact = compact,
                            surfaceSpec = surfaceSpec,
                            onClick = onOpenArchivedChats
                        )
                    }

                    conversations.forEach { conversation ->
                        val conversationMessages = messagesByConversation[conversation.id].orEmpty()
                        val lastMessage =
                            conversationMessages
                                .lastOrNull { message -> message.deliveryState != DeliveryState.System }
                        val quickActionsExpanded = expandedConversationActionsId == conversation.id
                        ConversationCapsule(
                            conversation = conversation,
                            lastMessage = lastMessage,
                            unreadCount = unreadCounts[conversation.id] ?: 0,
                            mentionCount = mentionCounts[conversation.id] ?: 0,
                            retryableCount = conversationMessages.count { message -> message.canRetry() },
                            draft = draftsByConversation[conversation.id],
                            locked = lockedConversationIds[conversation.id] == true,
                            isPinned = pinnedConversationIds[conversation.id] == true,
                            isMuted = isConversationMuted(conversation.id),
                            featured = conversation.id == NEARBY_GROUP_CONVERSATION_ID,
                            surfaceSpec = surfaceSpec,
                            onClick = {
                                expandedConversationActionsId = null
                                onOpenConversation(conversation)
                            },
                            onLongPress = {
                                expandedConversationActionsId =
                                    if (quickActionsExpanded) {
                                        null
                                    } else {
                                        conversation.id
                                    }
                            }
                        )
                        if (quickActionsExpanded) {
                            ConversationQuickActions(
                                conversation = conversation,
                                isPinned = pinnedConversationIds[conversation.id] == true,
                                isMuted = isConversationMuted(conversation.id),
                                compact = compact,
                                surfaceSpec = surfaceSpec,
                                onTogglePinned = {
                                    onToggleConversationPinned(conversation)
                                    expandedConversationActionsId = null
                                },
                                onToggleMuted = {
                                    onToggleConversationMuted(conversation)
                                    expandedConversationActionsId = null
                                },
                                onToggleArchived = {
                                    onToggleConversationArchived(conversation)
                                    expandedConversationActionsId = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchFrame(
    surfaceSpec: WatchSurfaceSpec,
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(surfaceSpec.screenShape)
                .background(
                    Brush.linearGradient(
                        colors =
                            listOf(
                                chatSurfaceHigh.copy(alpha = 0.98f),
                                chatSurface,
                                chatWallpaper
                            )
                    )
                )
                .then(modifier)
    ) {
        AmbientDial(accent = accent, surfaceSpec = surfaceSpec)
        content()
    }
}

@Composable
private fun AmbientDial(
    accent: Color,
    surfaceSpec: WatchSurfaceSpec
) {
    val compact = surfaceSpec.compact
    Box(modifier = Modifier.fillMaxSize()) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = accent.copy(alpha = 0.055f),
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-14).dp, y = if (compact) 48.dp else 58.dp)
                    .size(if (compact) 30.dp else 38.dp)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.035f),
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 16.dp, y = if (compact) (-28).dp else (-34).dp)
                    .size(if (compact) 28.dp else 36.dp)
        )
    }
}

@Composable
private fun HomeBeaconHeader(
    profile: ProfileSettings,
    transportMode: TransportMode,
    trustState: String,
    surfaceSpec: WatchSurfaceSpec,
    onOpenProfile: (() -> Unit)?
) {
    val compact = surfaceSpec.compact
    val displayName = profile.displayName.ifBlank { "SpotChat" }
    Row(
        modifier =
            Modifier
                .fillMaxWidth(if (surfaceSpec.isRound) 0.84f else 0.94f)
                .height(if (compact) 52.dp else 58.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.86f))
                .border(1.dp, chatDivider.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                .clickable(enabled = onOpenProfile != null) {
                    onOpenProfile?.invoke()
                }
                .padding(horizontal = if (compact) 9.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarBubble(
            avatar = avatarFor(profile.avatarId),
            displayName = displayName,
            size = if (compact) 34.dp else 38.dp,
            textSize = if (compact) 15.sp else 16.sp,
            selected = false,
            onClick = null
        )
        Spacer(modifier = Modifier.width(if (compact) 8.dp else 10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "SpotChat",
                color = chatRowMuted,
                fontSize = if (compact) 9.sp else 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = displayName,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = if (compact) 15.sp else 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        StatusPill(
            text = "${transportMode.label} · $trustState",
            trustState = trustState,
            compact = compact
        )
    }
}

@Composable
private fun StatusPill(
    text: String,
    trustState: String,
    compact: Boolean
) {
    Row(
        modifier =
            Modifier
                .height(if (compact) 22.dp else 24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(statusColor(trustState).copy(alpha = 0.15f))
                .padding(horizontal = if (compact) 6.dp else 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(trustState = trustState, size = if (compact) 5.dp else 6.dp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = if (compact) 8.sp else 9.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatusDot(
    trustState: String,
    size: Dp
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(statusColor(trustState))
    )
}

@Composable
private fun TransportOrbit(
    transportMode: TransportMode,
    lanDiscoveryEnabled: Boolean,
    surfaceSpec: WatchSurfaceSpec,
    onSelectMode: (TransportMode) -> Unit
) {
    val compact = surfaceSpec.compact
    Row(
        modifier =
            Modifier
                .fillMaxWidth(if (surfaceSpec.isRound) 0.84f else 0.94f)
                .height(if (compact) 32.dp else 36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.2f))
                .border(1.dp, chatDivider.copy(alpha = 0.58f), RoundedCornerShape(8.dp))
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TransportMode.entries
            .filterNot { mode -> mode == TransportMode.Relay }
            .forEach { mode ->
                OrbitButton(
                    mode = mode,
                    selected =
                        transportMode == mode &&
                            (mode != TransportMode.Lan || lanDiscoveryEnabled),
                    compact = compact,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelectMode(mode) }
                )
            }
    }
}

@Composable
private fun OrbitButton(
    mode: TransportMode,
    selected: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val background = if (selected) chatBlue.copy(alpha = 0.24f) else Color.Transparent
    val foreground = if (selected) Color.White else chatRowMuted
    Row(
        modifier =
            modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(7.dp))
                .background(background)
                .clickable(onClick = onClick)
                .padding(horizontal = if (compact) 4.dp else 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = mode.icon,
            contentDescription = mode.label,
            tint = foreground,
            modifier = Modifier.size(if (compact) 13.dp else 15.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = mode.label,
            color = foreground,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SecurityStrip(
    fingerprint: String,
    pairingCode: String?,
    trustedPeerCount: Int,
    surfaceSpec: WatchSurfaceSpec
) {
    val compact = surfaceSpec.compact
    val displayFingerprint = SpotChatCrypto.displayFingerprint(fingerprint)
    Row(
        modifier =
            Modifier
                .fillMaxWidth(if (surfaceSpec.isRound) 0.84f else 0.94f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.055f))
                .border(1.dp, chatGreen.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 6.dp else 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = "端到端加密",
            tint = chatGreen,
            modifier = Modifier.size(if (compact) 11.dp else 13.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text =
                pairingCode?.let { "校验 $it" }
                    ?: "$displayFingerprint · 可信 $trustedPeerCount",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = if (compact) 9.sp else 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PairingPrompt(
    peer: TrustedPeer,
    surfaceSpec: WatchSurfaceSpec,
    onConfirmPairing: () -> Unit,
    onRejectPairing: () -> Unit
) {
    val compact = surfaceSpec.compact
    Column(
        modifier =
            Modifier
                .fillMaxWidth(if (surfaceSpec.isRound) 0.84f else 0.94f)
                .clip(RoundedCornerShape(8.dp))
                .background(chatAmber.copy(alpha = 0.12f))
                .border(1.dp, chatAmber.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = if (compact) 8.dp else 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = peer.deviceName,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionPill(
                text = "拒绝",
                selected = false,
                modifier = Modifier.weight(1f),
                onClick = onRejectPairing
            )
            ActionPill(
                text = "信任",
                selected = true,
                modifier = Modifier.weight(1f),
                onClick = onConfirmPairing
            )
        }
    }
}

@Composable
private fun ActionPill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val background =
        if (selected) {
            chatGreen
        } else {
            Color.White.copy(alpha = 0.08f)
        }
    val foreground =
        if (selected) {
            Color(0xFF001F1B)
        } else {
            MaterialTheme.colorScheme.onBackground
        }
    Box(
        modifier =
            modifier
                .height(if (selected) 30.dp else 28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(background)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = foreground,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ChatListFilterStrip(
    activeFilter: ChatListFilter,
    filterCounts: Map<ChatListFilter, Int>,
    compact: Boolean,
    surfaceSpec: WatchSurfaceSpec,
    onSelectFilter: (ChatListFilter) -> Unit
) {
    val filters =
        remember(activeFilter, filterCounts) {
            visibleChatListFilters(
                activeFilter = activeFilter,
                filterCounts = filterCounts
            )
        }
    val filterScrollState = rememberScrollState()
    val activeIndex = filters.indexOf(activeFilter).coerceAtLeast(0)
    val density = LocalDensity.current
    val chipStepPx =
        with(density) {
            ((if (compact) 61.dp else 68.dp) * activeIndex).roundToPx()
        }

    LaunchedEffect(activeFilter, filters, compact) {
        filterScrollState.animateScrollTo(chipStepPx.coerceAtMost(filterScrollState.maxValue))
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth(if (surfaceSpec.isRound) 0.84f else 0.94f)
                .height(if (compact) 30.dp else 34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.18f))
                .border(1.dp, chatDivider.copy(alpha = 0.52f), RoundedCornerShape(8.dp))
                .horizontalScroll(filterScrollState)
                .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        filters.forEach { filter ->
            val selected = filter == activeFilter
            val count = filterCounts.getOrDefault(filter, 0)
            val countLabel = if (count > 99) "99+" else count.toString()
            Box(
                modifier =
                    Modifier
                        .width(if (compact) 58.dp else 65.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (selected) chatGreen else Color.Transparent)
                        .clickable { onSelectFilter(filter) }
                        .padding(horizontal = if (compact) 2.dp else 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${filter.label} $countLabel",
                    color = if (selected) Color(0xFF001F1B) else chatRowMuted,
                    fontSize = if (compact) 8.sp else 9.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun EmptyFilterCapsule(
    activeFilter: ChatListFilter,
    compact: Boolean,
    surfaceSpec: WatchSurfaceSpec
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f)
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.72f))
                .border(1.dp, chatDivider.copy(alpha = 0.48f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = if (compact) 8.dp else 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text =
                if (activeFilter == ChatListFilter.All) {
                    "还没有聊天"
                } else {
                    "没有符合“${activeFilter.label}”的聊天"
                },
            color = chatRowMuted,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ArchivedChatsCapsule(
    archivedCount: Int,
    unreadCount: Int,
    compact: Boolean,
    surfaceSpec: WatchSurfaceSpec,
    onClick: () -> Unit
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f)
                .height(if (compact) 42.dp else 46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.72f))
                .border(1.dp, chatDivider.copy(alpha = 0.52f), RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = if (compact) 9.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier =
                Modifier
                    .size(if (compact) 24.dp else 28.dp)
                    .clip(CircleShape)
                    .background(chatAmber.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Archive,
                contentDescription = "已归档",
                tint = chatAmber,
                modifier = Modifier.size(if (compact) 13.dp else 15.dp)
            )
        }
        Spacer(modifier = Modifier.width(if (compact) 8.dp else 10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "已归档",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = if (compact) 12.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text =
                    if (unreadCount > 0) {
                        "$archivedCount 个聊天 · $unreadCount 个未读"
                    } else {
                        "$archivedCount 个聊天"
                    },
                color = if (unreadCount > 0) Color.White else chatRowMuted,
                fontSize = if (compact) 9.sp else 10.sp,
                fontWeight = if (unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (unreadCount > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            UnreadBadge(count = unreadCount)
        }
    }
}

@Composable
private fun ConversationCapsule(
    conversation: ChatConversation,
    lastMessage: ChatBubble?,
    unreadCount: Int,
    mentionCount: Int = 0,
    retryableCount: Int,
    draft: ConversationDraft? = null,
    locked: Boolean = false,
    isPinned: Boolean,
    isMuted: Boolean,
    featured: Boolean,
    surfaceSpec: WatchSurfaceSpec,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    val compact = surfaceSpec.compact
    val accent = conversationAccentColor(conversation)
    val preview = conversationPreview(conversation, lastMessage, retryableCount, draft, locked)
    val width =
        if (surfaceSpec.isRound) {
            if (featured) 0.86f else 0.82f
        } else {
            0.94f
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth(width)
                .height(if (featured) if (compact) 66.dp else 72.dp else if (compact) 54.dp else 58.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (featured) {
                        Brush.linearGradient(
                            listOf(
                                accent.copy(alpha = 0.2f),
                                chatSurfaceHigh.copy(alpha = 0.86f)
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(
                                chatSurfaceHigh.copy(alpha = 0.84f),
                                Color.White.copy(alpha = 0.035f)
                            )
                        )
                    }
                )
                .border(
                    width = 1.dp,
                    color = if (featured) accent.copy(alpha = 0.28f) else chatDivider.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(8.dp)
                )
                .then(
                    if (onLongPress != null) {
                        Modifier.pointerInput(onClick, onLongPress) {
                            detectTapGestures(
                                onTap = { onClick() },
                                onLongPress = { onLongPress() }
                            )
                        }
                    } else {
                        Modifier.clickable(onClick = onClick)
                    }
                )
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 7.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConversationAvatar(
            conversation = conversation,
            size = if (featured) if (compact) 34.dp else 38.dp else if (compact) 30.dp else 34.dp,
            accent = accent
        )
        Spacer(modifier = Modifier.width(if (compact) 8.dp else 10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conversation.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = if (featured) if (compact) 14.sp else 16.sp else if (compact) 13.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isPinned) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = "已置顶",
                        tint = accent,
                        modifier =
                            Modifier
                                .padding(start = 4.dp)
                                .size(if (compact) 10.dp else 12.dp)
                    )
                }
                if (isMuted) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsOff,
                        contentDescription = "已静音",
                        tint = chatRowMuted,
                        modifier =
                            Modifier
                                .padding(start = 4.dp)
                                .size(if (compact) 10.dp else 12.dp)
                    )
                }
                Text(
                    text = lastMessage?.timestamp ?: conversation.kind.label,
                    color = chatRowMuted,
                    fontSize = if (compact) 8.sp else 9.sp,
                    maxLines = 1
                )
            }
            Text(
                text = preview,
                color =
                    if (retryableCount > 0 || unreadCount > 0 || mentionCount > 0) {
                        Color.White
                    } else {
                        chatRowMuted
                    },
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight =
                    if (retryableCount > 0 || unreadCount > 0 || mentionCount > 0) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    },
                maxLines = if (featured) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (retryableCount > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            RetryBadge(count = retryableCount)
        } else if (mentionCount > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            MentionBadge()
        } else if (unreadCount > 0) {
            Spacer(modifier = Modifier.width(6.dp))
            UnreadBadge(count = unreadCount)
        }
    }
}

@Composable
private fun ConversationQuickActions(
    conversation: ChatConversation,
    isPinned: Boolean,
    isMuted: Boolean,
    compact: Boolean,
    surfaceSpec: WatchSurfaceSpec,
    onTogglePinned: () -> Unit,
    onToggleMuted: () -> Unit,
    onToggleArchived: () -> Unit
) {
    val accent = conversationAccentColor(conversation)
    Row(
        modifier =
            Modifier
                .fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f)
                .height(if (compact) 30.dp else 34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.78f))
                .border(1.dp, accent.copy(alpha = 0.36f), RoundedCornerShape(8.dp))
                .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactConversationActionButton(
            icon = Icons.Filled.PushPin,
            label = if (isPinned) "取消" else "置顶",
            selected = isPinned,
            compact = compact,
            modifier = Modifier.weight(1f),
            onClick = onTogglePinned
        )
        CompactConversationActionButton(
            icon = Icons.Filled.NotificationsOff,
            label = if (isMuted) "恢复" else "静音",
            selected = isMuted,
            compact = compact,
            modifier = Modifier.weight(1f),
            onClick = onToggleMuted
        )
        CompactConversationActionButton(
            icon = Icons.Filled.Archive,
            label = "归档",
            selected = false,
            compact = compact,
            modifier = Modifier.weight(1f),
            onClick = onToggleArchived
        )
    }
}

@Composable
private fun CompactConversationActionButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val foreground = if (selected) Color(0xFF001F1B) else MaterialTheme.colorScheme.onBackground
    Row(
        modifier =
            modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(7.dp))
                .background(if (selected) chatGreen else Color.Black.copy(alpha = 0.18f))
                .clickable(onClick = onClick)
                .padding(horizontal = if (compact) 3.dp else 5.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = foreground,
            modifier = Modifier.size(if (compact) 11.dp else 13.dp)
        )
        Spacer(modifier = Modifier.width(if (compact) 3.dp else 4.dp))
        Text(
            text = label,
            color = foreground,
            fontSize = if (compact) 9.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WatchForwardMessageSurface(
    isRoundScreen: Boolean,
    conversations: List<ChatConversation>,
    sourceConversation: ChatConversation,
    message: ChatBubble,
    messagesByConversation: Map<String, List<ChatBubble>>,
    unreadCounts: Map<String, Int>,
    pinnedConversationIds: Map<String, Boolean>,
    isConversationMuted: (String) -> Boolean,
    allowsFrequentlyForwarded: (String) -> Boolean,
    onNavigateBack: () -> Unit,
    onSelectConversation: (ChatConversation) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val compact = maxWidth < 260.dp || maxHeight < 260.dp
        val surfaceSpec = WatchSurfaceSpec(isRound = isRoundScreen, compact = compact)
        val accent = conversationAccentColor(sourceConversation)
        val scrollState = rememberScrollState()

        WatchFrame(
            surfaceSpec = surfaceSpec,
            accent = accent,
            modifier = Modifier.padding(horizontal = surfaceSpec.chatHorizontalPadding)
        ) {
            ScreenScaffold(
                scrollState = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(),
                scrollIndicator = {}
            ) { scaffoldPadding ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(scaffoldPadding)
                            .padding(
                                top = surfaceSpec.chatTopPadding,
                                bottom = surfaceSpec.chatBottomPadding
                            ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp)
                ) {
                    ChatInfoHeader(
                        conversation = sourceConversation,
                        accent = accent,
                        title = "转发消息",
                        subtitle = "选择目标聊天",
                        surfaceSpec = surfaceSpec,
                        onNavigateBack = onNavigateBack
                    )

                    MessageCapsule(
                        message =
                            ChatBubble(
                                text = message.forwardPreviewText(),
                                mine = false,
                                encrypted = message.encrypted,
                                timestamp = message.timestamp,
                                deliveryState = DeliveryState.System
                            ),
                        compact = compact,
                        accent = accent,
                        isStarred = false
                    )

                    conversations.forEach { conversation ->
                        val conversationMessages = messagesByConversation[conversation.id].orEmpty()
                        val lastMessage =
                            conversationMessages
                                .lastOrNull { chatMessage -> chatMessage.deliveryState != DeliveryState.System }
                        val blockedByForwardPolicy =
                            conversation.kind == ConversationKind.Group &&
                                !allowsFrequentlyForwarded(conversation.id) &&
                                message.nextForwardCount() >= FREQUENTLY_FORWARDED_THRESHOLD
                        ConversationCapsule(
                            conversation =
                                if (blockedByForwardPolicy) {
                                    conversation.copy(subtitle = "禁止多次转发 · ${conversation.subtitle}")
                                } else {
                                    conversation
                                },
                            lastMessage = lastMessage,
                            unreadCount = unreadCounts[conversation.id] ?: 0,
                            retryableCount = conversationMessages.count { chatMessage -> chatMessage.canRetry() },
                            isPinned = pinnedConversationIds[conversation.id] == true,
                            isMuted = isConversationMuted(conversation.id),
                            featured = conversation.id == sourceConversation.id,
                            surfaceSpec = surfaceSpec,
                            onClick = {
                                if (!blockedByForwardPolicy) {
                                    onSelectConversation(conversation)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchArchivedChatsSurface(
    isRoundScreen: Boolean,
    conversations: List<ChatConversation>,
    archivedUnreadCount: Int,
    messagesByConversation: Map<String, List<ChatBubble>>,
    unreadCounts: Map<String, Int>,
    pinnedConversationIds: Map<String, Boolean>,
    isConversationMuted: (String) -> Boolean,
    draftsByConversation: Map<String, ConversationDraft>,
    lockedConversationIds: Map<String, Boolean>,
    onNavigateBack: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenConversation: (ChatConversation) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val compact = maxWidth < 260.dp || maxHeight < 260.dp
        val surfaceSpec = WatchSurfaceSpec(isRound = isRoundScreen, compact = compact)
        val scrollState = rememberScrollState()
        val headerConversation =
            conversations.firstOrNull()
                ?: ChatConversation(
                    id = NEARBY_GROUP_CONVERSATION_ID,
                    kind = ConversationKind.Group,
                    title = NEARBY_GROUP_TITLE,
                    subtitle = "没有已归档聊天"
                )

        WatchFrame(
            surfaceSpec = surfaceSpec,
            accent = chatAmber,
            modifier = Modifier.padding(horizontal = surfaceSpec.chatHorizontalPadding)
        ) {
            ScreenScaffold(
                scrollState = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(),
                scrollIndicator = {}
            ) { scaffoldPadding ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(scaffoldPadding)
                            .padding(
                                top = surfaceSpec.chatTopPadding,
                                bottom = surfaceSpec.chatBottomPadding
                            ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp)
                ) {
                    ChatInfoHeader(
                        conversation = headerConversation,
                        accent = chatAmber,
                        title = "已归档聊天",
                        subtitle =
                            if (conversations.isEmpty()) {
                                "新消息静默收纳"
                            } else if (archivedUnreadCount > 0) {
                                "${conversations.size} 个聊天 · $archivedUnreadCount 个未读 · 静默收纳"
                            } else {
                                "${conversations.size} 个聊天 · 静默收纳"
                            },
                        surfaceSpec = surfaceSpec,
                        onNavigateBack = onNavigateBack
                    )

                    if (archivedUnreadCount > 0) {
                        Column(
                            modifier = Modifier.fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f)
                        ) {
                            MessageActionButton(
                                icon = Icons.Filled.DoneAll,
                                text = "全部标为已读",
                                selected = true,
                                compact = compact,
                                onClick = onMarkAllRead
                            )
                        }
                    }

                    if (conversations.isEmpty()) {
                        SearchEmptyState(
                            text = "还没有归档聊天",
                            compact = compact,
                            surfaceSpec = surfaceSpec
                        )
                    } else {
                        conversations.forEach { conversation ->
                            val conversationMessages = messagesByConversation[conversation.id].orEmpty()
                            val lastMessage =
                                conversationMessages
                                    .lastOrNull { message -> message.deliveryState != DeliveryState.System }
                            ConversationCapsule(
                                conversation = conversation,
                                lastMessage = lastMessage,
                                unreadCount = unreadCounts[conversation.id] ?: 0,
                                retryableCount = conversationMessages.count { message -> message.canRetry() },
                                draft = draftsByConversation[conversation.id],
                                locked = lockedConversationIds[conversation.id] == true,
                                isPinned = pinnedConversationIds[conversation.id] == true,
                                isMuted = isConversationMuted(conversation.id),
                                featured = false,
                                surfaceSpec = surfaceSpec,
                                onClick = { onOpenConversation(conversation) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    Box(
        modifier =
            Modifier
                .size(17.dp)
                .clip(CircleShape)
                .background(chatRose),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 9) "9+" else count.toString(),
            color = Color.White,
            fontSize = if (count > 9) 8.sp else 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun RetryBadge(count: Int) {
    Row(
        modifier =
            Modifier
                .height(17.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(chatRose)
                .padding(horizontal = 5.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = "未发送",
            tint = Color.White,
            modifier = Modifier.size(9.dp)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = if (count > 9) "9+" else count.toString(),
            color = Color.White,
            fontSize = if (count > 9) 8.sp else 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun MentionBadge() {
    Box(
        modifier =
            Modifier
                .size(17.dp)
                .clip(CircleShape)
                .background(chatBlue),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.AlternateEmail,
            contentDescription = "提及你",
            tint = Color.White,
            modifier = Modifier.size(10.dp)
        )
    }
}

@Composable
private fun ConversationAvatar(
    conversation: ChatConversation,
    size: Dp,
    accent: Color
) {
    Box(
        modifier =
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors =
                            listOf(
                                accent.copy(alpha = 0.95f),
                                accent.copy(alpha = 0.42f),
                                Color.White.copy(alpha = 0.08f)
                            )
                    )
                ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector =
                if (conversation.kind == ConversationKind.Group) {
                    Icons.Filled.Group
                } else {
                    Icons.Filled.VerifiedUser
                },
            contentDescription = conversation.kind.label,
            tint = Color.White,
            modifier = Modifier.size(size * 0.48f)
        )
    }
}

@Composable
private fun WatchChatInfoSurface(
    isRoundScreen: Boolean,
    conversation: ChatConversation,
    trustedPeers: List<StoredTrustedPeer>,
    fingerprint: String,
    reachability: String,
    messages: List<ChatBubble>,
    pinnedMessage: ChatBubble?,
    groupAbout: String?,
    isPinned: Boolean,
    isFavorite: Boolean,
    isMuted: Boolean,
    muteStatus: String,
    muteAction: String,
    isArchived: Boolean,
    isBlocked: Boolean,
    isLocked: Boolean,
    readReceiptsEnabled: Boolean,
    allowsFrequentlyForwarded: Boolean,
    isAnnouncementOnly: Boolean,
    unreadCount: Int,
    starredCount: Int,
    retryableCount: Int,
    disappearingMode: DisappearingMessageMode,
    onNavigateBack: () -> Unit,
    onOpenSecurityCheck: () -> Unit,
    onOpenGroupMembers: () -> Unit,
    onEditGroupAbout: (() -> Unit)?,
    onOpenStarredMessages: () -> Unit,
    onOpenContentMessages: () -> Unit,
    onOpenPinnedMessage: (ChatBubble) -> Unit,
    onSearchMessages: () -> Unit,
    onTogglePinned: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEditAlias: (() -> Unit)?,
    onCycleTheme: () -> Unit,
    onToggleMuted: () -> Unit,
    onToggleArchived: () -> Unit,
    onToggleBlocked: () -> Unit,
    onToggleLocked: () -> Unit,
    onToggleReadReceipts: () -> Unit,
    onToggleFrequentlyForwarded: () -> Unit,
    onToggleAnnouncementOnly: () -> Unit,
    onToggleUnread: () -> Unit,
    onRetryFailedMessages: () -> Unit,
    onToggleDisappearingMessages: () -> Unit,
    onClearConversation: () -> Unit,
    onForgetPeer: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val compact = maxWidth < 260.dp || maxHeight < 260.dp
        val surfaceSpec = WatchSurfaceSpec(isRound = isRoundScreen, compact = compact)
        val accent = conversationAccentColor(conversation)
        val scrollState = rememberScrollState()
        val trustedByFingerprint = trustedPeers.associateBy { peer -> peer.fingerprint }
        val memberPeers =
            conversation.memberFingerprints.mapNotNull { memberFingerprint ->
                trustedByFingerprint[memberFingerprint]
            }
        val memberSummary =
            when {
                conversation.kind == ConversationKind.Direct ->
                    memberPeers.firstOrNull()?.deviceName ?: conversation.title
                memberPeers.isEmpty() -> "等待成员"
                else ->
                    memberPeers
                        .take(3)
                        .joinToString(separator = "、") { peer -> peer.deviceName }
        }
        val messageCount = messages.count { message -> message.deliveryState != DeliveryState.System }
        val contentSummary = conversationContentSummary(messages)

        WatchFrame(
            surfaceSpec = surfaceSpec,
            accent = accent,
            modifier = Modifier.padding(horizontal = surfaceSpec.chatHorizontalPadding)
        ) {
            ScreenScaffold(
                scrollState = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(),
                scrollIndicator = {}
            ) { scaffoldPadding ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(scaffoldPadding)
                            .padding(
                                top = surfaceSpec.chatTopPadding,
                                bottom = surfaceSpec.chatBottomPadding
                            ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
                ) {
                    ChatInfoHeader(
                        conversation = conversation,
                        accent = accent,
                        title = "聊天信息",
                        subtitle = conversation.title,
                        surfaceSpec = surfaceSpec,
                        onNavigateBack = onNavigateBack
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f),
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InfoMetricPill(
                            label = "成员",
                            value =
                                if (conversation.kind == ConversationKind.Group) {
                                    conversation.memberFingerprints.size.toString()
                                } else {
                                    "1"
                                },
                            compact = compact,
                            modifier = Modifier.weight(1f)
                        )
                        InfoMetricPill(
                            label = "消息",
                            value = messageCount.toString(),
                            compact = compact,
                            modifier = Modifier.weight(1f)
                        )
                        InfoMetricPill(
                            label = "加密",
                            value = "开启",
                            compact = compact,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f),
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InfoMetricPill(
                            label = "收藏",
                            value = if (isFavorite) "是" else "否",
                            compact = compact,
                            modifier = Modifier.weight(1f)
                        )
                        InfoMetricPill(
                            label = "通知",
                            value = if (isArchived) "归档" else muteStatus,
                            compact = compact,
                            modifier = Modifier.weight(1f)
                        )
                        InfoMetricPill(
                            label = "未读",
                            value = unreadCount.coerceAtMost(99).toString(),
                            compact = compact,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f),
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InfoMetricPill(
                            label = "星标",
                            value = starredCount.coerceAtMost(99).toString(),
                            compact = compact,
                            modifier = Modifier.weight(1f)
                        )
                        InfoMetricPill(
                            label = "归档",
                            value = if (isArchived) "是" else "否",
                            compact = compact,
                            modifier = Modifier.weight(1f)
                        )
                        InfoMetricPill(
                            label = "限时",
                            value = disappearingMode.label,
                            compact = compact,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f),
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InfoMetricPill(
                            label = "未发送",
                            value = retryableCount.coerceAtMost(99).toString(),
                            compact = compact,
                            modifier = Modifier.weight(1f)
                        )
                        InfoMetricPill(
                            label = "锁定",
                            value = if (isLocked) "是" else "否",
                            compact = compact,
                            modifier = Modifier.weight(1f)
                        )
                        InfoMetricPill(
                            label = "回执",
                            value = if (readReceiptsEnabled) "开" else "关",
                            compact = compact,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    pinnedMessage?.let { message ->
                        ChatInfoPinnedMessagePreview(
                            message = message,
                            accent = accent,
                            surfaceSpec = surfaceSpec,
                            onClick = { onOpenPinnedMessage(message) }
                        )
                    }

                    ChatInfoLine(
                        icon =
                            if (conversation.kind == ConversationKind.Group) {
                                Icons.Filled.Group
                            } else {
                                Icons.Filled.VerifiedUser
                            },
                        label =
                            if (conversation.kind == ConversationKind.Group) {
                                "成员"
                            } else {
                                "对方"
                            },
                        value = memberSummary,
                        accent = accent,
                        compact = compact,
                        surfaceSpec = surfaceSpec
                    )

                    ChatInfoLine(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        label = "内容概览",
                        value = contentSummary.summaryLabel(),
                        accent = chatAmber,
                        compact = compact,
                        surfaceSpec = surfaceSpec
                    )

                    groupAbout?.let { about ->
                        ChatInfoLine(
                            icon = Icons.Filled.Group,
                            label = "群公告",
                            value = about,
                            accent = chatGreen,
                            compact = compact,
                            surfaceSpec = surfaceSpec
                        )
                    }

                    if (conversation.kind == ConversationKind.Group) {
                        ChatInfoLine(
                            icon = Icons.Filled.Group,
                            label = "发言权限",
                            value =
                                if (isAnnouncementOnly) {
                                    "公告模式"
                                } else {
                                    "成员可发言"
                                },
                            accent = if (isAnnouncementOnly) chatAmber else chatGreen,
                            compact = compact,
                            surfaceSpec = surfaceSpec
                        )
                    }

                    if (conversation.kind == ConversationKind.Group) {
                        ChatInfoLine(
                            icon = Icons.AutoMirrored.Filled.Chat,
                            label = "多次转发",
                            value =
                                if (allowsFrequentlyForwarded) {
                                    "允许进入群聊"
                                } else {
                                    "自动拦截"
                                },
                            accent = if (allowsFrequentlyForwarded) chatAmber else chatRose,
                            compact = compact,
                            surfaceSpec = surfaceSpec
                        )
                    }

                    ChatInfoLine(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        label = "类型",
                        value = conversation.kind.label,
                        accent = accent,
                        compact = compact,
                        surfaceSpec = surfaceSpec
                    )

                    ChatInfoLine(
                        icon = Icons.Filled.Lan,
                        label = "可达状态",
                        value = reachability,
                        accent = accent,
                        compact = compact,
                        surfaceSpec = surfaceSpec
                    )

                    ChatInfoLine(
                        icon = Icons.Filled.Lock,
                        label = "我的指纹",
                        value = SpotChatCrypto.displayFingerprint(fingerprint),
                        accent = chatGreen,
                        compact = compact,
                        surfaceSpec = surfaceSpec
                    )

                    conversation.peerFingerprint?.let { peerFingerprint ->
                        ChatInfoLine(
                            icon = Icons.Filled.VerifiedUser,
                            label = "对方指纹",
                            value = SpotChatCrypto.displayFingerprint(peerFingerprint),
                            accent = chatBlue,
                            compact = compact,
                            surfaceSpec = surfaceSpec
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 7.dp)
                    ) {
                        MessageActionButton(
                            icon = Icons.Filled.PushPin,
                            text = if (isPinned) "取消置顶" else "置顶聊天",
                            selected = isPinned,
                            compact = compact,
                            onClick = onTogglePinned
                        )
                        MessageActionButton(
                            icon = Icons.Filled.StarRate,
                            text = if (isFavorite) "取消收藏" else "收藏聊天",
                            selected = isFavorite,
                            compact = compact,
                            onClick = onToggleFavorite
                        )
                        onEditAlias?.let { editAlias ->
                            MessageActionButton(
                                icon = Icons.Filled.Keyboard,
                                text = "联系人备注",
                                selected = conversation.peerFingerprint != null,
                                compact = compact,
                                onClick = editAlias
                            )
                        }
                        MessageActionButton(
                            icon = Icons.Filled.Palette,
                            text = "切换聊天颜色",
                            selected = conversation.themeColor != null,
                            compact = compact,
                            onClick = onCycleTheme
                        )
                        MessageActionButton(
                            icon = Icons.Filled.NotificationsOff,
                            text = muteAction,
                            selected = isMuted,
                            compact = compact,
                            onClick = onToggleMuted
                        )
                        MessageActionButton(
                            icon = Icons.Filled.Archive,
                            text = if (isArchived) "取消归档" else "归档聊天",
                            selected = isArchived,
                            compact = compact,
                            onClick = onToggleArchived
                        )
                        MessageActionButton(
                            icon = Icons.Filled.MarkChatUnread,
                            text = if (unreadCount > 0) "标为已读" else "标为未读",
                            selected = unreadCount > 0,
                            compact = compact,
                            onClick = onToggleUnread
                        )
                        MessageActionButton(
                            icon = Icons.Filled.VerifiedUser,
                            text = if (readReceiptsEnabled) "关闭已读回执" else "开启已读回执",
                            selected = !readReceiptsEnabled,
                            compact = compact,
                            onClick = onToggleReadReceipts
                        )
                        MessageActionButton(
                            icon = Icons.Filled.Lock,
                            text = if (isLocked) "解锁聊天预览" else "锁定聊天预览",
                            selected = isLocked,
                            compact = compact,
                            onClick = onToggleLocked
                        )
                        if (retryableCount > 0) {
                            MessageActionButton(
                                icon = Icons.Filled.Refresh,
                                text = "重发未发送",
                                selected = true,
                                compact = compact,
                                onClick = onRetryFailedMessages
                            )
                        }
                        if (conversation.kind == ConversationKind.Group) {
                            MessageActionButton(
                                icon = Icons.Filled.Group,
                                text = "群成员",
                                selected = memberPeers.isNotEmpty(),
                                compact = compact,
                                onClick = onOpenGroupMembers
                            )
                        }
                        onEditGroupAbout?.let { editGroupAbout ->
                            MessageActionButton(
                                icon = Icons.Filled.Group,
                                text = "编辑群公告",
                                selected = !groupAbout.isNullOrBlank(),
                                compact = compact,
                                onClick = editGroupAbout
                            )
                        }
                        if (conversation.kind == ConversationKind.Group) {
                            MessageActionButton(
                                icon = Icons.Filled.Group,
                                text =
                                    if (isAnnouncementOnly) {
                                        "关闭公告模式"
                                    } else {
                                        "开启公告模式"
                                    },
                                selected = isAnnouncementOnly,
                                compact = compact,
                                onClick = onToggleAnnouncementOnly
                            )
                            MessageActionButton(
                                icon = Icons.AutoMirrored.Filled.Chat,
                                text =
                                    if (allowsFrequentlyForwarded) {
                                        "禁止多次转发"
                                    } else {
                                        "允许多次转发"
                                    },
                                selected = !allowsFrequentlyForwarded,
                                destructive = allowsFrequentlyForwarded,
                                compact = compact,
                                onClick = onToggleFrequentlyForwarded
                            )
                        }
                        MessageActionButton(
                            icon = Icons.Filled.StarRate,
                            text = "星标消息",
                            selected = starredCount > 0,
                            compact = compact,
                            onClick = onOpenStarredMessages
                        )
                        MessageActionButton(
                            icon = Icons.AutoMirrored.Filled.Chat,
                            text = "内容消息",
                            selected = contentSummary.hasContent,
                            compact = compact,
                            onClick = onOpenContentMessages
                        )
                        pinnedMessage?.let { message ->
                            MessageActionButton(
                                icon = Icons.Filled.PushPin,
                                text = "查看置顶消息",
                                selected = true,
                                compact = compact,
                                onClick = { onOpenPinnedMessage(message) }
                            )
                        }
                        MessageActionButton(
                            icon = Icons.Filled.AutoDelete,
                            text = disappearingActionLabel(disappearingMode),
                            selected = disappearingMode != DisappearingMessageMode.Off,
                            compact = compact,
                            onClick = onToggleDisappearingMessages
                        )
                        MessageActionButton(
                            icon = Icons.Filled.Lock,
                            text = "安全校验",
                            selected = true,
                            compact = compact,
                            onClick = onOpenSecurityCheck
                        )
                        MessageActionButton(
                            icon = Icons.AutoMirrored.Filled.Chat,
                            text = "查找消息",
                            selected = false,
                            compact = compact,
                            onClick = onSearchMessages
                        )
                        MessageActionButton(
                            icon = Icons.Filled.Delete,
                            text = "清空聊天",
                            selected = false,
                            compact = compact,
                            onClick = onClearConversation
                        )
                        if (conversation.kind == ConversationKind.Direct) {
                            MessageActionButton(
                                icon = Icons.Filled.PersonRemove,
                                text = if (isBlocked) "解除阻止" else "阻止联系人",
                                selected = isBlocked,
                                destructive = !isBlocked,
                                compact = compact,
                                onClick = onToggleBlocked
                            )
                            MessageActionButton(
                                icon = Icons.Filled.PersonRemove,
                                text = "移除信任",
                                selected = false,
                                destructive = true,
                                compact = compact,
                                onClick = onForgetPeer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchMuteSettingsSurface(
    isRoundScreen: Boolean,
    conversation: ChatConversation,
    currentMute: MutedConversation?,
    onNavigateBack: () -> Unit,
    onSelectMutePreset: (MutePreset?) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val compact = maxWidth < 260.dp || maxHeight < 260.dp
        val surfaceSpec = WatchSurfaceSpec(isRound = isRoundScreen, compact = compact)
        val accent = conversationAccentColor(conversation)
        val scrollState = rememberScrollState()

        WatchFrame(
            surfaceSpec = surfaceSpec,
            accent = accent,
            modifier = Modifier.padding(horizontal = surfaceSpec.chatHorizontalPadding)
        ) {
            ScreenScaffold(
                scrollState = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(),
                scrollIndicator = {}
            ) { scaffoldPadding ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(scaffoldPadding)
                            .padding(
                                top = surfaceSpec.chatTopPadding,
                                bottom = surfaceSpec.chatBottomPadding
                            ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
                ) {
                    ChatInfoHeader(
                        conversation = conversation,
                        accent = accent,
                        title = "通知静音",
                        subtitle = conversation.title,
                        surfaceSpec = surfaceSpec,
                        onNavigateBack = onNavigateBack
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 7.dp)
                    ) {
                        MutePreset.entries.forEach { preset ->
                            MessageActionButton(
                                icon = Icons.Filled.NotificationsOff,
                                text = "静音${preset.label}",
                                selected = currentMute?.preset == preset,
                                compact = compact,
                                onClick = { onSelectMutePreset(preset) }
                            )
                        }
                        MessageActionButton(
                            icon = Icons.Filled.NotificationsOff,
                            text = "恢复通知",
                            selected = currentMute == null,
                            compact = compact,
                            onClick = { onSelectMutePreset(null) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchDisappearingSettingsSurface(
    isRoundScreen: Boolean,
    conversation: ChatConversation,
    currentMode: DisappearingMessageMode,
    onNavigateBack: () -> Unit,
    onSelectMode: (DisappearingMessageMode) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val compact = maxWidth < 260.dp || maxHeight < 260.dp
        val surfaceSpec = WatchSurfaceSpec(isRound = isRoundScreen, compact = compact)
        val accent = conversationAccentColor(conversation)
        val scrollState = rememberScrollState()

        WatchFrame(
            surfaceSpec = surfaceSpec,
            accent = accent,
            modifier = Modifier.padding(horizontal = surfaceSpec.chatHorizontalPadding)
        ) {
            ScreenScaffold(
                scrollState = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(),
                scrollIndicator = {}
            ) { scaffoldPadding ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(scaffoldPadding)
                            .padding(
                                top = surfaceSpec.chatTopPadding,
                                bottom = surfaceSpec.chatBottomPadding
                            ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
                ) {
                    ChatInfoHeader(
                        conversation = conversation,
                        accent = accent,
                        title = "限时消息",
                        subtitle = conversation.title,
                        surfaceSpec = surfaceSpec,
                        onNavigateBack = onNavigateBack
                    )

                    DisappearingMessageNotice(
                        currentMode = currentMode,
                        surfaceSpec = surfaceSpec
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 7.dp)
                    ) {
                        DisappearingMessageMode.entries.forEach { mode ->
                            MessageActionButton(
                                icon = Icons.Filled.AutoDelete,
                                text =
                                    if (mode == DisappearingMessageMode.Off) {
                                        "关闭限时"
                                    } else {
                                        "限时${mode.label}"
                                    },
                                selected = currentMode == mode,
                                compact = compact,
                                onClick = { onSelectMode(mode) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisappearingMessageNotice(
    currentMode: DisappearingMessageMode,
    surfaceSpec: WatchSurfaceSpec
) {
    val compact = surfaceSpec.compact
    Column(
        modifier =
            Modifier
                .fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f)
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.82f))
                .border(1.dp, chatGreen.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 7.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp)
    ) {
        ProfileInfoRow(
            icon = Icons.Filled.AutoDelete,
            label = "当前模式",
            value = currentMode.label,
            accent = if (currentMode == DisappearingMessageMode.Off) chatRowMuted else chatGreen,
            compact = compact
        )
        ProfileInfoRow(
            icon = Icons.Filled.Schedule,
            label = "生效范围",
            value = if (currentMode == DisappearingMessageMode.Off) "新消息保留" else "仅新消息",
            accent = chatBlue,
            compact = compact
        )
        ProfileInfoRow(
            icon = Icons.Filled.Lock,
            label = "聊天记录",
            value = disappearingPreviewLabel(currentMode),
            accent = chatAmber,
            compact = compact
        )
    }
}

@Composable
private fun WatchMessageActionsSurface(
    isRoundScreen: Boolean,
    conversation: ChatConversation,
    message: ChatBubble,
    isStarred: Boolean,
    isPinned: Boolean,
    hasLocalReaction: Boolean,
    voicePlaybackSpeed: VoicePlaybackSpeed,
    hasQuotedMessageTarget: Boolean,
    canReplyPrivately: Boolean,
    receiptSummary: MessageReceiptSummary?,
    reactionDetails: List<ReactionDetail>,
    onNavigateBack: () -> Unit,
    onPlayVoiceMessage: () -> Unit,
    onCycleVoicePlaybackSpeed: () -> Unit,
    onToggleStarred: () -> Unit,
    onTogglePinned: () -> Unit,
    onCopyMessage: () -> Unit,
    onCopyMessageInfo: () -> Unit,
    onViewInChat: () -> Unit,
    onOpenQuotedMessage: () -> Unit,
    onDeleteMessage: () -> Unit,
    onReactToMessage: (String) -> Unit,
    onRemoveReaction: () -> Unit,
    onOpenReactionUsers: () -> Unit,
    onOpenCustomMessageInput: () -> Unit,
    onSendQuickReply: (String) -> Unit,
    onReplyToMessage: () -> Unit,
    onReplyPrivately: () -> Unit,
    onForwardMessage: () -> Unit,
    onEditRetryMessage: () -> Unit,
    onRetryMessage: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val compact = maxWidth < 260.dp || maxHeight < 260.dp
        val surfaceSpec = WatchSurfaceSpec(isRound = isRoundScreen, compact = compact)
        val accent = conversationAccentColor(conversation)
        val scrollState = rememberScrollState()
        val reactionUserCount = reactionDetails.distinctBy { detail -> detail.senderFingerprint }.size

        WatchFrame(
            surfaceSpec = surfaceSpec,
            accent = accent,
            modifier = Modifier.padding(horizontal = surfaceSpec.chatHorizontalPadding)
        ) {
            ScreenScaffold(
                scrollState = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(),
                scrollIndicator = {}
            ) { scaffoldPadding ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(scaffoldPadding)
                            .padding(
                                top = surfaceSpec.chatTopPadding,
                                bottom = surfaceSpec.chatBottomPadding
                            ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
                ) {
                    ChatInfoHeader(
                        conversation = conversation,
                        accent = accent,
                        title = "消息详情",
                        subtitle = conversation.title,
                        surfaceSpec = surfaceSpec,
                        onNavigateBack = onNavigateBack
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
                    ) {
                        MessageCapsule(
                            message = message,
                            compact = compact,
                            accent = accent,
                            isStarred = isStarred
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 7.dp)
                    ) {
                        if (message.canRetry()) {
                            MessageActionButton(
                                icon = Icons.Filled.Refresh,
                                text = "重发",
                                selected = true,
                                compact = compact,
                                onClick = onRetryMessage
                            )
                            if (message.kind == ChatMessageKind.Text) {
                                MessageActionButton(
                                    icon = Icons.Filled.Keyboard,
                                    text = "编辑重发",
                                    selected = true,
                                    compact = compact,
                                    onClick = onEditRetryMessage
                                )
                            }
                        }
                        if (message.kind == ChatMessageKind.Voice && !message.canRetry()) {
                            MessageActionButton(
                                icon = Icons.Filled.Mic,
                                text = "播放语音 ${voicePlaybackSpeed.label}",
                                selected = true,
                                compact = compact,
                                onClick = onPlayVoiceMessage
                            )
                            MessageActionButton(
                                icon = Icons.Filled.Schedule,
                                text = "倍速 ${voicePlaybackSpeed.next().label}",
                                selected = voicePlaybackSpeed != VoicePlaybackSpeed.Normal,
                                compact = compact,
                                onClick = onCycleVoicePlaybackSpeed
                            )
                        }
                        MessageActionButton(
                            icon = if (isStarred) Icons.Filled.StarRate else Icons.Filled.StarBorder,
                            text = if (isStarred) "取消星标" else "星标消息",
                            selected = isStarred,
                            compact = compact,
                            onClick = onToggleStarred
                        )
                        MessageActionButton(
                            icon = Icons.Filled.PushPin,
                            text = if (isPinned) "取消置顶消息" else "置顶消息",
                            selected = isPinned,
                            compact = compact,
                            onClick = onTogglePinned
                        )
                        MessageActionButton(
                            icon = Icons.Filled.Keyboard,
                            text = "复制内容",
                            selected = false,
                            compact = compact,
                            onClick = onCopyMessage
                        )
                        MessageActionButton(
                            icon = Icons.Filled.DoneAll,
                            text = "复制消息信息",
                            selected = receiptSummary != null,
                            compact = compact,
                            onClick = onCopyMessageInfo
                        )
                        MessageActionButton(
                            icon = Icons.AutoMirrored.Filled.Chat,
                            text = "查看所在聊天",
                            selected = false,
                            compact = compact,
                            onClick = onViewInChat
                        )
                        if (hasQuotedMessageTarget) {
                            MessageActionButton(
                                icon = Icons.AutoMirrored.Filled.Chat,
                                text = "查看引用消息",
                                selected = false,
                                compact = compact,
                                onClick = onOpenQuotedMessage
                            )
                        }
                        reactionChoices.forEach { reaction ->
                            MessageActionButton(
                                icon = Icons.AutoMirrored.Filled.Chat,
                                text = "回应 ${reaction.label}",
                                selected = false,
                                compact = compact,
                                onClick = { onReactToMessage(reaction.code) }
                            )
                        }
                        if (hasLocalReaction) {
                            MessageActionButton(
                                icon = Icons.Filled.Delete,
                                text = "取消我的回应",
                                selected = true,
                                destructive = true,
                                compact = compact,
                                onClick = onRemoveReaction
                            )
                        }
                        if (reactionUserCount > 0) {
                            MessageActionButton(
                                icon = Icons.Filled.Group,
                                text = "回应者 ${reactionUserCount}人",
                                selected = true,
                                compact = compact,
                                onClick = onOpenReactionUsers
                            )
                        }
                        MessageActionButton(
                            icon = Icons.Filled.Keyboard,
                            text = "输入消息",
                            selected = !message.canRetry(),
                            compact = compact,
                            onClick = onOpenCustomMessageInput
                        )
                        MessageActionButton(
                            icon = Icons.AutoMirrored.Filled.Chat,
                            text = "回复这条",
                            selected = false,
                            compact = compact,
                            onClick = onReplyToMessage
                        )
                        if (canReplyPrivately) {
                            MessageActionButton(
                                icon = Icons.AutoMirrored.Filled.Chat,
                                text = "私聊回复",
                                selected = false,
                                compact = compact,
                                onClick = onReplyPrivately
                            )
                        }
                        MessageActionButton(
                            icon = Icons.AutoMirrored.Filled.Chat,
                            text = "转发消息",
                            selected = false,
                            compact = compact,
                            onClick = onForwardMessage
                        )
                        MessageActionButton(
                            icon = Icons.Filled.Delete,
                            text = "删除本机消息",
                            selected = false,
                            destructive = true,
                            compact = compact,
                            onClick = onDeleteMessage
                        )
                        customMessageQuickChoices.take(2).forEach { reply ->
                            MessageActionButton(
                                icon = Icons.AutoMirrored.Filled.Chat,
                                text = reply,
                                selected = false,
                                compact = compact,
                                onClick = { onSendQuickReply(reply) }
                            )
                        }
                    }

                    MessageMetaStrip(
                        message = message,
                        receiptSummary = receiptSummary,
                        reactionDetails = reactionDetails,
                        compact = compact,
                        surfaceSpec = surfaceSpec
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchVoicePlaybackSurface(
    isRoundScreen: Boolean,
    conversation: ChatConversation,
    message: ChatBubble,
    voicePlaybackSpeed: VoicePlaybackSpeed,
    onNavigateBack: () -> Unit,
    onPlayVoiceMessage: () -> Unit,
    onCycleVoicePlaybackSpeed: () -> Unit,
    onCopyMessageInfo: () -> Unit,
    onViewInChat: () -> Unit,
    onOpenMessageActions: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val compact = maxWidth < 260.dp || maxHeight < 260.dp
        val surfaceSpec = WatchSurfaceSpec(isRound = isRoundScreen, compact = compact)
        val accent = conversationAccentColor(conversation)
        val scrollState = rememberScrollState()
        val durationLabel = formatDuration(message.voiceDurationMs ?: 0L)
        val senderLabel =
            if (message.mine) {
                "我"
            } else {
                message.senderName ?: conversation.title
            }

        WatchFrame(
            surfaceSpec = surfaceSpec,
            accent = accent,
            modifier = Modifier.padding(horizontal = surfaceSpec.chatHorizontalPadding)
        ) {
            ScreenScaffold(
                scrollState = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(),
                scrollIndicator = {}
            ) { scaffoldPadding ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(scaffoldPadding)
                            .padding(
                                top = surfaceSpec.chatTopPadding,
                                bottom = surfaceSpec.chatBottomPadding
                            ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
                ) {
                    ChatInfoHeader(
                        conversation = conversation,
                        accent = accent,
                        title = "语音播放",
                        subtitle = "$senderLabel · $durationLabel",
                        surfaceSpec = surfaceSpec,
                        onNavigateBack = onNavigateBack
                    )

                    VoicePlaybackPanel(
                        durationLabel = durationLabel,
                        speedLabel = voicePlaybackSpeed.label,
                        compact = compact,
                        surfaceSpec = surfaceSpec
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
                    ) {
                        MessageCapsule(
                            message = message,
                            compact = compact,
                            accent = accent,
                            voicePlaybackSpeed = voicePlaybackSpeed
                        )
                    }

                    ChatInfoLine(
                        icon = Icons.Filled.Mic,
                        label = "语音",
                        value = "$durationLabel · ${voicePlaybackSpeed.label}",
                        accent = chatGreen,
                        compact = compact,
                        surfaceSpec = surfaceSpec
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 7.dp)
                    ) {
                        MessageActionButton(
                            icon = Icons.Filled.Mic,
                            text = "播放 ${voicePlaybackSpeed.label}",
                            selected = true,
                            compact = compact,
                            onClick = onPlayVoiceMessage
                        )
                        MessageActionButton(
                            icon = Icons.Filled.Schedule,
                            text = "倍速 ${voicePlaybackSpeed.next().label}",
                            selected = voicePlaybackSpeed != VoicePlaybackSpeed.Normal,
                            compact = compact,
                            onClick = onCycleVoicePlaybackSpeed
                        )
                        MessageActionButton(
                            icon = Icons.Filled.DoneAll,
                            text = "复制语音信息",
                            selected = false,
                            compact = compact,
                            onClick = onCopyMessageInfo
                        )
                        MessageActionButton(
                            icon = Icons.AutoMirrored.Filled.Chat,
                            text = "查看所在聊天",
                            selected = false,
                            compact = compact,
                            onClick = onViewInChat
                        )
                        MessageActionButton(
                            icon = Icons.Filled.Keyboard,
                            text = "更多消息操作",
                            selected = false,
                            compact = compact,
                            onClick = onOpenMessageActions
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoicePlaybackPanel(
    durationLabel: String,
    speedLabel: String,
    compact: Boolean,
    surfaceSpec: WatchSurfaceSpec
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f)
                .clip(RoundedCornerShape(8.dp))
                .background(chatGreen.copy(alpha = 0.14f))
                .border(1.dp, chatGreen.copy(alpha = 0.36f), RoundedCornerShape(8.dp))
                .padding(horizontal = if (compact) 9.dp else 11.dp, vertical = if (compact) 8.dp else 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 8.dp)
    ) {
        Box(
            modifier =
                Modifier
                    .size(if (compact) 38.dp else 44.dp)
                    .clip(CircleShape)
                    .background(chatGreen.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = "语音消息",
                tint = Color(0xFF001F1B),
                modifier = Modifier.size(if (compact) 20.dp else 22.dp)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VoiceWaveBar(level = 0.42f, compact = compact)
            VoiceWaveBar(level = 0.76f, compact = compact)
            VoiceWaveBar(level = 0.58f, compact = compact)
            VoiceWaveBar(level = 0.9f, compact = compact)
            VoiceWaveBar(level = 0.52f, compact = compact)
            VoiceWaveBar(level = 0.7f, compact = compact)
        }
        Text(
            text = "$durationLabel · $speedLabel",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = if (compact) 12.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WatchReactionUsersSurface(
    isRoundScreen: Boolean,
    conversation: ChatConversation,
    message: ChatBubble,
    reactionDetails: List<ReactionDetail>,
    onNavigateBack: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val compact = maxWidth < 260.dp || maxHeight < 260.dp
        val surfaceSpec = WatchSurfaceSpec(isRound = isRoundScreen, compact = compact)
        val accent = conversationAccentColor(conversation)
        val scrollState = rememberScrollState()
        val uniqueDetails = reactionDetails.distinctBy { detail -> detail.senderFingerprint }
        val subtitle =
            if (uniqueDetails.isEmpty()) {
                "暂无回应"
            } else {
                "${uniqueDetails.size} 位回应者 · ${reactionSummary(message)}"
            }

        WatchFrame(
            surfaceSpec = surfaceSpec,
            accent = accent,
            modifier = Modifier.padding(horizontal = surfaceSpec.chatHorizontalPadding)
        ) {
            ScreenScaffold(
                scrollState = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(),
                scrollIndicator = {}
            ) { scaffoldPadding ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(scaffoldPadding)
                            .padding(
                                top = surfaceSpec.chatTopPadding,
                                bottom = surfaceSpec.chatBottomPadding
                            ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
                ) {
                    ChatInfoHeader(
                        conversation = conversation,
                        accent = accent,
                        title = "回应者",
                        subtitle = subtitle,
                        surfaceSpec = surfaceSpec,
                        onNavigateBack = onNavigateBack
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
                    ) {
                        MessageCapsule(
                            message = message,
                            compact = compact,
                            accent = accent
                        )
                    }

                    ReactionUsersSummaryCard(
                        reactionSummary = reactionSummary(message).ifBlank { "暂无回应" },
                        userCount = uniqueDetails.size,
                        compact = compact,
                        surfaceSpec = surfaceSpec
                    )

                    if (uniqueDetails.isEmpty()) {
                        SearchEmptyState(
                            text = "这条消息还没有回应",
                            compact = compact,
                            surfaceSpec = surfaceSpec
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f),
                            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 7.dp)
                        ) {
                            uniqueDetails.forEachIndexed { index, detail ->
                                ReactionUserCapsule(
                                    detail = detail,
                                    index = index,
                                    accent = accent,
                                    compact = compact
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactionUsersSummaryCard(
    reactionSummary: String,
    userCount: Int,
    compact: Boolean,
    surfaceSpec: WatchSurfaceSpec
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f)
                .height(if (compact) 38.dp else 42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.82f))
                .border(1.dp, chatBlue.copy(alpha = 0.24f), RoundedCornerShape(8.dp))
                .padding(horizontal = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.StarRate,
            contentDescription = "回应总览",
            tint = chatBlue,
            modifier = Modifier.size(if (compact) 13.dp else 15.dp)
        )
        Spacer(modifier = Modifier.width(if (compact) 6.dp else 8.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "回应总览",
                color = chatRowMuted,
                fontSize = if (compact) 9.sp else 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = reactionSummary,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = if (compact) 11.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(if (compact) 6.dp else 8.dp))
        Text(
            text = "${userCount}人",
            color = chatGreen,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun ReactionUserCapsule(
    detail: ReactionDetail,
    index: Int,
    accent: Color,
    compact: Boolean
) {
    val avatarColor =
        when (index % 3) {
            0 -> chatGreen
            1 -> accent
            else -> chatAmber
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(if (compact) 42.dp else 46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.88f))
                .border(1.dp, chatDivider.copy(alpha = 0.58f), RoundedCornerShape(8.dp))
                .padding(horizontal = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier =
                Modifier
                    .size(if (compact) 26.dp else 30.dp)
                    .clip(CircleShape)
                    .background(avatarColor.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = detail.senderName.take(1).ifBlank { "?" },
                color = avatarColor,
                fontSize = if (compact) 12.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.width(if (compact) 8.dp else 10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = detail.senderName,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = if (compact) 11.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "回应 ${detail.reactionLabel}",
                color = chatRowMuted,
                fontSize = if (compact) 9.sp else 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = detail.reactionLabel,
            color = chatGreen,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun WatchMessageSearchSurface(
    isRoundScreen: Boolean,
    conversation: ChatConversation,
    query: String,
    results: List<ChatBubble>,
    starredMessageIds: Set<String>,
    onNavigateBack: () -> Unit,
    onSearchAgain: () -> Unit,
    onOpenMessage: (ChatBubble) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val compact = maxWidth < 260.dp || maxHeight < 260.dp
        val surfaceSpec = WatchSurfaceSpec(isRound = isRoundScreen, compact = compact)
        val accent = conversationAccentColor(conversation)
        val scrollState = rememberScrollState()
        val cleanQuery = query.trim()

        WatchFrame(
            surfaceSpec = surfaceSpec,
            accent = accent,
            modifier = Modifier.padding(horizontal = surfaceSpec.chatHorizontalPadding)
        ) {
            ScreenScaffold(
                scrollState = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(),
                scrollIndicator = {}
            ) { scaffoldPadding ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(scaffoldPadding)
                            .padding(
                                top = surfaceSpec.chatTopPadding,
                                bottom = surfaceSpec.chatBottomPadding
                            ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp)
                ) {
                    ChatInfoHeader(
                        conversation = conversation,
                        accent = accent,
                        title = "查找消息",
                        subtitle =
                            if (cleanQuery.isBlank()) {
                                conversation.title
                            } else {
                                "\"$cleanQuery\""
                            },
                        surfaceSpec = surfaceSpec,
                        onNavigateBack = onNavigateBack
                    )

                    MessageActionButton(
                        icon = Icons.Filled.Keyboard,
                        text = "输入关键词",
                        selected = true,
                        compact = compact,
                        onClick = onSearchAgain
                    )
                    if (cleanQuery.isBlank()) {
                        SearchEmptyState(
                            text = "输入关键词查找此聊天",
                            compact = compact,
                            surfaceSpec = surfaceSpec
                        )
                    } else if (results.isEmpty()) {
                        SearchEmptyState(
                            text = "没有找到匹配消息",
                            compact = compact,
                            surfaceSpec = surfaceSpec
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(if (surfaceSpec.isRound) 0.86f else 0.94f),
                            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)
                        ) {
                            Text(
                                text = "${results.size} 条结果",
                                color = chatRowMuted,
                                fontSize = if (compact) 9.sp else 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            results.forEach { message ->
                                MessageCapsule(
                                    message = message,
                                    compact = compact,
                                    accent = accent,
                                    isStarred = message.stableStarId() in starredMessageIds,
                                    onClick = { onOpenMessage(message) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchStarredMessagesSurface(
    isRoundScreen: Boolean,
    conversation: ChatConversation,
    messages: List<ChatBubble>,
    starredMessageIds: Set<String>,
    onNavigateBack: () -> Unit,
    onOpenMessage: (ChatBubble) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val compact = maxWidth < 260.dp || maxHeight < 260.dp
        val surfaceSpec = WatchSurfaceSpec(isRound = isRoundScreen, compact = compact)
        val accent = conversationAccentColor(conversation)
        val scrollState = rememberScrollState()

        WatchFrame(
            surfaceSpec = surfaceSpec,
            accent = accent,
            modifier = Modifier.padding(horizontal = surfaceSpec.chatHorizontalPadding)
        ) {
            ScreenScaffold(
                scrollState = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(),
                scrollIndicator = {}
            ) { scaffoldPadding ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(scaffoldPadding)
                            .padding(
                                top = surfaceSpec.chatTopPadding,
                                bottom = surfaceSpec.chatBottomPadding
                            ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp)
                ) {
                    ChatInfoHeader(
                        conversation = conversation,
                        accent = accent,
                        title = "星标消息",
                        subtitle = conversation.title,
                        surfaceSpec = surfaceSpec,
                        onNavigateBack = onNavigateBack
                    )

                    if (messages.isEmpty()) {
                        SearchEmptyState(
                            text = "还没有星标消息",
                            compact = compact,
                            surfaceSpec = surfaceSpec
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(if (surfaceSpec.isRound) 0.86f else 0.94f),
                            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)
                        ) {
                            Text(
                                text = "${messages.size} 条星标",
                                color = chatRowMuted,
                                fontSize = if (compact) 9.sp else 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            messages.forEach { message ->
                                MessageCapsule(
                                    message = message,
                                    compact = compact,
                                    accent = accent,
                                    isStarred = message.stableStarId() in starredMessageIds,
                                    onClick = { onOpenMessage(message) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchGroupMembersSurface(
    isRoundScreen: Boolean,
    conversation: ChatConversation,
    trustedPeers: List<StoredTrustedPeer>,
    peerReachability: (String) -> String,
    onNavigateBack: () -> Unit,
    onOpenDirectChat: (StoredTrustedPeer) -> Unit,
    onOpenSecurityCheck: (StoredTrustedPeer) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val compact = maxWidth < 260.dp || maxHeight < 260.dp
        val surfaceSpec = WatchSurfaceSpec(isRound = isRoundScreen, compact = compact)
        val accent = conversationAccentColor(conversation)
        val scrollState = rememberScrollState()
        val trustedByFingerprint = trustedPeers.associateBy { peer -> peer.fingerprint }
        val memberPeers =
            conversation.memberFingerprints.mapNotNull { fingerprint ->
                trustedByFingerprint[fingerprint]
            }
        val memberReachability =
            memberPeers.associateWith { peer ->
                peerReachability(peer.fingerprint)
            }
        var activeFilter by remember { mutableStateOf(GroupMemberFilter.All) }
        val filteredMembers =
            memberPeers.filter { peer ->
                memberReachability[peer].orEmpty().matchesGroupMemberFilter(activeFilter)
            }

        WatchFrame(
            surfaceSpec = surfaceSpec,
            accent = accent,
            modifier = Modifier.padding(horizontal = surfaceSpec.chatHorizontalPadding)
        ) {
            ScreenScaffold(
                scrollState = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(),
                scrollIndicator = {}
            ) { scaffoldPadding ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(scaffoldPadding)
                            .padding(
                                top = surfaceSpec.chatTopPadding,
                                bottom = surfaceSpec.chatBottomPadding
                            ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp)
                ) {
                    ChatInfoHeader(
                        conversation = conversation,
                        accent = accent,
                        title = "群成员",
                        subtitle = "${memberPeers.size} 位成员",
                        surfaceSpec = surfaceSpec,
                        onNavigateBack = onNavigateBack
                    )

                    GroupMemberFilterStrip(
                        activeFilter = activeFilter,
                        reachabilityValues = memberReachability.values.toList(),
                        totalCount = memberPeers.size,
                        compact = compact,
                        surfaceSpec = surfaceSpec,
                        onSelectFilter = { filter ->
                            activeFilter = filter
                        }
                    )

                    if (memberPeers.isEmpty()) {
                        SearchEmptyState(
                            text = "还没有可信成员",
                            compact = compact,
                            surfaceSpec = surfaceSpec
                        )
                    } else if (filteredMembers.isEmpty()) {
                        SearchEmptyState(
                            text = "没有${activeFilter.label}成员",
                            compact = compact,
                            surfaceSpec = surfaceSpec
                        )
                    } else {
                        filteredMembers.forEach { peer ->
                            GroupMemberRow(
                                peer = peer,
                                reachability = memberReachability[peer].orEmpty(),
                                compact = compact,
                                surfaceSpec = surfaceSpec,
                                onClick = { onOpenDirectChat(peer) },
                                onOpenSecurityCheck = { onOpenSecurityCheck(peer) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupMemberRow(
    peer: StoredTrustedPeer,
    reachability: String,
    compact: Boolean,
    surfaceSpec: WatchSurfaceSpec,
    onClick: () -> Unit,
    onOpenSecurityCheck: () -> Unit
) {
    val accent = if (reachability.contains("当前可发送")) chatGreen else chatAmber
    Row(
        modifier =
            Modifier
                .fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f)
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.82f))
                .border(1.dp, chatDivider.copy(alpha = 0.56f), RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 7.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier =
                Modifier
                    .size(if (compact) 26.dp else 30.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.VerifiedUser,
                contentDescription = peer.deviceName,
                tint = accent,
                modifier = Modifier.size(if (compact) 14.dp else 16.dp)
            )
        }
        Spacer(modifier = Modifier.width(if (compact) 7.dp else 9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = peerDisplayName(peer),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = if (compact) 11.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${peerReachabilityShortLabel(reachability)} · ${peerAbout(peer)}",
                color = chatRowMuted,
                fontSize = if (compact) 9.sp else 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(if (compact) 6.dp else 8.dp))
        Box(
            modifier =
                Modifier
                    .height(if (compact) 26.dp else 28.dp)
                    .width(if (compact) 44.dp else 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(chatGreen.copy(alpha = 0.18f))
                    .border(1.dp, chatGreen.copy(alpha = 0.38f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenSecurityCheck),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "校验",
                color = chatGreen,
                fontSize = if (compact) 9.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GroupMemberFilterStrip(
    activeFilter: GroupMemberFilter,
    reachabilityValues: List<String>,
    totalCount: Int,
    compact: Boolean,
    surfaceSpec: WatchSurfaceSpec,
    onSelectFilter: (GroupMemberFilter) -> Unit
) {
    val filterScrollState = rememberScrollState()
    val density = LocalDensity.current
    val chipStepPx =
        with(density) {
            ((if (compact) 48.dp else 53.dp) * activeFilter.ordinal).roundToPx()
        }

    LaunchedEffect(activeFilter, compact) {
        filterScrollState.animateScrollTo(chipStepPx.coerceAtMost(filterScrollState.maxValue))
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth(if (surfaceSpec.isRound) 0.84f else 0.94f)
                .height(if (compact) 30.dp else 34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.18f))
                .border(1.dp, chatDivider.copy(alpha = 0.52f), RoundedCornerShape(8.dp))
                .horizontalScroll(filterScrollState)
                .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GroupMemberFilter.entries.forEach { filter ->
            GroupMemberFilterSegment(
                filter = filter,
                count = reachabilityValues.countForFilter(filter, totalCount),
                selected = activeFilter == filter,
                compact = compact,
                onClick = { onSelectFilter(filter) }
            )
        }
    }
}

@Composable
private fun GroupMemberFilterSegment(
    filter: GroupMemberFilter,
    count: Int,
    selected: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val foreground = if (selected) Color(0xFF001F1B) else chatRowMuted
    val background =
        if (selected) {
            chatGreen
        } else {
            Color.Transparent
        }
    val segmentWidth = if (compact) 45.dp else 50.dp
    Box(
        modifier =
            modifier
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(7.dp))
                .background(background)
                .clickable(onClick = onClick)
                .padding(horizontal = if (compact) 2.dp else 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${filter.label} ${count.coerceAtMost(99)}",
            color = foreground,
            fontSize = if (compact) 8.sp else 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WatchChatContentMessagesSurface(
    isRoundScreen: Boolean,
    conversation: ChatConversation,
    messages: List<ChatBubble>,
    starredMessageIds: Set<String>,
    onNavigateBack: () -> Unit,
    onOpenMessage: (ChatBubble) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val compact = maxWidth < 260.dp || maxHeight < 260.dp
        val surfaceSpec = WatchSurfaceSpec(isRound = isRoundScreen, compact = compact)
        val accent = conversationAccentColor(conversation)
        val scrollState = rememberScrollState()
        val contentSummary = conversationContentSummary(messages)
        var activeFilter by remember { mutableStateOf(ContentMessageFilter.All) }
        val filteredMessages =
            messages.filter { message -> message.matchesContentFilter(activeFilter) }

        WatchFrame(
            surfaceSpec = surfaceSpec,
            accent = accent,
            modifier = Modifier.padding(horizontal = surfaceSpec.chatHorizontalPadding)
        ) {
            ScreenScaffold(
                scrollState = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(),
                scrollIndicator = {}
            ) { scaffoldPadding ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(scaffoldPadding)
                            .padding(
                                top = surfaceSpec.chatTopPadding,
                                bottom = surfaceSpec.chatBottomPadding
                            ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp)
                ) {
                    ChatInfoHeader(
                        conversation = conversation,
                        accent = accent,
                        title = "内容消息",
                        subtitle = conversation.title,
                        surfaceSpec = surfaceSpec,
                        onNavigateBack = onNavigateBack
                    )

                    ContentMessageFilterStrip(
                        activeFilter = activeFilter,
                        summary = contentSummary,
                        totalCount = messages.size,
                        compact = compact,
                        surfaceSpec = surfaceSpec,
                        onSelectFilter = { filter ->
                            activeFilter = filter
                        }
                    )

                    if (messages.isEmpty()) {
                        SearchEmptyState(
                            text = "还没有内容消息",
                            compact = compact,
                            surfaceSpec = surfaceSpec
                        )
                    } else if (filteredMessages.isEmpty()) {
                        SearchEmptyState(
                            text = "没有${activeFilter.label}消息",
                            compact = compact,
                            surfaceSpec = surfaceSpec
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(if (surfaceSpec.isRound) 0.86f else 0.94f),
                            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)
                        ) {
                            Text(
                                text = "${filteredMessages.size} 条 · ${activeFilter.label} · ${contentSummary.summaryLabel()}",
                                color = chatRowMuted,
                                fontSize = if (compact) 9.sp else 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            filteredMessages.forEach { message ->
                                MessageCapsule(
                                    message = message,
                                    compact = compact,
                                    accent = accent,
                                    isStarred = message.stableStarId() in starredMessageIds,
                                    onClick = { onOpenMessage(message) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContentMessageFilterStrip(
    activeFilter: ContentMessageFilter,
    summary: ConversationContentSummary,
    totalCount: Int,
    compact: Boolean,
    surfaceSpec: WatchSurfaceSpec,
    onSelectFilter: (ContentMessageFilter) -> Unit
) {
    val filterScrollState = rememberScrollState()
    val density = LocalDensity.current
    val chipStepPx =
        with(density) {
            ((if (compact) 50.dp else 55.dp) * activeFilter.ordinal).roundToPx()
        }

    LaunchedEffect(activeFilter, compact) {
        filterScrollState.animateScrollTo(chipStepPx.coerceAtMost(filterScrollState.maxValue))
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth(if (surfaceSpec.isRound) 0.84f else 0.94f)
                .height(if (compact) 30.dp else 34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.18f))
                .border(1.dp, chatDivider.copy(alpha = 0.52f), RoundedCornerShape(8.dp))
                .horizontalScroll(filterScrollState)
                .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContentMessageFilter.entries.forEach { filter ->
            ContentFilterSegment(
                filter = filter,
                count = summary.countForFilter(filter, totalCount),
                selected = activeFilter == filter,
                compact = compact,
                onClick = { onSelectFilter(filter) }
            )
        }
    }
}

@Composable
private fun ContentFilterSegment(
    filter: ContentMessageFilter,
    count: Int,
    selected: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val foreground = if (selected) Color(0xFF001F1B) else chatRowMuted
    val background =
        if (selected) {
            chatGreen
        } else {
            Color.Transparent
        }
    val segmentWidth = if (compact) 45.dp else 50.dp
    Box(
        modifier =
            modifier
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(7.dp))
                .background(background)
                .clickable(onClick = onClick)
                .padding(horizontal = if (compact) 2.dp else 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${filter.label} ${count.coerceAtMost(99)}",
            color = foreground,
            fontSize = if (compact) 8.sp else 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WatchSecurityCheckSurface(
    isRoundScreen: Boolean,
    conversation: ChatConversation,
    trustedPeers: List<StoredTrustedPeer>,
    localFingerprint: String,
    selectedPeerFingerprint: String?,
    onCopySafetyCode: (StoredTrustedPeer) -> Unit,
    onNavigateBack: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val compact = maxWidth < 260.dp || maxHeight < 260.dp
        val surfaceSpec = WatchSurfaceSpec(isRound = isRoundScreen, compact = compact)
        val accent = conversationAccentColor(conversation)
        val scrollState = rememberScrollState()
        val trustedByFingerprint = trustedPeers.associateBy { peer -> peer.fingerprint }
        val memberPeers =
            conversation.memberFingerprints.mapNotNull { fingerprint ->
                trustedByFingerprint[fingerprint]
            }
        val selectedPeer =
            selectedPeerFingerprint?.let { fingerprint -> trustedByFingerprint[fingerprint] }
        val displayedPeers = selectedPeer?.let(::listOf) ?: memberPeers
        val primaryPeer = selectedPeer ?: memberPeers.firstOrNull()

        WatchFrame(
            surfaceSpec = surfaceSpec,
            accent = accent,
            modifier = Modifier.padding(horizontal = surfaceSpec.chatHorizontalPadding)
        ) {
            ScreenScaffold(
                scrollState = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(),
                scrollIndicator = {}
            ) { scaffoldPadding ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(scaffoldPadding)
                            .padding(
                                top = surfaceSpec.chatTopPadding,
                                bottom = surfaceSpec.chatBottomPadding
                            ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
                ) {
                    ChatInfoHeader(
                        conversation = conversation,
                        accent = accent,
                        title = "安全校验",
                        subtitle =
                            if (conversation.kind == ConversationKind.Direct) {
                                primaryPeer?.let(::peerDisplayName) ?: conversation.title
                            } else if (selectedPeer != null) {
                                peerDisplayName(selectedPeer)
                            } else {
                                "${memberPeers.size} 位成员"
                            },
                        surfaceSpec = surfaceSpec,
                        onNavigateBack = onNavigateBack
                    )

                    primaryPeer?.let { peer ->
                        SafetyCodeCard(
                            peer = peer,
                            compact = compact,
                            surfaceSpec = surfaceSpec,
                            onCopySafetyCode = { onCopySafetyCode(peer) }
                        )
                    }

                    ChatInfoLine(
                        icon = Icons.Filled.Lock,
                        label = "我的指纹",
                        value = SpotChatCrypto.displayFingerprint(localFingerprint),
                        accent = chatGreen,
                        compact = compact,
                        surfaceSpec = surfaceSpec
                    )

                    if (displayedPeers.isEmpty()) {
                        SearchEmptyState(
                            text = "还没有可信成员可校验",
                            compact = compact,
                            surfaceSpec = surfaceSpec
                        )
                    } else {
                        displayedPeers.forEach { peer ->
                            ChatInfoLine(
                                icon = Icons.Filled.VerifiedUser,
                                label = peerDisplayName(peer),
                                value = safetyPeerSummary(peer),
                                accent = accent,
                                compact = compact,
                                surfaceSpec = surfaceSpec
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchEntryRecoverySurface(
    isRoundScreen: Boolean,
    recoveryState: WearEntryRecoveryState?,
    visibleConversationCount: Int,
    archivedConversationCount: Int,
    onNavigateBack: () -> Unit,
    onOpenConversationList: () -> Unit,
    onRefreshWearSurfaces: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val compact = maxWidth < 260.dp || maxHeight < 260.dp
        val surfaceSpec = WatchSurfaceSpec(isRound = isRoundScreen, compact = compact)
        val scrollState = rememberScrollState()
        val source = recoveryState?.source ?: "手表入口"
        val actionLabel = recoveryState?.actionLabel ?: "打开聊天"
        val targetLabel =
            recoveryState
                ?.targetConversationId
                ?.takeIf { conversationId -> conversationId.isNotBlank() }
                ?.let(::shortMessageId)
                ?: "未指定"

        WatchFrame(
            surfaceSpec = surfaceSpec,
            accent = chatAmber,
            modifier = Modifier.padding(horizontal = surfaceSpec.chatHorizontalPadding)
        ) {
            ScreenScaffold(
                scrollState = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(),
                scrollIndicator = {}
            ) { scaffoldPadding ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(scaffoldPadding)
                            .padding(
                                top = surfaceSpec.chatTopPadding,
                                bottom = surfaceSpec.chatBottomPadding
                            ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
                ) {
                    RecoveryHeader(
                        source = source,
                        compact = compact,
                        surfaceSpec = surfaceSpec,
                        onNavigateBack = onNavigateBack
                    )

                    ChatInfoLine(
                        icon = Icons.Filled.Warning,
                        label = "入口状态",
                        value = "$source 无法继续",
                        accent = chatAmber,
                        compact = compact,
                        surfaceSpec = surfaceSpec
                    )
                    ChatInfoLine(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        label = "请求动作",
                        value = actionLabel,
                        accent = chatBlue,
                        compact = compact,
                        surfaceSpec = surfaceSpec
                    )
                    ChatInfoLine(
                        icon = Icons.Filled.VerifiedUser,
                        label = "目标聊天",
                        value = targetLabel,
                        accent = chatRose,
                        compact = compact,
                        surfaceSpec = surfaceSpec
                    )
                    ChatInfoLine(
                        icon = Icons.Filled.Group,
                        label = "当前聊天",
                        value = "${visibleConversationCount.coerceAtMost(99)} 个可见 · ${archivedConversationCount.coerceAtMost(99)} 个归档",
                        accent = chatGreen,
                        compact = compact,
                        surfaceSpec = surfaceSpec
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 7.dp)
                    ) {
                        MessageActionButton(
                            icon = Icons.AutoMirrored.Filled.Chat,
                            text = "回聊天列表",
                            selected = true,
                            compact = compact,
                            onClick = onOpenConversationList
                        )
                        MessageActionButton(
                            icon = Icons.Filled.Refresh,
                            text = "刷新手表入口",
                            selected = false,
                            compact = compact,
                            onClick = onRefreshWearSurfaces
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecoveryHeader(
    source: String,
    compact: Boolean,
    surfaceSpec: WatchSurfaceSpec,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(surfaceSpec.chatHeaderWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.92f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileBackButton(
                compact = compact,
                contentDescription = "返回聊天列表",
                onClick = onNavigateBack
            )
            Spacer(modifier = Modifier.width(if (compact) 9.dp else 11.dp))
            Box(
                modifier =
                    Modifier
                        .size(if (compact) 40.dp else 46.dp)
                        .clip(CircleShape)
                        .background(chatAmber.copy(alpha = 0.18f))
                        .border(1.dp, chatAmber.copy(alpha = 0.46f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = source,
                    tint = chatAmber,
                    modifier = Modifier.size(if (compact) 20.dp else 23.dp)
                )
            }
        }
        Text(
            text = "入口已失效",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = if (compact) 17.sp else 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = source,
            color = chatRowMuted,
            fontSize = if (compact) 10.sp else 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SafetyCodeCard(
    peer: StoredTrustedPeer,
    compact: Boolean,
    surfaceSpec: WatchSurfaceSpec,
    onCopySafetyCode: () -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f)
                .clip(RoundedCornerShape(8.dp))
                .background(chatGreen.copy(alpha = 0.14f))
                .border(1.dp, chatGreen.copy(alpha = 0.36f), RoundedCornerShape(8.dp))
                .padding(horizontal = if (compact) 9.dp else 11.dp, vertical = if (compact) 8.dp else 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp)
    ) {
        Text(
            text = "配对校验码",
            color = chatRowMuted,
            fontSize = if (compact) 9.sp else 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = peer.pairingCode,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = if (compact) 18.sp else 21.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = "与 ${peer.deviceName} 当面核对",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
            fontSize = if (compact) 9.sp else 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Box(
            modifier =
                Modifier
                    .height(if (compact) 26.dp else 28.dp)
                    .fillMaxWidth(if (surfaceSpec.isRound) 0.72f else 0.82f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(chatGreen.copy(alpha = 0.2f))
                    .border(1.dp, chatGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onCopySafetyCode),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "复制校验码",
                color = chatGreen,
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SearchEmptyState(
    text: String,
    compact: Boolean,
    surfaceSpec: WatchSurfaceSpec
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f)
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.72f))
                .border(1.dp, chatDivider.copy(alpha = 0.48f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = if (compact) 8.dp else 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = chatRowMuted,
            fontSize = if (compact) 10.sp else 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChatInfoHeader(
    conversation: ChatConversation,
    accent: Color,
    title: String,
    subtitle: String,
    surfaceSpec: WatchSurfaceSpec,
    onNavigateBack: () -> Unit
) {
    val compact = surfaceSpec.compact
    Column(
        modifier = Modifier.fillMaxWidth(surfaceSpec.chatHeaderWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.92f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileBackButton(
                compact = compact,
                contentDescription = "返回聊天",
                onClick = onNavigateBack
            )
            Spacer(modifier = Modifier.width(if (compact) 9.dp else 11.dp))
            ConversationAvatar(
                conversation = conversation,
                size = if (compact) 40.dp else 46.dp,
                accent = accent
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = if (compact) 17.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                color = chatRowMuted,
                fontSize = if (compact) 10.sp else 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun InfoMetricPill(
    label: String,
    value: String,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier =
            modifier
                .height(if (compact) 42.dp else 46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.82f))
                .border(1.dp, chatDivider.copy(alpha = 0.56f), RoundedCornerShape(8.dp))
                .padding(horizontal = 5.dp, vertical = if (compact) 5.dp else 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = if (compact) 13.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            color = chatRowMuted,
            fontSize = if (compact) 9.sp else 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ChatInfoPinnedMessagePreview(
    message: ChatBubble,
    accent: Color,
    surfaceSpec: WatchSurfaceSpec,
    onClick: () -> Unit
) {
    val compact = surfaceSpec.compact
    Row(
        modifier =
            Modifier
                .fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f)
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.82f))
                .border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 7.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier =
                Modifier
                    .size(if (compact) 24.dp else 28.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = "置顶消息",
                tint = accent,
                modifier = Modifier.size(if (compact) 13.dp else 15.dp)
            )
        }
        Spacer(modifier = Modifier.width(if (compact) 7.dp else 9.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = "置顶消息",
                color = chatRowMuted,
                fontSize = if (compact) 9.sp else 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = message.previewText(),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = if (compact) 11.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChatInfoLine(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    compact: Boolean,
    surfaceSpec: WatchSurfaceSpec
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f)
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.82f))
                .border(1.dp, chatDivider.copy(alpha = 0.56f), RoundedCornerShape(8.dp))
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 7.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier =
                Modifier
                    .size(if (compact) 24.dp else 28.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accent,
                modifier = Modifier.size(if (compact) 13.dp else 15.dp)
            )
        }
        Spacer(modifier = Modifier.width(if (compact) 7.dp else 9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = chatRowMuted,
                fontSize = if (compact) 9.sp else 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = if (compact) 11.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MessageActionButton(
    icon: ImageVector,
    text: String,
    selected: Boolean,
    destructive: Boolean = false,
    compact: Boolean,
    onClick: () -> Unit
) {
    val background =
        when {
            destructive -> chatRose.copy(alpha = 0.22f)
            selected -> chatGreen
            else -> chatSurfaceHigh.copy(alpha = 0.88f)
        }
    val foreground =
        when {
            destructive -> Color.White
            selected -> Color(0xFF001F1B)
            else -> MaterialTheme.colorScheme.onBackground
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(if (compact) 32.dp else 36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(background)
                .border(
                    width = 1.dp,
                    color =
                        when {
                            destructive -> chatRose.copy(alpha = 0.62f)
                            selected -> Color.White.copy(alpha = 0.1f)
                            else -> chatDivider.copy(alpha = 0.58f)
                        },
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onClick)
                .padding(horizontal = if (compact) 9.dp else 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = foreground,
            modifier = Modifier.size(if (compact) 13.dp else 15.dp)
        )
        Spacer(modifier = Modifier.width(if (compact) 5.dp else 6.dp))
        Text(
            text = text,
            color = foreground,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MessageMetaStrip(
    message: ChatBubble,
    receiptSummary: MessageReceiptSummary?,
    reactionDetails: List<ReactionDetail>,
    compact: Boolean,
    surfaceSpec: WatchSurfaceSpec
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f)
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.82f))
                .border(1.dp, chatBlue.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 6.dp else 7.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp)
    ) {
        MessageMetaRow(
            icon =
                if (message.encrypted) Icons.Filled.Lock else Icons.Filled.DoneAll,
            label = "安全",
            value = if (message.encrypted) "E2EE 加密" else "明文",
            compact = compact
        )
        MessageMetaRow(
            icon = Icons.Filled.DoneAll,
            label = "状态",
            value = message.deliveryState.label,
            compact = compact
        )
        receiptSummary?.let { summary ->
            MessageMetaRow(
                icon = Icons.Filled.DoneAll,
                label = "送达",
                value = receiptProgressLabel(summary.deliveredCount, summary.expectedCount),
                compact = compact
            )
            MessageMetaRow(
                icon = Icons.Filled.DoneAll,
                label = "已读",
                value = receiptProgressLabel(summary.readCount, summary.expectedCount),
                compact = compact
            )
            if (summary.deliveredNames.isNotEmpty()) {
                MessageMetaRow(
                    icon = Icons.Filled.VerifiedUser,
                    label = "送达者",
                    value = receiptNamesLabel(summary.deliveredNames),
                    compact = compact
                )
            }
            if (summary.readNames.isNotEmpty()) {
                MessageMetaRow(
                    icon = Icons.Filled.VerifiedUser,
                    label = "已读者",
                    value = receiptNamesLabel(summary.readNames),
                    compact = compact
                )
            }
            if (summary.undeliveredNames.isNotEmpty()) {
                MessageMetaRow(
                    icon = Icons.Filled.VerifiedUser,
                    label = "未送达",
                    value = receiptNamesLabel(summary.undeliveredNames),
                    compact = compact
                )
            }
            if (summary.unreadNames.isNotEmpty()) {
                MessageMetaRow(
                    icon = Icons.Filled.VerifiedUser,
                    label = "未读",
                    value = receiptNamesLabel(summary.unreadNames),
                    compact = compact
                )
            }
        }
        MessageMetaRow(
            icon = Icons.Filled.Schedule,
            label = "时间",
            value = message.timestamp,
            compact = compact
        )
        MessageMetaRow(
            icon =
                if (message.kind == ChatMessageKind.Voice) {
                    Icons.Filled.Mic
                } else {
                    Icons.AutoMirrored.Filled.Chat
                },
            label = "类型",
            value =
                if (message.kind == ChatMessageKind.Voice) {
                    "语音 · ${formatDuration(message.voiceDurationMs ?: 0L)}"
                } else {
                    "文字"
                },
            compact = compact
        )
        message.messageId?.let { messageId ->
            MessageMetaRow(
                icon = Icons.Filled.VerifiedUser,
                label = "消息ID",
                value = shortMessageId(messageId),
                compact = compact
            )
        }
        message.senderFingerprint?.let { senderFingerprint ->
            MessageMetaRow(
                icon = Icons.Filled.VerifiedUser,
                label = "发送者",
                value = SpotChatCrypto.displayFingerprint(senderFingerprint),
                compact = compact
            )
        }
        message.expiresAtEpochMillis?.let { expiresAt ->
            MessageMetaRow(
                icon = Icons.Filled.AutoDelete,
                label = "限时",
                value = "剩 ${formatTimeRemaining(expiresAt)}",
                compact = compact
            )
        }
        if (message.forwarded) {
            MessageMetaRow(
                icon = Icons.AutoMirrored.Filled.Chat,
                label = "来源",
                value = message.forwardLabel(),
                compact = compact
            )
        }
        if (message.reactions.isNotEmpty()) {
            MessageMetaRow(
                icon = Icons.Filled.StarRate,
                label = "回应",
                value = reactionSummary(message),
                compact = compact
            )
            if (reactionDetails.isNotEmpty()) {
                MessageMetaRow(
                    icon = Icons.Filled.VerifiedUser,
                    label = "回应者",
                    value = reactionDetailsLabel(reactionDetails),
                    compact = compact
                )
            }
        }
        message.quotedMessage?.let { quote ->
            MessageMetaRow(
                icon = Icons.AutoMirrored.Filled.Chat,
                label = "引用",
                value = "${quote.senderName} · ${quote.text}",
                compact = compact
            )
        }
    }
}

@Composable
private fun MessageMetaRow(
    icon: ImageVector,
    label: String,
    value: String,
    compact: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = chatBlue,
            modifier = Modifier.size(if (compact) 12.dp else 14.dp)
        )
        Spacer(modifier = Modifier.width(if (compact) 5.dp else 6.dp))
        Text(
            text = label,
            color = chatRowMuted,
            fontSize = if (compact) 9.sp else 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.72f)
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = if (compact) 9.sp else 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.24f)
        )
    }
}

@Composable
private fun WatchProfileSurface(
    isRoundScreen: Boolean,
    profile: ProfileSettings,
    avatars: List<DefaultAvatar>,
    fingerprint: String,
    transportMode: TransportMode,
    trustState: String,
    trustedPeers: List<StoredTrustedPeer>,
    blockedPeerFingerprints: Map<String, Boolean>,
    defaultDisappearingMode: DisappearingMessageMode,
    defaultReadReceiptsEnabled: Boolean,
    securityChangeAlertsEnabled: Boolean,
    onNavigateBack: () -> Unit,
    onDefaultDisappearingModeChange: (DisappearingMessageMode) -> Unit,
    onToggleDefaultReadReceipts: () -> Unit,
    onToggleSecurityChangeAlerts: () -> Unit,
    onProfileChange: (ProfileSettings) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val compact = maxWidth < 260.dp || maxHeight < 260.dp
        val surfaceSpec = WatchSurfaceSpec(isRound = isRoundScreen, compact = compact)
        val selectedAvatar = avatarFor(profile.avatarId)
        val avatarRows =
            remember(avatars) {
                avatars.chunked(PROFILE_AVATARS_PER_ROW)
            }
        val blockedPeers =
            trustedPeers.filter { peer -> blockedPeerFingerprints[peer.fingerprint] == true }
        val listState =
            rememberScalingLazyListState(
                initialCenterItemIndex = 1
            )

        WatchFrame(
            surfaceSpec = surfaceSpec,
            accent = chatRose,
            modifier = Modifier.padding(horizontal = surfaceSpec.profileHorizontalPadding)
        ) {
            ScreenScaffold(
                scrollState = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        top = surfaceSpec.profileTopPadding,
                        bottom = surfaceSpec.profileBottomPadding
                    ),
                scrollIndicator = {
                    ScrollIndicator(
                        state = listState,
                        modifier = Modifier.padding(end = surfaceSpec.scrollIndicatorEndPadding)
                    )
                }
            ) { scaffoldPadding ->
                ScalingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = scaffoldPadding,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 11.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    anchorType = ScalingLazyListAnchorType.ItemCenter
                ) {
                    item {
                        ProfileHeader(
                            profile = profile,
                            selectedAvatar = selectedAvatar,
                            surfaceSpec = surfaceSpec,
                            onNavigateBack = onNavigateBack
                        )
                    }

                    item {
                        ProfileNameField(
                            displayName = profile.displayName,
                            surfaceSpec = surfaceSpec,
                            onDisplayNameChange = { displayName ->
                                onProfileChange(
                                    profile.copy(
                                        displayName =
                                            displayName
                                                .replace("\n", "")
                                                .take(ProfileStore.MAX_DISPLAY_NAME_CHARS)
                                    )
                                )
                            }
                        )
                    }

                    item {
                        ProfileAboutField(
                            about = profile.about,
                            surfaceSpec = surfaceSpec,
                            onAboutChange = { about ->
                                onProfileChange(
                                    profile.copy(
                                        about =
                                            about
                                                .replace("\n", " ")
                                                .take(ProfileStore.MAX_ABOUT_CHARS)
                                    )
                                )
                            }
                        )
                    }

                    item {
                        ProfileSectionLabel(
                            text = "身份",
                            compact = compact
                        )
                    }

                    item {
                        ProfileSecurityPanel(
                            fingerprint = fingerprint,
                            transportMode = transportMode,
                            trustState = trustState,
                            trustedPeerCount = trustedPeers.size,
                            surfaceSpec = surfaceSpec,
                        )
                    }

                    item {
                        ProfileSectionLabel(
                            text = "可信设备",
                            compact = compact
                        )
                    }

                    if (trustedPeers.isEmpty()) {
                        item {
                            ProfileEmptyTrustedPeer(
                                compact = compact,
                                surfaceSpec = surfaceSpec
                            )
                        }
                    } else {
                        item {
                            ProfileTrustedDevicesSummaryRow(
                                peerCount = trustedPeers.size,
                                surfaceSpec = surfaceSpec
                            )
                        }
                        val visibleTrustedPeers = if (compact) 2 else 3
                        trustedPeers.take(visibleTrustedPeers).forEach { peer ->
                            item {
                                ProfileTrustedPeerRow(
                                    peer = peer,
                                    surfaceSpec = surfaceSpec
                                )
                            }
                        }
                        if (trustedPeers.size > visibleTrustedPeers) {
                            item {
                                ProfileTrustedPeerMoreRow(
                                    hiddenPeerCount = trustedPeers.size - visibleTrustedPeers,
                                    surfaceSpec = surfaceSpec
                                )
                            }
                        }
                    }

                    item {
                        ProfileSectionLabel(
                            text = "隐私",
                            compact = compact
                        )
                    }

                    item {
                        ProfilePrivacyPanel(
                            blockedPeers = blockedPeers,
                            defaultDisappearingMode = defaultDisappearingMode,
                            defaultReadReceiptsEnabled = defaultReadReceiptsEnabled,
                            securityChangeAlertsEnabled = securityChangeAlertsEnabled,
                            surfaceSpec = surfaceSpec,
                            onCycleDefaultDisappearingMode = {
                                onDefaultDisappearingModeChange(defaultDisappearingMode.next())
                            },
                            onToggleDefaultReadReceipts = onToggleDefaultReadReceipts,
                            onToggleSecurityChangeAlerts = onToggleSecurityChangeAlerts
                        )
                    }

                    item {
                        ProfileSectionLabel(
                            text = "头像",
                            compact = compact
                        )
                    }

                    avatarRows.forEach { rowAvatars ->
                        item {
                            ProfileAvatarRow(
                                avatars = rowAvatars,
                                selectedAvatar = selectedAvatar,
                                displayName = profile.displayName,
                                surfaceSpec = surfaceSpec,
                                onSelectAvatar = { avatar ->
                                    onProfileChange(profile.copy(avatarId = avatar.id))
                                }
                            )
                        }
                    }

                    item {
                        ProfileIdentityPill(
                            displayName = profile.displayName.ifBlank { "SpotChat Watch" },
                            about = profile.about,
                            trustedPeerCount = trustedPeers.size,
                            modifier = Modifier.fillMaxWidth(surfaceSpec.profileSummaryWidth),
                            compact = compact
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: ProfileSettings,
    selectedAvatar: DefaultAvatar,
    surfaceSpec: WatchSurfaceSpec,
    onNavigateBack: () -> Unit
) {
    val compact = surfaceSpec.compact
    Column(
        modifier = Modifier.fillMaxWidth(surfaceSpec.profileHeaderWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileBackButton(
                compact = compact,
                onClick = onNavigateBack
            )
            Spacer(modifier = Modifier.width(if (compact) 8.dp else 10.dp))
            AvatarBubble(
                avatar = selectedAvatar,
                displayName = profile.displayName,
                size = if (compact) 42.dp else 48.dp,
                textSize = if (compact) 18.sp else 20.sp,
                selected = false,
                onClick = null
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "个人资料",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = if (compact) 18.sp else 21.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            Text(
                text = profile.about.ifBlank { ProfileStore.DEFAULT_ABOUT },
                color = chatRowMuted,
                fontSize = if (compact) 10.sp else 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProfileAvatarRow(
    avatars: List<DefaultAvatar>,
    selectedAvatar: DefaultAvatar,
    displayName: String,
    surfaceSpec: WatchSurfaceSpec,
    onSelectAvatar: (DefaultAvatar) -> Unit
) {
    val compact = surfaceSpec.compact
    Row(
        modifier = Modifier.fillMaxWidth(surfaceSpec.profileAvatarRowWidth),
        horizontalArrangement =
            Arrangement.spacedBy(
                space = surfaceSpec.profileAvatarSpacing,
                alignment = Alignment.CenterHorizontally
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        avatars.forEach { avatar ->
            AvatarBubble(
                avatar = avatar,
                displayName = displayName,
                size = if (avatar.id == selectedAvatar.id) {
                    surfaceSpec.profileAvatarSize + 4.dp
                } else {
                    surfaceSpec.profileAvatarSize
                },
                textSize = if (compact) 16.sp else 17.sp,
                selected = avatar.id == selectedAvatar.id,
                onClick = {
                    onSelectAvatar(avatar)
                }
            )
        }
    }
}

@Composable
private fun ProfileBackButton(
    compact: Boolean,
    contentDescription: String = "返回主界面",
    onClick: () -> Unit
) {
    Box(
        modifier =
            Modifier
                .size(if (compact) 28.dp else 32.dp)
                .clip(CircleShape)
                .background(chatSurfaceHigh.copy(alpha = 0.88f))
                .border(1.dp, chatDivider.copy(alpha = 0.58f), CircleShape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(if (compact) 15.dp else 16.dp)
        )
    }
}

@Composable
private fun ProfileNameField(
    displayName: String,
    surfaceSpec: WatchSurfaceSpec,
    onDisplayNameChange: (String) -> Unit
) {
    val compact = surfaceSpec.compact
    Box(
        modifier =
            Modifier
                .fillMaxWidth(surfaceSpec.profileFieldWidth)
                .height(if (compact) 36.dp else 40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.82f))
                .border(1.dp, chatDivider.copy(alpha = 0.58f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle =
                TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = if (compact) 14.sp else 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
        )
        if (displayName.isBlank()) {
            Text(
                text = "SpotChat Watch",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = if (compact) 13.sp else 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProfileAboutField(
    about: String,
    surfaceSpec: WatchSurfaceSpec,
    onAboutChange: (String) -> Unit
) {
    val compact = surfaceSpec.compact
    Column(
        modifier = Modifier.fillMaxWidth(surfaceSpec.profileFieldWidth),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp)
    ) {
        Text(
            text = "关于",
            color = chatRowMuted,
            fontSize = if (compact) 9.sp else 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(if (compact) 34.dp else 38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(chatSurfaceHigh.copy(alpha = 0.82f))
                    .border(1.dp, chatDivider.copy(alpha = 0.58f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = about,
                onValueChange = onAboutChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle =
                    TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = if (compact) 12.sp else 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
            )
        }
    }
}

@Composable
private fun ProfileSectionLabel(
    text: String,
    compact: Boolean
) {
    Text(
        text = text,
        color = chatRowMuted,
        fontSize = if (compact) 10.sp else 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1
    )
}

@Composable
private fun ProfileIdentityPill(
    displayName: String,
    about: String,
    trustedPeerCount: Int,
    modifier: Modifier,
    compact: Boolean
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.82f))
                .border(1.dp, chatRose.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = if (compact) 5.dp else 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "$displayName · 可信 $trustedPeerCount",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = about.ifBlank { ProfileStore.DEFAULT_ABOUT },
            color = chatRowMuted,
            fontSize = if (compact) 9.sp else 10.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ProfileSecurityPanel(
    fingerprint: String,
    transportMode: TransportMode,
    trustState: String,
    trustedPeerCount: Int,
    surfaceSpec: WatchSurfaceSpec
) {
    val compact = surfaceSpec.compact
    Column(
        modifier =
            Modifier
                .fillMaxWidth(surfaceSpec.profileSummaryWidth)
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.82f))
                .border(1.dp, chatGreen.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 7.dp else 9.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 7.dp)
    ) {
        ProfileInfoRow(
            icon = Icons.Filled.Lock,
            label = "我的指纹",
            value = SpotChatCrypto.displayFingerprint(fingerprint),
            accent = chatGreen,
            compact = compact
        )
        ProfileInfoRow(
            icon = transportMode.icon,
            label = "当前传输",
            value = transportMode.label,
            accent = chatBlue,
            compact = compact
        )
        ProfileInfoRow(
            icon = Icons.Filled.VerifiedUser,
            label = "信任状态",
            value = "$trustState · $trustedPeerCount 台",
            accent = chatAmber,
            compact = compact
        )
    }
}

@Composable
private fun ProfilePrivacyPanel(
    blockedPeers: List<StoredTrustedPeer>,
    defaultDisappearingMode: DisappearingMessageMode,
    defaultReadReceiptsEnabled: Boolean,
    securityChangeAlertsEnabled: Boolean,
    surfaceSpec: WatchSurfaceSpec,
    onCycleDefaultDisappearingMode: () -> Unit,
    onToggleDefaultReadReceipts: () -> Unit,
    onToggleSecurityChangeAlerts: () -> Unit
) {
    val compact = surfaceSpec.compact
    val blockedSummary =
        if (blockedPeers.isEmpty()) {
            "没有阻止联系人"
        } else {
            blockedPeers
                .take(2)
                .joinToString(separator = "、") { peer -> peerDisplayName(peer) }
                .let { names ->
                    if (blockedPeers.size > 2) {
                        "$names 等 ${blockedPeers.size} 人"
                    } else {
                        names
                    }
                }
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth(surfaceSpec.profileSummaryWidth)
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.82f))
                .border(1.dp, chatRose.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 7.dp else 9.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 7.dp)
    ) {
        ProfileInfoRow(
            icon = Icons.Filled.PersonRemove,
            label = "已阻止",
            value = blockedPeers.size.coerceAtMost(99).toString(),
            accent = chatRose,
            compact = compact
        )
        ProfileInfoRow(
            icon = Icons.Filled.Lock,
            label = "屏蔽名单",
            value = blockedSummary,
            accent = if (blockedPeers.isEmpty()) chatGreen else chatRose,
            compact = compact
        )
        ProfileInfoRow(
            icon = Icons.Filled.AutoDelete,
            label = "默认限时",
            value = defaultDisappearingMode.label,
            accent = if (defaultDisappearingMode == DisappearingMessageMode.Off) chatRowMuted else chatGreen,
            compact = compact,
            onClick = onCycleDefaultDisappearingMode
        )
        ProfileInfoRow(
            icon = Icons.Filled.DoneAll,
            label = "默认回执",
            value = if (defaultReadReceiptsEnabled) "开启" else "关闭",
            accent = if (defaultReadReceiptsEnabled) chatGreen else chatAmber,
            compact = compact,
            onClick = onToggleDefaultReadReceipts
        )
        ProfileInfoRow(
            icon = Icons.Filled.VerifiedUser,
            label = "安全提醒",
            value = if (securityChangeAlertsEnabled) "开启" else "关闭",
            accent = if (securityChangeAlertsEnabled) chatGreen else chatAmber,
            compact = compact,
            onClick = onToggleSecurityChangeAlerts
        )
    }
}

@Composable
private fun ProfileTrustedDevicesSummaryRow(
    peerCount: Int,
    surfaceSpec: WatchSurfaceSpec
) {
    val compact = surfaceSpec.compact
    Row(
        modifier =
            Modifier
                .fillMaxWidth(surfaceSpec.profileSummaryWidth)
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.82f))
                .border(1.dp, chatGreen.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 7.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.VerifiedUser,
            contentDescription = "可信设备",
            tint = chatGreen,
            modifier = Modifier.size(if (compact) 14.dp else 16.dp)
        )
        Spacer(modifier = Modifier.width(if (compact) 7.dp else 9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "可信设备",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = if (compact) 11.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$peerCount 台可信设备",
                color = chatRowMuted,
                fontSize = if (compact) 9.sp else 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProfileTrustedPeerRow(
    peer: StoredTrustedPeer,
    surfaceSpec: WatchSurfaceSpec
) {
    val compact = surfaceSpec.compact
    Row(
        modifier =
            Modifier
                .fillMaxWidth(surfaceSpec.profileSummaryWidth)
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.82f))
                .border(1.dp, chatDivider.copy(alpha = 0.56f), RoundedCornerShape(8.dp))
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 7.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier =
                Modifier
                    .size(if (compact) 26.dp else 30.dp)
                    .clip(CircleShape)
                    .background(chatGreen.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.VerifiedUser,
                contentDescription = "可信设备",
                tint = chatGreen,
                modifier = Modifier.size(if (compact) 14.dp else 16.dp)
            )
        }
        Spacer(modifier = Modifier.width(if (compact) 7.dp else 9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = peerDisplayName(peer),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = if (compact) 11.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = trustedPeerSubtitle(peer),
                color = chatRowMuted,
                fontSize = if (compact) 9.sp else 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProfileEmptyTrustedPeer(
    compact: Boolean,
    surfaceSpec: WatchSurfaceSpec
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth(surfaceSpec.profileSummaryWidth)
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.82f))
                .border(1.dp, chatAmber.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 7.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = "等待配对",
            tint = chatAmber,
            modifier = Modifier.size(if (compact) 14.dp else 16.dp)
        )
        Spacer(modifier = Modifier.width(if (compact) 7.dp else 9.dp))
        Text(
            text = "暂无可信设备",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProfileTrustedPeerMoreRow(
    hiddenPeerCount: Int,
    surfaceSpec: WatchSurfaceSpec
) {
    val compact = surfaceSpec.compact
    Box(
        modifier =
            Modifier
                .fillMaxWidth(surfaceSpec.profileSummaryWidth)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.045f))
                .border(1.dp, chatDivider.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 6.dp else 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "还有 $hiddenPeerCount 台可信设备",
            color = chatRowMuted,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    compact: Boolean,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier =
            if (onClick == null) {
                Modifier
            } else {
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClick)
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accent,
            modifier = Modifier.size(if (compact) 13.dp else 15.dp)
        )
        Spacer(modifier = Modifier.width(if (compact) 6.dp else 7.dp))
        Text(
            text = label,
            color = chatRowMuted,
            fontSize = if (compact) 9.sp else 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.72f)
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AvatarBubble(
    avatar: DefaultAvatar,
    displayName: String,
    size: Dp,
    textSize: TextUnit,
    selected: Boolean,
    onClick: (() -> Unit)?
) {
    val baseModifier =
        Modifier
            .size(size)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.secondary else Color.Transparent,
                shape = CircleShape
            )
            .clip(CircleShape)
            .background(avatar.background)
    val clickableModifier =
        if (onClick == null) {
            baseModifier
        } else {
            baseModifier.clickable(onClick = onClick)
        }

    Box(
        modifier = clickableModifier,
        contentAlignment = Alignment.Center
    ) {
        if (avatar.imageRes == null) {
            Text(
                text = profileInitial(displayName),
                color = avatar.foreground,
                fontSize = textSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        } else {
            Image(
                painter = painterResource(avatar.imageRes),
                contentDescription = "头像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun WatchChatSurface(
    isRoundScreen: Boolean,
    conversation: ChatConversation,
    transportMode: TransportMode,
    trustState: String,
    fingerprint: String,
    pairingCode: String?,
    pendingPeer: TrustedPeer?,
    trustedPeerCount: Int,
    messages: List<ChatBubble>,
    pinnedMessage: ChatBubble?,
    wearEntryContext: WearEntryContext?,
    starredMessageIds: Set<String>,
    draft: ConversationDraft?,
    isBlocked: Boolean,
    isAnnouncementOnly: Boolean,
    onSelectMode: (TransportMode) -> Unit,
    onConfirmPairing: () -> Unit,
    onRejectPairing: () -> Unit,
    onSendQuickReply: (String) -> Unit,
    onOpenCustomMessageInput: () -> Unit,
    onOpenDraftInput: () -> Unit,
    onSendDraft: () -> Unit,
    onClearDraft: () -> Unit,
    onToggleVoiceRecording: () -> Unit,
    onCancelVoiceRecording: () -> Unit,
    isRecordingVoice: Boolean,
    recordingElapsedMillis: Long,
    voicePlaybackSpeed: VoicePlaybackSpeed,
    onOpenChatInfo: () -> Unit,
    onOpenMessageActions: (ChatBubble) -> Unit,
    onPerformWearEntryAction: (WearEntryContext) -> Unit,
    onDismissWearEntryContext: () -> Unit,
    onNavigateBack: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val compact = maxWidth < 260.dp || maxHeight < 260.dp
        val surfaceSpec = WatchSurfaceSpec(isRound = isRoundScreen, compact = compact)
        val quickReplyHeight = if (compact) 28.dp else 32.dp
        val visibleMessageCount = surfaceSpec.visibleMessageCount(pendingPeer != null)
        val messageScrollState = rememberScrollState()
        val accent = conversationAccentColor(conversation)

        WatchFrame(
            surfaceSpec = surfaceSpec,
            accent = accent,
            modifier =
                Modifier.padding(
                    start = surfaceSpec.chatHorizontalPadding,
                    end = surfaceSpec.chatHorizontalPadding
                )
        ) {
            ScreenScaffold(
                scrollState = messageScrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(),
                scrollIndicator = {}
            ) { scaffoldPadding ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(scaffoldPadding),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ChatHeroHeader(
                        conversation = conversation,
                        transportMode = transportMode,
                        trustState = trustState,
                        surfaceSpec = surfaceSpec,
                        onOpenChatInfo = onOpenChatInfo,
                        onNavigateBack = onNavigateBack
                    )

                    if (pendingPeer != null) {
                        PairingPrompt(
                            peer = pendingPeer,
                            surfaceSpec = surfaceSpec,
                            onConfirmPairing = onConfirmPairing,
                            onRejectPairing = onRejectPairing
                        )
                    }

                    wearEntryContext?.let { context ->
                        LaunchedEffect(context.id) {
                            delay(WEAR_ENTRY_CONTEXT_VISIBLE_MS)
                            onDismissWearEntryContext()
                        }
                        WearEntryContextBanner(
                            context = context,
                            compact = compact,
                            surfaceSpec = surfaceSpec,
                            onPerformAction = {
                                onPerformWearEntryAction(context)
                            },
                            onDismiss = onDismissWearEntryContext
                        )
                    }

                    pinnedMessage?.let { message ->
                        PinnedMessageBanner(
                            message = message,
                            compact = compact,
                            surfaceSpec = surfaceSpec,
                            accent = accent,
                            onClick = { onOpenMessageActions(message) }
                        )
                    }

                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(if (surfaceSpec.isRound) 0.86f else 0.94f)
                                .verticalScroll(messageScrollState),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)
                    ) {
                        messages.takeLast(visibleMessageCount).forEach { message ->
                            MessageCapsule(
                                message = message,
                                compact = compact,
                                accent = accent,
                                isStarred = message.stableStarId() in starredMessageIds,
                                voicePlaybackSpeed = voicePlaybackSpeed,
                                onClick =
                                    if (message.deliveryState == DeliveryState.System) {
                                        null
                                    } else {
                                        { onOpenMessageActions(message) }
                                    }
                            )
                        }
                    }

                    if (isBlocked) {
                        BlockedReplyNotice(
                            surfaceSpec = surfaceSpec,
                            height = quickReplyHeight
                        )
                    } else if (isAnnouncementOnly) {
                        AnnouncementOnlyNotice(
                            surfaceSpec = surfaceSpec,
                            height = quickReplyHeight
                        )
                    } else {
                        ReplyDock(
                            quickReplyHeight = quickReplyHeight,
                            surfaceSpec = surfaceSpec,
                            draft = draft,
                            onSendQuickReply = onSendQuickReply,
                            onOpenCustomMessageInput = onOpenCustomMessageInput,
                            onOpenDraftInput = onOpenDraftInput,
                            onSendDraft = onSendDraft,
                            onClearDraft = onClearDraft,
                            onToggleVoiceRecording = onToggleVoiceRecording,
                            onCancelVoiceRecording = onCancelVoiceRecording,
                            isRecordingVoice = isRecordingVoice,
                            recordingElapsedMillis = recordingElapsedMillis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatHeroHeader(
    conversation: ChatConversation,
    transportMode: TransportMode,
    trustState: String,
    surfaceSpec: WatchSurfaceSpec,
    onOpenChatInfo: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val compact = surfaceSpec.compact
    val accent = conversationAccentColor(conversation)
    Row(
        modifier =
            Modifier
                .padding(top = surfaceSpec.chatTopPadding)
                .fillMaxWidth(if (surfaceSpec.isRound) 0.86f else 0.94f)
                .height(if (compact) 48.dp else 54.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.88f))
                .border(1.dp, chatDivider.copy(alpha = 0.58f), RoundedCornerShape(8.dp))
                .padding(horizontal = if (compact) 5.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileBackButton(
            compact = compact,
            contentDescription = "返回会话列表",
            onClick = onNavigateBack
        )
        Spacer(modifier = Modifier.width(if (compact) 6.dp else 8.dp))
        Box(
            modifier =
                Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onOpenChatInfo)
        ) {
            ConversationAvatar(
                conversation = conversation,
                size = if (compact) 31.dp else 35.dp,
                accent = accent
            )
        }
        Spacer(modifier = Modifier.width(if (compact) 7.dp else 9.dp))
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenChatInfo),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = conversation.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = if (compact) 14.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(trustState = conversation.subtitle, size = if (compact) 6.dp else 7.dp)
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "${transportMode.label} · ${conversation.subtitle}",
                    color = chatRowMuted,
                    fontSize = if (compact) 9.sp else 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Composable
private fun WearEntryContextBanner(
    context: WearEntryContext,
    compact: Boolean,
    surfaceSpec: WatchSurfaceSpec,
    onPerformAction: () -> Unit,
    onDismiss: () -> Unit
) {
    val icon =
        when (context.action) {
            WearEntryAction.Voice -> Icons.Filled.Mic
            WearEntryAction.Reply,
            WearEntryAction.QuickReply -> Icons.Filled.Keyboard
            WearEntryAction.OpenChat ->
                if (context.source.contains("Tile")) {
                    Icons.AutoMirrored.Filled.Chat
                } else {
                    Icons.Filled.MarkChatUnread
                }
        }
    val sourceSummary =
        if (context.unreadCount > 0) {
            "来自${context.source} · ${context.unreadCount}条未读"
        } else {
            "来自${context.source}"
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth(if (surfaceSpec.isRound) 0.86f else 0.94f)
                .height(if (compact) 44.dp else 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(chatBlue.copy(alpha = 0.16f))
                .border(1.dp, chatBlue.copy(alpha = 0.42f), RoundedCornerShape(8.dp))
                .clickable(onClick = onPerformAction)
                .padding(horizontal = if (compact) 8.dp else 9.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = chatBlue,
            modifier = Modifier.size(if (compact) 14.dp else 16.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = sourceSummary,
                color = chatBlue,
                fontSize = if (compact) 8.sp else 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${context.action.label} · ${context.latestPreview}",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = context.action.ctaLabel,
            color = Color.Black,
            fontSize = if (compact) 9.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(chatBlue)
                    .padding(horizontal = if (compact) 6.dp else 7.dp, vertical = 4.dp)
        )
        Text(
            text = "X",
            color = chatRowMuted,
            fontSize = if (compact) 13.sp else 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier =
                Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 3.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun MessageCapsule(
    message: ChatBubble,
    compact: Boolean,
    accent: Color,
    isStarred: Boolean = false,
    voicePlaybackSpeed: VoicePlaybackSpeed = VoicePlaybackSpeed.Normal,
    onClick: (() -> Unit)? = null
) {
    val alignment = if (message.mine) Alignment.CenterEnd else Alignment.CenterStart
    val background =
        when {
            message.deliveryState == DeliveryState.System -> Color.White.copy(alpha = 0.06f)
            message.mine -> chatGreen.copy(alpha = 0.92f)
            else -> chatIncoming.copy(alpha = 0.98f)
        }
    val foreground =
        when {
            message.deliveryState == DeliveryState.System -> MaterialTheme.colorScheme.onBackground
            message.mine -> Color(0xFF001F1B)
            else -> MaterialTheme.colorScheme.onBackground
        }
    val bubbleWidth =
        when {
            message.deliveryState == DeliveryState.System -> 0.9f
            message.mine -> 0.8f
            else -> 0.86f
        }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(bubbleWidth)
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (message.deliveryState == DeliveryState.System) {
                            Modifier.background(Color.White.copy(alpha = 0.055f))
                        } else {
                            Modifier.background(
                                Brush.linearGradient(
                                    listOf(
                                        background,
                                        background.copy(alpha = if (message.mine) 0.82f else 0.9f)
                                    )
                                )
                            )
                        }
                    )
                    .border(
                        width = 1.dp,
                        color =
                            if (message.deliveryState == DeliveryState.System) {
                                accent.copy(alpha = 0.2f)
                            } else {
                                if (message.mine) {
                                    Color.White.copy(alpha = 0.12f)
                                } else {
                                    chatDivider.copy(alpha = 0.58f)
                                }
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                    .then(
                        if (onClick == null) {
                            Modifier
                        } else {
                            Modifier.clickable(onClick = onClick)
                        }
                    )
                    .padding(
                        horizontal = if (compact) 9.dp else 11.dp,
                        vertical = if (compact) 7.dp else 9.dp
                    ),
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 3.dp)
        ) {
            if (message.deliveryState != DeliveryState.System) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector =
                            if (message.mine) {
                                Icons.Filled.DoneAll
                            } else if (message.kind == ChatMessageKind.Voice) {
                                Icons.Filled.Mic
                            } else {
                                Icons.Filled.VerifiedUser
                            },
                        contentDescription =
                            if (message.mine) {
                                message.deliveryState.label
                            } else if (message.encrypted) {
                                "已加密"
                            } else {
                                "未加密"
                            },
                        tint = foreground.copy(alpha = 0.78f),
                        modifier = Modifier.size(if (compact) 10.dp else 11.dp)
                    )
                    Spacer(modifier = Modifier.width(if (compact) 3.dp else 4.dp))
                    Text(
                        text =
                            if (message.kind == ChatMessageKind.Voice) {
                                "语音 · ${voicePlaybackSpeed.label}"
                            } else if (message.mine) {
                                "${if (message.encrypted) "E2EE" else "明文"} · ${message.deliveryState.label}"
                            } else {
                                if (message.encrypted) "E2EE" else "明文"
                            },
                        color = foreground.copy(alpha = 0.78f),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (isStarred) {
                        Icon(
                            imageVector = Icons.Filled.StarRate,
                            contentDescription = "已星标",
                            tint = chatAmber,
                            modifier =
                                Modifier
                                    .padding(end = 4.dp)
                                    .size(if (compact) 10.dp else 11.dp)
                        )
                    }
                    Text(
                        text =
                            message.expiresAtEpochMillis
                                ?.let { expiresAt -> "剩 ${formatTimeRemaining(expiresAt)}" }
                                ?: message.timestamp,
                        color = foreground.copy(alpha = 0.62f),
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }
            }
            if (!message.mine && message.senderName != null) {
                Text(
                    text = message.senderName,
                    color = foreground.copy(alpha = 0.72f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (message.forwarded) {
                Text(
                    text = message.forwardLabel(),
                    color = foreground.copy(alpha = 0.72f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            message.quotedMessage?.let { quote ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = if (message.mine) 0.14f else 0.22f))
                            .border(1.dp, foreground.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                            .padding(horizontal = if (compact) 6.dp else 7.dp, vertical = if (compact) 4.dp else 5.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = quote.senderName,
                        color = foreground.copy(alpha = 0.76f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = quote.text,
                        color = foreground.copy(alpha = 0.72f),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text =
                    message.previewText(),
                color = foreground,
                fontSize = if (compact) 12.sp else 14.sp,
                lineHeight = if (compact) 15.sp else 17.sp,
                maxLines = if (message.deliveryState == DeliveryState.System) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                textAlign =
                    if (message.deliveryState == DeliveryState.System) {
                        TextAlign.Center
                    } else {
                        TextAlign.Start
                    },
                modifier =
                    if (message.deliveryState == DeliveryState.System) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier
                    }
            )
            if (message.reactions.isNotEmpty()) {
                Text(
                    text = reactionSummary(message),
                    color = foreground.copy(alpha = 0.82f),
                    fontSize = if (compact) 10.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PinnedMessageBanner(
    message: ChatBubble,
    compact: Boolean,
    surfaceSpec: WatchSurfaceSpec,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth(if (surfaceSpec.isRound) 0.86f else 0.94f)
                .height(if (compact) 34.dp else 38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.82f))
                .border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.PushPin,
            contentDescription = "置顶消息",
            tint = accent,
            modifier = Modifier.size(if (compact) 13.dp else 15.dp)
        )
        Spacer(modifier = Modifier.width(if (compact) 7.dp else 9.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "置顶消息",
                color = chatRowMuted,
                fontSize = if (compact) 8.sp else 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = message.previewText(),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReplyDock(
    quickReplyHeight: Dp,
    surfaceSpec: WatchSurfaceSpec,
    draft: ConversationDraft?,
    onSendQuickReply: (String) -> Unit,
    onOpenCustomMessageInput: () -> Unit,
    onOpenDraftInput: () -> Unit,
    onSendDraft: () -> Unit,
    onClearDraft: () -> Unit,
    onToggleVoiceRecording: () -> Unit,
    onCancelVoiceRecording: () -> Unit,
    isRecordingVoice: Boolean,
    recordingElapsedMillis: Long
) {
    val compact = surfaceSpec.compact
    Row(
        modifier =
            Modifier
                .padding(bottom = surfaceSpec.chatBottomPadding)
                .fillMaxWidth(if (surfaceSpec.isRound) 0.86f else 0.94f)
                .height(quickReplyHeight + 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.24f))
                .border(1.dp, chatDivider.copy(alpha = 0.62f), RoundedCornerShape(8.dp))
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isRecordingVoice) {
            VoiceCancelButton(
                height = quickReplyHeight - 6.dp,
                onClick = onCancelVoiceRecording
            )
            VoiceRecordingStatus(
                elapsedMillis = recordingElapsedMillis,
                height = quickReplyHeight - 6.dp,
                compact = compact,
                modifier = Modifier.weight(1f)
            )
            VoiceButton(
                height = quickReplyHeight - 6.dp,
                recording = true,
                onClick = onToggleVoiceRecording
            )
        } else {
            if (draft != null) {
                QuickReplyChip(
                    text = "草稿：${draft.text}",
                    height = quickReplyHeight - 6.dp,
                    modifier = Modifier.weight(1f),
                    onClick = onSendDraft
                )
            } else {
                customMessageQuickChoices.take(1).forEach { reply ->
                    QuickReplyChip(
                        text = reply,
                        height = quickReplyHeight - 6.dp,
                        modifier = Modifier.weight(1f),
                        onClick = { onSendQuickReply(reply) }
                    )
                }
            }
            InputButton(
                height = quickReplyHeight - 6.dp,
                onClick = onOpenCustomMessageInput
            )
            DraftButton(
                height = quickReplyHeight - 6.dp,
                hasDraft = draft != null,
                onClick = if (draft == null) onOpenDraftInput else onClearDraft
            )
            VoiceButton(
                height = quickReplyHeight - 6.dp,
                recording = false,
                onClick = onToggleVoiceRecording
            )
        }
    }
}

@Composable
private fun VoiceCancelButton(
    height: Dp,
    onClick: () -> Unit
) {
    Box(
        modifier =
            Modifier
                .size(height)
                .clip(CircleShape)
                .background(chatRose.copy(alpha = 0.92f))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = "取消录音",
            tint = Color.White,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun VoiceRecordingStatus(
    elapsedMillis: Long,
    height: Dp,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val maxDurationMillis = SpotChatVoiceRecorder.MAX_RECORDING_DURATION_MS
    val progress =
        (elapsedMillis.toFloat() / maxDurationMillis.toFloat())
            .coerceIn(0f, 1f)
    Row(
        modifier =
            modifier
                .height(height)
                .clip(RoundedCornerShape(8.dp))
                .background(chatGreen.copy(alpha = 0.16f))
                .border(1.dp, chatGreen.copy(alpha = 0.52f), RoundedCornerShape(8.dp))
                .padding(horizontal = if (compact) 5.dp else 7.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VoiceWaveBar(level = 0.46f + progress * 0.16f, compact = compact)
            VoiceWaveBar(level = 0.78f, compact = compact)
            VoiceWaveBar(level = 0.56f + progress * 0.24f, compact = compact)
            VoiceWaveBar(level = 0.9f - progress * 0.16f, compact = compact)
        }
        Text(
            text = "录音 ${formatRecordingDuration(elapsedMillis)}",
            color = Color.White,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${ceil((maxDurationMillis - elapsedMillis).coerceAtLeast(0L) / 1_000f).toInt()}秒",
            color = chatAmber,
            fontSize = if (compact) 9.sp else 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun VoiceWaveBar(
    level: Float,
    compact: Boolean
) {
    val baseHeight = if (compact) 12.dp else 14.dp
    Box(
        modifier =
            Modifier
                .width(2.dp)
                .height(baseHeight * level.coerceIn(0.32f, 1f))
                .clip(RoundedCornerShape(1.dp))
                .background(chatGreen)
    )
}

@Composable
private fun BlockedReplyNotice(
    surfaceSpec: WatchSurfaceSpec,
    height: Dp
) {
    Box(
        modifier =
            Modifier
                .padding(bottom = surfaceSpec.chatBottomPadding)
                .fillMaxWidth(if (surfaceSpec.isRound) 0.86f else 0.94f)
                .height(height + 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(chatRose.copy(alpha = 0.22f))
                .border(1.dp, chatRose.copy(alpha = 0.58f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "已阻止此联系人",
            color = Color.White,
            fontSize = if (surfaceSpec.compact) 11.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AnnouncementOnlyNotice(
    surfaceSpec: WatchSurfaceSpec,
    height: Dp
) {
    Row(
        modifier =
            Modifier
                .padding(bottom = surfaceSpec.chatBottomPadding)
                .fillMaxWidth(if (surfaceSpec.isRound) 0.86f else 0.94f)
                .height(height + 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(chatAmber.copy(alpha = 0.18f))
                .border(1.dp, chatAmber.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Group,
            contentDescription = "公告模式",
            tint = chatAmber,
            modifier = Modifier.size(if (surfaceSpec.compact) 13.dp else 15.dp)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = "公告模式",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = if (surfaceSpec.compact) 11.sp else 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun QuickReplyChip(
    text: String,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier =
            modifier
                .height(height)
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.9f))
                .border(1.dp, chatDivider.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun InputButton(
    height: Dp,
    onClick: () -> Unit
) {
    Box(
        modifier =
            Modifier
                .size(height)
                .clip(CircleShape)
                .background(chatBlue.copy(alpha = 0.88f))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Keyboard,
            contentDescription = "输入消息",
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun DraftButton(
    height: Dp,
    hasDraft: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier =
            Modifier
                .size(height)
                .clip(CircleShape)
                .background(if (hasDraft) chatAmber.copy(alpha = 0.9f) else chatSurfaceHigh.copy(alpha = 0.88f))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (hasDraft) Icons.Filled.Delete else Icons.Filled.Keyboard,
            contentDescription = if (hasDraft) "清除草稿" else "保存草稿",
            tint = if (hasDraft) Color(0xFF241600) else Color.White,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun VoiceButton(
    height: Dp,
    recording: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier =
            Modifier
                .size(height)
                .clip(CircleShape)
                .background(if (recording) chatRose.copy(alpha = 0.92f) else chatGreen.copy(alpha = 0.88f))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = if (recording) "停止录音" else "录音",
            tint = if (recording) Color.White else Color(0xFF001F1B),
            modifier = Modifier.size(16.dp)
        )
    }
}

private fun nowTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

private fun formatClockTime(epochMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))

@Composable
private fun statusColor(trustState: String): Color =
    when {
        trustState.contains("失败") || trustState.contains("拒绝") -> MaterialTheme.colorScheme.error
        trustState.contains("待") || trustState.contains("未") || trustState.contains("最近发现") -> chatAmber
        else -> chatGreen
    }

private fun peerAbout(peer: StoredTrustedPeer): String =
    peer.about.ifBlank { ProfileStore.DEFAULT_ABOUT }

private fun peerDisplayName(peer: StoredTrustedPeer): String =
    peer.alias.ifBlank { peer.deviceName }

private fun trustedPeerSubtitle(peer: StoredTrustedPeer): String {
    val trustedAt =
        if (peer.trustedAtEpochMillis <= 0L) {
            "未知时间"
        } else {
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(Date(peer.trustedAtEpochMillis))
        }
    val deviceNameLabel =
        if (peer.alias.isBlank()) {
            peer.deviceName
        } else {
            "设备 ${peer.deviceName}"
        }
    return "$deviceNameLabel · ${peerAbout(peer)} · $trustedAt"
}

private fun safetyPeerSummary(peer: StoredTrustedPeer): String =
    "${peerAbout(peer)} · 校验 ${peer.pairingCode}"

private fun avatarFor(avatarId: String): DefaultAvatar =
    defaultAvatars.firstOrNull { avatar -> avatar.id == avatarId } ?: defaultAvatars.first()

private fun conversationAccentColor(conversation: ChatConversation): Color {
    conversation.themeColor?.let { themeColor -> return themeColor }
    if (conversation.kind == ConversationKind.Group) {
        return chatGreen
    }

    val seed = conversation.peerFingerprint ?: conversation.id
    return conversationThemePalette[abs(seed.hashCode()) % conversationThemePalette.size]
}

private fun conversationPreview(
    conversation: ChatConversation,
    lastMessage: ChatBubble?,
    retryableCount: Int = 0,
    draft: ConversationDraft? = null,
    locked: Boolean = false
): String {
    val basePreview = lastMessage?.let { message ->
        val text = message.previewText()
        val replyPrefix = if (message.quotedMessage == null) "" else "回复 "
        val forwardPrefix = if (message.forwarded) "${message.forwardLabel()} " else ""
        when {
            message.mine -> "我：$forwardPrefix$replyPrefix$text"
            message.senderName != null -> "${message.senderName}：$forwardPrefix$replyPrefix$text"
            else -> "$forwardPrefix$replyPrefix$text"
        }
    } ?: conversation.subtitle
    val draftPreview = draft?.text?.takeIf { text -> text.isNotBlank() }?.let { text -> "草稿：$text" }
    val preview = if (locked) "已锁定聊天" else draftPreview ?: basePreview
    return if (retryableCount > 0) {
        "未发送 $retryableCount 条 · $preview"
    } else {
        preview
    }
}

private fun directConversationId(peerFingerprint: String): String =
    "$DIRECT_CONVERSATION_PREFIX$peerFingerprint"

private fun shortMessageId(messageId: String): String =
    if (messageId.length <= 12) {
        messageId
    } else {
        "${messageId.take(8)}...${messageId.takeLast(4)}"
    }

private fun String.shortReachabilityLabel(): String =
    when {
        contains("当前可发送") || contains("可发送") -> "可发"
        contains("最近发现") -> "最近"
        contains("等待") -> "等待"
        else -> take(4)
    }

private fun peerReachabilityShortLabel(reachability: String): String =
    when {
        reachability.contains("当前可发送") -> "在线"
        reachability.contains("最近发现") -> "最近"
        reachability.contains("等待") -> "离线"
        else -> reachability.take(4)
    }

private fun String.matchesGroupMemberFilter(filter: GroupMemberFilter): Boolean =
    when (filter) {
        GroupMemberFilter.All -> true
        GroupMemberFilter.Online -> contains("当前可发送")
        GroupMemberFilter.Recent -> contains("最近发现")
        GroupMemberFilter.Offline -> contains("等待")
    }

private fun List<String>.countForFilter(
    filter: GroupMemberFilter,
    totalCount: Int
): Int =
    when (filter) {
        GroupMemberFilter.All -> totalCount
        GroupMemberFilter.Online -> count { reachability -> reachability.matchesGroupMemberFilter(filter) }
        GroupMemberFilter.Recent -> count { reachability -> reachability.matchesGroupMemberFilter(filter) }
        GroupMemberFilter.Offline -> count { reachability -> reachability.matchesGroupMemberFilter(filter) }
    }

private fun disappearingActionLabel(mode: DisappearingMessageMode): String =
    if (mode == DisappearingMessageMode.Off) {
        "开启限时消息"
    } else {
        "限时消息${mode.label}"
    }

private fun String.toDisappearingMode(): DisappearingMessageMode =
    DisappearingMessageMode.entries.firstOrNull { mode -> mode.profileKey == this }
        ?: DisappearingMessageMode.Off

private fun disappearingSystemMessage(mode: DisappearingMessageMode): String =
    if (mode == DisappearingMessageMode.Off) {
        "已关闭限时消息，后续新消息会保留"
    } else {
        "已开启限时消息，后续新消息将在${mode.label}后自动删除"
    }

private fun disappearingPreviewLabel(mode: DisappearingMessageMode): String =
    if (mode == DisappearingMessageMode.Off) {
        "现有消息不变"
    } else {
        "旧消息不受影响"
    }

private fun conversationContentSummary(messages: List<ChatBubble>): ConversationContentSummary {
    val userMessages = messages.filter { message -> message.deliveryState != DeliveryState.System }
    return ConversationContentSummary(
        voiceCount = userMessages.count { message -> message.kind == ChatMessageKind.Voice },
        forwardedCount = userMessages.count { message -> message.forwarded },
        quotedCount = userMessages.count { message -> message.quotedMessage != null },
        reactedCount = userMessages.count { message -> message.reactions.isNotEmpty() },
        disappearingCount = userMessages.count { message -> message.expiresAtEpochMillis != null },
        linkCount = userMessages.count { message -> message.hasLinkPreview() }
    )
}

private fun contentMessages(messages: List<ChatBubble>): List<ChatBubble> =
    messages.filter { message ->
        message.deliveryState != DeliveryState.System &&
            (
                message.kind == ChatMessageKind.Voice ||
                    message.forwarded ||
                    message.quotedMessage != null ||
                    message.reactions.isNotEmpty() ||
                    message.expiresAtEpochMillis != null ||
                    message.hasLinkPreview()
            )
    }

private fun ChatBubble.matchesContentFilter(filter: ContentMessageFilter): Boolean =
    when (filter) {
        ContentMessageFilter.All -> true
        ContentMessageFilter.Voice -> kind == ChatMessageKind.Voice
        ContentMessageFilter.Forwarded -> forwarded
        ContentMessageFilter.Quoted -> quotedMessage != null
        ContentMessageFilter.Reacted -> reactions.isNotEmpty()
        ContentMessageFilter.Disappearing -> expiresAtEpochMillis != null
        ContentMessageFilter.Links -> hasLinkPreview()
    }

private fun ConversationContentSummary.countForFilter(
    filter: ContentMessageFilter,
    totalCount: Int
): Int =
    when (filter) {
        ContentMessageFilter.All -> totalCount
        ContentMessageFilter.Voice -> voiceCount
        ContentMessageFilter.Forwarded -> forwardedCount
        ContentMessageFilter.Quoted -> quotedCount
        ContentMessageFilter.Reacted -> reactedCount
        ContentMessageFilter.Disappearing -> disappearingCount
        ContentMessageFilter.Links -> linkCount
    }

private fun ConversationContentSummary.summaryLabel(): String {
    val parts =
        listOf(
            "语音 $voiceCount",
            "转发 $forwardedCount",
            "引用 $quotedCount",
            "回应 $reactedCount",
            "限时 $disappearingCount",
            "链接 $linkCount"
        )
    return parts.joinToString(separator = " · ")
}

private fun ChatBubble.hasLinkPreview(): Boolean {
    val text = previewText().lowercase(Locale.getDefault())
    return text.contains("https://") ||
        text.contains("http://") ||
        text.contains("www.")
}

private fun reactionLabel(reactionCode: String): String =
    reactionChoices.firstOrNull { reaction -> reaction.code == reactionCode }?.label ?: reactionCode

private fun reactionSummary(message: ChatBubble): String =
    message.reactions.values
        .groupingBy(::reactionLabel)
        .eachCount()
        .entries
        .joinToString(separator = " ") { (label, count) ->
            if (count > 1) "$label x$count" else label
        }

private fun reactionDetailsLabel(details: List<ReactionDetail>): String =
    details
        .distinct()
        .let { uniqueDetails ->
            val visibleDetails =
                uniqueDetails
                    .take(2)
                    .joinToString(separator = "、") { detail ->
                        "${detail.senderName}：${detail.reactionLabel}"
                    }
            if (uniqueDetails.size > 2) {
                "$visibleDetails 等 ${uniqueDetails.size} 人"
            } else {
                visibleDetails
            }
        }

private fun receiptProgressLabel(
    currentCount: Int,
    expectedCount: Int
): String =
    if (expectedCount <= 1) {
        if (currentCount > 0) "是" else "否"
    } else {
        "${currentCount.coerceAtMost(expectedCount)}/$expectedCount"
    }

private fun receiptNamesLabel(names: List<String>): String =
    names
        .distinct()
        .let { uniqueNames ->
            val visibleNames = uniqueNames.take(2).joinToString(separator = "、")
            if (uniqueNames.size > 2) {
                "$visibleNames 等 ${uniqueNames.size} 人"
            } else {
                visibleNames
            }
        }

private fun formatTimeRemaining(expiresAtEpochMillis: Long): String {
    val remainingMs = (expiresAtEpochMillis - System.currentTimeMillis()).coerceAtLeast(0L)
    val totalSeconds = (remainingMs / 1_000L).coerceAtLeast(1L)
    return when {
        totalSeconds < 60L -> "${totalSeconds}秒"
        totalSeconds < 3_600L -> "${totalSeconds / 60L}分"
        totalSeconds < 86_400L -> "${totalSeconds / 3_600L}小时"
        else -> "${totalSeconds / 86_400L}天"
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000L).coerceAtLeast(1L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(Locale.getDefault(), minutes, seconds)
}

private fun formatRecordingDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(Locale.getDefault(), minutes, seconds)
}

private fun ChatBubble.previewText(): String =
    if (kind == ChatMessageKind.Voice) {
        "语音 · ${formatDuration(voiceDurationMs ?: 0L)}"
    } else {
        text
    }

private fun ChatBubble.forwardPreviewText(): String =
    "${forwardLabel(prefix = "转发预览")}：${previewText()}"

private fun ChatBubble.copyText(): String =
    when (kind) {
        ChatMessageKind.Text -> {
            val quotePrefix =
                quotedMessage?.let { quote ->
                    "回复 ${quote.senderName}：${quote.text}\n"
                }.orEmpty()
            "$quotePrefix$text"
        }

        ChatMessageKind.Voice -> "语音消息 · ${formatDuration(voiceDurationMs ?: 0L)}"
    }.trim()

private fun ChatBubble.forwardText(): String =
    if (kind == ChatMessageKind.Voice) {
        "语音消息 · ${formatDuration(voiceDurationMs ?: 0L)}"
    } else {
        text
    }

private fun ChatBubble.normalizedForwardCount(): Int =
    if (forwarded) {
        forwardCount.coerceAtLeast(1)
    } else {
        0
    }

private fun ChatBubble.nextForwardCount(): Int =
    if (forwarded) {
        normalizedForwardCount() + 1
    } else {
        1
    }

private fun ChatPayload.normalizedForwardCount(): Int =
    if (forwarded) {
        forwardCount.coerceAtLeast(1)
    } else {
        0
    }

private fun forwardLabel(count: Int): String =
    when {
        count >= FREQUENTLY_FORWARDED_THRESHOLD -> "多次转发"
        count > 1 -> "已转发 $count 次"
        count == 1 -> "已转发"
        else -> "转发"
    }

private fun ChatBubble.forwardLabel(prefix: String? = null): String {
    val label = forwardLabel(normalizedForwardCount())
    return prefix?.let { "$it · $label" } ?: label
}

private fun messageInfoText(
    conversation: ChatConversation,
    message: ChatBubble,
    receiptSummary: MessageReceiptSummary?,
    reactionDetails: List<ReactionDetail>
): String =
    buildString {
        val sender =
            when {
                message.mine -> "我"
                message.senderName != null -> message.senderName
                conversation.kind == ConversationKind.Direct -> conversation.title
                else -> "SpotChat"
            }
        appendLine("SpotChat 消息信息")
        appendLine("聊天：${conversation.title}")
        appendLine("发送者：$sender")
        appendLine("时间：${message.timestamp}")
        appendLine("类型：${if (message.kind == ChatMessageKind.Voice) "语音" else "文字"}")
        appendLine("安全：${if (message.encrypted) "E2EE 加密" else "明文"}")
        appendLine("状态：${message.deliveryState.label}")
        appendLine("内容：${message.copyText()}")
        if (message.forwarded) {
            appendLine("标记：${message.forwardLabel()}")
        }
        message.quotedMessage?.let { quote ->
            appendLine("引用：${quote.senderName}：${quote.text}")
        }
        message.expiresAtEpochMillis?.let { expiresAt ->
            appendLine("限时：${formatTimeRemaining(expiresAt)} 后过期")
        }
        if (reactionDetails.isNotEmpty()) {
            appendLine("回应：${reactionDetails.joinToString(separator = "、") { detail -> "${detail.senderName} ${detail.reactionLabel}" }}")
        }
        receiptSummary?.let { summary ->
            appendLine("送达：${receiptProgressLabel(summary.deliveredCount, summary.expectedCount)}")
            appendLine("已读：${receiptProgressLabel(summary.readCount, summary.expectedCount)}")
            if (summary.deliveredNames.isNotEmpty()) {
                appendLine("送达者：${receiptNamesLabel(summary.deliveredNames)}")
            }
            if (summary.readNames.isNotEmpty()) {
                appendLine("已读者：${receiptNamesLabel(summary.readNames)}")
            }
            if (summary.undeliveredNames.isNotEmpty()) {
                appendLine("未送达：${receiptNamesLabel(summary.undeliveredNames)}")
            }
            if (summary.unreadNames.isNotEmpty()) {
                appendLine("未读：${receiptNamesLabel(summary.unreadNames)}")
            }
        }
    }.trim()

private fun ChatBubble.searchText(): String =
    buildList {
        add(previewText())
        senderName?.let(::add)
        quotedMessage?.let { quote ->
            add(quote.senderName)
            add(quote.text)
        }
        if (forwarded) {
            add("转发 ${forwardLabel()}")
        }
        add(deliveryState.label)
        add(if (encrypted) "加密" else "明文")
    }.joinToString(separator = " ")

private fun ChatBubble.toQuotedMessage(conversation: ChatConversation): QuotedMessage =
    QuotedMessage(
        messageId = messageId ?: UUID.randomUUID().toString(),
        senderName =
            when {
                mine -> "我"
                senderName != null -> senderName
                conversation.kind == ConversationKind.Direct -> conversation.title
                else -> "SpotChat"
            },
        text = previewText().trim().take(MAX_QUOTED_MESSAGE_CHARS)
    )

private fun deliveryStateRank(state: DeliveryState): Int =
    when (state) {
        DeliveryState.System -> 0
        DeliveryState.Failed -> 1
        DeliveryState.Waiting -> 2
        DeliveryState.Sending -> 3
        DeliveryState.Sent -> 4
        DeliveryState.Delivered -> 5
        DeliveryState.Read -> 6
        DeliveryState.Received -> 6
    }

private fun DeliveryState.isReceiptState(): Boolean =
    this == DeliveryState.Sent || this == DeliveryState.Delivered || this == DeliveryState.Read

private fun encodeChatPayload(
    conversation: ChatConversation,
    text: String,
    quotedMessage: QuotedMessage?,
    forwarded: Boolean,
    forwardCount: Int
): String =
    ChatPayloadCodec.encode(
        ChatPayload(
            kind =
                if (conversation.kind == ConversationKind.Group) {
                    CHAT_PAYLOAD_KIND_GROUP
                } else {
                    CHAT_PAYLOAD_KIND_DIRECT
                },
            text = text,
            groupId =
                if (conversation.kind == ConversationKind.Group) {
                    conversation.id
                } else {
                    null
                },
            groupName =
                if (conversation.kind == ConversationKind.Group) {
                    conversation.title
                } else {
                    null
                },
            quote = quotedMessage,
            forwarded = forwarded,
            forwardCount =
                if (forwarded) {
                    forwardCount.coerceAtLeast(1)
                } else {
                    0
                }
        )
    )

private fun decodeChatPayload(text: String): ChatPayload =
    ChatPayloadCodec.decodeOrLegacy(text)

private fun profileInitial(displayName: String): String {
    val trimmedName = displayName.trim()
    if (trimmedName.isEmpty()) {
        return "S"
    }
    return String(Character.toChars(trimmedName.codePointAt(0))).uppercase(Locale.getDefault())
}

private fun PeerHello.lanPort(): Int? =
    transports.firstNotNullOfOrNull { hint ->
        hint.removePrefix("lan:").takeIf { it != hint }?.toIntOrNull()
    }

private fun Throwable?.readableMessage(fallback: String): String =
    this?.message?.takeIf { it.isNotBlank() } ?: fallback
