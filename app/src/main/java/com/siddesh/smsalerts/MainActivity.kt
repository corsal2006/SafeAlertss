package com.siddesh.smsalerts

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.siddesh.smsalerts.ui.navigation.SafeAlertNavGraph
import com.siddesh.smsalerts.ui.theme.SafeAlertTheme
import com.siddesh.smsalerts.ui.viewmodel.*
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SafeAlerts/GoogleAuth"
    }

    private val viewModelFactory by lazy {
        AppViewModelFactory(application)
    }

    private val authViewModel: AuthViewModel by viewModels { viewModelFactory }
    private val homeViewModel: HomeViewModel by viewModels { viewModelFactory }
    private val sosViewModel: SosViewModel by viewModels { viewModelFactory }
    private val contactsViewModel: ContactsViewModel by viewModels { viewModelFactory }
    private val historyViewModel: HistoryViewModel by viewModels { viewModelFactory }
    private val profileViewModel: ProfileViewModel by viewModels { viewModelFactory }

    private lateinit var credentialManager: CredentialManager
    private lateinit var googleSignInClient: GoogleSignInClient

    // Legacy Google Sign-In launcher fallback
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        android.util.Log.d(TAG, "GOOGLE_ACTIVITY_RESULT: resultCode = ${result.resultCode}, hasData = ${result.data != null}")
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            android.util.Log.d(TAG, "GOOGLE_CREDENTIAL_RECEIVED: idToken present = ${!idToken.isNullOrBlank()}")
            if (!idToken.isNullOrBlank()) {
                authViewModel.signInWithGoogle(idToken)
            } else {
                val message = "No Google credential available"
                android.util.Log.e(TAG, "GOOGLE_CREDENTIAL_EMPTY: idToken is null or blank")
                authViewModel.setError(message)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        } catch (e: ApiException) {
            handleGoogleApiException(e)
        } catch (e: Exception) {
            if (result.resultCode == RESULT_CANCELED) {
                android.util.Log.d(TAG, "GOOGLE_PICKER_CANCELLED: user closed or backed out of picker")
                authViewModel.setError("Google Sign-In cancelled")
            } else {
                android.util.Log.e(TAG, "GOOGLE_SIGN_IN_EXCEPTION: class=${e.javaClass.simpleName}, message=${e.message}", e)
                authViewModel.setError("Google Sign-In failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    private fun getWebClientId(): String {
        return try {
            getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            val resId = resources.getIdentifier("default_web_client_id", "string", packageName)
            if (resId != 0) getString(resId) else "663925742780-en3qinv0sd0hoooous15fn6begrduoko.apps.googleusercontent.com"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webClientId = getWebClientId()
        android.util.Log.d(TAG, "CONFIGURED_WEB_CLIENT_ID: $webClientId")

        // Initialize AndroidX Credential Manager
        credentialManager = CredentialManager.create(this)

        // Initialize Legacy GoogleSignInClient as fallback
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .requestProfile()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Refresh auth diagnostics with context
        authViewModel.refreshDiagnostics(this)

        // Handle potential email sign-in link from intent
        handleIncomingIntent(intent)

        setContent {
            val prefs by homeViewModel.userPreferences.collectAsState()
            val systemInDark = isSystemInDarkTheme()
            val isDark = when (prefs.themeMode) {
                "light" -> false
                "dark" -> true
                else -> systemInDark
            }

            SafeAlertTheme(darkTheme = isDark) {
                val navController = rememberNavController()

                SafeAlertNavGraph(
                    navController = navController,
                    authViewModel = authViewModel,
                    homeViewModel = homeViewModel,
                    sosViewModel = sosViewModel,
                    contactsViewModel = contactsViewModel,
                    historyViewModel = historyViewModel,
                    profileViewModel = profileViewModel,
                    onGoogleSignInClick = {
                        launchGoogleSignIn()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun launchGoogleSignIn() {
        val webClientId = getWebClientId()
        android.util.Log.d(TAG, "INITIATING_GOOGLE_SIGN_IN via Credential Manager with client: $webClientId")
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@MainActivity
                )
                val credential = result.credential
                if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val idToken = googleIdTokenCredential.idToken
                        android.util.Log.d(TAG, "CREDENTIAL_MANAGER_SUCCESS: idToken acquired")
                        authViewModel.updateGoogleCredentialDiagnostic(
                            category = "SUCCESS",
                            result = "CREDENTIAL_MANAGER_TOKEN_ACQUIRED"
                        )
                        authViewModel.signInWithGoogle(idToken)
                    } catch (e: GoogleIdTokenParsingException) {
                        android.util.Log.e("GoogleSignIn", "Google Sign-In failed [GoogleIdTokenParsingException]: class=${e.javaClass.name}, message=${e.message}, cause=${e.cause}", e)
                        authViewModel.updateGoogleCredentialDiagnostic(
                            category = "INVALID_CREDENTIAL",
                            result = "PARSING_EXCEPTION",
                            errorMessage = e.message
                        )
                        authViewModel.setError("Invalid Google credential received: ${e.message}")
                    }
                } else {
                    android.util.Log.w("GoogleSignIn", "Google Sign-In [Unexpected Credential Type]: ${credential.type}")
                    launchLegacyGoogleSignIn()
                }
            } catch (e: GetCredentialCancellationException) {
                android.util.Log.d("GoogleSignIn", "Google Sign-In cancelled by user")
                authViewModel.updateGoogleCredentialDiagnostic(
                    category = "USER_CANCELLED",
                    result = "CANCELLED",
                    errorMessage = "User cancelled selection"
                )
                authViewModel.setError("Google Sign-In cancelled")
            } catch (e: NoCredentialException) {
                android.util.Log.w("GoogleSignIn", "Google Sign-In [NoCredentialException]: class=${e.javaClass.name}, message=${e.message}. Falling back to legacy Google Sign-In", e)
                authViewModel.updateGoogleCredentialDiagnostic(
                    category = "NO_CREDENTIAL",
                    result = "FALLBACK_TO_LEGACY",
                    errorMessage = e.message
                )
                launchLegacyGoogleSignIn()
            } catch (e: GetCredentialException) {
                android.util.Log.e("GoogleSignIn", "Google Sign-In failed [GetCredentialException]: class=${e.javaClass.name}, type=${e.type}, message=${e.message}, cause=${e.cause}", e)
                val msg = e.message ?: ""
                val type = e.type
                if (msg.contains("10") || msg.contains("Developer", ignoreCase = true) || msg.contains("configuration", ignoreCase = true) || type.contains("configuration", ignoreCase = true)) {
                    val developerMsg = "Developer Configuration Error (Code 10): Release signing certificate SHA-1/SHA-256 is not registered in Firebase Console for com.siddesh.smsalerts"
                    authViewModel.updateGoogleCredentialDiagnostic(
                        category = "DEVELOPER_ERROR",
                        result = "FAILED",
                        errorMessage = developerMsg
                    )
                    authViewModel.setError(developerMsg)
                    Toast.makeText(this@MainActivity, developerMsg, Toast.LENGTH_LONG).show()
                } else {
                    launchLegacyGoogleSignIn()
                }
            } catch (e: Exception) {
                android.util.Log.e("GoogleSignIn", "Google Sign-In failed [Unexpected Exception]: class=${e.javaClass.name}, message=${e.message}, cause=${e.cause}", e)
                launchLegacyGoogleSignIn()
            }
        }
    }

    private fun launchLegacyGoogleSignIn() {
        android.util.Log.d("GoogleSignIn", "LAUNCHING_LEGACY_GOOGLE_SIGN_IN")
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }

    private fun handleGoogleApiException(e: ApiException) {
        val statusCode = e.statusCode
        val exceptionClass = e.javaClass.name
        android.util.Log.e("GoogleSignIn", "Google Sign-In failed [ApiException]: class=$exceptionClass, statusCode=$statusCode, message=${e.message}, cause=${e.cause}", e)
        val (category, message) = when (statusCode) {
            12501, 16 -> {
                android.util.Log.d("GoogleSignIn", "Google Sign-In cancelled by user: statusCode=$statusCode")
                "USER_CANCELLED" to "Google Sign-In cancelled"
            }
            10 -> {
                android.util.Log.e("GoogleSignIn", "Developer Configuration Error (Code 10): Release SHA-1/SHA-256 is missing in Firebase Console for com.siddesh.smsalerts")
                "DEVELOPER_ERROR" to "Developer Error (Code 10): Release SHA-1/SHA-256 must be registered in Firebase Console."
            }
            12500 -> {
                android.util.Log.e("GoogleSignIn", "Configuration Error (Code 12500): Check Firebase Google Auth provider")
                "CONFIG_ERROR" to "Configuration Error (Code 12500): Check Google Auth Provider in Firebase Console."
            }
            7 -> {
                android.util.Log.e("GoogleSignIn", "Network Error (Code 7): No internet connection")
                "NETWORK_ERROR" to "Network error: Please check your internet connection."
            }
            8 -> {
                android.util.Log.e("GoogleSignIn", "Internal Error (Code 8): Google Play Services internal error")
                "INTERNAL_ERROR" to "Google Play Services internal error (Code 8). Please retry."
            }
            else -> {
                android.util.Log.e("GoogleSignIn", "Google Sign-In API error: statusCode=$statusCode")
                "API_ERROR" to "Google Sign-In API error (Code: $statusCode): ${e.localizedMessage ?: "Unknown error"}"
            }
        }

        authViewModel.updateGoogleCredentialDiagnostic(
            category = category,
            result = "FAILED",
            errorMessage = message
        )
        authViewModel.setError(message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun handleIncomingIntent(intent: Intent) {
        val emailLink = intent.data?.toString()
        if (emailLink != null && authViewModel.isSignInWithEmailLink(emailLink)) {
            authViewModel.completeSignInWithEmailLink(emailLink)
        }
    }
}
