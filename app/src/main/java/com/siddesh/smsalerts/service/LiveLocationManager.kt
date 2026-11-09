package com.siddesh.smsalerts.service

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.siddesh.smsalerts.SafeAlertApp
import com.siddesh.smsalerts.data.location.*
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

data class LiveLocationPoint(
    val sequenceNumber: Long = 1, val incidentId: String = "",
    val latitude: Double, val longitude: Double, val accuracy: Float?, val timestamp: Long,
    val isDeliveredToCloud: Boolean, val isBroadcastOverMesh: Boolean,
    val source: String = "fused", val isCached: Boolean = false
)

/** One service-owned fused subscription shared by all explicitly authorized sessions. */
class LiveLocationManager private constructor(private val context: Context) {
    companion object {
        const val UPDATE_INTERVAL_MS = 15_000L
        @Volatile private var instance: LiveLocationManager? = null
        fun getInstance(context: Context): LiveLocationManager = instance ?: synchronized(this) {
            instance ?: LiveLocationManager(context.applicationContext).also { instance = it }
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val location = LocationService(context)
    private val preferences = UserPreferencesDataStore(context)
    private val outbox = EmergencyOutbox(context)
    private val sessions = ConcurrentHashMap.newKeySet<String>()
    private var trackingJob: Job? = null
    private var generation = 0
    private val _isSharing = MutableStateFlow(false)
    val isSharing = _isSharing.asStateFlow()
    private val _latest = MutableStateFlow<LiveLocationPoint?>(null)
    val latestLocationPoint = _latest.asStateFlow()
    private val _cloudStatus = MutableStateFlow("No cloud location submitted.")
    val cloudStatus = _cloudStatus.asStateFlow()
    private val _locationStatus = MutableStateFlow("Location sharing stopped.")
    val locationStatus = _locationStatus.asStateFlow()

    fun reportUnavailable(session: String, reason: String) {
        _locationStatus.value = reason
        if (session !in setOf("circle", "journey")) outbox.updateTransportStatus(session, "LOCATION", reason)
    }
    fun startLiveSharing(incidentId: String) {
        if (!location.hasLocationPermission()) { reportUnavailable(incidentId, LocationResult.PermissionDenied.explanation()); return }
        if (!SafeAlertApp.isVisible && !EmergencyProtectionService.locationSessionActive) {
            reportUnavailable(incidentId, "Open SafeAlerts to start location sharing; Android prevents a new background location session.")
            return
        }
        EmergencyProtectionService.startLocationSession(context, incidentId)
    }
    private suspend fun authorized(id: String, owner: String): Boolean {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"
        if (currentUid != owner && owner != "local_user" && currentUid != "local_user") return false
        val prefs = preferences.preferencesFlow.first()
        return when (id) {
            "circle" -> TrustedCircleManager.getInstance(context).hasActiveOutgoingSharing()
            "journey" -> prefs.womensSafetyActive && prefs.womensSafetyShareLiveLocation
            else -> outbox.getIncident(id)?.let {
                (it.userId == owner || it.userId == currentUid || it.userId == "local_user" || currentUid == "local_user") &&
                    it.state !in setOf("RESOLVED", "CANCELLED")
            } == true
        }
    }
    @Synchronized fun beginTrackingFromService(session: String) {
        if (session.isBlank()) return
        sessions.add(session)
        if (trackingJob?.isActive == true) return
        val run = ++generation
        val owner = FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"
        _isSharing.value = true
        trackingJob = scope.launch {
            try {
                coroutineScope {
                    val monitor = launch {
                        while (isActive) {
                            sessions.toList().forEach { if (!authorized(it, owner)) sessions.remove(it) }
                            if (sessions.isEmpty()) { this@coroutineScope.cancel(); break }
                            if (!location.hasLocationPermission()) {
                                sessions.forEach { reportUnavailable(it, LocationResult.PermissionDenied.explanation()) }
                                this@coroutineScope.cancel(); break
                            }
                            if (!location.isGpsEnabled()) _locationStatus.value = LocationResult.GpsDisabled.explanation()
                            preferences.updateLiveLocationTracking(true, sessions.first())
                            delay(UPDATE_INTERVAL_MS)
                        }
                    }
                    location.updates(UPDATE_INTERVAL_MS).conflate().collect { result ->
                        if (result is LocationResult.Success) {
                            sessions.toList().filter { authorized(it, owner) }.forEach { recordFix(owner, it, result) }
                        } else _locationStatus.value = result.explanation()
                    }
                    monitor.cancel()
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _locationStatus.value = "Location tracking stopped: the device could not save or obtain a fix. Check storage and permissions, then retry."
                com.siddesh.smsalerts.utils.PrivacyLog.e("LiveLocation", "LOCATION_SESSION_FAILED", e)
            } finally {
                if (generation == run) {
                    _isSharing.value = false
                    EmergencyProtectionService.locationSessionActive = false
                    withContext(NonCancellable) {
                        try { preferences.updateLiveLocationTracking(false) }
                        catch (e: Exception) { com.siddesh.smsalerts.utils.PrivacyLog.e("LiveLocation", "LOCATION_STATE_SAVE_FAILED", e) }
                    }
                    EmergencyProtectionService.refreshSessions(context)
                }
            }
        }
    }
    /** Initial SOS fixes take the same durable path even if a foreground service cannot start. */
    suspend fun recordFix(owner: String, session: String, result: LocationResult.Success) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"
        if (owner.isBlank() || (currentUid != owner && owner != "local_user" && currentUid != "local_user")) return
        LocationStore(context).save(owner, session, result)
        LocationRepository.getInstance(context).updateFromLiveFeed(result)
        if (session !in setOf("circle", "journey")) {
            outbox.updateIncident(session) { saved ->
                if (result.timestamp >= (saved.locationTimestamp ?: 0)) saved.copy(latitude = result.latitude,
                    longitude = result.longitude, accuracy = result.accuracy, locationTimestamp = result.timestamp,
                    locationSource = result.source, locationCached = result.isCached, mapsUrl = result.mapsUrl) else saved
            }
            outbox.updateTransportStatus(session, "LOCATION", result.description)
        }
        _locationStatus.value = result.description
        _latest.value = LiveLocationPoint((_latest.value?.sequenceNumber ?: 0) + 1, session, result.latitude,
            result.longitude, result.accuracy, result.timestamp, false, false, result.source, result.isCached)
        LocationSyncWorker.enqueue(context, owner)
        _cloudStatus.value = "Saved on this device; waiting for server confirmation."
    }
    fun cloudOutcome(owner: String, confirmedTimestamp: Long?, message: String) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"
        if (currentUid != owner && owner != "local_user" && currentUid != "local_user") return
        _cloudStatus.value = message
        if (confirmedTimestamp != null && _latest.value?.timestamp == confirmedTimestamp)
            _latest.value = _latest.value?.copy(isDeliveredToCloud = true)
    }
    @Synchronized fun stopLiveSharing(sessionId: String? = null) {
        if (sessionId == null) sessions.clear() else sessions.remove(sessionId)
        if (sessions.isNotEmpty()) return
        ++generation; trackingJob?.cancel(); trackingJob = null
        _isSharing.value = false; _latest.value = null
        _locationStatus.value = "Location sharing stopped."
        EmergencyProtectionService.locationSessionActive = false
        scope.launch { if (sessions.isEmpty()) preferences.updateLiveLocationTracking(false) }
        EmergencyProtectionService.refreshSessions(context)
    }
}
