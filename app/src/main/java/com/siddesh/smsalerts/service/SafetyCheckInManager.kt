package com.siddesh.smsalerts.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
<<<<<<< HEAD
import com.siddesh.smsalerts.utils.PrivacyLog as Log
=======
>>>>>>> origin/main
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
<<<<<<< HEAD
import kotlinx.coroutines.flow.first

private const val TAG = "SafeAlerts/CheckInMgr"
=======
>>>>>>> origin/main

data class SafetyCheckInState(
    val isActive: Boolean = false,
    val durationMinutes: Int = 30,
    val targetTimeMillis: Long = 0L,
    val remainingSeconds: Long = 0L,
<<<<<<< HEAD
    val isExpired: Boolean = false,
    val gracePeriodMinutes: Int = 2,
    val isGracePeriodActive: Boolean = false,
    val graceRemainingSeconds: Long = 0L,
    val isPaused: Boolean = false,
    val escalationPolicy: String = "AUTO_EMERGENCY",
    val isEscalated: Boolean = false,
    val escalatedIncidentId: String = ""
)

/**
 * Controller for Section 4: Safety Check-In Timer.
 * Provides user-configured timers with persistent deadlines, grace periods,
 * pause/resume, and authoritative escalation to EmergencyManager on expiration.
 */
=======
    val isExpired: Boolean = false
)

>>>>>>> origin/main
class SafetyCheckInManager(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    private val preferences = UserPreferencesDataStore(context)
<<<<<<< HEAD
    private val _operationError = MutableStateFlow("")
    val operationError = _operationError.asStateFlow()
    private val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO +
        kotlinx.coroutines.CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "Check-In persistence or scheduling failed", error)
            _operationError.value = "Check-In change could not be completed. Check device storage and verify the saved deadline before relying on it."
        })
=======
    private val scope = CoroutineScope(Dispatchers.IO)
>>>>>>> origin/main

    private val _checkInState = MutableStateFlow(SafetyCheckInState())
    val checkInState: StateFlow<SafetyCheckInState> = _checkInState.asStateFlow()

    init {
        scope.launch {
            preferences.preferencesFlow.collect { prefs ->
                val now = System.currentTimeMillis()
<<<<<<< HEAD
                if (prefs.safetyCheckInActive) {
                    val remaining = if (prefs.safetyCheckInPaused) {
                        prefs.safetyCheckInRemainingSecondsOnPause
                    } else if (prefs.safetyCheckInTargetTimestamp > 0) {
                        ((prefs.safetyCheckInTargetTimestamp - now) / 1000).coerceAtLeast(0)
                    } else 0L

                    val graceRemaining = if (prefs.safetyCheckInGracePeriodActive && prefs.safetyCheckInGracePeriodTargetTimestamp > 0) {
                        ((prefs.safetyCheckInGracePeriodTargetTimestamp - now) / 1000).coerceAtLeast(0)
                    } else 0L

=======
                if (prefs.safetyCheckInActive && prefs.safetyCheckInTargetTimestamp > 0) {
                    val remaining = ((prefs.safetyCheckInTargetTimestamp - now) / 1000).coerceAtLeast(0)
>>>>>>> origin/main
                    _checkInState.value = SafetyCheckInState(
                        isActive = true,
                        durationMinutes = prefs.safetyCheckInDurationMinutes,
                        targetTimeMillis = prefs.safetyCheckInTargetTimestamp,
                        remainingSeconds = remaining,
<<<<<<< HEAD
                        isExpired = remaining == 0L,
                        gracePeriodMinutes = prefs.safetyCheckInGracePeriodMinutes,
                        isGracePeriodActive = prefs.safetyCheckInGracePeriodActive,
                        graceRemainingSeconds = graceRemaining,
                        isPaused = prefs.safetyCheckInPaused,
                        escalationPolicy = prefs.safetyCheckInEscalationPolicy,
                        isEscalated = false,
                        escalatedIncidentId = ""
=======
                        isExpired = remaining == 0L
>>>>>>> origin/main
                    )
                } else {
                    _checkInState.value = SafetyCheckInState(
                        isActive = false,
                        durationMinutes = prefs.safetyCheckInDurationMinutes,
                        targetTimeMillis = 0L,
                        remainingSeconds = 0L,
<<<<<<< HEAD
                        isExpired = false,
                        gracePeriodMinutes = prefs.safetyCheckInGracePeriodMinutes,
                        isGracePeriodActive = false,
                        graceRemainingSeconds = 0L,
                        isPaused = false,
                        escalationPolicy = prefs.safetyCheckInEscalationPolicy,
                        isEscalated = prefs.safetyCheckInEscalated,
                        escalatedIncidentId = prefs.safetyCheckInIncidentId
=======
                        isExpired = false
>>>>>>> origin/main
                    )
                }
            }
        }
    }

<<<<<<< HEAD
    fun startCheckIn(
        durationMinutes: Int,
        graceMinutes: Int = 2,
        escalationPolicy: String = "AUTO_EMERGENCY"
    ) {
        val targetMillis = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        Log.i(TAG, "Starting Safety Check-In: duration=${durationMinutes}m, grace=${graceMinutes}m, target=$targetMillis")

        scope.launch {
            preferences.startSafetyCheckIn(durationMinutes, targetMillis, graceMinutes, escalationPolicy)
            SafetyTimerWork.schedule(context, SafetyTimerWork.CHECK_IN, preferences.preferencesFlow.first())
        }


    }

    fun pauseCheckIn() {
        val current = _checkInState.value
        if (!current.isActive || current.isPaused) return
        Log.i(TAG, "Pausing Safety Check-In with ${current.remainingSeconds}s remaining")

        cancelAlarm(SafetyCheckInReceiver.ACTION_CHECK_IN_EXPIRED, 101)
        cancelAlarm(SafetyCheckInReceiver.ACTION_CHECK_IN_FINAL_EXPIRY, 102)

        scope.launch {
            preferences.pauseSafetyCheckIn(current.remainingSeconds)
        }
    }

    fun resumeCheckIn() {
        val current = _checkInState.value
        if (!current.isActive || !current.isPaused) return
        val newTargetMillis = System.currentTimeMillis() + (current.remainingSeconds * 1000L)
        Log.i(TAG, "Resuming Safety Check-In with new target=$newTargetMillis")

        scope.launch {
            preferences.resumeSafetyCheckIn(newTargetMillis)
            SafetyTimerWork.schedule(context, SafetyTimerWork.CHECK_IN, preferences.preferencesFlow.first())
        }


    }

    fun markSafeOrCancel() {
        Log.i(TAG, "User marked safe / cancelled Safety Check-In")
        cancelAlarm(SafetyCheckInReceiver.ACTION_CHECK_IN_EXPIRED, 101)
        cancelAlarm(SafetyCheckInReceiver.ACTION_CHECK_IN_FINAL_EXPIRY, 102)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        notificationManager?.cancel(SafetyCheckInReceiver.NOTIFICATION_ID)

        scope.launch {
            preferences.cancelOrCompleteSafetyCheckIn()
        }
    }

    fun resolveEscalatedCheckIn() {
        Log.i(TAG, "Resolving escalated emergency check-in")
        EmergencyManager.getInstance(context).resolveEmergency(_checkInState.value.escalatedIncidentId)
        scope.launch {
            preferences.clearSafetyCheckInEscalation()
        }
    }

    fun updateRemainingTime() {
        val current = _checkInState.value
        if (current.isActive && !current.isPaused) {
            val now = System.currentTimeMillis()
            val remaining = if (current.targetTimeMillis > 0) {
                ((current.targetTimeMillis - now) / 1000).coerceAtLeast(0)
            } else 0L

            val graceRemaining = if (current.isGracePeriodActive) {
                val graceTarget = current.targetTimeMillis + (current.gracePeriodMinutes * 60 * 1000L)
                ((graceTarget - now) / 1000).coerceAtLeast(0)
            } else 0L

            val isExpiredNow = remaining == 0L
            _checkInState.value = current.copy(
                remainingSeconds = remaining,
                isExpired = isExpiredNow,
                graceRemainingSeconds = graceRemaining
            )


        }
    }

    private fun cancelAlarm(action: String, requestCode: Int) {
        val intent = Intent(context, SafetyCheckInReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
=======
    fun startCheckIn(durationMinutes: Int) {
        val targetMillis = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)

        scope.launch {
            preferences.startSafetyCheckIn(durationMinutes, targetMillis)
        }

        val intent = Intent(context, SafetyCheckInReceiver::class.java).apply {
            action = SafetyCheckInReceiver.ACTION_CHECK_IN_EXPIRED
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            101,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager?.let { am ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetMillis, pendingIntent)
                    } else {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetMillis, pendingIntent)
                    }
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetMillis, pendingIntent)
                }
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetMillis, pendingIntent)
            }
        }
    }

    fun markSafeOrCancel() {
        val intent = Intent(context, SafetyCheckInReceiver::class.java).apply {
            action = SafetyCheckInReceiver.ACTION_CHECK_IN_EXPIRED
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            101,
>>>>>>> origin/main
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null && alarmManager != null) {
            alarmManager.cancel(pendingIntent)
        }
<<<<<<< HEAD
=======

        scope.launch {
            preferences.cancelOrCompleteSafetyCheckIn()
        }
    }

    fun updateRemainingTime() {
        val current = _checkInState.value
        if (current.isActive && current.targetTimeMillis > 0) {
            val now = System.currentTimeMillis()
            val remaining = ((current.targetTimeMillis - now) / 1000).coerceAtLeast(0)
            _checkInState.value = current.copy(
                remainingSeconds = remaining,
                isExpired = remaining == 0L
            )
        }
>>>>>>> origin/main
    }
}
