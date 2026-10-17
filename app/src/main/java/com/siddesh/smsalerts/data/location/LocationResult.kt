package com.siddesh.smsalerts.data.location

sealed class LocationResult {
    data class Success(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val mapsUrl: String = generateMapsUrl(latitude, longitude)
    ) : LocationResult()

    object PermissionDenied : LocationResult()
    object GpsDisabled : LocationResult()
    data class Unavailable(val reason: String) : LocationResult()

    companion object {
        fun generateMapsUrl(latitude: Double, longitude: Double): String {
            return "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
        }
    }
}
