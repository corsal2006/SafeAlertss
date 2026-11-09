package com.siddesh.smsalerts.data.location

import android.content.Context
import android.util.AtomicFile
import com.siddesh.smsalerts.mesh.MeshSecurity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SavedFix(val owner: String, val session: String, val point: LocationResult.Success)

/** Encrypted, bounded location journal. One recent fix per session is retried after reconnect. */
class LocationStore(private val context: Context) {
    companion object { private val lock = Any() }
    private fun file(owner: String) = AtomicFile(File(context.filesDir, "locations_${MeshSecurity.sha256Hex(owner).take(24)}.enc"))
    fun read(owner: String): List<SavedFix> = synchronized(lock) {
        val file = file(owner)
        if (!file.baseFile.exists()) return@synchronized emptyList()
        val array = JSONArray(MeshSecurity.decryptLocal(file.openRead().use { it.readBytes() }).toString(Charsets.UTF_8))
        (0 until array.length()).map { i -> array.getJSONObject(i).let { json ->
            SavedFix(owner, json.getString("session"), LocationResult.Success(json.getDouble("lat"), json.getDouble("lon"),
                json.getDouble("accuracy").toFloat(), timestamp = json.getLong("timestamp"),
                source = json.getString("source"), isCached = json.getBoolean("cached")))
        } }
    }
    fun save(owner: String, session: String, point: LocationResult.Success) = synchronized(lock) {
        require(LocationQuality.coordinates(point.latitude, point.longitude))
        require(point.accuracy.isFinite() && point.accuracy >= 0 && LocationQuality.usable(point.timestamp, System.currentTimeMillis()))
        val old = read(owner)
        if (!LocationQuality.newer(point.timestamp, old.filter { it.session == session }.maxOfOrNull { it.point.timestamp } ?: 0)) return@synchronized
        val entries = (old + SavedFix(owner, session, point)).filter { System.currentTimeMillis() - it.point.timestamp <= 24 * 3600_000L }.takeLast(240)
        write(owner, entries)
    }
    fun removeSession(owner: String, session: String) = synchronized(lock) {
        write(owner, read(owner).filterNot { it.session == session })
    }
    private fun write(owner: String, entries: List<SavedFix>) {
        val json = JSONArray(entries.map { fix -> JSONObject().apply {
            put("session", fix.session); put("lat", fix.point.latitude); put("lon", fix.point.longitude)
            put("accuracy", fix.point.accuracy.toDouble()); put("timestamp", fix.point.timestamp)
            put("source", fix.point.source); put("cached", fix.point.isCached)
        } })
        val file = file(owner); val stream = file.startWrite()
        try { stream.write(MeshSecurity.encryptLocal(json.toString().toByteArray())); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    fun delete(owner: String) = synchronized(lock) { file(owner).delete() }
}
