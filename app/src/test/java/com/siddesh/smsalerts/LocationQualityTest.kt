package com.siddesh.smsalerts

import com.siddesh.smsalerts.data.location.LocationQuality
import com.siddesh.smsalerts.data.location.LocationResult
import com.siddesh.smsalerts.data.repository.SparkCircleRepository
import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Test

class LocationQualityTest {
    @Test fun invalidAndNonFiniteCoordinatesAreRejectedButZeroIsReal() {
        assertTrue(LocationQuality.coordinates(0.0, 0.0))
        assertTrue(LocationQuality.coordinates(-90.0, 180.0))
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 90.001).forEach { assertFalse(LocationQuality.coordinates(it, 77.0)) }
        assertFalse(LocationQuality.coordinates(12.0, -180.001))
    }
    @Test fun futureAndExpiredFixesCannotBeDescribedAsFresh() {
        val now = 10_000_000L
        assertTrue(LocationQuality.fresh(now - 60_000, now))
        assertFalse(LocationQuality.fresh(now - 60_001, now))
        assertFalse(LocationQuality.fresh(now + 1, now))
        assertFalse(LocationQuality.usable(now - 900_001, now))
        assertFalse(LocationQuality.newer(now, now))
        assertFalse(LocationQuality.newer(now - 1, now))
        assertTrue(LocationQuality.newer(now + 1, now))
        assertFalse(LocationResult.Success(12.0, 77.0, 10f, timestamp = System.currentTimeMillis(), isCached = true).isFresh)
    }
    @Test fun invitationExpiryPreservesServerSubMillisecondPrecision() {
        val created = Timestamp(1_700_000_000L, 123456789)
        val expiry = SparkCircleRepository.invitationExpiry(created)
        assertEquals(created.seconds + 900, expiry.seconds)
        assertEquals(created.nanoseconds, expiry.nanoseconds)
    }
}
