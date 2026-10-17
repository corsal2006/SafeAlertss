package com.siddesh.smsalerts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.siddesh.smsalerts.ui.theme.CardBorder
import com.siddesh.smsalerts.ui.theme.EmergencyRed
import com.siddesh.smsalerts.ui.theme.StatusError
import com.siddesh.smsalerts.ui.theme.StatusSuccess
import com.siddesh.smsalerts.ui.theme.StatusWarning
import com.siddesh.smsalerts.ui.theme.SurfaceCard
import com.siddesh.smsalerts.ui.theme.SurfaceCardElevated
import com.siddesh.smsalerts.ui.theme.TextMuted
import com.siddesh.smsalerts.ui.theme.TextPrimary
import com.siddesh.smsalerts.ui.theme.TextSecondary
import com.siddesh.smsalerts.ui.viewmodel.AuthViewModel

@Composable
fun AuthDiagnosticButton(
    modifier: Modifier = Modifier,
    authViewModel: AuthViewModel? = null
) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showDialog = true },
        modifier = modifier.height(36.dp),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceCard.copy(alpha = 0.5f))
    ) {
        Icon(
            imageVector = Icons.Default.BugReport,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Auth Diagnostics",
            fontSize = 12.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Medium
        )
    }

    if (showDialog) {
        val vm: AuthViewModel = authViewModel ?: viewModel()
        val diagInfo by vm.diagnostics.collectAsState()

        val isFirebaseInitialized = try {
            FirebaseApp.getInstance() != null
        } catch (e: Exception) {
            false
        }

        val auth = try { FirebaseAuth.getInstance() } catch (e: Exception) { null }
        val isAuthAvailable = auth != null
        val currentUser = auth?.currentUser
        val hasCurrentUser = currentUser != null

        val context = androidx.compose.ui.platform.LocalContext.current
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        val webClientId = if (resId != 0) {
            try { context.getString(resId) } catch (e: Exception) { "" }
        } else {
            "663925742780-en3qinv0sd0hoooous15fn6begrduoko.apps.googleusercontent.com"
        }
        val isWebClientIdValid = webClientId.isNotBlank()

        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = SurfaceCardElevated,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = EmergencyRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Authentication Diagnostics",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DiagnosticRow(
                        label = "Package Name",
                        value = "com.siddesh.smsalerts",
                        isOk = true
                    )
                    DiagnosticRow(
                        label = "Firebase Project",
                        value = "smselerts",
                        isOk = true
                    )
                    DiagnosticRow(
                        label = "Firebase Initialized",
                        value = if (isFirebaseInitialized) "ACTIVE" else "FAILED",
                        isOk = isFirebaseInitialized
                    )
                    DiagnosticRow(
                        label = "Firebase Auth Available",
                        value = if (isAuthAvailable) "AVAILABLE" else "FAILED",
                        isOk = isAuthAvailable
                    )
                    DiagnosticRow(
                        label = "Google Configuration",
                        value = if (isWebClientIdValid) "CONFIGURED" else "MISSING",
                        isOk = isWebClientIdValid
                    )
                    DiagnosticRow(
                        label = "Web Client ID",
                        value = if (isWebClientIdValid) "FOUND" else "MISSING",
                        isOk = isWebClientIdValid
                    )
                    DiagnosticRow(
                        label = "Current User",
                        value = if (hasCurrentUser) (currentUser?.displayName ?: currentUser?.email ?: "SIGNED_IN") else "NOT_SIGNED_IN",
                        isOk = hasCurrentUser
                    )
                    DiagnosticRow(
                        label = "Google Credential",
                        value = diagInfo.googleCredentialResult,
                        isOk = diagInfo.googleCredentialResult.contains("ACQUIRED") || diagInfo.googleCredentialResult.contains("RECEIVED") || diagInfo.googleCredentialResult == "NOT_ATTEMPTED"
                    )
                    DiagnosticRow(
                        label = "Firebase Credential",
                        value = diagInfo.firebaseCredentialResult,
                        isOk = diagInfo.firebaseCredentialResult.contains("CREATED") || diagInfo.firebaseCredentialResult == "NOT_ATTEMPTED"
                    )
                    DiagnosticRow(
                        label = "Firebase Sign-In",
                        value = diagInfo.firebaseSignInResult,
                        isOk = diagInfo.firebaseSignInResult == "SUCCESS" || diagInfo.firebaseSignInResult == "NOT_ATTEMPTED"
                    )

                    // Error Category display (if an error occurred)
                    if (!diagInfo.lastErrorCategory.isNullOrBlank() && diagInfo.lastErrorCategory != "SUCCESS") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(StatusError.copy(alpha = 0.1f))
                                .border(1.dp, StatusError.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = null,
                                        tint = StatusError,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Error Category: ${diagInfo.lastErrorCategory}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusError
                                    )
                                }
                                if (!diagInfo.lastErrorMessage.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = diagInfo.lastErrorMessage ?: "",
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    // Configuration Advisory (if Developer Error 10 / google-services package mismatch detected)
                    if (!diagInfo.configWarning.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(StatusWarning.copy(alpha = 0.1f))
                                .border(1.dp, StatusWarning.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = StatusWarning,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Config Advisory:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusWarning
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = diagInfo.configWarning ?: "",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextSecondary,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Text("CLOSE")
                }
            }
        )
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    isOk: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.weight(1.2f)
        )
        Row(
            modifier = Modifier.weight(1.0f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isOk) StatusSuccess else StatusError,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isOk) StatusSuccess else StatusError,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}
