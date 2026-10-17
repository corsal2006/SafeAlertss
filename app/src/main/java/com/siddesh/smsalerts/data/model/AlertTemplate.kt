package com.siddesh.smsalerts.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AlertTemplate(
    val emergencyType: EmergencyType,
    val rawTemplate: String
)

object AlertTemplateProvider {

    const val PLACEHOLDER_USER_NAME = "{USER_NAME}"
    const val PLACEHOLDER_LOCATION = "{GOOGLE_MAPS_LOCATION}"
    const val PLACEHOLDER_TIME = "{TIME}"

    val MEDICAL_TEMPLATE = """
🚑 MEDICAL EMERGENCY
I need urgent medical assistance.
Name: $PLACEHOLDER_USER_NAME
Location: $PLACEHOLDER_LOCATION
Time: $PLACEHOLDER_TIME
Please contact me immediately and send medical help if required.
""".trimIndent()

    val ACCIDENT_TEMPLATE = """
🚗 ACCIDENT ALERT
I have been involved in an accident and may need immediate assistance.
Name: $PLACEHOLDER_USER_NAME
Location: $PLACEHOLDER_LOCATION
Time: $PLACEHOLDER_TIME
Please contact me immediately and arrange emergency assistance.
""".trimIndent()

    val FIRE_TEMPLATE = """
🔥 FIRE EMERGENCY
There is a fire emergency and immediate assistance may be required.
Name: $PLACEHOLDER_USER_NAME
Location: $PLACEHOLDER_LOCATION
Time: $PLACEHOLDER_TIME
Please contact emergency services if necessary.
""".trimIndent()

    val CRIME_TEMPLATE = """
🚨 EMERGENCY ALERT
I may be facing a safety/security emergency.
Name: $PLACEHOLDER_USER_NAME
Location: $PLACEHOLDER_LOCATION
Time: $PLACEHOLDER_TIME
Please contact me immediately.
""".trimIndent()

    val PERSONAL_TEMPLATE = """
🆘 PERSONAL EMERGENCY
I need immediate assistance.
Name: $PLACEHOLDER_USER_NAME
Location: $PLACEHOLDER_LOCATION
Time: $PLACEHOLDER_TIME
Please contact me immediately.
""".trimIndent()

    val MISSING_TEMPLATE = """
👤 LOST / MISSING ALERT
I need help locating someone / I am currently lost.
Name: $PLACEHOLDER_USER_NAME
Location: $PLACEHOLDER_LOCATION
Time: $PLACEHOLDER_TIME
Please contact me immediately.
""".trimIndent()

    val DISASTER_TEMPLATE = """
🌊 NATURAL DISASTER ALERT
I may be affected by a natural disaster and require assistance.
Name: $PLACEHOLDER_USER_NAME
Location: $PLACEHOLDER_LOCATION
Time: $PLACEHOLDER_TIME
Please contact me immediately.
""".trimIndent()

    val OTHER_TEMPLATE = """
⚠️ EMERGENCY ALERT
I need urgent assistance.
Name: $PLACEHOLDER_USER_NAME
Location: $PLACEHOLDER_LOCATION
Time: $PLACEHOLDER_TIME
Please contact me immediately.
""".trimIndent()

    fun getTemplateForType(type: EmergencyType): String {
        return when (type) {
            EmergencyType.MEDICAL -> MEDICAL_TEMPLATE
            EmergencyType.ACCIDENT -> ACCIDENT_TEMPLATE
            EmergencyType.FIRE -> FIRE_TEMPLATE
            EmergencyType.CRIME -> CRIME_TEMPLATE
            EmergencyType.PERSONAL -> PERSONAL_TEMPLATE
            EmergencyType.MISSING -> MISSING_TEMPLATE
            EmergencyType.DISASTER -> DISASTER_TEMPLATE
            EmergencyType.OTHER -> OTHER_TEMPLATE
        }
    }

    fun formatMessage(
        type: EmergencyType,
        userName: String,
        mapsUrl: String?,
        timestamp: Long = System.currentTimeMillis(),
        customMessageOverride: String? = null
    ): String {
        val baseTemplate = if (!customMessageOverride.isNullOrBlank()) {
            customMessageOverride
        } else {
            getTemplateForType(type)
        }

        val name = userName.ifBlank { "SafeAlert User" }
        val locationText = if (!mapsUrl.isNullOrBlank()) {
            mapsUrl
        } else {
            "Location unavailable / permission pending"
        }
        val formattedTime = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(timestamp))

        return baseTemplate
            .replace(PLACEHOLDER_USER_NAME, name)
            .replace(PLACEHOLDER_LOCATION, locationText)
            .replace(PLACEHOLDER_TIME, formattedTime)
    }
}
