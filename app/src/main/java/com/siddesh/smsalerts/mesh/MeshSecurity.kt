package com.siddesh.smsalerts.mesh

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.*
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Group encryption plus self-certifying ECDSA identities and a signed relay chain. No application-wide shared secret. */
object MeshSecurity {
    private const val SIGN_ALIAS = "safealerts_mesh_identity_v2"
    private const val WRAP_ALIAS = "safealerts_mesh_group_wrap_v2"
    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    @Synchronized private fun signingKey(): KeyStore.PrivateKeyEntry {
        val ks = store()
        if (!ks.containsAlias(SIGN_ALIAS)) KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
            initialize(KeyGenParameterSpec.Builder(SIGN_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_SHA256).build())
        }.generateKeyPair()
        return store().getEntry(SIGN_ALIAS, null) as KeyStore.PrivateKeyEntry
    }
    @Synchronized private fun wrappingKey(): SecretKey {
        if (!store().containsAlias(WRAP_ALIAS)) KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(WRAP_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
        return store().getKey(WRAP_ALIAS, null) as SecretKey
    }
    fun createGroup(context: Context): String = encode(ByteArray(32).also { SecureRandom().nextBytes(it) }).also { configureGroup(context, it) }
    fun configureGroup(context: Context, code: String, cloudGroupId: String = "") {
        val key = decode(code.trim()); require(key.size == 32) { "Use the full private group code from the other phone." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        check(context.getSharedPreferences("mesh_v2", 0).edit().putString("group", encode(cipher.iv + cipher.doFinal(key)))
            .putString("cloudGroupId", cloudGroupId).putString("owner", com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()).commit())
    }
    fun cloudGroupId(context: Context): String = context.getSharedPreferences("mesh_v2", 0).let {
        if (it.getString("owner", "") == com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()) it.getString("cloudGroupId", "").orEmpty() else ""
    }
    fun clearGroup(context: Context) { check(context.getSharedPreferences("mesh_v2", 0).edit().remove("group").remove("cloudGroupId").commit()) }
    fun groupCode(context: Context): String? = groupKey(context)?.let { encode(it) }
    fun hasGroup(context: Context) = groupKey(context) != null
    fun identityFingerprint(): String = sha256Hex(encode(signingKey().certificate.publicKey.encoded))
    fun formatFingerprint(value: String): String = value.uppercase(java.util.Locale.ROOT).chunked(4).joinToString(" ")
    fun encryptLocal(bytes: ByteArray): ByteArray {
        require(bytes.size <= 1024 * 1024)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        return cipher.iv + cipher.doFinal(bytes)
    }
    fun decryptLocal(bytes: ByteArray): ByteArray {
        require(bytes.size in 28..(1024 * 1024 + 28))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size))
    }
    private fun groupKey(context: Context): ByteArray? = try {
        val prefs = context.getSharedPreferences("mesh_v2", 0)
        val currentOwner = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (prefs.contains("owner") && prefs.getString("owner", "") != currentOwner) null
        else prefs.getString("group", null)?.let { text ->
            val bytes = decode(text); val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            cipher.doFinal(bytes.copyOfRange(12, bytes.size))
        }
    } catch (_: Exception) { null }
    fun encrypt(context: Context, bytes: ByteArray): ByteArray = MeshCrypto.encrypt(requireNotNull(groupKey(context)) { "Configure a private nearby group first" }, bytes)
    fun decrypt(context: Context, bytes: ByteArray): ByteArray = MeshCrypto.decrypt(requireNotNull(groupKey(context)), bytes)
    fun getOrCreatePseudonymousDeviceId(context: Context): String = sha256Hex(encode(signingKey().certificate.publicKey.encoded)).take(32)
    fun signPacket(context: Context, packet: MeshPacket): MeshPacket {
        val key = signingKey()
        val unsigned = packet.copy(version = 2, senderDeviceId = getOrCreatePseudonymousDeviceId(context), publicKey = encode(key.certificate.publicKey.encoded), hopCount = 0, relayProofs = emptyList())
        return unsigned.copy(signature = encode(MeshCrypto.sign(key.privateKey, unsigned.getSigningPayload().toByteArray())))
    }
    private fun relayPayload(packet: MeshPacket, prior: List<String>) = (packet.getSigningPayload() + packet.signature + org.json.JSONArray(prior).toString()).toByteArray()
    fun forward(context: Context, packet: MeshPacket): MeshPacket {
        require(packet.canForward())
        val key = signingKey()
        val proof = encode(key.certificate.publicKey.encoded) + "." + encode(MeshCrypto.sign(key.privateKey, relayPayload(packet, packet.relayProofs)))
        return packet.copy(hopCount = packet.hopCount + 1, relayProofs = packet.relayProofs + proof)
    }
    fun verifyPacketIntegrity(packet: MeshPacket, currentTimeMs: Long = System.currentTimeMillis(), decoder: (String) -> ByteArray = ::decode): Boolean = try {
        require(packet.version == 2 && packet.messageId.length in 1..64 && packet.emergencyId.length in 1..64)
        require(packet.timestamp <= currentTimeMs + 120_000 && packet.expiryTimestamp > packet.timestamp && packet.expiryTimestamp - packet.timestamp <= MeshPacket.DEFAULT_TTL_MS)
        require(!packet.isExpired(currentTimeMs) && packet.maxHops in 0..5 && packet.hopCount in 0..packet.maxHops && packet.relayProofs.size == packet.hopCount)
        require(packet.packetType in setOf(1, 2, 3, 4, MeshPacket.TYPE_CHAT_DELETE) && packet.messageText.toByteArray().size <= 4096 && packet.senderName.length <= 100)
        if (packet.packetType == MeshPacket.TYPE_CHAT_DELETE) require(packet.messageText.matches(Regex("[a-fA-F0-9-]{36}")) && packet.recipientId != MeshPacket.BROADCAST_RECIPIENT)
        require(packet.latitude == null || packet.latitude.isFinite() && packet.latitude in -90.0..90.0)
        require(packet.longitude == null || packet.longitude.isFinite() && packet.longitude in -180.0..180.0)
        require(packet.recipientId.length in 1..128 && packet.emergencyType.length <= 100)
        require(packet.groupId.length <= 100 && packet.senderUid.length <= 128)
        require((packet.latitude == null) == (packet.longitude == null))
        require(packet.accuracy == null || packet.accuracy.isFinite() && packet.accuracy >= 0)
        require(packet.age == null || packet.age in 0..150)
        require(packet.locationTimestamp == null || packet.locationTimestamp in 0..currentTimeMs + 120_000)
        require(packet.senderDeviceId == sha256Hex(packet.publicKey).take(32))
        require(MeshCrypto.verify(decoder(packet.publicKey), packet.getSigningPayload().toByteArray(), decoder(packet.signature)))
        val identities = mutableSetOf(packet.senderDeviceId)
        packet.relayProofs.forEachIndexed { i, proof ->
            val parts = proof.split('.'); require(parts.size == 2 && identities.add(sha256Hex(parts[0]).take(32)))
            require(MeshCrypto.verify(decoder(parts[0]), relayPayload(packet, packet.relayProofs.take(i)), decoder(parts[1])))
        }
        true
    } catch (_: Exception) { false }
    fun sha256Hex(data: String): String = MessageDigest.getInstance("SHA-256").digest(data.toByteArray()).joinToString("") { "%02x".format(it) }
}
