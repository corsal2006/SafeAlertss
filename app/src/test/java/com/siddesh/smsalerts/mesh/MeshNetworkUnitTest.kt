package com.siddesh.smsalerts.mesh

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MeshNetworkUnitTest {

    @Test
    fun meshPacket_serializationAndDeserialization_preservesAllFields() {
        val mid = UUID.randomUUID().toString()
        val eid = UUID.randomUUID().toString()
        val sid = "device_alpha_1"
        val timestamp = 1728000000000L
        val expiry = timestamp + 86400000L

        val packet = MeshPacket(
            version = 1,
            messageId = mid,
            emergencyId = eid,
            senderDeviceId = sid,
            recipientId = "BROADCAST",
            packetType = MeshPacket.TYPE_SOS_ALERT,
            timestamp = timestamp,
            expiryTimestamp = expiry,
            hopCount = 1,
            maxHops = 5,
            emergencyType = "MEDICAL",
            senderName = "Alice",
            messageText = "I need urgent medical attention.",
            latitude = 12.9716,
            longitude = 77.5946,
            accuracy = 4.5f,
            locationTimestamp = timestamp,
            signature = "sig_hmac_abc123"
        )

        val jsonStr = packet.toJson()
        val parsed = MeshPacket.fromJson(jsonStr)

        assertNotNull(parsed)
        assertEquals(1, parsed!!.version)
        assertEquals(mid, parsed.messageId)
        assertEquals(eid, parsed.emergencyId)
        assertEquals(sid, parsed.senderDeviceId)
        assertEquals("BROADCAST", parsed.recipientId)
        assertEquals(MeshPacket.TYPE_SOS_ALERT, parsed.packetType)
        assertEquals(timestamp, parsed.timestamp)
        assertEquals(expiry, parsed.expiryTimestamp)
        assertEquals(1, parsed.hopCount)
        assertEquals(5, parsed.maxHops)
        assertEquals("MEDICAL", parsed.emergencyType)
        assertEquals("Alice", parsed.senderName)
        assertEquals("I need urgent medical attention.", parsed.messageText)
        assertEquals(12.9716, parsed.latitude!!, 0.0001)
        assertEquals(77.5946, parsed.longitude!!, 0.0001)
        assertEquals(4.5f, parsed.accuracy!!, 0.01f)
        assertEquals("sig_hmac_abc123", parsed.signature)
    }

    @Test
    fun meshPacket_ttlExpiryEvaluation() {
        val now = 1728000000000L
        val validPacket = MeshPacket(
            senderDeviceId = "dev_1",
            timestamp = now,
            expiryTimestamp = now + 60000L
        )
        assertFalse(validPacket.isExpired(now))
        assertFalse(validPacket.isExpired(now + 30000L))
        assertTrue(validPacket.isExpired(now + 60001L))

        val expiredPacket = MeshPacket(
            senderDeviceId = "dev_1",
            timestamp = now - 100000L,
            expiryTimestamp = now - 1000L
        )
        assertTrue(expiredPacket.isExpired(now))
        assertFalse(expiredPacket.canForward())
    }

    @Test
    fun meshPacket_hopCountAndForwardingLimits() {
        val packet = MeshPacket(
            senderDeviceId = "dev_1",
            hopCount = 0,
            maxHops = 3
        )
        assertTrue(packet.canForward())

        val hop1 = packet.createForwardedHop()
        assertEquals(1, hop1.hopCount)
        assertTrue(hop1.canForward())

        val hop2 = hop1.createForwardedHop()
        assertEquals(2, hop2.hopCount)
        assertTrue(hop2.canForward())

        val hop3 = hop2.createForwardedHop()
        assertEquals(3, hop3.hopCount)
        // Reached max hops -> canForward must be false
        assertFalse(hop3.canForward())
    }

    @Test
    fun meshDeduplicator_preventsReplayAndLoops() {
        val deduplicator = MeshDeduplicator(maxCapacity = 100, retentionPeriodMs = 60000L)
        val msg1 = "msg_001"
        val msg2 = "msg_002"
        val now = 1000L

        assertFalse(deduplicator.isDuplicate(msg1, now))
        assertFalse(deduplicator.recordAndCheckDuplicate(msg1, now))

        // Subsequent check of same messageId must report duplicate
        assertTrue(deduplicator.isDuplicate(msg1, now + 100))
        assertTrue(deduplicator.recordAndCheckDuplicate(msg1, now + 200))

        // Different message ID is not a duplicate
        assertFalse(deduplicator.isDuplicate(msg2, now))
        assertFalse(deduplicator.recordAndCheckDuplicate(msg2, now))
        assertEquals(2, deduplicator.size)

        // After retention period expires (now + 70000L)
        deduplicator.pruneIfNecessary(now + 70000L)
        assertEquals(0, deduplicator.size)
        assertFalse(deduplicator.isDuplicate(msg1, now + 70000L))
    }

    @Test
    fun meshSecurity_verifiesIntegrityAndRejectsMalformed() {
        val now = 1728000000000L
        val validPacket = MeshPacket(
            version = 1,
            messageId = "valid_m1",
            emergencyId = "valid_e1",
            senderDeviceId = "valid_d1",
            packetType = MeshPacket.TYPE_SOS_ALERT,
            timestamp = now,
            expiryTimestamp = now + 3600000L,
            hopCount = 0,
            maxHops = 5,
            signature = "sig_valid"
        )
        assertTrue(MeshSecurity.verifyPacketIntegrity(validPacket, now))

        // Corrupted packet: wrong version
        assertFalse(MeshSecurity.verifyPacketIntegrity(validPacket.copy(version = 99), now))

        // Corrupted packet: blank sender ID
        assertFalse(MeshSecurity.verifyPacketIntegrity(validPacket.copy(senderDeviceId = ""), now))

        // Corrupted packet: blank message ID
        assertFalse(MeshSecurity.verifyPacketIntegrity(validPacket.copy(messageId = "   "), now))

        // Replay/Spoof: Timestamp 30 minutes in future (exceeds 15 min clock skew threshold)
        val futureSpoof = validPacket.copy(timestamp = now + 30 * 60 * 1000L)
        assertFalse(MeshSecurity.verifyPacketIntegrity(futureSpoof, now))

        // Expired packet
        val expired = validPacket.copy(expiryTimestamp = now - 5000L)
        assertFalse(MeshSecurity.verifyPacketIntegrity(expired, now))

        // Negative hop count or exceeded hop count
        assertFalse(MeshSecurity.verifyPacketIntegrity(validPacket.copy(hopCount = -1), now))
        assertFalse(MeshSecurity.verifyPacketIntegrity(validPacket.copy(hopCount = 6, maxHops = 5), now))

        // Missing signature on SOS alert
        assertFalse(MeshSecurity.verifyPacketIntegrity(validPacket.copy(signature = ""), now))
    }
}
