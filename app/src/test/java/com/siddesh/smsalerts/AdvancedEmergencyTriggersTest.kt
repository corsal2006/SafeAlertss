package com.siddesh.smsalerts

import com.siddesh.smsalerts.data.model.EmergencyAlert
import com.siddesh.smsalerts.data.voice.VoiceEmergencyDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedEmergencyTriggersTest {

    @Test
    fun emergencyAlert_triggerSourceSerialization() {
        val alert = EmergencyAlert(
            alertId = "alert_123",
            emergencyType = "PERSONAL",
            triggerSource = "SHAKE",
            message = "Emergency! Need assistance.",
            latitude = 37.7749,
            longitude = -122.4194,
            mapsUrl = "https://maps.google.com/?q=37.7749,-122.4194",
            timestamp = 1727850000000L,
            recipients = listOf("Sarah Connor (+15550199)"),
            recipientCount = 1,
            status = "DELIVERED"
        )

        val map = alert.toMap()
        assertEquals("SHAKE", map["triggerSource"])
        assertEquals("PERSONAL", map["emergencyType"])
        assertEquals(1, map["recipientCount"])
        assertEquals("DELIVERED", map["status"])

        val voiceAlert = alert.copy(triggerSource = "VOICE")
        val voiceMap = voiceAlert.toMap()
        assertEquals("VOICE", voiceMap["triggerSource"])
    }

    @Test
    fun voiceEmergencyPhrases_exactAndNormalizedMatches() {
        val supportedPhrases = VoiceEmergencyDetector.EMERGENCY_PHRASES

        assertTrue(supportedPhrases.contains("HELP"))
        assertTrue(supportedPhrases.contains("HELP ME"))
        assertTrue(supportedPhrases.contains("I NEED HELP"))
        assertTrue(supportedPhrases.contains("EMERGENCY"))
        assertTrue(supportedPhrases.contains("CALL FOR HELP"))

        // Test phrase recognition matcher
        fun matchesEmergency(spokenText: String): Boolean {
            val normalized = spokenText.trim().uppercase()
            return supportedPhrases.any { trigger ->
                normalized == trigger || normalized.contains(trigger)
            }
        }

        // Positive tests
        assertTrue(matchesEmergency("help"))
        assertTrue(matchesEmergency("HELP"))
        assertTrue(matchesEmergency("help me!"))
        assertTrue(matchesEmergency("please help me now"))
        assertTrue(matchesEmergency("I need help right now"))
        assertTrue(matchesEmergency("EMERGENCY!"))
        assertTrue(matchesEmergency("Call for help please"))

        // Negative tests
        assertFalse(matchesEmergency("hello how are you"))
        assertFalse(matchesEmergency("good morning"))
        assertFalse(matchesEmergency("what is the weather today"))
        assertFalse(matchesEmergency("turn on the flashlight"))
    }

    @Test
    fun shakeDetector_logicSimulation() {
        var triggerCount = 0
        val requiredShakes = 3
        val cooldownMs = 30_000L
        val windowMs = 1800L

        var shakeCount = 0
        var lastShakeTimestamp = 0L
        var lastTriggerTimestamp = 0L

        fun onMotionDetected(timestamp: Long) {
            if (lastTriggerTimestamp > 0L && timestamp - lastTriggerTimestamp < cooldownMs) {
                // Cooldown active, ignore
                return
            }

            if (timestamp - lastShakeTimestamp > windowMs) {
                shakeCount = 1
            } else {
                shakeCount++
            }
            lastShakeTimestamp = timestamp

            if (shakeCount >= requiredShakes) {
                triggerCount++
                lastTriggerTimestamp = timestamp
                shakeCount = 0
            }
        }

        // Simulate 3 deliberate shakes within window (e.g., 300ms apart)
        onMotionDetected(1000L)
        assertEquals(0, triggerCount)
        onMotionDetected(1300L)
        assertEquals(0, triggerCount)
        onMotionDetected(1600L)
        assertEquals(1, triggerCount) // Triggered!

        // Shake during cooldown (at 5000L, cooldown is 30s) -> ignored
        onMotionDetected(5000L)
        onMotionDetected(5300L)
        onMotionDetected(5600L)
        assertEquals(1, triggerCount) // Still 1

        // Shakes after cooldown expires (at 35000L) -> should trigger again
        onMotionDetected(35000L)
        onMotionDetected(35300L)
        onMotionDetected(35600L)
        assertEquals(2, triggerCount)

        // Random isolated movements spread far apart (>1800ms) -> should NOT trigger
        onMotionDetected(70000L) // Shake 1
        onMotionDetected(75000L) // Shake 1 again (reset due to window timeout)
        onMotionDetected(80000L) // Shake 1 again
        assertEquals(2, triggerCount) // Never reached 3 consecutive in window
    }
}
