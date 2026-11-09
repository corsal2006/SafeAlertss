package com.siddesh.smsalerts.service

import android.content.Context
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.siddesh.smsalerts.data.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import java.util.concurrent.TimeUnit

class GroupSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        fun enqueue(context: Context, owner: String, group: String) {
            WorkManager.getInstance(context).enqueueUniqueWork("group-sync-$owner-$group", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<GroupSyncWorker>().setInputData(workDataOf("owner" to owner, "group" to group))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        }
    }
    override suspend fun doWork(): Result {
        val owner = inputData.getString("owner") ?: return Result.failure()
        val group = inputData.getString("group") ?: return Result.failure()
        if (FirebaseAuth.getInstance().currentUser?.uid != owner) return Result.success()
        val outbox = GroupMessageOutbox(applicationContext, owner, group)
        return try {
            val repo = GroupRepository(applicationContext)
            outbox.read().filter { it.status != "CLOUD_CONFIRMED" }.take(50).forEach { message ->
                if (FirebaseAuth.getInstance().currentUser?.uid != owner) return Result.success()
                repo.send(group, message.id, message.text, message.timestamp)
                outbox.status(message.id, "CLOUD_CONFIRMED")
            }
            if (outbox.read().any { it.status != "CLOUD_CONFIRMED" }) Result.retry() else Result.success()
        } catch (e: TimeoutCancellationException) { if (runAttemptCount < 6) Result.retry() else Result.failure()
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            val denied = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
            outbox.read().filter { it.status != "CLOUD_CONFIRMED" }.forEach { outbox.status(it.id,
                if (denied) "CLOUD_REJECTED: membership or rules" else "CLOUD_UNCONFIRMED: retry required") }
            if (!denied && runAttemptCount < 6) Result.retry() else Result.failure()
        }
    }
}
