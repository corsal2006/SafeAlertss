package com.siddesh.smsalerts.service

import kotlinx.coroutines.tasks.await
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.siddesh.smsalerts.MainActivity
import com.siddesh.smsalerts.R

class EmergencyMessagingService : FirebaseMessagingService() {
    companion object {
        fun registerCurrentDevice() {
            if (com.google.firebase.FirebaseApp.getInstance().options.projectId?.startsWith("demo-") == true) return
            val user = FirebaseAuth.getInstance().currentUser ?: return
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (FirebaseAuth.getInstance().currentUser?.uid != user.uid) return@addOnSuccessListener
                val id = java.security.MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
                FirebaseFirestore.getInstance().collection("users").document(user.uid).collection("fcm_tokens").document(id)
                    .set(mapOf("token" to token, "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()))
            }
        }
    }
    override fun onNewToken(token: String) { registerCurrentDevice() }
    override fun onMessageReceived(message: RemoteMessage) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (message.data["recipientUid"] != uid) return
        val id = message.data["incidentId"]?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,100}")) } ?: return
        CircleNotificationWorker.enqueue(this, uid, id)
    }
}

/** A service callback must not outlive its process allowance while fetching authorization. */
class CircleNotificationWorker(context: android.content.Context, params: androidx.work.WorkerParameters) : androidx.work.CoroutineWorker(context, params) {
    companion object {
        fun enqueue(context: android.content.Context, uid: String, id: String) {
        if (context.getSharedPreferences("circle_notified", 0).contains("$uid:$id")) return
        val request = androidx.work.OneTimeWorkRequestBuilder<CircleNotificationWorker>()
            .setInputData(androidx.work.workDataOf("incidentId" to id, "recipientUid" to uid))
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
            .apply { if (Build.VERSION.SDK_INT >= 31) setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }.build()
        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork("circle-notification-$uid-$id", androidx.work.ExistingWorkPolicy.KEEP, request)
        }
    }
    override suspend fun doWork(): Result {
        val uid = inputData.getString("recipientUid") ?: return Result.failure()
        val id = inputData.getString("incidentId") ?: return Result.failure()
        if (FirebaseAuth.getInstance().currentUser?.uid != uid) return Result.success()
        val seen = applicationContext.getSharedPreferences("circle_notified", 0)
        if (seen.contains("$uid:$id")) return Result.success()
        return try {
            val doc = kotlinx.coroutines.withTimeout(8_000) {
                FirebaseFirestore.getInstance().collection("incidents").document(id)
                    .get(com.google.firebase.firestore.Source.SERVER).await()
            }
            if (FirebaseAuth.getInstance().currentUser?.uid != uid || doc.getString("status") != "ACTIVE") return Result.success()
            val context = applicationContext
            val nm = context.getSystemService(NotificationManager::class.java)
            if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) return Result.success()
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("circle_emergencies", "Trusted Circle emergencies", NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) })
            val intent = Intent(context, MainActivity::class.java).putExtra("incidentId", id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            nm.notify(id.hashCode(), NotificationCompat.Builder(context, "circle_emergencies")
                .setSmallIcon(R.drawable.ic_notification_shield).setContentTitle("${doc.getString("senderName").orEmpty()}: emergency")
                .setContentText(doc.getString("category").orEmpty()).setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setOnlyAlertOnce(true).setContentIntent(pending).setAutoCancel(true).build())
            seen.edit().putLong("$uid:$id", System.currentTimeMillis()).apply()
            Result.success()
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) Result.success()
            else if (runAttemptCount < 3) Result.retry() else Result.failure()
        } catch (_: SecurityException) { Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
    }
}
