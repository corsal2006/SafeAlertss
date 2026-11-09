package com.siddesh.smsalerts.service

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.siddesh.smsalerts.data.model.CircleInvitation
import com.siddesh.smsalerts.data.model.TrustedCircleMember
import com.siddesh.smsalerts.data.repository.SparkCircleRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

data class CircleCode(val code: String = "", val expiresAt: Long = 0)

/** Acceptance and each directional grant are separate, server-authorized decisions. */
class TrustedCircleManager private constructor(private val context: Context) {
    companion object {
        @Volatile private var instance: TrustedCircleManager? = null
        fun getInstance(context: Context): TrustedCircleManager = instance ?: synchronized(this) {
            instance ?: TrustedCircleManager(context.applicationContext).also { instance = it }
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val repository = SparkCircleRepository()
    private val _members = MutableStateFlow<List<TrustedCircleMember>>(emptyList())
    val members = _members.asStateFlow()
    private val _invitations = MutableStateFlow<List<CircleInvitation>>(emptyList())
    val invitations = _invitations.asStateFlow()
    private val _status = MutableStateFlow("")
    val status = _status.asStateFlow()
    private val _code = MutableStateFlow(CircleCode())
    val invitationCode = _code.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private var relationships: ListenerRegistration? = null
    private val peerListeners = mutableListOf<ListenerRegistration>()
    private val locationListeners = mutableMapOf<String, ListenerRegistration>()
    private val incidentListeners = mutableMapOf<String, ListenerRegistration>()
    private val relationIds = mutableMapOf<String, String>()
    private val relationVersions = mutableMapOf<String, Timestamp>()
    private val inFlight = mutableSetOf<String>()
    private val outgoingExpiry = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val incomingExpiry = mutableMapOf<String, Long>()
    private var subscriptionGeneration = 0L
    private val grantGeneration = mutableMapOf<String, Long>()
    private var previousUid: String? = null

    init {
        scope.launch {
            while (isActive) {
                delay(15_000)
                val now = System.currentTimeMillis()
                _members.value = _members.value.map { member ->
                    val incoming = (incomingExpiry[member.id] ?: 0) > now
                    member.copy(amISharingLocationWithThem = (outgoingExpiry[member.id] ?: 0) > now,
                        isSharingLocationWithMe = incoming,
                        lastLatitude = if (incoming) member.lastLatitude else null,
                        lastLongitude = if (incoming) member.lastLongitude else null,
                        activeIncidentId = if (incoming) member.activeIncidentId else null)
                }
                incomingExpiry.filterValues { it <= now }.keys.forEach {
                    locationListeners.remove(it)?.remove(); incidentListeners.remove(it)?.remove()
                }
            }
        }
        auth.addAuthStateListener { a ->
            relationships?.remove(); clearPeers(); _invitations.value = emptyList(); _members.value = emptyList()
            _code.value = CircleCode()
            if (previousUid != null && previousUid != a.currentUser?.uid) LiveLocationManager.getInstance(context).stopLiveSharing()
            previousUid = a.currentUser?.uid
            a.currentUser?.uid?.let { uid ->
                relationships = db.collection("circle_relationships").whereArrayContains("participants", uid)
                    .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                        if (auth.currentUser?.uid != uid) return@addSnapshotListener
                        if (error != null) {
                            clearPeers(); _members.value = emptyList(); _invitations.value = emptyList()
                            _status.value = cloudError(error); return@addSnapshotListener
                        }
                        clearPeers()
                        val generation = subscriptionGeneration
                        val docs = snapshot?.documents.orEmpty()
                        _invitations.value = docs.filter { it.getString("status") == "PENDING" && it.getString("recipientUid") == uid &&
                            (it.getTimestamp("expiresAt")?.toDate()?.time ?: 0) > System.currentTimeMillis() }.map {
                            CircleInvitation(id = it.id, senderUid = it.getString("senderUid").orEmpty(),
                                senderName = "Loading sender name…", status = "PENDING", shareMyLiveLocation = false)
                        }
                        _invitations.value.forEach { invite ->
                            db.collection("public_profiles").document(invite.senderUid).get().addOnSuccessListener { profile ->
                                if (auth.currentUser?.uid == uid && generation == subscriptionGeneration)
                                    _invitations.value = _invitations.value.map { if (it.id == invite.id)
                                        it.copy(senderName = profile.getString("name")?.takeIf(String::isNotBlank) ?: "Name not provided") else it }
                            }.addOnFailureListener { error ->
                                if (auth.currentUser?.uid == uid && generation == subscriptionGeneration) {
                                    _invitations.value = _invitations.value.map { if (it.id == invite.id) it.copy(senderName = "Profile unavailable") else it }
                                    _status.value = "Inviter profile could not be loaded. Reconnect before accepting."
                                    com.siddesh.smsalerts.utils.PrivacyLog.w("Circle", "INVITER_PROFILE_FAILED", error)
                                }
                            }
                        }
                        _members.value = docs.filter { it.getString("status") == "ACCEPTED" }.map { doc ->
                            val peer = if (doc.getString("senderUid") == uid) doc.getString("recipientUid").orEmpty() else doc.getString("senderUid").orEmpty()
                            relationIds[peer] = doc.id
                            doc.getTimestamp("createdAt")?.let { relationVersions[peer] = it }
                            TrustedCircleMember(id = peer, name = "Loading member name…", phoneNumber = "", relationship = "Accepted")
                        }
                        _members.value.forEach { peer -> subscribePeer(uid, peer.id) }
                        _status.value = if (snapshot?.metadata?.isFromCache == true)
                            "Offline: cached relationships. Changes require server confirmation."
                        else "Circle alerts require this app to be running and connected. Sharing expires after one hour."
                    }
            }
        }
    }
    private fun cloudError(e: FirebaseFirestoreException) = when (e.code) {
        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED -> "Cloud sync quota reached. Cloud changes are temporarily unavailable until quota resets."
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Code unavailable, expired, attempt limit reached, or permission denied. Change not confirmed."
        else -> "Circle sync unavailable (${e.code}). Check internet connectivity and security rules."
    }
    private fun clearPeers() {
        subscriptionGeneration++; grantGeneration.clear()
        peerListeners.forEach { it.remove() }; peerListeners.clear()
        locationListeners.values.forEach { it.remove() }; locationListeners.clear()
        incidentListeners.values.forEach { it.remove() }; incidentListeners.clear()
        relationVersions.clear(); relationIds.clear(); outgoingExpiry.clear(); incomingExpiry.clear()
    }
    private fun change(peer: String, transform: (TrustedCircleMember) -> TrustedCircleMember) {
        _members.value = _members.value.map { if (it.id == peer) transform(it) else it }
    }
    private fun subscribePeer(uid: String, peer: String) {
        val generation = subscriptionGeneration
        fun current() = auth.currentUser?.uid == uid && generation == subscriptionGeneration && relationIds.containsKey(peer)
        fun validGrant(doc: DocumentSnapshot?, expiry: Long) = doc?.getBoolean("shareEnabled") == true &&
            expiry > System.currentTimeMillis() && doc.getTimestamp("relationshipCreatedAt") == relationVersions[peer] &&
            relationVersions[peer] != null
        peerListeners += db.collection("public_profiles").document(peer).addSnapshotListener { doc, error ->
            if (current()) change(peer) { it.copy(name = if (error == null) doc?.getString("name").orEmpty().ifBlank { "Name not provided" } else "Profile unavailable", photoUrl = doc?.getString("photoUrl")) }
        }
        peerListeners += db.collection("users").document(uid).collection("circle_members").document(peer).addSnapshotListener { doc, _ ->
            if (!current()) return@addSnapshotListener
            val expiry = doc?.getTimestamp("expiresAt")?.toDate()?.time ?: 0L
            val enabled = validGrant(doc, expiry)
            outgoingExpiry[peer] = if (enabled) expiry else 0
            change(peer) { it.copy(amISharingLocationWithThem = enabled) }
            if (enabled && com.siddesh.smsalerts.SafeAlertApp.isVisible) LiveLocationManager.getInstance(context).startLiveSharing("circle")
        }
        peerListeners += db.collection("users").document(peer).collection("circle_members").document(uid).addSnapshotListener { doc, _ ->
            if (!current()) return@addSnapshotListener
            val grant = (grantGeneration[peer] ?: 0) + 1; grantGeneration[peer] = grant
            fun authorized() = current() && grantGeneration[peer] == grant && (incomingExpiry[peer] ?: 0) > System.currentTimeMillis()
            val expiry = doc?.getTimestamp("expiresAt")?.toDate()?.time ?: 0L
            val enabled = validGrant(doc, expiry)
            incomingExpiry[peer] = if (enabled) expiry else 0
            change(peer) { it.copy(isSharingLocationWithMe = enabled, lastLatitude = if (enabled) it.lastLatitude else null,
                lastLongitude = if (enabled) it.lastLongitude else null, activeIncidentId = if (enabled) it.activeIncidentId else null) }
            locationListeners.remove(peer)?.remove(); incidentListeners.remove(peer)?.remove()
            if (enabled) incidentListeners[peer] = db.collection("incidents").whereEqualTo("ownerUid", peer)
                .whereEqualTo("status", "ACTIVE").limit(20).addSnapshotListener(MetadataChanges.INCLUDE, incidentListener@ { incidents, error ->
                    if (!authorized()) return@incidentListener
                    val active = if (error == null) incidents?.documents?.firstOrNull()?.id else null
                    change(peer) { it.copy(activeIncidentId = active) }
                    if (error != null) _status.value = cloudError(error)
                    if (incidents?.metadata?.isFromCache == false && error == null)
                        incidents.documents.forEach { CircleNotificationWorker.enqueue(context, uid, it.id) }
                })
            if (enabled) locationListeners[peer] = db.collection("users").document(peer).collection("live_location").document("current")
                .addSnapshotListener locationListener@ { point, error ->
                    if (!authorized()) return@locationListener
                    if (error != null || point?.exists() != true) {
                        change(peer) { it.copy(lastLatitude = null, lastLongitude = null) }; return@locationListener
                    }
                    change(peer) { it.copy(lastLatitude = point.getDouble("latitude"), lastLongitude = point.getDouble("longitude"),
                        lastAccuracyMeters = point.getDouble("accuracy")?.toFloat(), lastLocationTimestamp = point.getLong("timestamp") ?: 0) }
                }
        }
    }
    fun hasActiveOutgoingSharing(): Boolean = outgoingExpiry.values.any { it > System.currentTimeMillis() }
    private fun action(key: String, success: String = "Change confirmed by server.", block: suspend () -> Unit) {
        if (!inFlight.add(key)) return
        _busy.value = true
        val owner = auth.currentUser?.uid
        scope.launch {
            try { withTimeout(15_000) { block() }; if (auth.currentUser?.uid == owner) _status.value = success }
            catch (e: Exception) {
                if (auth.currentUser?.uid == owner) _status.value = when (e) {
                    is FirebaseFirestoreException -> cloudError(e)
                    is IllegalStateException -> e.message ?: "Request unavailable."
                    else -> "Change not confirmed. Reconnect and retry; remote access ends when revocation is confirmed or the grant expires."
                }
            } finally { inFlight.remove(key); _busy.value = inFlight.isNotEmpty() }
        }
    }
    fun manageCode(operation: String) = action("code", if (operation == "revoke") "Invitation code revoked." else "Invitation code checked by server.") {
        val owner = auth.currentUser?.uid
        val result = repository.manage(operation)
        if (auth.currentUser?.uid == owner) _code.value = result
    }
    fun joinCode(input: String) {
        val code = input.replace(Regex("\\s"), "").uppercase(java.util.Locale.ROOT)
        if (!Regex("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}").matches(code)) {
            _status.value = "Enter the eight-character invitation code."; return
        }
        action("join", "Code validated. Tap Accept to connect; location sharing remains off.") { repository.join(code) }
    }
    fun acceptInvitation(invitation: CircleInvitation) = action(invitation.id) { repository.respond(invitation.id, "ACCEPTED") }
    fun rejectInvitation(inviteId: String) = action(inviteId) { repository.respond(inviteId, "REJECTED") }
    fun removeMember(memberId: String) {
        val relation = relationIds[memberId] ?: return
        outgoingExpiry.remove(memberId); incomingExpiry.remove(memberId)
        grantGeneration[memberId] = (grantGeneration[memberId] ?: 0) + 1
        locationListeners.remove(memberId)?.remove(); incidentListeners.remove(memberId)?.remove()
        change(memberId) { it.copy(amISharingLocationWithThem = false, isSharingLocationWithMe = false,
            lastLatitude = null, lastLongitude = null, activeIncidentId = null) }
        if (!hasActiveOutgoingSharing()) LiveLocationManager.getInstance(context).stopLiveSharing("circle")
        action(relation) { repository.respond(relation, "REVOKED") }
    }
    fun toggleBlockMember(memberId: String) = removeMember(memberId)
    fun toggleShareMyLiveLocation(memberId: String, enable: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val relation = relationIds[memberId] ?: return
        val version = relationVersions[memberId] ?: return
        if (!enable) {
            outgoingExpiry.remove(memberId); change(memberId) { it.copy(amISharingLocationWithThem = false) }
            if (!hasActiveOutgoingSharing()) LiveLocationManager.getInstance(context).stopLiveSharing("circle")
        }
        action("share-$memberId") {
            db.collection("users").document(uid).collection("circle_members").document(memberId).set(mapOf(
                "relationId" to relation, "relationshipCreatedAt" to version, "shareEnabled" to enable,
                "expiresAt" to Timestamp(java.util.Date(System.currentTimeMillis() + if (enable) 3600_000L else 0L)))).await()
            if (enable && auth.currentUser?.uid == uid) {
                outgoingExpiry[memberId] = System.currentTimeMillis() + 3600_000L
                LiveLocationManager.getInstance(context).startLiveSharing("circle")
            }
        }
    }
    fun stopAllSharing() { _members.value.forEach { toggleShareMyLiveLocation(it.id, false) } }
}
