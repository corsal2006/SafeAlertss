package com.siddesh.smsalerts.walkie

import java.util.UUID

/**
 * Message models for nearby Walkie-Talkie text and voice communication.
 */
data class WalkieTextMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String,
    val recipientId: String = "BROADCAST",
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isOutgoing: Boolean = false,
    val deliveryStatus: String = "QUEUED",
    val deleteRequestId: String? = null,
    val pendingPacketJson: String? = null,
    val deletedForBoth: Boolean = false
)

enum class WalkieState {
    IDLE,
    READY,
    TRANSMITTING,
    RECEIVING,
    ERROR
}

data class WalkieAudioChunk(
    val senderId: String,
    val sequenceNumber: Int,
    val isLastChunk: Boolean,
    val audioBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as WalkieAudioChunk
        return senderId == other.senderId &&
                sequenceNumber == other.sequenceNumber &&
                isLastChunk == other.isLastChunk &&
                audioBytes.contentEquals(other.audioBytes)
    }

    override fun hashCode(): Int {
        var result = senderId.hashCode()
        result = 31 * result + sequenceNumber
        result = 31 * result + isLastChunk.hashCode()
        result = 31 * result + audioBytes.contentHashCode()
        return result
    }
}
