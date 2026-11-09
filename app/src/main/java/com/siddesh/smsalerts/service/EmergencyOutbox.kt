package com.siddesh.smsalerts.service

import android.content.Context
import com.siddesh.smsalerts.utils.PrivacyLog as Log
import com.siddesh.smsalerts.data.model.EmergencyIncident
import org.json.JSONArray
import java.io.File

private const val TAG = "SafeAlerts/Outbox"
private const val OUTBOX_FILE = "safealerts_emergency_outbox.json"

/**
 * Thread-safe durable Outbox for pending and active emergency incidents.
 * Guarantees that incidents are persisted before external dispatch begins,
 * surviving process death and device restarts.
 */
class EmergencyOutbox(private val context: Context) {

    private val file = File(context.filesDir, OUTBOX_FILE)
    companion object {
        private val lock = Any()
        internal fun retainRecentHistory(list: List<EmergencyIncident>): List<EmergencyIncident> {
            val (ended, active) = list.partition { it.state in setOf(EmergencyIncident.STATE_RESOLVED, EmergencyIncident.STATE_CANCELLED) }
            return active + ended.sortedBy { it.resolvedTimestamp ?: it.creationTimestamp }.takeLast(50)
        }
    }
    private val atomicFile = android.util.AtomicFile(file)

    fun saveIncident(incident: EmergencyIncident) {
        synchronized(lock) {
            val list = loadAllInternal().toMutableList()
            val existingIndex = list.indexOfFirst { it.incidentId == incident.incidentId }
            if (existingIndex >= 0) {
                if (list[existingIndex].state in setOf(EmergencyIncident.STATE_RESOLVED, EmergencyIncident.STATE_CANCELLED)) return
                list[existingIndex] = incident
            } else {
                list.add(incident)
            }
            writeListInternal(list)
            Log.d(TAG, "Persisted incident ${incident.incidentId} (state=${incident.state}) to disk")
        }
    }

    fun getIncident(incidentId: String): EmergencyIncident? {
        synchronized(lock) {
            return loadAllInternal().find { it.incidentId == incidentId }
        }
    }

    fun getActiveIncidents(): List<EmergencyIncident> {
        synchronized(lock) {
            return loadAllInternal().filter {
                it.state != EmergencyIncident.STATE_RESOLVED &&
                it.state != EmergencyIncident.STATE_CANCELLED
            }
        }
    }

    fun getAllIncidents(): List<EmergencyIncident> {
        synchronized(lock) {
            return loadAllInternal()
        }
    }

    /** Compare and transition under the same disk lock used by cancellation and recipient claims. */
    fun transition(id: String, expected: String, next: String, eligible: (EmergencyIncident) -> Boolean = { true }): Boolean = synchronized(lock) {
        val list = loadAllInternal().toMutableList()
        val index = list.indexOfFirst { it.incidentId == id }
        if (index < 0 || list[index].state != expected || !eligible(list[index])) return@synchronized false
        list[index] = list[index].copy(state = next,
            resolvedTimestamp = if (next == EmergencyIncident.STATE_CANCELLED) System.currentTimeMillis() else list[index].resolvedTimestamp)
        writeListInternal(list)
        true
    }

    fun updateIncident(id: String, transform: (EmergencyIncident) -> EmergencyIncident) = synchronized(lock) {
        val list = loadAllInternal().toMutableList()
        val index = list.indexOfFirst { it.incidentId == id }
        if (index < 0 || list[index].state in setOf(EmergencyIncident.STATE_CANCELLED, EmergencyIncident.STATE_RESOLVED)) return@synchronized
        list[index] = transform(list[index])
        writeListInternal(list)
    }

    fun updateState(incidentId: String, newState: String, failureReason: String? = null) {
        synchronized(lock) {
            val list = loadAllInternal().toMutableList()
            val idx = list.indexOfFirst { it.incidentId == incidentId }
            if (idx < 0 && newState in setOf(EmergencyIncident.STATE_RESOLVED, EmergencyIncident.STATE_CANCELLED)) {
                list.add(EmergencyIncident(incidentId = incidentId, state = newState, userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()))
                writeListInternal(list)
            }
            if (idx >= 0) {
                val current = list[idx]
                if (current.state in setOf(EmergencyIncident.STATE_RESOLVED, EmergencyIncident.STATE_CANCELLED)) return
                val updated = current.copy(
                    state = newState,
                    failureReason = failureReason ?: current.failureReason,
                    resolvedTimestamp = if (newState == EmergencyIncident.STATE_RESOLVED || newState == EmergencyIncident.STATE_CANCELLED) {
                        System.currentTimeMillis()
                    } else current.resolvedTimestamp
                )
                list[idx] = updated
                writeListInternal(list)
                Log.i(TAG, "Updated incident $incidentId state -> $newState")
            }
        }
    }

    fun updateRecipientStatus(incidentId: String, phone: String, status: String) {
        synchronized(lock) {
            val list = loadAllInternal().toMutableList()
            val idx = list.indexOfFirst { it.incidentId == incidentId }
            if (idx >= 0) {
                val current = list[idx]
                val newStatuses = current.recipientStatuses.toMutableMap()
                newStatuses[phone] = status
                list[idx] = current.copy(recipientStatuses = newStatuses)
                writeListInternal(list)
            }
        }
    }

    fun updateTransportStatus(incidentId: String, transport: String, status: String) {
        synchronized(lock) {
            val list = loadAllInternal().toMutableList()
            val idx = list.indexOfFirst { it.incidentId == incidentId }
            if (idx >= 0) {
                val current = list[idx]
                val newTransports = current.transportStatuses.toMutableMap()
                newTransports[transport] = status
                list[idx] = current.copy(transportStatuses = newTransports)
                writeListInternal(list)
            }
        }
    }

    /** Reserve BEFORE SmsManager. Unknown submissions are never automatically resent. */
    fun reserveRecipient(incidentId: String, phone: String): Boolean = synchronized(lock) {
        val incident = getIncident(incidentId) ?: return@synchronized false
        if (incident.userId.isNotBlank() && incident.userId != com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid) return@synchronized false
        if (incident.state in setOf(EmergencyIncident.STATE_CANCELLED, EmergencyIncident.STATE_RESOLVED) || phone in incident.recipientStatuses) return@synchronized false
        updateRecipientStatus(incidentId, phone, "SUBMITTING_UNKNOWN")
        true
    }

    fun submittedRecipient(incidentId: String, phone: String) = synchronized(lock) {
        if (getIncident(incidentId)?.recipientStatuses?.get(phone) == "SUBMITTING_UNKNOWN") updateRecipientStatus(incidentId, phone, "SUBMITTED")
    }

    fun recordSmsPart(incidentId: String, phone: String, part: Int, total: Int, delivered: Boolean, ok: Boolean) = synchronized(lock) {
        val prefix = if (delivered) "DELIVERY" else "SENT"
        updateTransportStatus(incidentId, "$prefix:$phone:$part", if (ok) "OK" else "FAILED")
        val statuses = getIncident(incidentId)?.transportStatuses.orEmpty()
        val parts = (0 until total).map { statuses["$prefix:$phone:$it"] }
        val previous = getIncident(incidentId)?.recipientStatuses?.get(phone)
        val status = when {
            parts.any { it == "FAILED" } -> if (delivered) "DELIVERY_FAILED" else "SEND_FAILED_OR_PARTIAL"
            parts.all { it == "OK" } -> if (delivered) "DELIVERED" else "SENT_TO_CARRIER"
            else -> "PARTIAL_CALLBACK"
        }
        if (previous != "DELIVERED" && !(previous == "SENT_TO_CARRIER" && status == "PARTIAL_CALLBACK")) updateRecipientStatus(incidentId, phone, status)
        val incident = getIncident(incidentId)
        val allRecipientStatuses = incident?.recipientStatuses.orEmpty().values
        if (allRecipientStatuses.isNotEmpty()) {
            val overallSms = when {
                allRecipientStatuses.all { it == "DELIVERED" } -> "DELIVERED"
                allRecipientStatuses.all { it in setOf("DELIVERED", "SENT_TO_CARRIER") } -> "SENT_TO_CARRIER"
                allRecipientStatuses.any { it in setOf("DELIVERY_FAILED", "SEND_FAILED_OR_PARTIAL", "FAILED", "SECURITY_EXCEPTION") } -> "PARTIAL_FAILURE"
                else -> incident?.transportStatuses?.get("SMS") ?: "SUBMITTED"
            }
            updateTransportStatus(incidentId, "SMS", overallSms)
        }
    }

    fun deleteIncident(incidentId: String): Boolean = synchronized(lock) {
        val list = loadAllInternal().toMutableList()
        val owner = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val removed = list.removeAll { it.userId == owner && it.incidentId == incidentId && it.state in setOf(EmergencyIncident.STATE_RESOLVED, EmergencyIncident.STATE_CANCELLED) }
        if (removed) {
            writeListInternal(list)
        }
        removed
    }

    fun clearHistory(): Unit = synchronized(lock) {
        val owner = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val activeOnly = loadAllInternal().filter { it.userId != owner || it.state !in setOf(EmergencyIncident.STATE_RESOLVED, EmergencyIncident.STATE_CANCELLED) }
        writeListInternal(activeOnly)
    }

    private fun loadAllInternal(): List<EmergencyIncident> {
        if (!file.exists()) return emptyList()
        return try {
            val content = atomicFile.openRead().use { it.readBytes().toString(Charsets.UTF_8) }
            val arr = JSONArray(content)
            val result = mutableListOf<EmergencyIncident>()
            for (i in 0 until arr.length()) {
                val jsonStr = arr.getString(i)
                result.add(EmergencyIncident.fromJson(jsonStr) ?: throw java.io.IOException("Invalid saved incident"))
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read outbox file: ${e.message}")
            throw java.io.IOException("Emergency outbox could not be read", e)
        }
    }

    private fun writeListInternal(list: List<EmergencyIncident>) {
        try {
            val arr = JSONArray()
            // Keep at most 50 recent incidents to prevent unbounded storage
            val boundedList = retainRecentHistory(list)
            for (item in boundedList) {
                arr.put(item.toJson())
            }
            val stream = atomicFile.startWrite()
            try {
                stream.write(arr.toString().toByteArray(Charsets.UTF_8))
                atomicFile.finishWrite(stream)
            } catch (e: Exception) { atomicFile.failWrite(stream); throw e }
        } catch (e: Exception) {
            throw java.io.IOException("Emergency outbox could not be persisted", e)
        }
    }
}
