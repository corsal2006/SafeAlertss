package com.siddesh.smsalerts.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.work.*
import com.siddesh.smsalerts.data.model.EmergencyIncident
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/** Scheduling only; all incident claims and dispatch remain in EmergencyManager. */
object EmergencyCountdownWork {
    fun boot(context: Context): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    fun remaining(context: Context, incident: EmergencyIncident): Long =
        CountdownPolicy.remaining(incident, System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot(context))

    fun schedule(context: Context, incident: EmergencyIncident) {
        val wait = remaining(context, incident)
        WorkManager.getInstance(context).enqueueUniqueWork("countdown-${incident.incidentId}", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<EmergencyCountdownWorker>()
                .setInputData(workDataOf("id" to incident.incidentId))
                .setInitialDelay(wait, TimeUnit.MILLISECONDS).build())
        val alarm = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, EmergencyCountdownReceiver::class.java)
            .setData(android.net.Uri.parse("safealerts://countdown/${incident.incidentId}"))
            .putExtra("id", incident.incidentId)
        val pending = PendingIntent.getBroadcast(context, 4100, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val at = SystemClock.elapsedRealtime() + wait
        try {
            if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) alarm.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
            else alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
        } catch (_: SecurityException) {
            alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
        }
    }
}

class EmergencyCountdownWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.failure()
        return try {
            if (EmergencyManager.getInstance(applicationContext).expireCountdown(id)) Result.success() else Result.retry()
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { if (runAttemptCount < 5) Result.retry() else Result.failure() }
    }
}

class EmergencyCountdownReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(8_000) { EmergencyManager.getInstance(context).expireCountdown(id) }
            } catch (e: Exception) {
                com.siddesh.smsalerts.utils.PrivacyLog.w("Countdown", "Alarm handoff failed; durable work remains: ${e.javaClass.simpleName}")
            } finally { pending.finish() }
        }
    }
}
