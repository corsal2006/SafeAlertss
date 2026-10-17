package com.siddesh.smsalerts.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.siddesh.smsalerts.data.model.EmergencyContact
import com.siddesh.smsalerts.data.model.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val TAG = "SafeAlerts/Contacts"

class ContactsRepository {

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    /**
     * Listens to trusted contacts for [userId].
     * Supports both "trustedContacts" (primary) and "emergencyContacts" (legacy/fallback) collections.
     */
    fun getContactsFlow(userId: String): Flow<List<EmergencyContact>> = callbackFlow {
        val localCached = LocalContactsCache.getContacts()
        if (localCached.isNotEmpty()) {
            trySend(localCached)
        }

        if (userId.isBlank()) {
            if (localCached.isEmpty()) {
                trySend(emptyList())
            }
            awaitClose { }
            return@callbackFlow
        }

        var emergencyContactsListener: ListenerRegistration? = null
        val primaryRef = firestore.collection("users")
            .document(userId)
            .collection("trustedContacts")
            .orderBy("createdAt", Query.Direction.ASCENDING)

        val primaryListener = primaryRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Primary trustedContacts listener error: ${error.message} (code=${error.code})")
                val fallbackList = LocalContactsCache.getContacts()
                if (fallbackList.isNotEmpty()) {
                    trySend(fallbackList)
                }
            }

            val primaryList = snapshot?.documents?.mapNotNull { doc ->
                mapDocumentToContact(doc)
            } ?: emptyList()

            if (primaryList.isNotEmpty()) {
                LocalContactsCache.saveContacts(primaryList)
                trySend(primaryList)
            } else if (error == null) {
                trySend(primaryList)
            }

            // If primary is empty or permission denied, listen to legacy emergencyContacts as fallback
            if (primaryList.isEmpty() && emergencyContactsListener == null) {
                val legacyRef = firestore.collection("users")
                    .document(userId)
                    .collection("emergencyContacts")
                    .orderBy("createdAt", Query.Direction.ASCENDING)

                emergencyContactsListener = legacyRef.addSnapshotListener { legSnapshot, legError ->
                    if (legError != null) {
                        Log.w(TAG, "Legacy emergencyContacts listener error: ${legError.message}")
                        return@addSnapshotListener
                    }
                    val legacyList = legSnapshot?.documents?.mapNotNull { doc ->
                        mapDocumentToContact(doc)
                    } ?: emptyList()
                    if (legacyList.isNotEmpty()) {
                        Log.d(TAG, "Loaded ${legacyList.size} contacts from legacy emergencyContacts collection")
                        LocalContactsCache.saveContacts(legacyList)
                        trySend(legacyList)
                    }
                }
            }
        }

        awaitClose {
            primaryListener.remove()
            emergencyContactsListener?.remove()
        }
    }

    private fun mapDocumentToContact(doc: com.google.firebase.firestore.DocumentSnapshot): EmergencyContact {
        return EmergencyContact(
            id = doc.id,
            name = doc.getString("name") ?: "",
            phoneNumber = doc.getString("phoneNumber") ?: "",
            relationship = doc.getString("relationship") ?: "Emergency Contact",
            isActive = doc.getBoolean("enabled") ?: doc.getBoolean("isActive") ?: true,
            enabled = doc.getBoolean("enabled") ?: doc.getBoolean("isActive") ?: true,
            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
        )
    }

    suspend fun addContact(userId: String, contact: EmergencyContact): Resource<String> {
        val contactId = if (contact.id.isNotBlank()) contact.id else java.util.UUID.randomUUID().toString()
        val finalContact = contact.copy(
            id = contactId,
            createdAt = if (contact.createdAt > 0) contact.createdAt else System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        LocalContactsCache.addOrUpdateContact(finalContact)

        val currentUser = auth.currentUser
        if (currentUser == null || currentUser.uid.isBlank()) {
            Log.i(TAG, "Offline contact added locally (id=$contactId)")
            return Resource.Success(contactId)
        }

        val authenticatedUid = currentUser.uid
        val contactData = finalContact.toMap()
        val primaryPath = "users/$authenticatedUid/trustedContacts/$contactId"

        Log.d(TAG, "CONTACT_SAVE_ATTEMPT: writing to primary path: $primaryPath")

        return try {
            // 1. Attempt write to primary path: users/{uid}/trustedContacts/{id}
            firestore.collection("users")
                .document(authenticatedUid)
                .collection("trustedContacts")
                .document(contactId)
                .set(contactData)
                .await()

            Log.i(TAG, "CONTACT_SAVE_SUCCESS: successfully saved to $primaryPath")

            // Mirror to legacy collection in background for full backwards compatibility
            try {
                firestore.collection("users")
                    .document(authenticatedUid)
                    .collection("emergencyContacts")
                    .document(contactId)
                    .set(contactData)
            } catch (_: Exception) {}

            Resource.Success(contactId)
        } catch (e: Exception) {
            val isPermissionDenied = e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
            Log.w(TAG, "CONTACT_SAVE_PRIMARY_FAILED path=$primaryPath, error=${e.message}, isPermissionDenied=$isPermissionDenied")

            if (isPermissionDenied) {
                // Attempt fallback to legacy emergencyContacts path
                val legacyPath = "users/$authenticatedUid/emergencyContacts/$contactId"
                Log.d(TAG, "CONTACT_SAVE_FALLBACK: attempting legacy path: $legacyPath")
                try {
                    firestore.collection("users")
                        .document(authenticatedUid)
                        .collection("emergencyContacts")
                        .document(contactId)
                        .set(contactData)
                        .await()

                    Log.i(TAG, "CONTACT_SAVE_SUCCESS: successfully saved via fallback path $legacyPath")
                    return Resource.Success(contactId)
                } catch (fallbackEx: Exception) {
                    Log.e(TAG, "CONTACT_SAVE_FALLBACK_FAILED path=$legacyPath, error=${fallbackEx.message}")
                }
            }

            // Classify error category cleanly as per requirement C4
            val category = when {
                e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "PERMISSION_DENIED: Missing or insufficient permissions. Update Firestore Rules in Firebase Console to allow 'trustedContacts'."
                e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE ->
                    "NETWORK_ERROR: Firestore service is currently unavailable. Check your internet connection."
                e is IllegalArgumentException ->
                    "INVALID_ARGUMENT: ${e.localizedMessage}"
                else ->
                    "UNKNOWN_ERROR: ${e.localizedMessage ?: "Failed to save trusted contact."}"
            }

            Log.e(TAG, "CONTACT_SAVE_ERROR category=$category, uid=$authenticatedUid, targetPath=$primaryPath", e)
            Resource.Error(category, e)
        }
    }

    suspend fun updateContact(userId: String, contact: EmergencyContact): Resource<Unit> {
        val currentUser = auth.currentUser
        if (currentUser == null || currentUser.uid.isBlank()) {
            return Resource.Error("AUTH_REQUIRED: Please sign in before updating contacts.")
        }
        val authenticatedUid = currentUser.uid
        val updated = contact.copy(updatedAt = System.currentTimeMillis())
        val data = updated.toMap()

        return try {
            firestore.collection("users")
                .document(authenticatedUid)
                .collection("trustedContacts")
                .document(contact.id)
                .set(data)
                .await()

            try {
                firestore.collection("users")
                    .document(authenticatedUid)
                    .collection("emergencyContacts")
                    .document(contact.id)
                    .set(data)
            } catch (_: Exception) {}

            Resource.Success(Unit)
        } catch (e: Exception) {
            if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                try {
                    firestore.collection("users")
                        .document(authenticatedUid)
                        .collection("emergencyContacts")
                        .document(contact.id)
                        .set(data)
                        .await()
                    return Resource.Success(Unit)
                } catch (_: Exception) {}
            }
            Resource.Error(e.localizedMessage ?: "Failed to update emergency contact.", e)
        }
    }

    suspend fun deleteContact(userId: String, contactId: String): Resource<Unit> {
        val currentUser = auth.currentUser
        if (currentUser == null || currentUser.uid.isBlank()) {
            return Resource.Error("AUTH_REQUIRED: Please sign in before deleting contacts.")
        }
        val authenticatedUid = currentUser.uid

        return try {
            firestore.collection("users")
                .document(authenticatedUid)
                .collection("trustedContacts")
                .document(contactId)
                .delete()
                .await()

            try {
                firestore.collection("users")
                    .document(authenticatedUid)
                    .collection("emergencyContacts")
                    .document(contactId)
                    .delete()
            } catch (_: Exception) {}

            Resource.Success(Unit)
        } catch (e: Exception) {
            if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                try {
                    firestore.collection("users")
                        .document(authenticatedUid)
                        .collection("emergencyContacts")
                        .document(contactId)
                        .delete()
                        .await()
                    return Resource.Success(Unit)
                } catch (_: Exception) {}
            }
            Resource.Error(e.localizedMessage ?: "Failed to delete emergency contact.", e)
        }
    }
}
