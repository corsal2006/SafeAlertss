package com.siddesh.smsalerts.mesh

/**
 * Representation of a discovered or connected SafeAlerts mesh peer.
 */
data class MeshPeer(
    val deviceId: String,
    val deviceName: String,
    val bluetoothAddress: String,
    val rssi: Int = -60,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val isConnected: Boolean = false,
    val isAuthenticated: Boolean = false,
    val verificationFingerprint: String = ""
) {
    val signalStrengthDescription: String
        get() = when {
            rssi >= -65 -> "Strong"
            rssi >= -80 -> "Good"
            else -> "Weak"
        }
}
