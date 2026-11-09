package com.siddesh.smsalerts.data.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.siddesh.smsalerts.service.EmergencyOutbox

/** Explicit private PendingIntents report real carrier send/delivery callbacks, including multipart SMS. */
class SmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val incident = intent.getStringExtra("incident") ?: return
        val phone = intent.getStringExtra("phone") ?: return
        val part = intent.getIntExtra("part", -1)
        val total = intent.getIntExtra("total", 0)
        if (part !in 0 until total || total > 100) return
        try { EmergencyOutbox(context).recordSmsPart(incident, phone, part, total,
            intent.action == "com.siddesh.smsalerts.SMS_DELIVERED", resultCode == Activity.RESULT_OK)
        } catch (_: java.io.IOException) { com.siddesh.smsalerts.utils.PrivacyLog.w("SafeAlerts/SMS", "Could not persist SMS callback; status remains unconfirmed") }
    }
}
