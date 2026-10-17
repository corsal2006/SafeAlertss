package com.siddesh.smsalerts.service

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import com.siddesh.smsalerts.data.location.LocationResult
import com.siddesh.smsalerts.data.location.LocationService
import com.siddesh.smsalerts.data.model.EmergencyAlert
import com.siddesh.smsalerts.data.model.EmergencyContact
import com.siddesh.smsalerts.data.model.EmergencyIncident
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import com.siddesh.smsalerts.data.repository.AlertsRepository
import com.siddesh.smsalerts.data.repository.AuthRepository
import com.siddesh.smsalerts.data.repository.ContactsRepository
import com.siddesh.smsalerts.data.repository.LocalContactsCache
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
    val outbox = EmergencyOutbox(context)
    var currentIncidentId: String? = null
        private set

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
        _stage.value = SosStage.TriggerDetected(triggerSource, emergencyType)

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
        currentIncidentId?.let { outbox.updateState(it, EmergencyIncident.STATE_CANCELLED) }
        LiveLocationManager.getInstance(context).stopLiveSharing()
        _stage.value = SosStage.Cancelled
        HapticUtils.vibrateTick(context)
        Log.i(TAG, "EMERGENCY_CANCELLED by user.")
    }

    fun resolveEmergency() {
        countdownJob?.cancel()
        dispatchJob?.cancel()
        isDispatching = false
        currentIncidentId?.let { outbox.updateState(it, EmergencyIncident.STATE_RESOLVED) }
        LiveLocationManager.getInstance(context).stopLiveSharing()
        _stage.value = SosStage.Resolved
        HapticUtils.vibrateTick(context)
        Log.i(TAG, "EMERGENCY_RESOLVED.")
    }

    private suspend fun executeDispatch(
        triggerSource: String,
        emergencyType: String,
        customMessage: String
    ) {
        if (isDispatching) return
        isDispatching = true
        lastTriggerTimestamp = System.currentTimeMillis()
        val incidentId = java.util.UUID.randomUUID().toString()
        currentIncidentId = incidentId

        _stage.value = SosStage.EmergencyActive
        Log.i(TAG, "EMERGENCY_ACTIVE: incidentId=$incidentId, source=$triggerSource, type=$emergencyType")

        try {
            // Fetch current user preferences
            val userPrefs = try {
                preferences.preferencesFlow.first()
            } catch (e: Exception) {
                com.siddesh.smsalerts.data.preferences.AppUserPreferences()
            }

            val userId = authRepository.currentUserId ?: "offline_user_${userPrefs.pseudonymousDeviceId.ifBlank { "device" }}"

            // 1. Fetch enabled trusted contacts with local offline fallback
            val contactsList: List<EmergencyContact> = try {
                val repoContacts = contactsRepository.getContactsFlow(userId).first().filter { it.isActive && it.enabled }
                if (repoContacts.isNotEmpty()) repoContacts else LocalContactsCache.getContacts().filter { it.isActive && it.enabled }
            } catch (e: Exception) {
                LocalContactsCache.getContacts().filter { it.isActive && it.enabled }
            }

            // Persist incident to durable outbox before external dispatch
            val incident = EmergencyIncident(
                incidentId = incidentId,
                triggerSource = triggerSource,
                emergencyType = emergencyType,
                creationTimestamp = System.currentTimeMillis(),
                userId = userId,
                messageText = customMessage,
                recipientPhoneNumbers = contactsList.map { it.phoneNumber },
                state = EmergencyIncident.STATE_INCIDENT_CREATED
            )
            outbox.saveIncident(incident)

            if (contactsList.isEmpty()) {
                Log.e(TAG, "NO_TRUSTED_CONTACTS: Emergency triggered ($triggerSource) but zero active contacts found.")
                outbox.updateState(incidentId, EmergencyIncident.STATE_FAILED, "No active emergency contacts configured.")
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

            outbox.updateState(incidentId, EmergencyIncident.STATE_DISPATCHING)

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

            // 3. Acquire current GPS location (waiting for valid location, with non-blocking fallback if allowed)
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
                    if (userPrefs.allowSmsWithoutLocation) {
                        Log.w(TAG, "GPS disabled on device, but allowSmsWithoutLocation is enabled. Proceeding without GPS.")
                    } else {
                        Log.w(TAG, "EMERGENCY_HALTED: GPS is disabled on device")
                        _stage.value = SosStage.Error(
                            reason = "Location/GPS is disabled on your device. Please turn on Location in Settings so your emergency coordinates can be attached.",
                            canRetry = true,
                            fallbackIntent = com.siddesh.smsalerts.utils.PermissionManager.createLocationSettingsIntent()
                        )
                        HapticUtils.vibrateError(context)
                        return
                    }
                }
                is LocationResult.PermissionDenied -> {
                    if (userPrefs.allowSmsWithoutLocation) {
                        Log.w(TAG, "Location permission denied, but allowSmsWithoutLocation is enabled. Proceeding without GPS.")
                    } else {
                        Log.w(TAG, "EMERGENCY_HALTED: Location permission denied")
                        _stage.value = SosStage.Error(
                            reason = "Location permission is required to acquire your emergency coordinates. Please grant location permission in App Settings.",
                            canRetry = true,
                            fallbackIntent = com.siddesh.smsalerts.utils.PermissionManager.createAppSettingsIntent(context)
                        )
                        HapticUtils.vibrateError(context)
                        return
                    }
                }
                is LocationResult.Unavailable -> {
                    if (userPrefs.allowSmsWithoutLocation) {
                        Log.w(TAG, "Location fix unavailable (${locResult.reason}), but allowSmsWithoutLocation is enabled. Proceeding without GPS.")
                    } else {
                        Log.w(TAG, "EMERGENCY_HALTED: Location unavailable: ${locResult.reason}")
                        _stage.value = SosStage.Error(
                            reason = "Unable to acquire an accurate GPS location fix (${locResult.reason}). Please ensure you have satellite reception and retry.",
                            canRetry = true
                        )
                        HapticUtils.vibrateError(context)
                        return
                    }
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
                buildString {
                    append("🚨 EMERGENCY ALERT\n\n")
                    append("I need immediate assistance.\n\n")
                    append("Name: $userName\n\n")
                    if (!mapsUrl.isNullOrBlank()) {
                        append("📍 My current location:\n$mapsUrl\n\n")
                    } else {
                        append("📍 Location: Temporarily Unavailable\n\n")
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
                    } else {
                        append("\n\n📍 Location: Temporarily Unavailable")
                    }
                    append("\n\nTime: $timestampStr")
                    append("\n\nPlease contact me immediately.")
                }
            }

            // 5. Send cellular SMS to all enabled trusted contacts
            val smsResult = smsSender.sendEmergencySms(contactsList, messageText)
            Log.i(TAG, "SMS_DISPATCH: successful=${smsResult.successfulCount}/${contactsList.size}")

            val fallbackIntent = if (smsResult.successfulCount == 0) {
                val phoneNumbers = contactsList.map { it.phoneNumber }
                smsSender.createFallbackSmsIntent(phoneNumbers, messageText)
            } else null

            outbox.updateTransportStatus(incidentId, "SMS", if (smsResult.successfulCount > 0) "SENT" else "FAILED")
            for (contact in contactsList) {
                outbox.updateRecipientStatus(incidentId, contact.phoneNumber, if (smsResult.successfulCount > 0) "SENT" else "FAILED")
            }

            // 6. Broadcast over Offline Bluetooth Mesh Network
            if (userPrefs.bluetoothMeshEnabled) {
                try {
                    val meshManager = com.siddesh.smsalerts.mesh.BleMeshManager.getInstance(context)
                    val meshPacket = com.siddesh.smsalerts.mesh.MeshPacket(
                        messageId = java.util.UUID.randomUUID().toString(),
                        emergencyId = incidentId,
                        senderDeviceId = meshManager.myDeviceId,
                        emergencyType = emergencyType,
                        senderName = userName,
                        messageText = chosenMessage,
                        latitude = latitude,
                        longitude = longitude,
                        locationTimestamp = System.currentTimeMillis()
                    )
                    meshManager.broadcastSosPacket(meshPacket)
                    outbox.updateTransportStatus(incidentId, "BLE_MESH", "BROADCAST")
                    Log.i(TAG, "BLE_MESH_DISPATCH: Emergency packet broadcasted over local mesh.")
                } catch (e: Exception) {
                    outbox.updateTransportStatus(incidentId, "BLE_MESH", "FAILED")
                    Log.w(TAG, "Mesh broadcast warning: ${e.message}")
                }
            }

            // 7. Initiate Live Location Sharing if enabled
            if (userPrefs.locationSharingEnabled && latitude != null && longitude != null) {
                try {
                    LiveLocationManager.getInstance(context).startLiveSharing(incidentId)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to start live location sharing: ${e.message}")
                }
            }

            // 8. Store alert audit in Firestore users/{uid}/alerts/{alertId}
            _stage.value = SosStage.SavingCloudAudit
            val alertRecord = EmergencyAlert(
                alertId = incidentId,
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
                sendMethod = if (smsResult.successfulCount > 0) "Direct SMS + BLE Mesh" else "SMS Composer + BLE Mesh",
                failureReason = smsResult.failureReason,
                createdAt = System.currentTimeMillis()
            )

            try {
                alertsRepository.saveEmergencyAlert(userId, alertRecord)
                outbox.updateTransportStatus(incidentId, "CLOUD_AUDIT", "SAVED")
                Log.d(TAG, "ALERT_SAVED to Firestore")
            } catch (e: Exception) {
                outbox.updateTransportStatus(incidentId, "CLOUD_AUDIT", "FAILED_OFFLINE")
                Log.w(TAG, "FIRESTORE_AUDIT_WARNING (offline safety active): ${e.message}")
            }

            if (smsResult.successfulCount > 0) {
                outbox.updateState(incidentId, EmergencyIncident.STATE_ACTIVE)
                HapticUtils.vibrateEmergencyAlertSent(context)
                postNotification(
                    title = "🚨 Emergency Alert Dispatched ($triggerSource)",
                    message = "SMS dispatched to ${smsResult.successfulCount} trusted contact(s) & broadcast over Bluetooth mesh."
                )
                _stage.value = SosStage.Completed(alertRecord, smsResult)
            } else {
                outbox.updateState(incidentId, EmergencyIncident.STATE_FAILED, smsResult.failureReason)
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
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                openIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(com.siddesh.smsalerts.R.drawable.ic_notification_shield)
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
