package com.siddesh.smsalerts.data.repository

import android.content.Context
import android.util.AtomicFile
import com.siddesh.smsalerts.mesh.MeshSecurity
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class QueuedGroupMessage(val id: String, val text: String, val timestamp: Long, val status: String = "SAVED_LOCALLY")
class GroupMessageOutbox(private val context: Context, private val owner: String, private val group: String) {
    companion object { private val lock = Any() }
    private val file get() = AtomicFile(File(context.filesDir, "group_outbox_${MeshSecurity.sha256Hex("$owner:$group").take(24)}.enc"))
    fun read(): List<QueuedGroupMessage> = synchronized(lock) {
        if (!file.baseFile.exists()) return@synchronized emptyList()
        val array = JSONArray(MeshSecurity.decryptLocal(file.openRead().use { it.readBytes() }).toString(Charsets.UTF_8))
        (0 until array.length()).map { array.getJSONObject(it).let { j -> QueuedGroupMessage(j.getString("id"), j.getString("text"), j.getLong("timestamp"), j.getString("status")) } }
    }
    fun add(message: QueuedGroupMessage) = synchronized(lock) {
        val list = read().filterNot { it.id == message.id }.toMutableList()
        while (list.size >= 200) {
            val confirmed = list.indexOfFirst { it.status == "CLOUD_CONFIRMED" }
            check(confirmed >= 0) { "200 messages await cloud sync. Reconnect or remove saved messages before sending more." }
            list.removeAt(confirmed)
        }
        write(list + message)
    }
    fun status(id: String, value: String) = synchronized(lock) { write(read().map { if (it.id == id) it.copy(status = value) else it }) }
    fun deleteLocal(id: String) = synchronized(lock) { write(read().filterNot { it.id == id }) }
    private fun write(messages: List<QueuedGroupMessage>) {
        val bytes = JSONArray(messages.map { JSONObject().put("id", it.id).put("text", it.text).put("timestamp", it.timestamp).put("status", it.status) }).toString().toByteArray()
        val stream = file.startWrite()
        try { stream.write(MeshSecurity.encryptLocal(bytes)); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
}
