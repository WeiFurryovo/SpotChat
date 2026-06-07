package com.weifurry.spotchat.presentation

import android.Manifest
import android.app.Activity
import android.app.RemoteInput
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.runtime.Composable
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
import com.weifurry.spotchat.domain.DuplicateMessageException
import com.weifurry.spotchat.domain.ProfileSettings
import com.weifurry.spotchat.domain.ProfileStore
import com.weifurry.spotchat.domain.StoredTrustedPeer
import com.weifurry.spotchat.domain.SpotChatEngine
import com.weifurry.spotchat.domain.TrustedPeer
import com.weifurry.spotchat.domain.TrustedPeerStore
import com.weifurry.spotchat.notifications.SpotChatNotificationIntents
import com.weifurry.spotchat.notifications.SpotChatNotifier
import com.weifurry.spotchat.presentation.theme.SpotChatTheme
import com.weifurry.spotchat.protocol.ChatCodec
import com.weifurry.spotchat.protocol.PacketKind
import com.weifurry.spotchat.protocol.PeerHello
import com.weifurry.spotchat.protocol.WirePacket
import com.weifurry.spotchat.transport.BluetoothChatTransport
import com.weifurry.spotchat.transport.LanChatTransport
import com.weifurry.spotchat.transport.SpotChatTransport
import com.weifurry.spotchat.transport.TransportEvent
import com.weifurry.spotchat.transport.TransportKind
import com.weifurry.spotchat.transport.TransportPeer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private enum class TransportMode(
    val label: String,
    val icon: ImageVector
) {
    Lan("局域网", Icons.Filled.Lan),
    Bluetooth("蓝牙", Icons.Filled.Bluetooth)
}

private enum class DeliveryState(
    val label: String
) {
    Received("收到"),
    Waiting("待确认"),
    Sending("发送中"),
    Sent("已发送"),
    Delivered("已送达"),
    Failed("失败"),
    System("状态")
}

private enum class AppSurface {
    ConversationList,
    Chat,
    ChatInfo,
    MessageActions,
    Profile
}

private enum class ConversationKind(
    val label: String
) {
    Direct("私聊"),
    Group("群聊")
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
    val messageId: String? = null,
    val deliveryState: DeliveryState = DeliveryState.Received
)

private data class ChatConversation(
    val id: String,
    val kind: ConversationKind,
    val title: String,
    val subtitle: String,
    val peerFingerprint: String? = null,
    val memberFingerprints: List<String> = emptyList()
)

private data class OutgoingMessageRef(
    val conversationId: String,
    val displayMessageId: String,
    val expectedDeliveries: Int
)

private data class PendingOutboundMessage(
    val conversationId: String,
    val text: String,
    val displayMessageId: String,
    val targetFingerprints: List<String>
)

@Serializable
private data class ChatPayload(
    val version: Int = 1,
    val kind: String = CHAT_PAYLOAD_KIND_DIRECT,
    val text: String,
    val groupId: String? = null,
    val groupName: String? = null
)

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
private const val MAX_CUSTOM_MESSAGE_CHARS = 280
private const val NEARBY_GROUP_CONVERSATION_ID = "group:nearby"
private const val NEARBY_GROUP_TITLE = "附近群聊"
private const val DIRECT_CONVERSATION_PREFIX = "direct:"
private const val CHAT_PAYLOAD_KIND_DIRECT = "direct"
private const val CHAT_PAYLOAD_KIND_GROUP = "group"
private val customMessageQuickChoices = arrayOf("收到", "马上到", "稍后联系")
private val chatPayloadJson =
    Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
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

@Composable
internal fun SpotChatApp(
    notificationIntent: Intent? = null,
    onNotificationIntentHandled: (Intent) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val defaultDeviceName =
        remember {
            listOf(Build.MANUFACTURER, Build.MODEL)
                .joinToString(separator = " ")
                .trim()
                .ifBlank { "SpotChat Watch" }
        }
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
    val engine =
        remember(identity, deviceName) {
            SpotChatEngine(deviceName, identity)
        }
    val trustedPeerStore =
        remember(context) {
            TrustedPeerStore(context)
        }
    val localFingerprint =
        remember(identity) {
            SpotChatCrypto.fingerprint(identity.public)
        }
    val lanTransport =
        remember(context, deviceName) {
            LanChatTransport(deviceName, context)
        }
    val bluetoothTransport =
        remember(context) {
            BluetoothChatTransport(context)
        }
    val notifier =
        remember(context) {
            SpotChatNotifier(context)
        }
    val coroutineScope = rememberCoroutineScope()
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
                                text = "与 ${peer.deviceName} 的私聊已准备好",
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
    val outgoingMessages = remember { mutableStateMapOf<String, OutgoingMessageRef>() }
    val deliveredCounts = remember { mutableStateMapOf<String, Int>() }
    val pendingOutboundMessages = remember { mutableStateMapOf<String, PendingOutboundMessage>() }
    var activeConversationId by remember { mutableStateOf(NEARBY_GROUP_CONVERSATION_ID) }
    if (conversationMessages[activeConversationId] == null) {
        activeConversationId = NEARBY_GROUP_CONVERSATION_ID
    }
    var transportMode by remember { mutableStateOf(TransportMode.Lan) }
    var trustState by remember { mutableStateOf("未配对") }
    var activePeer by remember { mutableStateOf<TransportPeer?>(null) }
    var activePeerFingerprint by remember { mutableStateOf<String?>(null) }
    var pendingPeer by remember { mutableStateOf<TrustedPeer?>(null) }
    var pairingCode by remember { mutableStateOf<String?>(null) }
    var appSurface by remember { mutableStateOf(AppSurface.ConversationList) }
    var selectedActionMessage by remember { mutableStateOf<ChatBubble?>(null) }
    val greetedPeers = remember { mutableSetOf<String>() }
    val knownPeersByFingerprint = remember { mutableStateMapOf<String, TransportPeer>() }

    fun hasBluetoothRuntimePermissions(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
            )

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

    LaunchedEffect(Unit) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun currentTransport(): SpotChatTransport =
        if (transportMode == TransportMode.Lan) {
            lanTransport
        } else {
            bluetoothTransport
        }

    fun transportHints(): List<String> =
        if (transportMode == TransportMode.Lan) {
            listOf("lan:${LanChatTransport.DEFAULT_SERVICE_PORT}")
        } else {
            listOf("bluetooth:${BluetoothChatTransport.SPOTCHAT_SERVICE_UUID}")
        }

    suspend fun sendPacket(
        transport: SpotChatTransport,
        peer: TransportPeer,
        packet: WirePacket
    ) {
        transport.send(peer, ChatCodec.encode(packet))
    }

    fun messagesForConversation(conversationId: String): List<ChatBubble> =
        conversationMessages[conversationId].orEmpty()

    fun conversationById(conversationId: String): ChatConversation? {
        if (conversationId == NEARBY_GROUP_CONVERSATION_ID) {
            return ChatConversation(
                id = NEARBY_GROUP_CONVERSATION_ID,
                kind = ConversationKind.Group,
                title = NEARBY_GROUP_TITLE,
                subtitle =
                    if (trustedPeers.isEmpty()) {
                        "群聊 · 等待成员"
                    } else {
                        "群聊 · ${trustedPeers.size} 位成员"
                    },
                memberFingerprints = trustedPeers.map { peer -> peer.fingerprint }
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
            title = peer.deviceName,
            subtitle =
                if (knownPeersByFingerprint[peer.fingerprint] == null) {
                    "私聊 · 待发现"
                } else {
                    "私聊 · 可发送"
                },
            peerFingerprint = peer.fingerprint,
            memberFingerprints = listOf(peer.fingerprint)
        )
    }

    fun clearConversationAlerts(conversationId: String) {
        unreadCounts[conversationId] = 0
        notifier.clearConversation(conversationId)
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
            messageText = message.text,
            unreadCount = unreadCounts[conversationId] ?: 1
        )
    }

    fun appendMessage(
        conversationId: String,
        message: ChatBubble
    ) {
        conversationMessages[conversationId] = messagesForConversation(conversationId) + message
        if (
            !message.mine &&
            message.deliveryState != DeliveryState.System &&
            (appSurface != AppSurface.Chat || activeConversationId != conversationId)
        ) {
            unreadCounts[conversationId] = (unreadCounts[conversationId] ?: 0) + 1
            notifyIncomingMessage(conversationId, message)
        }
    }

    suspend fun sendHello(
        transport: SpotChatTransport,
        peer: TransportPeer
    ) {
        runCatching {
            sendPacket(transport, peer, engine.helloPacket(transportHints()))
        }.onFailure { error ->
            trustState = "握手失败"
            appendMessage(
                NEARBY_GROUP_CONVERSATION_ID,
                ChatBubble(
                    text = error.readableMessage("无法发送配对信息"),
                    mine = false,
                    encrypted = false,
                    timestamp = nowTime()
                )
            )
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

    fun ensureDirectConversation(storedPeer: StoredTrustedPeer): String {
        val conversationId = directConversationId(storedPeer.fingerprint)
        if (conversationMessages[conversationId] == null) {
            conversationMessages[conversationId] =
                listOf(
                    ChatBubble(
                        text = "与 ${storedPeer.deviceName} 的私聊已准备好",
                        mine = false,
                        encrypted = true,
                        timestamp = nowTime(),
                        deliveryState = DeliveryState.System
                    )
                )
        }
        return conversationId
    }

    fun updateMessageState(
        messageId: String,
        deliveryState: DeliveryState
    ) {
        val outboundMessage = outgoingMessages[messageId]
        if (
            deliveryState == DeliveryState.Delivered &&
            outboundMessage != null &&
            outboundMessage.expectedDeliveries > 1
        ) {
            val deliveredCount = (deliveredCounts[outboundMessage.displayMessageId] ?: 0) + 1
            deliveredCounts[outboundMessage.displayMessageId] = deliveredCount
            if (deliveredCount < outboundMessage.expectedDeliveries) {
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
                conversationMessages[conversationId] =
                    messages.toMutableList().also { updatedMessages ->
                        updatedMessages[index] = updatedMessages[index].copy(deliveryState = deliveryState)
                    }
                return
            }
        }
    }

    fun trustedPeer(fingerprint: String): StoredTrustedPeer? =
        trustedPeers.firstOrNull { peer -> peer.fingerprint == fingerprint }

    fun trustedPeer(peer: TrustedPeer): StoredTrustedPeer? =
        trustedPeers.firstOrNull { storedPeer ->
            storedPeer.fingerprint == peer.fingerprint || storedPeer.publicKey == peer.publicKey
        }

    fun removeTrustedPeer(storedPeer: StoredTrustedPeer) {
        trustedPeers.removeAll { existing ->
            existing.fingerprint == storedPeer.fingerprint || existing.publicKey == storedPeer.publicKey
        }
    }

    fun rememberPeerRoute(
        fingerprint: String,
        peer: TransportPeer
    ) {
        knownPeersByFingerprint[fingerprint] = peer
    }

    fun routeForPeer(fingerprint: String): TransportPeer? =
        knownPeersByFingerprint[fingerprint]

    fun hasLanConnection(): Boolean {
        val connectivityManager =
            context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun conversations(): List<ChatConversation> =
        buildList {
            add(
                ChatConversation(
                    id = NEARBY_GROUP_CONVERSATION_ID,
                    kind = ConversationKind.Group,
                    title = NEARBY_GROUP_TITLE,
                    subtitle =
                        if (trustedPeers.isEmpty()) {
                            "群聊 · 等待成员"
                        } else {
                            "群聊 · ${trustedPeers.size} 位成员"
                        },
                    memberFingerprints = trustedPeers.map { peer -> peer.fingerprint }
                )
            )
            trustedPeers.forEach { peer ->
                add(
                    ChatConversation(
                        id = directConversationId(peer.fingerprint),
                        kind = ConversationKind.Direct,
                        title = peer.deviceName,
                        subtitle =
                            if (routeForPeer(peer.fingerprint) == null) {
                                "私聊 · 待发现"
                            } else {
                                "私聊 · 可发送"
                            },
                        peerFingerprint = peer.fingerprint,
                        memberFingerprints = listOf(peer.fingerprint)
                    )
                )
            }
        }

    fun activeConversation(): ChatConversation =
        conversations().firstOrNull { conversation -> conversation.id == activeConversationId }
            ?: conversations().first()

    fun openConversation(conversation: ChatConversation) {
        activeConversationId = conversation.id
        clearConversationAlerts(conversation.id)
        selectedActionMessage = null
        appSurface = AppSurface.Chat
    }

    suspend fun sendEncryptedAck(
        transport: SpotChatTransport,
        peer: TransportPeer,
        senderFingerprint: String,
        messageId: String,
        failureState: String
    ) {
        runCatching {
            sendPacket(
                transport = transport,
                peer = peer,
                packet =
                    engine.encryptAckForPeer(
                        peerFingerprint = senderFingerprint,
                        deliveredMessageId = messageId
                    )
            )
        }.onFailure {
            trustState = failureState
        }
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

    suspend fun handleTransportEvent(
        transport: SpotChatTransport,
        event: TransportEvent
    ) {
        when (event) {
            is TransportEvent.StateChanged -> {
                trustState = event.message
            }

            is TransportEvent.PeerFound -> {
                activePeer = event.peer
                trustState = "发现 ${event.peer.name}"
                if (greetedPeers.add(event.peer.id)) {
                    sendHello(transport, event.peer)
                }
            }

            is TransportEvent.FrameReceived -> {
                val packet =
                    runCatching { ChatCodec.decode(event.frame) }
                        .getOrElse { error ->
                            appendMessage(
                                activeConversationId,
                                ChatBubble(
                                    text = error.readableMessage("收到无法解析的数据"),
                                    mine = false,
                                    encrypted = false,
                                    timestamp = nowTime()
                                )
                            )
                            return
                        }
                when (packet.kind) {
                    PacketKind.HELLO -> {
                        val hello = packet.hello ?: return
                        val openedPeer = engine.openSession(hello)
                        val mergedPeer = mergePeerWithHello(event.peer, hello)
                        activePeer = mergedPeer
                        rememberPeerRoute(openedPeer.fingerprint, mergedPeer)
                        pairingCode = openedPeer.pairingCode
                        val storedPeer = trustedPeer(openedPeer)
                        if (storedPeer != null && storedPeer.publicKey == openedPeer.publicKey) {
                            val refreshedPeer = trustedPeerStore.trust(openedPeer)
                            removeTrustedPeer(refreshedPeer)
                            trustedPeers.add(0, refreshedPeer)
                            ensureDirectConversation(refreshedPeer)
                            pendingPeer = null
                            activePeerFingerprint = openedPeer.fingerprint
                            trustState = "已信任 ${refreshedPeer.deviceName}"
                        } else {
                            activePeerFingerprint = null
                            pendingPeer = openedPeer
                            trustState = "待确认 ${openedPeer.deviceName}"
                            appendSystemMessage(
                                text = "发现 ${openedPeer.deviceName}，请核对校验码",
                                encrypted = true
                            )
                        }
                        if (greetedPeers.add(mergedPeer.id)) {
                            sendHello(transport, mergedPeer)
                        }
                    }

                    PacketKind.ENCRYPTED_MESSAGE -> {
                        val encryptedMessage = packet.encryptedMessage ?: return
                        val storedSender = trustedPeer(encryptedMessage.senderFingerprint)
                        if (storedSender == null) {
                            trustState = "拦截未确认消息"
                            appendSystemMessage(
                                text = "未确认设备发来的消息已拦截",
                                encrypted = false
                            )
                            return
                        }
                        runCatching { engine.decryptText(encryptedMessage) }
                            .onSuccess { plain ->
                                val payload = decodeChatPayload(plain.text)
                                val conversationId =
                                    if (payload.kind == CHAT_PAYLOAD_KIND_GROUP) {
                                        NEARBY_GROUP_CONVERSATION_ID
                                    } else {
                                        directConversationId(plain.senderFingerprint)
                                    }
                                appendMessage(
                                    conversationId,
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
                                        messageId = plain.messageId,
                                        deliveryState = DeliveryState.Received
                                    )
                                )
                                trustState = "收到加密消息"
                                rememberPeerRoute(plain.senderFingerprint, event.peer)
                                val replyPeer = routeForPeer(plain.senderFingerprint) ?: event.peer
                                sendEncryptedAck(
                                    transport = transport,
                                    peer = replyPeer,
                                    senderFingerprint = plain.senderFingerprint,
                                    messageId = plain.messageId,
                                    failureState = "回执发送失败"
                                )
                            }
                            .onFailure { error ->
                                if (error is DuplicateMessageException) {
                                    trustState = "重复消息已忽略"
                                    rememberPeerRoute(error.senderFingerprint, event.peer)
                                    val replyPeer = routeForPeer(error.senderFingerprint) ?: event.peer
                                    sendEncryptedAck(
                                        transport = transport,
                                        peer = replyPeer,
                                        senderFingerprint = error.senderFingerprint,
                                        messageId = error.messageId,
                                        failureState = "重复回执发送失败"
                                    )
                                } else {
                                    appendMessage(
                                        activeConversationId,
                                        ChatBubble(
                                            text = error.readableMessage("无法解密消息"),
                                            mine = false,
                                            encrypted = false,
                                            timestamp = nowTime()
                                        )
                                    )
                                    trustState = "解密失败"
                                }
                            }
                    }

                    PacketKind.ENCRYPTED_ACK -> {
                        val encryptedAck = packet.encryptedMessage ?: return
                        if (trustedPeer(encryptedAck.senderFingerprint) == null) {
                            trustState = "拦截未认证回执"
                            return
                        }
                        rememberPeerRoute(encryptedAck.senderFingerprint, event.peer)
                        runCatching { engine.decryptAck(encryptedAck) }
                            .onSuccess { ack ->
                                updateMessageState(ack.messageId, DeliveryState.Delivered)
                                trustState = "对方已收到"
                            }
                            .onFailure {
                                trustState = "回执验证失败"
                            }
                    }

                    PacketKind.ACK -> {
                        trustState = "忽略未认证回执"
                    }
                }
            }

            is TransportEvent.Failure -> {
                trustState = event.message
                appendMessage(
                    activeConversationId,
                    ChatBubble(
                        text = event.cause.readableMessage(event.message),
                        mine = false,
                        encrypted = false,
                        timestamp = nowTime()
                    )
                )
            }
        }
    }

    fun confirmPairing() {
        val peer = pendingPeer ?: return
        val storedPeer = trustedPeerStore.trust(peer)
        removeTrustedPeer(storedPeer)
        trustedPeers.add(0, storedPeer)
        pendingPeer = null
        activePeerFingerprint = storedPeer.fingerprint
        pairingCode = storedPeer.pairingCode
        trustState = "已信任 ${storedPeer.deviceName}"
        val directConversationKey = ensureDirectConversation(storedPeer)
        appendSystemMessage(
            text = "已信任 ${storedPeer.deviceName}，可以开始加密聊天",
            encrypted = true,
            conversationId = directConversationKey
        )
    }

    fun rejectPairing() {
        val peer = pendingPeer ?: return
        trustedPeerStore.forget(peer.fingerprint, peer.publicKey)
        trustedPeers.removeAll { storedPeer ->
            storedPeer.fingerprint == peer.fingerprint || storedPeer.publicKey == peer.publicKey
        }
        pendingPeer = null
        activePeerFingerprint = null
        pairingCode = null
        trustState = "已拒绝 ${peer.deviceName}"
        appendSystemMessage(
            text = "已拒绝 ${peer.deviceName} 的配对请求",
            encrypted = false
        )
    }

    fun selectMode(mode: TransportMode) {
        if (mode == TransportMode.Bluetooth && !hasBluetoothRuntimePermissions()) {
            trustState = "蓝牙需要授权"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    )
                )
            }
            return
        }
        transportMode = mode
        trustState = if (mode == TransportMode.Lan) "局域网发现中" else "蓝牙待连接"
    }

    fun updateProfile(updated: ProfileSettings) {
        profile = profileStore.save(updated)
    }

    fun targetsForConversation(conversation: ChatConversation): List<Pair<String, TransportPeer>> =
        conversation.memberFingerprints.mapNotNull { fingerprint ->
            val peer = routeForPeer(fingerprint)
            val storedPeer = trustedPeer(fingerprint)
            if (peer == null || storedPeer == null) {
                null
            } else {
                fingerprint to peer
            }
        }

    fun sendPreparedMessage(
        conversation: ChatConversation,
        text: String,
        displayMessageId: String,
        targets: List<Pair<String, TransportPeer>>,
        requeueOnFailure: Boolean
    ) {
        trustState = "正在加密发送"
        coroutineScope.launch {
            var sentCount = 0
            var failedCount = 0
            targets.forEach { (peerFingerprint, peer) ->
                val payload =
                    encodeChatPayload(
                        conversation = conversation,
                        text = text
                    )
                val packetResult = runCatching { engine.encryptTextForPeer(peerFingerprint, payload) }
                if (packetResult.isFailure) {
                    failedCount += 1
                    appendMessage(
                        conversation.id,
                        ChatBubble(
                            text = packetResult.exceptionOrNull().readableMessage("无法加密消息"),
                            mine = false,
                            encrypted = false,
                            timestamp = nowTime()
                        )
                    )
                    return@forEach
                }
                val packet = packetResult.getOrNull() ?: return@forEach
                val packetMessageId = packet.encryptedMessage?.messageId ?: return@forEach
                outgoingMessages[packetMessageId] =
                    OutgoingMessageRef(
                        conversationId = conversation.id,
                        displayMessageId = displayMessageId,
                        expectedDeliveries = targets.size
                    )

                runCatching {
                    sendPacket(currentTransport(), peer, packet)
                }.onSuccess {
                    sentCount += 1
                }.onFailure { error ->
                    failedCount += 1
                    appendMessage(
                        conversation.id,
                        ChatBubble(
                            text = error.readableMessage("无法发送消息"),
                            mine = false,
                            encrypted = false,
                            timestamp = nowTime()
                        )
                    )
                }
            }

            when {
                sentCount > 0 && failedCount == 0 -> {
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
                    pendingOutboundMessages.remove(displayMessageId)
                    updateMessageState(displayMessageId, DeliveryState.Sent)
                    trustState = "部分成员已发送"
                }

                requeueOnFailure -> {
                    pendingOutboundMessages[displayMessageId] =
                        PendingOutboundMessage(
                            conversationId = conversation.id,
                            text = text,
                            displayMessageId = displayMessageId,
                            targetFingerprints = conversation.memberFingerprints
                        )
                    updateMessageState(displayMessageId, DeliveryState.Waiting)
                    trustState = "等待对方上线"
                }

                else -> {
                    updateMessageState(displayMessageId, DeliveryState.Failed)
                    trustState = "发送失败"
                }
            }
        }
    }

    fun trySendPendingOutboundMessage(queuedReply: PendingOutboundMessage) {
        val conversation = conversationById(queuedReply.conversationId) ?: return
        val targets = targetsForConversation(conversation)
        if (targets.isEmpty()) {
            return
        }
        updateMessageState(queuedReply.displayMessageId, DeliveryState.Sending)
        sendPreparedMessage(
            conversation = conversation,
            text = queuedReply.text,
            displayMessageId = queuedReply.displayMessageId,
            targets = targets,
            requeueOnFailure = true
        )
    }

    fun sendMessageToConversation(
        conversation: ChatConversation,
        text: String,
        requeueWhenOffline: Boolean = true
    ) {
        val cleanText = text.trim().take(MAX_CUSTOM_MESSAGE_CHARS)
        if (cleanText.isBlank()) {
            return
        }

        val displayMessageId = UUID.randomUUID().toString()
        if (transportMode == TransportMode.Lan && !hasLanConnection()) {
            trustState = "局域网未连接"
            appendMessage(
                conversation.id,
                ChatBubble(
                    text = cleanText,
                    mine = true,
                    encrypted = true,
                    timestamp = nowTime(),
                    messageId = displayMessageId,
                    deliveryState =
                        if (requeueWhenOffline && conversation.memberFingerprints.isNotEmpty()) {
                            DeliveryState.Waiting
                        } else {
                            DeliveryState.Failed
                        }
                )
            )
            if (requeueWhenOffline && conversation.memberFingerprints.isNotEmpty()) {
                pendingOutboundMessages[displayMessageId] =
                    PendingOutboundMessage(
                        conversationId = conversation.id,
                        text = cleanText,
                        displayMessageId = displayMessageId,
                        targetFingerprints = conversation.memberFingerprints
                    )
                trustState = "等待网络恢复"
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
            trustState = "成员未在线"
            appendMessage(
                conversation.id,
                ChatBubble(
                    text = cleanText,
                    mine = true,
                    encrypted = true,
                    timestamp = nowTime(),
                    messageId = displayMessageId,
                    deliveryState = DeliveryState.Waiting
                )
            )
            if (requeueWhenOffline) {
                pendingOutboundMessages[displayMessageId] =
                    PendingOutboundMessage(
                        conversationId = conversation.id,
                        text = cleanText,
                        displayMessageId = displayMessageId,
                        targetFingerprints = conversation.memberFingerprints
                    )
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
                deliveryState = DeliveryState.Sending
            )
        )

        sendPreparedMessage(
            conversation = conversation,
            text = cleanText,
            displayMessageId = displayMessageId,
            targets = targets,
            requeueOnFailure = requeueWhenOffline
        )
    }

    fun sendQuickReply(text: String) {
        sendMessageToConversation(activeConversation(), text)
    }

    LaunchedEffect(pendingOutboundMessages.size, knownPeersByFingerprint.size) {
        pendingOutboundMessages.values
            .toList()
            .filter { queuedReply ->
                queuedReply.targetFingerprints.any { fingerprint ->
                    knownPeersByFingerprint[fingerprint] != null
                }
            }
            .forEach { queuedReply -> trySendPendingOutboundMessage(queuedReply) }
    }

    fun handleNotificationIntent(intent: Intent) {
        if (!notifier.isTrustedNotificationIntent(intent)) {
            return
        }
        val conversationId =
            intent.getStringExtra(SpotChatNotificationIntents.EXTRA_CONVERSATION_ID) ?: return
        val conversation = conversationById(conversationId) ?: return
        clearConversationAlerts(conversation.id)
        openConversation(conversation)

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
        }
    }

    LaunchedEffect(notificationIntent, trustedPeers.size) {
        val intent = notificationIntent ?: return@LaunchedEffect
        handleNotificationIntent(intent)
        onNotificationIntentHandled(intent)
    }

    val messageInputLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
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

            if (message.isNotBlank()) {
                sendQuickReply(message)
            }
        }

    fun openCustomMessageInput() {
        val remoteInputBuilder =
            RemoteInput.Builder(CUSTOM_MESSAGE_REMOTE_INPUT_KEY)
                .setLabel("输入消息")
                .setChoices(customMessageQuickChoices)
                .setAllowFreeFormInput(true)
        WearableRemoteInputExtender(remoteInputBuilder)
            .setEmojisAllowed(true)
            .setInputActionType(EditorInfo.IME_ACTION_SEND)

        val inputIntent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putTitleExtra(inputIntent, activeConversation().title)
        RemoteInputIntentHelper.putConfirmLabelExtra(inputIntent, "发送")
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

    LaunchedEffect(transportMode, deviceName) {
        val transport = currentTransport()
        activePeer = null
        activePeerFingerprint = null
        pendingPeer = null
        pairingCode = null
        greetedPeers.clear()
        knownPeersByFingerprint.clear()

        runCatching { transport.start() }
            .onFailure { error ->
                trustState = error.readableMessage("传输启动失败")
                return@LaunchedEffect
            }

        if (transportMode == TransportMode.Bluetooth) {
            runCatching { bluetoothTransport.bondedPeers() }
                .getOrDefault(emptyList())
                .forEach { peer ->
                    activePeer = peer
                    trustState = "尝试连接 ${peer.name}"
                    if (greetedPeers.add(peer.id)) {
                        sendHello(transport, peer)
                    }
                }
        }

        try {
            transport.events.collect { event ->
                handleTransportEvent(transport, event)
            }
        } finally {
            transport.stop()
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
                val selectedConversation =
                    currentConversations.firstOrNull { conversation -> conversation.id == activeConversationId }
                        ?: currentConversations.first()
                val conversationListSurface: @Composable (Boolean) -> Unit = { profileNavigationEnabled ->
                    WatchConversationListSurface(
                        isRoundScreen = isRoundScreen,
                        profile = profile,
                        conversations = currentConversations,
                        unreadCounts = unreadCounts,
                        messagesByConversation = conversationMessages,
                        transportMode = transportMode,
                        trustState = trustState,
                        fingerprint = localFingerprint,
                        pairingCode = pairingCode,
                        pendingPeer = pendingPeer,
                        trustedPeerCount = trustedPeers.size,
                        onSelectMode = ::selectMode,
                        onConfirmPairing = ::confirmPairing,
                        onRejectPairing = ::rejectPairing,
                        onOpenConversation = ::openConversation,
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

                if (
                    appSurface == AppSurface.Chat ||
                    appSurface == AppSurface.ChatInfo ||
                    appSurface == AppSurface.MessageActions
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
                            onSelectMode = ::selectMode,
                            onConfirmPairing = ::confirmPairing,
                            onRejectPairing = ::rejectPairing,
                            onSendQuickReply = ::sendQuickReply,
                            onOpenCustomMessageInput = ::openCustomMessageInput,
                            onOpenChatInfo = {
                                appSurface = AppSurface.ChatInfo
                            },
                            onOpenMessageActions = { message ->
                                selectedActionMessage = message
                                appSurface = AppSurface.MessageActions
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
                            messages = messagesForConversation(selectedConversation.id),
                            onNavigateBack = dismissOverlay
                        )
                    }
                }

                if (appSurface == AppSurface.MessageActions) {
                    val actionMessage =
                        selectedActionMessage
                            ?: messagesForConversation(selectedConversation.id)
                                .lastOrNull { message -> message.deliveryState != DeliveryState.System }
                    if (actionMessage != null) {
                        SlideInOverlay(
                            onDismissed = {
                                appSurface = AppSurface.Chat
                            }
                        ) { dismissOverlay ->
                            WatchMessageActionsSurface(
                                isRoundScreen = isRoundScreen,
                                conversation = selectedConversation,
                                message = actionMessage,
                                onNavigateBack = dismissOverlay,
                                onOpenCustomMessageInput = {
                                    appSurface = AppSurface.Chat
                                    openCustomMessageInput()
                                },
                                onSendQuickReply = { reply ->
                                    appSurface = AppSurface.Chat
                                    sendQuickReply(reply)
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
                            onNavigateBack = dismissOverlay,
                            onProfileChange = ::updateProfile
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlideInOverlay(
    onDismissed: () -> Unit,
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

        LaunchedEffect(widthPx) {
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
    unreadCounts: Map<String, Int>,
    messagesByConversation: Map<String, List<ChatBubble>>,
    transportMode: TransportMode,
    trustState: String,
    fingerprint: String,
    pairingCode: String?,
    pendingPeer: TrustedPeer?,
    trustedPeerCount: Int,
    onSelectMode: (TransportMode) -> Unit,
    onConfirmPairing: () -> Unit,
    onRejectPairing: () -> Unit,
    onOpenConversation: (ChatConversation) -> Unit,
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

                    conversations.forEach { conversation ->
                        val lastMessage =
                            messagesByConversation[conversation.id]
                                .orEmpty()
                                .lastOrNull { message -> message.deliveryState != DeliveryState.System }
                        ConversationCapsule(
                            conversation = conversation,
                            lastMessage = lastMessage,
                            unreadCount = unreadCounts[conversation.id] ?: 0,
                            featured = conversation.id == NEARBY_GROUP_CONVERSATION_ID,
                            surfaceSpec = surfaceSpec,
                            onClick = {
                                onOpenConversation(conversation)
                            }
                        )
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
        TransportMode.entries.forEach { mode ->
            OrbitButton(
                mode = mode,
                selected = transportMode == mode,
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
private fun ConversationCapsule(
    conversation: ChatConversation,
    lastMessage: ChatBubble?,
    unreadCount: Int,
    featured: Boolean,
    surfaceSpec: WatchSurfaceSpec,
    onClick: () -> Unit
) {
    val compact = surfaceSpec.compact
    val accent = conversationAccentColor(conversation)
    val preview = conversationPreview(conversation, lastMessage)
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
                .clickable(onClick = onClick)
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
                Text(
                    text = lastMessage?.timestamp ?: conversation.kind.label,
                    color = chatRowMuted,
                    fontSize = if (compact) 8.sp else 9.sp,
                    maxLines = 1
                )
            }
            Text(
                text = preview,
                color = if (unreadCount > 0) Color.White else chatRowMuted,
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = if (unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = if (featured) 2 else 1,
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
            text = count.coerceAtMost(9).toString(),
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
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
    messages: List<ChatBubble>,
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
                        label = "类型",
                        value = conversation.kind.label,
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
                }
            }
        }
    }
}

@Composable
private fun WatchMessageActionsSurface(
    isRoundScreen: Boolean,
    conversation: ChatConversation,
    message: ChatBubble,
    onNavigateBack: () -> Unit,
    onOpenCustomMessageInput: () -> Unit,
    onSendQuickReply: (String) -> Unit
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
                        title = "消息操作",
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
                            accent = accent
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 7.dp)
                    ) {
                        MessageActionButton(
                            icon = Icons.Filled.Keyboard,
                            text = "输入回复",
                            selected = true,
                            compact = compact,
                            onClick = onOpenCustomMessageInput
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
                        compact = compact,
                        surfaceSpec = surfaceSpec
                    )
                }
            }
        }
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
    compact: Boolean,
    onClick: () -> Unit
) {
    val background =
        if (selected) {
            chatGreen
        } else {
            chatSurfaceHigh.copy(alpha = 0.88f)
        }
    val foreground =
        if (selected) {
            Color(0xFF001F1B)
        } else {
            MaterialTheme.colorScheme.onBackground
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
                    color = if (selected) Color.White.copy(alpha = 0.1f) else chatDivider.copy(alpha = 0.58f),
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
    compact: Boolean,
    surfaceSpec: WatchSurfaceSpec
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth(if (surfaceSpec.isRound) 0.82f else 0.94f)
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.82f))
                .border(1.dp, chatBlue.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 6.dp else 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector =
                if (message.encrypted) {
                    Icons.Filled.Lock
                } else {
                    Icons.Filled.DoneAll
                },
            contentDescription =
                if (message.encrypted) {
                    "已加密"
                } else {
                    "明文"
                },
            tint = chatBlue,
            modifier = Modifier.size(if (compact) 12.dp else 14.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = "${message.timestamp} · ${if (message.encrypted) "E2EE" else "明文"} · ${message.deliveryState.label}",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = if (compact) 9.sp else 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
    onNavigateBack: () -> Unit,
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
                            surfaceSpec = surfaceSpec
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
                text = "身份与可信设备",
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
    trustedPeerCount: Int,
    modifier: Modifier,
    compact: Boolean
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(chatSurfaceHigh.copy(alpha = 0.82f))
                .border(1.dp, chatRose.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = if (compact) 5.dp else 6.dp),
        contentAlignment = Alignment.Center
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
                text = peer.deviceName,
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
    compact: Boolean
) {
    Row(
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
    onSelectMode: (TransportMode) -> Unit,
    onConfirmPairing: () -> Unit,
    onRejectPairing: () -> Unit,
    onSendQuickReply: (String) -> Unit,
    onOpenCustomMessageInput: () -> Unit,
    onOpenChatInfo: () -> Unit,
    onOpenMessageActions: (ChatBubble) -> Unit,
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
                                onClick =
                                    if (message.deliveryState == DeliveryState.System) {
                                        null
                                    } else {
                                        { onOpenMessageActions(message) }
                                    }
                            )
                        }
                    }

                    ReplyDock(
                        quickReplyHeight = quickReplyHeight,
                        surfaceSpec = surfaceSpec,
                        onSendQuickReply = onSendQuickReply,
                        onOpenCustomMessageInput = onOpenCustomMessageInput
                    )
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
                StatusDot(trustState = trustState, size = if (compact) 6.dp else 7.dp)
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "${conversation.kind.label} · ${transportMode.label} · $trustState",
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
private fun MessageCapsule(
    message: ChatBubble,
    compact: Boolean,
    accent: Color,
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
                            if (message.mine) {
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
                    Text(
                        text = message.timestamp,
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
            Text(
                text = message.text,
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
        }
    }
}

@Composable
private fun ReplyDock(
    quickReplyHeight: Dp,
    surfaceSpec: WatchSurfaceSpec,
    onSendQuickReply: (String) -> Unit,
    onOpenCustomMessageInput: () -> Unit
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
        customMessageQuickChoices.take(2).forEach { reply ->
            QuickReplyChip(
                text = reply,
                height = quickReplyHeight - 6.dp,
                modifier = Modifier.weight(1f),
                onClick = { onSendQuickReply(reply) }
            )
        }
        InputButton(
            height = quickReplyHeight - 6.dp,
            onClick = onOpenCustomMessageInput
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

private fun nowTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

@Composable
private fun statusColor(trustState: String): Color =
    when {
        trustState.contains("失败") || trustState.contains("拒绝") -> MaterialTheme.colorScheme.error
        trustState.contains("待") || trustState.contains("未") -> chatAmber
        else -> chatGreen
    }

private fun trustedPeerSubtitle(peer: StoredTrustedPeer): String {
    val trustedAt =
        if (peer.trustedAtEpochMillis <= 0L) {
            "未知时间"
        } else {
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                .format(Date(peer.trustedAtEpochMillis))
        }
    return "${SpotChatCrypto.displayFingerprint(peer.fingerprint)} · $trustedAt"
}

private fun avatarFor(avatarId: String): DefaultAvatar =
    defaultAvatars.firstOrNull { avatar -> avatar.id == avatarId } ?: defaultAvatars.first()

private fun conversationAccentColor(conversation: ChatConversation): Color {
    if (conversation.kind == ConversationKind.Group) {
        return chatGreen
    }

    val palette =
        listOf(
            Color(0xFF53BDEB),
            Color(0xFFFFB4C8),
            Color(0xFFFFCC66),
            Color(0xFFB6E3F4),
            Color(0xFFC0AEDE)
        )
    val seed = conversation.peerFingerprint ?: conversation.id
    return palette[abs(seed.hashCode()) % palette.size]
}

private fun conversationPreview(
    conversation: ChatConversation,
    lastMessage: ChatBubble?
): String =
    lastMessage?.let { message ->
        when {
            message.mine -> "我：${message.text}"
            message.senderName != null -> "${message.senderName}：${message.text}"
            else -> message.text
        }
    } ?: conversation.subtitle

private fun directConversationId(peerFingerprint: String): String =
    "$DIRECT_CONVERSATION_PREFIX$peerFingerprint"

private fun encodeChatPayload(
    conversation: ChatConversation,
    text: String
): String =
    chatPayloadJson.encodeToString(
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
                }
        )
    )

private fun decodeChatPayload(text: String): ChatPayload =
    runCatching { chatPayloadJson.decodeFromString<ChatPayload>(text) }
        .getOrNull()
        ?.takeIf { payload -> payload.version == 1 && payload.text.isNotBlank() }
        ?: ChatPayload(
            kind = CHAT_PAYLOAD_KIND_DIRECT,
            text = text
        )

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
