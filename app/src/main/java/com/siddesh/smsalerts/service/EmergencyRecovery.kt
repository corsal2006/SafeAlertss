package com.siddesh.smsalerts.service

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

/** Reuses persisted timer sessions and unfinished incidents after a process restart. */
object EmergencyRecovery {
    suspend fun restore(context: Context) {
        SafetyTimerWork.restore(context)
        val owner = FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"
        val incidents = EmergencyOutbox(context).getActiveIncidents()
        incidents.filter { (it.userId == owner || it.userId == "local_user" || it.userId.isBlank()) && it.state == "COUNTDOWN" }
            .forEach { EmergencyCountdownWork.schedule(context, it) }
        incidents
            .filter { (it.userId == owner || it.userId == "local_user" || it.userId.isBlank()) && it.state in setOf("INCIDENT_CREATED", "DISPATCHING") }
            .forEach { EmergencyDispatchWorker.enqueue(context, it.incidentId) }
        if (owner.isNotBlank() && owner != "local_user") LocationSyncWorker.enqueue(context, owner)
    }
}
