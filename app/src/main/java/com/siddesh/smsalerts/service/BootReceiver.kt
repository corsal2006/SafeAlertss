package com.siddesh.smsalerts.service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.*

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != "android.intent.action.QUICKBOOT_POWERON") return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { withTimeout(8_000) { EmergencyRecovery.restore(context) } }
            catch (_: Exception) { com.siddesh.smsalerts.utils.PrivacyLog.w("SafeAlerts/Boot", "Timer restoration pending until next app start") }
            finally { pending.finish() }
        }
    }
}
