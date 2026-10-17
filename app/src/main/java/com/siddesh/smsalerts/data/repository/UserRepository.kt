package com.siddesh.smsalerts.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.siddesh.smsalerts.data.model.Resource
import com.siddesh.smsalerts.data.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "SafeAlerts/UserRepo"

/**
 * Single source of truth for the active UserProfile.
 * Automatically tracks FirebaseAuth state and maintains a real-time Firestore listener on `users/{uid}`.
 * Survives process restarts and ensures profile name is never lost or replaced with "User" / "Guest".
 */
class UserRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        @Volatile
        private var INSTANCE: UserRepository? = null

        fun getInstance(
            auth: FirebaseAuth = FirebaseAuth.getInstance(),
            firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
        ): UserRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserRepository(auth, firestore).also { INSTANCE = it }
            }
        }
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private var profileListener: ListenerRegistration? = null

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            Log.d(TAG, "AUTH_STATE_CHANGE: uid = ${user?.uid ?: "SIGNED_OUT"}")
            if (user != null) {
                listenToUserProfile(user)
            } else {
                detachListener()
                _userProfile.value = null
            }
        }
    }

    private fun detachListener() {
        try {
            profileListener?.remove()
        } catch (e: Exception) {
            Log.w(TAG, "Error detaching profile listener: ${e.message}")
        } finally {
            profileListener = null
        }
    }

    private fun listenToUserProfile(user: FirebaseUser) {
        detachListener()

        // 1. Immediately provide cached in-memory profile from FirebaseUser so UI never flashes "User" or "Guest"
        val initialName = user.displayName?.takeIf { it.isNotBlank() } ?: "User"
        if (_userProfile.value == null || _userProfile.value?.uid != user.uid) {
            _userProfile.value = UserProfile(
                uid = user.uid,
                name = initialName,
                fullName = initialName,
                email = user.email.orEmpty(),
                phoneNumber = user.phoneNumber.orEmpty(),
                photoUrl = user.photoUrl?.toString(),
                authProvider = if (user.providerData.any { it.providerId == "google.com" }) "google" else "email_password"
            )
        }

        // 2. Attach real-time snapshot listener to Firestore users/{uid}
        try {
            val docRef = firestore.collection("users").document(user.uid)
            profileListener = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Firestore profile listener error: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val fullName = snapshot.getString("fullName")
                        ?: snapshot.getString("name")
                        ?: user.displayName
                        ?: "User"
                    val email = snapshot.getString("email") ?: user.email.orEmpty()
                    val phone = snapshot.getString("phoneNumber") ?: user.phoneNumber.orEmpty()
                    val photo = snapshot.getString("photoUrl") ?: user.photoUrl?.toString()
                    val provider = snapshot.getString("provider")
                        ?: snapshot.getString("authProvider")
                        ?: "email_password"
                    val createdAt = snapshot.getLong("createdAt") ?: System.currentTimeMillis()
                    val updatedAt = snapshot.getLong("updatedAt") ?: System.currentTimeMillis()

                    val profile = UserProfile(
                        uid = user.uid,
                        name = fullName,
                        fullName = fullName,
                        email = email,
                        phoneNumber = phone,
                        photoUrl = photo,
                        authProvider = provider,
                        provider = provider,
                        createdAt = createdAt,
                        updatedAt = updatedAt
                    )
                    Log.d(TAG, "PROFILE_LOADED: name='$fullName', uid='${user.uid}'")
                    _userProfile.value = profile
                } else if (snapshot != null && !snapshot.exists()) {
                    // Profile document does not exist yet; auto-create it from FirebaseUser
                    repositoryScope.launch {
                        syncProfile(
                            user = user,
                            fallbackName = user.displayName.orEmpty().ifBlank { "User" },
                            fallbackEmail = user.email.orEmpty(),
                            provider = if (user.providerData.any { it.providerId == "google.com" }) "google" else "email_password"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach profile listener: ${e.message}", e)
        }
    }

    suspend fun syncProfile(
        user: FirebaseUser,
        fallbackName: String,
        fallbackEmail: String,
        provider: String
    ): UserProfile {
        val name = when {
            user.displayName?.isNotBlank() == true -> user.displayName!!.trim()
            fallbackName.isNotBlank() -> fallbackName.trim()
            else -> "User"
        }
        val email = user.email?.takeIf { it.isNotBlank() } ?: fallbackEmail.trim()
        val photo = user.photoUrl?.toString()
        val phone = user.phoneNumber.orEmpty()

        val profile = UserProfile(
            uid = user.uid,
            name = name,
            fullName = name,
            email = email,
            phoneNumber = phone,
            photoUrl = photo,
            authProvider = provider,
            provider = provider,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        // Optimistically update memory so Home Screen sees it instantly
        _userProfile.value = profile

        // Persist to Firestore
        try {
            withTimeoutOrNull(5000L) {
                val docRef = firestore.collection("users").document(user.uid)
                docRef.set(profile.toMap(), SetOptions.merge()).await()
                Log.d(TAG, "PROFILE_CREATED: Firestore document written for ${user.uid}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing profile to Firestore: ${e.message}", e)
        }

        return profile
    }

    suspend fun updateProfile(name: String, phoneNumber: String): Resource<Unit> {
        val user = auth.currentUser ?: return Resource.Error("User not signed in.")
        val trimmedName = name.trim()
        val trimmedPhone = phoneNumber.trim()

        if (trimmedName.isBlank()) {
            return Resource.Error("Name cannot be empty.")
        }

        return try {
            val updates = mapOf(
                "fullName" to trimmedName,
                "name" to trimmedName,
                "phoneNumber" to trimmedPhone,
                "updatedAt" to System.currentTimeMillis()
            )

            withTimeoutOrNull(5000L) {
                firestore.collection("users").document(user.uid).set(updates, SetOptions.merge()).await()
            }

            try {
                user.updateProfile(userProfileChangeRequest {
                    displayName = trimmedName
                }).await()
            } catch (e: Exception) {
                Log.w(TAG, "Could not update FirebaseUser display name: ${e.message}")
            }

            _userProfile.value = _userProfile.value?.copy(
                name = trimmedName,
                fullName = trimmedName,
                phoneNumber = trimmedPhone,
                updatedAt = System.currentTimeMillis()
            )

            Resource.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update profile: ${e.message}", e)
            Resource.Error(e.localizedMessage ?: "Failed to update profile", e)
        }
    }
}
