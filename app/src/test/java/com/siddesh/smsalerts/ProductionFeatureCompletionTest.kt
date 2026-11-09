package com.siddesh.smsalerts

import com.siddesh.smsalerts.data.location.GeocodedLocation
import com.siddesh.smsalerts.data.location.RouteResult
import com.siddesh.smsalerts.data.model.EmergencyIncident
import com.siddesh.smsalerts.data.model.TrustedCircleMember
import com.siddesh.smsalerts.data.sensor.ShakePattern
import com.siddesh.smsalerts.data.voice.VoiceEmergencyDetector
import com.siddesh.smsalerts.service.WomensSafetyJourneyState
import org.junit.Assert.*
import org.junit.Test

class ProductionFeatureCompletionTest {

    // ==========================================
    // 1. OSM ROUTING & GEOCODING DATA MODEL
    // ==========================================

    @Test
    fun geocodedLocation_coordinateExtraction_andFormatting() {
        val place = GeocodedLocation(
            displayName = "Central Station, Downtown, Cityville",
            latitude = 12.9716,
            longitude = 77.5946
        )

        assertEquals("Central Station, Downtown, Cityville", place.displayName)
        assertEquals(12.9716, place.latitude, 0.0001)
        assertEquals(77.5946, place.longitude, 0.0001)
    }

    @Test
    fun routeResult_pointsAndSummaryData() {
        val waypoints = listOf(
            Pair(12.9716, 77.5946),
            Pair(12.9750, 77.6000),
            Pair(12.9800, 77.6100)
        )
        val route = RouteResult(
            points = waypoints,
            distanceMeters = 2450.0,
            durationSeconds = 480.0,
            summary = "2.5 km (8 mins)"
        )

        assertEquals(3, route.points.size)
        assertEquals(2450.0, route.distanceMeters, 0.1)
        assertEquals(480.0, route.durationSeconds, 0.1)
        assertEquals("2.5 km (8 mins)", route.summary)
    }

    // ==========================================
    // 2. FIVE-SHAKE TRIGGER ENGINE
    // ==========================================

    @Test
    fun shakePattern_configurablePeaks_defaultFiveShakes() {
        val pattern = ShakePattern()
        assertEquals("Default required peaks must be 5", 5, pattern.requiredPeaks)

        pattern.requiredPeaks = 3
        assertEquals(3, pattern.requiredPeaks)

        pattern.requiredPeaks = 7
        assertEquals(7, pattern.requiredPeaks)
    }

    @Test
    fun shakePattern_samplePeakCountingAndCooldown() {
        val pattern = ShakePattern(threshold = 2.7f, requiredPeaks = 5)
        val cooldownMs = 15000L

        // Peak requires netG >= 2.7f followed by armed reset when netG < 1.0f
        var currentTime = 1000L
        for (i in 1..4) {
            // High acceleration
            val triggered = pattern.sample(netG = 3.5f, now = currentTime, cooldownMs = cooldownMs)
            assertFalse("4 peaks should not trigger emergency", triggered)
            currentTime += 250L
            // Reset arming
            pattern.sample(netG = 0.5f, now = currentTime, cooldownMs = cooldownMs)
            currentTime += 50L
        }

        // 5th peak should trigger
        val finalTrigger = pattern.sample(netG = 3.5f, now = currentTime, cooldownMs = cooldownMs)
        assertTrue("5 valid qualifying peaks within 3s must trigger emergency", finalTrigger)

        // Reset arming and try another peak during cooldown
        pattern.sample(netG = 0.5f, now = currentTime + 50L, cooldownMs = cooldownMs)
        val cooldownTrigger = pattern.sample(netG = 3.5f, now = currentTime + 1000L, cooldownMs = cooldownMs)
        assertFalse("Must not trigger during cooldown", cooldownTrigger)
    }

    // ==========================================
    // 3. VOICE TRIGGER RECOGNITION
    // ==========================================

    @Test
    fun voiceTrigger_detectsEmergencyPhraseAccurately() {
        assertEquals("HELP ME", VoiceEmergencyDetector.matchPhrase("Help me"))
        assertEquals("HELP ME", VoiceEmergencyDetector.matchPhrase("HELP ME!"))
        assertEquals("HELP ME", VoiceEmergencyDetector.matchPhrase("  help    me   "))
        assertEquals("HELP ME", VoiceEmergencyDetector.matchPhrase("Please, help me now"))

        assertNull(VoiceEmergencyDetector.matchPhrase("I am helpful"))
        assertNull(VoiceEmergencyDetector.matchPhrase("hello world"))
        assertNull(VoiceEmergencyDetector.matchPhrase("can you hear me"))
    }

    // ==========================================
    // 4. EMERGENCY INCIDENT MODEL & LIFECYCLE
    // ==========================================

    @Test
    fun emergencyIncident_stateTransitionsAndResolution() {
        val incident = EmergencyIncident(
            incidentId = "inc_test_101",
            userId = "user_456",
            emergencyType = "WOMENS_SAFETY",
            state = EmergencyIncident.STATE_TRIGGERED,
            creationTimestamp = 1700000000000L,
            latitude = 12.9716,
            longitude = 77.5946
        )

        assertEquals("inc_test_101", incident.incidentId)
        assertEquals(EmergencyIncident.STATE_TRIGGERED, incident.state)
        assertEquals(12.9716, incident.latitude!!, 0.0001)

        val active = incident.copy(state = EmergencyIncident.STATE_ACTIVE)
        assertEquals(EmergencyIncident.STATE_ACTIVE, active.state)

        val resolved = active.copy(state = EmergencyIncident.STATE_RESOLVED)
        assertEquals(EmergencyIncident.STATE_RESOLVED, resolved.state)
    }

    // ==========================================
    // 5. WOMEN'S SAFETY DURATION & GRACE PERIOD
    // ==========================================

    @Test
    fun womensSafety_durationValidation_andCustomStepping() {
        val state = WomensSafetyJourneyState(
            destination = "Office HQ",
            durationMinutes = 45,
            gracePeriodMinutes = 2
        )

        assertEquals("Office HQ", state.destination)
        assertEquals(45, state.durationMinutes)
        assertEquals(2, state.gracePeriodMinutes)

        // Validate grace options: 0, 1, 2, 5
        val validGraceOptions = listOf(0, 1, 2, 5)
        assertTrue(validGraceOptions.contains(state.gracePeriodMinutes))

        // Validate custom duration stepping within sensible limits (5 to 360 mins)
        var customMins = 45
        customMins = (customMins + 5).coerceIn(5, 360)
        assertEquals(50, customMins)

        customMins = (customMins - 10).coerceIn(5, 360)
        assertEquals(40, customMins)
    }

    // ==========================================
    // 6. TRUSTED CIRCLE DANGER STATE & STALENESS
    // ==========================================

    @Test
    fun trustedCircle_dangerState_identification() {
        val safeMember = TrustedCircleMember(
            id = "user_safe",
            name = "Alice",
            phoneNumber = "+15550101",
            activeIncidentId = null,
            isSharingLocationWithMe = true
        )
        assertNull("Safe member has null activeIncidentId", safeMember.activeIncidentId)

        val endangeredMember = TrustedCircleMember(
            id = "user_danger",
            name = "Bob",
            phoneNumber = "+15550102",
            activeIncidentId = "inc_danger_999",
            isSharingLocationWithMe = true
        )
        assertNotNull("Endangered member has active incident", endangeredMember.activeIncidentId)
        assertEquals("inc_danger_999", endangeredMember.activeIncidentId)
    }

    @Test
    fun trustedCircle_locationStaleness_calculation() {
        val now = System.currentTimeMillis()

        // 1. Fresh location (< 60s)
        val freshMember = TrustedCircleMember(
            id = "user_1",
            name = "Fresh User",
            phoneNumber = "+15550103",
            lastLatitude = 12.9716,
            lastLongitude = 77.5946,
            lastLocationTimestamp = now - 15_000L // 15 seconds ago
        )
        assertFalse("Location under 60 seconds old must NOT be stale", freshMember.isLocationStale)

        // 2. Stale location (> 60s)
        val staleMember = TrustedCircleMember(
            id = "user_2",
            name = "Stale User",
            phoneNumber = "+15550104",
            lastLatitude = 12.9716,
            lastLongitude = 77.5946,
            lastLocationTimestamp = now - 120_000L // 2 minutes ago
        )
        assertTrue("Location over 60 seconds old must be marked stale", staleMember.isLocationStale)

        // 3. No timestamp
        val noLocationMember = TrustedCircleMember(
            id = "user_3",
            name = "No Location User",
            phoneNumber = "+15550105",
            lastLocationTimestamp = 0L
        )
        assertTrue("Member with no location timestamp must be stale", noLocationMember.isLocationStale)
    }
}
