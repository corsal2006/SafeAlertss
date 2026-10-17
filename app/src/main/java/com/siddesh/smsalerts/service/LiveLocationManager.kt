package com.siddesh.smsalerts.service

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.siddesh.smsalerts.data.location.LocationResult
import com.siddesh.smsalerts.data.location.LocationService
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import com.siddesh.smsalerts.data.repository.AuthRepository
import com.siddesh.smsalerts.mesh.BleMeshManager
import com.siddesh.smsalerts.mesh.MeshPacket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

private const val TAG = "SafeAlerts/LiveLocation"

data class LiveLocationPoint(
    val sequenceNumber: Long = 1L,
    val incidentId: String = "",
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val timestamp: Long,
    val isDeliveredToCloud: Boolean,
    val isBroadcastOverMesh: Boolean
)

/**
 * Controller for Section 3: Live Location Sharing (15-Second Cadence Target).
 * Provides ongoing location updates during an active emergency incident across Internet and Bluetooth Mesh.
 */
class LiveLocationManager private constructor(private val context: Context) {

    companion object {
        const val UPDATE_INTERVAL_MS = 15_000L // 15 seconds cadence per Master Requirement 3.2

        @Volatile
        private var INSTANCE: LiveLocationManager? = null

        fun getInstance(context: Context): LiveLocationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LiveLocationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val locationService = LocationService(context)
    private val authRepository = AuthRepository()
    private val preferences = UserPreferencesDataStore(context)
    private val bleMeshManager = BleMeshManager.getInstance(context)

    private val _isSharing = MutableStateFlow(false)
    val isSharing: StateFlow<Boolean> = _isSharing.asStateFlow()

    private val _latestLocationPoint = MutableStateFlow<LiveLocationPoint?>(null)
    val latestLocationPoint: StateFlow<LiveLocationPoint?> = _latestLocationPoint.asStateFlow()

    private var trackingJob: Job? = null
    private var currentIncidentId: String = ""
    private var sequenceCounter: Long = 0L

    fun startLiveSharing(incidentId: String) {
        if (_isSharing.value) return
        currentIncidentId = incidentId
        sequenceCounter = 0L
        _isSharing.value = true

        scope.launch {
            preferences.updateLiveLocationTracking(true, incidentId)
        }

        trackingJob = scope.launch {
            Log.i(TAG, "LIVE_LOCATION_STARTED (15s cadence) for incident $incidentId")
            while (_isSharing.value && isActive) {
                pushLocationUpdate()
                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    fun stopLiveSharing() {
        if (!_isSharing.value) return
        _isSharing.value = false
        trackingJob?.cancel()
        trackingJob = null
        sequenceCounter = 0L

        scope.launch {
            preferences.updateLiveLocationTracking(false, "")
        }
        Log.i(TAG, "LIVE_LOCATION_STOPPED for incident $currentIncidentId")
    }

    private suspend fun pushLocationUpdate() {
        val result = locationService.getCurrentLocation(timeoutMs = 8_000L)
        if (result !is LocationResult.Success) {
            Log.w(TAG, "Unable to acquire location fix during 15s live tracking window")
            return
        }

        sequenceCounter++
        val lat = result.latitude
        val lon = result.longitude
        val now = System.currentTimeMillis()
        var cloudDelivered = false
        var meshBroadcast = false

        // 1. Internet delivery: Push to Firestore users/{uid}/alerts/{incidentId}/locations
        val uid = authRepository.currentUserId
        if (!uid.isNullOrBlank() && currentIncidentId.isNotBlank()) {
            try {
                val locDoc = mapOf(
                    "sequenceNumber" to sequenceCounter,
                    "latitude" to lat,
                    "longitude" to lon,
                    "mapsUrl" to "https://maps.google.com/?q=$lat,$lon",
                    "timestamp" to now,
                    "incidentId" to currentIncidentId
                )
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .collection("alerts")
                    .document(currentIncidentId)
                    .collection("locations")
                    .document("seq_${sequenceCounter}_$now")
                    .set(locDoc)
                cloudDelivered = true
                Log.d(TAG, "Live location #$sequenceCounter pushed to Firestore: $lat,$lon")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to push live location to cloud: ${e.message}")
            }
        }

        // 2. Offline Bluetooth Mesh delivery: Broadcast location update packet
        try {
            val meshPacket = MeshPacket(
                messageId = UUID.randomUUID().toString(),
                emergencyId = currentIncidentId,
                senderDeviceId = bleMeshManager.myDeviceId,
                packetType = MeshPacket.TYPE_SOS_ALERT,
                emergencyType = "LOCATION_UPDATE",
                messageText = "Live location update #$sequenceCounter: https://maps.google.com/?q=$lat,$lon",
                latitude = lat,
                longitude = lon,
                locationTimestamp = now
            )
            bleMeshManager.broadcastSosPacket(meshPacket)
            meshBroadcast = true
            Log.d(TAG, "Live location #$sequenceCounter broadcasted over BLE mesh")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to broadcast mesh location: ${e.message}")
        }

        _latestLocationPoint.value = LiveLocationPoint(
            sequenceNumber = sequenceCounter,
            incidentId = currentIncidentId,
            latitude = lat,
            longitude = lon,
            accuracy = null,
            timestamp = now,
            isDeliveredToCloud = cloudDelivered,
            isBroadcastOverMesh = meshBroadcast
        )
    }
}
