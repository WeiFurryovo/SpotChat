package com.weifurry.spotchat.domain

import com.weifurry.spotchat.protocol.EncryptedChatMessage
import com.weifurry.spotchat.protocol.PeerHello
import com.weifurry.spotchat.protocol.SessionChallenge
import com.weifurry.spotchat.protocol.SessionConfirmationPayload
import com.weifurry.spotchat.protocol.WirePacket
import com.weifurry.spotchat.transport.TransportPeer

internal class SessionCoordinator(
    private val engine: SpotChatEngine,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) {
    internal data class PendingChallenge(
        val peer: TransportPeer,
        val openedPeer: TrustedPeer,
        val challenge: SessionChallenge,
        val expiresAtEpochMillis: Long
    )

    internal data class OutgoingChallenge(
        val packet: WirePacket,
        val challengeId: String
    )

    internal sealed interface ConfirmationMatch {
        data object Missing : ConfirmationMatch

        data object RouteMismatch : ConfirmationMatch

        data class Found(val pending: PendingChallenge) : ConfirmationMatch
    }

    private val helloSentAtByRoute = LinkedHashMap<String, Long>()
    private val helloSendAttempts = mutableListOf<Long>()
    private val pendingSessionChallenges = LinkedHashMap<String, PendingChallenge>()
    private val handshakeAttemptsByRoute = LinkedHashMap<String, MutableList<Long>>()

    fun helloPacket(transports: List<String>): WirePacket =
        engine.helloPacket(transports = transports)

    fun openSession(hello: PeerHello): TrustedPeer = engine.openSession(hello)

    fun shouldSendHello(
        peer: TransportPeer,
        nowEpochMillis: Long = this.nowEpochMillis()
    ): Boolean {
        val cutoff = nowEpochMillis - HELLO_RATE_WINDOW_MS
        helloSendAttempts.removeAll { attemptedAt -> attemptedAt <= cutoff }
        helloSentAtByRoute
            .filterValues { sentAt -> sentAt <= nowEpochMillis - HELLO_ROUTE_COOLDOWN_MS }
            .keys
            .toList()
            .forEach(helloSentAtByRoute::remove)
        val routeKey = routeKey(peer)
        if (routeKey in helloSentAtByRoute || helloSendAttempts.size >= MAX_HELLO_SENDS_PER_WINDOW) {
            return false
        }
        if (helloSentAtByRoute.size >= MAX_HELLO_ROUTES) {
            helloSentAtByRoute.entries
                .minByOrNull { (_, sentAt) -> sentAt }
                ?.key
                ?.let(helloSentAtByRoute::remove)
        }
        helloSentAtByRoute[routeKey] = nowEpochMillis
        helloSendAttempts += nowEpochMillis
        return true
    }

    fun allowHandshake(
        peer: TransportPeer,
        nowEpochMillis: Long = this.nowEpochMillis()
    ): Boolean {
        val cutoff = nowEpochMillis - HANDSHAKE_RATE_WINDOW_MS
        handshakeAttemptsByRoute.values.forEach { attempts ->
            attempts.removeAll { attemptedAt -> attemptedAt <= cutoff }
        }
        handshakeAttemptsByRoute
            .filterValues { attempts -> attempts.isEmpty() }
            .keys
            .toList()
            .forEach(handshakeAttemptsByRoute::remove)
        val routeKey = routeKey(peer)
        if (
            routeKey !in handshakeAttemptsByRoute &&
            handshakeAttemptsByRoute.size >= MAX_HANDSHAKE_ROUTES
        ) {
            handshakeAttemptsByRoute.entries
                .minByOrNull { (_, attempts) -> attempts.lastOrNull() ?: Long.MIN_VALUE }
                ?.key
                ?.let(handshakeAttemptsByRoute::remove)
        }
        val attempts = handshakeAttemptsByRoute.getOrPut(routeKey) { mutableListOf() }
        if (attempts.size >= MAX_HANDSHAKE_ATTEMPTS_PER_ROUTE) {
            return false
        }
        attempts += nowEpochMillis
        return true
    }

    fun isValidIncomingChallenge(
        challenge: SessionChallenge,
        localHello: PeerHello,
        nowEpochMillis: Long = this.nowEpochMillis()
    ): Boolean =
        challenge.challengeId.isNotBlank() &&
            challenge.responderFingerprint == engine.localFingerprint &&
            challenge.responderHello == localHello &&
            challenge.createdAtEpochMillis >= nowEpochMillis - SESSION_CHALLENGE_CLOCK_SKEW_MS &&
            challenge.createdAtEpochMillis <= nowEpochMillis + SESSION_CHALLENGE_CLOCK_SKEW_MS

    fun prepareChallenge(
        peer: TransportPeer,
        openedPeer: TrustedPeer,
        responderHello: PeerHello,
        transports: List<String>
    ): OutgoingChallenge? {
        pruneSessionChallenges()
        if (
            pendingSessionChallenges.values.any { pending ->
                pending.openedPeer.fingerprint == openedPeer.fingerprint &&
                    samePeerRoute(pending.peer, peer)
            }
        ) {
            return null
        }
        while (pendingSessionChallenges.size >= MAX_PENDING_SESSION_CHALLENGES) {
            val oldestChallengeId = pendingSessionChallenges.keys.firstOrNull() ?: break
            pendingSessionChallenges.remove(oldestChallengeId)
        }
        val packet =
            engine.sessionChallengePacket(
                responderHello = responderHello,
                transports = transports
            )
        val challenge = packet.sessionChallenge ?: error("Missing session challenge")
        pendingSessionChallenges[challenge.challengeId] =
            PendingChallenge(
                peer = peer,
                openedPeer = openedPeer,
                challenge = challenge,
                expiresAtEpochMillis = nowEpochMillis() + SESSION_CHALLENGE_TTL_MS
            )
        return OutgoingChallenge(
            packet = packet,
            challengeId = challenge.challengeId
        )
    }

    fun discardChallenge(challengeId: String) {
        pendingSessionChallenges.remove(challengeId)
    }

    fun matchConfirmation(
        encryptedConfirmation: EncryptedChatMessage,
        peer: TransportPeer
    ): ConfirmationMatch {
        pruneSessionChallenges()
        val pending =
            pendingSessionChallenges[encryptedConfirmation.messageId]
                ?: return ConfirmationMatch.Missing
        return if (samePeerRoute(pending.peer, peer)) {
            ConfirmationMatch.Found(pending)
        } else {
            ConfirmationMatch.RouteMismatch
        }
    }

    fun decryptConfirmation(
        encryptedConfirmation: EncryptedChatMessage
    ): SessionConfirmationPayload =
        engine.decryptSessionConfirmation(encryptedConfirmation)

    fun validateAndConsumeConfirmation(
        encryptedConfirmation: EncryptedChatMessage,
        confirmation: SessionConfirmationPayload,
        pending: PendingChallenge
    ): Boolean {
        val valid =
            confirmation.challengeId == encryptedConfirmation.messageId &&
                confirmation.challengeBinding == engine.sessionChallengeBinding(pending.challenge) &&
                confirmation.challengerFingerprint == engine.localFingerprint &&
                confirmation.responderFingerprint == pending.openedPeer.fingerprint &&
                encryptedConfirmation.senderFingerprint == pending.openedPeer.fingerprint
        if (valid) {
            pendingSessionChallenges.remove(confirmation.challengeId)
        }
        return valid
    }

    fun createConfirmation(
        peerFingerprint: String,
        challenge: SessionChallenge
    ): WirePacket =
        engine.encryptSessionConfirmationForPeer(
            peerFingerprint = peerFingerprint,
            challenge = challenge
        )

    fun confirmSession(peerFingerprint: String) {
        engine.confirmSession(peerFingerprint)
    }

    fun protectPendingSessionForTrust(peerFingerprint: String) {
        engine.protectPendingSessionForTrust(peerFingerprint)
    }

    fun rejectPendingSession(peerFingerprint: String) {
        engine.rejectPendingSession(peerFingerprint)
    }

    fun clearHandshakeState() {
        helloSentAtByRoute.clear()
        helloSendAttempts.clear()
        pendingSessionChallenges.clear()
        handshakeAttemptsByRoute.clear()
    }

    private fun pruneSessionChallenges(nowEpochMillis: Long = this.nowEpochMillis()) {
        pendingSessionChallenges
            .filterValues { challenge -> challenge.expiresAtEpochMillis <= nowEpochMillis }
            .keys
            .toList()
            .forEach(pendingSessionChallenges::remove)
    }

    private fun routeKey(peer: TransportPeer): String = "${peer.kind}:${peer.address}"

    private fun samePeerRoute(
        first: TransportPeer,
        second: TransportPeer
    ): Boolean =
        first.kind == second.kind && first.address == second.address

    private companion object {
        const val SESSION_CHALLENGE_TTL_MS = 30_000L
        const val SESSION_CHALLENGE_CLOCK_SKEW_MS = 60_000L
        const val MAX_PENDING_SESSION_CHALLENGES = 32
        const val MAX_HANDSHAKE_ATTEMPTS_PER_ROUTE = 4
        const val MAX_HANDSHAKE_ROUTES = 64
        const val HANDSHAKE_RATE_WINDOW_MS = 10_000L
        const val HELLO_ROUTE_COOLDOWN_MS = 10_000L
        const val HELLO_RATE_WINDOW_MS = 10_000L
        const val MAX_HELLO_SENDS_PER_WINDOW = 4
        const val MAX_HELLO_ROUTES = 64
    }
}
