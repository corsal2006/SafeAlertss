package com.siddesh.smsalerts

<<<<<<< HEAD
import com.siddesh.smsalerts.service.SafetyCheckInState
=======
>>>>>>> origin/main
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyCheckInTest {

    private fun formatRemainingTime(millis: Long): String {
        if (millis <= 0) return "00m 00s"
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%02dh %02dm", hours, minutes)
        } else {
            String.format("%02dm %02ds", minutes, seconds)
        }
    }

    @Test
    fun formatRemainingTime_zeroOrNegative_returnsZeroFormat() {
        assertEquals("00m 00s", formatRemainingTime(0))
        assertEquals("00m 00s", formatRemainingTime(-5000))
    }

    @Test
    fun formatRemainingTime_minutesAndSeconds() {
<<<<<<< HEAD
=======
        // 14 minutes and 30 seconds = (14 * 60 + 30) * 1000 = 870,000 ms
>>>>>>> origin/main
        val millis = (14 * 60 + 30) * 1000L
        assertEquals("14m 30s", formatRemainingTime(millis))
    }

    @Test
    fun formatRemainingTime_hoursAndMinutes() {
<<<<<<< HEAD
=======
        // 1 hour and 15 minutes = 75 minutes * 60 * 1000 = 4,500,000 ms
>>>>>>> origin/main
        val millis = 75 * 60 * 1000L
        assertEquals("01h 15m", formatRemainingTime(millis))
    }

    @Test
    fun intervalCalculations_verifyDurations() {
        val now = 1000000L
        val fifteenMinExpiry = now + (15 * 60 * 1000L)
        val thirtyMinExpiry = now + (30 * 60 * 1000L)
        val oneHourExpiry = now + (60 * 60 * 1000L)

        assertEquals(15 * 60 * 1000L, fifteenMinExpiry - now)
        assertEquals(30 * 60 * 1000L, thirtyMinExpiry - now)
        assertEquals(60 * 60 * 1000L, oneHourExpiry - now)

        val isExpiredBefore = now >= fifteenMinExpiry
        assertFalse(isExpiredBefore)

        val isExpiredAfter = (fifteenMinExpiry + 1000) >= fifteenMinExpiry
        assertTrue(isExpiredAfter)
    }
<<<<<<< HEAD

    @Test
    fun safetyCheckInState_gracePeriodAndPauseBehavior() {
        val now = System.currentTimeMillis()
        val durationMinutes = 30
        val graceMinutes = 2
        val targetTime = now + (durationMinutes * 60 * 1000L)

        val activeState = SafetyCheckInState(
            isActive = true,
            durationMinutes = durationMinutes,
            targetTimeMillis = targetTime,
            remainingSeconds = (durationMinutes * 60).toLong(),
            isExpired = false,
            gracePeriodMinutes = graceMinutes,
            isGracePeriodActive = false,
            isPaused = false
        )

        assertTrue(activeState.isActive)
        assertFalse(activeState.isExpired)
        assertEquals(1800L, activeState.remainingSeconds)
        assertEquals(2, activeState.gracePeriodMinutes)

        // Test Pause State
        val pausedState = activeState.copy(isPaused = true)
        assertTrue(pausedState.isPaused)
        assertEquals(1800L, pausedState.remainingSeconds)

        // Test Grace Period Activation upon Timer Expiry
        val graceTarget = targetTime + (graceMinutes * 60 * 1000L)
        val graceState = activeState.copy(
            isExpired = true,
            remainingSeconds = 0L,
            isGracePeriodActive = true,
            graceRemainingSeconds = 120L
        )

        assertTrue(graceState.isExpired)
        assertTrue(graceState.isGracePeriodActive)
        assertEquals(120L, graceState.graceRemainingSeconds)
        assertEquals(graceTarget, targetTime + 120_000L)
    }

    @Test
    fun immediateEscalation_whenGracePeriodIsZero() {
        val now = System.currentTimeMillis()
        val target = now + 60_000L
        val immediateState = SafetyCheckInState(
            isActive = true,
            durationMinutes = 1,
            targetTimeMillis = target,
            remainingSeconds = 0L,
            isExpired = true,
            gracePeriodMinutes = 0,
            isGracePeriodActive = false
        )

        assertTrue(immediateState.isExpired)
        assertFalse(immediateState.isGracePeriodActive)
        assertEquals(0, immediateState.gracePeriodMinutes)
    }
=======
>>>>>>> origin/main
}
