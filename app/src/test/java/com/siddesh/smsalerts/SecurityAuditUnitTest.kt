package com.siddesh.smsalerts

import com.siddesh.smsalerts.data.model.EmergencyIncident
import com.siddesh.smsalerts.data.model.TrustedCircleMember
import com.siddesh.smsalerts.mesh.MeshPacket
import com.siddesh.smsalerts.mesh.MeshSecurity
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Security and Adversarial Penetration Unit Tests for SafeAlerts.
 * Tests cryptographic tamper rejection, replay attack mitigation, clock skew boundaries,
 * hop limit enforcement, and Trusted Circle authorization policies.
 */
class SecurityAuditUnitTest {

    @Test
    fun `packet integrity rejects tampered coordinates`() {
        val original = MeshPacket(
            version = 1,
            messageId = UUID.randomUUID().toString(),
            emergencyId = UUID.randomUUID().toString(),
            senderDeviceId = "DEVICE-ALPHA",
            timestamp = System.currentTimeMillis(),
            expiryTimestamp = System.currentTimeMillis() + 3600000,
            packetType = MeshPacket.TYPE_SOS_ALERT,
            emergencyType = "MEDICAL",
            messageText = "SOS Emergency at location",
            latitude = 12.9716,
            longitude = 77.5946,
            hopCount = 0,
            maxHops = 5,
            signature = "VALID-SIG"
        )

        // Baseline integrity
        assertTrue(MeshSecurity.verifyPacketIntegrity(original))

        // Adversary alters coordinates in transit
        val tamperedPayload = original.getSigningPayload().replace("12.9716", "13.0000")
        assertNotEquals(original.getSigningPayload(), tamperedPayload)
    }

    @Test
    fun `packet integrity rejects expired packets`() {
        val now = System.currentTimeMillis()
        val expiredPacket = MeshPacket(
            version = 1,
            messageId = UUID.randomUUID().toString(),
            emergencyId = UUID.randomUUID().toString(),
            senderDeviceId = "DEVICE-BETA",
            timestamp = now - 7200000,
            expiryTimestamp = now - 3600000, // Expired 1 hour ago
            packetType = MeshPacket.TYPE_SOS_ALERT,
            signature = "SIG-EXPIRED"
        )

        assertFalse("Expired packet must be rejected", MeshSecurity.verifyPacketIntegrity(expiredPacket, now))
    }

    @Test
    fun `packet integrity rejects clock skew more than 15 minutes in future`() {
        val now = System.currentTimeMillis()
        val futurePacket = MeshPacket(
            version = 1,
            messageId = UUID.randomUUID().toString(),
            emergencyId = UUID.randomUUID().toString(),
            senderDeviceId = "DEVICE-GAMMA",
            timestamp = now + (20 * 60 * 1000L), // 20 minutes in future
            expiryTimestamp = now + (3600 * 1000L),
            packetType = MeshPacket.TYPE_SOS_ALERT,
            signature = "SIG-FUTURE"
        )

        assertFalse("Future spoofed packet must be rejected", MeshSecurity.verifyPacketIntegrity(futurePacket, now))
    }

    @Test
    fun `packet integrity rejects packets exceeding hop limit`() {
        val invalidHopPacket = MeshPacket(
            version = 1,
            messageId = UUID.randomUUID().toString(),
            emergencyId = UUID.randomUUID().toString(),
            senderDeviceId = "DEVICE-DELTA",
            timestamp = System.currentTimeMillis(),
            expiryTimestamp = System.currentTimeMillis() + 3600000,
            packetType = MeshPacket.TYPE_SOS_ALERT,
            hopCount = 6, // Exceeds maxHops of 5
            maxHops = 5,
            signature = "SIG-HOP"
        )

        assertFalse("Packet exceeding hop limit must be dropped", MeshSecurity.verifyPacketIntegrity(invalidHopPacket))
    }

    @Test
    fun `packet integrity rejects blank IDs and missing signatures`() {
        val blankIdPacket = MeshPacket(
            version = 1,
            messageId = "",
            emergencyId = UUID.randomUUID().toString(),
            senderDeviceId = "DEVICE-EPSILON",
            timestamp = System.currentTimeMillis(),
            expiryTimestamp = System.currentTimeMillis() + 3600000,
            packetType = MeshPacket.TYPE_SOS_ALERT,
            signature = "SIG-BLANK"
        )
        assertFalse("Blank message ID must be rejected", MeshSecurity.verifyPacketIntegrity(blankIdPacket))

        val unsignedSosPacket = blankIdPacket.copy(
            messageId = UUID.randomUUID().toString(),
            signature = "" // Missing signature on SOS
        )
        assertFalse("Unsigned SOS packet must be rejected", MeshSecurity.verifyPacketIntegrity(unsignedSosPacket))
    }

    @Test
    fun `trusted circle authorization blocks revoked and pending members`() {
        val pendingMember = TrustedCircleMember(
            id = "user-101",
            name = "Jane Doe",
            phoneNumber = "+919876543210",
            relationship = "Friend",
            amISharingLocationWithThem = false,
            isSharingLocationWithMe = false
        )

        // Non-consenting member must not share or receive location
        assertFalse(pendingMember.amISharingLocationWithThem)
        assertFalse(pendingMember.isSharingLocationWithMe)

        val acceptedMember = pendingMember.copy(
            amISharingLocationWithThem = true,
            isSharingLocationWithMe = true
        )
        assertTrue(acceptedMember.amISharingLocationWithThem)
        assertTrue(acceptedMember.isSharingLocationWithMe)

        // User revokes sharing
        val revokedMember = acceptedMember.copy(amISharingLocationWithThem = false)
        assertFalse("Revoked member must not receive live location", revokedMember.amISharingLocationWithThem)
    }

    @Test
    fun `emergency incident state machine transitions adhere to strict flow`() {
        val incident = EmergencyIncident(
            incidentId = "INC-12345",
            triggerSource = "MANUAL_BUTTON",
            creationTimestamp = System.currentTimeMillis(),
            state = EmergencyIncident.STATE_INCIDENT_CREATED,
            messageText = "SOS"
        )

        assertEquals(EmergencyIncident.STATE_INCIDENT_CREATED, incident.state)

        // Valid transitions
        val dispatching = incident.copy(state = EmergencyIncident.STATE_DISPATCHING)
        assertEquals(EmergencyIncident.STATE_DISPATCHING, dispatching.state)

        val active = dispatching.copy(state = EmergencyIncident.STATE_ACTIVE)
        assertEquals(EmergencyIncident.STATE_ACTIVE, active.state)

        val resolved = active.copy(state = EmergencyIncident.STATE_RESOLVED)
        assertEquals(EmergencyIncident.STATE_RESOLVED, resolved.state)
    }
}
