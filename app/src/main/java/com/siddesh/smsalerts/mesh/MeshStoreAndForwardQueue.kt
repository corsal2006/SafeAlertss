package com.siddesh.smsalerts.mesh

import android.content.Context
import android.util.AtomicFile
import java.io.File
import org.json.JSONArray

/** Bounded encrypted queue. Cloud ingestion of a nearby stranger's location is deliberately unsupported. */
class MeshStoreAndForwardQueue(private val context: Context, private val label: String = "queue") {
    companion object { private val lock = Any() }
    private val file get() = AtomicFile(File(context.filesDir, "mesh_${MeshSecurity.sha256Hex(MeshSecurity.groupCode(context).orEmpty()).take(16)}_${label}.enc"))
    private fun load(includeExpired: Boolean = false): List<MeshPacket> {
        if (!file.baseFile.exists()) return emptyList()
        return try {
            val decryptedBytes = MeshSecurity.decryptLocal(file.openRead().use { it.readBytes() })
            val array = JSONArray(decryptedBytes.toString(Charsets.UTF_8))
            (0 until array.length()).mapNotNull { MeshPacket.fromJson(array.getString(it)) }
                .filter { includeExpired || !it.isExpired() }
        } catch (e: Exception) {
            throw java.io.IOException("Saved nearby queue could not be read; preserving it for recovery", e)
        }
    }
    private fun save(packets: List<MeshPacket>) {
        val bytes = JSONArray(packets.map { it.toJson() }).toString().toByteArray()
        val stream = file.startWrite()
        try {
            stream.write(MeshSecurity.encryptLocal(bytes))
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            com.siddesh.smsalerts.utils.PrivacyLog.e("MeshQueue", "Failed saving queue: ${e.message}")
            throw java.io.IOException("Nearby packet could not be saved", e)
        }
    }
    suspend fun enqueue(packet: MeshPacket): List<MeshPacket> = synchronized(lock) {
        val list = load().filterNot { it.messageId == packet.messageId }.toMutableList()
        val evicted = mutableListOf<MeshPacket>()
        require(JSONArray(listOf(packet.toJson())).toString().toByteArray().size <= 16_384) { "Packet exceeds queue capacity" }
        while (list.size >= 12 || (JSONArray((list + packet).map { it.toJson() }).toString().toByteArray().size > 16_384)) {
            val chat = list.indexOfFirst { it.packetType != MeshPacket.TYPE_SOS_ALERT }
            if (chat >= 0) evicted.add(list.removeAt(chat)) else throw IllegalStateException("Nearby emergency queue full")
        }
        list.add(packet); save(list)
        evicted
    }
    suspend fun expirePackets(): List<MeshPacket> = synchronized(lock) {
        val packets = load(includeExpired = true)
        val expired = packets.filter { it.isExpired() }
        if (expired.isNotEmpty()) save(packets.filterNot { it.isExpired() })
        expired
    }
    suspend fun getPendingPackets(): List<MeshPacket> = synchronized(lock) { load().sortedBy { if (it.packetType == MeshPacket.TYPE_SOS_ALERT) 0 else 1 } }
    suspend fun removePacket(messageId: String) = synchronized(lock) {
        val packets = load()
        // Removing a nonexistent packet must not encrypt an empty queue without a configured key.
        if (packets.any { it.messageId == messageId }) save(packets.filterNot { it.messageId == messageId })
    }
    suspend fun getQueueCount(): Int = synchronized(lock) { load().size }
    suspend fun uploadPendingToCloudGateway() { /* No cloud recipient authorization exists for relayed packets. */ }
}
