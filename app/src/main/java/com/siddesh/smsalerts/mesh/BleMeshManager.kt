package com.siddesh.smsalerts.mesh

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import com.siddesh.smsalerts.utils.PrivacyLog as Log
import com.siddesh.smsalerts.utils.PermissionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "SafeAlerts/BleMesh"

interface MeshPacketListener {
    /** True means the addressed packet was validated and persisted by the application. */
    fun onPacketReceived(packet: MeshPacket): Boolean
}

/**
 * Android Bluetooth Low Energy Mesh Coordinator.
 * Implements Section 5: Automatic peer discovery, GATT server/client communication,
 * and multi-hop store-and-forward relay.
 */
class BleMeshManager private constructor(private val context: Context) {

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000FA10-0000-1000-8000-00805F9B34FB")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FA11-0000-1000-8000-00805F9B34FB")

        @Volatile
        private var INSTANCE: BleMeshManager? = null

        fun getInstance(context: Context): BleMeshManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BleMeshManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val deduplicator = MeshDeduplicator()
    val queue = MeshStoreAndForwardQueue(context)
    private val inbox = MeshStoreAndForwardQueue(context, "inbox")
    private val _receivedSos = MutableStateFlow<List<MeshPacket>>(emptyList())
    val receivedSos = _receivedSos.asStateFlow()
    val myDeviceId: String = MeshSecurity.getOrCreatePseudonymousDeviceId(context)

    private val _discoveredPeers = MutableStateFlow<List<MeshPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<MeshPeer>> = _discoveredPeers.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _transferStatus = MutableStateFlow<Map<String, String>>(emptyMap())
    val transferStatus = _transferStatus.asStateFlow()
    private val _status = MutableStateFlow("Nearby stopped")
    val status = _status.asStateFlow()
    private val transmitMutex = Mutex()
    private val receiveMutex = Mutex()
    private val replay = context.getSharedPreferences("mesh_v2_replay", 0)
    private val attempts = context.getSharedPreferences("mesh_v2_attempts", 0)
    private val lastHandshake = ConcurrentHashMap<String, Long>()
    private val challenges = ConcurrentHashMap<String, Pair<String, Long>>()
    private val receiveRates = ConcurrentHashMap<String, Pair<Long, Int>>()
    private data class Assembly(val total: Int, var next: Int = 0, val bytes: ByteArrayOutputStream = ByteArrayOutputStream(), val start: Long = android.os.SystemClock.elapsedRealtime())
    private val assemblies = ConcurrentHashMap<String, Assembly>()
    private val listeners = mutableListOf<MeshPacketListener>()
    private val peersMap = ConcurrentHashMap<String, MeshPeer>()

    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    init {
        scope.launch {
            while (isActive) {
                delay(30_000)
                pruneStalePeers()
                if (_isScanning.value && MeshSecurity.hasGroup(context)) {
                    try {
                        queue.expirePackets().forEach { event(it.messageId, "EXPIRED: no further transfer") }
                        val pending = queue.getPendingPackets()
                        val ids = pending.map { it.messageId }.toSet()
                        val edit = attempts.edit()
                        attempts.all.keys.filter { key -> ids.none { key.contains(it) } }.forEach { edit.remove(it) }
                        edit.apply()
                        pending.forEach { transmitToKnownPeers(it) }
                        _receivedSos.value = inbox.getPendingPackets()
                    }
                    catch (_: Exception) { _status.value = "Nearby queue unavailable; check group configuration" }
                }
            }
        }
    }
    private fun event(id: String, state: String) {
        _transferStatus.value = (_transferStatus.value + (id to state)).entries.toList().takeLast(250).associate { it.toPair() }
        val audit = context.getSharedPreferences("mesh_v2_audit", 0)
        val entries = audit.getString("events", "").orEmpty().lineSequence().filter { it.isNotBlank() }.toList().takeLast(249)
        audit.edit().putString("events", (entries + "${System.currentTimeMillis()} $id $state").joinToString("\n")).apply()
        try {
            val outbox = com.siddesh.smsalerts.service.EmergencyOutbox(context)
            if (outbox.getIncident(id) != null) outbox.updateTransportStatus(id, "BLE_MESH", state)
        } catch (e: Exception) { Log.w(TAG, "Could not persist BLE incident outcome: ${e.javaClass.simpleName}") }
    }

    fun addListener(listener: MeshPacketListener) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) listeners.add(listener)
        }
    }

    fun removeListener(listener: MeshPacketListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = try { bluetoothAdapter?.isEnabled == true } catch (_: SecurityException) { false }

    @SuppressLint("MissingPermission")
    fun startMesh() {
        if (!MeshSecurity.hasGroup(context)) { _status.value = "Set up a private nearby group on both phones first"; return }
        if (!PermissionManager.hasBluetoothPermissions(context)) {
            Log.w(TAG, "Missing Bluetooth permissions; mesh cannot start")
            _status.value = "Bluetooth permissions required"
            return
        }
        if (!isBluetoothSupported() || !isBluetoothEnabled()) {
            Log.w(TAG, "Bluetooth not supported or disabled; mesh cannot start")
            _status.value = "Bluetooth is unavailable or turned off"
            return
        }

        com.siddesh.smsalerts.walkie.WalkieTalkieManager.getInstance(context)
        scope.launch { try { _receivedSos.value = inbox.getPendingPackets() } catch (_: Exception) { _status.value = "Nearby inbox unavailable" } }
        _status.value = "Discovering compatible peers; transfers require the same private group"
        startGattServer()
        if (gattServer == null) return
        startAdvertising()
        startScanning()
    }

    @SuppressLint("MissingPermission")
    fun stopMesh() {
        stopScanning()
        stopAdvertising()
        stopGattServer()
        assemblies.clear(); challenges.clear(); lastHandshake.clear()
        _receivedSos.value = emptyList()
        _status.value = "Nearby stopped"
        peersMap.clear()
        _discoveredPeers.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    private fun startGattServer() {
        if (gattServer != null) return
        try {
            val callback = object : BluetoothGattServerCallback() {
                override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
                    if (status != BluetoothGatt.GATT_SUCCESS) _status.value = "Nearby service registration failed (Android code $status). Stop Nearby and retry."
                    else Log.i(TAG, "GATT_SERVICE_REGISTERED")
                }
                override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
                    val address = device?.address ?: return
                    peersMap[address]?.let { peersMap[address] = it.copy(isConnected = newState == BluetoothProfile.STATE_CONNECTED) }
                    _discoveredPeers.value = peersMap.values.toList()
                    if (newState == BluetoothProfile.STATE_DISCONNECTED) assemblies.remove(address)
                }

                override fun onCharacteristicWriteRequest(
                    device: BluetoothDevice?,
                    requestId: Int,
                    characteristic: BluetoothGattCharacteristic?,
                    preparedWrite: Boolean,
                    responseNeeded: Boolean,
                    offset: Int,
                    value: ByteArray?
                ) {
                    if (device == null || characteristic?.uuid != CHARACTERISTIC_UUID || preparedWrite || offset != 0 || value == null || value.size < 5) {
                        if (responseNeeded && device != null) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null)
                        return
                    }
                    val buffer = ByteBuffer.wrap(value)
                    val index = buffer.short.toInt() and 0xffff
                    val total = buffer.short.toInt() and 0xffff
                    val address = device.address
                    var valid = total in 1..1024 && index < total
                    if (valid && index == 0) {
                        if (assemblies.size >= 16 && !assemblies.containsKey(address)) valid = false
                        else assemblies[address] = Assembly(total)
                    }
                    val assembly = assemblies[address]
                    valid = valid && assembly != null && assembly.total == total && assembly.next == index &&
                        assembly.bytes.size() + value.size - 4 <= 16_412 && android.os.SystemClock.elapsedRealtime() - assembly.start < 30_000
                    if (!valid || assembly == null) {
                        assemblies.remove(address)
                        if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
                        return
                    }
                    assembly.bytes.write(value, 4, value.size - 4); assembly.next++
                    if (index + 1 == total) {
                        assemblies.remove(address)
                        scope.launch {
                            val accepted = try {
                                val json = MeshSecurity.decrypt(context, assembly.bytes.toByteArray()).toString(Charsets.UTF_8)
                                MeshPacket.fromJson(json)?.let { acceptPacket(it, address) } ?: false
                            } catch (_: Exception) { false }
                            if (responseNeeded) try { gattServer?.sendResponse(device, requestId, if (accepted) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE, 0, null) } catch (_: SecurityException) { }
                        }
                    } else if (responseNeeded) gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }

            gattServer = bluetoothManager?.openGattServer(context, callback)
            val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            service.addCharacteristic(characteristic)
            if (gattServer?.addService(service) != true) {
                stopGattServer(); _status.value = "Nearby GATT service could not be registered. Retry Bluetooth setup."
            }
        } catch (e: Exception) {
            stopGattServer(); _status.value = "Nearby GATT startup failed (${e.javaClass.simpleName})."
            Log.e(TAG, "Error starting GATT Server: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopGattServer() {
        try {
            gattServer?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing GATT server: ${e.message}")
        } finally {
            gattServer = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        if (_isAdvertising.value) return
        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            _status.value = "This phone cannot advertise the nearby service. Receiving connections is unavailable."
            Log.w(TAG, "BLE Advertising not supported on this device")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        try {
            advertiser?.startAdvertising(settings, data, advertiseCallback)

            Log.i(TAG, "BLE Advertising started for SafeAlerts Mesh")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start advertising: ${e.message}")
            _status.value = "Bluetooth advertising failed (${e.javaClass.simpleName})."
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        if (!_isAdvertising.value) return
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping advertising: ${e.message}")
        } finally {
            _isAdvertising.value = false
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            _isAdvertising.value = true
        }

        override fun onStartFailure(errorCode: Int) {
            _isAdvertising.value = false
            _status.value = "Advertising unavailable (Android code $errorCode)"
            _isAdvertising.value = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        if (_isScanning.value) return
        scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            _status.value = "Bluetooth scanner unavailable. Check Bluetooth and permissions."
            Log.w(TAG, "BLE Scanner unavailable")
            return
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()

        try {
            scanner?.startScan(listOf(filter), scanSettings, scanCallback)
            _isScanning.value = true
            Log.i(TAG, "BLE Scan started for SafeAlerts Mesh peers")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BLE scan: ${e.message}")
            _status.value = "Bluetooth scan could not start (${e.javaClass.simpleName})."
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        if (!_isScanning.value) return
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping scan: ${e.message}")
        } finally {
            _isScanning.value = false
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val device = result.device ?: return
            val address = device.address ?: return
            val name = try { device.name ?: "SafeAlerts Device" } catch (_: Exception) { "SafeAlerts Device" }
            val peer = MeshPeer(
                deviceId = address.replace(":", "").takeLast(16),
                deviceName = name,
                bluetoothAddress = address,
                rssi = result.rssi,
                lastSeenTimestamp = System.currentTimeMillis()
            )
            if (peersMap.size >= 64 && !peersMap.containsKey(address)) return
            val existing = peersMap[address]
            peersMap[address] = if (existing?.isAuthenticated == true) existing.copy(rssi = result.rssi, lastSeenTimestamp = System.currentTimeMillis()) else peer
            val now = System.currentTimeMillis()
            if (now - (lastHandshake[address] ?: 0L) > 60_000 && peersMap.size <= 32) {
                lastHandshake[address] = now
                scope.launch {
                    try {
                        val name = com.siddesh.smsalerts.data.repository.UserRepository.getInstance().userProfile.value?.fullName.orEmpty()
                        val nonce = UUID.randomUUID().toString()
                        challenges[address] = nonce to System.currentTimeMillis()
                        val hello = MeshSecurity.signPacket(context, MeshPacket(senderDeviceId = myDeviceId, packetType = MeshPacket.TYPE_HEARTBEAT,
                            senderName = name.take(100), messageText = "hello:$nonce", maxHops = 0))
                        transmitMutex.withLock { connectAndWritePacket(device, MeshSecurity.encrypt(context, hello.toByteArray())) }
                    } catch (_: Exception) { _status.value = "Peer handshake failed; verify the private group code" }
                }
            }
            _discoveredPeers.value = peersMap.values.toList()
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE Scan failed: $errorCode")
            _isScanning.value = false
            _status.value = "Bluetooth discovery failed (Android code $errorCode). Wait, then retry Nearby."
        }
    }

    private fun pruneStalePeers() {
        val now = System.currentTimeMillis()
        val staleThreshold = 120_000L // 2 minutes
        val iterator = peersMap.entries.iterator()
        var changed = false
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.lastSeenTimestamp > staleThreshold) {
                iterator.remove()
                changed = true
            }
        }
        lastHandshake.keys.retainAll(peersMap.keys)
        challenges.entries.removeIf { now - it.value.second > 30_000 }
        receiveRates.entries.removeIf { now - it.value.first > 60_000 }
        assemblies.entries.removeIf { android.os.SystemClock.elapsedRealtime() - it.value.start > 30_000 }
        if (changed) {
            _discoveredPeers.value = peersMap.values.toList()
        }
    }

    /**
     * Broadcasts an emergency SOS packet across all reachable SafeAlerts peers.
     */
    fun broadcastSosPacket(packet: MeshPacket): Boolean {
        if (!MeshSecurity.hasGroup(context)) { event(packet.messageId, "SAVED_LOCALLY: configure a private group to transmit"); return false }
        if (packet.messageText.toByteArray().size > 4096) { event(packet.messageId, "FAILED: message exceeds 4096 bytes"); return false }
        val group = MeshSecurity.groupCode(context)
        scope.launch {
            try {
                check(group == MeshSecurity.groupCode(context)) { "Nearby group changed" }
                val signed = MeshSecurity.signPacket(context, packet)
                queue.enqueue(signed).forEach { event(it.messageId, "FAILED: queue capacity prioritized SOS") }
                replay.edit().putLong(signed.messageId, signed.expiryTimestamp).commit()
                event(signed.messageId, if (_isScanning.value && peersMap.values.none { it.isAuthenticated }) "NO_COMPATIBLE_DEVICES: saved for retry" else "SAVED_LOCALLY: waiting for transport")
                if (_isScanning.value) transmitToKnownPeers(signed)
            } catch (_: Exception) { event(packet.messageId, "FAILED: could not queue packet") }
        }
        return true
    }
    fun retryPacket(id: String) {
        scope.launch {
            try {
                val packet = queue.getPendingPackets().find { it.messageId == id }
                if (packet == null) { event(id, "EXPIRED_OR_UNAVAILABLE: cannot resend"); return@launch }
                val edit = attempts.edit()
                attempts.all.keys.filter { it.contains(id) && !it.startsWith("ack:") }.forEach { edit.remove(it) }
                edit.commit()
                event(id, "QUEUED: retry requested")
                if (_isScanning.value) transmitToKnownPeers(packet)
            } catch (e: Exception) { event(id, "FAILED: retry could not be prepared") }
        }
    }
    @SuppressLint("MissingPermission")
    fun connectPeer(peer: MeshPeer) {
        if (!_isScanning.value || !PermissionManager.hasBluetoothPermissions(context)) return
        val address = peer.bluetoothAddress
        if (System.currentTimeMillis() - (lastHandshake[address] ?: 0L) < 5000) return
        lastHandshake[address] = System.currentTimeMillis()
        scope.launch {
            try {
                val nonce = UUID.randomUUID().toString()
                challenges[address] = nonce to System.currentTimeMillis()
                val hello = MeshSecurity.signPacket(context, MeshPacket(senderDeviceId = myDeviceId,
                    packetType = MeshPacket.TYPE_HEARTBEAT, messageText = "hello:$nonce", maxHops = 0,
                    senderName = com.siddesh.smsalerts.data.repository.UserRepository.getInstance().userProfile.value?.fullName.orEmpty().take(100)))
                val device = bluetoothAdapter?.getRemoteDevice(address) ?: return@launch
                val sent = transmitMutex.withLock { connectAndWritePacket(device, MeshSecurity.encrypt(context, hello.toByteArray())) }
                _status.value = if (sent) "Handshake sent; waiting for this peer's signed reply." else "Connection failed. Check the other phone's group and Bluetooth."
            } catch (e: Exception) { _status.value = "Peer connection failed. Check Bluetooth permissions and the private group." }
        }
    }
    fun handleIncomingPacketString(packetJson: String, senderAddress: String) {
        MeshPacket.fromJson(packetJson)?.let { handleIncomingPacket(it, senderAddress) }
    }
    fun handleIncomingPacket(packet: MeshPacket, senderAddress: String) {
        scope.launch {
            try { if (!acceptPacket(packet, senderAddress)) _status.value = "Incoming packet rejected by validation." }
            catch (e: Exception) { _status.value = "Incoming packet could not be stored; sender must retry." }
        }
    }
    private suspend fun acceptPacket(packet: MeshPacket, address: String): Boolean = receiveMutex.withLock {
        if (!MeshSecurity.verifyPacketIntegrity(packet)) return@withLock false
        val now = System.currentTimeMillis()
        val rate = receiveRates[address]
        if (rate != null && now - rate.first < 60_000 && rate.second >= 30) return@withLock false
        receiveRates[address] = if (rate == null || now - rate.first >= 60_000) now to 1 else rate.first to rate.second + 1
        val lastSigner = packet.relayProofs.lastOrNull()?.substringBefore('.')?.let { MeshSecurity.sha256Hex(it).take(32) } ?: packet.senderDeviceId
        if (packet.packetType == MeshPacket.TYPE_HEARTBEAT) {
            if (packet.hopCount != 0 || packet.maxHops != 0) return@withLock false
            if (packet.messageText.startsWith("reply:")) {
                val challenge = challenges[address] ?: return@withLock false
                if (packet.recipientId != myDeviceId || packet.messageText != "reply:${challenge.first}" || now - challenge.second > 30_000) return@withLock false
                challenges.remove(address)
                val existing = peersMap[address]
                peersMap[address] = MeshPeer(packet.senderDeviceId, packet.senderName.ifBlank { "Name not provided" }, address,
                    existing?.rssi ?: -127, now, isConnected = existing?.isConnected == true, isAuthenticated = true,
                    verificationFingerprint = MeshSecurity.sha256Hex(packet.publicKey))
                // A new authenticated session may retry bounded packets that failed on the old link.
                attempts.all.keys.filter { it.endsWith(":${packet.senderDeviceId}") && !it.startsWith("ack:") }
                    .forEach { attempts.edit().remove(it).apply() }
                _discoveredPeers.value = peersMap.values.toList()
            } else if (packet.messageText.startsWith("hello:")) {
                val nonce = packet.messageText.removePrefix("hello:")
                if (!nonce.matches(Regex("[a-fA-F0-9-]{36}"))) return@withLock false
                scope.launch {
                    val reply = MeshSecurity.signPacket(context, MeshPacket(senderDeviceId = myDeviceId, recipientId = packet.senderDeviceId,
                        packetType = MeshPacket.TYPE_HEARTBEAT, messageText = "reply:$nonce", maxHops = 0,
                        senderName = com.siddesh.smsalerts.data.repository.UserRepository.getInstance().userProfile.value?.fullName.orEmpty().take(100)))
                    try { bluetoothAdapter?.getRemoteDevice(address)?.let { d -> transmitMutex.withLock { connectAndWritePacket(d, MeshSecurity.encrypt(context, reply.toByteArray())) } } } catch (_: Exception) { }
                }
            } else return@withLock false
            return@withLock true
        }
        val forMe = packet.recipientId == myDeviceId || packet.recipientId == MeshPacket.BROADCAST_RECIPIENT
        val duplicate = replay.getLong(packet.messageId, 0) > now
        if (!duplicate) {
            val expired = replay.all.filterValues { (it as? Long ?: 0) <= now }.keys
            val edit = replay.edit(); expired.forEach { edit.remove(it) }
            if (replay.all.size - expired.size >= 1000) return@withLock false
            if (forMe && synchronized(listeners) { listeners.toList().any { !it.onPacketReceived(packet) } }) return@withLock false
            if (packet.canForward() && packet.senderDeviceId != myDeviceId && packet.recipientId != myDeviceId) {
                queue.enqueue(MeshSecurity.forward(context, packet)).forEach { event(it.messageId, "FAILED: queue capacity prioritized SOS") }
            }
            if (forMe && packet.packetType == MeshPacket.TYPE_SOS_ALERT) {
                inbox.enqueue(packet)
                _receivedSos.value = inbox.getPendingPackets()
            }
            check(edit.putLong(packet.messageId, packet.expiryTimestamp).commit())
            event(packet.messageId, "RECEIVED from $lastSigner")
            if (forMe && packet.packetType == MeshPacket.TYPE_SOS_ALERT) notifyNearbySos(packet)
        }
        if (forMe && packet.packetType in listOf(MeshPacket.TYPE_SOS_ALERT, MeshPacket.TYPE_WALKIE_TEXT, MeshPacket.TYPE_CHAT_DELETE)) {
            broadcastSosPacket(MeshPacket(senderDeviceId = myDeviceId, recipientId = packet.senderDeviceId,
                packetType = MeshPacket.TYPE_ACK, messageText = packet.messageId, maxHops = 5))
        }
        if (forMe && packet.packetType == MeshPacket.TYPE_ACK) {
            val original = queue.getPendingPackets().find { it.messageId == packet.messageText && it.senderDeviceId == myDeviceId }
            if (original != null && (original.recipientId == packet.senderDeviceId || original.recipientId == MeshPacket.BROADCAST_RECIPIENT)) {
                attempts.edit().putBoolean("ack:${original.messageId}:${packet.senderDeviceId}", true).apply()
                event(original.messageId, "ACKNOWLEDGED by ${packet.senderDeviceId}")
                if (original.recipientId != MeshPacket.BROADCAST_RECIPIENT) queue.removePacket(original.messageId)
            }
        }
        true
    }
    private fun notifyNearbySos(packet: MeshPacket) {
        try {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            val channel = "nearby_emergencies"
            if (android.os.Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(android.app.NotificationChannel(channel, "Nearby group emergencies", android.app.NotificationManager.IMPORTANCE_HIGH))
            val intent = android.content.Intent(context, com.siddesh.smsalerts.MainActivity::class.java).putExtra("openNearby", true)
            val open = android.app.PendingIntent.getActivity(context, packet.messageId.hashCode(), intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            nm.notify(packet.messageId.hashCode(), androidx.core.app.NotificationCompat.Builder(context, channel)
                .setSmallIcon(com.siddesh.smsalerts.R.drawable.ic_notification_shield)
                .setContentTitle("Nearby SOS: ${packet.senderName.ifBlank { "Group member" }}")
                .setContentText("Authenticated group packet. Open Nearby Offline for details.")
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PRIVATE)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH).setContentIntent(open).setAutoCancel(true).build())
        } catch (_: SecurityException) { }
    }

    @SuppressLint("MissingPermission")
    private suspend fun transmitToKnownPeers(packet: MeshPacket) {
        if (packet.isExpired()) { event(packet.messageId, "EXPIRED"); queue.removePacket(packet.messageId); return }
        for (peer in peersMap.values.filter { it.isAuthenticated }.take(32)) {
            if (peer.deviceId == packet.senderDeviceId || packet.relayProofs.any { MeshSecurity.sha256Hex(it.substringBefore('.')).take(32) == peer.deviceId }) continue
            val key = "${packet.messageId}:${peer.deviceId}"
            val count = attempts.getInt(key, 0)
            if (count >= 3 || attempts.getBoolean("ack:$key", false)) continue
            attempts.edit().putInt(key, count + 1).commit()
            if (!_transferStatus.value[packet.messageId].orEmpty().startsWith("ACKNOWLEDGED")) event(packet.messageId, "TRANSMITTING: peer receipt unconfirmed")
            try {
                val device = bluetoothAdapter?.getRemoteDevice(peer.bluetoothAddress) ?: continue
                val sent = transmitMutex.withLock { connectAndWritePacket(device, MeshSecurity.encrypt(context, packet.toByteArray())) }
                if (sent) {
                    attempts.edit().putBoolean("ok:$key", true).apply()
                    if (!_transferStatus.value[packet.messageId].orEmpty().startsWith("ACKNOWLEDGED")) event(packet.messageId, if (packet.senderDeviceId == myDeviceId) "SENT_TO_PEER ${peer.deviceId}" else "FORWARDED_TO_PEER ${peer.deviceId}")
                } else if (count == 2) event(packet.messageId, "FAILED to peer ${peer.deviceId}")
            } catch (_: Exception) { if (count == 2) event(packet.messageId, "FAILED to peer ${peer.deviceId}") }
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private suspend fun connectAndWritePacket(device: BluetoothDevice, data: ByteArray): Boolean {
        var connection: BluetoothGatt? = null
        try {
            return withTimeoutOrNull(20_000) {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    var mtu = 23
                    var frames = emptyList<ByteArray>()
                    var index = 0
                    fun finish(ok: Boolean) { if (continuation.isActive) continuation.resume(ok) }
                    fun writeNext(gatt: BluetoothGatt) {
                        val characteristic = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHARACTERISTIC_UUID)
                        if (characteristic == null) { finish(false); return }
                        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        characteristic.value = frames[index]
                        try { if (!gatt.writeCharacteristic(characteristic)) finish(false) } catch (_: SecurityException) { finish(false) }
                    }
                    connection = device.connectGatt(context, false, object : BluetoothGattCallback() {
                        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                            peersMap[device.address]?.let { peersMap[device.address] = it.copy(isConnected = newState == BluetoothProfile.STATE_CONNECTED); _discoveredPeers.value = peersMap.values.toList() }
                            if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) { finish(false); return }
                            try { if (newState == BluetoothProfile.STATE_CONNECTED && !gatt.requestMtu(247)) gatt.discoverServices() }
                            catch (_: SecurityException) { finish(false) }
                        }
                        override fun onMtuChanged(gatt: BluetoothGatt, value: Int, status: Int) { if (status == BluetoothGatt.GATT_SUCCESS) mtu = value; try { if (!gatt.discoverServices()) finish(false) } catch (_: SecurityException) { finish(false) } }
                        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                            if (status != BluetoothGatt.GATT_SUCCESS) { finish(false); return }
                            val chunks = data.asList().chunked((mtu - 7).coerceIn(16, 240)).map { it.toByteArray() }
                            frames = chunks.mapIndexed { i, bytes -> ByteBuffer.allocate(4 + bytes.size).putShort(i.toShort()).putShort(chunks.size.toShort()).put(bytes).array() }
                            writeNext(gatt)
                        }
                        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                            if (status != BluetoothGatt.GATT_SUCCESS) { finish(false); return }
                            index++
                            if (index == frames.size) finish(true) else writeNext(gatt)
                        }
                    })
                    if (connection == null) finish(false)
                    continuation.invokeOnCancellation { try { connection?.disconnect(); connection?.close() } catch (_: Exception) { } }
                }
            } ?: false
        } finally { try { connection?.disconnect(); connection?.close() } catch (_: Exception) { } }
    }
}
