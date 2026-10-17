package com.siddesh.smsalerts.walkie

import android.content.Context
import android.util.Log
import com.siddesh.smsalerts.mesh.BleMeshManager
import com.siddesh.smsalerts.mesh.MeshPacket
import com.siddesh.smsalerts.mesh.MeshPacketListener
import com.siddesh.smsalerts.mesh.MeshPeer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

private const val TAG = "SafeAlerts/OfflineChat"

/**
 * Controller for Section 5: Nearby Offline Text Chat & Store-and-Forward Mesh Relay.
 * Voice recording and audio walkie-talkie features have been removed per specification.
 * Provides authentic peer-to-peer text messaging over Bluetooth Low Energy,
 * duplicate packet suppression, persistent outbox, and application-layer ACK delivery tracking.
 */
class WalkieTalkieManager private constructor(private val context: Context) : MeshPacketListener {

    companion object {
        private const val CHAT_CACHE_FILE = "safealerts_walkie_chat.json"

        @Volatile
        private var INSTANCE: WalkieTalkieManager? = null

        fun getInstance(context: Context): WalkieTalkieManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WalkieTalkieManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bleMeshManager = BleMeshManager.getInstance(context)

    private val _selectedPeer = MutableStateFlow<MeshPeer?>(null)
    val selectedPeer: StateFlow<MeshPeer?> = _selectedPeer.asStateFlow()

    private val _messages = MutableStateFlow<List<WalkieTextMessage>>(emptyList())
    val messages: StateFlow<List<WalkieTextMessage>> = _messages.asStateFlow()

    private val _blockedPeers = MutableStateFlow<Set<String>>(emptySet())
    val blockedPeers: StateFlow<Set<String>> = _blockedPeers.asStateFlow()

    val discoveredPeers: StateFlow<List<MeshPeer>> = bleMeshManager.discoveredPeers
    val isAdvertising: StateFlow<Boolean> = bleMeshManager.isAdvertising
    val isScanning: StateFlow<Boolean> = bleMeshManager.isScanning

    init {
        bleMeshManager.addListener(this)
        loadMessagesFromDisk()
    }

    fun isBluetoothSupported(): Boolean = bleMeshManager.isBluetoothSupported()

    fun isBluetoothEnabled(): Boolean = bleMeshManager.isBluetoothEnabled()

    fun startMesh() {
        bleMeshManager.startMesh()
    }

    fun stopMesh() {
        bleMeshManager.stopMesh()
    }

    fun selectPeer(peer: MeshPeer?) {
        _selectedPeer.value = peer
    }

    fun blockPeer(peerId: String) {
        _blockedPeers.value = _blockedPeers.value + peerId
        Log.i(TAG, "Blocked peer $peerId from sending local messages")
    }

    fun unblockPeer(peerId: String) {
        _blockedPeers.value = _blockedPeers.value - peerId
    }

    fun isPeerBlocked(peerId: String): Boolean = _blockedPeers.value.contains(peerId)

    fun sendTextMessage(text: String, senderName: String = "Me"): WalkieTextMessage {
        if (text.isBlank()) throw IllegalArgumentException("Text cannot be blank")
        val myId = bleMeshManager.myDeviceId
        val targetId = _selectedPeer.value?.deviceId ?: MeshPacket.BROADCAST_RECIPIENT
        val msgId = UUID.randomUUID().toString()

        val packet = MeshPacket(
            messageId = msgId,
            emergencyId = UUID.randomUUID().toString(),
            senderDeviceId = myId,
            recipientId = targetId,
            packetType = MeshPacket.TYPE_WALKIE_TEXT,
            senderName = senderName,
            messageText = text
        )

        bleMeshManager.broadcastSosPacket(packet)

        val localMsg = WalkieTextMessage(
            id = msgId,
            senderId = myId,
            senderName = "You",
            recipientId = targetId,
            text = text,
            isOutgoing = true,
            deliveryStatus = if (targetId == MeshPacket.BROADCAST_RECIPIENT) "SENT" else "RELAYED"
        )
        _messages.value = _messages.value + localMsg
        saveMessagesToDisk()
        return localMsg
    }

    fun resendMessage(messageId: String) {
        val target = _messages.value.find { it.id == messageId } ?: return
        val myId = bleMeshManager.myDeviceId

        val packet = MeshPacket(
            messageId = target.id,
            emergencyId = UUID.randomUUID().toString(),
            senderDeviceId = myId,
            recipientId = target.recipientId,
            packetType = MeshPacket.TYPE_WALKIE_TEXT,
            senderName = target.senderName,
            messageText = target.text
        )

        bleMeshManager.broadcastSosPacket(packet)

        _messages.value = _messages.value.map { msg ->
            if (msg.id == messageId) msg.copy(deliveryStatus = "SENT") else msg
        }
        saveMessagesToDisk()
    }

    fun clearMessages() {
        _messages.value = emptyList()
        scope.launch {
            try {
                val file = File(context.filesDir, CHAT_CACHE_FILE)
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear chat cache: ${e.message}")
            }
        }
    }

    override fun onPacketReceived(packet: MeshPacket) {
        if (isPeerBlocked(packet.senderDeviceId)) {
            Log.d(TAG, "Ignoring packet from blocked peer: ${packet.senderDeviceId}")
            return
        }

        when (packet.packetType) {
            MeshPacket.TYPE_WALKIE_TEXT -> {
                // Check if already received
                if (_messages.value.any { it.id == packet.messageId }) {
                    return
                }

                val msg = WalkieTextMessage(
                    id = packet.messageId,
                    senderId = packet.senderDeviceId,
                    senderName = packet.senderName,
                    recipientId = packet.recipientId,
                    text = packet.messageText,
                    timestamp = packet.timestamp,
                    isOutgoing = false,
                    deliveryStatus = "DELIVERED"
                )
                _messages.value = _messages.value + msg
                saveMessagesToDisk()

                // Send ACK back to sender
                val ackPacket = MeshPacket(
                    messageId = UUID.randomUUID().toString(),
                    emergencyId = packet.emergencyId,
                    senderDeviceId = bleMeshManager.myDeviceId,
                    recipientId = packet.senderDeviceId,
                    packetType = MeshPacket.TYPE_ACK,
                    senderName = "ACK",
                    messageText = packet.messageId
                )
                bleMeshManager.broadcastSosPacket(ackPacket)
            }
            MeshPacket.TYPE_ACK -> {
                // Update delivery status of outgoing message
                val ackedMsgId = packet.messageText
                _messages.value = _messages.value.map { msg ->
                    if (msg.id == ackedMsgId) msg.copy(deliveryStatus = "DELIVERED") else msg
                }
                saveMessagesToDisk()
            }
        }
    }

    private fun saveMessagesToDisk() {
        scope.launch {
            try {
                val file = File(context.filesDir, CHAT_CACHE_FILE)
                val jsonArr = JSONArray()
                // Keep last 150 messages
                val toSave = _messages.value.takeLast(150)
                for (m in toSave) {
                    val obj = JSONObject().apply {
                        put("id", m.id)
                        put("sid", m.senderId)
                        put("sn", m.senderName)
                        put("rid", m.recipientId)
                        put("txt", m.text)
                        put("ts", m.timestamp)
                        put("out", m.isOutgoing)
                        put("st", m.deliveryStatus)
                    }
                    jsonArr.put(obj)
                }
                file.writeText(jsonArr.toString())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist chat messages: ${e.message}")
            }
        }
    }

    private fun loadMessagesFromDisk() {
        scope.launch {
            try {
                val file = File(context.filesDir, CHAT_CACHE_FILE)
                if (!file.exists()) return@launch
                val jsonArr = JSONArray(file.readText())
                val list = mutableListOf<WalkieTextMessage>()
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    list.add(
                        WalkieTextMessage(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            senderId = obj.optString("sid", ""),
                            senderName = obj.optString("sn", ""),
                            recipientId = obj.optString("rid", "BROADCAST"),
                            text = obj.optString("txt", ""),
                            timestamp = obj.optLong("ts", System.currentTimeMillis()),
                            isOutgoing = obj.optBoolean("out", false),
                            deliveryStatus = obj.optString("st", "SENT")
                        )
                    )
                }
                _messages.value = list
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load chat messages: ${e.message}")
            }
        }
    }

    fun release() {
        bleMeshManager.removeListener(this)
    }
}
