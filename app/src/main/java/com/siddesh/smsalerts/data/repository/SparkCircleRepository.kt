package com.siddesh.smsalerts.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.siddesh.smsalerts.service.CircleCode
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.security.SecureRandom

/** All authority, budgets and expiry are checked by Firestore rules, never by this UI client. */
class SparkCircleRepository {
    companion object {
        // Date truncates server microseconds; rules require the exact timestamp plus 15 minutes.
        fun invitationExpiry(created: Timestamp) = Timestamp(created.seconds + 900, created.nanoseconds)
    }
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val random = SecureRandom()
    private val ttl = 15 * 60_000L
    private fun uid() = auth.currentUser?.takeUnless { it.isAnonymous }?.uid
        ?: error("Sign in with your existing Google or email account.")
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private fun codeRef(hash: String) = db.collection("spark_circle_codes").document(hash)
    private fun ownerRef(uid: String) = db.collection("spark_circle_code_owners").document(uid)
    private fun ticketRef(uid: String) = db.collection("spark_circle_lookups").document(uid)
    private fun budget(old: DocumentSnapshot, limit: Int): Pair<Any, Long> {
        val start = old.getTimestamp("windowStart")
        val reset = start == null || System.currentTimeMillis() - start.toDate().time >= ttl
        val count = if (reset) 1L else (old.getLong("count") ?: 0) + 1
        check(count <= limit) { "Attempt limit reached. Try again after the 15-minute window." }
        return (if (reset) FieldValue.serverTimestamp() else start!!) to count
    }
    suspend fun manage(operation: String): CircleCode {
        val uid = uid()
        val owner = ownerRef(uid)
        if (operation == "generate" || operation == "regenerate") {
            val alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
            val code = (1..8).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
            val digest = hash(code)
            db.runTransaction { tx ->
                val previous = tx.get(owner)
                val (window, count) = budget(previous, 10)
                tx.set(owner, mapOf("code" to code, "codeHash" to digest, "issuedAt" to FieldValue.serverTimestamp(),
                    "windowStart" to window, "count" to count))
                tx.set(codeRef(digest), mapOf("ownerUid" to uid, "createdAt" to FieldValue.serverTimestamp(),
                    "revoked" to false, "usedBy" to ""))
            }.await()
        }
        val pointer = owner.get(Source.SERVER).await()
        val digest = pointer.getString("codeHash") ?: return CircleCode()
        val ref = codeRef(digest)
        if (operation == "revoke") {
            ref.update("revoked", true).await()
            return CircleCode()
        }
        val doc = ref.get(Source.SERVER).await()
        val expires = (doc.getTimestamp("createdAt")?.toDate()?.time ?: 0) + ttl
        if (doc.getBoolean("revoked") != false || !doc.getString("usedBy").isNullOrEmpty() ||
            expires <= System.currentTimeMillis()) return CircleCode()
        return CircleCode(pointer.getString("code").orEmpty(), expires)
    }
    suspend fun join(code: String) {
        val recipient = uid()
        val digest = hash(code)
        val ticket = ticketRef(recipient)
        // Count every lookup, including absent/expired codes. The ticket only authorizes this hash.
        db.runTransaction { tx ->
            val (window, count) = budget(tx.get(ticket), 5)
            tx.set(ticket, mapOf("codeHash" to digest, "requestedAt" to FieldValue.serverTimestamp(),
                "windowStart" to window, "count" to count))
        }.await()
        val ref = codeRef(digest)
        val invitation = ref.get(Source.SERVER).await()
        val sender = invitation.getString("ownerUid") ?: error("Code unavailable or expired.")
        check(sender != recipient) { "Use a code from another person." }
        val id = hash(listOf(sender, recipient).sorted().joinToString(":"))
        val relation = db.collection("circle_relationships").document(id)
        db.runTransaction { tx ->
            val fresh = tx.get(ref)
            val old = tx.get(relation)
            val created = fresh.getTimestamp("createdAt") ?: error("Code unavailable.")
            val expiry = invitationExpiry(created)
            check(fresh.getBoolean("revoked") == false &&
                expiry.toDate().time > System.currentTimeMillis()) { "Code unavailable or expired." }
            if (fresh.getString("usedBy") == recipient && old.getString("codeHash") == digest &&
                old.getString("status") in listOf("PENDING", "ACCEPTED")) return@runTransaction
            check(fresh.getString("usedBy") == "") { "Code already used. Ask for a new code." }
            check(old.getString("status") != "ACCEPTED") { "You are already connected." }
            tx.set(relation, mapOf("participants" to listOf(sender, recipient), "senderUid" to sender,
                "recipientUid" to recipient, "status" to "PENDING", "codeHash" to digest,
                "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp(),
                "expiresAt" to expiry))
            tx.update(ref, "usedBy", recipient)
        }.await()
    }
    suspend fun respond(id: String, status: String) {
        val actor = uid()
        require(status in setOf("ACCEPTED", "REJECTED", "REVOKED"))
        val relation = db.collection("circle_relationships").document(id)
        db.runTransaction { tx ->
            check(uid() == actor) { "Account changed. Try again." }
            val current = tx.get(relation)
            check(actor in (current.get("participants") as? List<*>).orEmpty()) { "Relationship unavailable." }
            if (status != "REVOKED") check(current.getString("recipientUid") == actor) { "Only the invited person can respond." }
            // A committed response can be retried after a lost confirmation. Preserve strict rules
            // by recognizing the existing state instead of attempting the transition again.
            if (current.getString("status") != status) tx.update(relation,
                mapOf("status" to status, "updatedAt" to FieldValue.serverTimestamp()))
            Unit
        }.await()
    }
}
