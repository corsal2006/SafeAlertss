package com.siddesh.smsalerts.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class LocationService(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    fun hasLocationPermission(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineLocation || coarseLocation
    }

    fun isGpsEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    fun hasLocationCapability(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION) ||
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
    }

    private fun isValidLocation(location: Location?): Boolean {
        if (location == null) return false
        val lat = location.latitude
        val lng = location.longitude
        if (lat.isNaN() || lng.isNaN()) return false
        if (lat < -90.0 || lat > 90.0) return false
        if (lng < -180.0 || lng > 180.0) return false
        if (lat == 0.0 && lng == 0.0) return false
        return true
    }

    private fun isFreshLocation(location: Location): Boolean {
        val ageMs = System.currentTimeMillis() - location.time
        return ageMs in 0..900_000L
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(timeoutMs: Long = 10000L): LocationResult {
        if (!hasLocationPermission()) {
            return LocationResult.PermissionDenied
        }

        if (!hasLocationCapability()) {
            return LocationResult.Unavailable("Device lacks GPS/location hardware capability.")
        }

        if (!isGpsEnabled()) {
            return LocationResult.GpsDisabled
        }

        return try {
            val cancellationTokenSource = CancellationTokenSource()

            // 1. Request current high accuracy location with timeout
            val location: Location? = withTimeoutOrNull(timeoutMs) {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                ).await()
            }

            if (location != null && isValidLocation(location)) {
                LocationResult.Success(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy
                )
            } else {
                // 2. Fallback to recent last-known location if valid and fresh
                val lastKnown: Location? = try {
                    fusedLocationClient.lastLocation.await()
                } catch (e: Exception) {
                    null
                }

                if (lastKnown != null && isValidLocation(lastKnown) && isFreshLocation(lastKnown)) {
                    LocationResult.Success(
                        latitude = lastKnown.latitude,
                        longitude = lastKnown.longitude,
                        accuracy = lastKnown.accuracy
                    )
                } else {
                    LocationResult.Unavailable("GPS fix timed out after ${timeoutMs / 1000}s. Please ensure you are not indoors with blocked satellite signals.")
                }
            }
        } catch (e: SecurityException) {
            LocationResult.PermissionDenied
        } catch (e: Exception) {
            LocationResult.Unavailable(e.localizedMessage ?: "Unknown location acquisition error.")
        }
    }
}
