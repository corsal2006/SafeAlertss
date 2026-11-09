package com.siddesh.smsalerts

import com.siddesh.smsalerts.data.model.TrustedCircleMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedCircleUnitTest {

    @Test
    fun trustedCircleMember_locationStaleness_freshWithinOneMinute() {
        val now = System.currentTimeMillis()
        val member = TrustedCircleMember(
            name = "Priya Sharma",
            phoneNumber = "+919876543210",
            relationship = "Sister",
            lastLatitude = 12.9716,
            lastLongitude = 77.5946,
            lastAccuracyMeters = 8.5f,
            lastLocationTimestamp = now - 15_000L // one target update interval ago
        )

        assertFalse("Location from 15 seconds ago should be fresh", member.isLocationStale)
    }

    @Test
    fun trustedCircleMember_locationStaleness_staleAfterOneMinute() {
        val now = System.currentTimeMillis()
        val member = TrustedCircleMember(
            name = "Rahul Verma",
            phoneNumber = "+919876543211",
            relationship = "Brother",
            lastLatitude = 12.9716,
            lastLongitude = 77.5946,
            lastAccuracyMeters = 15.0f,
            lastLocationTimestamp = now - 61_000L
        )

        assertTrue("Location older than one minute should be flagged as stale", member.isLocationStale)
    }

    @Test
    fun trustedCircleMember_zeroTimestamp_isStale() {
        val member = TrustedCircleMember(
            name = "Anjali",
            phoneNumber = "+919876543212",
            relationship = "Friend",
            lastLocationTimestamp = 0L
        )

        assertTrue("Uninitialized location timestamp should be marked stale", member.isLocationStale)
    }

    @Test
    fun trustedCircleMember_jsonSerializationRoundTrip() {
        val original = TrustedCircleMember(
            id = "test-id-123",
            name = "Neha Gupta",
            phoneNumber = "+919812345678",
            relationship = "Mother",
            isSharingLocationWithMe = true,
            amISharingLocationWithThem = true,
            lastLatitude = 19.0760,
            lastLongitude = 72.8777,
            lastAccuracyMeters = 5.0f,
            lastLocationTimestamp = 1700000000000L,
            sharingDurationHours = 8,
            isBlocked = false
        )

        val jsonString = original.toJson()
        val restored = TrustedCircleMember.fromJson(jsonString)

        assertNotNull(restored)
        assertEquals(original.id, restored!!.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.phoneNumber, restored.phoneNumber)
        assertEquals(original.relationship, restored.relationship)
        assertEquals(original.isSharingLocationWithMe, restored.isSharingLocationWithMe)
        assertEquals(original.amISharingLocationWithThem, restored.amISharingLocationWithThem)
        assertEquals(original.lastLatitude!!, restored.lastLatitude!!, 0.0001)
        assertEquals(original.lastLongitude!!, restored.lastLongitude!!, 0.0001)
        assertEquals(original.sharingDurationHours, restored.sharingDurationHours)
        assertEquals(original.isBlocked, restored.isBlocked)
    }

    @Test
    fun trustedCircleMember_mutualConsentRevocation() {
        val member = TrustedCircleMember(
            name = "Vikram",
            phoneNumber = "+919888877777",
            isSharingLocationWithMe = true,
            amISharingLocationWithThem = true
        )

        val revoked = member.copy(amISharingLocationWithThem = false)
        assertFalse("Location sharing should be revokable without deleting member", revoked.amISharingLocationWithThem)
        assertTrue("Member relationship should be preserved", revoked.isSharingLocationWithMe)
    }
}
