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
    val alertHelpline1091: Boolean = true
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
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _journeyState = MutableStateFlow(WomensSafetyJourneyState())
    val journeyState: StateFlow<WomensSafetyJourneyState> = _journeyState.asStateFlow()

    init {
        scope.launch {
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
                        alertHelpline1091 = prefs.womensSafetyAlertHelpline1091
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
                        alertHelpline1091 = prefs.womensSafetyAlertHelpline1091
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
        alertHelpline: Boolean = true
    ) {
        val targetMillis = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        Log.i(TAG, "Starting Women's Safety Journey to '$destination' ($durationMinutes mins, target=$targetMillis)")

        scope.launch {
            preferences.startWomensSafetyJourney(
                destination = destination,
                durationMinutes = durationMinutes,
                targetTimestamp = targetMillis,
                gracePeriodMinutes = gracePeriodMinutes,
                shareLiveLocation = shareLocation,
                alertHelpline1091 = alertHelpline
            )
        }

        scheduleAlarm(targetMillis, ACTION_JOURNEY_EXPIRED, 201)
    }

    fun extendDuration(extraMinutes: Int) {
        val current = _journeyState.value
        if (!current.isActive) return
        val newTarget = System.currentTimeMillis() + ((current.remainingSeconds + (extraMinutes * 60)) * 1000L)
        Log.i(TAG, "Extending Women's Safety Journey by $extraMinutes mins (new target=$newTarget)")

        cancelAlarm(ACTION_JOURNEY_EXPIRED, 201)
        cancelAlarm(ACTION_JOURNEY_FINAL_EXPIRY, 202)

        scope.launch {
            preferences.extendWomensSafetyJourney(newTarget)
        }

        scheduleAlarm(newTarget, ACTION_JOURNEY_EXPIRED, 201)
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

    private fun scheduleAlarm(triggerAtMillis: Long, action: String, requestCode: Int) {
        if (alarmManager == null) return
        val intent = Intent(context, WomensSafetyReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
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
