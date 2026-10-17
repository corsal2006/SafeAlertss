package com.siddesh.smsalerts.mesh

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

private const val TAG = "SafeAlerts/MeshQueue"

/**
 * Durable store-and-forward queue for SafeAlerts Offline Mesh.
 * Saves unacknowledged SOS packets to internal disk storage.
 * Opportunistically uploads relayed emergency packets to Firebase Firestore when Internet becomes available.
 */
class MeshStoreAndForwardQueue(private val context: Context) {

    private val queueFile = File(context.filesDir, "safealerts_mesh_queue.json")
    private val lock = Any()

    suspend fun enqueue(packet: MeshPacket) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val currentList = loadPacketsInternal().toMutableList()
            // Avoid duplicate message IDs in queue
            if (currentList.none { it.messageId == packet.messageId }) {
                currentList.add(packet)
                savePacketsInternal(currentList)
                Log.d(TAG, "Enqueued packet ${packet.messageId} (queue size: ${currentList.size})")
            }
        }
    }

    suspend fun getPendingPackets(): List<MeshPacket> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val valid = loadPacketsInternal().filter { it.canForward() && !it.isExpired(now) }
            // Automatically prune expired packets
            savePacketsInternal(valid)
            return@withContext valid
        }
    }

    suspend fun removePacket(messageId: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val currentList = loadPacketsInternal().toMutableList()
            if (currentList.removeAll { it.messageId == messageId }) {
                savePacketsInternal(currentList)
                Log.d(TAG, "Removed packet $messageId from queue")
            }
        }
    }

    suspend fun getQueueCount(): Int = withContext(Dispatchers.IO) {
        synchronized(lock) {
            return@withContext loadPacketsInternal().size
        }
    }

    /**
     * Gateway forwarder: If this device has internet access, forward relayed SOS packets to Cloud Firestore.
     */
    suspend fun uploadPendingToCloudGateway() = withContext(Dispatchers.IO) {
        val pending = getPendingPackets()
        if (pending.isEmpty()) return@withContext

        try {
            val firestore = FirebaseFirestore.getInstance()
            for (packet in pending) {
                if (packet.packetType == MeshPacket.TYPE_SOS_ALERT) {
                    val gatewayDoc = mapOf(
                        "emergencyId" to packet.emergencyId,
                        "originMessageId" to packet.messageId,
                        "originDeviceId" to packet.senderDeviceId,
                        "senderName" to packet.senderName,
                        "emergencyType" to packet.emergencyType,
                        "messageText" to packet.messageText,
                        "latitude" to packet.latitude,
                        "longitude" to packet.longitude,
                        "accuracy" to packet.accuracy,
                        "timestamp" to packet.timestamp,
                        "hopCount" to packet.hopCount,
                        "relayedBy" to MeshSecurity.getOrCreatePseudonymousDeviceId(context),
                        "relayedAt" to System.currentTimeMillis(),
                        "status" to "RELAYED_VIA_MESH_GATEWAY"
                    )

                    firestore.collection("mesh_relayed_emergencies")
                        .document(packet.emergencyId)
                        .set(gatewayDoc)
                        .addOnSuccessListener {
                            Log.i(TAG, "GATEWAY_SUCCESS: Relayed emergency ${packet.emergencyId} uploaded to cloud!")
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "Gateway upload failed for ${packet.emergencyId}: ${e.message}")
                        }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gateway forward exception: ${e.message}")
        }
    }

    private fun loadPacketsInternal(): List<MeshPacket> {
        if (!queueFile.exists()) return emptyList()
        return try {
            val content = queueFile.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(content)
            val list = mutableListOf<MeshPacket>()
            for (i in 0 until jsonArray.length()) {
                val itemStr = jsonArray.getString(i)
                MeshPacket.fromJson(itemStr)?.let { list.add(it) }
            }
            list
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read mesh queue file: ${e.message}")
            emptyList()
        }
    }

    private fun savePacketsInternal(packets: List<MeshPacket>) {
        try {
            val jsonArray = JSONArray()
            for (packet in packets) {
                jsonArray.put(packet.toJson())
            }
            queueFile.writeText(jsonArray.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save mesh queue file: ${e.message}")
        }
    }
}
