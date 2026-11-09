package com.siddesh.smsalerts.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.siddesh.smsalerts.utils.PrivacyLog as Log
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

private const val TAG = "SafeAlerts/WomensSafety"

data class WomensSafetyJourneyState(
    val isActive: Boolean = false,
    val destination: String = "",
    val durationMinutes: Int = 30,
    val targetTimestamp: Long = 0L,
    val remainingSeconds: Long = 0L,
    val gracePeriodMinutes: Int = 2,
    val isGracePeriodActive: Boolean = false,
    val graceRemainingSeconds: Long = 0L,
    val shareLiveLocation: Boolean = true,
    val alertHelpline1091: Boolean = true,
    val isEscalated: Boolean = false,
    val escalatedIncidentId: String = ""
)

/**
 * Controller for Section 8: Women's Safety Journey.
 * Provides user-configured journey deadlines, grace periods, GPS location sharing,
 * and authoritative escalation to EmergencyManager and Women's Helpline 1091 on timeout.
 */
class WomensSafetyManager(private val context: Context) {

    companion object {
        const val ACTION_JOURNEY_EXPIRED = "com.siddesh.smsalerts.ACTION_WOMENS_SAFETY_EXPIRED"
        const val ACTION_JOURNEY_FINAL_EXPIRY = "com.siddesh.smsalerts.ACTION_WOMENS_SAFETY_FINAL_EXPIRY"
        const val ACTION_JOURNEY_ARRIVED = "com.siddesh.smsalerts.ACTION_WOMENS_SAFETY_ARRIVED"
        const val NOTIFICATION_ID = 4040
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    private val preferences = UserPreferencesDataStore(context)
    private val _operationError = MutableStateFlow<String?>(null)
    val operationError = _operationError.asStateFlow()
    fun clearOperationError() { _operationError.value = null }
    private val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO +
        kotlinx.coroutines.CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "Journey persistence or scheduling failed", error)
            _operationError.value = "Journey change could not be completed. Check device storage and verify the saved deadline before relying on it."
        })

    private val _journeyState = MutableStateFlow(WomensSafetyJourneyState())
    val journeyState: StateFlow<WomensSafetyJourneyState> = _journeyState.asStateFlow()
    private var observer: kotlinx.coroutines.Job? = null
    fun closeObserver() { observer?.cancel() }

    init {
        observer = scope.launch {
            preferences.preferencesFlow.collect { prefs ->
                val now = System.currentTimeMillis()
                if (prefs.womensSafetyActive) {
                    val remaining = if (prefs.womensSafetyTargetTimestamp > 0) {
                        ((prefs.womensSafetyTargetTimestamp - now) / 1000).coerceAtLeast(0)
                    } else 0L

                    val graceRemaining = if (prefs.womensSafetyGracePeriodActive && prefs.womensSafetyGracePeriodTargetTimestamp > 0) {
                        ((prefs.womensSafetyGracePeriodTargetTimestamp - now) / 1000).coerceAtLeast(0)
                    } else 0L

                    _journeyState.value = WomensSafetyJourneyState(
                        isActive = true,
                        destination = prefs.womensSafetyDestination,
                        durationMinutes = prefs.womensSafetyDurationMinutes,
                        targetTimestamp = prefs.womensSafetyTargetTimestamp,
                        remainingSeconds = remaining,
                        gracePeriodMinutes = prefs.womensSafetyGracePeriodMinutes,
                        isGracePeriodActive = prefs.womensSafetyGracePeriodActive,
                        graceRemainingSeconds = graceRemaining,
                        shareLiveLocation = prefs.womensSafetyShareLiveLocation,
                        alertHelpline1091 = prefs.womensSafetyAlertHelpline1091,
                        isEscalated = false,
                        escalatedIncidentId = ""
                    )
                } else {
                    _journeyState.value = WomensSafetyJourneyState(
                        isActive = false,
                        destination = "",
                        durationMinutes = prefs.womensSafetyDurationMinutes,
                        targetTimestamp = 0L,
                        remainingSeconds = 0L,
                        gracePeriodMinutes = prefs.womensSafetyGracePeriodMinutes,
                        isGracePeriodActive = false,
                        graceRemainingSeconds = 0L,
                        shareLiveLocation = prefs.womensSafetyShareLiveLocation,
                        alertHelpline1091 = prefs.womensSafetyAlertHelpline1091,
                        isEscalated = prefs.womensSafetyEscalated,
                        escalatedIncidentId = prefs.womensSafetyIncidentId
                    )
                }
            }
        }
    }

    fun startJourney(
        destination: String,
        durationMinutes: Int,
        gracePeriodMinutes: Int = 2,
        shareLocation: Boolean = true,
        alertHelpline: Boolean = true,
        latitude: Double? = null, longitude: Double? = null
    ) {
        if (durationMinutes !in 1..1440 || destination.isBlank()) return
        val targetMillis = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        Log.i(TAG, "Starting journey timer")

        scope.launch {
            preferences.startWomensSafetyJourney(
                destination = destination,
                durationMinutes = durationMinutes,
                targetTimestamp = targetMillis,
                gracePeriodMinutes = gracePeriodMinutes,
                shareLiveLocation = shareLocation,
                latitude = latitude, longitude = longitude,
                alertHelpline1091 = alertHelpline
            )
            SafetyTimerWork.schedule(context, SafetyTimerWork.JOURNEY, preferences.preferencesFlow.first())
            if (shareLocation) LiveLocationManager.getInstance(context).startLiveSharing("journey")
        }


    }

    private var lastExtensionTime = 0L
    fun extendDuration(extraMinutes: Int) {
        val elapsed = android.os.SystemClock.elapsedRealtime()
        if (elapsed - lastExtensionTime < 1000 || extraMinutes !in 1..60) return
        lastExtensionTime = elapsed
        val current = _journeyState.value
        if (!current.isActive) return
        val newTarget = System.currentTimeMillis() + ((current.remainingSeconds + (extraMinutes * 60)) * 1000L)
        Log.i(TAG, "Extending Women's Safety Journey by $extraMinutes mins (new target=$newTarget)")

        cancelAlarm(ACTION_JOURNEY_EXPIRED, 201)
        cancelAlarm(ACTION_JOURNEY_FINAL_EXPIRY, 202)

        scope.launch {
            preferences.extendWomensSafetyJourney(newTarget)
            SafetyTimerWork.schedule(context, SafetyTimerWork.JOURNEY, preferences.preferencesFlow.first())
        }


    }

    fun markArrivedOrCancel() {
        Log.i(TAG, "User marked arrived / cancelled Women's Safety Journey")
        cancelAlarm(ACTION_JOURNEY_EXPIRED, 201)
        cancelAlarm(ACTION_JOURNEY_FINAL_EXPIRY, 202)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        notificationManager?.cancel(NOTIFICATION_ID)

        scope.launch {
            preferences.cancelOrCompleteWomensSafetyJourney()
        }
    }

    fun resolveEscalatedJourney() {
        Log.i(TAG, "Resolving escalated Women's Safety Journey emergency")
        EmergencyManager.getInstance(context).resolveEmergency(_journeyState.value.escalatedIncidentId)
        scope.launch {
            preferences.clearWomensSafetyEscalation()
        }
    }

    fun updateRemainingTime() {
        val current = _journeyState.value
        if (current.isActive) {
            val now = System.currentTimeMillis()
            val remaining = if (current.targetTimestamp > 0) {
                ((current.targetTimestamp - now) / 1000).coerceAtLeast(0)
            } else 0L

            val graceRemaining = if (current.isGracePeriodActive) {
                val graceTarget = current.targetTimestamp + (current.gracePeriodMinutes * 60 * 1000L)
                ((graceTarget - now) / 1000).coerceAtLeast(0)
            } else 0L

            _journeyState.value = current.copy(
                remainingSeconds = remaining,
                graceRemainingSeconds = graceRemaining
            )
        }
    }

    private fun cancelAlarm(action: String, requestCode: Int) {
        if (alarmManager == null) return
        val intent = Intent(context, WomensSafetyReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
