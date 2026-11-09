package com.siddesh.smsalerts.data.sensor

/** Qualifying distinct peaks within three seconds; reset below the release threshold between peaks. */
class ShakePattern(private val threshold: Float = 2.7f, var requiredPeaks: Int = 5) {
    private val peaks = ArrayDeque<Long>()
    private var armed = true
    private var lastPeak = Long.MIN_VALUE
    private var lastTrigger: Long? = null
    fun sample(netG: Float, now: Long, cooldownMs: Long = 30_000): Boolean {
        if (netG < 1f) armed = true
        if (!netG.isFinite() || netG < threshold || !armed) return false
        if (lastTrigger?.let { now - it < cooldownMs } == true) return false
        if (lastPeak != Long.MIN_VALUE && now - lastPeak < 220) return false
        armed = false; lastPeak = now
        while (peaks.isNotEmpty() && now - peaks.first() > 3_000) peaks.removeFirst()
        peaks.addLast(now)
        if (peaks.size < requiredPeaks) return false
        peaks.clear(); lastTrigger = now
        return true
    }
    fun reset() { peaks.clear(); armed = true; lastPeak = Long.MIN_VALUE }
}

