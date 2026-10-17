package com.siddesh.smsalerts.service

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

                    // Forward boot broadcast to SafetyCheckInReceiver and WomensSafetyReceiver for active timer restoration
                    if (prefs.safetyCheckInActive) {
                        val checkInIntent = Intent(context, SafetyCheckInReceiver::class.java).apply {
                            action = Intent.ACTION_BOOT_COMPLETED
                        }
                        context.sendBroadcast(checkInIntent)
                    }

                    if (prefs.womensSafetyActive) {
                        val wsIntent = Intent(context, WomensSafetyReceiver::class.java).apply {
                            action = Intent.ACTION_BOOT_COMPLETED
                        }
                        context.sendBroadcast(wsIntent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking preferences on boot: ${e.message}", e)
                }
            }
        }
    }
}
