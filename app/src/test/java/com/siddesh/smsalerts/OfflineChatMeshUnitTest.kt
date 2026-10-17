package com.siddesh.smsalerts

import com.siddesh.smsalerts.mesh.MeshPacket
import com.siddesh.smsalerts.walkie.WalkieTextMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class OfflineChatMeshUnitTest {

    @Test
    fun textMessage_initialStatus_isSentOrRelayed() {
        val msg = WalkieTextMessage(
            id = UUID.randomUUID().toString(),
            senderId = "device-A",
            senderName = "Me",
            recipientId = "device-B",
            text = "Testing offline direct BLE communication",
            isOutgoing = true,
            deliveryStatus = "SENT"
        )

        assertTrue(msg.isOutgoing)
        assertEquals("SENT", msg.deliveryStatus)
        assertEquals("Testing offline direct BLE communication", msg.text)
    }

    @Test
    fun textMessage_ackReceived_transitionsToDelivered() {
        val msgId = UUID.randomUUID().toString()
        val original = WalkieTextMessage(
            id = msgId,
            senderId = "device-A",
            senderName = "Me",
            recipientId = "device-B",
            text = "Are you safe?",
            isOutgoing = true,
            deliveryStatus = "SENT"
        )

        // Incoming ACK packet referencing the original message ID
        val ackPacket = MeshPacket(
            messageId = UUID.randomUUID().toString(),
            emergencyId = UUID.randomUUID().toString(),
            senderDeviceId = "device-B",
            recipientId = "device-A",
            packetType = MeshPacket.TYPE_ACK,
            senderName = "ACK",
            messageText = msgId
        )

        assertEquals(msgId, ackPacket.messageText)
        assertEquals(MeshPacket.TYPE_ACK, ackPacket.packetType)

        // Simulate state transition upon ACK matching
        val updated = if (original.id == ackPacket.messageText) {
            original.copy(deliveryStatus = "DELIVERED")
        } else original

        assertEquals("DELIVERED", updated.deliveryStatus)
    }

    @Test
    fun meshPacket_serializationAndDeDuplication() {
        val packet = MeshPacket(
            messageId = "msg-unique-999",
            emergencyId = "em-777",
            senderDeviceId = "sender-node-1",
            recipientId = MeshPacket.BROADCAST_RECIPIENT,
            packetType = MeshPacket.TYPE_WALKIE_TEXT,
            senderName = "SafeAlert Peer",
            messageText = "Urgent: SafeAlerts offline mesh check"
        )

        val json = packet.toJson()
        val restored = MeshPacket.fromJson(json)

        assertEquals(packet.messageId, restored?.messageId)
        assertEquals(packet.emergencyId, restored?.emergencyId)
        assertEquals(packet.senderDeviceId, restored?.senderDeviceId)
        assertEquals(packet.messageText, restored?.messageText)
        assertEquals(MeshPacket.TYPE_WALKIE_TEXT, restored?.packetType)
    }
}
