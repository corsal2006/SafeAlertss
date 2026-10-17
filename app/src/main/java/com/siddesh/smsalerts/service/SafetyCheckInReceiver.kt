package com.siddesh.smsalerts.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.siddesh.smsalerts.MainActivity
import com.siddesh.smsalerts.R
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "SafeAlerts/CheckInRcvr"

/**
 * Authoritative receiver for Safety Check-In timer expiry and confirmation.
 * Implements Section 4: Required Expiry Behavior.
 *
 * Sequence:
 * 1. Timer expires (ACTION_CHECK_IN_EXPIRED).
 * 2. If grace period > 0, post urgent warning notification with "I'm Safe" action and schedule ACTION_CHECK_IN_FINAL_EXPIRY.
 * 3. If grace period == 0 or ACTION_CHECK_IN_FINAL_EXPIRY triggers without safety confirmation:
 *    Invoke EmergencyManager to create incident, dispatch SMS to personal contacts, and start live tracking.
 */
class SafetyCheckInReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CHECK_IN_EXPIRED = "com.siddesh.smsalerts.ACTION_CHECK_IN_EXPIRED"
        const val ACTION_CHECK_IN_FINAL_EXPIRY = "com.siddesh.smsalerts.ACTION_CHECK_IN_FINAL_EXPIRY"
        const val ACTION_IM_SAFE = "com.siddesh.smsalerts.ACTION_IM_SAFE"
        const val CHANNEL_ID = "safety_check_in_channel"
        const val NOTIFICATION_ID = 2001
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = UserPreferencesDataStore(context)
        Log.i(TAG, "SafetyCheckInReceiver received action: ${intent.action}")

        when (intent.action) {
            ACTION_IM_SAFE -> {
                // User confirmed safety from notification
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(NOTIFICATION_ID)

                // Cancel any pending grace period alarm
                cancelAlarm(context, ACTION_CHECK_IN_FINAL_EXPIRY, 102)

                CoroutineScope(Dispatchers.IO).launch {
                    prefs.cancelOrCompleteSafetyCheckIn()
                    Log.i(TAG, "User safety confirmed via notification action; check-in completed.")
                }
            }

            ACTION_CHECK_IN_EXPIRED -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val currentPrefs = prefs.preferencesFlow.first()
                    if (!currentPrefs.safetyCheckInActive || currentPrefs.safetyCheckInPaused) {
                        Log.d(TAG, "Check-in expired action ignored because timer is inactive or paused.")
                        return@launch
                    }

                    if (currentPrefs.safetyCheckInGracePeriodMinutes > 0 && !currentPrefs.safetyCheckInGracePeriodActive) {
                        // Apply grace period: notify user and schedule final expiry
                        val graceMillis = currentPrefs.safetyCheckInGracePeriodMinutes * 60 * 1000L
                        val graceTarget = System.currentTimeMillis() + graceMillis
                        prefs.activateSafetyCheckInGracePeriod(graceTarget)

                        showGracePeriodAlertNotification(context, currentPrefs.safetyCheckInGracePeriodMinutes)
                        scheduleFinalExpiryAlarm(context, graceTarget)
                        Log.i(TAG, "Grace period started (${currentPrefs.safetyCheckInGracePeriodMinutes} min). Alert scheduled for $graceTarget")
                    } else {
                        // Immediate escalation (grace period == 0 or already expired)
                        triggerEmergencyEscalation(context, prefs)
                    }
                }
            }

            ACTION_CHECK_IN_FINAL_EXPIRY -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val currentPrefs = prefs.preferencesFlow.first()
                    if (currentPrefs.safetyCheckInActive) {
                        Log.w(TAG, "FINAL GRACE PERIOD EXPIRED without user safety confirmation! Escalating emergency...")
                        triggerEmergencyEscalation(context, prefs)
                    }
                }
            }

            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON" -> {
                // Check if an active timer deadline was crossed during shutdown
                CoroutineScope(Dispatchers.IO).launch {
                    val currentPrefs = prefs.preferencesFlow.first()
                    if (currentPrefs.safetyCheckInActive && !currentPrefs.safetyCheckInPaused) {
                        val now = System.currentTimeMillis()
                        if (currentPrefs.safetyCheckInTargetTimestamp in 1..now) {
                            Log.w(TAG, "Check-in expired while device was powered off. Triggering escalation...")
                            triggerEmergencyEscalation(context, prefs)
                        } else if (currentPrefs.safetyCheckInTargetTimestamp > now) {
                            // Reschedule alarm for remaining time
                            scheduleAlarm(context, currentPrefs.safetyCheckInTargetTimestamp, ACTION_CHECK_IN_EXPIRED, 101)
                        }
                    }
                }
            }
        }
    }

    private fun triggerEmergencyEscalation(context: Context, prefs: UserPreferencesDataStore) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(NOTIFICATION_ID)

        // Clear active check-in state
        CoroutineScope(Dispatchers.IO).launch {
            prefs.cancelOrCompleteSafetyCheckIn()
        }

        // Trigger authoritative EmergencyManager
        val emergencyManager = EmergencyManager.getInstance(context)
        emergencyManager.triggerEmergency(
            triggerSource = "TIMER_EXPIRY",
            emergencyType = "SAFETY CHECK-IN EXPIRATION",
            customMessage = "Safety check-in timer expired without confirmation. Please verify my safety immediately.",
            bypassCountdown = true
        )

        showDispatchedNotification(context)
    }

    private fun showGracePeriodAlertNotification(context: Context, graceMinutes: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(notificationManager)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val safeIntent = Intent(context, SafetyCheckInReceiver::class.java).apply {
            action = ACTION_IM_SAFE
        }
        val safePendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            safeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_shield)
            .setContentTitle("⚠️ Safety Check-In Expired")
            .setContentText("Confirm you are safe within $graceMinutes min or emergency alerts will dispatch to contacts!")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Your scheduled check-in time has elapsed. Please confirm you are safe within $graceMinutes minutes, or emergency alerts and your live GPS location will be automatically dispatched to your trusted contacts."))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(R.drawable.ic_notification_shield, "I'M SAFE", safePendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showDispatchedNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(notificationManager)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_shield)
            .setContentTitle("🚨 Emergency Alert Dispatched")
            .setContentText("Check-in timed out without response. Emergency contacts notified.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun createChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Safety Check-In Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alerts when a timed safety check-in has not been confirmed"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun scheduleFinalExpiryAlarm(context: Context, triggerMillis: Long) {
        scheduleAlarm(context, triggerMillis, ACTION_CHECK_IN_FINAL_EXPIRY, 102)
    }

    private fun scheduleAlarm(context: Context, triggerMillis: Long, action: String, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, SafetyCheckInReceiver::class.java).apply {
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
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        } catch (e: Exception) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
    }

    private fun cancelAlarm(context: Context, action: String, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, SafetyCheckInReceiver::class.java).apply {
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
        }
    }
}
