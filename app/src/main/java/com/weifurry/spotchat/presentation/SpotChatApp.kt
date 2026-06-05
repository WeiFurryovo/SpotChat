package com.weifurry.spotchat.presentation

import android.Manifest
import android.app.Activity
import android.app.RemoteInput
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Mic
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
private const val CHAT_PAYLOAD_KIND_DIRECT = "direct"
private const val CHAT_PAYLOAD_KIND_GROUP = "group"
private val customMessageQuickChoices = arrayOf("收到", "马上到", "稍后联系")
private val chatPayloadJson =
    Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
private val chatGreen = Color(0xFF00A884)
private val chatGreenDark = Color(0xFF005C4B)
private val chatBlue = Color(0xFF53BDEB)
private val chatWallpaper = Color(0xFF050B0C)
private val chatIncoming = Color(0xFF111B21)
private val chatRowMuted = Color(0xFF8696A0)

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

    val profileVerticalGap: Dp
        get() =
            when {
                isRound && compact -> 8.dp
                isRound -> 10.dp
                compact -> 9.dp
                else -> 11.dp
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

    val chatToggleWidth: Float
        get() = if (isRound) {
            if (compact) 0.82f else 0.76f
        } else {
            0.94f
        }

    val fingerprintWidth: Float
        get() = if (isRound) {
            if (compact) 0.78f else 0.88f
        } else {
            0.94f
        }

    val chatMessageWidth: Float
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

    val pairingWidth: Float
        get() = if (isRound) 0.88f else 0.94f

    val conversationRowWidth: Float
        get() = if (isRound) {
            if (compact) 0.9f else 0.92f
        } else {
            0.94f
        }

    val scrollIndicatorEndPadding: Dp
        get() = if (isRound) 4.dp else 6.dp

    fun visibleMessageCount(hasPendingPeer: Boolean): Int =
        when {
            hasPendingPeer && isRound -> 1
            hasPendingPeer -> 2
            isRound && compact -> 1
            isRound -> 2
            compact -> 3
            else -> 4
        }
}

@Composable
internal fun SpotChatApp() {
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
        unreadCounts[conversation.id] = 0
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

    fun sendQuickReply(text: String) {
        val conversation = activeConversation()
        if (transportMode == TransportMode.Lan && !hasLanConnection()) {
            trustState = "局域网未连接"
            appendMessage(
                conversation.id,
                ChatBubble(
                    text = "当前没有可用的局域网连接，消息未发送",
                    mine = false,
                    encrypted = false,
                    timestamp = nowTime(),
                    deliveryState = DeliveryState.Failed
                )
            )
            return
        }

        val targets =
            conversation.memberFingerprints.mapNotNull { fingerprint ->
                val peer = routeForPeer(fingerprint)
                val storedPeer = trustedPeer(fingerprint)
                if (peer == null || storedPeer == null) {
                    null
                } else {
                    fingerprint to peer
                }
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

        if (targets.isEmpty()) {
            trustState = "成员未在线"
            appendMessage(
                conversation.id,
                ChatBubble(
                    text =
                        if (conversation.kind == ConversationKind.Direct) {
                            "对方暂时未在线，消息未发送"
                        } else {
                            "群成员暂时未在线，消息未发送"
                        },
                    mine = false,
                    encrypted = true,
                    timestamp = nowTime(),
                    deliveryState = DeliveryState.Waiting
                )
            )
            return
        }

        val displayMessageId = UUID.randomUUID().toString()
        appendMessage(
            conversation.id,
            ChatBubble(
                text = text,
                mine = true,
                encrypted = true,
                timestamp = nowTime(),
                messageId = displayMessageId,
                deliveryState = DeliveryState.Sending
            )
        )

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
                    updateMessageState(displayMessageId, DeliveryState.Sent)
                    trustState =
                        if (conversation.kind == ConversationKind.Group) {
                            "群聊已加密发送"
                        } else {
                            "已加密发送"
                        }
                }

                sentCount > 0 -> {
                    updateMessageState(displayMessageId, DeliveryState.Sent)
                    trustState = "部分成员已发送"
                }

                else -> {
                    updateMessageState(displayMessageId, DeliveryState.Failed)
                    trustState = "发送失败"
                }
            }
        }
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

                if (appSurface == AppSurface.Chat) {
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
                            onNavigateBack = dismissOverlay
                        )
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
        val listGap = if (compact) 3.dp else 4.dp

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(surfaceSpec.screenShape)
                    .background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    Color(0xFF102321),
                                    chatWallpaper,
                                    Color.Black
                                )
                        )
                    )
                    .padding(horizontal = surfaceSpec.chatHorizontalPadding)
        ) {
            ChatWallpaperGlyphs(compact = compact)
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
                                top = surfaceSpec.chatTopPadding,
                                bottom = surfaceSpec.conversationBottomPadding
                            ),
                    verticalArrangement = Arrangement.spacedBy(listGap),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    InboxHeader(
                        profile = profile,
                        transportMode = transportMode,
                        trustState = trustState,
                        surfaceSpec = surfaceSpec,
                        onOpenProfile = if (profileNavigationEnabled) onOpenProfile else null,
                    )

                    InboxTransportBar(
                        transportMode = transportMode,
                        compact = compact,
                        surfaceSpec = surfaceSpec,
                        onSelectMode = onSelectMode
                    )

                    EncryptionStatusPill(
                        fingerprint = fingerprint,
                        pairingCode = pairingCode,
                        trustedPeerCount = trustedPeerCount,
                        surfaceSpec = surfaceSpec
                    )

                    if (pendingPeer != null) {
                        PairingActionRow(
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
                        ConversationRow(
                            conversation = conversation,
                            lastMessage = lastMessage,
                            unreadCount = unreadCounts[conversation.id] ?: 0,
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
private fun ConversationRow(
    conversation: ChatConversation,
    lastMessage: ChatBubble?,
    unreadCount: Int,
    surfaceSpec: WatchSurfaceSpec,
    onClick: () -> Unit
) {
    val compact = surfaceSpec.compact
    val accent = conversationAccentColor(conversation)
    val lastTime = lastMessage?.timestamp ?: "现在"
    Row(
        modifier =
            Modifier
                .fillMaxWidth(surfaceSpec.conversationRowWidth)
                .height(if (compact) 56.dp else 64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (unreadCount > 0) {
                        chatGreen.copy(alpha = 0.12f)
                    } else {
                        Color.Transparent
                    }
                )
                .clickable(onClick = onClick)
                .padding(horizontal = if (compact) 5.dp else 6.dp, vertical = if (compact) 5.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConversationAvatar(
            conversation = conversation,
            size = if (compact) 42.dp else 48.dp,
            accent = accent
        )
        Spacer(modifier = Modifier.width(if (compact) 8.dp else 10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = if (compact) 16.sp else 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector =
                        if (lastMessage?.mine == true) {
                            Icons.Filled.DoneAll
                        } else {
                            Icons.AutoMirrored.Filled.Chat
                        },
                    contentDescription = "消息摘要",
                    tint = if (lastMessage?.mine == true) chatBlue else chatRowMuted,
                    modifier = Modifier.size(if (compact) 12.dp else 14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = conversationPreview(conversation, lastMessage),
                    color = if (unreadCount > 0) MaterialTheme.colorScheme.onSurface else chatRowMuted,
                    fontSize = if (compact) 11.sp else 13.sp,
                    fontWeight = if (unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(if (compact) 4.dp else 6.dp))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = lastTime,
                color = if (unreadCount > 0) chatGreen else chatRowMuted,
                fontSize = if (compact) 10.sp else 12.sp,
                fontWeight = if (unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
            Box(
                modifier =
                    Modifier
                        .padding(top = 6.dp)
                        .size(if (compact) 10.dp else 12.dp)
                        .clip(CircleShape)
                        .background(if (unreadCount > 0) chatGreen else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                if (unreadCount > 1) {
                    Text(
                        text = unreadCount.coerceAtMost(9).toString(),
                        color = Color.White,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatWallpaperGlyphs(compact: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = chatGreen.copy(alpha = 0.06f),
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-18).dp, y = if (compact) 20.dp else 26.dp)
                    .size(if (compact) 34.dp else 42.dp)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.045f),
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = if (compact) 2.dp else 6.dp, y = if (compact) (-6).dp else (-12).dp)
                    .size(if (compact) 44.dp else 54.dp)
        )
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            tint = chatBlue.copy(alpha = 0.055f),
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-24).dp, y = if (compact) (-34).dp else (-46).dp)
                    .size(if (compact) 34.dp else 42.dp)
        )
    }
}

@Composable
private fun InboxHeader(
    profile: ProfileSettings,
    transportMode: TransportMode,
    trustState: String,
    surfaceSpec: WatchSurfaceSpec,
    onOpenProfile: (() -> Unit)?
) {
    val compact = surfaceSpec.compact
    Row(
        modifier = Modifier.fillMaxWidth(surfaceSpec.chatHeaderWidth),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarBubble(
            avatar = avatarFor(profile.avatarId),
            displayName = profile.displayName,
            size = if (compact) 34.dp else 38.dp,
            textSize = if (compact) 15.sp else 17.sp,
            selected = false,
            onClick = onOpenProfile
        )
        Spacer(modifier = Modifier.width(if (compact) 8.dp else 10.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "SpotChat",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = if (compact) 19.sp else 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(if (compact) 6.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (trustState.contains("失败") || trustState.contains("拒绝")) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    chatGreen
                                }
                            )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${transportMode.label} · $trustState",
                    color = chatRowMuted,
                    fontSize = if (compact) 10.sp else 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun InboxTransportBar(
    transportMode: TransportMode,
    compact: Boolean,
    surfaceSpec: WatchSurfaceSpec,
    onSelectMode: (TransportMode) -> Unit
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth(surfaceSpec.chatToggleWidth)
                .height(if (compact) 30.dp else 34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(chatIncoming.copy(alpha = 0.72f))
                .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TransportMode.entries.forEach { mode ->
            InboxTransportSegment(
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
private fun InboxTransportSegment(
    mode: TransportMode,
    selected: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val background = if (selected) chatGreenDark else Color.Transparent
    val foreground = if (selected) Color.White else chatRowMuted
    Row(
        modifier =
            modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(7.dp))
                .background(background)
                .clickable(onClick = onClick)
                .padding(horizontal = if (compact) 5.dp else 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = mode.icon,
            contentDescription = mode.label,
            tint = foreground,
            modifier = Modifier.size(if (compact) 12.dp else 14.dp)
        )
        Spacer(modifier = Modifier.width(if (compact) 3.dp else 4.dp))
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
private fun EncryptionStatusPill(
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
                .fillMaxWidth(surfaceSpec.fingerprintWidth)
                .clip(RoundedCornerShape(8.dp))
                .background(chatGreen.copy(alpha = 0.14f))
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 5.dp else 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = "端到端加密",
            tint = chatGreen,
            modifier = Modifier.size(if (compact) 11.dp else 13.dp)
        )
        Spacer(modifier = Modifier.width(if (compact) 4.dp else 5.dp))
        Text(
            text =
                pairingCode?.let { "校验 $it" }
                    ?: "$displayFingerprint · $trustedPeerCount 位可信",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = if (compact) 9.sp else 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
                                accent.copy(alpha = 0.58f)
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
private fun WatchProfileSurface(
    isRoundScreen: Boolean,
    profile: ProfileSettings,
    avatars: List<DefaultAvatar>,
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

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(surfaceSpec.screenShape)
                    .background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    Color(0xFF231A2B),
                                    Color(0xFF10141D),
                                    Color(0xFF030506)
                                )
                        )
                    )
                    .padding(horizontal = surfaceSpec.profileHorizontalPadding)
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
                    verticalArrangement = Arrangement.spacedBy(surfaceSpec.profileVerticalGap),
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
                        Text(
                            text = "头像",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = if (compact) 10.sp else 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
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
                        Text(
                            text = profile.displayName.ifBlank { "SpotChat Watch" },
                            modifier = Modifier.fillMaxWidth(surfaceSpec.profileSummaryWidth),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = if (compact) 10.sp else 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
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
    Row(
        modifier = Modifier.fillMaxWidth(surfaceSpec.profileHeaderWidth),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileBackButton(
            compact = compact,
            onClick = onNavigateBack
        )
        Spacer(modifier = Modifier.width(if (compact) 6.dp else 7.dp))
        AvatarBubble(
            avatar = selectedAvatar,
            displayName = profile.displayName,
            size = if (compact) 32.dp else 36.dp,
            textSize = if (compact) 15.sp else 17.sp,
            selected = false,
            onClick = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = "个人资料",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = if (compact) 15.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = "显示名与头像",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = if (compact) 9.sp else 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
                size = surfaceSpec.profileAvatarSize,
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
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
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
                .height(if (compact) 32.dp else 36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 10.dp),
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
                    fontSize = if (compact) 13.sp else 14.sp,
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

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(surfaceSpec.screenShape)
                    .background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    Color(0xFF102321),
                                    chatWallpaper,
                                    Color.Black
                                )
                        )
                    )
                    .padding(
                        start = surfaceSpec.chatHorizontalPadding,
                        top = surfaceSpec.chatTopPadding,
                        end = surfaceSpec.chatHorizontalPadding,
                        bottom = surfaceSpec.chatBottomPadding
                    )
        ) {
            ChatWallpaperGlyphs(compact = compact)
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
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ChatTopBar(
                        conversation = conversation,
                        transportMode = transportMode,
                        trustState = trustState,
                        surfaceSpec = surfaceSpec,
                        onNavigateBack = onNavigateBack
                    )

                    Spacer(modifier = Modifier.height(if (compact) 3.dp else 4.dp))

                    InboxTransportBar(
                        transportMode = transportMode,
                        compact = compact,
                        surfaceSpec = surfaceSpec,
                        onSelectMode = onSelectMode
                    )

                    EncryptionStatusPill(
                        fingerprint = fingerprint,
                        pairingCode = pairingCode,
                        trustedPeerCount = trustedPeerCount,
                        surfaceSpec = surfaceSpec
                    )

                    if (pendingPeer != null) {
                        PairingActionRow(
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
                                .fillMaxWidth(surfaceSpec.chatMessageWidth)
                                .heightIn(min = if (compact) 54.dp else 72.dp)
                                .verticalScroll(messageScrollState),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)
                    ) {
                        messages.takeLast(visibleMessageCount).forEach { message ->
                            MessageBubble(
                                message = message,
                                compact = compact
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(if (compact) 5.dp else 6.dp))

                    ChatComposerBar(
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
private fun ChatTopBar(
    conversation: ChatConversation,
    transportMode: TransportMode,
    trustState: String,
    surfaceSpec: WatchSurfaceSpec,
    onNavigateBack: () -> Unit
) {
    val compact = surfaceSpec.compact
    val accent = conversationAccentColor(conversation)
    Row(
        modifier = Modifier.fillMaxWidth(surfaceSpec.chatHeaderWidth),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileBackButton(
            compact = compact,
            contentDescription = "返回会话列表",
            onClick = onNavigateBack
        )
        Spacer(modifier = Modifier.width(if (compact) 5.dp else 6.dp))
        ConversationAvatar(
            conversation = conversation,
            size = if (compact) 30.dp else 34.dp,
            accent = accent
        )
        Spacer(modifier = Modifier.width(if (compact) 6.dp else 7.dp))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = conversation.title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = if (compact) 16.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(if (compact) 6.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (trustState.contains("失败") || trustState.contains("拒绝")) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    chatGreen
                                }
                            )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${conversation.kind.label} · ${transportMode.label} · $trustState",
                    color = chatRowMuted,
                    fontSize = if (compact) 9.sp else 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PairingActionRow(
    peer: TrustedPeer,
    surfaceSpec: WatchSurfaceSpec,
    onConfirmPairing: () -> Unit,
    onRejectPairing: () -> Unit
) {
    Column(
        modifier =
            Modifier
                .padding(bottom = 6.dp)
                .fillMaxWidth(surfaceSpec.pairingWidth)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = peer.deviceName,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier =
                Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PairingButton(
                text = "拒绝",
                selected = false,
                modifier = Modifier.weight(1f),
                onClick = onRejectPairing
            )
            PairingButton(
                text = "信任",
                selected = true,
                modifier = Modifier.weight(1f),
                onClick = onConfirmPairing
            )
        }
    }
}

@Composable
private fun PairingButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val background =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    val foreground =
        if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    Box(
        modifier =
            modifier
                .height(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(background)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = foreground,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun MessageBubble(
    message: ChatBubble,
    compact: Boolean
) {
    val alignment = if (message.mine) Alignment.CenterEnd else Alignment.CenterStart
    val background =
        if (message.mine) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val foreground =
        if (message.mine) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(if (message.mine) 0.78f else 1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(background)
                    .padding(
                        horizontal = if (compact) 8.dp else 9.dp,
                        vertical = if (compact) 6.dp else 7.dp
                    )
        ) {
            if (message.deliveryState != DeliveryState.System) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = if (message.encrypted) "已加密" else "未加密",
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
                fontSize = if (compact) 12.sp else 13.sp,
                lineHeight = if (compact) 15.sp else 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChatComposerBar(
    quickReplyHeight: Dp,
    surfaceSpec: WatchSurfaceSpec,
    onSendQuickReply: (String) -> Unit,
    onOpenCustomMessageInput: () -> Unit
) {
    val compact = surfaceSpec.compact
    Row(
        modifier =
            Modifier
                .fillMaxWidth(surfaceSpec.quickReplyWidth)
                .height(quickReplyHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(chatIncoming.copy(alpha = 0.9f))
                .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickReplyChip(
            text = "收到",
            height = quickReplyHeight - 6.dp,
            modifier = Modifier.weight(1f),
            onClick = { onSendQuickReply("收到") }
        )
        QuickReplyChip(
            text = "马上到",
            height = quickReplyHeight - 6.dp,
            modifier = Modifier.weight(1f),
            onClick = { onSendQuickReply("马上到") }
        )
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
                .background(chatGreenDark.copy(alpha = 0.66f))
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
                .background(MaterialTheme.colorScheme.secondary)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Keyboard,
            contentDescription = "输入消息",
            tint = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}

private fun nowTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

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
    "direct:$peerFingerprint"

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
