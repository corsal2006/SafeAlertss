package com.siddesh.smsalerts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siddesh.smsalerts.data.model.Resource
import com.siddesh.smsalerts.data.model.UserProfile
import com.siddesh.smsalerts.data.repository.AuthRepository
import com.siddesh.smsalerts.data.repository.AuthState
import com.siddesh.smsalerts.utils.ValidationUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthDiagnosticInfo(
    val firebaseInitialized: Boolean = false,
    val firebaseAuthAvailable: Boolean = false,
    val webClientIdFound: Boolean = false,
    val webClientId: String = "",
    val currentUserUid: String? = null,
    val currentUserEmail: String? = null,
    val googleCredentialResult: String = "NOT_ATTEMPTED",
    val firebaseCredentialResult: String = "NOT_ATTEMPTED",
    val firebaseSignInResult: String = "NOT_ATTEMPTED",
    val lastErrorCategory: String? = null,
    val lastErrorMessage: String? = null,
    val configWarning: String? = null
)

sealed class AuthUiState {
    object Idle : AuthUiState()
    data class Loading(val message: String = "Processing...") : AuthUiState()
    data class Authenticated(val user: UserProfile) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
    data class EmailLinkSent(val message: String) : AuthUiState()
    data class PasswordResetSent(val message: String) : AuthUiState()
}

class AuthViewModel @JvmOverloads constructor(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // Diagnostics State
    private val _diagnostics = MutableStateFlow(AuthDiagnosticInfo())
    val diagnostics: StateFlow<AuthDiagnosticInfo> = _diagnostics.asStateFlow()

    init {
        refreshDiagnostics()
    }

    fun refreshDiagnostics(context: android.content.Context? = null) {
        val isFbInit = try { com.google.firebase.FirebaseApp.getInstance() != null } catch (e: Exception) { false }
        val auth = try { com.google.firebase.auth.FirebaseAuth.getInstance() } catch (e: Exception) { null }
        val user = auth?.currentUser
        val webId = try {
            if (context != null) {
                val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                if (resId != 0) context.getString(resId) else "663925742780-en3qinv0sd0hoooous15fn6begrduoko.apps.googleusercontent.com"
            } else {
                "663925742780-en3qinv0sd0hoooous15fn6begrduoko.apps.googleusercontent.com"
            }
        } catch (e: Exception) {
            "663925742780-en3qinv0sd0hoooous15fn6begrduoko.apps.googleusercontent.com"
        }

        _diagnostics.value = _diagnostics.value.copy(
            firebaseInitialized = isFbInit,
            firebaseAuthAvailable = auth != null,
            webClientIdFound = webId.isNotBlank(),
            webClientId = webId,
            currentUserUid = user?.uid,
            currentUserEmail = user?.email
        )
    }

    fun updateGoogleCredentialDiagnostic(
        category: String,
        result: String,
        errorMessage: String? = null,
        configWarning: String? = null
    ) {
        _diagnostics.value = _diagnostics.value.copy(
            googleCredentialResult = result,
            lastErrorCategory = category,
            lastErrorMessage = errorMessage,
            configWarning = configWarning
        )
    }

    fun updateFirebaseSignInDiagnostic(
        credResult: String,
        signInResult: String,
        category: String? = null,
        errorMessage: String? = null
    ) {
        _diagnostics.value = _diagnostics.value.copy(
            firebaseCredentialResult = credResult,
            firebaseSignInResult = signInResult,
            lastErrorCategory = category ?: _diagnostics.value.lastErrorCategory,
            lastErrorMessage = errorMessage ?: _diagnostics.value.lastErrorMessage
        )
    }

    // Real-time authentication state
    val authState: StateFlow<AuthState> = authRepository.authState

    val currentUserId: String?
        get() = authRepository.currentUserId

    fun isUserLoggedIn(): Boolean = authRepository.isUserLoggedIn()

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }

    fun setError(message: String) {
        _uiState.value = AuthUiState.Error(message)
    }

    // 1. Email + Password Login
    fun login(email: String, password: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter your email address.")
            return
        }
        if (!ValidationUtils.isValidEmail(trimmedEmail)) {
            _uiState.value = AuthUiState.Error("Please enter a valid email address.")
            return
        }
        if (password.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter your password.")
            return
        }
        if (password.length < 6) {
            _uiState.value = AuthUiState.Error("Password must be at least 6 characters.")
            return
        }

        _uiState.value = AuthUiState.Loading("Signing you in...")
        viewModelScope.launch {
            when (val result = authRepository.login(trimmedEmail, password)) {
                is Resource.Success -> {
                    _uiState.value = AuthUiState.Authenticated(result.data)
                }
                is Resource.Error -> {
                    _uiState.value = AuthUiState.Error(result.message)
                }
                is Resource.Loading -> {}
            }
        }
    }

    // 2. Email + Password Registration
    fun register(
        name: String,
        email: String,
        password: String,
        confirmPassword: String,
        phoneNumber: String
    ) {
        val trimmedName = name.trim()
        val trimmedEmail = email.trim()
        val trimmedPhone = phoneNumber.trim()

        if (trimmedName.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter your full name.")
            return
        }
        if (trimmedEmail.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter your email address.")
            return
        }
        if (!ValidationUtils.isValidEmail(trimmedEmail)) {
            _uiState.value = AuthUiState.Error("Please enter a valid email address.")
            return
        }
        if (trimmedPhone.isNotBlank()) {
            val digits = trimmedPhone.filter { it.isDigit() }
            if (digits.length < 7) {
                _uiState.value = AuthUiState.Error("Please enter a valid phone number (at least 7 digits).")
                return
            }
        }
        if (password.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter a password.")
            return
        }
        if (password.length < 6) {
            _uiState.value = AuthUiState.Error("Password must be at least 6 characters long.")
            return
        }
        if (password != confirmPassword) {
            _uiState.value = AuthUiState.Error("Passwords do not match.")
            return
        }

        _uiState.value = AuthUiState.Loading("Creating your account...")
        viewModelScope.launch {
            when (val result = authRepository.register(trimmedName, trimmedEmail, password, trimmedPhone)) {
                is Resource.Success -> {
                    _uiState.value = AuthUiState.Authenticated(result.data)
                }
                is Resource.Error -> {
                    _uiState.value = AuthUiState.Error(result.message)
                }
                is Resource.Loading -> {}
            }
        }
    }

    // 3. Passwordless Email Sign-In Link
    private var lastSentEmail: String? = null

    fun sendSignInLink(email: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter your email address.")
            return
        }
        if (!ValidationUtils.isValidEmail(trimmedEmail)) {
            _uiState.value = AuthUiState.Error("Please enter a valid email address.")
            return
        }

        lastSentEmail = trimmedEmail
        _uiState.value = AuthUiState.Loading("Sending sign-in link...")
        viewModelScope.launch {
            when (val result = authRepository.sendSignInLinkToEmail(trimmedEmail)) {
                is Resource.Success -> {
                    _uiState.value = AuthUiState.EmailLinkSent(
                        "Check your email ($trimmedEmail) for your secure passwordless sign-in link."
                    )
                }
                is Resource.Error -> _uiState.value = AuthUiState.Error(result.message)
                is Resource.Loading -> {}
            }
        }
    }

    fun isSignInWithEmailLink(link: String): Boolean = authRepository.isSignInWithEmailLink(link)

    fun completeSignInWithEmailLink(link: String) {
        val targetEmail = lastSentEmail
        if (targetEmail.isNullOrBlank()) {
            _uiState.value = AuthUiState.Error("Please confirm your email address to complete passwordless sign-in.")
            return
        }
        completeSignInWithEmailLink(targetEmail, link)
    }

    fun completeSignInWithEmailLink(email: String, link: String) {
        _uiState.value = AuthUiState.Loading("Verifying sign-in link...")
        viewModelScope.launch {
            when (val result = authRepository.signInWithEmailLink(email.trim(), link)) {
                is Resource.Success -> _uiState.value = AuthUiState.Authenticated(result.data)
                is Resource.Error -> _uiState.value = AuthUiState.Error(result.message)
                is Resource.Loading -> {}
            }
        }
    }

    // 4. Google Sign-In
    fun signInWithGoogle(idToken: String) {
        _uiState.value = AuthUiState.Loading("Authenticating with Google...")
        updateFirebaseSignInDiagnostic(
            credResult = "GOOGLE_ID_TOKEN_ACQUIRED",
            signInResult = "SIGNING_IN_TO_FIREBASE"
        )
        viewModelScope.launch {
            when (val result = authRepository.signInWithGoogleCredential(idToken)) {
                is Resource.Success -> {
                    updateFirebaseSignInDiagnostic(
                        credResult = "FIREBASE_CREDENTIAL_CREATED",
                        signInResult = "SUCCESS"
                    )
                    refreshDiagnostics()
                    _uiState.value = AuthUiState.Authenticated(result.data)
                }
                is Resource.Error -> {
                    updateFirebaseSignInDiagnostic(
                        credResult = "FIREBASE_CREDENTIAL_FAILED",
                        signInResult = "FAILED: ${result.message}",
                        category = "FIREBASE_AUTH_ERROR",
                        errorMessage = result.message
                    )
                    _uiState.value = AuthUiState.Error(result.message)
                }
                is Resource.Loading -> {}
            }
        }
    }

    // 5. Password Reset
    fun forgotPassword(email: String) {
        val trimmedEmail = email.trim()
        if (!ValidationUtils.isValidEmail(trimmedEmail)) {
            _uiState.value = AuthUiState.Error("Please enter your registered email address.")
            return
        }

        _uiState.value = AuthUiState.Loading("Sending reset instructions...")
        viewModelScope.launch {
            when (val result = authRepository.forgotPassword(trimmedEmail)) {
                is Resource.Success -> {
                    _uiState.value = AuthUiState.PasswordResetSent(
                        "Password reset instructions sent to $trimmedEmail. Please check your inbox."
                    )
                }
                is Resource.Error -> _uiState.value = AuthUiState.Error(result.message)
                is Resource.Loading -> {}
            }
        }
    }

    fun logout() {
        authRepository.logout()
        _uiState.value = AuthUiState.Idle
    }
}
