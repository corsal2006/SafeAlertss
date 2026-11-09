package com.siddesh.smsalerts.data.model

import org.json.JSONObject
import java.util.UUID

data class TrustedCircleMember(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phoneNumber: String,
    val relationship: String = "Family",
    val isSharingLocationWithMe: Boolean = false,
    val amISharingLocationWithThem: Boolean = false,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
    val lastAccuracyMeters: Float? = null,
    val lastLocationTimestamp: Long = 0L,
    val sharingDurationHours: Int = 24,
    val isBlocked: Boolean = false,
    val activeIncidentId: String? = null,
    val photoUrl: String? = null
) {
    val isLocationStale: Boolean
        get() {
            if (lastLocationTimestamp == 0L) return true
            val ageMs = System.currentTimeMillis() - lastLocationTimestamp
            return ageMs !in 0..60_000L // Future timestamps are not fresh fixes either.
        }

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("phone", phoneNumber)
        obj.put("rel", relationship)
        obj.put("shareWithMe", isSharingLocationWithMe)
        obj.put("shareWithThem", amISharingLocationWithThem)
        if (lastLatitude != null) obj.put("lat", lastLatitude)
        if (lastLongitude != null) obj.put("lng", lastLongitude)
        if (lastAccuracyMeters != null) obj.put("acc", lastAccuracyMeters.toDouble())
        obj.put("ts", lastLocationTimestamp)
        obj.put("dur", sharingDurationHours)
        obj.put("blocked", isBlocked)
        return obj.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): TrustedCircleMember? {
            return try {
                val obj = JSONObject(jsonStr)
                TrustedCircleMember(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.optString("name", "Unknown"),
                    phoneNumber = obj.optString("phone", ""),
                    relationship = obj.optString("rel", "Family"),
                    isSharingLocationWithMe = obj.optBoolean("shareWithMe", false),
                    amISharingLocationWithThem = obj.optBoolean("shareWithThem", false),
                    lastLatitude = if (obj.has("lat")) obj.optDouble("lat") else null,
                    lastLongitude = if (obj.has("lng")) obj.optDouble("lng") else null,
                    lastAccuracyMeters = if (obj.has("acc")) obj.optDouble("acc").toFloat() else null,
                    lastLocationTimestamp = obj.optLong("ts", 0L),
                    sharingDurationHours = obj.optInt("dur", 24),
                    isBlocked = obj.optBoolean("blocked", false)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class CircleInvitation(
    val id: String = UUID.randomUUID().toString(),
    val senderUid: String = "",
    val senderName: String = "",
    val senderPhone: String = "",
    val status: String = "PENDING", // PENDING, ACCEPTED, REJECTED, REVOKED
    val timestamp: Long = System.currentTimeMillis(),
    val shareMyLiveLocation: Boolean = false
)
