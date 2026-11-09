package com.siddesh.smsalerts.mesh

import org.json.JSONObject
import java.util.UUID

/**
 * Standardized, versioned emergency packet envelope for SafeAlerts Offline Bluetooth Mesh.
 * Adheres to Section 5: Store-and-forward SOS protocol specification.
 */
data class MeshPacket(
    val version: Int = 2,
    val messageId: String = UUID.randomUUID().toString(),
    val emergencyId: String = UUID.randomUUID().toString(),
    val senderDeviceId: String,
    val recipientId: String = BROADCAST_RECIPIENT,
    val packetType: Int = TYPE_SOS_ALERT,
    val timestamp: Long = System.currentTimeMillis(),
    val expiryTimestamp: Long = System.currentTimeMillis() + DEFAULT_TTL_MS,
    val hopCount: Int = 0,
    val maxHops: Int = DEFAULT_MAX_HOPS,
    val emergencyType: String = "EMERGENCY",
    val senderName: String = "",
    val messageText: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Float? = null,
    val locationTimestamp: Long? = null,
    val signature: String = "",
    val publicKey: String = "",
    val age: Int? = null,
    val relayProofs: List<String> = emptyList(),
    val groupId: String = "",
    val senderUid: String = ""
) {
    companion object {
        const val BROADCAST_RECIPIENT = "BROADCAST"
        const val DEFAULT_MAX_HOPS = 5
        const val DEFAULT_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours

        // Packet Types
        const val TYPE_SOS_ALERT = 1
        const val TYPE_SOS_ACK = 2
        const val TYPE_ACK = 2
        const val TYPE_HEARTBEAT = 3
        const val TYPE_WALKIE_TEXT = 4
        const val TYPE_WALKIE_VOICE = 5
        const val TYPE_CHAT_DELETE = 6

        fun fromJson(jsonStr: String): MeshPacket? {
            if (jsonStr.toByteArray().size > 16_384) return null
            return try {
                val json = JSONObject(jsonStr)
                MeshPacket(
                    version = json.getInt("v"),
                    messageId = json.getString("mid"),
                    emergencyId = json.getString("eid"),
                    senderDeviceId = json.getString("sid"),
                    recipientId = json.optString("rid", BROADCAST_RECIPIENT),
                    packetType = json.optInt("pt", TYPE_SOS_ALERT),
                    timestamp = json.getLong("ts"),
                    expiryTimestamp = json.getLong("exp"),
                    hopCount = json.optInt("hc", 0),
                    maxHops = json.optInt("mh", DEFAULT_MAX_HOPS),
                    emergencyType = json.optString("et", "EMERGENCY"),
                    senderName = json.optString("sn", ""),
                    messageText = json.optString("msg", ""),
                    latitude = if (json.has("lat")) json.getDouble("lat") else null,
                    longitude = if (json.has("lon")) json.getDouble("lon") else null,
                    accuracy = if (json.has("acc")) json.getDouble("acc").toFloat() else null,
                    locationTimestamp = if (json.has("lts")) json.getLong("lts") else null,
                    signature = json.optString("sig", ""),
                    publicKey = json.optString("pk", ""),
                    groupId = json.optString("gid", ""),
                    senderUid = json.optString("uid", ""),
                    age = if (json.has("age")) json.getInt("age") else null,
                    relayProofs = json.optJSONArray("relays")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toJson(): String {
        val json = JSONObject().apply {
            put("v", version)
            put("mid", messageId)
            put("eid", emergencyId)
            put("sid", senderDeviceId)
            put("rid", recipientId)
            put("pt", packetType)
            put("ts", timestamp)
            put("exp", expiryTimestamp)
            put("hc", hopCount)
            put("mh", maxHops)
            put("et", emergencyType)
            put("sn", senderName)
            put("msg", messageText)
            latitude?.let { put("lat", it) }
            longitude?.let { put("lon", it) }
            accuracy?.let { put("acc", it.toDouble()) }
            locationTimestamp?.let { put("lts", it) }
            put("sig", signature)
            put("pk", publicKey)
            if (groupId.isNotBlank()) { put("gid", groupId); put("uid", senderUid) }
            age?.let { put("age", it) }
            put("relays", org.json.JSONArray(relayProofs))
        }
        return json.toString()
    }

    fun toByteArray(): ByteArray = toJson().toByteArray(Charsets.UTF_8)

    fun isExpired(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        return currentTimeMs >= expiryTimestamp
    }

    fun canForward(): Boolean {
        return hopCount < maxHops && !isExpired()
    }

    fun createForwardedHop(): MeshPacket {
        return copy(hopCount = hopCount + 1)
    }

    /**
     * Builds canonical string representation for cryptographic signing & verification
     */
    fun getSigningPayload(): String {
        val base = listOf(version, messageId, emergencyId, senderDeviceId, recipientId, timestamp, expiryTimestamp, packetType, maxHops, emergencyType, senderName, messageText, latitude, longitude, accuracy, locationTimestamp, publicKey, age)
        return org.json.JSONArray(if (groupId.isBlank()) base else base + listOf(groupId, senderUid)).toString()
    }
}
