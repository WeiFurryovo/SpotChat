package com.weifurry.spotchat.presentation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.weifurry.spotchat.crypto.IdentityStore
import com.weifurry.spotchat.crypto.SpotChatCrypto
import com.weifurry.spotchat.domain.SpotChatEngine
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
    val wireName: String,
    val icon: ImageVector
) {
    Lan("局域网", "lan", Icons.Filled.Lan),
    Bluetooth("蓝牙", "bluetooth", Icons.Filled.Bluetooth)
}

private data class ChatBubble(
    val text: String,
    val mine: Boolean,
    val encrypted: Boolean,
    val timestamp: String
)

@Composable
internal fun SpotChatApp() {
    val context = LocalContext.current
    val identity =
        remember(context) {
            IdentityStore(context).getOrCreateIdentity()
        }
    val deviceName =
        remember {
            listOf(Build.MANUFACTURER, Build.MODEL)
                .joinToString(separator = " ")
                .trim()
                .ifBlank { "SpotChat Watch" }
        }
    val engine =
        remember(identity, deviceName) {
            SpotChatEngine(deviceName, identity)
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
                    timestamp = nowTime()
                ),
                ChatBubble(
                    text = "所有聊天内容都会先加密再发送",
                    mine = false,
                    encrypted = true,
                    timestamp = nowTime()
                )
            )
        }
    var transportMode by remember { mutableStateOf(TransportMode.Lan) }
    var trustState by remember { mutableStateOf("未配对") }
    var activePeer by remember { mutableStateOf<TransportPeer?>(null) }
    var activePeerFingerprint by remember { mutableStateOf<String?>(null) }
    var pairingCode by remember { mutableStateOf<String?>(null) }
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
                        val trustedPeer = engine.openSession(hello)
                        val mergedPeer = mergePeerWithHello(event.peer, hello)
                        activePeer = mergedPeer
                        activePeerFingerprint = trustedPeer.fingerprint
                        pairingCode = trustedPeer.pairingCode
                        trustState = "校验 ${trustedPeer.pairingCode}"
                        if (greetedPeers.add(mergedPeer.id)) {
                            sendHello(transport, mergedPeer)
                        }
                    }

                    PacketKind.ENCRYPTED_MESSAGE -> {
                        val encryptedMessage = packet.encryptedMessage ?: return
                        runCatching { engine.decryptText(encryptedMessage) }
                            .onSuccess { plain ->
                                messages +=
                                    ChatBubble(
                                        text = plain.text,
                                        mine = false,
                                        encrypted = true,
                                        timestamp = nowTime()
                                    )
                                trustState = "收到加密消息"
                            }
                            .onFailure { error ->
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

                    PacketKind.ACK -> {
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

    fun sendQuickReply(text: String) {
        val peer = activePeer
        val peerFingerprint = activePeerFingerprint
        if (peer == null || peerFingerprint == null) {
            trustState = "等待配对"
            messages +=
                ChatBubble(
                    text = "还没有完成配对",
                    mine = false,
                    encrypted = true,
                    timestamp = nowTime()
                )
            return
        }

        messages +=
            ChatBubble(
                text = text,
                mine = true,
                encrypted = true,
                timestamp = nowTime()
            )
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

            runCatching {
                sendPacket(currentTransport(), peer, packet)
            }.onSuccess {
                trustState = "已加密发送"
            }.onFailure { error ->
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

    LaunchedEffect(transportMode) {
        val transport = currentTransport()
        activePeer = null
        activePeerFingerprint = null
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
        AppScaffold {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                WatchChatSurface(
                    transportMode = transportMode,
                    trustState = trustState,
                    fingerprint = localFingerprint,
                    pairingCode = pairingCode,
                    messages = messages,
                    onSelectMode = ::selectMode,
                    onSendQuickReply = ::sendQuickReply
                )
            }
        }
    }
}

@Composable
private fun WatchChatSurface(
    transportMode: TransportMode,
    trustState: String,
    fingerprint: String,
    pairingCode: String?,
    messages: List<ChatBubble>,
    onSelectMode: (TransportMode) -> Unit,
    onSendQuickReply: (String) -> Unit
) {
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
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
                .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        val compact = maxWidth < 220.dp
        val quickReplyHeight = if (compact) 30.dp else 34.dp

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatusHeader(
                trustState = trustState,
                transportMode = transportMode,
                compact = compact
            )

            Spacer(modifier = Modifier.height(if (compact) 6.dp else 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TransportToggle(
                    mode = TransportMode.Lan,
                    selected = transportMode == TransportMode.Lan,
                    onClick = { onSelectMode(TransportMode.Lan) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                TransportToggle(
                    mode = TransportMode.Bluetooth,
                    selected = transportMode == TransportMode.Bluetooth,
                    onClick = { onSelectMode(TransportMode.Bluetooth) }
                )
            }

            FingerprintPill(
                fingerprint = fingerprint,
                pairingCode = pairingCode
            )

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .heightIn(min = 62.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                messages.takeLast(5).forEach { message ->
                    MessageBubble(message = message)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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

@Composable
private fun StatusHeader(
    trustState: String,
    transportMode: TransportMode,
    compact: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(0.82f),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier =
                Modifier
                    .size(if (compact) 26.dp else 30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "端到端加密",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(if (compact) 15.dp else 17.dp)
            )
        }
        Spacer(modifier = Modifier.width(7.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = "SpotChat",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = if (compact) 16.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = "${transportMode.label} · $trustState",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = if (compact) 10.sp else 11.sp,
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
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(background)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = mode.icon,
            contentDescription = mode.label,
            tint = foreground,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = mode.label,
            color = foreground,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun FingerprintPill(
    fingerprint: String,
    pairingCode: String?
) {
    Row(
        modifier =
            Modifier
                .padding(top = 7.dp, bottom = 6.dp)
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0x332DE6D1))
                .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Key,
            contentDescription = "本机密钥指纹",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = pairingCode?.let { "校验 $it" } ?: fingerprint,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MessageBubble(message: ChatBubble) {
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
                    .fillMaxWidth(0.78f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(background)
                    .padding(horizontal = 9.dp, vertical = 7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.VerifiedUser,
                    contentDescription = if (message.encrypted) "已加密" else "未加密",
                    tint = foreground.copy(alpha = 0.78f),
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (message.encrypted) "E2EE" else "明文",
                    color = foreground.copy(alpha = 0.78f),
                    fontSize = 9.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = message.timestamp,
                    color = foreground.copy(alpha = 0.62f),
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
            Text(
                text = message.text,
                color = foreground,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickReplyChip(
    text: String,
    height: androidx.compose.ui.unit.Dp,
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
    height: androidx.compose.ui.unit.Dp,
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

private fun PeerHello.lanPort(): Int? =
    transports.firstNotNullOfOrNull { hint ->
        hint.removePrefix("lan:").takeIf { it != hint }?.toIntOrNull()
    }

private fun Throwable?.readableMessage(fallback: String): String =
    this?.message?.takeIf { it.isNotBlank() } ?: fallback
