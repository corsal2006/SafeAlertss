package com.siddesh.smsalerts.data.repository

import android.content.Context
import android.util.Log
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

    fun saveContacts(contacts: List<EmergencyContact>) {
        val ctx = appContext ?: return
        synchronized(lock) {
            try {
                val file = File(ctx.filesDir, CACHE_FILE)
                val arr = JSONArray()
                for (contact in contacts) {
                    arr.put(contact.toJson())
                }
                file.writeText(arr.toString(), Charsets.UTF_8)
                Log.d(TAG, "Cached ${contacts.size} emergency contacts locally")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cache contacts: ${e.message}")
            }
        }
    }

    fun getContacts(): List<EmergencyContact> {
        val ctx = appContext ?: return emptyList()
        synchronized(lock) {
            val file = File(ctx.filesDir, CACHE_FILE)
            if (!file.exists()) return emptyList()
            return try {
                val text = file.readText(Charsets.UTF_8)
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
