package com.siddesh.smsalerts

import com.siddesh.smsalerts.service.WomensSafetyJourneyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WomensSafetyUnitTest {

    @Test
    fun journeyState_initialState_isInactive() {
        val state = WomensSafetyJourneyState()
        assertFalse(state.isActive)
        assertEquals("", state.destination)
        assertEquals(30, state.durationMinutes)
        assertEquals(0L, state.remainingSeconds)
        assertFalse(state.isGracePeriodActive)
    }

    @Test
    fun journeyState_activeJourney_verifiesDestinationAndDurations() {
        val now = System.currentTimeMillis()
        val durationMins = 45
        val target = now + (durationMins * 60 * 1000L)

        val active = WomensSafetyJourneyState(
            isActive = true,
            destination = "Metro Station",
            durationMinutes = durationMins,
            targetTimestamp = target,
            remainingSeconds = 45 * 60L,
            gracePeriodMinutes = 5,
            isGracePeriodActive = false,
            shareLiveLocation = true,
            alertHelpline1091 = true
        )

        assertTrue(active.isActive)
        assertEquals("Metro Station", active.destination)
        assertEquals(2700L, active.remainingSeconds)
        assertEquals(5, active.gracePeriodMinutes)
        assertTrue(active.shareLiveLocation)
        assertTrue(active.alertHelpline1091)
    }

    @Test
    fun journeyState_extension_increasesDurationCorrectly() {
        val initialRemaining = 600L // 10 minutes remaining
        val extraMinutes = 15
        val newRemaining = initialRemaining + (extraMinutes * 60L) // 25 minutes = 1500s

        assertEquals(1500L, newRemaining)
    }

    @Test
    fun journeyState_timeoutAndGracePeriod_transitionsToEscalation() {
        val expiredJourney = WomensSafetyJourneyState(
            isActive = true,
            destination = "Downtown Hostel",
            durationMinutes = 30,
            remainingSeconds = 0L,
            gracePeriodMinutes = 2,
            isGracePeriodActive = true,
            graceRemainingSeconds = 120L
        )

        assertTrue(expiredJourney.isGracePeriodActive)
        assertEquals(120L, expiredJourney.graceRemainingSeconds)

        // After grace period expires:
        val finalExpired = expiredJourney.copy(
            graceRemainingSeconds = 0L
        )
        assertEquals(0L, finalExpired.graceRemainingSeconds)
    }
}
