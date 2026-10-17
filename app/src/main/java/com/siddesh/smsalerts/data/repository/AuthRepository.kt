package com.siddesh.smsalerts.data.repository

import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.siddesh.smsalerts.data.model.Resource
import com.siddesh.smsalerts.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "AUTH_DIAGNOSTIC"

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: FirebaseUser) : AuthState
    data class Error(val message: String) : AuthState
}

class AuthRepository {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private val _authState = MutableStateFlow<AuthState>(
        auth.currentUser?.let { AuthState.SignedIn(it) } ?: AuthState.SignedOut
    )
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            Log.d(TAG, "AUTH_STATE_CHANGED: user = ${user?.uid ?: "SIGNED_OUT"}")
            _authState.value = if (user != null) {
                AuthState.SignedIn(user)
            } else {
                AuthState.SignedOut
            }
        }
    }

    val currentUser: FirebaseUser?
        get() = try {
            auth.currentUser
        } catch (e: Exception) {
            Log.e(TAG, "ERROR_READING_CURRENT_USER: ${e.message}", e)
            null
        }

    val currentUserId: String?
        get() = currentUser?.uid

    fun isUserLoggedIn(): Boolean = currentUser != null

    // 1. Email + Password Login
    suspend fun login(email: String, password: String): Resource<UserProfile> {
        Log.d(TAG, "LOGIN_STARTED")

        return try {
            val authResult = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = authResult.user
                ?: run {
                    Log.e(TAG, "LOGIN_FAILED code=NULL_USER message=No user object returned")
                    return Resource.Error("Authentication succeeded but no user account returned.")
                }

            Log.d(TAG, "LOGIN_SUCCESS uid=${user.uid}")

            // Synchronize Firestore profile without blocking authentication
            val profile = syncUserProfileAfterAuth(
                user = user,
                fallbackEmail = email.trim(),
                fallbackName = user.displayName ?: "User",
                authProvider = "email_password"
            )

            Resource.Success(profile)
        } catch (e: Throwable) {
            val errorCode = if (e is FirebaseAuthException) e.errorCode else e.javaClass.simpleName
            Log.e(TAG, "LOGIN_FAILED code=$errorCode message=${e.message}", e)
            Resource.Error(mapFirebaseError(e), e)
        }
    }

    // 2. Email + Password Registration
    suspend fun register(
        name: String,
        email: String,
        password: String,
        phoneNumber: String
    ): Resource<UserProfile> {
        Log.d(TAG, "SIGNUP_STARTED")

        return try {
            val authResult = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = authResult.user
                ?: run {
                    Log.e(TAG, "SIGNUP_FAILED code=NULL_USER message=No user returned")
                    return Resource.Error("Registration succeeded but user account is null.")
                }

            Log.d(TAG, "SIGNUP_SUCCESS uid=${user.uid}")

            // Update display name on Firebase Auth profile
            try {
                val profileChange = userProfileChangeRequest {
                    displayName = name.trim()
                }
                user.updateProfile(profileChange).await()
            } catch (e: Exception) {
                Log.w(TAG, "SIGNUP_UPDATE_PROFILE_WARNING: ${e.message}")
            }

            val newProfile = userRepository.syncProfile(
                user = user,
                fallbackName = name.trim(),
                fallbackEmail = email.trim(),
                provider = "email_password"
            ).copy(phoneNumber = phoneNumber.trim())

            Resource.Success(newProfile)
        } catch (e: Throwable) {
            val errorCode = if (e is FirebaseAuthException) e.errorCode else e.javaClass.simpleName
            Log.e(TAG, "SIGNUP_FAILED code=$errorCode message=${e.message}", e)
            Resource.Error(mapFirebaseError(e), e)
        }
    }

    // 3. Passwordless Email Sign-In Link
    suspend fun sendSignInLinkToEmail(email: String): Resource<Unit> {
        Log.d(TAG, "EMAIL_LINK_SEND_STARTED")

        return try {
            val actionCodeSettings = ActionCodeSettings.newBuilder()
                .setUrl("https://smselerts.firebaseapp.com/login")
                .setHandleCodeInApp(true)
                .setAndroidPackageName(
                    "com.siddesh.smsalerts",
                    true,
                    "1"
                )
                .build()

            auth.sendSignInLinkToEmail(email.trim(), actionCodeSettings).await()
            Log.d(TAG, "EMAIL_LINK_SEND_SUCCESS")
            Resource.Success(Unit)
        } catch (e: Throwable) {
            val errorCode = if (e is FirebaseAuthException) e.errorCode else e.javaClass.simpleName
            Log.e(TAG, "EMAIL_LINK_SEND_FAILED code=$errorCode: ${e.message}", e)
            Resource.Error(mapFirebaseError(e), e)
        }
    }

    fun isSignInWithEmailLink(link: String): Boolean {
        return try {
            auth.isSignInWithEmailLink(link)
        } catch (e: Exception) {
            Log.e(TAG, "EMAIL_LINK_VERIFY_ERROR: ${e.message}", e)
            false
        }
    }

    suspend fun signInWithEmailLink(email: String, link: String): Resource<UserProfile> {
        Log.d(TAG, "EMAIL_LINK_AUTH_STARTED")
        return try {
            val authResult = auth.signInWithEmailLink(email.trim(), link).await()
            val user = authResult.user
                ?: run {
                    Log.e(TAG, "EMAIL_LINK_AUTH_FAILED: user is null")
                    return Resource.Error("Failed to authenticate with email link.")
                }

            Log.d(TAG, "EMAIL_LINK_AUTH_SUCCESS uid=${user.uid}")

            val profile = syncUserProfileAfterAuth(
                user = user,
                fallbackEmail = email.trim(),
                fallbackName = user.displayName ?: "User",
                authProvider = "email_link"
            )

            Resource.Success(profile)
        } catch (e: Throwable) {
            val errorCode = if (e is FirebaseAuthException) e.errorCode else e.javaClass.simpleName
            Log.e(TAG, "EMAIL_LINK_AUTH_FAILED code=$errorCode: ${e.message}", e)
            Resource.Error(mapFirebaseError(e), e)
        }
    }

    // 4. Google Sign-In with ID Token
    suspend fun signInWithGoogleCredential(idToken: String): Resource<UserProfile> {
        Log.d("SafeAlerts/GoogleAuth", "GOOGLE_FIREBASE_AUTH_STARTED")
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user
                ?: run {
                    Log.e("SafeAlerts/GoogleAuth", "GOOGLE_FIREBASE_AUTH_FAILED: user is null after signInWithCredential")
                    return Resource.Error("Google authentication succeeded but user is null.")
                }

            Log.d("SafeAlerts/GoogleAuth", "GOOGLE_FIREBASE_AUTH_SUCCESS: uid=${user.uid}")

            val displayName = user.displayName?.ifBlank { "Google User" } ?: "Google User"
            val email = user.email ?: ""

            val profile = syncUserProfileAfterAuth(
                user = user,
                fallbackEmail = email,
                fallbackName = displayName,
                authProvider = "google"
            )

            Resource.Success(profile)
        } catch (e: Throwable) {
            val errorCode = if (e is FirebaseAuthException) e.errorCode else e.javaClass.simpleName
            Log.e("GoogleSignIn", "Google Sign-In failed [FirebaseAuth]: class=${e.javaClass.name}, code=$errorCode, message=${e.message}, cause=${e.cause}", e)
            val errorMessage = when (e) {
                is FirebaseNetworkException -> "Network error. Check your internet connection."
                is FirebaseAuthException -> "Firebase Authentication Error ($errorCode): ${e.localizedMessage ?: e.message}"
                else -> mapFirebaseError(e)
            }
            Resource.Error(errorMessage, e)
        }
    }

    // 5. Password Reset
    suspend fun forgotPassword(email: String): Resource<Unit> {
        Log.d(TAG, "PASSWORD_RESET_STARTED")
        return try {
            auth.sendPasswordResetEmail(email.trim()).await()
            Log.d(TAG, "PASSWORD_RESET_SUCCESS")
            Resource.Success(Unit)
        } catch (e: Throwable) {
            val errorCode = if (e is FirebaseAuthException) e.errorCode else e.javaClass.simpleName
            Log.e(TAG, "PASSWORD_RESET_FAILED code=$errorCode: ${e.message}", e)
            Resource.Error(mapFirebaseError(e), e)
        }
    }

    // 6. User Profile Fetch
    suspend fun getUserProfile(userId: String): Resource<UserProfile> {
        val cached = userRepository.userProfile.value
        if (cached != null && cached.uid == userId && cached.name.isNotBlank()) {
            return Resource.Success(cached)
        }
        return try {
            val doc = withTimeoutOrNull(4000L) {
                firestore.collection("users").document(userId).get().await()
            }
            if (doc != null && doc.exists()) {
                val profile = UserProfile(
                    uid = userId,
                    name = doc.getString("fullName") ?: doc.getString("name") ?: "",
                    fullName = doc.getString("fullName") ?: doc.getString("name") ?: "",
                    email = doc.getString("email") ?: "",
                    phoneNumber = doc.getString("phoneNumber") ?: "",
                    photoUrl = doc.getString("photoUrl"),
                    authProvider = doc.getString("provider") ?: doc.getString("authProvider") ?: "email_password",
                    provider = doc.getString("provider") ?: doc.getString("authProvider") ?: "email_password",
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                )
                Resource.Success(profile)
            } else {
                val currentUser = auth.currentUser
                if (currentUser != null && currentUser.uid == userId) {
                    val fallback = UserProfile(
                        uid = userId,
                        name = currentUser.displayName ?: "User",
                        fullName = currentUser.displayName ?: "User",
                        email = currentUser.email ?: "",
                        phoneNumber = currentUser.phoneNumber ?: "",
                        photoUrl = currentUser.photoUrl?.toString(),
                        authProvider = "email_password",
                        provider = "email_password"
                    )
                    Resource.Success(fallback)
                } else {
                    Resource.Error("User profile document not found.")
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "PROFILE_FETCH_WARNING: ${e.message}")
            Resource.Error(mapFirebaseError(e), e)
        }
    }

    // 7. Profile Update
    suspend fun updateUserProfile(name: String, phoneNumber: String): Resource<Unit> {
        return userRepository.updateProfile(name, phoneNumber)
    }

    fun logout() {
        try {
            Log.d(TAG, "AUTH_LOGOUT")
            auth.signOut()
        } catch (e: Exception) {
            Log.e(TAG, "LOGOUT_ERROR: ${e.message}", e)
        }
    }

    private val userRepository: UserRepository by lazy { UserRepository.getInstance(auth, firestore) }

    private suspend fun syncUserProfileAfterAuth(
        user: FirebaseUser,
        fallbackEmail: String,
        fallbackName: String,
        authProvider: String
    ): UserProfile {
        return userRepository.syncProfile(
            user = user,
            fallbackName = fallbackName,
            fallbackEmail = fallbackEmail,
            provider = authProvider
        )
    }

    fun mapFirebaseError(throwable: Throwable): String {
        return when (throwable) {
            is FirebaseAuthInvalidCredentialsException -> {
                when (throwable.errorCode) {
                    "ERROR_INVALID_EMAIL" -> "The email address is improperly formatted."
                    "ERROR_WRONG_PASSWORD" -> "Incorrect password. Please verify your credentials."
                    else -> throwable.localizedMessage ?: "Invalid credentials. Please verify your email and password."
                }
            }
            is FirebaseAuthInvalidUserException -> {
                when (throwable.errorCode) {
                    "ERROR_USER_NOT_FOUND" -> "No account found with this email. Please register first."
                    "ERROR_USER_DISABLED" -> "This user account has been disabled by administration."
                    else -> "Account does not exist or has been disabled."
                }
            }
            is FirebaseAuthUserCollisionException -> {
                "An account with this email address already exists. Please sign in or use another provider."
            }
            is FirebaseNetworkException -> {
                "Network connection error. Please verify your cellular data or Wi-Fi connectivity."
            }
            is FirebaseAuthException -> {
                when (throwable.errorCode) {
                    "ERROR_TOO_MANY_REQUESTS" -> "Too many unsuccessful requests. Please wait a few moments before trying again."
                    "ERROR_OPERATION_NOT_ALLOWED" -> "This sign-in method is currently disabled in Firebase Console. Please enable it in Authentication -> Sign-in method."
                    "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> "An account already exists with the same email using a different sign-in method."
                    else -> throwable.localizedMessage ?: "Authentication failed (${throwable.errorCode})."
                }
            }
            is FirebaseFirestoreException -> {
                "Firestore database error: ${throwable.localizedMessage ?: throwable.code.name}"
            }
            else -> throwable.localizedMessage ?: "An unexpected error occurred. Please try again."
        }
    }
}
