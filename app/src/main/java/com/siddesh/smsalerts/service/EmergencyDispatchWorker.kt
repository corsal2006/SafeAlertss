package com.siddesh.smsalerts.service

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class EmergencyDispatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        fun enqueue(context: Context, id: String) {
            WorkManager.getInstance(context).enqueueUniqueWork("incident-$id", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<EmergencyDispatchWorker>().setInputData(workDataOf("incident" to id))
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS).build())
        }
    }
    override suspend fun doWork(): Result {
        val id = inputData.getString("incident") ?: return Result.failure()
        val manager = EmergencyManager.getInstance(applicationContext)
        val incident = manager.outbox.getIncident(id) ?: return Result.success()
        return try {
            manager.triggerEmergencySuspending(incident.triggerSource, incident.emergencyType, incident.messageText, id)
            Result.success()
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
    }
}
