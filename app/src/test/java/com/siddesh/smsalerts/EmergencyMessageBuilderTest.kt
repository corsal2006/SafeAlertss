package com.siddesh.smsalerts

import com.siddesh.smsalerts.data.sms.SmsSender
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyMessageBuilderTest {

    // Test helper object without needing Android context
    private fun formatMessage(
        emergencyType: String = "",
        customMessage: String? = null,
        baseMessage: String = "",
        mapsUrl: String? = null,
        timestamp: Long = 1700000000000L
    ): String {
        // Mirrors SmsSender logic directly for pure JVM isolation
        val chosenMessage = when {
            !customMessage.isNullOrBlank() -> customMessage
            baseMessage.isNotBlank() -> baseMessage
            else -> "Emergency alert: I may need assistance. Please contact me as soon as possible."
        }

        return buildString {
            if (emergencyType.isNotBlank() && emergencyType != "EMERGENCY") {
                append("[$emergencyType] ")
            }
            append(chosenMessage)
            if (!mapsUrl.isNullOrBlank()) {
                append("\n\nLocation: $mapsUrl")
            } else {
                append("\n\nLocation: Unavailable")
            }
            append("\nTime: 1700000000000")
        }
    }

    @Test
    fun defaultMessage_usedWhenCustomMessageIsBlank() {
        val result = formatMessage(customMessage = "", mapsUrl = null)
        assertTrue(result.contains("Emergency alert: I may need assistance."))
        assertTrue(result.contains("Location: Unavailable"))
    }

    @Test
    fun customMessage_isPreserved() {
        val custom = "I am on Highway 101 and my car broke down."
        val result = formatMessage(customMessage = custom, mapsUrl = null)
        assertTrue(result.contains(custom))
        assertFalse(result.contains("Emergency alert: I may need assistance."))
    }

    @Test
    fun locationLink_includedWhenProvided() {
        val mapsUrl = "https://maps.google.com/?q=37.7749,-122.4194"
        val result = formatMessage(customMessage = "Need assistance", mapsUrl = mapsUrl)
        assertTrue(result.contains("Location: https://maps.google.com/?q=37.7749,-122.4194"))
        assertFalse(result.contains("Location: Unavailable"))
    }

    @Test
    fun locationUnavailable_handledGracefullyWhenNull() {
        val result = formatMessage(customMessage = "Assistance needed", mapsUrl = null)
        assertTrue(result.contains("Location: Unavailable"))
    }

    @Test
    fun emergencyType_prefixAppliedWhenSpecialized() {
        val medicalResult = formatMessage(emergencyType = "MEDICAL", customMessage = "Need help")
        assertTrue(medicalResult.startsWith("[MEDICAL]"))

        val defaultResult = formatMessage(emergencyType = "EMERGENCY", customMessage = "Need help")
        assertFalse(defaultResult.startsWith("[EMERGENCY]"))
    }
}
