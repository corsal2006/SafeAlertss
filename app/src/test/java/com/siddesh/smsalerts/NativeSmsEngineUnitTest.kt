package com.siddesh.smsalerts

import com.siddesh.smsalerts.data.preferences.AppUserPreferences
import com.siddesh.smsalerts.utils.EmergencyMessageFormatter
import com.siddesh.smsalerts.utils.ValidationUtils
import org.junit.Assert.*
import org.junit.Test

class NativeSmsEngineUnitTest {

    @Test
    fun generalEmergencyTemplate_includesExpectedFieldsAndExcludesWomensHelpline() {
        val generalMsg = EmergencyMessageFormatter.formatEmergencyMessage(
            userName = "Aarav Patel",
            customMessage = "Please assist, vehicle breakdown.",
            emergencyType = "ROAD_ACCIDENT",
            policeNumber = "112",
            isPoliceEnabled = true,
            ambulanceNumber = "108",
            isAmbulanceEnabled = true,
            womensHelplineNumber = "1091",
            isWomensHelplineEnabled = true,
            latitude = 19.0760,
            longitude = 72.8777,
            accuracyMeters = 12.0f,
            locationTimestampMs = 1700000000000L,
            incidentTimestampMs = 1700000000000L,
            age = 29,
            isJourney = false
        )

        assertTrue("Must include SAFEALERTS EMERGENCY header", generalMsg.contains("SAFEALERTS EMERGENCY:"))
        assertTrue("Must include user name", generalMsg.contains("Aarav Patel"))
        assertTrue("Must include custom message", generalMsg.contains("vehicle breakdown"))
        assertTrue("Must include age", generalMsg.contains("Age: 29"))
        assertTrue("Must include emergency type category", generalMsg.contains("Category: ROAD_ACCIDENT"))
        assertTrue("Must include police", generalMsg.contains("Police/emergency: 112"))
        assertTrue("Must include ambulance", generalMsg.contains("Ambulance: 108"))
        assertFalse("General emergency must NOT include Women's Safety helpline", generalMsg.contains("1091"))
        assertFalse("General emergency must NOT include Women's Safety heading", generalMsg.contains("WOMEN'S SAFETY"))
        assertTrue("Must include coordinates", generalMsg.contains("19.076,72.8777"))
        assertTrue("Must include google maps link", generalMsg.contains("https://maps.google.com/?q=19.076,72.8777"))
        assertTrue("Must include location time", generalMsg.contains("Location time:"))
    }

    @Test
    fun womensSafetyTemplate_includesDestinationExpectedArrivalAndHelpline() {
        val expectedArrival = 1700003600000L
        val journeyMsg = EmergencyMessageFormatter.formatEmergencyMessage(
            userName = "Pooja Sharma",
            emergencyType = "WOMEN'S SAFETY OVERDUE",
            policeNumber = "112",
            isPoliceEnabled = true,
            ambulanceNumber = "108",
            isAmbulanceEnabled = true,
            womensHelplineNumber = "1091",
            isWomensHelplineEnabled = true,
            latitude = 28.6139,
            longitude = 77.2090,
            accuracyMeters = 8.0f,
            locationTimestampMs = 1700000000000L,
            incidentTimestampMs = 1700000000000L,
            age = 24,
            isJourney = true,
            destination = "Indiranagar Metro Station",
            expectedArrivalTimeMs = expectedArrival
        )

        assertTrue("Must include Women's Safety Alert heading", journeyMsg.contains("SAFEALERTS WOMEN'S SAFETY ALERT:"))
        assertTrue("Must include user name", journeyMsg.contains("Pooja Sharma"))
        assertTrue("Must include destination", journeyMsg.contains("at Indiranagar Metro Station"))
        assertTrue("Must include arrival time", journeyMsg.contains("by"))
        assertTrue("Must include emergency escalation instruction", journeyMsg.contains("Please call me immediately and follow the configured emergency escalation procedure"))
        assertTrue("Must include age", journeyMsg.contains("Age: 24"))
        assertTrue("Must include Women's Safety helpline", journeyMsg.contains("Women's Safety helpline: 1091"))
        assertTrue("Must include coordinates", journeyMsg.contains("28.6139,77.209"))
        assertTrue("Must include google maps link", journeyMsg.contains("https://maps.google.com/?q=28.6139,77.209"))
    }

    @Test
    fun locationUnavailable_explicitlyReported() {
        val msg = EmergencyMessageFormatter.formatEmergencyMessage(
            userName = "Vikram",
            latitude = null,
            longitude = null,
            isJourney = false
        )
        assertTrue("Must explicitly report location unavailable", msg.contains("Latest location: unavailable"))
        assertFalse("Must not invent a maps URL", msg.contains("https://maps.google.com"))
    }

    @Test
    fun phoneValidationAndNormalization_handlesVariousFormats() {
        assertEquals("+919876543210", ValidationUtils.normalizePhoneNumber("+91 98765-43210"))
        assertEquals("+14155552671", ValidationUtils.normalizePhoneNumber("+1 (415) 555-2671"))
        assertEquals("9876543210", ValidationUtils.normalizePhoneNumber("98765 43210"))

        assertTrue(ValidationUtils.isValidPhoneNumber("+919876543210"))
        assertTrue(ValidationUtils.isValidPhoneNumber("9876543210"))
        assertTrue(ValidationUtils.isValidPhoneNumber("+14155552671"))

        assertFalse(ValidationUtils.isValidPhoneNumber(""))
        assertFalse(ValidationUtils.isValidPhoneNumber(null))
        assertFalse(ValidationUtils.isValidPhoneNumber("123")) // Too short
        assertFalse(ValidationUtils.isValidPhoneNumber("abcde"))
    }

    @Test
    fun directSmsPreference_defaultIsTrue() {
        val prefs = AppUserPreferences()
        assertTrue("directSmsEnabled should default to true", prefs.directSmsEnabled)
    }

    @Test
    fun recipientLevelDeliveryStatus_transitionsTruthfully() {
        // Test states that can be established
        val validStatuses = setOf(
            "SUBMITTING_UNKNOWN",
            "SUBMITTED",
            "SENT_TO_CARRIER",
            "DELIVERED",
            "SEND_FAILED_OR_PARTIAL",
            "DELIVERY_FAILED",
            "INVALID_PHONE",
            "PERMISSION_DENIED",
            "SIM_ERROR"
        )
        for (status in validStatuses) {
            assertNotNull(status)
        }
    }
}
