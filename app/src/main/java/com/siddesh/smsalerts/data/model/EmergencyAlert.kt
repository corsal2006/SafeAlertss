package com.siddesh.smsalerts.data.model

data class EmergencyAlert(
    val alertId: String = "",
    val uid: String = "",
    val emergencyType: String = "",
    val message: String = "",
    val recipientIds: List<String> = emptyList(),
    val recipientPhoneNumbers: List<String> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val mapsUrl: String? = null,
    val locationAvailable: Boolean = (latitude != null && longitude != null && !mapsUrl.isNullOrBlank()),
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "SMS Sent",
    val sendMethod: String = "Direct SMS",
    val triggerSource: String = "BUTTON", // BUTTON, MEDICAL, ACCIDENT, FIRE, SHAKE, VOICE, OTHER
    // Compatibility fields
    val id: String = alertId,
    val recipients: List<String> = recipientPhoneNumbers,
    val recipientCount: Int = recipientPhoneNumbers.size,
    val locationIncluded: Boolean = locationAvailable,
    val deliveryMethod: String = sendMethod,
    val timestamp: Long = createdAt,
    val failureReason: String? = null
) {
    fun toMap(): Map<String, Any?> {
        val effectiveId = if (alertId.isNotBlank()) alertId else id
        val effectiveSendMethod = if (sendMethod.isNotBlank()) sendMethod else deliveryMethod
        return mapOf(
            "alertId" to effectiveId,
            "id" to effectiveId,
            "uid" to uid,
            "emergencyType" to emergencyType,
            "triggerSource" to triggerSource,
            "message" to message,
            "recipientIds" to recipientIds,
            "recipientPhoneNumbers" to recipientPhoneNumbers,
            "latitude" to latitude,
            "longitude" to longitude,
            "mapsUrl" to mapsUrl,
            "locationUrl" to mapsUrl,
            "locationAvailable" to locationAvailable,
            "locationIncluded" to locationAvailable,
            "createdAt" to createdAt,
            "timestamp" to createdAt,
            "status" to status,
            "sendMethod" to effectiveSendMethod,
            "deliveryMethod" to effectiveSendMethod,
            "recipients" to (recipientPhoneNumbers.ifEmpty { recipients }),
            "recipientCount" to (if (recipientPhoneNumbers.isNotEmpty()) recipientPhoneNumbers.size else recipientCount),
            "failureReason" to failureReason
        )
    }
}
