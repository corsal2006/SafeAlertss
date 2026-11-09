package com.siddesh.smsalerts.data.location

import android.content.Context
import com.siddesh.smsalerts.utils.PrivacyLog as Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "SafeAlerts/LocationRepo"

/**
 * Single source of truth for device location across Home, SOS, Trusted Circle, Women's Safety and Live Tracking.
 *
 * Guarantees:
 * 1. Fresh GPS fixes are prioritized over stale/cached data.
 * 2. Stale or out-of-order callbacks cannot overwrite newer fixes.
 * 3. Never falls back to hardcoded city centers (e.g. Bengaluru 12.9716, 77.5946) or (0,0).
 * 4. Transparently conveys accuracy, fix age, and approximate vs precise permission state.
 */
class LocationRepository private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: LocationRepository? = null

        fun getInstance(context: Context): LocationRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocationRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val locationService = LocationService(context)
    private val refreshMutex = Mutex()

    private val _currentLocation = MutableStateFlow<LocationResult?>(null)
    val currentLocation: StateFlow<LocationResult?> = _currentLocation.asStateFlow()

    private val _lastValidLocation = MutableStateFlow<LocationResult.Success?>(null)
    val lastValidLocation: StateFlow<LocationResult.Success?> = _lastValidLocation.asStateFlow()

    private val _isLocating = MutableStateFlow(false)
    val isLocating: StateFlow<Boolean> = _isLocating.asStateFlow()

    fun hasLocationPermission(): Boolean = locationService.hasLocationPermission()
    fun hasFineLocationPermission(): Boolean = locationService.hasFineLocationPermission()
    fun isGpsEnabled(): Boolean = locationService.isGpsEnabled()

    /**
     * Obtains a fresh location fix using FusedLocationProviderClient with a high-accuracy priority.
     * Updates [currentLocation] and [lastValidLocation] upon successful acquisition.
     */
    suspend fun refreshLocation(timeoutMs: Long = 10_000): LocationResult {
        return refreshMutex.withLock {
            _isLocating.value = true
            try {
                if (!hasLocationPermission()) {
                    val result = LocationResult.PermissionDenied
                    _currentLocation.value = result
                    return@withLock result
                }

                if (!isGpsEnabled()) {
                    val result = LocationResult.GpsDisabled
                    _currentLocation.value = result
                    return@withLock result
                }

                val result = locationService.getCurrentLocation(timeoutMs)
                if (result is LocationResult.Success) {
                    if (LocationQuality.coordinates(result.latitude, result.longitude) &&
                        result.accuracy.isFinite() && result.accuracy >= 0
                    ) {
                        val currentTimestamp = _lastValidLocation.value?.timestamp ?: 0L
                        if (result.timestamp >= currentTimestamp) {
                            _lastValidLocation.value = result
                        }
                        _currentLocation.value = result
                        Log.d(TAG, "Location refreshed successfully: ${result.badgeLabel}")
                    } else {
                        Log.w(TAG, "Location provider returned invalid coordinate values: lat=${result.latitude}, lon=${result.longitude}")
                        val unavailable = LocationResult.Unavailable("Invalid coordinates received from GPS hardware.")
                        _currentLocation.value = unavailable
                        return@withLock unavailable
                    }
                } else {
                    _currentLocation.value = result
                }
                result
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh location: ${e.message}", e)
                val errorResult = LocationResult.Unavailable("GPS error (${e.javaClass.simpleName}). Move outdoors and retry.")
                _currentLocation.value = errorResult
                errorResult
            } finally {
                _isLocating.value = false
            }
        }
    }

    /**
     * Ingests a validated location fix from continuous tracking (e.g. [com.siddesh.smsalerts.service.LiveLocationManager]).
     * Discards invalid coordinates and rejects out-of-order/stale fixes.
     */
    fun updateFromLiveFeed(fix: LocationResult.Success): Boolean {
        if (!LocationQuality.coordinates(fix.latitude, fix.longitude) ||
            !fix.accuracy.isFinite() || fix.accuracy < 0
        ) {
            Log.w(TAG, "Discarded live location fix with invalid coordinate/accuracy: $fix")
            return false
        }

        val existingTimestamp = _lastValidLocation.value?.timestamp ?: 0L
        if (fix.timestamp < existingTimestamp) {
            Log.w(TAG, "Discarded out-of-order live location fix (timestamp ${fix.timestamp} < existing $existingTimestamp)")
            return false
        }

        _lastValidLocation.value = fix
        _currentLocation.value = fix
        return true
    }

    /**
     * Updates the UI status without erasing a previously acquired valid fix.
     */
    fun updateStatus(result: LocationResult) {
        if (result !is LocationResult.Success) {
            _currentLocation.value = result
        }
    }
}
