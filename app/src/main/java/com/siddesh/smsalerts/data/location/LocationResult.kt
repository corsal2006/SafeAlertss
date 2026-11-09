package com.siddesh.smsalerts.data.location

sealed class LocationResult {
    data class Success(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val mapsUrl: String = generateMapsUrl(latitude, longitude),
        val timestamp: Long = 0L,
        val source: String = "fused",
        val isCached: Boolean = false,
        val isPrecise: Boolean = true
    ) : LocationResult() {
        val isFresh: Boolean get() = !isCached && LocationQuality.fresh(timestamp, System.currentTimeMillis())

        fun ageMs(currentTime: Long = System.currentTimeMillis()): Long = (currentTime - timestamp).coerceAtLeast(0L)

        fun formatAge(currentTime: Long = System.currentTimeMillis()): String {
            val age = ageMs(currentTime)
            return when {
                age < 5_000L -> "just now"
                age < 60_000L -> "${age / 1000}s ago"
                age < 3_600_000L -> "${age / 60_000}m ago"
                else -> "${age / 3_600_000}h ago"
            }
        }

        fun formatAccuracy(): String = "±${kotlin.math.round(accuracy).toInt()}m"

        fun badgeLabel(currentTime: Long = System.currentTimeMillis()): String {
            val type = if (isCached) "Cached" else if (isPrecise) "GPS" else "Approx"
            return "$type · ${formatAccuracy()} · ${formatAge(currentTime)}"
        }

        val badgeLabel: String get() = badgeLabel(System.currentTimeMillis())

        val precisionLabel: String
            get() = if (isPrecise) "Precise GPS" else "Approximate Location"

        val description: String get() = when {
            isCached -> "Last-known fix (cached · ${formatAge()}); check its timestamp"
            !isPrecise -> "Approximate location (${formatAccuracy()}); precise location was not granted"
            isFresh -> "Fresh GPS location (${formatAccuracy()})"
            else -> "Stale location fix (${formatAge()})"
        }
    }

    object PermissionDenied : LocationResult()
    object GpsDisabled : LocationResult()
    data class Unavailable(val reason: String) : LocationResult()

    fun explanation(): String = when (this) {
        is Success -> description
        PermissionDenied -> "Location permission denied. Allow precise location in Android app settings."
        GpsDisabled -> "Device location is switched off. Open location settings and enable GPS."
        is Unavailable -> reason
    }

    companion object {
        fun generateMapsUrl(latitude: Double, longitude: Double): String {
            return "https://www.google.com/maps?q=$latitude,$longitude"
        }
    }
}
