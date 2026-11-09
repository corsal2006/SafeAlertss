package com.siddesh.smsalerts

import com.siddesh.smsalerts.data.location.LocationQuality
import com.siddesh.smsalerts.data.location.LocationResult
import org.junit.Assert.*
import org.junit.Test

/**
 * Validates the single shared Location pipeline:
 * 1. Coordinates quality and bounds checking.
 * 2. Rejection of NaN, Infinity, out-of-range latitude/longitude, and negative accuracy.
 * 3. Freshness and staleness detection.
 * 4. Transparent formatting of accuracy and fix age.
 * 5. Distinction between approximate (coarse) and precise (fine) location.
 */
class LocationRepositoryTest {

    @Test
    fun validCoordinates_areAccepted() {
        assertTrue(LocationQuality.coordinates(12.9716, 77.5946))
        assertTrue(LocationQuality.coordinates(-33.8688, 151.2093))
        assertTrue(LocationQuality.coordinates(0.0, 0.0))
        assertTrue(LocationQuality.coordinates(90.0, 180.0))
        assertTrue(LocationQuality.coordinates(-90.0, -180.0))
    }

    @Test
    fun invalidCoordinates_areRejected() {
        assertFalse(LocationQuality.coordinates(Double.NaN, 77.5946))
        assertFalse(LocationQuality.coordinates(12.9716, Double.NaN))
        assertFalse(LocationQuality.coordinates(Double.POSITIVE_INFINITY, 77.5946))
        assertFalse(LocationQuality.coordinates(12.9716, Double.NEGATIVE_INFINITY))
        assertFalse(LocationQuality.coordinates(90.001, 77.5946))
        assertFalse(LocationQuality.coordinates(-90.001, 77.5946))
        assertFalse(LocationQuality.coordinates(12.9716, 180.001))
        assertFalse(LocationQuality.coordinates(12.9716, -180.001))
    }

    @Test
    fun successResult_formatsAgeAndAccuracyCorrectly() {
        val now = 1_700_000_000_000L
        val freshFix = LocationResult.Success(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracy = 8.4f,
            timestamp = now - 3_000L,
            source = "gps",
            isCached = false,
            isPrecise = true
        )

        assertEquals("just now", freshFix.formatAge(now))
        assertEquals("±8m", freshFix.formatAccuracy())
        assertEquals("GPS · ±8m · just now", freshFix.badgeLabel(now))
        assertEquals("Precise GPS", freshFix.precisionLabel)
        assertTrue(freshFix.isPrecise)
        assertFalse(freshFix.isCached)
    }

    @Test
    fun cachedResult_formatsBadgeAsCached() {
        val now = 1_700_000_000_000L
        val cachedFix = LocationResult.Success(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracy = 25.0f,
            timestamp = now - 120_000L,
            source = "fused",
            isCached = true,
            isPrecise = true
        )

        assertEquals("2m ago", cachedFix.formatAge(now))
        assertEquals("±25m", cachedFix.formatAccuracy())
        assertEquals("Cached · ±25m · 2m ago", cachedFix.badgeLabel(now))
        assertTrue(cachedFix.isCached)
        assertFalse(cachedFix.isFresh)
    }

    @Test
    fun approximateResult_clearlyIndicatesCoarseLocation() {
        val now = 1_700_000_000_000L
        val coarseFix = LocationResult.Success(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracy = 100.0f,
            timestamp = now - 10_000L,
            source = "network",
            isCached = false,
            isPrecise = false
        )

        assertEquals("Approx · ±100m · 10s ago", coarseFix.badgeLabel(now))
        assertEquals("Approximate Location", coarseFix.precisionLabel)
        assertFalse(coarseFix.isPrecise)
        assertTrue(coarseFix.description.contains("precise location was not granted"))
    }

    @Test
    fun outOfOrderLiveFix_mustBeRejectedByTimestampComparison() {
        val baseTimestamp = 1_700_000_000_000L
        val fix1 = LocationResult.Success(12.97, 77.59, 10f, timestamp = baseTimestamp)
        val olderFix = LocationResult.Success(12.98, 77.58, 5f, timestamp = baseTimestamp - 5_000L)

        assertFalse("Older fix cannot be considered newer", LocationQuality.newer(olderFix.timestamp, fix1.timestamp))
        assertTrue("Newer fix is recognized", LocationQuality.newer(baseTimestamp + 15_000L, fix1.timestamp))
    }

    @Test
    fun googleMapsUrl_correctlyEncodesCoordinates() {
        val url = LocationResult.generateMapsUrl(12.9716, 77.5946)
        assertEquals("https://www.google.com/maps?q=12.9716,77.5946", url)
    }
}
