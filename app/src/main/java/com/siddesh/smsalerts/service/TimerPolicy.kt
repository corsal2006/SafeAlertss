package com.siddesh.smsalerts.service

/** Pure deadline policy shared by alarms, recovery and durable workers. */
object TimerPolicy {
    enum class Phase { INACTIVE, WAITING, GRACE, EXPIRED }

    fun phase(active: Boolean, paused: Boolean, deadline: Long, graceMinutes: Int, now: Long): Phase = when {
        !active || paused || deadline <= 0 -> Phase.INACTIVE
        now < deadline -> Phase.WAITING
        now < deadline + graceMinutes.coerceIn(0, 60) * 60_000L -> Phase.GRACE
        else -> Phase.EXPIRED
    }
}
