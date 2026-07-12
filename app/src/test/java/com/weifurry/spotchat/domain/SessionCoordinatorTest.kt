package com.weifurry.spotchat.domain

import com.weifurry.spotchat.crypto.SpotChatCrypto
import com.weifurry.spotchat.transport.TransportKind
import com.weifurry.spotchat.transport.TransportPeer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCoordinatorTest {
    @Test
    fun helloSendingEnforcesRouteCooldownAndGlobalWindowCap() {
        var nowEpochMillis = 1_700_000_000_000L
        val coordinator = coordinator(nowEpochMillis = { nowEpochMillis })
        val firstRoute = peer(index = 1)

        assertTrue(coordinator.shouldSendHello(firstRoute))
        assertFalse(
            coordinator.shouldSendHello(
                firstRoute.copy(id = "same-route-new-id", port = 9999)
            )
        )
        nowEpochMillis += 9_999L
        assertFalse(coordinator.shouldSendHello(firstRoute))
        nowEpochMillis += 1L
        assertTrue(coordinator.shouldSendHello(firstRoute))

        coordinator.clearHandshakeState()
        val routes = (10..14).map(::peer)
        routes.take(4).forEach { route ->
            assertTrue(coordinator.shouldSendHello(route))
        }
        assertFalse(coordinator.shouldSendHello(routes.last()))

        nowEpochMillis += 9_999L
        assertFalse(coordinator.shouldSendHello(routes.last()))
        nowEpochMillis += 1L
        assertTrue(coordinator.shouldSendHello(routes.last()))
    }

    @Test
    fun handshakeRateLimitIsScopedToRouteAndResetsAtWindowBoundary() {
        var nowEpochMillis = 1_700_000_000_000L
        val coordinator = coordinator(nowEpochMillis = { nowEpochMillis })
        val route = peer(index = 1)

        repeat(4) {
            assertTrue(coordinator.allowHandshake(route))
        }
        assertFalse(
            coordinator.allowHandshake(
                route.copy(id = "same-route-new-id", port = 9999)
            )
        )
        assertTrue(
            coordinator.allowHandshake(
                route.copy(
                    id = "bluetooth-route",
                    kind = TransportKind.BLUETOOTH
                )
            )
        )

        nowEpochMillis += 9_999L
        assertFalse(coordinator.allowHandshake(route))
        nowEpochMillis += 1L
        assertTrue(coordinator.allowHandshake(route))
    }

    @Test
    fun incomingChallengeRequiresMatchingIdentityHelloAndClockWindow() {
        val nowEpochMillis = 1_700_000_000_000L
        val watchEngine = engine("watch")
        val phoneEngine = engine("phone")
        val coordinator = SessionCoordinator(watchEngine) { nowEpochMillis }
        val localHello =
            coordinator.helloPacket(transports = listOf("lan:test")).hello
                ?: error("missing local hello")
        val challenge =
            phoneEngine
                .sessionChallengePacket(
                    responderHello = localHello,
                    challengeId = "incoming-challenge",
                    createdAtEpochMillis = nowEpochMillis
                )
                .sessionChallenge
                ?: error("missing session challenge")

        assertTrue(coordinator.isValidIncomingChallenge(challenge, localHello))
        assertFalse(
            coordinator.isValidIncomingChallenge(
                challenge.copy(challengeId = ""),
                localHello
            )
        )
        assertFalse(
            coordinator.isValidIncomingChallenge(
                challenge.copy(responderFingerprint = "wrong-fingerprint"),
                localHello
            )
        )
        assertFalse(
            coordinator.isValidIncomingChallenge(
                challenge.copy(responderHello = localHello.copy(deviceName = "other-watch")),
                localHello
            )
        )
        assertTrue(
            coordinator.isValidIncomingChallenge(
                challenge.copy(createdAtEpochMillis = nowEpochMillis - 60_000L),
                localHello
            )
        )
        assertFalse(
            coordinator.isValidIncomingChallenge(
                challenge.copy(createdAtEpochMillis = nowEpochMillis - 60_001L),
                localHello
            )
        )
        assertTrue(
            coordinator.isValidIncomingChallenge(
                challenge.copy(createdAtEpochMillis = nowEpochMillis + 60_000L),
                localHello
            )
        )
        assertFalse(
            coordinator.isValidIncomingChallenge(
                challenge.copy(createdAtEpochMillis = nowEpochMillis + 60_001L),
                localHello
            )
        )
    }

    @Test
    fun preparedChallengesDeduplicateUntilDiscardedOrExpired() {
        var nowEpochMillis = 1_700_000_000_000L
        val watchEngine = engine("watch")
        val phoneEngine = engine("phone")
        val coordinator = SessionCoordinator(watchEngine) { nowEpochMillis }
        val phoneHello =
            phoneEngine.helloPacket().hello ?: error("missing phone hello")
        val openedPhone = coordinator.openSession(phoneHello)
        val route = peer(index = 1)

        val first =
            coordinator.prepareChallenge(
                peer = route,
                openedPeer = openedPhone,
                responderHello = phoneHello,
                transports = listOf("lan:test")
            )
        assertNotNull(first)
        first ?: error("missing first outgoing challenge")
        assertEquals(first.challengeId, first.packet.sessionChallenge?.challengeId)

        assertNull(
            coordinator.prepareChallenge(
                peer = route.copy(id = "same-route-new-id", port = 9999),
                openedPeer = openedPhone,
                responderHello = phoneHello,
                transports = listOf("lan:test")
            )
        )

        coordinator.discardChallenge(first.challengeId)
        val afterDiscard =
            coordinator.prepareChallenge(
                peer = route,
                openedPeer = openedPhone,
                responderHello = phoneHello,
                transports = listOf("lan:test")
            )
        assertNotNull(afterDiscard)
        afterDiscard ?: error("missing challenge after discard")
        assertNotEquals(first.challengeId, afterDiscard.challengeId)

        nowEpochMillis += 30_000L
        val afterExpiry =
            coordinator.prepareChallenge(
                peer = route,
                openedPeer = openedPhone,
                responderHello = phoneHello,
                transports = listOf("lan:test")
            )
        assertNotNull(afterExpiry)
        afterExpiry ?: error("missing challenge after expiry")
        assertNotEquals(afterDiscard.challengeId, afterExpiry.challengeId)
    }

    @Test
    fun confirmationMatchingRejectsWrongRouteAndInvalidPayloadWithoutConsumingPending() {
        val nowEpochMillis = System.currentTimeMillis()
        val watchEngine = engine("watch")
        val phoneEngine = engine("phone")
        val watchCoordinator = SessionCoordinator(watchEngine) { nowEpochMillis }
        val phoneCoordinator = SessionCoordinator(phoneEngine) { nowEpochMillis }
        val phoneHello =
            phoneCoordinator.helloPacket(transports = listOf("lan:test")).hello
                ?: error("missing phone hello")
        val openedPhone = watchCoordinator.openSession(phoneHello)
        val route = peer(index = 1)
        val outgoing =
            watchCoordinator.prepareChallenge(
                peer = route,
                openedPeer = openedPhone,
                responderHello = phoneHello,
                transports = listOf("lan:test")
            ) ?: error("missing outgoing challenge")
        val challenge =
            outgoing.packet.sessionChallenge ?: error("missing session challenge")
        val openedWatch = phoneCoordinator.openSession(challenge.challengerHello)
        val encryptedConfirmation =
            phoneCoordinator
                .createConfirmation(
                    peerFingerprint = openedWatch.fingerprint,
                    challenge = challenge
                )
                .encryptedMessage
                ?: error("missing encrypted confirmation")

        assertEquals(
            SessionCoordinator.ConfirmationMatch.RouteMismatch,
            watchCoordinator.matchConfirmation(
                encryptedConfirmation = encryptedConfirmation,
                peer = route.copy(address = "192.0.2.200")
            )
        )

        val firstMatch =
            watchCoordinator.matchConfirmation(
                encryptedConfirmation = encryptedConfirmation,
                peer = route
            )
        assertTrue(firstMatch is SessionCoordinator.ConfirmationMatch.Found)
        val firstPending =
            (firstMatch as SessionCoordinator.ConfirmationMatch.Found).pending
        val confirmation = watchCoordinator.decryptConfirmation(encryptedConfirmation)
        val invalidConfirmation = confirmation.copy(responderFingerprint = "wrong-fingerprint")

        assertFalse(
            watchCoordinator.validateAndConsumeConfirmation(
                encryptedConfirmation = encryptedConfirmation,
                confirmation = invalidConfirmation,
                pending = firstPending
            )
        )

        val secondMatch =
            watchCoordinator.matchConfirmation(
                encryptedConfirmation = encryptedConfirmation,
                peer = route
            )
        assertTrue(secondMatch is SessionCoordinator.ConfirmationMatch.Found)
        val secondPending =
            (secondMatch as SessionCoordinator.ConfirmationMatch.Found).pending
        assertTrue(
            watchCoordinator.validateAndConsumeConfirmation(
                encryptedConfirmation = encryptedConfirmation,
                confirmation = confirmation,
                pending = secondPending
            )
        )
        assertEquals(
            SessionCoordinator.ConfirmationMatch.Missing,
            watchCoordinator.matchConfirmation(
                encryptedConfirmation = encryptedConfirmation,
                peer = route
            )
        )

        watchCoordinator.confirmSession(openedPhone.fingerprint)
        phoneCoordinator.confirmSession(openedWatch.fingerprint)
    }

    private fun coordinator(nowEpochMillis: () -> Long): SessionCoordinator =
        SessionCoordinator(engine("local"), nowEpochMillis)

    private fun engine(deviceName: String): SpotChatEngine =
        SpotChatEngine(deviceName, SpotChatCrypto.generateIdentity())

    private fun peer(
        index: Int,
        kind: TransportKind = TransportKind.LAN
    ): TransportPeer =
        TransportPeer(
            id = "peer-$index",
            name = "Peer $index",
            address = "192.0.2.$index",
            port = 4_000 + index,
            kind = kind
        )
}
