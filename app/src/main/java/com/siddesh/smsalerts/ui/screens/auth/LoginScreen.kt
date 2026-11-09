package com.siddesh.smsalerts.ui.screens.auth

<<<<<<< HEAD
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import com.siddesh.smsalerts.R
=======
import androidx.compose.foundation.background
>>>>>>> origin/main
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siddesh.smsalerts.ui.theme.BackgroundDark
import kotlinx.coroutines.launch
import com.siddesh.smsalerts.ui.theme.CardBorder
import com.siddesh.smsalerts.ui.theme.EmergencyRed
import com.siddesh.smsalerts.ui.theme.EmergencyRedDark
import com.siddesh.smsalerts.ui.theme.StatusError
import com.siddesh.smsalerts.ui.theme.StatusSuccess
import com.siddesh.smsalerts.ui.theme.SurfaceCard
import com.siddesh.smsalerts.ui.theme.SurfaceCardElevated
import com.siddesh.smsalerts.ui.theme.TextMuted
import com.siddesh.smsalerts.ui.theme.TextPrimary
import com.siddesh.smsalerts.ui.theme.TextSecondary
import com.siddesh.smsalerts.ui.viewmodel.AuthUiState
import com.siddesh.smsalerts.ui.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onGoogleSignInClick: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit
) {
    val uiState by authViewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    val context = androidx.compose.ui.platform.LocalContext.current
    val preferencesDataStore = remember { com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberEmail by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    var showEmailLinkDialog by remember { mutableStateOf(false) }
    var emailLinkInput by remember { mutableStateOf("") }
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    LaunchedEffect(Unit) {
        preferencesDataStore.preferencesFlow.collect { prefs ->
            if (prefs.rememberEmail && prefs.savedEmail.isNotBlank() && email.isBlank()) {
                email = prefs.savedEmail
                rememberEmail = true
            }
        }
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is AuthUiState.Authenticated -> onNavigateToHome()
            is AuthUiState.Error -> {
                snackbarHostState.showSnackbar((uiState as AuthUiState.Error).message)
            }
            is AuthUiState.EmailLinkSent -> {
                snackbarHostState.showSnackbar((uiState as AuthUiState.EmailLinkSent).message)
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // SafeAlert Logo
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(EmergencyRed.copy(alpha = 0.15f))
                    .border(1.dp, EmergencyRed.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
<<<<<<< HEAD
                    contentDescription = "SafeAlerts Logo",
=======
                    contentDescription = "SafeAlert Logo",
>>>>>>> origin/main
                    tint = EmergencyRed,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
<<<<<<< HEAD
                text = "SafeAlerts",
=======
                text = "SafeAlert",
>>>>>>> origin/main
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Stay Safe. Stay Connected.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Error banner if any
            if (uiState is AuthUiState.Error) {
                val errorMsg = (uiState as AuthUiState.Error).message
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StatusError.copy(alpha = 0.12f))
                        .border(1.dp, StatusError.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(text = errorMsg, fontSize = 13.sp, color = StatusError)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Success banner (e.g. Email link sent)
            if (uiState is AuthUiState.EmailLinkSent) {
                val msg = (uiState as AuthUiState.EmailLinkSent).message
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StatusSuccess.copy(alpha = 0.12f))
                        .border(1.dp, StatusSuccess.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(text = msg, fontSize = 13.sp, color = StatusSuccess)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Email Field
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    authViewModel.resetState()
                },
                label = { Text("Email Address") },
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = null, tint = TextMuted)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmergencyRed,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = EmergencyRed,
                    focusedLabelColor = EmergencyRed,
                    unfocusedLabelColor = TextSecondary,
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Password Field
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    authViewModel.resetState()
                },
                label = { Text("Password") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = TextMuted)
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = TextMuted
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        authViewModel.login(email, password)
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmergencyRed,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = EmergencyRed,
                    focusedLabelColor = EmergencyRed,
                    unfocusedLabelColor = TextSecondary,
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Remember Email & Forgot Password Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { rememberEmail = !rememberEmail }
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = rememberEmail,
                        onCheckedChange = { rememberEmail = it },
                        colors = androidx.compose.material3.CheckboxDefaults.colors(
                            checkedColor = EmergencyRed,
                            uncheckedColor = TextSecondary,
                            checkmarkColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Remember email",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }

                Text(
                    text = "Forgot Password?",
                    fontSize = 13.sp,
                    color = EmergencyRed,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(onClick = onNavigateToForgotPassword)
<<<<<<< HEAD
                        .padding(horizontal = 8.dp, vertical = 10.dp)
=======
                        .padding(vertical = 4.dp)
>>>>>>> origin/main
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Button 1: [ Login ]
            Button(
                onClick = {
                    focusManager.clearFocus()
                    scope.launch {
                        preferencesDataStore.updateRememberEmail(rememberEmail, email)
                    }
                    authViewModel.login(email, password)
                },
                enabled = uiState !is AuthUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmergencyRed,
                    disabledContainerColor = EmergencyRedDark.copy(alpha = 0.5f)
                )
            ) {
                if (uiState is AuthUiState.Loading && (uiState as AuthUiState.Loading).message.contains("Signing", ignoreCase = true)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text((uiState as AuthUiState.Loading).message, color = Color.White, fontSize = 14.sp)
                } else {
                    Text(
                        text = "LOGIN",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Button 2: [ Continue with Google ]
            OutlinedButton(
                onClick = onGoogleSignInClick,
                enabled = uiState !is AuthUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceCard)
            ) {
                if (uiState is AuthUiState.Loading && (uiState as AuthUiState.Loading).message.contains("Google", ignoreCase = true)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = TextPrimary, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text((uiState as AuthUiState.Loading).message, color = TextPrimary, fontSize = 14.sp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
<<<<<<< HEAD
                        Image(
                            painter = painterResource(id = R.drawable.ic_google_logo),
                            contentDescription = "Google Logo",
                            modifier = Modifier.size(20.dp)
                        )
=======
                        Text("G", fontSize = 18.sp, fontWeight = FontWeight.Black, color = EmergencyRed)
>>>>>>> origin/main
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Continue with Google",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Button 3: [ Sign in with Email Link ]
            OutlinedButton(
                onClick = {
                    emailLinkInput = email
                    showEmailLinkDialog = true
                },
                enabled = uiState !is AuthUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent)
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sign in with Email Link",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Create Account link
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Don't have an account?", fontSize = 14.sp, color = TextSecondary)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Create Account",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmergencyRed,
<<<<<<< HEAD
                    modifier = Modifier
                        .clickable(onClick = onNavigateToRegister)
                        .padding(horizontal = 8.dp, vertical = 10.dp)
=======
                    modifier = Modifier.clickable(onClick = onNavigateToRegister)
>>>>>>> origin/main
                )
            }
        }

        androidx.compose.material3.SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    // Passwordless Email Link Dialog
    if (showEmailLinkDialog) {
        AlertDialog(
            onDismissRequest = { showEmailLinkDialog = false },
            title = {
                Text("Passwordless Sign-In", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "We will send an encrypted authentication link directly to your inbox. Tap the link to sign in instantly without typing a password.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = emailLinkInput,
                        onValueChange = { emailLinkInput = it },
                        label = { Text("Your Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmergencyRed,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        authViewModel.sendSignInLink(emailLinkInput)
                        showEmailLinkDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Text("SEND SIGN-IN LINK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmailLinkDialog = false }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = SurfaceCardElevated
        )
    }
}
