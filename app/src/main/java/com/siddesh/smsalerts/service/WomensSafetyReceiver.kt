package com.siddesh.smsalerts.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import kotlinx.coroutines.*
import androidx.work.await
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore

class WomensSafetyReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_JOURNEY_EXPIRED = WomensSafetyManager.ACTION_JOURNEY_EXPIRED
        const val ACTION_JOURNEY_FINAL_EXPIRY = WomensSafetyManager.ACTION_JOURNEY_FINAL_EXPIRY
        const val ACTION_JOURNEY_ARRIVED = WomensSafetyManager.ACTION_JOURNEY_ARRIVED
        const val CHANNEL_ID = "womens_safety_channel"
        const val NOTIFICATION_ID = WomensSafetyManager.NOTIFICATION_ID
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(8_000L) {
                    val session = intent.getStringExtra(SafetyTimerWork.SESSION).orEmpty()
                    val deadline = intent.getLongExtra(SafetyTimerWork.DEADLINE, 0)
                    if (intent.action == ACTION_JOURNEY_ARRIVED) {
                        UserPreferencesDataStore(context).confirmTimer(true, session, deadline)
                        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
                    } else if (session.isNotBlank()) {
                        SafetyTimerWork.enqueue(context, SafetyTimerWork.JOURNEY, session, deadline).await()
                    } else {
                        SafetyTimerWork.restore(context)
                    }
                }
            } catch (_: Exception) { com.siddesh.smsalerts.utils.PrivacyLog.w("SafeAlerts/Timer", "Timer enqueue failed; persisted schedule remains available") }
            finally { pending.finish() }
        }
    }
}
