package com.siddesh.smsalerts.service

import android.content.Context
import android.util.Log
import com.siddesh.smsalerts.data.model.TrustedCircleMember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import java.util.UUID

private const val TAG = "SafeAlerts/TrustedCircle"
private const val STORAGE_FILE = "safealerts_trusted_circle.json"

/**
 * Controller for Section 9: Trusted Circle & Mutual Live Location Sharing.
 * Provides fine-grained consent, duration bounds, revocation, and location staleness checks.
 */
class TrustedCircleManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: TrustedCircleManager? = null

        fun getInstance(context: Context): TrustedCircleManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TrustedCircleManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _members = MutableStateFlow<List<TrustedCircleMember>>(emptyList())
    val members: StateFlow<List<TrustedCircleMember>> = _members.asStateFlow()

    init {
        loadFromDisk()
    }

    fun addMember(
        name: String,
        phoneNumber: String,
        relationship: String = "Family",
        durationHours: Int = 24,
        shareMyLiveLocation: Boolean = true
    ): TrustedCircleMember {
        val member = TrustedCircleMember(
            id = UUID.randomUUID().toString(),
            name = name,
            phoneNumber = phoneNumber,
            relationship = relationship,
            isSharingLocationWithMe = true,
            amISharingLocationWithThem = shareMyLiveLocation,
            sharingDurationHours = durationHours
        )
        _members.value = _members.value + member
        saveToDisk()
        Log.i(TAG, "Added Trusted Circle member: $name ($phoneNumber)")
        return member
    }

    fun removeMember(memberId: String) {
        _members.value = _members.value.filterNot { it.id == memberId }
        saveToDisk()
        Log.i(TAG, "Removed member $memberId from Trusted Circle")
    }

    fun toggleShareMyLiveLocation(memberId: String, enable: Boolean) {
        _members.value = _members.value.map { m ->
            if (m.id == memberId) m.copy(amISharingLocationWithThem = enable) else m
        }
        saveToDisk()
    }

    fun stopAllSharing() {
        _members.value = _members.value.map { it.copy(amISharingLocationWithThem = false) }
        saveToDisk()
        Log.i(TAG, "Revoked all outgoing location sharing to Trusted Circle")
    }

    fun updateMemberLocation(memberId: String, lat: Double, lng: Double, accuracy: Float) {
        val now = System.currentTimeMillis()
        _members.value = _members.value.map { m ->
            if (m.id == memberId) {
                m.copy(
                    lastLatitude = lat,
                    lastLongitude = lng,
                    lastAccuracyMeters = accuracy,
                    lastLocationTimestamp = now
                )
            } else m
        }
        saveToDisk()
    }

    fun toggleBlockMember(memberId: String) {
        _members.value = _members.value.map { m ->
            if (m.id == memberId) m.copy(isBlocked = !m.isBlocked) else m
        }
        saveToDisk()
    }

    private fun saveToDisk() {
        scope.launch {
            try {
                val file = File(context.filesDir, STORAGE_FILE)
                val arr = JSONArray()
                for (m in _members.value) {
                    arr.put(m.toJson())
                }
                file.writeText(arr.toString(), Charsets.UTF_8)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save trusted circle: ${e.message}")
            }
        }
    }

    private fun loadFromDisk() {
        scope.launch {
            try {
                val file = File(context.filesDir, STORAGE_FILE)
                if (!file.exists()) return@launch
                val arr = JSONArray(file.readText(Charsets.UTF_8))
                val list = mutableListOf<TrustedCircleMember>()
                for (i in 0 until arr.length()) {
                    val str = arr.getString(i)
                    TrustedCircleMember.fromJson(str)?.let { list.add(it) }
                }
                _members.value = list
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load trusted circle: ${e.message}")
            }
        }
    }
}
