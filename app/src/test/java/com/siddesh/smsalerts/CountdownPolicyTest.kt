package com.siddesh.smsalerts

import com.siddesh.smsalerts.data.model.EmergencyIncident
import com.siddesh.smsalerts.service.CountdownPolicy
import org.junit.Assert.*
import org.junit.Test

class CountdownPolicyTest {
    @Test fun allConfiguredWindowsRespectElapsedTimeAndIgnoreClockEdits() {
        for (seconds in listOf(3, 5, 10)) {
            val incident = EmergencyIncident(state = "COUNTDOWN", countdownSeconds = seconds,
                countdownDeadlineWall = 10_000 + seconds * 1000L, countdownDeadlineElapsed = 1000 + seconds * 1000L, countdownBootCount = 7)
            assertEquals(seconds * 1000L, CountdownPolicy.remaining(incident, 999_999, 1000, 7))
            assertEquals(1L, CountdownPolicy.remaining(incident, 0, 999 + seconds * 1000L, 7))
            assertEquals(0L, CountdownPolicy.remaining(incident, 0, 1000 + seconds * 1000L, 7))
            assertEquals(0L, CountdownPolicy.remaining(incident.copy(state = "CANCELLED"), 0, 1000, 7))
            assertEquals(0L, CountdownPolicy.remaining(incident.copy(state = "RESOLVED"), 0, 1000, 7))
            assertEquals(0L, CountdownPolicy.remaining(incident, 100_000, 0, 8))
            assertEquals(seconds * 1000L, CountdownPolicy.remaining(incident, 0, 0, 8))
        }
    }
    @Test fun durableIncidentRetainsCountdownAndSelectedRecipients() {
        val original = EmergencyIncident(state = "COUNTDOWN", countdownDeadlineElapsed = 5000, countdownDeadlineWall = 10000,
            countdownBootCount = 2, countdownSeconds = 3, restrictRecipients = true, recipientPhoneNumbers = listOf("TEST-CONTACT"))
        assertEquals(original, EmergencyIncident.fromJson(original.toJson()))
    }
}
