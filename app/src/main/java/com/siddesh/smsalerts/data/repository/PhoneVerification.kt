package com.siddesh.smsalerts.data.repository

import android.os.SystemClock
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

data class PhoneVerificationState(
    val busy: Boolean = false, val verifiedPhone: String = "", val message: String = "",
    val resendAt: Long = 0, val emailVerified: Boolean = false, val email: String = ""
)

/** Spark supports email verification. No executable SMS OTP request path exists in this build. */
class PhoneVerification(private val scope: CoroutineScope) {
    private val auth = FirebaseAuth.getInstance()
    private val _state = MutableStateFlow(PhoneVerificationState())
    val state = _state.asStateFlow()
    private var owner: String? = null
    init { refreshAccount() }
    fun refreshAccount() {
        val user = auth.currentUser
        if (owner != user?.uid) { owner = user?.uid; _state.value = PhoneVerificationState() }
        _state.value = _state.value.copy(verifiedPhone = user?.phoneNumber.orEmpty(), email = user?.email.orEmpty(), emailVerified = user?.isEmailVerified == true)
    }
    fun sendEmailVerification() {
        refreshAccount()
        val user = auth.currentUser ?: return
        if (_state.value.busy || user.isEmailVerified || user.email.isNullOrBlank() || SystemClock.elapsedRealtime() < _state.value.resendAt) return
        _state.value = _state.value.copy(busy = true, resendAt = SystemClock.elapsedRealtime() + 60_000)
        scope.launch {
            try {
                withTimeout(15_000) { user.sendEmailVerification().await() }
                if (auth.currentUser?.uid == user.uid) _state.value = _state.value.copy(message = "Verification email requested. Open its link, then tap Refresh verification. This does not verify your phone.")
            } catch (_: Exception) {
                if (auth.currentUser?.uid == user.uid) _state.value = _state.value.copy(message = "Email request not confirmed. Check internet or try later; Firebase free email limits apply.")
            } finally { if (auth.currentUser?.uid == user.uid) _state.value = _state.value.copy(busy = false) }
        }
    }
    fun reloadVerification() {
        val user = auth.currentUser ?: return
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true)
        scope.launch {
            try {
                withTimeout(15_000) { user.reload().await() }
                if (auth.currentUser?.uid == user.uid) { refreshAccount(); _state.value = _state.value.copy(message = "Account verification refreshed.") }
            } catch (_: Exception) {
                if (auth.currentUser?.uid == user.uid) _state.value = _state.value.copy(message = "Could not refresh verification. Reconnect and retry.")
            } finally { if (auth.currentUser?.uid == user.uid) _state.value = _state.value.copy(busy = false) }
        }
    }
}
