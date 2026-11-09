package com.siddesh.smsalerts.utils

/** General emergencies never inherit the journey-only helpline or wording. No tracking URL is fabricated. */
object EmergencyMessageFormatter {
    const val DEFAULT_MESSAGE_PREFIX = "I need help."
    fun formatEmergencyMessage(
        userName: String, customMessage: String? = null, emergencyType: String? = null,
        policeNumber: String = "", isPoliceEnabled: Boolean = true,
        womensHelplineNumber: String = "", isWomensHelplineEnabled: Boolean = true,
        latitude: Double? = null, longitude: Double? = null, accuracyMeters: Float? = null,
        incidentTimestampMs: Long = System.currentTimeMillis(), secureLiveTrackingUrl: String? = null,
        age: Int? = null, isJourney: Boolean = false, ambulanceNumber: String = "", isAmbulanceEnabled: Boolean = true,
        locationTimestampMs: Long? = null,
        destination: String? = null,
        expectedArrivalTimeMs: Long? = null,
        locationCached: Boolean = false, locationSource: String? = null
    ): String = buildString {
        if (isJourney) {
            val destText = if (!destination.isNullOrBlank()) " at $destination" else ""
            val timeText = if (expectedArrivalTimeMs != null && expectedArrivalTimeMs > 0) " by ${DateUtils.formatFullDateTime(expectedArrivalTimeMs)}" else ""
            appendLine("SAFEALERTS WOMEN'S SAFETY ALERT: ${if (userName.isNotBlank()) userName else "User"} has not confirmed arrival$destText$timeText.")
            appendLine("Please call me immediately and follow the configured emergency escalation procedure.")
        } else {
            appendLine("SAFEALERTS EMERGENCY: ${if (userName.isNotBlank()) userName else "User"} may need urgent assistance.")
            if (customMessage?.isNotBlank() == true && customMessage != DEFAULT_MESSAGE_PREFIX) {
                appendLine("Message: $customMessage")
            }
            appendLine("Please call me immediately. If I do not answer, contact the configured emergency services.")
        }
        if (userName.isNotBlank()) appendLine("Name: $userName")
        if (age != null && age >= 0) appendLine("Age: $age")
        if (!emergencyType.isNullOrBlank()) appendLine("Category: $emergencyType")
        if (isPoliceEnabled && policeNumber.isNotBlank()) appendLine("Police/emergency: $policeNumber")
        if (isAmbulanceEnabled && ambulanceNumber.isNotBlank()) appendLine("Ambulance: $ambulanceNumber")
        if (isJourney && isWomensHelplineEnabled && womensHelplineNumber.isNotBlank()) appendLine("Women's Safety helpline: $womensHelplineNumber")
        if (latitude != null && longitude != null) {
            if (locationCached) appendLine("Location is a cached last-known fix; check the location time.")
            else if (locationTimestampMs != null && !com.siddesh.smsalerts.data.location.LocationQuality.fresh(locationTimestampMs, System.currentTimeMillis()))
                appendLine("Location fix is stale; it is not a current position.")
            if (!locationSource.isNullOrBlank()) appendLine("Location source: $locationSource")
            appendLine("Latest location: $latitude,$longitude" + (accuracyMeters?.let { " (accuracy ${it.toInt()} m)" } ?: ""))
            if (locationTimestampMs != null && locationTimestampMs > 0) appendLine("Location time: ${DateUtils.formatFullDateTime(locationTimestampMs)}")
            else appendLine("Location time: unavailable")
            appendLine("Map: https://maps.google.com/?q=$latitude,$longitude")
        } else appendLine("Latest location: unavailable")
        if (!secureLiveTrackingUrl.isNullOrBlank()) appendLine("Live tracking: $secureLiveTrackingUrl")
        append("Incident time: ${DateUtils.formatFullDateTime(incidentTimestampMs)}")
    }
}
