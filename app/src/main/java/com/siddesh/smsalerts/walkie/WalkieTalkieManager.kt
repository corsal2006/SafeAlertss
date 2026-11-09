package com.siddesh.smsalerts.walkie

import android.content.Context
import com.siddesh.smsalerts.utils.PrivacyLog as Log
import com.siddesh.smsalerts.mesh.BleMeshManager
import com.siddesh.smsalerts.mesh.MeshPacket
import com.siddesh.smsalerts.mesh.MeshPacketListener
import com.siddesh.smsalerts.mesh.MeshPeer
import com.siddesh.smsalerts.mesh.MeshSecurity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
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

    private val diskLock = Any()
    private fun namespace(): String = MeshSecurity.sha256Hex(
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty() + ":" +
            MeshSecurity.cloudGroupId(context).ifBlank { MeshSecurity.groupCode(context).orEmpty() }).take(24)
    private var activeNamespace = namespace()
    private var historyLoaded = false
    private val deleted get() = context.getSharedPreferences("chat_deleted_$activeNamespace", 0)
    private val chatFile get() = File(context.filesDir, "chat_${activeNamespace}.enc")
    private val _error = MutableStateFlow("")
    val error = _error.asStateFlow()
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
        refreshSession()
        com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener { refreshSession() }
        scope.launch { bleMeshManager.transferStatus.collect { statuses ->
            synchronized(diskLock) {
                _messages.update { messages -> messages.map { m ->
                    val request = m.deleteRequestId
                    val status = statuses[request ?: m.id]
                    if (m.isOutgoing && status != null) m.copy(deliveryStatus = if (request != null) "Deletion: $status" else status) else m
                } }
                try { saveMessagesToDisk() } catch (e: Exception) { _error.value = "Chat storage unavailable; check device storage." }
            }
        } }
        scope.launch { discoveredPeers.collect { peers ->
            _selectedPeer.value?.let { selected -> _selectedPeer.value = peers.find { it.bluetoothAddress == selected.bluetoothAddress } ?: selected.copy(isConnected = false) }
        } }
    }

    fun isBluetoothSupported(): Boolean = bleMeshManager.isBluetoothSupported()

    fun isBluetoothEnabled(): Boolean = bleMeshManager.isBluetoothEnabled()

    fun startMesh() {
        refreshSession()
        bleMeshManager.startMesh()
    }

    fun stopMesh() {
        bleMeshManager.stopMesh()
    }

    fun selectPeer(peer: MeshPeer?) {
        _selectedPeer.value = peer
        peer?.let { bleMeshManager.connectPeer(it) }
    }

    fun refreshSession() = synchronized(diskLock) {
        historyLoaded = false
        activeNamespace = namespace()
        _messages.value = emptyList()
        _selectedPeer.value = null
        _blockedPeers.value = emptySet()
        val expired = deleted.all.filterValues { (it as? Long ?: 0L) <= System.currentTimeMillis() }.keys
        if (expired.isNotEmpty()) deleted.edit().also { editor -> expired.forEach { editor.remove(it) } }.commit()
        loadMessagesFromDisk()
    }

    fun blockPeer(peerId: String) {
        _blockedPeers.value = _blockedPeers.value + peerId
        Log.i(TAG, "Blocked peer $peerId from sending local messages")
    }

    fun unblockPeer(peerId: String) {
        _blockedPeers.value = _blockedPeers.value - peerId
    }

    fun isPeerBlocked(peerId: String): Boolean = _blockedPeers.value.contains(peerId)

    fun sendTextMessage(text: String, senderName: String = com.siddesh.smsalerts.data.repository.UserRepository.getInstance().userProfile.value?.fullName.orEmpty()): WalkieTextMessage = synchronized(diskLock) {
        check(activeNamespace == namespace()) { "Account or nearby group changed. Reopen nearby chat." }
        require(text.isNotBlank() && text.toByteArray().size <= 1000) { "Enter 1–1000 bytes of text" }
        require(_selectedPeer.value == null || _selectedPeer.value?.isAuthenticated == true) { "Wait for peer authentication" }
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
            messageText = text,
            groupId = MeshSecurity.cloudGroupId(context),
            senderUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        )

        val localMsg = WalkieTextMessage(
            id = msgId,
            senderId = myId,
            senderName = "You",
            recipientId = targetId,
            text = text,
            isOutgoing = true,
            deliveryStatus = "PREPARING",
            pendingPacketJson = packet.toJson()
        )
        _messages.update { (it + localMsg).takeLast(150) }
        saveMessagesToDisk()
        val owner = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        val group = MeshSecurity.cloudGroupId(context)
        if (owner != null && group.isNotBlank() && targetId == MeshPacket.BROADCAST_RECIPIENT) {
            com.siddesh.smsalerts.data.repository.GroupMessageOutbox(context, owner, group)
                .add(com.siddesh.smsalerts.data.repository.QueuedGroupMessage(msgId, text, packet.timestamp))
            com.siddesh.smsalerts.service.GroupSyncWorker.enqueue(context, owner, group)
        }
        val queued = bleMeshManager.broadcastSosPacket(packet)
        if (!queued) {
            _messages.update { list -> list.map { if (it.id == msgId) it.copy(deliveryStatus = "FAILED") else it } }
            saveMessagesToDisk()
        }
        localMsg
    }

    fun resendMessage(messageId: String) {
        val target = _messages.value.find { it.id == messageId } ?: return
        if (!target.isOutgoing) return
        val id = target.deleteRequestId ?: messageId
        val session = activeNamespace
        scope.launch {
            try {
                check(session == namespace()) { "Nearby session changed" }
                if (bleMeshManager.queue.getPendingPackets().any { it.messageId == id }) bleMeshManager.retryPacket(id)
                else target.pendingPacketJson?.let(MeshPacket::fromJson)?.let { packet ->
                    if (packet.expiryTimestamp > System.currentTimeMillis()) bleMeshManager.broadcastSosPacket(packet)
                    else _error.value = "This message expired. Start a new message if it is still needed."
                } ?: run { _error.value = "The original packet is unavailable and cannot be retried." }
            } catch (e: Exception) { _error.value = "Retry could not read the saved packet." }
        }
    }

    fun requestDeleteForBoth(id: String) {
        val message = _messages.value.find { it.id == id && it.isOutgoing && it.recipientId != MeshPacket.BROADCAST_RECIPIENT } ?: return
        if (message.deleteRequestId != null) { resendMessage(id); return }
        val packet = MeshPacket(senderDeviceId = bleMeshManager.myDeviceId, recipientId = message.recipientId,
            packetType = MeshPacket.TYPE_CHAT_DELETE, messageText = id)
        check(deleted.edit().putLong("${message.senderId}:$id", System.currentTimeMillis() + MeshPacket.DEFAULT_TTL_MS).commit())
        _messages.update { list -> list.map { if (it.id == id) it.copy(text = "", deleteRequestId = packet.messageId,
            pendingPacketJson = packet.toJson(), deletedForBoth = true, deliveryStatus = "Deletion: preparing") else it } }
        saveMessagesToDisk()
        val session = activeNamespace
        scope.launch {
            try {
                check(session == namespace()) { "Nearby session changed" }
                bleMeshManager.queue.removePacket(id)
                if (!bleMeshManager.broadcastSosPacket(packet)) _error.value = "Deletion was not queued. Reconfigure the group and retry."
            } catch (e: Exception) { _error.value = "Deletion request could not be persisted." }
        }
    }

    fun deleteLocally(id: String) {
        check(deleted.edit().putLong(id, System.currentTimeMillis() + MeshPacket.DEFAULT_TTL_MS).commit())
        _messages.update { it.filterNot { m -> m.id == id } }
        saveMessagesToDisk()
        val session = activeNamespace
        scope.launch {
            try { if (session == namespace()) bleMeshManager.queue.removePacket(id) }
            catch (e: Exception) { _error.value = "Pending transfer could not be removed from storage." }
        }
    }
    fun clearMessages() { _messages.value.forEach { deleteLocally(it.id) } }

    override fun onPacketReceived(packet: MeshPacket): Boolean = synchronized(diskLock) {
        if (packet.packetType !in setOf(MeshPacket.TYPE_WALKIE_TEXT, MeshPacket.TYPE_CHAT_DELETE, MeshPacket.TYPE_ACK)) return@synchronized true
        if (isPeerBlocked(packet.senderDeviceId)) return@synchronized false
        if (packet.recipientId != MeshPacket.BROADCAST_RECIPIENT && packet.recipientId != bleMeshManager.myDeviceId) return@synchronized false
        if (deleted.contains(packet.messageId) || deleted.contains("${packet.senderDeviceId}:${packet.messageId}")) return@synchronized true
        when (packet.packetType) {
            MeshPacket.TYPE_CHAT_DELETE -> {
                val original = _messages.value.find { it.id == packet.messageText }
                if (original != null && (original.senderId != packet.senderDeviceId || original.isOutgoing ||
                            original.recipientId != bleMeshManager.myDeviceId)) return@synchronized false
                // A sender-scoped tombstone also handles delete-before-message delivery.
                check(deleted.edit().putLong("${packet.senderDeviceId}:${packet.messageText}", packet.expiryTimestamp).commit())
                _messages.update { list -> list.map { if (it.id == packet.messageText && it.senderId == packet.senderDeviceId)
                    it.copy(text = "", deletedForBoth = true, deliveryStatus = "Deleted by sender") else it } }
                saveMessagesToDisk()
            }
            MeshPacket.TYPE_WALKIE_TEXT -> {
                // Check if already received
                if (_messages.value.any { it.id == packet.messageId }) {
                    saveMessagesToDisk()
                    return@synchronized true
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
                _messages.update { (it + msg).takeLast(150) }
                saveMessagesToDisk()

            }
            MeshPacket.TYPE_ACK -> {
                // Update delivery status of outgoing message
                val ackedMsgId = packet.messageText
                _messages.value = _messages.value.map { msg ->
                    if (msg.isOutgoing && msg.id == ackedMsgId && (msg.recipientId == packet.senderDeviceId || msg.recipientId == MeshPacket.BROADCAST_RECIPIENT)) msg.copy(deliveryStatus = "ACKNOWLEDGED") else msg
                }
                saveMessagesToDisk()
            }
        }
        true
    }

    private fun saveMessagesToDisk() {
        synchronized(diskLock) {
            check(historyLoaded) { "Saved history is not available" }
            try {
                val file = chatFile
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
                        put("deleteRequest", m.deleteRequestId)
                        put("pendingPacket", m.pendingPacketJson)
                        put("deletedForBoth", m.deletedForBoth)
                    }
                    jsonArr.put(obj)
                }
                val atomic = android.util.AtomicFile(file)
                val stream = atomic.startWrite()
                try { stream.write(MeshSecurity.encryptLocal(jsonArr.toString().toByteArray(Charsets.UTF_8))); atomic.finishWrite(stream) } catch (e: Exception) { atomic.failWrite(stream); throw e }
            } catch (e: Exception) {
                throw java.io.IOException("Chat could not be persisted", e)
            }
        }
    }

    private fun loadMessagesFromDisk() {
        val session = activeNamespace
        val file = chatFile
        synchronized(diskLock) {
            try {
                if (!file.exists()) { historyLoaded = true; return }
                val jsonArr = JSONArray(MeshSecurity.decryptLocal(android.util.AtomicFile(file).openRead().use { it.readBytes() }).toString(Charsets.UTF_8))
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
                            deliveryStatus = obj.optString("st", "UNKNOWN"),
                            deleteRequestId = obj.optString("deleteRequest").takeIf { it.isNotBlank() && it != "null" },
                            pendingPacketJson = obj.optString("pendingPacket").takeIf { it.isNotBlank() && it != "null" },
                            deletedForBoth = obj.optBoolean("deletedForBoth")
                        )
                    )
                }
                synchronized(diskLock) {
                    if (session == activeNamespace) _messages.update { current -> (current + list.filterNot { deleted.contains(it.id) }).distinctBy { it.id }.takeLast(150) }
                }
                historyLoaded = true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load chat messages: ${e.message}")
                _error.value = "Saved chat could not be read. Check storage; encrypted history cannot be read after its device key is lost."
            }
        }
    }

    fun release() {
        bleMeshManager.removeListener(this)
    }
}
