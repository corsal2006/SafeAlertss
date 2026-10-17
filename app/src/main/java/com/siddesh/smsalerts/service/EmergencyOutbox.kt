package com.siddesh.smsalerts.service

import android.content.Context
import android.util.Log
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
    private val lock = Any()

    fun saveIncident(incident: EmergencyIncident) {
        synchronized(lock) {
            val list = loadAllInternal().toMutableList()
            val existingIndex = list.indexOfFirst { it.incidentId == incident.incidentId }
            if (existingIndex >= 0) {
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

    fun updateState(incidentId: String, newState: String, failureReason: String? = null) {
        synchronized(lock) {
            val list = loadAllInternal().toMutableList()
            val idx = list.indexOfFirst { it.incidentId == incidentId }
            if (idx >= 0) {
                val current = list[idx]
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

    private fun loadAllInternal(): List<EmergencyIncident> {
        if (!file.exists()) return emptyList()
        return try {
            val content = file.readText(Charsets.UTF_8)
            val arr = JSONArray(content)
            val result = mutableListOf<EmergencyIncident>()
            for (i in 0 until arr.length()) {
                val jsonStr = arr.getString(i)
                EmergencyIncident.fromJson(jsonStr)?.let { result.add(it) }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read outbox file: ${e.message}")
            emptyList()
        }
    }

    private fun writeListInternal(list: List<EmergencyIncident>) {
        try {
            val arr = JSONArray()
            // Keep at most 50 recent incidents to prevent unbounded storage
            val boundedList = list.takeLast(50)
            for (item in boundedList) {
                arr.put(item.toJson())
            }
            file.writeText(arr.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write outbox file: ${e.message}")
        }
    }
}
