package com.siddesh.smsalerts.data.repository

import android.content.Context
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.siddesh.smsalerts.data.location.LocationStore
import com.siddesh.smsalerts.service.EmergencyOutbox
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** Invoked only after History's explicit permanent-deletion confirmation. Retry the same ID on error. */
class HistoryDeletionRepository(private val context: Context) {
    suspend fun delete(owner: String, id: String) = withTimeout(30_000) {
        require(owner.isNotBlank() && FirebaseAuth.getInstance().currentUser?.uid == owner)
        require(id.isNotBlank() && '/' !in id)
        val outbox = EmergencyOutbox(context)
        val saved = outbox.getIncident(id)
        require(saved == null || (saved.userId == owner && saved.state in setOf("RESOLVED", "CANCELLED"))) {
            "Only your resolved or cancelled incidents can be deleted."
        }
        val db = FirebaseFirestore.getInstance()
        val marker = db.document("users/$owner/deleted_incidents/$id")
        val summary = db.document("incidents/$id")
        val entry = db.document("users/$owner/alerts/$id")
        val mirror = db.document("users/$owner/emergencyAlerts/$id")
        val current = db.document("users/$owner/live_location/current")
        // Synchronize the already-confirmed local terminal state, including legacy audit entries.
        db.runTransaction { tx ->
            val deleted = tx.get(marker)
            val remote = tx.get(summary)
            val audit = tx.get(entry)
            val legacy = tx.get(mirror)
            if (!deleted.exists() && saved != null) {
                if (remote.exists()) {
                    require(remote.getString("ownerUid") == owner)
                    if (remote.getString("status") == "ACTIVE") tx.update(summary, "status", saved.state, "updatedAt", FieldValue.serverTimestamp())
                }
                if (audit.exists()) tx.update(entry, "status", saved.state)
                if (legacy.exists()) tx.update(mirror, "status", saved.state)
            }
            Unit
        }.await()
        db.runTransaction { tx ->
            val deleted = tx.get(marker)
            val remote = tx.get(summary)
            val audit = tx.get(entry)
            val legacy = tx.get(mirror)
            if (!deleted.exists()) {
                val ended = setOf("RESOLVED", "CANCELLED", "Resolved", "Cancelled")
                require(if (remote.exists()) remote.getString("ownerUid") == owner && remote.getString("status") in ended
                    else listOf(audit, legacy).all { !it.exists() || it.getString("status") in ended }) {
                    "Resolve the incident and confirm synchronization before deleting it."
                }
                tx.set(marker, mapOf("deletedAt" to FieldValue.serverTimestamp()))
            }
            Unit
        }.await()
        WorkManager.getInstance(context).cancelUniqueWork("incident-cloud-$id")
        // The durable marker blocks late writes, including work queued by another device.
        while (true) {
            val page = entry.collection("locations").limit(100).get(Source.SERVER).await()
            if (page.isEmpty) break
            db.batch().apply { page.documents.forEach { delete(it.reference) } }.commit().await()
        }
        db.runTransaction { tx ->
            val docs = listOf(summary, entry, mirror).map { it to tx.get(it) }
            val latest = tx.get(current)
            docs.filter { it.second.exists() }.forEach { tx.delete(it.first) }
            if (latest.getString("incidentId") == id) tx.delete(current)
            else if ((latest.get("sessionIds") as? List<*>)?.contains(id) == true) {
                tx.update(current, "sessionIds", FieldValue.arrayRemove(id), "receivedAt", FieldValue.serverTimestamp())
            }
            Unit
        }.await()
        check(FirebaseAuth.getInstance().currentUser?.uid == owner) { "Account changed. Sign back in and retry local cleanup." }
        LocationStore(context).removeSession(owner, id)
        outbox.deleteIncident(id)
        Unit
    }
}
