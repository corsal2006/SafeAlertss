package com.siddesh.smsalerts.data.model

<<<<<<< HEAD
import org.json.JSONObject

=======
>>>>>>> origin/main
data class EmergencyContact(
    val id: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val relationship: String = "Emergency Contact",
    val enabled: Boolean = true,
    val isActive: Boolean = enabled,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "name" to name,
            "phoneNumber" to phoneNumber,
            "relationship" to relationship,
            "enabled" to enabled,
            "isActive" to enabled,
            "createdAt" to createdAt,
            "updatedAt" to updatedAt
        )
    }
<<<<<<< HEAD

    fun toJson(): String {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("phoneNumber", phoneNumber)
        json.put("relationship", relationship)
        json.put("enabled", enabled)
        json.put("isActive", isActive)
        json.put("createdAt", createdAt)
        json.put("updatedAt", updatedAt)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): EmergencyContact? {
            return try {
                val json = JSONObject(jsonStr)
                EmergencyContact(
                    id = json.optString("id", ""),
                    name = json.optString("name", ""),
                    phoneNumber = json.optString("phoneNumber", ""),
                    relationship = json.optString("relationship", "Emergency Contact"),
                    enabled = json.optBoolean("enabled", true),
                    isActive = json.optBoolean("isActive", true),
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
                )
            } catch (e: Exception) {
                null
            }
        }
    }
=======
>>>>>>> origin/main
}
