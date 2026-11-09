package com.siddesh.smsalerts.data.repository

import android.content.Context
import com.siddesh.smsalerts.utils.PrivacyLog as Log
import com.siddesh.smsalerts.data.model.EmergencyContact
import org.json.JSONArray
import java.io.File

private const val TAG = "SafeAlerts/ContactsCache"
private const val CACHE_FILE = "safealerts_contacts_cache.json"

/**
 * Thread-safe local persistent cache for emergency contacts.
 * Guarantees that contacts are immediately accessible offline or when unauthenticated,
 * ensuring emergency SMS dispatch is never blocked by network or auth issues.
 */
object LocalContactsCache {

    private val lock = Any()
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun hasUsedPrimary(): Boolean {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return false
        return appContext?.getSharedPreferences("contacts_migration", 0)?.getBoolean(uid, false) == true
    }
    fun markPrimaryUsed() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        appContext?.getSharedPreferences("contacts_migration", 0)?.edit()?.putBoolean(uid, true)?.apply()
    }
    fun removeContact(id: String) = synchronized(lock) { saveContacts(getContacts().filterNot { it.id == id }) }

    fun saveContacts(contacts: List<EmergencyContact>) {
        val ctx = appContext ?: return
        synchronized(lock) {
            try {
                val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"
                val file = File(ctx.filesDir, "${uid}_$CACHE_FILE")
                val arr = JSONArray()
                for (contact in contacts) {
                    arr.put(contact.toJson())
                }
                val atomic = android.util.AtomicFile(file)
                val stream = atomic.startWrite()
                try { stream.write(arr.toString().toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
                catch (e: Exception) { atomic.failWrite(stream); throw e }

                // Also maintain local_user backup so offline dispatch always succeeds
                if (uid != "local_user") {
                    try {
                        val backupFile = File(ctx.filesDir, "local_user_$CACHE_FILE")
                        val backupAtomic = android.util.AtomicFile(backupFile)
                        val backupStream = backupAtomic.startWrite()
                        backupStream.write(arr.toString().toByteArray(Charsets.UTF_8))
                        backupAtomic.finishWrite(backupStream)
                    } catch (_: Exception) {}
                }
                Log.d(TAG, "Cached ${contacts.size} emergency contacts locally")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cache contacts: ${e.message}")
            }
        }
    }

    fun getContacts(): List<EmergencyContact> {
        val ctx = appContext ?: return emptyList()
        synchronized(lock) {
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "local_user"
            var file = File(ctx.filesDir, "${uid}_$CACHE_FILE")
            if (!file.exists()) {
                val backupFile = File(ctx.filesDir, "local_user_$CACHE_FILE")
                val legacyFile = File(ctx.filesDir, CACHE_FILE)
                file = when {
                    backupFile.exists() -> backupFile
                    legacyFile.exists() -> legacyFile
                    else -> return emptyList()
                }
            }
            return try {
                val text = android.util.AtomicFile(file).openRead().use { it.readBytes().toString(Charsets.UTF_8) }
                val arr = JSONArray(text)
                val list = mutableListOf<EmergencyContact>()
                for (i in 0 until arr.length()) {
                    val jsonStr = arr.getString(i)
                    EmergencyContact.fromJson(jsonStr)?.let { list.add(it) }
                }
                list
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load cached contacts: ${e.message}")
                emptyList()
            }
        }
    }

    fun addOrUpdateContact(contact: EmergencyContact) {
        synchronized(lock) {
            val current = getContacts().toMutableList()
            val existingIndex = current.indexOfFirst { it.id == contact.id || it.phoneNumber == contact.phoneNumber }
            if (existingIndex >= 0) {
                current[existingIndex] = contact
            } else {
                current.add(contact)
            }
            saveContacts(current)
        }
    }
}
