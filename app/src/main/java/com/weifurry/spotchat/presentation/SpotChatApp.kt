package com.weifurry.spotchat.presentation

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

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
    Chat,
    Profile
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
    val messageId: String? = null,
    val deliveryState: DeliveryState = DeliveryState.Received
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
        get() = if (isRound) 20.dp else 12.dp

    val chatTopPadding: Dp
        get() = if (isRound) 22.dp else 12.dp

    val chatBottomPadding: Dp
        get() = if (isRound) 28.dp else 12.dp

    val chatHeaderWidth: Float
        get() = if (isRound) {
            if (compact) 0.78f else 0.82f
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
            if (compact) 0.84f else 0.78f
        } else {
            0.94f
        }

    val quickReplyWidth: Float
        get() = if (isRound) {
            if (compact) 0.78f else 0.72f
        } else {
            0.94f
        }

    val pairingWidth: Float
        get() = if (isRound) 0.88f else 0.94f

    val scrollIndicatorEndPadding: Dp
        get() = if (isRound) 12.dp else 6.dp

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
        remember(deviceName) {
            LanChatTransport(deviceName)
        }
    val bluetoothTransport =
        remember(context) {
            BluetoothChatTransport(context)
        }
    val coroutineScope = rememberCoroutineScope()
    val messages =
        remember {
            mutableStateListOf(
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
        }
    val trustedPeers =
        remember {
            mutableStateListOf<StoredTrustedPeer>().apply {
                addAll(trustedPeerStore.all())
            }
        }
    var transportMode by remember { mutableStateOf(TransportMode.Lan) }
    var trustState by remember { mutableStateOf("未配对") }
    var activePeer by remember { mutableStateOf<TransportPeer?>(null) }
    var activePeerFingerprint by remember { mutableStateOf<String?>(null) }
    var pendingPeer by remember { mutableStateOf<TrustedPeer?>(null) }
    var pairingCode by remember { mutableStateOf<String?>(null) }
    var appSurface by remember { mutableStateOf(AppSurface.Chat) }
    val greetedPeers = remember { mutableSetOf<String>() }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            trustState = "蓝牙权限已更新"
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

    suspend fun sendHello(
        transport: SpotChatTransport,
        peer: TransportPeer
    ) {
        runCatching {
            sendPacket(transport, peer, engine.helloPacket(transportHints()))
        }.onFailure { error ->
            trustState = "握手失败"
            messages +=
                ChatBubble(
                    text = error.readableMessage("无法发送配对信息"),
                    mine = false,
                    encrypted = false,
                    timestamp = nowTime()
                )
        }
    }

    fun appendSystemMessage(
        text: String,
        encrypted: Boolean = true
    ) {
        messages +=
            ChatBubble(
                text = text,
                mine = false,
                encrypted = encrypted,
                timestamp = nowTime(),
                deliveryState = DeliveryState.System
            )
    }

    fun updateMessageState(
        messageId: String,
        deliveryState: DeliveryState
    ) {
        val index = messages.indexOfFirst { message -> message.messageId == messageId }
        if (index >= 0) {
            messages[index] = messages[index].copy(deliveryState = deliveryState)
        }
    }

    fun trustedPeer(fingerprint: String): StoredTrustedPeer? =
        trustedPeers.firstOrNull { peer -> peer.fingerprint == fingerprint }

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
                            messages +=
                                ChatBubble(
                                    text = error.readableMessage("收到无法解析的数据"),
                                    mine = false,
                                    encrypted = false,
                                    timestamp = nowTime()
                                )
                            return
                        }
                when (packet.kind) {
                    PacketKind.HELLO -> {
                        val hello = packet.hello ?: return
                        val openedPeer = engine.openSession(hello)
                        val mergedPeer = mergePeerWithHello(event.peer, hello)
                        activePeer = mergedPeer
                        pairingCode = openedPeer.pairingCode
                        val storedPeer = trustedPeer(openedPeer.fingerprint)
                        if (storedPeer != null && storedPeer.publicKey == openedPeer.publicKey) {
                            pendingPeer = null
                            activePeerFingerprint = openedPeer.fingerprint
                            trustState = "已信任 ${storedPeer.deviceName}"
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
                        if (trustedPeer(encryptedMessage.senderFingerprint) == null) {
                            trustState = "拦截未确认消息"
                            appendSystemMessage(
                                text = "未确认设备发来的消息已拦截",
                                encrypted = false
                            )
                            return
                        }
                        runCatching { engine.decryptText(encryptedMessage) }
                            .onSuccess { plain ->
                                messages +=
                                    ChatBubble(
                                        text = plain.text,
                                        mine = false,
                                        encrypted = true,
                                        timestamp = nowTime(),
                                        messageId = plain.messageId,
                                        deliveryState = DeliveryState.Received
                                    )
                                trustState = "收到加密消息"
                                val replyPeer = activePeer ?: event.peer
                                runCatching {
                                    sendPacket(
                                        transport,
                                        replyPeer,
                                        engine.ackPacket(plain.messageId)
                                    )
                                }.onFailure {
                                    trustState = "回执发送失败"
                                }
                            }
                            .onFailure { error ->
                                if (error is DuplicateMessageException) {
                                    trustState = "重复消息已忽略"
                                    val replyPeer = activePeer ?: event.peer
                                    runCatching {
                                        sendPacket(
                                            transport,
                                            replyPeer,
                                            engine.ackPacket(error.messageId)
                                        )
                                    }.onFailure {
                                        trustState = "重复回执发送失败"
                                    }
                                } else {
                                    messages +=
                                        ChatBubble(
                                            text = error.readableMessage("无法解密消息"),
                                            mine = false,
                                            encrypted = false,
                                            timestamp = nowTime()
                                        )
                                    trustState = "解密失败"
                                }
                            }
                    }

                    PacketKind.ACK -> {
                        val ack = packet.ack ?: return
                        updateMessageState(ack.messageId, DeliveryState.Delivered)
                        trustState = "对方已收到"
                    }
                }
            }

            is TransportEvent.Failure -> {
                trustState = event.message
                messages +=
                    ChatBubble(
                        text = event.cause.readableMessage(event.message),
                        mine = false,
                        encrypted = false,
                        timestamp = nowTime()
                    )
            }
        }
    }

    fun confirmPairing() {
        val peer = pendingPeer ?: return
        val storedPeer = trustedPeerStore.trust(peer)
        trustedPeers.removeAll { existing -> existing.fingerprint == storedPeer.fingerprint }
        trustedPeers.add(0, storedPeer)
        pendingPeer = null
        activePeerFingerprint = storedPeer.fingerprint
        pairingCode = storedPeer.pairingCode
        trustState = "已信任 ${storedPeer.deviceName}"
        appendSystemMessage(
            text = "已信任 ${storedPeer.deviceName}，可以开始加密聊天",
            encrypted = true
        )
    }

    fun rejectPairing() {
        val peer = pendingPeer ?: return
        trustedPeerStore.forget(peer.fingerprint)
        trustedPeers.removeAll { storedPeer -> storedPeer.fingerprint == peer.fingerprint }
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
        transportMode = mode
        trustState = if (mode == TransportMode.Lan) "局域网发现中" else "蓝牙待授权"
        if (mode == TransportMode.Bluetooth && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        }
    }

    fun updateProfile(updated: ProfileSettings) {
        profile = profileStore.save(updated)
    }

    fun sendQuickReply(text: String) {
        val peer = activePeer
        val peerFingerprint = activePeerFingerprint
        if (peer == null || peerFingerprint == null) {
            trustState = if (pendingPeer == null) "等待配对" else "请先确认校验码"
            messages +=
                ChatBubble(
                    text = if (pendingPeer == null) "还没有完成配对" else "请先确认配对校验码",
                    mine = false,
                    encrypted = true,
                    timestamp = nowTime(),
                    deliveryState = DeliveryState.Waiting
                )
            return
        }

        if (trustedPeer(peerFingerprint) == null) {
            activePeerFingerprint = null
            trustState = "请重新确认配对"
            appendSystemMessage("当前设备还没有被信任，消息不会发送")
            return
        }

        trustState = "正在加密发送"
        coroutineScope.launch {
            val packet =
                runCatching { engine.encryptTextForPeer(peerFingerprint, text) }
                    .getOrElse { error ->
                        trustState = "加密失败"
                        messages +=
                            ChatBubble(
                                text = error.readableMessage("无法加密消息"),
                                mine = false,
                                encrypted = false,
                                timestamp = nowTime()
                            )
                        return@launch
                    }
            val messageId = packet.encryptedMessage?.messageId ?: return@launch
            messages +=
                ChatBubble(
                    text = text,
                    mine = true,
                    encrypted = true,
                    timestamp = nowTime(),
                    messageId = messageId,
                    deliveryState = DeliveryState.Sending
                )

            runCatching {
                sendPacket(currentTransport(), peer, packet)
            }.onSuccess {
                updateMessageState(messageId, DeliveryState.Sent)
                trustState = "已加密发送"
            }.onFailure { error ->
                updateMessageState(messageId, DeliveryState.Failed)
                trustState = "发送失败"
                messages +=
                    ChatBubble(
                        text = error.readableMessage("无法发送消息"),
                        mine = false,
                        encrypted = false,
                        timestamp = nowTime()
                    )
            }
        }
    }

    LaunchedEffect(transportMode, deviceName) {
        val transport = currentTransport()
        activePeer = null
        activePeerFingerprint = null
        pendingPeer = null
        pairingCode = null
        greetedPeers.clear()

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
        val dismissState = rememberSwipeToDismissBoxState()
        val rootDismissState = rememberSwipeToDismissBoxState()
        LaunchedEffect(appSurface) {
            if (appSurface == AppSurface.Profile) {
                dismissState.snapTo(SwipeToDismissValue.Default)
            }
            if (appSurface == AppSurface.Chat) {
                rootDismissState.snapTo(SwipeToDismissValue.Default)
            }
        }
        BackHandler(enabled = appSurface == AppSurface.Profile) {
            appSurface = AppSurface.Chat
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
                AnimatedContent(
                    targetState = appSurface,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val opensProfile = targetState == AppSurface.Profile
                        val enter =
                            slideInHorizontally(animationSpec = tween(durationMillis = 220)) { fullWidth ->
                                if (opensProfile) fullWidth else -fullWidth
                            } + fadeIn(animationSpec = tween(durationMillis = 160))
                        val exit =
                            slideOutHorizontally(animationSpec = tween(durationMillis = 220)) { fullWidth ->
                                if (opensProfile) -fullWidth else fullWidth
                            } + fadeOut(animationSpec = tween(durationMillis = 140))

                        enter togetherWith exit
                    },
                    contentAlignment = Alignment.Center,
                    label = "SpotChatSurfaceTransition"
                ) { targetSurface ->
                    when (targetSurface) {
                        AppSurface.Chat ->
                            SwipeToDismissBox(
                                onDismissed = {
                                    activity?.finish()
                                },
                                modifier = Modifier.fillMaxSize(),
                                state = rootDismissState,
                                backgroundKey = "SpotChatRootDismissBackground",
                                contentKey = AppSurface.Chat
                            ) { isBackground ->
                                if (isBackground) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .background(Color.Black)
                                    )
                                } else {
                                    WatchChatSurface(
                                        isRoundScreen = isRoundScreen,
                                        profile = profile,
                                        transportMode = transportMode,
                                        trustState = trustState,
                                        fingerprint = localFingerprint,
                                        pairingCode = pairingCode,
                                        pendingPeer = pendingPeer,
                                        trustedPeerCount = trustedPeers.size,
                                        messages = messages,
                                        onSelectMode = ::selectMode,
                                        onConfirmPairing = ::confirmPairing,
                                        onRejectPairing = ::rejectPairing,
                                        onSendQuickReply = ::sendQuickReply,
                                        onOpenProfile = {
                                            appSurface = AppSurface.Profile
                                        }
                                    )
                                }
                            }

                        AppSurface.Profile ->
                            SwipeToDismissBox(
                                onDismissed = {
                                    appSurface = AppSurface.Chat
                                },
                                modifier = Modifier.fillMaxSize(),
                                state = dismissState,
                                backgroundKey = AppSurface.Chat,
                                contentKey = AppSurface.Profile
                            ) { isBackground ->
                                if (isBackground) {
                                    WatchChatSurface(
                                        isRoundScreen = isRoundScreen,
                                        profile = profile,
                                        transportMode = transportMode,
                                        trustState = trustState,
                                        fingerprint = localFingerprint,
                                        pairingCode = pairingCode,
                                        pendingPeer = pendingPeer,
                                        trustedPeerCount = trustedPeers.size,
                                        messages = messages,
                                        onSelectMode = ::selectMode,
                                        onConfirmPairing = ::confirmPairing,
                                        onRejectPairing = ::rejectPairing,
                                        onSendQuickReply = ::sendQuickReply,
                                        onOpenProfile = {},
                                        profileNavigationEnabled = false
                                    )
                                } else {
                                    WatchProfileSurface(
                                        isRoundScreen = isRoundScreen,
                                        profile = profile,
                                        avatars = defaultAvatars,
                                        onNavigateBack = {
                                            appSurface = AppSurface.Chat
                                        },
                                        onProfileChange = ::updateProfile
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
            contentDescription = "返回主界面",
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
    profile: ProfileSettings,
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
                                    Color(0xFF173032),
                                    Color(0xFF081719),
                                    Color(0xFF020506)
                                )
                        )
                    )
                    .padding(
                        start = surfaceSpec.chatHorizontalPadding,
                        top = surfaceSpec.chatTopPadding,
                        end = surfaceSpec.chatHorizontalPadding,
                        bottom = surfaceSpec.chatBottomPadding
                    ),
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
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StatusHeader(
                        avatar = avatarFor(profile.avatarId),
                        displayName = profile.displayName,
                        trustState = trustState,
                        transportMode = transportMode,
                        surfaceSpec = surfaceSpec,
                        onOpenProfile = if (profileNavigationEnabled) onOpenProfile else null
                    )

                    Spacer(modifier = Modifier.height(if (compact) 4.dp else 6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(surfaceSpec.chatToggleWidth),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TransportToggle(
                            mode = TransportMode.Lan,
                            selected = transportMode == TransportMode.Lan,
                            compact = compact,
                            onClick = { onSelectMode(TransportMode.Lan) }
                        )
                        Spacer(modifier = Modifier.width(if (compact) 6.dp else 8.dp))
                        TransportToggle(
                            mode = TransportMode.Bluetooth,
                            selected = transportMode == TransportMode.Bluetooth,
                            compact = compact,
                            onClick = { onSelectMode(TransportMode.Bluetooth) }
                        )
                    }

                    FingerprintPill(
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
                                .heightIn(min = if (compact) 42.dp else 56.dp)
                                .verticalScroll(messageScrollState),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
                    ) {
                        messages.takeLast(visibleMessageCount).forEach { message ->
                            MessageBubble(
                                message = message,
                                compact = compact
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(if (compact) 5.dp else 6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(surfaceSpec.quickReplyWidth),
                        horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        QuickReplyChip(
                            text = "收到",
                            height = quickReplyHeight,
                            modifier = Modifier.weight(1f),
                            onClick = { onSendQuickReply("收到") }
                        )
                        QuickReplyChip(
                            text = "马上到",
                            height = quickReplyHeight,
                            modifier = Modifier.weight(1f),
                            onClick = { onSendQuickReply("马上到") }
                        )
                        SendButton(
                            height = quickReplyHeight,
                            onClick = { onSendQuickReply("稍后联系") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusHeader(
    avatar: DefaultAvatar,
    displayName: String,
    trustState: String,
    transportMode: TransportMode,
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
            avatar = avatar,
            displayName = displayName,
            size = if (compact) 26.dp else 30.dp,
            textSize = if (compact) 13.sp else 15.sp,
            selected = false,
            onClick = onOpenProfile
        )
        Spacer(modifier = Modifier.width(if (compact) 6.dp else 7.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = "SpotChat",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = if (compact) 15.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = "${transportMode.label} · $trustState",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = if (compact) 9.sp else 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TransportToggle(
    mode: TransportMode,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    val background =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    val foreground =
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Row(
        modifier =
            Modifier
                .height(if (compact) 26.dp else 28.dp)
                .clip(RoundedCornerShape(if (compact) 13.dp else 14.dp))
                .background(background)
                .clickable(onClick = onClick)
                .padding(horizontal = if (compact) 8.dp else 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = mode.icon,
            contentDescription = mode.label,
            tint = foreground,
            modifier = Modifier.size(if (compact) 13.dp else 14.dp)
        )
        Spacer(modifier = Modifier.width(if (compact) 3.dp else 4.dp))
        Text(
            text = mode.label,
            color = foreground,
            fontSize = if (compact) 10.sp else 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun FingerprintPill(
    fingerprint: String,
    pairingCode: String?,
    trustedPeerCount: Int,
    surfaceSpec: WatchSurfaceSpec
) {
    val compact = surfaceSpec.compact
    Row(
        modifier =
            Modifier
                .padding(top = if (compact) 5.dp else 7.dp, bottom = if (compact) 5.dp else 6.dp)
                .fillMaxWidth(surfaceSpec.fingerprintWidth)
                .clip(RoundedCornerShape(if (compact) 12.dp else 13.dp))
                .background(Color(0x332DE6D1))
                .padding(horizontal = if (compact) 8.dp else 9.dp, vertical = if (compact) 4.dp else 5.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Key,
            contentDescription = "本机密钥指纹",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(if (compact) 10.dp else 12.dp)
        )
        Spacer(modifier = Modifier.width(if (compact) 4.dp else 5.dp))
        Text(
            text = pairingCode?.let { "校验 $it" } ?: "本机 $fingerprint · 已信任 $trustedPeerCount",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = if (compact) 9.sp else 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SendButton(
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
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = "发送",
            tint = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}

private fun nowTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

private fun avatarFor(avatarId: String): DefaultAvatar =
    defaultAvatars.firstOrNull { avatar -> avatar.id == avatarId } ?: defaultAvatars.first()

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
