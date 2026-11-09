package com.siddesh.smsalerts.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Shared fused provider. Only LiveLocationManager owns a continuous subscription. */
class LocationService(private val context: Context) {
    companion object {
        @Volatile private var client: FusedLocationProviderClient? = null
        private fun provider(context: Context) = client ?: synchronized(this) {
            client ?: LocationServices.getFusedLocationProviderClient(context.applicationContext).also { client = it }
        }
    }
    private val fused get() = provider(context)
    fun hasLocationPermission() = has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)
    fun hasFineLocationPermission() = has(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun has(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    fun isGpsEnabled(): Boolean = (context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)?.let {
        it.isProviderEnabled(LocationManager.GPS_PROVIDER) || it.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    } == true
    fun hasLocationCapability() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION)
    private fun priority() = if (has(Manifest.permission.ACCESS_FINE_LOCATION)) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
    private fun convert(location: Location?, cached: Boolean): LocationResult.Success? {
        location ?: return null
        val now = System.currentTimeMillis()
        val fixTime = if (location.time > now && location.time - now <= 10_000L) now else location.time
        if (!LocationQuality.coordinates(location.latitude, location.longitude) || !location.hasAccuracy() ||
            !location.accuracy.isFinite() || location.accuracy < 0 || !LocationQuality.usable(fixTime, now)) return null
        val isFine = has(Manifest.permission.ACCESS_FINE_LOCATION)
        return LocationResult.Success(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            timestamp = fixTime,
            source = location.provider ?: "fused",
            isCached = cached,
            isPrecise = isFine
        )
    }
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(timeoutMs: Long = 10_000): LocationResult {
        if (!hasLocationPermission()) return LocationResult.PermissionDenied
        if (!isGpsEnabled()) return LocationResult.GpsDisabled
        val token = CancellationTokenSource()
        return try {
            val fresh = withTimeoutOrNull(timeoutMs) {
                fused.getCurrentLocation(CurrentLocationRequest.Builder().setPriority(priority())
                    .setMaxUpdateAgeMillis(0).setDurationMillis(timeoutMs).build(), token.token).await()
            }
            convert(fresh, false) ?: run {
                val fallback = withTimeoutOrNull(3000) { fused.lastLocation.await() }
                convert(fallback, true) ?: LocationResult.Unavailable("No valid location fix within ${timeoutMs / 1000}s. Move near a window or outdoors and retry.")
            }
        } catch (e: CancellationException) { throw e
        } catch (e: SecurityException) { LocationResult.PermissionDenied
        } catch (e: Exception) { LocationResult.Unavailable("Location provider unavailable (${e.javaClass.simpleName}). Check device location and retry.")
        } finally { token.cancel() }
    }

    /** Android targets this interval; permissions, power policy and hardware can delay delivery. */
    @SuppressLint("MissingPermission")
    fun updates(intervalMs: Long = 15_000) = callbackFlow<LocationResult> {
        if (!hasLocationPermission()) { trySend(LocationResult.PermissionDenied); close(); return@callbackFlow }
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                result.locations.maxByOrNull { it.time }?.let { location ->
                    convert(location, false)?.let { trySend(it) }
                        ?: trySend(LocationResult.Unavailable("Location provider returned an invalid or old fix."))
                }
            }
            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) trySend(if (!isGpsEnabled()) LocationResult.GpsDisabled
                    else LocationResult.Unavailable("Waiting for a location fix. The last fix may be stale."))
            }
        }
        try {
            fused.requestLocationUpdates(LocationRequest.Builder(priority(), intervalMs)
                .setMinUpdateIntervalMillis(intervalMs / 2).setMaxUpdateDelayMillis(intervalMs)
                .setWaitForAccurateLocation(false).build(), callback, Looper.getMainLooper())
                .addOnFailureListener { trySend(LocationResult.Unavailable("Location subscription failed (${it.javaClass.simpleName}).")); close() }
        } catch (e: SecurityException) { trySend(LocationResult.PermissionDenied); close() }
        awaitClose { fused.removeLocationUpdates(callback) }
    }
}
