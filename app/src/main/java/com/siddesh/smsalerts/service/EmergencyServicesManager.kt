package com.siddesh.smsalerts.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.siddesh.smsalerts.utils.PrivacyLog as Log
import com.siddesh.smsalerts.data.model.UserProfile
import com.siddesh.smsalerts.data.preferences.AppUserPreferences

private const val TAG = "SafeAlerts/Services"

enum class CallDispatchStatus {
    INITIATED,
    DIALER_OPENED,
    CANCELLED,
    FAILED
}

data class EmergencyCallResult(
    val status: CallDispatchStatus,
    val targetNumber: String,
    val targetName: String,
    val message: String
)

/**
 * Manages authorized public emergency services (Police, Ambulance, Fire Brigade) workflows.
 * Implements Section 9: Configurable regional emergency numbers, safe briefing, and dialer actions.
 */
class EmergencyServicesManager(private val context: Context) {

    /**
     * Opens the dialer so the user confirms every emergency call with the phone app.
     */
    fun initiateEmergencyCall(
        serviceName: String,
        phoneNumber: String
    ): EmergencyCallResult {
        val sanitized = phoneNumber.replace(Regex("[^0-9+]"), "").trim()
        if (sanitized.isBlank()) {
            return EmergencyCallResult(
                status = CallDispatchStatus.FAILED,
                targetNumber = phoneNumber,
                targetName = serviceName,
                message = "Invalid emergency telephone number."
            )
        }

        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sanitized")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.i(TAG, "EMERGENCY_DIALER_OPENED")

            return EmergencyCallResult(
                status = CallDispatchStatus.DIALER_OPENED,
                targetNumber = sanitized,
                targetName = serviceName,
                message = "Phone dialer opened with $serviceName ($sanitized). Confirm the call in your phone app."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch call intent: ${e.message}")
            return EmergencyCallResult(
                status = CallDispatchStatus.FAILED,
                targetNumber = sanitized,
                targetName = serviceName,
                message = "Failed to launch phone call: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Safe test action that opens the Android dialer with the number pre-filled.
     * Guaranteed NEVER to dial automatically, ensuring zero accidental emergency dispatches.
     */
    fun launchSafeTestDialer(serviceName: String, phoneNumber: String): EmergencyCallResult {
        val sanitized = phoneNumber.replace(Regex("[^0-9+]"), "").trim()
        if (sanitized.isBlank()) {
            return EmergencyCallResult(
                status = CallDispatchStatus.FAILED,
                targetNumber = phoneNumber,
                targetName = serviceName,
                message = "Invalid telephone number."
            )
        }
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sanitized")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            EmergencyCallResult(
                status = CallDispatchStatus.DIALER_OPENED,
                targetNumber = sanitized,
                targetName = serviceName,
                message = "Dialer opened in safe test mode for $serviceName ($sanitized)."
            )
        } catch (e: Exception) {
            EmergencyCallResult(
                status = CallDispatchStatus.FAILED,
                targetNumber = sanitized,
                targetName = serviceName,
                message = "Failed to launch dialer: ${e.localizedMessage}"
            )
        }
    }

    /**
     * Generates an emergency briefing for first responders containing medical, identity, and location details.
     */
    fun generateEmergencyBriefing(
        userProfile: UserProfile?,
        preferences: AppUserPreferences,
        latitude: Double?,
        longitude: Double?
    ): String {
        val name = userProfile?.fullName?.ifBlank { userProfile.name } ?: ""
        val locationStr = if (latitude != null && longitude != null) {
            "https://maps.google.com/?q=$latitude,$longitude"
        } else "Location unavailable"

        return buildString {
            append("EMERGENCY BRIEFING\n")
            append("Name: $name\n")
            if (preferences.bloodGroup.isNotBlank()) {
                append("Blood Group: ${preferences.bloodGroup}\n")
            }
            if (preferences.allergies.isNotBlank()) {
                append("Allergies: ${preferences.allergies}\n")
            }
            if (preferences.medicalNotes.isNotBlank()) {
                append("Medical Notes: ${preferences.medicalNotes}\n")
            }
            append("Current Location: $locationStr\n")
            append("Time: ${com.siddesh.smsalerts.utils.DateUtils.formatFullDateTime(System.currentTimeMillis())}")
        }
    }
}
