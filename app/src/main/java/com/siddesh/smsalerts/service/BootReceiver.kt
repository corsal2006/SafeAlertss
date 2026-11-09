package com.siddesh.smsalerts.service
<<<<<<< HEAD
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
=======

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "SafeAlerts/BootReceiver"

/**
 * BroadcastReceiver for restoring Emergency Protection service after device restart.
 * ONLY starts the service if the user explicitly enabled shake/emergency protection in settings.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d(TAG, "Device boot completed received. Checking user preferences...")
            val preferencesDataStore = UserPreferencesDataStore(context)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = preferencesDataStore.preferencesFlow.first()
                    if (prefs.shakeToAlertEnabled) {
                        Log.i(TAG, "Emergency protection is enabled. Restoring background shake detection service...")
                        EmergencyProtectionService.startService(context)
                    } else {
                        Log.d(TAG, "Emergency protection is disabled in settings. Skipping service auto-start.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking preferences on boot: ${e.message}", e)
                }
            }
>>>>>>> origin/main
        }
    }
}
