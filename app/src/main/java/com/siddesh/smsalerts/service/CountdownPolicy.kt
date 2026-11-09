package com.siddesh.smsalerts.service

import com.siddesh.smsalerts.data.model.EmergencyIncident

/** Wall time is only a reboot fallback. Clock edits cannot shorten a countdown in the same boot. */
object CountdownPolicy {
    fun seconds(value: Int): Int = value.takeIf { it in setOf(3, 5, 10) } ?: 5

    fun remaining(incident: EmergencyIncident, wall: Long, elapsed: Long, boot: Int): Long {
        if (incident.state != EmergencyIncident.STATE_COUNTDOWN) return 0
        return if (boot >= 0 && boot == incident.countdownBootCount) {
            (incident.countdownDeadlineElapsed - elapsed).coerceAtLeast(0)
        } else {
            (incident.countdownDeadlineWall - wall).coerceIn(0, seconds(incident.countdownSeconds) * 1000L)
        }
    }
}
