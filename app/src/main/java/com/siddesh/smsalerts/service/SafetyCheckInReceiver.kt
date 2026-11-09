package com.siddesh.smsalerts.service

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
        const val ACTION_IM_SAFE = "com.siddesh.smsalerts.ACTION_IM_SAFE"
        const val CHANNEL_ID = "safety_check_in_channel"
        const val NOTIFICATION_ID = 2001
    }

    override fun onReceive(context: Context, intent: Intent) {
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
}
