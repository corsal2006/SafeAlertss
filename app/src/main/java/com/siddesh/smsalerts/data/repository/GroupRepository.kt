package com.siddesh.smsalerts.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.siddesh.smsalerts.mesh.MeshSecurity
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.security.SecureRandom
import java.util.UUID

/** One group identity for cloud membership/history and its current encrypted nearby channel. */
class GroupRepository(private val context: Context) {
    val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private fun uid() = auth.currentUser?.takeUnless { it.isAnonymous }?.uid ?: error("Sign in to use an online group.")
    private fun key() = android.util.Base64.encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) }, android.util.Base64.NO_WRAP)
    private suspend fun profile(owner: String): Map<String, Any> {
        val saved = db.document("users/$owner").get(Source.SERVER).await()
        check(uid() == owner) { "Account changed. Retry with the current account." }
        return mapOf("name" to saved.getString("fullName").orEmpty().ifBlank { saved.getString("name").orEmpty() }
            .ifBlank { auth.currentUser?.displayName.orEmpty() }.take(100),
            "photoUrl" to saved.getString("photoUrl").orEmpty().ifBlank { auth.currentUser?.photoUrl?.toString().orEmpty() },
            "deviceId" to MeshSecurity.getOrCreatePseudonymousDeviceId(context), "joinedAt" to FieldValue.serverTimestamp())
    }
    suspend fun create(name: String): String = withTimeout(15_000) {
        val owner = uid(); require(name.trim().length in 1..60)
        val id = UUID.randomUUID().toString()
        val batch = db.batch()
        batch.set(db.document("groups/$id"), mapOf("ownerUid" to owner, "name" to name.trim(), "memberUids" to listOf(owner),
            "meshKey" to key(), "epoch" to 1, "needsRotation" to false, "inviteHash" to "", "lastJoinHash" to "",
            "createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
        batch.set(db.document("groups/$id/members/$owner"), profile(owner))
        batch.commit().await(); id
    }
    suspend fun invite(id: String): String = withTimeout(15_000) {
        val owner = uid()
        val group = db.document("groups/$id").get(Source.SERVER).await()
        check(group.getString("ownerUid") == owner) { "Only the group owner can invite members." }
        val code = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val hash = MeshSecurity.sha256Hex(code)
        val batch = db.batch()
        batch.set(db.document("group_invitations/$hash"), mapOf("groupId" to id, "groupName" to group.getString("name"),
            "ownerUid" to owner, "createdAt" to FieldValue.serverTimestamp(), "usedBy" to "", "revoked" to false))
        batch.update(group.reference, mapOf("inviteHash" to hash, "updatedAt" to FieldValue.serverTimestamp()))
        batch.commit().await(); code
    }
    suspend fun preview(code: String): DocumentSnapshot = withTimeout(15_000) {
        val owner = uid()
        val normalized = code.trim().lowercase(java.util.Locale.ROOT)
        require(normalized.matches(Regex("[a-f0-9]{32}"))) { "Paste the complete 32-character group invitation." }
        val hash = MeshSecurity.sha256Hex(normalized)
        val ticket = db.document("spark_circle_lookups/$owner")
        db.runTransaction { tx ->
            val old = tx.get(ticket)
            val start = old.getTimestamp("windowStart")
            val reset = start == null || System.currentTimeMillis() - start.toDate().time >= 900_000
            val count = if (reset) 1L else (old.getLong("count") ?: 0) + 1
            check(count <= 5) { "Lookup limit reached. Try again after 15 minutes." }
            tx.set(ticket, mapOf("codeHash" to hash, "requestedAt" to FieldValue.serverTimestamp(),
                "windowStart" to if (reset) FieldValue.serverTimestamp() else start!!, "count" to count))
        }.await()
        db.document("group_invitations/$hash").get(Source.SERVER).await().also {
            check(it.exists() && it.getBoolean("revoked") == false && it.getString("usedBy") == "") { "Invitation is invalid, revoked or already used." }
            check(System.currentTimeMillis() < (it.getTimestamp("createdAt")?.toDate()?.time ?: 0) + 900_000) { "Invitation expired." }
        }
    }
    suspend fun accept(invitation: DocumentSnapshot): String = withTimeout(15_000) {
        val owner = uid(); val id = invitation.getString("groupId") ?: error("Invalid group invitation.")
        check(invitation.getString("ownerUid") != owner) { "You already own this group." }
        val batch = db.batch()
        batch.update(db.document("groups/$id"), mapOf("memberUids" to FieldValue.arrayUnion(owner),
            "lastJoinHash" to invitation.id, "updatedAt" to FieldValue.serverTimestamp()))
        batch.set(db.document("groups/$id/members/$owner"), profile(owner))
        batch.update(invitation.reference, "usedBy", owner)
        batch.commit().await(); id
    }
    suspend fun revokeInvitation(id: String) = withTimeout(15_000) {
        uid(); val group = db.document("groups/$id").get(Source.SERVER).await()
        val hash = group.getString("inviteHash").orEmpty()
        if (hash.isNotBlank()) db.document("group_invitations/$hash").update("revoked", true).await()
    }
    suspend fun remove(id: String, member: String) = withTimeout(15_000) {
        val owner = uid(); val ref = db.document("groups/$id")
        db.runTransaction { tx ->
            val group = tx.get(ref); val members = (group.get("memberUids") as? List<*>)?.filterIsInstance<String>().orEmpty()
            check(owner == group.getString("ownerUid") || owner == member)
            check(member != group.getString("ownerUid")) { "The owner cannot leave an active group. Remove members first or retain the group." }
            // A retry after an acknowledged/uncertain commit must not rotate twice or fail.
            if (member !in members) return@runTransaction Unit
            val updates = mutableMapOf<String, Any>("memberUids" to (members - member), "updatedAt" to FieldValue.serverTimestamp(), "inviteHash" to "")
            if (owner == group.getString("ownerUid")) {
                updates["meshKey"] = key(); updates["epoch"] = (group.getLong("epoch") ?: 1) + 1; updates["needsRotation"] = false
            } else updates["needsRotation"] = true
            tx.update(ref, updates)
            Unit
        }.await()
    }
    suspend fun rotateIfNeeded(id: String) = withTimeout(15_000) {
        val owner = uid(); val ref = db.document("groups/$id")
        db.runTransaction { tx ->
            val group = tx.get(ref)
            if (group.getString("ownerUid") == owner && group.getBoolean("needsRotation") == true)
                tx.update(ref, mapOf("meshKey" to key(), "epoch" to (group.getLong("epoch") ?: 1) + 1,
                    "needsRotation" to false, "updatedAt" to FieldValue.serverTimestamp()))
        }.await()
    }
    fun activate(group: DocumentSnapshot) {
        check(uid() in (group.get("memberUids") as? List<*>).orEmpty())
        check(group.getBoolean("needsRotation") == false) { "Group owner must refresh the group key after a member leaves." }
        com.siddesh.smsalerts.mesh.BleMeshManager.getInstance(context).stopMesh()
        MeshSecurity.configureGroup(context, group.getString("meshKey") ?: error("Group key unavailable."), group.id)
        com.siddesh.smsalerts.walkie.WalkieTalkieManager.getInstance(context).refreshSession()
    }
    suspend fun send(id: String, messageId: String, text: String, deviceTimestamp: Long) = withTimeout(12_000) {
        val owner = uid(); require(text.isNotBlank() && text.toByteArray().size <= 1000)
        val ref = db.document("groups/$id/messages/$messageId")
        db.runTransaction { tx ->
            val existing = tx.get(ref)
            if (existing.exists()) check(existing.getString("senderUid") == owner && existing.getString("text") == text) { "Message ID conflict. The existing message was preserved." }
            else tx.set(ref, mapOf("groupId" to id, "senderUid" to owner,
                "senderDeviceId" to MeshSecurity.getOrCreatePseudonymousDeviceId(context), "text" to text,
                "deviceTimestamp" to deviceTimestamp, "createdAt" to FieldValue.serverTimestamp()))
        }.await()
    }
}
