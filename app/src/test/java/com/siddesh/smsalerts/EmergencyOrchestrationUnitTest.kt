package com.siddesh.smsalerts

import com.siddesh.smsalerts.data.model.EmergencyAlert
import com.siddesh.smsalerts.data.model.UserProfile
import com.siddesh.smsalerts.data.preferences.AppUserPreferences
import com.siddesh.smsalerts.data.sms.SmsDispatchResult
import com.siddesh.smsalerts.data.voice.VoiceEmergencyDetector
import com.siddesh.smsalerts.ui.viewmodel.SosStage
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class EmergencyOrchestrationUnitTest {

    @Test
    fun emergencyStage_lifecycleTransitions() {
        var currentStage: SosStage = SosStage.Idle
        assertEquals(SosStage.Idle, currentStage)

        // 1. Trigger Detected
        currentStage = SosStage.TriggerDetected(source = "SHAKE", emergencyType = "PERSONAL")
        assertTrue(currentStage is SosStage.TriggerDetected)
        assertEquals("SHAKE", (currentStage as SosStage.TriggerDetected).source)

        // 2. Countdown Verification
        for (sec in 5 downTo 1) {
            currentStage = SosStage.Countdown(sec)
            assertEquals(sec, (currentStage as SosStage.Countdown).secondsLeft)
        }

        // 3. Emergency Active
        currentStage = SosStage.EmergencyActive
        assertEquals(SosStage.EmergencyActive, currentStage)

        // 4. Dispatching actions
        currentStage = SosStage.AcquiringLocation
        assertEquals(SosStage.AcquiringLocation, currentStage)

        currentStage = SosStage.TransmittingSms
        assertEquals(SosStage.TransmittingSms, currentStage)

        // 5. Completion
        val fakeAlert = EmergencyAlert(
            alertId = "incident_999",
            emergencyType = "MEDICAL",
            message = "Help me",
            status = "SMS_SENT"
        )
        val fakeSmsResult = SmsDispatchResult(
            overallStatus = "SENT",
            successfulCount = 2,
            totalCount = 2,
            contactStatuses = emptyList()
        )
        currentStage = SosStage.Completed(fakeAlert, fakeSmsResult)
        assertTrue(currentStage is SosStage.Completed)
        assertEquals("incident_999", (currentStage as SosStage.Completed).alert.alertId)

        // 6. Explicit Resolution
        currentStage = SosStage.Resolved
        assertEquals(SosStage.Resolved, currentStage)
    }

    @Test
    fun voiceEmergencyDetector_customPhraseMatchingAndNormalization() {
        val defaultPhrases = VoiceEmergencyDetector.DEFAULT_EMERGENCY_PHRASES
        val customPhrases = listOf("CODE RED", "MAYDAY", "SAFEALERT SOS")

        fun matchSpoken(spokenText: String, phrases: List<String>) = VoiceEmergencyDetector.matchPhrase(spokenText, phrases)

        val allPhrases = defaultPhrases + customPhrases

        // Custom phrase matching
        assertEquals("CODE RED", matchSpoken("Please initiate code red immediately!", allPhrases))
        assertEquals("MAYDAY", matchSpoken("Mayday! Mayday!", allPhrases))
        assertEquals("SAFEALERT SOS", matchSpoken("safealert sos right now", allPhrases))

        // Punctuation and casing normalization
        assertNull(matchSpoken("help... please!", allPhrases))
        assertEquals("HELP ME", matchSpoken("Please HELP ME!", allPhrases))

        // Negative tests
        assertNull(matchSpoken("Just calling to say hello", allPhrases))
        assertNull(matchSpoken("Turning on the red light", allPhrases))
        assertNull(matchSpoken("Today is may day holiday", allPhrases))
    }

    @Test
    fun emergencyBriefing_containsMedicalAndCoordinates() {
        val profile = UserProfile(
            name = "Jane Doe",
            fullName = "Jane Doe",
            email = "jane@example.com",
            phoneNumber = "+919876543210"
        )
        val prefs = AppUserPreferences(
            bloodGroup = "O+",
            allergies = "Penicillin, Peanuts",
            medicalNotes = "Asthma patient; carry inhaler",
            policeNumber = "112",
            ambulanceNumber = "108"
        )

        val latitude = 13.0827
        val longitude = 80.2707

        val briefing = buildString {
            append("EMERGENCY BRIEFING\n")
            append("Name: ${profile.fullName}\n")
            if (prefs.bloodGroup.isNotBlank()) append("Blood Group: ${prefs.bloodGroup}\n")
            if (prefs.allergies.isNotBlank()) append("Allergies: ${prefs.allergies}\n")
            if (prefs.medicalNotes.isNotBlank()) append("Medical Notes: ${prefs.medicalNotes}\n")
            append("Current Location: https://maps.google.com/?q=$latitude,$longitude")
        }

        assertTrue(briefing.contains("Jane Doe"))
        assertTrue(briefing.contains("Blood Group: O+"))
        assertTrue(briefing.contains("Penicillin, Peanuts"))
        assertTrue(briefing.contains("Asthma patient"))
        assertTrue(briefing.contains("https://maps.google.com/?q=13.0827,80.2707"))
    }

    @Test
    fun locationFallback_formatsGracefullyWhenGpsUnavailable() {
        fun buildEmergencyMessage(userName: String, mapsUrl: String?): String {
            return buildString {
                append("🚨 EMERGENCY ALERT\n\n")
                append("Name: $userName\n\n")
                if (!mapsUrl.isNullOrBlank()) {
                    append("📍 Location:\n$mapsUrl\n\n")
                } else {
                    append("📍 Location: Temporarily Unavailable\n\n")
                }
                append("Please contact me immediately.")
            }
        }

        // When GPS acquired
        val withGps = buildEmergencyMessage("Alex", "https://maps.google.com/?q=37.7749,-122.4194")
        assertTrue(withGps.contains("https://maps.google.com/?q=37.7749,-122.4194"))
        assertFalse(withGps.contains("Temporarily Unavailable"))

        // When GPS disabled / fix unavailable indoors
        val withoutGps = buildEmergencyMessage("Alex", null)
        assertTrue(withoutGps.contains("📍 Location: Temporarily Unavailable"))
        assertTrue(withoutGps.contains("Name: Alex"))
        assertTrue(withoutGps.contains("Please contact me immediately."))
    }
}
