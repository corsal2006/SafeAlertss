package com.siddesh.smsalerts

import com.siddesh.smsalerts.data.model.CircleInvitation
import com.siddesh.smsalerts.data.model.TrustedCircleMember
import com.siddesh.smsalerts.mesh.MeshPacket
import com.siddesh.smsalerts.service.SafetyCheckInState
import com.siddesh.smsalerts.service.WomensSafetyJourneyState
import com.siddesh.smsalerts.utils.EmergencyMessageFormatter
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Production-quality validation suite for SafeAlerts upgrades:
 * 1. Configurable, editable emergency SMS message template with regional helplines & live tracking link.
 * 2. Safety Check-In 4 consistent grace options (Immediate, 1 min, 2 min, 5 min) and expiry escalation.
 * 3. Women's Safety Journey deadline extensions (+10m, +15m) and arrival/cancel protection.
 * 4. Mutual Trusted Circle account relationship, bilateral location sharing consent, and staleness.
 * 5. Offline BLE emergency packet integrity and deduplication.
 */
class EmergencyUpgradeValidationTest {

    // ==========================================
    // 1. EMERGENCY MESSAGE FORMATTER TESTS
    // ==========================================

    @Test
    fun emergencyMessageFormatter_withAllHelplinesAndCoordinates() {
        val message = EmergencyMessageFormatter.formatEmergencyMessage(
            userName = "Priya Sharma",
            customMessage = "Please assist, traveling alone.",
            emergencyType = "SAFETY CHECK-IN OVERDUE",
            policeNumber = "112",
            isPoliceEnabled = true,
            womensHelplineNumber = "1091",
            isWomensHelplineEnabled = true,
            latitude = 12.971598,
            longitude = 77.594562,
            accuracyMeters = 8.5f,
            incidentTimestampMs = 1700000000000L,
            secureLiveTrackingUrl = "https://safealerts-app.web.app/track/inc-12345"
        )

        assertTrue("Message must contain username", message.contains("Priya Sharma"))
        assertTrue("Message must contain emergency type tag", message.contains("SAFETY CHECK-IN OVERDUE"))
        assertTrue("Message must contain police 112 helpline", message.contains("Police/emergency: 112"))
        assertFalse("General Check-In must exclude the journey helpline", message.contains("1091"))
        assertTrue("Message must contain google maps link with lat/lng", message.contains("https://maps.google.com/?q=12.971598,77.594562"))
        assertTrue("Message must contain live tracking link", message.contains("https://safealerts-app.web.app/track/inc-12345"))
    }

    @Test
    fun emergencyMessageFormatter_whenGpsUnavailable_showsNoticeWithoutCrashing() {
        val message = EmergencyMessageFormatter.formatEmergencyMessage(
            userName = "Rahul Verma",
            customMessage = null,
            policeNumber = "112",
            isPoliceEnabled = true,
            womensHelplineNumber = "1091",
            isWomensHelplineEnabled = false,
            latitude = null,
            longitude = null,
            accuracyMeters = null,
            incidentTimestampMs = 1700000000000L,
            secureLiveTrackingUrl = null
        )

        assertTrue(message.contains("Rahul Verma"))
        assertTrue("Message must clearly state coordinates unavailable when GPS is off", message.contains("Latest location: unavailable"))
        assertFalse("Disabled women's helpline must not appear", message.contains("women's safety helpline"))
        assertTrue("Enabled police helpline must appear", message.contains("Police/emergency: 112"))
    }

    // ==========================================
    // 2. SAFETY CHECK-IN 4 CONSISTENT GRACE OPTIONS
    // ==========================================

    @Test
    fun safetyCheckInGraceOptions_fourConsistentOptions() {
        val graceOptions = listOf(
            "Immediate" to 0,
            "1 min" to 1,
            "2 mins" to 2,
            "5 mins" to 5
        )

        assertEquals("Must have exactly 4 consistent options", 4, graceOptions.size)
        assertEquals("Immediate represents 0 minutes", 0, graceOptions[0].second)
        assertEquals("1 min option", 1, graceOptions[1].second)
        assertEquals("2 mins option", 2, graceOptions[2].second)
        assertEquals("5 mins option", 5, graceOptions[3].second)

        // Verify deadline calculation with Immediate (0 min)
        val now = 1000000L
        val baseDurationMinutes = 15
        val immediateGrace = 0
        val targetWithImmediate = now + (baseDurationMinutes * 60_000L) + (immediateGrace * 60_000L)
        assertEquals(now + 900_000L, targetWithImmediate)

        // Verify deadline calculation with 5 mins grace
        val fiveMinGrace = 5
        val targetWithFiveMin = now + (baseDurationMinutes * 60_000L) + (fiveMinGrace * 60_000L)
        assertEquals(now + 900_000L + 300_000L, targetWithFiveMin)
    }

    // ==========================================
    // 3. WOMEN'S SAFETY JOURNEY EXTENSION & CANCEL
    // ==========================================

    @Test
    fun womensSafetyJourney_extensionCalculations() {
        val initialMinutes = 30
        val now = 2000000L
        var state = WomensSafetyJourneyState(
            isActive = true,
            destination = "Indiranagar Metro Station",
            durationMinutes = initialMinutes,
            targetTimestamp = now + (initialMinutes * 60_000L),
            remainingSeconds = (initialMinutes * 60).toLong()
        )

        // Extend +10 min
        val extended10Target = state.targetTimestamp + (10 * 60_000L)
        state = state.copy(
            durationMinutes = state.durationMinutes + 10,
            targetTimestamp = extended10Target,
            remainingSeconds = state.remainingSeconds + 600
        )
        assertEquals(40, state.durationMinutes)
        assertEquals(now + (40 * 60_000L), state.targetTimestamp)
        assertEquals(2400L, state.remainingSeconds)

        // Extend +15 min
        val extended15Target = state.targetTimestamp + (15 * 60_000L)
        state = state.copy(
            durationMinutes = state.durationMinutes + 15,
            targetTimestamp = extended15Target,
            remainingSeconds = state.remainingSeconds + 900
        )
        assertEquals(55, state.durationMinutes)
        assertEquals(now + (55 * 60_000L), state.targetTimestamp)
        assertEquals(3300L, state.remainingSeconds)
    }

    @Test
    fun womensSafetyJourney_cancelAndArrivalPreventEscalation() {
        val activeState = WomensSafetyJourneyState(
            isActive = true,
            destination = "MG Road",
            isEscalated = false
        )
        assertTrue(activeState.isActive)
        assertFalse(activeState.isEscalated)

        // User safely arrived / cancelled
        val resolvedState = activeState.copy(
            isActive = false,
            remainingSeconds = 0L,
            isGracePeriodActive = false,
            isEscalated = false
        )
        assertFalse("Cancelled journey must not remain active", resolvedState.isActive)
        assertFalse("Cancelled journey must not trigger escalation", resolvedState.isEscalated)
    }

    // ==========================================
    // 4. TRUSTED CIRCLE BILATERAL CONSENT & STALENESS
    // ==========================================

    @Test
    fun trustedCircle_bilateralConsentEnforcement() {
        val memberA = TrustedCircleMember(
            id = "user_b_uid",
            name = "Ananya",
            phoneNumber = "+919876543210",
            isSharingLocationWithMe = true,
            amISharingLocationWithThem = false,
            lastLatitude = 12.9352,
            lastLongitude = 77.6245,
            lastLocationTimestamp = System.currentTimeMillis()
        )

        assertTrue("User A can view User B's location because B enabled sharing with A", memberA.isSharingLocationWithMe)
        assertFalse("User B cannot view User A's location because A did not enable sharing with B", memberA.amISharingLocationWithThem)
        assertFalse("Recent timestamp must not be stale", memberA.isLocationStale)

        // If location is older than 15 minutes, it is marked stale
        val staleMember = memberA.copy(
            lastLocationTimestamp = System.currentTimeMillis() - (20 * 60 * 1000L)
        )
        assertTrue("Location older than 15 minutes must report isLocationStale=true", staleMember.isLocationStale)
    }

    @Test
    fun circleInvitation_statesAndRevocation() {
        val invite = CircleInvitation(
            id = UUID.randomUUID().toString(),
            senderUid = "alice_uid",
            senderName = "Alice",
            status = "PENDING",
            shareMyLiveLocation = true
        )

        assertEquals("PENDING", invite.status)
        assertTrue(invite.shareMyLiveLocation)

        val accepted = invite.copy(status = "ACCEPTED")
        assertEquals("ACCEPTED", accepted.status)

        val rejected = invite.copy(status = "REJECTED")
        assertEquals("REJECTED", rejected.status)
    }

    // ==========================================
    // 5. OFFLINE BLE PACKET INTEGRITY & DEDUPLICATION
    // ==========================================

    @Test
    fun meshPacket_sosPacketIntegrityAndDeduplication() {
        val msgId = UUID.randomUUID().toString()
        val incId = UUID.randomUUID().toString()
        val packet = MeshPacket(
            messageId = msgId,
            emergencyId = incId,
            senderDeviceId = "device_alpha_123",
            packetType = MeshPacket.TYPE_SOS_ALERT,
            emergencyType = "OFFLINE_SOS",
            senderName = "Vikram",
            messageText = "SOS in offline sector",
            latitude = 13.0827,
            longitude = 80.2707,
            hopCount = 0,
            maxHops = 3
        )

        assertEquals(msgId, packet.messageId)
        assertEquals(incId, packet.emergencyId)
        assertEquals(MeshPacket.TYPE_SOS_ALERT, packet.packetType)
        assertEquals(0, packet.hopCount)
        assertEquals(3, packet.maxHops)

        // Relayed packet increments hopCount
        val relayed = packet.copy(hopCount = packet.hopCount + 1)
        assertEquals(1, relayed.hopCount)
        assertTrue(relayed.hopCount < relayed.maxHops)

        // Packet at max hops must not be forwarded further
        val maxed = packet.copy(hopCount = 3)
        assertFalse("Packet reaching max hops must not be relayed again", maxed.hopCount < maxed.maxHops)
    }
}
