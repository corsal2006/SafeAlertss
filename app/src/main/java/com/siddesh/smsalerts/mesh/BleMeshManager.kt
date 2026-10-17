package com.siddesh.smsalerts.mesh

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.siddesh.smsalerts.utils.PermissionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "SafeAlerts/BleMesh"

interface MeshPacketListener {
    fun onPacketReceived(packet: MeshPacket)
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
    val myDeviceId: String = MeshSecurity.getOrCreatePseudonymousDeviceId(context)

    private val _discoveredPeers = MutableStateFlow<List<MeshPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<MeshPeer>> = _discoveredPeers.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val listeners = mutableListOf<MeshPacketListener>()
    private val peersMap = ConcurrentHashMap<String, MeshPeer>()

    private var gattServer: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    init {
        scope.launch {
            // Periodic prune and opportunistic gateway sync every 60 seconds
            while (isActive) {
                delay(60_000L)
                deduplicator.pruneIfNecessary()
                queue.uploadPendingToCloudGateway()
                pruneStalePeers()
            }
        }
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

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun startMesh() {
        if (!isBluetoothSupported() || !isBluetoothEnabled()) {
            Log.w(TAG, "Bluetooth not supported or disabled; mesh cannot start")
            return
        }

        if (!PermissionManager.hasBluetoothPermissions(context)) {
            Log.w(TAG, "Missing Bluetooth permissions; mesh cannot start")
            return
        }

        startGattServer()
        startAdvertising()
        startScanning()
    }

    @SuppressLint("MissingPermission")
    fun stopMesh() {
        stopScanning()
        stopAdvertising()
        stopGattServer()
        peersMap.clear()
        _discoveredPeers.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    private fun startGattServer() {
        if (gattServer != null) return
        try {
            val callback = object : BluetoothGattServerCallback() {
                override fun onCharacteristicWriteRequest(
                    device: BluetoothDevice?,
                    requestId: Int,
                    characteristic: BluetoothGattCharacteristic?,
                    preparedWrite: Boolean,
                    responseNeeded: Boolean,
                    offset: Int,
                    value: ByteArray?
                ) {
                    super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
                    if (responseNeeded) {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                    }

                    if (value != null && value.isNotEmpty()) {
                        val packetStr = String(value, Charsets.UTF_8)
                        handleIncomingPacketString(packetStr, device?.address ?: "")
                    }
                }
            }

            gattServer = bluetoothManager?.openGattServer(context, callback)
            val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            val characteristic = BluetoothGattCharacteristic(
                CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or
                        BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            service.addCharacteristic(characteristic)
            gattServer?.addService(service)
            Log.i(TAG, "BLE GATT Server started successfully")
        } catch (e: Exception) {
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
            _isAdvertising.value = true
            Log.i(TAG, "BLE Advertising started for SafeAlerts Mesh")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start advertising: ${e.message}")
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
            Log.d(TAG, "AdvertiseCallback onStartSuccess")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.w(TAG, "AdvertiseCallback onStartFailure: $errorCode")
            _isAdvertising.value = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        if (_isScanning.value) return
        scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
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
            val name = try { device.name ?: "SafeAlert Device" } catch (_: Exception) { "SafeAlert Device" }
            val peer = MeshPeer(
                deviceId = address.replace(":", "").takeLast(16),
                deviceName = name,
                bluetoothAddress = address,
                rssi = result.rssi,
                lastSeenTimestamp = System.currentTimeMillis()
            )
            peersMap[address] = peer
            _discoveredPeers.value = peersMap.values.toList()
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE Scan failed: $errorCode")
            _isScanning.value = false
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
        if (changed) {
            _discoveredPeers.value = peersMap.values.toList()
        }
    }

    /**
     * Broadcasts an emergency SOS packet across all reachable SafeAlerts peers.
     */
    fun broadcastSosPacket(packet: MeshPacket) {
        scope.launch {
            val signedPacket = MeshSecurity.signPacket(context, packet)
            deduplicator.markSeen(signedPacket.messageId)
            queue.enqueue(signedPacket)

            Log.i(TAG, "BROADCASTING SOS PACKET mid=${signedPacket.messageId}, type=${signedPacket.emergencyType}")
            transmitToKnownPeers(signedPacket)
        }
    }

    /**
     * Internal handler when a packet is received via GATT write.
     */
    fun handleIncomingPacketString(packetJson: String, senderAddress: String) {
        val packet = MeshPacket.fromJson(packetJson) ?: return
        handleIncomingPacket(packet, senderAddress)
    }

    fun handleIncomingPacket(packet: MeshPacket, senderAddress: String) {
        // 1. Packet validation & integrity check
        if (!MeshSecurity.verifyPacketIntegrity(packet)) {
            Log.w(TAG, "REJECTED_PACKET: Invalid signature, corrupted, or expired packet (${packet.messageId})")
            return
        }

        // 2. Deduplication check
        if (deduplicator.recordAndCheckDuplicate(packet.messageId)) {
            Log.d(TAG, "DUPLICATE_PACKET_IGNORED: Already processed ${packet.messageId}")
            return
        }

        Log.i(TAG, "VALID_MESH_PACKET_RECEIVED: mid=${packet.messageId}, hops=${packet.hopCount}/${packet.maxHops}, type=${packet.emergencyType}")

        // 3. Notify app listeners
        synchronized(listeners) {
            for (listener in listeners) {
                try {
                    listener.onPacketReceived(packet)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in packet listener: ${e.message}")
                }
            }
        }

        // 4. Store-and-forward relay policy
        scope.launch {
            // Save to local store-and-forward queue
            queue.enqueue(packet)
            // If internet is active on this device, immediately upload to cloud gateway!
            queue.uploadPendingToCloudGateway()

            // If hop limit allows, forward packet to other nearby peers!
            if (packet.canForward()) {
                val forwarded = packet.createForwardedHop()
                Log.d(TAG, "RELAYING_PACKET_TO_PEERS: mid=${forwarded.messageId}, new_hop=${forwarded.hopCount}")
                transmitToKnownPeers(forwarded)
            } else {
                Log.d(TAG, "PACKET_HOP_LIMIT_REACHED: mid=${packet.messageId}, hop=${packet.hopCount}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun transmitToKnownPeers(packet: MeshPacket) = withContext(Dispatchers.IO) {
        val bytes = packet.toByteArray()
        val peers = peersMap.values.toList()

        for (peer in peers) {
            try {
                val device = bluetoothAdapter?.getRemoteDevice(peer.bluetoothAddress) ?: continue
                connectAndWritePacket(device, bytes)
                delay(150L) // Stagger writes to avoid radio collision
            } catch (e: Exception) {
                Log.w(TAG, "Failed transmit to peer ${peer.bluetoothAddress}: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectAndWritePacket(device: BluetoothDevice, data: ByteArray) {
        device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt?.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gatt?.close()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt?.getService(SERVICE_UUID)
                    val characteristic = service?.getCharacteristic(CHARACTERISTIC_UUID)
                    if (characteristic != null) {
                        characteristic.value = data
                        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        gatt.writeCharacteristic(characteristic)
                        Log.d(TAG, "Wrote mesh packet to device ${device.address}")
                    }
                }
                gatt?.disconnect()
            }
        })
    }
}
