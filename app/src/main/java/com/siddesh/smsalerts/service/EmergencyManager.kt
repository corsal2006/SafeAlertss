package com.siddesh.smsalerts.service

<<<<<<< HEAD
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.siddesh.smsalerts.utils.PrivacyLog as Log
import com.google.firebase.auth.FirebaseAuth
import com.siddesh.smsalerts.data.location.LocationResult
import com.siddesh.smsalerts.data.location.LocationService
import com.siddesh.smsalerts.data.model.*
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import com.siddesh.smsalerts.data.repository.*
import com.siddesh.smsalerts.data.sms.*
import com.siddesh.smsalerts.ui.viewmodel.SosStage
import com.siddesh.smsalerts.utils.HapticUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

private const val TAG = "SafeAlerts/Emergency"

/** Single dispatcher for all triggers. A durable incident and per-recipient submission ledger precede external effects. */
class EmergencyManager private constructor(private val context: Context) {
    companion object {
        @Volatile private var instance: EmergencyManager? = null
        fun getInstance(context: Context): EmergencyManager = instance ?: synchronized(this) {
            instance ?: EmergencyManager(context.applicationContext).also { instance = it }
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dispatchMutex = Mutex()
    private val preferences = UserPreferencesDataStore(context)
    private val locationService = LocationService(context)
    private val smsSender = SmsSender(context)
    val outbox = EmergencyOutbox(context)
    private val _stage = MutableStateFlow<SosStage>(SosStage.Idle)
    val stage = _stage.asStateFlow()
    private val _activeIncident = MutableStateFlow<com.siddesh.smsalerts.data.model.EmergencyIncident?>(null)
    val activeIncident: StateFlow<com.siddesh.smsalerts.data.model.EmergencyIncident?> = _activeIncident.asStateFlow()

    fun refreshActiveIncident() {
        val activeUid = FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"
        val incident = try {
            outbox.getActiveIncidents().firstOrNull { (it.userId == activeUid || it.userId == "local_user") && it.state !in setOf("RESOLVED", "CANCELLED") }
        } catch (_: Exception) { null }
        _activeIncident.value = incident
        if (incident != null) {
            currentIncidentId = incident.incidentId
            if (_stage.value is SosStage.Idle && incident.state in setOf(EmergencyIncident.STATE_ACTIVE, EmergencyIncident.STATE_DISPATCHING, EmergencyIncident.STATE_INCIDENT_CREATED)) {
                _stage.value = SosStage.EmergencyActive
            }
        } else {
            currentIncidentId = null
        }
    }

    fun resetToIdle() {
        countdownJob?.cancel()
        refreshActiveIncident()
        val current = _activeIncident.value
        if (current == null || current.state in setOf(EmergencyIncident.STATE_RESOLVED, EmergencyIncident.STATE_CANCELLED)) {
            _stage.value = SosStage.Idle
        }
    }

    init {
        refreshActiveIncident()
    }

    var currentIncidentId: String? = null
        private set
    @Volatile var isDispatching = false
        private set
    private var countdownJob: Job? = null
    private var lastTriggerTimestamp = 0L

    @Synchronized
    fun startCountdown(triggerSource: String = "BUTTON", emergencyType: String = "PERSONAL", customMessage: String = "", countdownSeconds: Int? = null, recipientPhones: List<String>? = null) {
        if (isDispatching || countdownJob?.isActive == true) return
        val countdownOwner = FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"
        val activeIncidents = try { outbox.getActiveIncidents().filter { it.userId == countdownOwner || it.userId == "local_user" } }
            catch (e: Exception) {
                Log.e(TAG, "Saved emergencies could not be read", e)
                _stage.value = SosStage.Error("Saved emergencies could not be read. Check device storage.", canRetry = false)
                return
            }
        val alreadyActive = activeIncidents.firstOrNull { it.state in setOf(EmergencyIncident.STATE_ACTIVE, EmergencyIncident.STATE_DISPATCHING, EmergencyIncident.STATE_INCIDENT_CREATED) }
        if (alreadyActive != null) {
            currentIncidentId = alreadyActive.incidentId
            _stage.value = SosStage.EmergencyActive
            return
        }
        val pending = activeIncidents.firstOrNull { it.state == EmergencyIncident.STATE_COUNTDOWN }
        val countdownId = pending?.incidentId ?: UUID.randomUUID().toString()
        currentIncidentId = countdownId
        _stage.value = SosStage.TriggerDetected(triggerSource, emergencyType)
        countdownJob = scope.launch {
            try {
                val seconds = CountdownPolicy.seconds(countdownSeconds ?: preferences.preferencesFlow.first().emergencyCountdownSeconds)
                val activeOwner = FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"
                if (activeOwner != countdownOwner) return@launch
                val incident = withContext(Dispatchers.IO) {
                    pending ?: EmergencyIncident(incidentId = countdownId, triggerSource = triggerSource, emergencyType = emergencyType, userId = countdownOwner,
                            messageText = customMessage, state = EmergencyIncident.STATE_COUNTDOWN,
                            countdownDeadlineWall = System.currentTimeMillis() + seconds * 1000L,
                            countdownDeadlineElapsed = android.os.SystemClock.elapsedRealtime() + seconds * 1000L,
                            countdownBootCount = EmergencyCountdownWork.boot(context), countdownSeconds = seconds,
                            recipientPhoneNumbers = recipientPhones.orEmpty(), restrictRecipients = recipientPhones != null).also { outbox.saveIncident(it) }
                }
                currentIncidentId = incident.incidentId
                try {
                    LiveLocationManager.getInstance(context).startLiveSharing(incident.incidentId)
                } catch (_: Exception) {}
                EmergencyCountdownWork.schedule(context, incident)
                var previousSecond = -1
                while (isActive && (FirebaseAuth.getInstance().currentUser?.uid ?: "local_user") == countdownOwner) {
                    val saved = withContext(Dispatchers.IO) { outbox.getIncident(incident.incidentId) } ?: break
                    if (saved.state != EmergencyIncident.STATE_COUNTDOWN) break
                    val remaining = EmergencyCountdownWork.remaining(context, saved)
                    if (remaining <= 0) { withContext(Dispatchers.IO) { expireCountdown(saved.incidentId) }; break }
                    val second = ((remaining + 999) / 1000).toInt()
                    _stage.value = SosStage.Countdown(second)
                    if (second != previousSecond) { HapticUtils.vibrateCountdownTick(context); previousSecond = second }
                    delay(100)
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                Log.e(TAG, "Countdown persistence/scheduling failed", e)
                _stage.value = SosStage.Error("Countdown could not be prepared. Check device storage and retry.", canRetry = false)
            }
        }
    }
    /** Returns false only for an early wake; cancelled, stale and foreign-account work is finished. */
    fun expireCountdown(id: String, sendNow: Boolean = false): Boolean {
        val incident = outbox.getIncident(id) ?: return true
        val activeUid = FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"
        if (incident.userId.isNotBlank() && incident.userId != activeUid && incident.userId != "local_user") return true
        if (incident.state == EmergencyIncident.STATE_COUNTDOWN && !sendNow && EmergencyCountdownWork.remaining(context, incident) > 0) return false
        if (outbox.transition(id, EmergencyIncident.STATE_COUNTDOWN, EmergencyIncident.STATE_INCIDENT_CREATED) { sendNow || EmergencyCountdownWork.remaining(context, it) == 0L }
            || outbox.getIncident(id)?.state == EmergencyIncident.STATE_INCIDENT_CREATED) {
            currentIncidentId = id
            EmergencyDispatchWorker.enqueue(context, id)
            _stage.value = SosStage.TriggerDetected(incident.triggerSource, incident.emergencyType)
        }
        return true
    }
    fun triggerAutomaticEmergency(source: String) { startCountdown(source, "PERSONAL EMERGENCY") }
    fun triggerShakeEmergency() = triggerAutomaticEmergency("SHAKE")
    fun triggerVoiceEmergency() = triggerAutomaticEmergency("VOICE")
    fun triggerManualEmergency(type: String) = startCountdown(emergencyType = type)
    fun resumeIncident(id: String) {
        val activeUid = FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"
        val incident = outbox.getIncident(id)?.takeIf { it.userId == activeUid || it.userId == "local_user" } ?: return
        currentIncidentId = id
        when (incident.state) {
            EmergencyIncident.STATE_COUNTDOWN -> startCountdown(incident.triggerSource, incident.emergencyType)
            EmergencyIncident.STATE_CANCELLED -> _stage.value = SosStage.Cancelled
            EmergencyIncident.STATE_RESOLVED -> _stage.value = SosStage.Resolved
            else -> {
                _stage.value = SosStage.EmergencyActive
                if (incident.state != EmergencyIncident.STATE_ACTIVE) EmergencyDispatchWorker.enqueue(context, id)
            }
        }
    }

    @Synchronized
    fun triggerEmergency(triggerSource: String, emergencyType: String = "PERSONAL", customMessage: String = "", bypassCountdown: Boolean = false, forcedIncidentId: String? = null): String {
        if (!bypassCountdown) {
            startCountdown(triggerSource, emergencyType, customMessage)
            return currentIncidentId.orEmpty()
        }
        currentIncidentId?.let { pendingId ->
            if (forcedIncidentId == null && outbox.getIncident(pendingId)?.state == EmergencyIncident.STATE_COUNTDOWN) {
                expireCountdown(pendingId, sendNow = true)
                return pendingId
            }
        }
        val now = System.currentTimeMillis()
        if (forcedIncidentId == null && (isDispatching || now - lastTriggerTimestamp < 15_000)) return currentIncidentId.orEmpty()
        val activeOwner = FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"
        val activeIncidents = try { outbox.getActiveIncidents().filter { it.userId == activeOwner || it.userId == "local_user" } } catch (_: Exception) { emptyList() }
        val existingActive = activeIncidents.firstOrNull { it.state in setOf(EmergencyIncident.STATE_ACTIVE, EmergencyIncident.STATE_DISPATCHING) }
        if (existingActive != null && forcedIncidentId == null) {
            currentIncidentId = existingActive.incidentId
            _stage.value = SosStage.EmergencyActive
            return existingActive.incidentId
        }
        val id = forcedIncidentId ?: UUID.randomUUID().toString()
        lastTriggerTimestamp = now
        currentIncidentId = id
        val incident = EmergencyIncident(incidentId = id, triggerSource = triggerSource, emergencyType = emergencyType,
            userId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(), messageText = customMessage)
        try {
            if (outbox.getIncident(id) == null) outbox.saveIncident(incident)
            EmergencyDispatchWorker.enqueue(context, id)
            _stage.value = SosStage.TriggerDetected(triggerSource, emergencyType)
        } catch (_: Exception) { _stage.value = SosStage.Error("Could not persist emergency. Check available device storage.") }
        return id
    }

    suspend fun triggerEmergencySuspending(triggerSource: String, emergencyType: String = "PERSONAL", customMessage: String = "", forcedIncidentId: String? = null): String = dispatchMutex.withLock {
        val id = forcedIncidentId ?: UUID.randomUUID().toString()
        val previous = outbox.getIncident(id)
        if (previous?.state in setOf(EmergencyIncident.STATE_COUNTDOWN, EmergencyIncident.STATE_ACTIVE, EmergencyIncident.STATE_RESOLVED, EmergencyIncident.STATE_CANCELLED)) return@withLock id
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"
        if (previous != null && previous.userId != uid && previous.userId != "local_user" && uid != "local_user") return@withLock id
        currentIncidentId = id
        isDispatching = true

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val dispatchWakeLock = try {
            powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SafeAlerts:EmergencyDispatchLock")?.apply {
                setReferenceCounted(false)
                acquire(60_000L)
            }
        } catch (_: Exception) { null }

        try {
            val p = preferences.preferencesFlow.first()
            // A cancellation/arrival that wins before external dispatch invalidates already-queued work.
            if (triggerSource == "TIMER_EXPIRY" && (!p.safetyCheckInEscalated || p.safetyCheckInIncidentId != id)) return@withLock id
            if (triggerSource == "WOMENS_SAFETY_TIMEOUT" && (!p.womensSafetyEscalated || p.womensSafetyIncidentId != id)) return@withLock id
            val incident = previous ?: EmergencyIncident(incidentId = id, triggerSource = triggerSource, emergencyType = emergencyType,
                userId = uid, messageText = customMessage)
            outbox.saveIncident(incident)
            _stage.value = SosStage.EmergencyActive
            val contacts = (try { withTimeoutOrNull(2500) { ContactsRepository().getContactsFlow(uid).first() } }
                catch (e: CancellationException) { throw e } catch (e: Exception) {
                    outbox.updateTransportStatus(id, "CONTACTS", "CACHED: cloud contacts unavailable"); null
                } ?: LocalContactsCache.getContacts()).filter { it.enabled && it.isActive &&
                (!incident.restrictRecipients || it.phoneNumber in incident.recipientPhoneNumbers) }.distinctBy { it.phoneNumber }
            val profile = withTimeoutOrNull(2000) { UserRepository.getInstance().userProfile.filterNotNull().first { it.uid == uid && it.isLoaded } }
            _stage.value = SosStage.AcquiringLocation
            val locationRepo = com.siddesh.smsalerts.data.location.LocationRepository.getInstance(context)
            val locationResult = locationRepo.refreshLocation(10_000)
            outbox.updateTransportStatus(id, "LOCATION", locationResult.explanation())
            val acquired = locationResult as? LocationResult.Success
            val concurrentFix = outbox.getIncident(id)?.let { saved ->
                val lat = saved.latitude; val lon = saved.longitude; val time = saved.locationTimestamp
                if (lat != null && lon != null && time != null && saved.accuracy != null &&
                    com.siddesh.smsalerts.data.location.LocationQuality.usable(time, System.currentTimeMillis()))
                    LocationResult.Success(lat, lon, saved.accuracy, timestamp = time, source = saved.locationSource, isCached = saved.locationCached)
                else null
            }
            val location = listOfNotNull(acquired, concurrentFix).maxByOrNull { it.timestamp }
            if (location != null) {
                locationRepo.updateFromLiveFeed(location)
            }
            val name = profile?.fullName?.ifBlank { profile.name }.orEmpty().ifBlank { FirebaseAuth.getInstance().currentUser?.displayName.orEmpty() }
            val isJourney = triggerSource == "WOMENS_SAFETY_TIMEOUT" || triggerSource == "WOMENS_SAFETY"
            val message = com.siddesh.smsalerts.utils.EmergencyMessageFormatter.formatEmergencyMessage(
                userName = name, customMessage = customMessage.ifBlank { p.defaultEmergencyMessage }, emergencyType = emergencyType,
                policeNumber = p.policeNumber, isPoliceEnabled = p.policeEnabled,
                womensHelplineNumber = p.womensHelplineNumber, isWomensHelplineEnabled = p.womensHelplineEnabled,
                ambulanceNumber = p.ambulanceNumber, isAmbulanceEnabled = p.ambulanceEnabled,
                latitude = location?.latitude, longitude = location?.longitude, accuracyMeters = location?.accuracy,
                locationTimestampMs = location?.timestamp, incidentTimestampMs = incident.creationTimestamp,
                age = profile?.age, isJourney = isJourney,
                destination = if (isJourney) p.womensSafetyDestination else null,
                expectedArrivalTimeMs = if (isJourney) (p.womensSafetyTargetTimestamp + p.womensSafetyGracePeriodMinutes * 60_000L) else null,
                secureLiveTrackingUrl = null, locationCached = location?.isCached == true, locationSource = location?.source)
            ensureActiveIncident(id)
            outbox.updateIncident(id) { saved ->
                val newer = location != null && location.timestamp >= (saved.locationTimestamp ?: 0)
                saved.copy(messageText = message, senderName = name,
                senderPhone = profile?.phoneNumber.orEmpty(), policeNumber = p.policeNumber,
                helplineNumber = if (triggerSource.startsWith("WOMENS_SAFETY") && p.womensHelplineEnabled) p.womensHelplineNumber else "",
                locationTimestamp = if (newer) location?.timestamp else saved.locationTimestamp,
                latitude = if (newer) location?.latitude else saved.latitude,
                locationSource = if (newer) location?.source.orEmpty() else saved.locationSource,
                locationCached = if (newer) location?.isCached == true else saved.locationCached,
                longitude = if (newer) location?.longitude else saved.longitude,
                accuracy = if (newer) location?.accuracy else saved.accuracy,
                mapsUrl = if (newer) location?.mapsUrl else saved.mapsUrl,
                recipientPhoneNumbers = contacts.map { it.phoneNumber }, state = EmergencyIncident.STATE_DISPATCHING) }
            outbox.updateTransportStatus(id, "CLOUD", "QUEUED")
            IncidentSyncWorker.enqueue(context, id)
            if (location != null) {
                com.siddesh.smsalerts.data.location.LocationStore(context).save(uid, id, location)
                LocationSyncWorker.enqueue(context, uid)
            }
            _stage.value = SosStage.TransmittingSms
            val sms = if (p.directSmsEnabled && triggerSource != "OFFLINE_SOS") {
                if (contacts.isNotEmpty()) {
                    smsSender.sendEmergencySms(contacts = contacts, messageText = message, incidentId = id)
                } else {
                    outbox.updateTransportStatus(id, "SMS", "NO_RECIPIENTS")
                    SmsDispatchResult("No Recipients", 0, 0, emptyList(), "No emergency contacts configured.")
                }
            } else {
                outbox.updateTransportStatus(id, "SMS", if (triggerSource == "OFFLINE_SOS") "NOT_SELECTED_FOR_NEARBY" else "DISABLED_IN_PREFERENCES")
                SmsDispatchResult("Disabled", 0, contacts.size, emptyList(), "Direct SMS is disabled in settings.")
            }
            ensureActiveIncident(id)
            if (p.bluetoothMeshEnabled || triggerSource == "OFFLINE_SOS") {
                try {
                    val mesh = com.siddesh.smsalerts.mesh.BleMeshManager.getInstance(context)
                    mesh.broadcastSosPacket(com.siddesh.smsalerts.mesh.MeshPacket(messageId = id, emergencyId = id,
                        senderDeviceId = mesh.myDeviceId, emergencyType = emergencyType, senderName = name,
                        messageText = message, latitude = location?.latitude, longitude = location?.longitude,
                        locationTimestamp = location?.timestamp ?: 0L, accuracy = location?.accuracy, age = profile?.age))
                } catch (_: Exception) { outbox.updateTransportStatus(id, "BLE_MESH", "FAILED") }
            }
            try {
                LiveLocationManager.getInstance(context).startLiveSharing(id)
            } catch (_: Exception) {
                outbox.updateTransportStatus(id, "TRACKING", "UNAVAILABLE")
            }
            _stage.value = SosStage.SavingCloudAudit
            val alert = EmergencyAlert(alertId = id, uid = uid, emergencyType = emergencyType, triggerSource = triggerSource,
                message = message, latitude = location?.latitude, longitude = location?.longitude, mapsUrl = location?.mapsUrl,
                locationAvailable = location != null, timestamp = incident.creationTimestamp,
                recipients = contacts.map { it.name }, recipientPhoneNumbers = contacts.map { it.phoneNumber }, recipientCount = contacts.size,
                status = "EMERGENCY_ACTIVE", sendMethod = "Native SMS via SmsManager, BLE & Cloud",
                failureReason = sms.failureReason, createdAt = incident.creationTimestamp)
            if (uid.isNotBlank() && triggerSource != "OFFLINE_SOS") {
                try {
                    val saved = withTimeoutOrNull(4000) { AlertsRepository().saveEmergencyAlert(uid, alert) }
                    outbox.updateTransportStatus(id, "CLOUD_AUDIT", if (saved is Resource.Success) "SAVED" else "PENDING_OR_FAILED")
                } catch (_: Exception) { outbox.updateTransportStatus(id, "CLOUD_AUDIT", "PENDING_OR_FAILED") }
            }
            ensureActiveIncident(id)
            outbox.updateState(id, EmergencyIncident.STATE_ACTIVE)
            _stage.value = SosStage.EmergencyActive
            refreshActiveIncident()
            postNotification("Emergency active", "Incident saved. Open it to check location and actual delivery outcomes.")
        } catch (e: CancellationException) {
            if (outbox.getIncident(id)?.state in setOf(EmergencyIncident.STATE_RESOLVED, EmergencyIncident.STATE_CANCELLED)) return@withLock id
            throw e
        } catch (e: Exception) {
            _stage.value = SosStage.Error("Emergency processing interrupted. Saved work will retry without resending uncertain SMS submissions.")
            throw e
        } finally {
            try {
                if (dispatchWakeLock?.isHeld == true) dispatchWakeLock.release()
            } catch (_: Exception) {}
            isDispatching = false
        }
        id
    }

    private fun ensureActiveIncident(id: String) {
        val incident = outbox.getIncident(id) ?: throw CancellationException("Incident unavailable")
        if (incident.state in setOf(EmergencyIncident.STATE_RESOLVED, EmergencyIncident.STATE_CANCELLED)) throw CancellationException("Incident ended")
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"
        if (incident.userId != currentUid && incident.userId != "local_user" && currentUid != "local_user") throw CancellationException("Incident owner changed")
    }
    fun cancelEmergency() {
        try {
        countdownJob?.cancel()
        val id = currentIncidentId
        if (id == null) { _stage.value = SosStage.Cancelled; return }
        if (outbox.getIncident(id) == null) {
            // A cancellation during preference/disk setup leaves a tombstone before the pending save.
            outbox.updateState(id, EmergencyIncident.STATE_CANCELLED)
            _stage.value = SosStage.Cancelled
        } else if (outbox.transition(id, EmergencyIncident.STATE_COUNTDOWN, EmergencyIncident.STATE_CANCELLED)) {
            _stage.value = SosStage.Cancelled
        } else if (outbox.getIncident(id)?.state == EmergencyIncident.STATE_CANCELLED) {
            _stage.value = SosStage.Cancelled
        } else {
            _stage.value = SosStage.Error("Dispatch has already started. Open the incident to resolve it; submitted messages cannot be recalled.", canRetry = false)
        }
        } catch (e: Exception) {
            Log.e(TAG, "Cancellation could not be persisted", e)
            _stage.value = SosStage.Error("Cancellation was not saved. Retry immediately; scheduled escalation may still run.", canRetry = false)
        } finally {
            if (_stage.value is SosStage.Cancelled) {
                currentIncidentId?.let { LiveLocationManager.getInstance(context).stopLiveSharing(it) }
                currentIncidentId = null
            }
            refreshActiveIncident()
        }
    }
    fun resolveEmergency(incidentId: String = currentIncidentId.orEmpty()) {
        countdownJob?.cancel()
        if (incidentId.isNotBlank()) {
            outbox.updateState(incidentId, EmergencyIncident.STATE_RESOLVED)
            IncidentSyncWorker.enqueue(context, incidentId)
            LiveLocationManager.getInstance(context).stopLiveSharing(incidentId)
        }
        if (currentIncidentId == incidentId) {
            currentIncidentId = null
        }
        _stage.value = SosStage.Resolved
        refreshActiveIncident()
=======
import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import com.siddesh.smsalerts.data.location.LocationResult
import com.siddesh.smsalerts.data.location.LocationService
import com.siddesh.smsalerts.data.model.EmergencyAlert
import com.siddesh.smsalerts.data.model.EmergencyContact
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import com.siddesh.smsalerts.data.repository.AlertsRepository
import com.siddesh.smsalerts.data.repository.AuthRepository
import com.siddesh.smsalerts.data.repository.ContactsRepository
import com.siddesh.smsalerts.data.repository.UserRepository
import com.siddesh.smsalerts.data.sms.SmsDispatchResult
import com.siddesh.smsalerts.data.sms.SmsSender
import com.siddesh.smsalerts.ui.viewmodel.SosStage
import com.siddesh.smsalerts.utils.DateUtils
import com.siddesh.smsalerts.utils.HapticUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "SafeAlerts/Emergency"

/**
 * Centralized Emergency Dispatch Engine.
 * All triggers (Emergency Button, Shortcuts [Medical, Accident, Fire, Security],
 * Shake 3x, Voice "HELP", Foreground Service) route through this single engine.
 *
 * Guarantees:
 * - Single source of truth for alert generation, SMS transmission, and Firestore audit.
 * - Deduplication: rejects simultaneous triggers while an alert is processing.
 * - Sensor debouncing & post-trigger cooldown protection.
 * - Offline resilience: SMS sends first; Firestore database errors never block emergency SMS.
 */
class EmergencyManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: EmergencyManager? = null

        fun getInstance(context: Context): EmergencyManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EmergencyManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val authRepository = AuthRepository()
    private val userRepository = UserRepository.getInstance()
    private val contactsRepository = ContactsRepository()
    private val alertsRepository = AlertsRepository()
    private val locationService = LocationService(context)
    private val smsSender = SmsSender(context)
    private val preferences = UserPreferencesDataStore(context)

    private val _stage = MutableStateFlow<SosStage>(SosStage.Countdown(5))
    val stage: StateFlow<SosStage> = _stage.asStateFlow()

    private var countdownJob: Job? = null
    private var dispatchJob: Job? = null

    @Volatile
    var isDispatching: Boolean = false
        private set

    private var lastTriggerTimestamp = 0L

    fun startCountdown(
        triggerSource: String = "BUTTON",
        emergencyType: String = "PERSONAL",
        customMessage: String = "",
        countdownSeconds: Int = 5
    ) {
        if (isDispatching) {
            Log.w(TAG, "EMERGENCY_REJECTED: Alert is already being processed.")
            _stage.value = SosStage.Error(
                reason = "Emergency alert is already being processed.",
                canRetry = false
            )
            return
        }

        countdownJob?.cancel()
        dispatchJob?.cancel()

        if (countdownSeconds <= 0) {
            dispatchJob = scope.launch {
                executeDispatch(
                    triggerSource = triggerSource,
                    emergencyType = emergencyType,
                    customMessage = customMessage
                )
            }
            return
        }

        countdownJob = scope.launch {
            for (sec in countdownSeconds downTo 1) {
                _stage.value = SosStage.Countdown(sec)
                HapticUtils.vibrateCountdownTick(context)
                delay(1000L)
            }
            executeDispatch(
                triggerSource = triggerSource,
                emergencyType = emergencyType,
                customMessage = customMessage
            )
        }
    }

    fun triggerAutomaticEmergency(source: String) {
        Log.i(TAG, "AUTOMATIC_EMERGENCY_INVOKED: source=$source")
        triggerEmergency(
            triggerSource = source,
            emergencyType = "PERSONAL EMERGENCY",
            customMessage = "I need immediate assistance.",
            bypassCountdown = true
        )
    }

    fun triggerShakeEmergency() = triggerAutomaticEmergency("SHAKE")

    fun triggerVoiceEmergency() = triggerAutomaticEmergency("VOICE")

    fun triggerManualEmergency(type: String) {
        startCountdown(
            triggerSource = "BUTTON",
            emergencyType = type,
            customMessage = "",
            countdownSeconds = 5
        )
    }

    fun triggerEmergency(
        triggerSource: String,
        emergencyType: String = "PERSONAL",
        customMessage: String = "",
        bypassCountdown: Boolean = false
    ) {
        val now = System.currentTimeMillis()

        if (isDispatching) {
            Log.w(TAG, "EMERGENCY_REJECTED: Alert dispatch in progress.")
            _stage.value = SosStage.Error(
                reason = "Emergency alert is already being processed.",
                canRetry = false
            )
            return
        }

        // Enforce cooldown (minimum 30s to prevent accidental repeat triggers)
        val cooldownMs = 30_000L
        if (lastTriggerTimestamp > 0L && now - lastTriggerTimestamp < cooldownMs) {
            val remainingSec = ((lastTriggerTimestamp + cooldownMs - now) / 1000).coerceAtLeast(1)
            Log.w(TAG, "EMERGENCY_COOLDOWN_ACTIVE: ${remainingSec}s remaining.")
            _stage.value = SosStage.Error(
                reason = "Cooldown active. Please wait $remainingSec seconds before triggering another emergency.",
                canRetry = false
            )
            return
        }

        countdownJob?.cancel()

        if (bypassCountdown) {
            scope.launch {
                executeDispatch(
                    triggerSource = triggerSource,
                    emergencyType = emergencyType,
                    customMessage = customMessage
                )
            }
        } else {
            startCountdown(
                triggerSource = triggerSource,
                emergencyType = emergencyType,
                customMessage = customMessage
            )
        }
    }

    fun cancelEmergency() {
        countdownJob?.cancel()
        dispatchJob?.cancel()
        isDispatching = false
        _stage.value = SosStage.Cancelled
        HapticUtils.vibrateTick(context)
        Log.i(TAG, "EMERGENCY_CANCELLED by user.")
    }

    private suspend fun executeDispatch(
        triggerSource: String,
        emergencyType: String,
        customMessage: String
    ) {
        if (isDispatching) return
        isDispatching = true
        lastTriggerTimestamp = System.currentTimeMillis()

        Log.i(TAG, "EMERGENCY_TRIGGERED source=$triggerSource, type=$emergencyType")

        try {
            val userId = authRepository.currentUserId
            if (userId == null) {
                _stage.value = SosStage.Error(
                    reason = "User session expired. Please sign in to authenticate your emergency alerts.",
                    canRetry = false
                )
                Log.e(TAG, "AUTH_FAILURE: User not authenticated.")
                return
            }

            // 1. Fetch enabled trusted contacts
            val contactsList: List<EmergencyContact> = try {
                contactsRepository.getContactsFlow(userId).first().filter { it.isActive && it.enabled }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching contacts: ${e.message}")
                emptyList()
            }

            if (contactsList.isEmpty()) {
                Log.e(TAG, "NO_TRUSTED_CONTACTS: Emergency triggered ($triggerSource) but zero active contacts found.")
                postNotification(
                    title = "⚠️ Safe Alerts: No Contacts",
                    message = "Emergency alert triggered by $triggerSource, but no trusted contacts are registered."
                )
                _stage.value = SosStage.Error(
                    reason = "No active emergency contacts configured! Please add at least one trusted contact in the Contacts tab.",
                    canRetry = false
                )
                HapticUtils.vibrateError(context)
                return
            }

            // 2. Verify SEND_SMS permission upfront
            if (!smsSender.hasSmsPermission()) {
                Log.w(TAG, "EMERGENCY_HALTED: SEND_SMS permission is not granted")
                _stage.value = SosStage.Error(
                    reason = "SMS permission (SEND_SMS) is required to automatically send emergency alerts. Please grant SMS permission in App Settings.",
                    canRetry = true,
                    fallbackIntent = com.siddesh.smsalerts.utils.PermissionManager.createAppSettingsIntent(context)
                )
                HapticUtils.vibrateError(context)
                return
            }

            // 3. Acquire current GPS location (waiting for valid location, never fake/null)
            _stage.value = SosStage.AcquiringLocation
            var latitude: Double? = null
            var longitude: Double? = null
            var mapsUrl: String? = null

            when (val locResult = locationService.getCurrentLocation(timeoutMs = 10000L)) {
                is LocationResult.Success -> {
                    latitude = locResult.latitude
                    longitude = locResult.longitude
                    mapsUrl = "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
                    Log.d(TAG, "LOCATION_SUCCESS: coordinates acquired successfully")
                }
                is LocationResult.GpsDisabled -> {
                    Log.w(TAG, "EMERGENCY_HALTED: GPS is disabled on device")
                    _stage.value = SosStage.Error(
                        reason = "Location/GPS is disabled on your device. Please turn on Location in Settings so your emergency coordinates can be attached.",
                        canRetry = true,
                        fallbackIntent = com.siddesh.smsalerts.utils.PermissionManager.createLocationSettingsIntent()
                    )
                    HapticUtils.vibrateError(context)
                    return
                }
                is LocationResult.PermissionDenied -> {
                    Log.w(TAG, "EMERGENCY_HALTED: Location permission denied")
                    _stage.value = SosStage.Error(
                        reason = "Location permission is required to acquire your emergency coordinates. Please grant location permission in App Settings.",
                        canRetry = true,
                        fallbackIntent = com.siddesh.smsalerts.utils.PermissionManager.createAppSettingsIntent(context)
                    )
                    HapticUtils.vibrateError(context)
                    return
                }
                is LocationResult.Unavailable -> {
                    Log.w(TAG, "EMERGENCY_HALTED: Location unavailable: ${locResult.reason}")
                    _stage.value = SosStage.Error(
                        reason = "Unable to acquire an accurate GPS location fix (${locResult.reason}). Please ensure you have satellite reception and retry.",
                        canRetry = true
                    )
                    HapticUtils.vibrateError(context)
                    return
                }
            }

            // 4. Format emergency message with user name and location
            _stage.value = SosStage.TransmittingSms
            val userName = userRepository.userProfile.value?.let {
                if (it.fullName.isNotBlank()) it.fullName else it.name
            } ?: authRepository.currentUser?.displayName ?: "SafeAlert User"

            val chosenMessage = when {
                customMessage.isNotBlank() -> customMessage
                else -> "I need immediate assistance."
            }

            val timestampStr = DateUtils.formatFullDateTime(System.currentTimeMillis())
            val messageText = if (triggerSource == "SHAKE" || triggerSource == "VOICE" || triggerSource == "AUTOMATIC") {
                // PART H exact automatic template
                buildString {
                    append("🚨 EMERGENCY ALERT\n\n")
                    append("I need immediate assistance.\n\n")
                    append("Name: $userName\n\n")
                    if (!mapsUrl.isNullOrBlank()) {
                        append("📍 My current location:\n$mapsUrl\n\n")
                    }
                    append("Time:\n$timestampStr\n\n")
                    append("Please contact me immediately.")
                }
            } else {
                buildString {
                    append("🚨 EMERGENCY ALERT\n\n")
                    append("Type: $emergencyType\n")
                    append("Name: $userName\n\n")
                    append(chosenMessage)
                    if (!mapsUrl.isNullOrBlank()) {
                        append("\n\n📍 Location:\n$mapsUrl")
                    }
                    append("\n\nTime: $timestampStr")
                    append("\n\nPlease contact me immediately.")
                }
            }

            // 4. Send SMS to all enabled trusted contacts
            val smsResult = smsSender.sendEmergencySms(contactsList, messageText)
            Log.i(TAG, "SMS_DISPATCH: successful=${smsResult.successfulCount}/${contactsList.size}")

            val fallbackIntent = if (smsResult.successfulCount == 0) {
                val phoneNumbers = contactsList.map { it.phoneNumber }
                smsSender.createFallbackSmsIntent(phoneNumbers, messageText)
            } else null

            // 5. Store alert audit in Firestore users/{uid}/alerts/{alertId}
            _stage.value = SosStage.SavingCloudAudit
            val alertRecord = EmergencyAlert(
                alertId = "",
                uid = userId,
                emergencyType = emergencyType,
                triggerSource = triggerSource,
                message = messageText,
                latitude = latitude,
                longitude = longitude,
                mapsUrl = mapsUrl,
                locationAvailable = (latitude != null && longitude != null),
                timestamp = System.currentTimeMillis(),
                recipients = contactsList.map { "${it.name} (${it.phoneNumber})" },
                recipientPhoneNumbers = contactsList.map { it.phoneNumber },
                recipientCount = contactsList.size,
                status = if (smsResult.successfulCount > 0) "SMS_SENT" else if (fallbackIntent != null) "SMS_COMPOSER_OPENED" else "SMS_FAILED",
                sendMethod = if (smsResult.successfulCount > 0) "Direct SMS" else "SMS Composer",
                failureReason = smsResult.failureReason,
                createdAt = System.currentTimeMillis()
            )

            try {
                alertsRepository.saveEmergencyAlert(userId, alertRecord)
                Log.d(TAG, "ALERT_SAVED to Firestore")
            } catch (e: Exception) {
                Log.w(TAG, "FIRESTORE_AUDIT_WARNING (offline safety active): ${e.message}")
            }

            if (smsResult.successfulCount > 0) {
                HapticUtils.vibrateEmergencyAlertSent(context)
                postNotification(
                    title = "🚨 Emergency Alert Dispatched ($triggerSource)",
                    message = "SMS dispatched to ${smsResult.successfulCount} trusted contact(s) with your GPS coordinates."
                )
                _stage.value = SosStage.Completed(alertRecord, smsResult)
            } else {
                HapticUtils.vibrateError(context)
                postNotification(
                    title = "❌ Emergency SMS Failed ($triggerSource)",
                    message = smsResult.failureReason ?: "Failed to transmit emergency SMS."
                )
                _stage.value = SosStage.Error(
                    reason = smsResult.failureReason ?: "Failed to transmit emergency SMS.",
                    canRetry = true,
                    fallbackIntent = fallbackIntent
                )
            }
        } finally {
            isDispatching = false
        }
>>>>>>> origin/main
    }

    private fun postNotification(title: String, message: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                ?: return

            val channelId = "emergency_alerts_channel"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "Emergency Alerts",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "High priority emergency alert status and dispatch notifications"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val openIntent = Intent(context, com.siddesh.smsalerts.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
<<<<<<< HEAD
                putExtra("incidentId", currentIncidentId)
=======
>>>>>>> origin/main
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                openIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
<<<<<<< HEAD
                .setSmallIcon(com.siddesh.smsalerts.R.drawable.ic_notification_shield)
=======
                .setSmallIcon(com.siddesh.smsalerts.R.drawable.ic_app_logo)
>>>>>>> origin/main
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to post emergency notification: ${e.message}")
        }
    }
}
