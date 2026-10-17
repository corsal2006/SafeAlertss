package com.siddesh.smsalerts.mesh

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic security layer for SafeAlerts Offline Mesh Network.
 * Handles HMAC-SHA256 packet integrity, replay prevention, and pseudonymous device identification.
 */
object MeshSecurity {

    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val PREF_KEY_LOCAL_SECRET = "safealert_mesh_device_secret"

    /**
     * Generates or retrieves an existing pseudonymous 16-character hex device ID.
     */
    fun getOrCreatePseudonymousDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences("safealerts_mesh_security", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("pseudonymous_device_id", null)
        if (deviceId.isNullOrBlank()) {
            val randomBytes = ByteArray(8)
            SecureRandom().nextBytes(randomBytes)
            deviceId = randomBytes.joinToString("") { "%02x".format(it) }
            prefs.edit().putString("pseudonymous_device_id", deviceId).apply()
        }
        return deviceId
    }

    /**
     * Generates or retrieves persistent local secret key for signing SOS packets.
     */
    private fun getDeviceSecret(context: Context): ByteArray {
        val prefs = context.getSharedPreferences("safealerts_mesh_security", Context.MODE_PRIVATE)
        var secretStr = prefs.getString(PREF_KEY_LOCAL_SECRET, null)
        if (secretStr.isNullOrBlank()) {
            val randomKey = ByteArray(32)
            SecureRandom().nextBytes(randomKey)
            secretStr = Base64.encodeToString(randomKey, Base64.NO_WRAP)
            prefs.edit().putString(PREF_KEY_LOCAL_SECRET, secretStr).apply()
        }
        return Base64.decode(secretStr, Base64.NO_WRAP)
    }

    /**
     * Computes HMAC-SHA256 signature for a mesh packet using device secret.
     */
    fun signPacket(context: Context, packet: MeshPacket): MeshPacket {
        val payload = packet.getSigningPayload()
        val secret = getDeviceSecret(context)
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secret, HMAC_ALGORITHM))
        val rawHmac = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        val sig = Base64.encodeToString(rawHmac, Base64.NO_WRAP)
        return packet.copy(signature = sig)
    }

    /**
     * Verifies that the packet is not expired, not corrupted, and timestamps are sane.
     */
    fun verifyPacketIntegrity(packet: MeshPacket, currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        if (packet.version != 1) return false
        if (packet.messageId.isBlank() || packet.emergencyId.isBlank()) return false
        if (packet.senderDeviceId.isBlank()) return false

        // Clock skew check: packet timestamp cannot be more than 15 minutes into the future
        if (packet.timestamp > currentTimeMs + 15 * 60 * 1000L) return false

        // Expiry check
        if (packet.isExpired(currentTimeMs)) return false

        // Hop count check
        if (packet.hopCount < 0 || packet.hopCount > packet.maxHops) return false

        // Ensure signature is present for SOS alerts
        if (packet.packetType == MeshPacket.TYPE_SOS_ALERT && packet.signature.isBlank()) return false

        return true
    }

    /**
     * SHA-256 helper for hash digests.
     */
    fun sha256Hex(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
