package com.siddesh.smsalerts.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Authoritative Emergency Incident data model.
 * Adheres to Section 3: Central Emergency Orchestration Engine.
 *
 * Durable state transitions:
 * IDLE -> TRIGGERED -> INCIDENT_CREATED -> DISPATCHING -> ACTIVE -> RESOLVED
 * (with explicit CANCELLED and FAILED states).
 */
data class EmergencyIncident(
    val incidentId: String = UUID.randomUUID().toString(),
    val triggerSource: String = "MANUAL_BUTTON", // MANUAL_BUTTON, TIMER_EXPIRY, WOMENS_SAFETY_TIMEOUT, SHAKE, VOICE
    val emergencyType: String = "EMERGENCY",
    val creationTimestamp: Long = System.currentTimeMillis(),
    val userId: String = "",
    val senderName: String = "",
    val senderPhone: String = "",
    val policeNumber: String = "",
    val helplineNumber: String = "",
    val locationTimestamp: Long? = null,
    val locationSource: String = "",
    val locationCached: Boolean = false,
    val countdownDeadlineWall: Long = 0,
    val countdownDeadlineElapsed: Long = 0,
    val countdownBootCount: Int = -1,
    val countdownSeconds: Int = 5,
    val messageText: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Float? = null,
    val mapsUrl: String? = null,
    val recipientPhoneNumbers: List<String> = emptyList(),
    val restrictRecipients: Boolean = false,
    val recipientStatuses: Map<String, String> = emptyMap(), // phone -> "SENT", "DELIVERED", "FAILED", "PENDING"
    val transportStatuses: Map<String, String> = emptyMap(), // "SMS" -> "SENT", "BLE_MESH" -> "BROADCAST", "CLOUD" -> "SAVED"
    val retryCount: Int = 0,
    val state: String = STATE_INCIDENT_CREATED,
    val resolvedTimestamp: Long? = null,
    val failureReason: String? = null
) {
    companion object {
        const val STATE_IDLE = "IDLE"
        const val STATE_TRIGGERED = "TRIGGERED"
        const val STATE_COUNTDOWN = "COUNTDOWN"
        const val STATE_INCIDENT_CREATED = "INCIDENT_CREATED"
        const val STATE_DISPATCHING = "DISPATCHING"
        const val STATE_ACTIVE = "ACTIVE"
        const val STATE_RESOLVED = "RESOLVED"
        const val STATE_CANCELLED = "CANCELLED"
        const val STATE_FAILED = "FAILED"

        fun fromJson(jsonStr: String): EmergencyIncident? {
            return try {
                val json = JSONObject(jsonStr)
                val recipientsList = mutableListOf<String>()
                if (json.has("recipients")) {
                    val arr = json.getJSONArray("recipients")
                    for (i in 0 until arr.length()) {
                        recipientsList.add(arr.getString(i))
                    }
                }

                val recStatuses = mutableMapOf<String, String>()
                if (json.has("recipientStatuses")) {
                    val obj = json.getJSONObject("recipientStatuses")
                    for (key in obj.keys()) {
                        recStatuses[key] = obj.getString(key)
                    }
                }

                val transStatuses = mutableMapOf<String, String>()
                if (json.has("transportStatuses")) {
                    val obj = json.getJSONObject("transportStatuses")
                    for (key in obj.keys()) {
                        transStatuses[key] = obj.getString(key)
                    }
                }

                EmergencyIncident(
                    incidentId = json.getString("incidentId"),
                    triggerSource = json.optString("triggerSource", "MANUAL_BUTTON"),
                    emergencyType = json.optString("emergencyType", "EMERGENCY"),
                    creationTimestamp = json.getLong("creationTimestamp"),
                    userId = json.optString("userId", ""),
                    senderName = json.optString("senderName"),
                    senderPhone = json.optString("senderPhone"),
                    policeNumber = json.optString("policeNumber"),
                    helplineNumber = json.optString("helplineNumber"),
                    locationTimestamp = if (json.has("locationTimestamp")) json.getLong("locationTimestamp") else null,
                    locationSource = json.optString("locationSource"),
                    locationCached = json.optBoolean("locationCached"),
                    countdownDeadlineWall = json.optLong("countdownDeadlineWall"),
                    countdownDeadlineElapsed = json.optLong("countdownDeadlineElapsed"),
                    countdownBootCount = json.optInt("countdownBootCount", -1),
                    countdownSeconds = json.optInt("countdownSeconds", 5),
                    messageText = json.optString("messageText", ""),
                    latitude = if (json.has("latitude")) json.getDouble("latitude") else null,
                    longitude = if (json.has("longitude")) json.getDouble("longitude") else null,
                    accuracy = if (json.has("accuracy")) json.getDouble("accuracy").toFloat() else null,
                    mapsUrl = if (json.has("mapsUrl")) json.getString("mapsUrl") else null,
                    recipientPhoneNumbers = recipientsList,
                    restrictRecipients = json.optBoolean("restrictRecipients", false),
                    recipientStatuses = recStatuses,
                    transportStatuses = transStatuses,
                    retryCount = json.optInt("retryCount", 0),
                    state = json.optString("state", STATE_INCIDENT_CREATED),
                    resolvedTimestamp = if (json.has("resolvedTimestamp")) json.getLong("resolvedTimestamp") else null,
                    failureReason = if (json.has("failureReason")) json.getString("failureReason") else null
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toJson(): String {
        val json = JSONObject().apply {
            put("incidentId", incidentId)
            put("triggerSource", triggerSource)
            put("emergencyType", emergencyType)
            put("creationTimestamp", creationTimestamp)
            put("userId", userId)
            put("senderName", senderName)
            put("senderPhone", senderPhone)
            put("policeNumber", policeNumber)
            put("helplineNumber", helplineNumber)
            locationTimestamp?.let { put("locationTimestamp", it) }
            put("locationSource", locationSource)
            put("locationCached", locationCached)
            put("countdownDeadlineWall", countdownDeadlineWall)
            put("countdownDeadlineElapsed", countdownDeadlineElapsed)
            put("countdownBootCount", countdownBootCount)
            put("countdownSeconds", countdownSeconds)
            put("messageText", messageText)
            latitude?.let { put("latitude", it) }
            longitude?.let { put("longitude", it) }
            accuracy?.let { put("accuracy", it.toDouble()) }
            mapsUrl?.let { put("mapsUrl", it) }
            put("recipients", JSONArray(recipientPhoneNumbers))
            put("restrictRecipients", restrictRecipients)
            put("recipientStatuses", JSONObject(recipientStatuses))
            put("transportStatuses", JSONObject(transportStatuses))
            put("retryCount", retryCount)
            put("state", state)
            resolvedTimestamp?.let { put("resolvedTimestamp", it) }
            failureReason?.let { put("failureReason", it) }
        }
        return json.toString()
    }
}
