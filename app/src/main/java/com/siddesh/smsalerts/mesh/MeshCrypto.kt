package com.siddesh.smsalerts.mesh

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Version 2 wire primitives. GCM rejects tampering before any JSON is processed. */
object MeshCrypto {
    fun encrypt(key: ByteArray, plain: ByteArray): ByteArray {
        require(key.size == 32 && plain.size <= 16_384)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        cipher.updateAAD("SafeAlerts-v2".toByteArray())
        return cipher.iv + cipher.doFinal(plain)
    }
    fun decrypt(key: ByteArray, wire: ByteArray): ByteArray {
        require(key.size == 32 && wire.size in 28..16_412)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, wire.copyOfRange(0, 12)))
        cipher.updateAAD("SafeAlerts-v2".toByteArray())
        return cipher.doFinal(wire.copyOfRange(12, wire.size))
    }
    fun sign(privateKey: PrivateKey, payload: ByteArray): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        initSign(privateKey); update(payload); sign()
    }
    fun verify(publicKey: ByteArray, payload: ByteArray, signature: ByteArray): Boolean = try {
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKey)))
            update(payload); verify(signature)
        }
    } catch (_: Exception) { false }
}
