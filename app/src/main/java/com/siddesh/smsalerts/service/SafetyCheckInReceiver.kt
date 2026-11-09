package com.siddesh.smsalerts.service

<<<<<<< HEAD
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import kotlinx.coroutines.*
import androidx.work.await
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore

class SafetyCheckInReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_CHECK_IN_EXPIRED = "com.siddesh.smsalerts.ACTION_CHECK_IN_EXPIRED"
        const val ACTION_CHECK_IN_FINAL_EXPIRY = "com.siddesh.smsalerts.ACTION_CHECK_IN_FINAL_EXPIRY"
=======
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.siddesh.smsalerts.MainActivity
import com.siddesh.smsalerts.R
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SafetyCheckInReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CHECK_IN_EXPIRED = "com.siddesh.smsalerts.ACTION_CHECK_IN_EXPIRED"
>>>>>>> origin/main
        const val ACTION_IM_SAFE = "com.siddesh.smsalerts.ACTION_IM_SAFE"
        const val CHANNEL_ID = "safety_check_in_channel"
        const val NOTIFICATION_ID = 2001
    }

    override fun onReceive(context: Context, intent: Intent) {
<<<<<<< HEAD
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(8_000L) {
                    val session = intent.getStringExtra(SafetyTimerWork.SESSION).orEmpty()
                    val deadline = intent.getLongExtra(SafetyTimerWork.DEADLINE, 0)
                    if (intent.action == ACTION_IM_SAFE) {
                        UserPreferencesDataStore(context).confirmTimer(false, session, deadline)
                        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
                    } else if (session.isNotBlank()) {
                        SafetyTimerWork.enqueue(context, SafetyTimerWork.CHECK_IN, session, deadline).await()
                    } else {
                        SafetyTimerWork.restore(context)
                    }
                }
            } catch (_: Exception) { com.siddesh.smsalerts.utils.PrivacyLog.w("SafeAlerts/Timer", "Timer enqueue failed; persisted schedule remains available") }
            finally { pending.finish() }
        }
    }
=======
        val prefs = UserPreferencesDataStore(context)

        when (intent.action) {
            ACTION_IM_SAFE -> {
                // User confirmed safety from notification
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_ID)

                CoroutineScope(Dispatchers.IO).launch {
                    prefs.cancelOrCompleteSafetyCheckIn()
                }
            }

            ACTION_CHECK_IN_EXPIRED, Intent.ACTION_BOOT_COMPLETED -> {
                showSafetyCheckInAlertNotification(context)
            }
        }
    }

    private fun showSafetyCheckInAlertNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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

        // Open app intent
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "I'm Safe" action intent
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
            .setSmallIcon(R.drawable.ic_app_logo)
            .setContentTitle("⚠️ Safety Check-In Expired")
            .setContentText("Your scheduled check-in time has elapsed. Please confirm you are safe.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(0, "I'm Safe", safePendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
>>>>>>> origin/main
}
