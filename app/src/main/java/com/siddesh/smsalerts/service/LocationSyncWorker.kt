package com.siddesh.smsalerts.service

import android.content.Context
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.siddesh.smsalerts.data.location.*
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/** Network retry for the newest durable fix; it never drives the location sampling interval. */
class LocationSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        fun enqueue(context: Context, owner: String) {
            WorkManager.getInstance(context).enqueueUniqueWork("location-sync-$owner", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<LocationSyncWorker>().setInputData(workDataOf("owner" to owner))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        }
    }
    override suspend fun doWork(): Result {
        val owner = inputData.getString("owner") ?: return Result.failure()
        if (FirebaseAuth.getInstance().currentUser?.uid != owner) return Result.success()
        return try {
            val confirmed = LocationPublisher.publish(applicationContext, owner)
            if (confirmed != null) LiveLocationManager.getInstance(applicationContext).cloudOutcome(owner, confirmed,
                "Location confirmed by Firestore. Recipient viewing is not confirmed.")
            Result.success()
        } catch (e: TimeoutCancellationException) {
            LiveLocationManager.getInstance(applicationContext).cloudOutcome(owner, null, "Location saved locally; server timed out. Retry queued.")
            if (runAttemptCount < 6) Result.retry() else Result.failure()
        } catch (e: CancellationException) { throw e
        } catch (e: FirebaseFirestoreException) {
            LiveLocationManager.getInstance(applicationContext).cloudOutcome(owner, null, "Cloud location unconfirmed (${e.code}). Local fixes remain saved.")
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED || runAttemptCount >= 6) Result.failure() else Result.retry()
        } catch (e: Exception) {
            LiveLocationManager.getInstance(applicationContext).cloudOutcome(owner, null, "Saved location could not be synchronized. Check device storage and retry.")
            com.siddesh.smsalerts.utils.PrivacyLog.w("LocationSync", "LOCATION_SYNC_FAILED", e)
            if (runAttemptCount < 6) Result.retry() else Result.failure()
        }
    }
}

object LocationPublisher {
    private val mutex = kotlinx.coroutines.sync.Mutex()
    suspend fun publish(context: Context, owner: String): Long? {
        mutex.lock()
        try {
            if (FirebaseAuth.getInstance().currentUser?.uid != owner) return null
            val prefs = UserPreferencesDataStore(context).preferencesFlow.first()
            val outbox = EmergencyOutbox(context)
            val fixes = LocationStore(context).read(owner).groupBy { it.session }.values.mapNotNull { it.maxByOrNull { fix -> fix.point.timestamp } }
                .filter { fix -> when (fix.session) {
                    "circle" -> TrustedCircleManager.getInstance(context).hasActiveOutgoingSharing()
                    "journey" -> prefs.womensSafetyActive && prefs.womensSafetyShareLiveLocation
                    else -> prefs.locationSharingEnabled && outbox.getIncident(fix.session)?.let { it.userId == owner && it.state !in setOf("RESOLVED", "CANCELLED") } == true
                } }
            val latest = fixes.maxByOrNull { it.point.timestamp } ?: return null
            val db = FirebaseFirestore.getInstance()
            withTimeout(10_000) {
                val current = db.document("users/$owner/live_location/current")
                db.runTransaction { tx ->
                    val previous = tx.get(current)
                    if (latest.point.timestamp > (previous.getLong("timestamp") ?: 0)) tx.set(current, data(latest, fixes.map { it.session }))
                }.await()
                // One rolling minute slot: at most 60 history documents per active incident.
                fixes.filter { it.session !in setOf("circle", "journey") }.forEach { fix ->
                    val parent = db.document("incidents/${fix.session}")
                    val slot = db.collection("users").document(owner).collection("alerts").document(fix.session)
                        .collection("locations").document("minute_${(fix.point.timestamp / 60_000) % 60}")
                    db.runTransaction { tx ->
                        val incident = tx.get(parent)
                        if (incident.getString("ownerUid") == owner && incident.getString("status") == "ACTIVE") {
                            val previous = tx.get(slot)
                            if (fix.point.timestamp / 60_000 > (previous.getLong("timestamp") ?: 0) / 60_000) tx.set(slot, data(fix, listOf(fix.session)))
                        }
                    }.await()
                }
            }
            fixes.filter { it.session !in setOf("circle", "journey") }.forEach { outbox.updateTransportStatus(it.session, "LOCATION_CLOUD", "SERVER_CONFIRMED: ${it.point.timestamp}") }
            return latest.point.timestamp
        } finally { mutex.unlock() }
    }
    private fun data(fix: SavedFix, sessions: List<String>) = mapOf(
        "ownerUid" to fix.owner, "incidentId" to fix.session, "sessionIds" to sessions.take(20),
        "latitude" to fix.point.latitude, "longitude" to fix.point.longitude, "accuracy" to fix.point.accuracy,
        "timestamp" to fix.point.timestamp, "source" to fix.point.source, "cached" to fix.point.isCached,
        "receivedAt" to FieldValue.serverTimestamp())
}
