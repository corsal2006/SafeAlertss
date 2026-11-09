package com.siddesh.smsalerts.service

import android.content.Context
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class IncidentSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        fun enqueue(context: Context, id: String) {
            WorkManager.getInstance(context).enqueueUniqueWork("incident-cloud-$id", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<IncidentSyncWorker>().setInputData(workDataOf("id" to id))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.MINUTES).build())
        }
    }
    override suspend fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.failure()
        val outbox = EmergencyOutbox(applicationContext)
        val incident = outbox.getIncident(id) ?: return Result.success()
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.success()
        if (incident.userId != user.uid) return Result.success()
        if (incident.state in setOf("COUNTDOWN", "INCIDENT_CREATED")) return Result.success()
        return try {
            withTimeout(12_000) {
                val ref = FirebaseFirestore.getInstance().collection("incidents").document(id)
                FirebaseFirestore.getInstance().runTransaction { tx ->
                    if (tx.get(FirebaseFirestore.getInstance().document("users/${user.uid}/deleted_incidents/$id")).exists()) return@runTransaction Unit
                    val remote = tx.get(ref)
                    val latest = outbox.getIncident(id) ?: incident
                    val latestState = latest.state.takeIf { it in setOf("RESOLVED", "CANCELLED") } ?: "ACTIVE"
                    if (remote.exists()) {
                        if (remote.getString("status") == "ACTIVE") tx.update(ref, mapOf("status" to latestState, "updatedAt" to FieldValue.serverTimestamp()))
                    } else tx.set(ref, mapOf("ownerUid" to user.uid, "senderName" to latest.senderName.take(100),
                        "senderPhone" to "", "category" to latest.emergencyType,
                        "policeNumber" to latest.policeNumber, "helplineNumber" to latest.helplineNumber,
                        "status" to latestState, "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
                    Unit
                }.await()
            }
            outbox.updateTransportStatus(id, "CLOUD", "SYNCED")
            LocationSyncWorker.enqueue(applicationContext, user.uid)
            Result.success()
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            outbox.updateTransportStatus(id, "CLOUD", if (runAttemptCount < 4) "QUEUED: server timed out"
                else "UNCONFIRMED: cloud retry limit reached")
            if (runAttemptCount < 4) Result.retry() else Result.failure()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) {
            val denied = e is com.google.firebase.firestore.FirebaseFirestoreException &&
                e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
            val quota = e is com.google.firebase.firestore.FirebaseFirestoreException &&
                e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED
            outbox.updateTransportStatus(id, "CLOUD", if (denied) "FAILED: backend rules rejected sync"
                else if (runAttemptCount >= 4) "UNCONFIRMED: cloud retry limit reached"
                else if (quota) "QUEUED: free quota reached; retry is delayed" else "QUEUED: waiting for server")
            if (denied || runAttemptCount >= 4) Result.failure() else Result.retry()
        }
    }
}
