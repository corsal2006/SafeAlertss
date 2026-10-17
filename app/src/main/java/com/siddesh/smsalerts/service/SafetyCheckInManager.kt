package com.siddesh.smsalerts.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "SafeAlerts/CheckInMgr"

data class SafetyCheckInState(
    val isActive: Boolean = false,
    val durationMinutes: Int = 30,
    val targetTimeMillis: Long = 0L,
    val remainingSeconds: Long = 0L,
    val isExpired: Boolean = false,
    val gracePeriodMinutes: Int = 2,
    val isGracePeriodActive: Boolean = false,
    val graceRemainingSeconds: Long = 0L,
    val isPaused: Boolean = false,
    val escalationPolicy: String = "AUTO_EMERGENCY"
)

/**
 * Controller for Section 4: Safety Check-In Timer.
 * Provides user-configured timers with persistent deadlines, grace periods,
 * pause/resume, and authoritative escalation to EmergencyManager on expiration.
 */
class SafetyCheckInManager(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    private val preferences = UserPreferencesDataStore(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _checkInState = MutableStateFlow(SafetyCheckInState())
    val checkInState: StateFlow<SafetyCheckInState> = _checkInState.asStateFlow()

    init {
        scope.launch {
            preferences.preferencesFlow.collect { prefs ->
                val now = System.currentTimeMillis()
                if (prefs.safetyCheckInActive) {
                    val remaining = if (prefs.safetyCheckInPaused) {
                        prefs.safetyCheckInRemainingSecondsOnPause
                    } else if (prefs.safetyCheckInTargetTimestamp > 0) {
                        ((prefs.safetyCheckInTargetTimestamp - now) / 1000).coerceAtLeast(0)
                    } else 0L

                    val graceRemaining = if (prefs.safetyCheckInGracePeriodActive && prefs.safetyCheckInGracePeriodTargetTimestamp > 0) {
                        ((prefs.safetyCheckInGracePeriodTargetTimestamp - now) / 1000).coerceAtLeast(0)
                    } else 0L

                    _checkInState.value = SafetyCheckInState(
                        isActive = true,
                        durationMinutes = prefs.safetyCheckInDurationMinutes,
                        targetTimeMillis = prefs.safetyCheckInTargetTimestamp,
                        remainingSeconds = remaining,
                        isExpired = remaining == 0L,
                        gracePeriodMinutes = prefs.safetyCheckInGracePeriodMinutes,
                        isGracePeriodActive = prefs.safetyCheckInGracePeriodActive,
                        graceRemainingSeconds = graceRemaining,
                        isPaused = prefs.safetyCheckInPaused,
                        escalationPolicy = prefs.safetyCheckInEscalationPolicy
                    )
                } else {
                    _checkInState.value = SafetyCheckInState(
                        isActive = false,
                        durationMinutes = prefs.safetyCheckInDurationMinutes,
                        targetTimeMillis = 0L,
                        remainingSeconds = 0L,
                        isExpired = false,
                        gracePeriodMinutes = prefs.safetyCheckInGracePeriodMinutes,
                        isGracePeriodActive = false,
                        graceRemainingSeconds = 0L,
                        isPaused = false,
                        escalationPolicy = prefs.safetyCheckInEscalationPolicy
                    )
                }
            }
        }
    }

    fun startCheckIn(
        durationMinutes: Int,
        graceMinutes: Int = 2,
        escalationPolicy: String = "AUTO_EMERGENCY"
    ) {
        val targetMillis = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        Log.i(TAG, "Starting Safety Check-In: duration=${durationMinutes}m, grace=${graceMinutes}m, target=$targetMillis")

        scope.launch {
            preferences.startSafetyCheckIn(durationMinutes, targetMillis, graceMinutes, escalationPolicy)
        }

        scheduleAlarm(targetMillis, SafetyCheckInReceiver.ACTION_CHECK_IN_EXPIRED, 101)
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
        }

        scheduleAlarm(newTargetMillis, SafetyCheckInReceiver.ACTION_CHECK_IN_EXPIRED, 101)
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

            _checkInState.value = current.copy(
                remainingSeconds = remaining,
                isExpired = remaining == 0L,
                graceRemainingSeconds = graceRemaining
            )
        }
    }

    private fun scheduleAlarm(triggerMillis: Long, action: String, requestCode: Int) {
        val intent = Intent(context, SafetyCheckInReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager?.let { am ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                    } else {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                    }
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exact alarm permission fallback: ${e.message}")
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        }
    }

    private fun cancelAlarm(action: String, requestCode: Int) {
        val intent = Intent(context, SafetyCheckInReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null && alarmManager != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
