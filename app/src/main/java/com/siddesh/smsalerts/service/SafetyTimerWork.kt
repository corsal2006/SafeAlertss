package com.siddesh.smsalerts.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.siddesh.smsalerts.MainActivity
import com.siddesh.smsalerts.R
import com.siddesh.smsalerts.data.preferences.AppUserPreferences
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Scheduling adapter for the existing two timers; EmergencyManager remains the sole dispatcher. */
object SafetyTimerWork {
    const val JOURNEY = "journey"
    const val CHECK_IN = "check_in"
    const val SESSION = "session"
    const val KIND = "kind"
    const val DEADLINE = "deadline"

    suspend fun restore(context: Context) {
        val prefs = UserPreferencesDataStore(context)
        prefs.migrateTimerSessions()
        val p = prefs.preferencesFlow.first()
        schedule(context, CHECK_IN, p)
        schedule(context, JOURNEY, p)
    }

    fun schedule(context: Context, kind: String, p: AppUserPreferences) {
        val journey = kind == JOURNEY
        val active = if (journey) p.womensSafetyActive else p.safetyCheckInActive && !p.safetyCheckInPaused
        val escalated = if (journey) p.womensSafetyEscalated else p.safetyCheckInEscalated
        val session = if (journey) p.journeySessionId else p.checkInSessionId
        val deadline = if (journey) p.womensSafetyTargetTimestamp else p.safetyCheckInTargetTimestamp
        val grace = if (journey) p.womensSafetyGracePeriodMinutes else p.safetyCheckInGracePeriodMinutes
        if (session.isBlank() || (!active && !escalated)) return
        val times = if (escalated) listOf(0L) else listOf(deadline, deadline + grace * 60_000L).distinct()
        times.forEachIndexed { index, time ->
            enqueue(context, kind, session, deadline, time, "scheduled-$index")
            if (active) {
                val intent = receiverIntent(context, kind).putExtra(SESSION, session).putExtra(DEADLINE, deadline)
                val pi = PendingIntent.getBroadcast(context, (if (journey) 201 else 101) + index, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val am = context.getSystemService(AlarmManager::class.java)
                try {
                    if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.coerceAtLeast(System.currentTimeMillis()), pi)
                    } else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.coerceAtLeast(System.currentTimeMillis()), pi)
                } catch (_: SecurityException) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.coerceAtLeast(System.currentTimeMillis()), pi)
                }
            }
        }
    }

    private fun receiverIntent(context: Context, kind: String): Intent =
        if (kind == JOURNEY) Intent(context, WomensSafetyReceiver::class.java).setAction(WomensSafetyReceiver.ACTION_JOURNEY_EXPIRED)
        else Intent(context, SafetyCheckInReceiver::class.java).setAction(SafetyCheckInReceiver.ACTION_CHECK_IN_EXPIRED)

    fun enqueue(context: Context, kind: String, session: String, deadline: Long, at: Long = 0L, source: String = "alarm"): Operation {
        val request = OneTimeWorkRequestBuilder<SafetyTimerWorker>()
            .setInputData(workDataOf(KIND to kind, SESSION to session, DEADLINE to deadline))
            .setInitialDelay((at - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag("safety-timer-$kind")
            .build()
        return WorkManager.getInstance(context).enqueueUniqueWork("timer-$kind-$session-$deadline-$source", ExistingWorkPolicy.KEEP, request)
    }

    fun notifyGrace(context: Context, kind: String, session: String, deadline: Long) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = "safety_timer_status"
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(channel, "Safety timers", NotificationManager.IMPORTANCE_HIGH))
        val journey = kind == JOURNEY
        val safeIntent = receiverIntent(context, kind).setAction(if (journey) WomensSafetyReceiver.ACTION_JOURNEY_ARRIVED else SafetyCheckInReceiver.ACTION_IM_SAFE)
            .putExtra(SESSION, session).putExtra(DEADLINE, deadline)
        val safe = PendingIntent.getBroadcast(context, if (journey) 2011 else 1, safeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            nm.notify(if (journey) WomensSafetyReceiver.NOTIFICATION_ID else SafetyCheckInReceiver.NOTIFICATION_ID,
                NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_notification_shield)
                    .setContentTitle(if (journey) "Journey overdue" else "Safety Check-In overdue")
                    .setContentText("Confirm safety before the grace deadline. Emergency escalation is scheduled.")
                    .setContentIntent(open).addAction(0, if (journey) "I'VE ARRIVED" else "I'M SAFE", safe)
                    .setPriority(NotificationCompat.PRIORITY_HIGH).build())
        } catch (_: SecurityException) { /* Denied notifications must never block escalation. */ }
    }
}

class SafetyTimerWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val kind = inputData.getString(SafetyTimerWork.KIND) ?: return Result.failure()
        val session = inputData.getString(SafetyTimerWork.SESSION) ?: return Result.success()
        val deadline = inputData.getLong(SafetyTimerWork.DEADLINE, 0)
        val prefs = UserPreferencesDataStore(applicationContext)
        val journey = kind == SafetyTimerWork.JOURNEY
        val p = prefs.preferencesFlow.first()
        val owner = if (journey) p.journeyOwnerUid else p.checkInOwnerUid
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (owner.isNotBlank() && currentUid.isNotBlank() && owner != currentUid && owner != "local_user" && currentUid != "local_user") return Result.success()
        val decision = prefs.advanceTimer(journey, session, deadline, System.currentTimeMillis())
        if (decision == "GRACE") SafetyTimerWork.notifyGrace(applicationContext, kind, session, deadline)
        if (decision != "EXPIRED") return Result.success()
        applicationContext.getSystemService(NotificationManager::class.java)
            .cancel(if (journey) WomensSafetyReceiver.NOTIFICATION_ID else SafetyCheckInReceiver.NOTIFICATION_ID)
        return try {
            EmergencyManager.getInstance(applicationContext).triggerEmergencySuspending(
                triggerSource = if (journey) "WOMENS_SAFETY_TIMEOUT" else "TIMER_EXPIRY",
                emergencyType = if (journey) "WOMEN'S SAFETY OVERDUE" else "SAFETY CHECK-IN EXPIRATION",
                customMessage = if (journey) "Journey overdue. Destination: ${p.womensSafetyDestination}. Expected arrival: ${com.siddesh.smsalerts.utils.DateUtils.formatFullDateTime(deadline)}."
                    else "Safety Check-In expired without confirmation.",
                forcedIncidentId = session
            )
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
    }
}
