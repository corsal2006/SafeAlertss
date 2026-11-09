package com.siddesh.smsalerts.data.location

object LocationQuality {
    const val FRESH_MS = 60_000L
    const val FALLBACK_MAX_MS = 15 * 60_000L
    fun coordinates(lat: Double, lon: Double) = lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
    fun fresh(timestamp: Long, now: Long) = timestamp > 0 && now - timestamp in 0..FRESH_MS
    fun usable(timestamp: Long, now: Long) = timestamp > 0 && now - timestamp in 0..FALLBACK_MAX_MS
    fun newer(timestamp: Long, previous: Long) = timestamp > 0 && timestamp > previous
}
