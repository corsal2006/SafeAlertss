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

private const val TAG = "SafeAlerts/WomensSafetyRcvr"

/**
 * Authoritative receiver for Section 8: Women's Safety Journey timeout and escalation.
 * Handles journey expiration, grace period alerting, arrival confirmations, and automatic
 * escalation to EmergencyManager and trusted contacts.
 */
class WomensSafetyReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_JOURNEY_EXPIRED = WomensSafetyManager.ACTION_JOURNEY_EXPIRED
        const val ACTION_JOURNEY_FINAL_EXPIRY = WomensSafetyManager.ACTION_JOURNEY_FINAL_EXPIRY
        const val ACTION_JOURNEY_ARRIVED = WomensSafetyManager.ACTION_JOURNEY_ARRIVED
        const val CHANNEL_ID = "womens_safety_channel"
        const val NOTIFICATION_ID = WomensSafetyManager.NOTIFICATION_ID
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = UserPreferencesDataStore(context)
        Log.i(TAG, "WomensSafetyReceiver received action: ${intent.action}")

        when (intent.action) {
            ACTION_JOURNEY_ARRIVED -> {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(NOTIFICATION_ID)
                cancelAlarm(context, ACTION_JOURNEY_FINAL_EXPIRY, 202)

                CoroutineScope(Dispatchers.IO).launch {
                    prefs.cancelOrCompleteWomensSafetyJourney()
                    Log.i(TAG, "User marked arrived safely via notification action.")
                }
            }

            ACTION_JOURNEY_EXPIRED -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val currentPrefs = prefs.preferencesFlow.first()
                    if (!currentPrefs.womensSafetyActive) {
                        Log.d(TAG, "Journey expired action ignored because journey is inactive.")
                        return@launch
                    }

                    if (currentPrefs.womensSafetyGracePeriodMinutes > 0 && !currentPrefs.womensSafetyGracePeriodActive) {
                        val graceMillis = currentPrefs.womensSafetyGracePeriodMinutes * 60 * 1000L
                        val graceTarget = System.currentTimeMillis() + graceMillis
                        prefs.activateWomensSafetyGracePeriod(graceTarget)

                        showGracePeriodAlertNotification(
                            context = context,
                            destination = currentPrefs.womensSafetyDestination,
                            graceMinutes = currentPrefs.womensSafetyGracePeriodMinutes
                        )
                        scheduleFinalExpiryAlarm(context, graceTarget)
                        Log.i(TAG, "Women's Safety grace period started (${currentPrefs.womensSafetyGracePeriodMinutes} min). Alert scheduled for $graceTarget")
                    } else {
                        triggerEmergencyEscalation(context, prefs)
                    }
                }
            }

            ACTION_JOURNEY_FINAL_EXPIRY -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val currentPrefs = prefs.preferencesFlow.first()
                    if (currentPrefs.womensSafetyActive) {
                        Log.w(TAG, "FINAL WOMEN'S SAFETY GRACE PERIOD EXPIRED! Escalating emergency...")
                        triggerEmergencyEscalation(context, prefs)
                    }
                }
            }

            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val currentPrefs = prefs.preferencesFlow.first()
                    if (currentPrefs.womensSafetyActive) {
                        val now = System.currentTimeMillis()
                        if (currentPrefs.womensSafetyTargetTimestamp in 1..now) {
                            Log.w(TAG, "Women's safety journey expired during reboot. Escalating...")
                            triggerEmergencyEscalation(context, prefs)
                        } else if (currentPrefs.womensSafetyTargetTimestamp > now) {
                            scheduleAlarm(context, currentPrefs.womensSafetyTargetTimestamp, ACTION_JOURNEY_EXPIRED, 201)
                        }
                    }
                }
            }
        }
    }

    private fun triggerEmergencyEscalation(context: Context, prefs: UserPreferencesDataStore) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(NOTIFICATION_ID)

        CoroutineScope(Dispatchers.IO).launch {
            val currentPrefs = prefs.preferencesFlow.first()
            val destination = currentPrefs.womensSafetyDestination.ifBlank { "Destination" }
            prefs.cancelOrCompleteWomensSafetyJourney()

            val emergencyManager = EmergencyManager.getInstance(context)
            emergencyManager.triggerEmergency(
                triggerSource = "WOMENS_SAFETY_TIMEOUT",
                emergencyType = "WOMEN'S SAFETY OVERDUE",
                customMessage = "Women's Safety journey overdue to $destination without arrival confirmation! Please verify my safety immediately.",
                bypassCountdown = true
            )
        }

        showDispatchedNotification(context)
    }

    private fun showGracePeriodAlertNotification(context: Context, destination: String, graceMinutes: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(notificationManager)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            2010,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val arrivedIntent = Intent(context, WomensSafetyReceiver::class.java).apply {
            action = ACTION_JOURNEY_ARRIVED
        }
        val arrivedPendingIntent = PendingIntent.getBroadcast(
            context,
            2011,
            arrivedIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_shield)
            .setContentTitle("⚠️ Women's Safety: Journey Overdue!")
            .setContentText("Expected arrival at '$destination' has elapsed. You have $graceMinutes min to confirm arrival before SOS alert.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Expected arrival at '$destination' has elapsed. You have $graceMinutes minutes to confirm your safety. If unconfirmed, emergency SMS alerts with your live location will be dispatched to your trusted contacts.")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                R.drawable.ic_notification_shield,
                "I'VE ARRIVED SAFELY",
                arrivedPendingIntent
            )
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
            2012,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_shield)
            .setContentTitle("🚨 Emergency Alerts Dispatched")
            .setContentText("Women's safety journey overdue without confirmation. Emergency contacts and mesh broadcast initiated.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun scheduleFinalExpiryAlarm(context: Context, triggerAtMillis: Long) {
        scheduleAlarm(context, triggerAtMillis, ACTION_JOURNEY_FINAL_EXPIRY, 202)
    }

    private fun scheduleAlarm(context: Context, triggerAtMillis: Long, action: String, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
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
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelAlarm(context: Context, action: String, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
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

    private fun createChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Women's Safety Journeys",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts for overdue safe journeys and emergency escalations"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
