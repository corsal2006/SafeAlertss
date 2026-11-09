package com.siddesh.smsalerts.service

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.siddesh.smsalerts.mesh.*
import com.siddesh.smsalerts.data.repository.GroupRepository
import kotlinx.coroutines.*

/** Keeps revocation/key rotation effective even while another screen is visible. Offline caches cannot prove revocation. */
class GroupSessionManager private constructor(private val context: Context) {
    companion object {
        @Volatile private var instance: GroupSessionManager? = null
        fun start(context: Context) { if (instance == null) synchronized(this) {
            if (instance == null) instance = GroupSessionManager(context.applicationContext)
        } }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("mesh_v2", 0)
    private var listener: ListenerRegistration? = null
    private var generation = 0
    private var rotating = false
    private val change = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key in setOf("cloudGroupId", "owner")) observe() }
    init { prefs.registerOnSharedPreferenceChangeListener(change); FirebaseAuth.getInstance().addAuthStateListener { observe() } }
    @Synchronized private fun observe() {
        listener?.remove(); listener = null
        val run = ++generation
        val owner = FirebaseAuth.getInstance().currentUser?.uid
        val groupId = MeshSecurity.cloudGroupId(context)
        BleMeshManager.getInstance(context).stopMesh()
        if (owner == null || groupId.isBlank()) return
        listener = FirebaseFirestore.getInstance().document("groups/$groupId").addSnapshotListener(MetadataChanges.INCLUDE) { doc, error ->
            if (run != generation || FirebaseAuth.getInstance().currentUser?.uid != owner) return@addSnapshotListener
            if (error?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED || (doc != null && !doc.metadata.isFromCache &&
                    (!doc.exists() || owner !in (doc.get("memberUids") as? List<*>).orEmpty()))) {
                BleMeshManager.getInstance(context).stopMesh(); MeshSecurity.clearGroup(context)
                return@addSnapshotListener
            }
            if (doc?.getBoolean("needsRotation") == true) {
                BleMeshManager.getInstance(context).stopMesh()
                if (doc.getString("ownerUid") == owner && !rotating) {
                    rotating = true
                    scope.launch { try { GroupRepository(context).rotateIfNeeded(groupId) }
                        catch (e: Exception) { com.siddesh.smsalerts.utils.PrivacyLog.w("GroupSession", "Key rotation unconfirmed", e) }
                        finally { rotating = false } }
                }
            } else if (doc?.exists() == true && doc.getString("meshKey") != MeshSecurity.groupCode(context)) {
                try { GroupRepository(context).activate(doc) }
                catch (e: Exception) { com.siddesh.smsalerts.utils.PrivacyLog.w("GroupSession", "Group activation failed", e) }
            }
        }
    }
}
