package com.siddesh.smsalerts.ui.screens.profile

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import com.siddesh.smsalerts.ui.components.GlassCard
import com.siddesh.smsalerts.ui.components.SafeAlertTopBar
import com.siddesh.smsalerts.ui.theme.*
import com.siddesh.smsalerts.ui.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onLogoutSuccess: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferencesDataStore = remember { UserPreferencesDataStore(context) }
    val preferences by preferencesDataStore.preferencesFlow.collectAsState(
        initial = com.siddesh.smsalerts.data.preferences.AppUserPreferences()
    )

    val userProfile by viewModel.userProfile.collectAsState()
    val contactsCount by viewModel.contactsCount.collectAsState()
    val alertsCount by viewModel.alertsCount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val updateMessage by viewModel.updateMessage.collectAsState()

    var isEditing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userProfile) {
        userProfile?.let {
            editName = it.name
            editPhone = it.phoneNumber
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(updateMessage) {
        updateMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
            isEditing = false
        }
    }

    Scaffold(
        topBar = {
            SafeAlertTopBar(
                title = "User Profile"
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Card with Avatar
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val photoUrl = userProfile?.photoUrl
                    if (!photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "User Avatar",
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .border(2.dp, EmergencyRed, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(EmergencyRed.copy(alpha = 0.15f))
                                .border(2.dp, EmergencyRed, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (userProfile?.name?.take(1) ?: "U").uppercase(),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmergencyRed
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = userProfile?.name?.ifBlank { "SafeAlert User" } ?: "SafeAlert User",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text(
                        text = userProfile?.email?.ifBlank { "No email associated" } ?: "No email associated",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Account Provider Badge
                    val providerText = when (userProfile?.authProvider) {
                        "google" -> "Google Account"
                        "email_link" -> "Passwordless Email Link"
                        else -> "Email & Password"
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceCardElevated)
                            .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (userProfile?.authProvider == "google") Icons.Default.AccountCircle else Icons.Default.Email,
                                contentDescription = null,
                                tint = EmergencyRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = providerText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            // Stats Row: Trusted Contacts count & History count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassCard(modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$contactsCount",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = EmergencyRed
                        )
                        Text(
                            text = "Trusted Contacts",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                GlassCard(modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$alertsCount",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF38BDF8)
                        )
                        Text(
                            text = "Alerts Logged",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Edit Profile / Profile Details Form
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PROFILE DETAILS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.sp
                        )

                        TextButton(
                            onClick = {
                                if (isEditing) {
                                    viewModel.updateProfile(editName, editPhone)
                                } else {
                                    isEditing = true
                                }
                            }
                        ) {
                            Text(
                                text = if (isEditing) "SAVE" else "EDIT",
                                color = EmergencyRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Full Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TextMuted) },
                        enabled = isEditing,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmergencyRed,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            disabledTextColor = TextPrimary,
                            disabledBorderColor = CardBorder
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("Phone Number") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = TextMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        enabled = isEditing,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmergencyRed,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            disabledTextColor = TextPrimary,
                            disabledBorderColor = CardBorder
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // Theme Preferences Selector (Requirement 12)
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "APP THEME",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val themeModes = listOf(
                        Triple("light", "Light", "☀"),
                        Triple("dark", "Dark", "🌙"),
                        Triple("system", "System", "⚙")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        themeModes.forEach { (mode, title, icon) ->
                            val isSelected = preferences.themeMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) EmergencyRed.copy(alpha = 0.15f) else SurfaceCardElevated)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) EmergencyRed else CardBorder,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        coroutineScope.launch {
                                            preferencesDataStore.updateThemeMode(mode)
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = icon, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) TextPrimary else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Notification Settings Shortcut
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(fallback)
                    }
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Notification Channels",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Configure urgent alarms & check-in sound alerts",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                }
            }

            // Logout Button
            Button(
                onClick = { showLogoutDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmergencyRed.copy(alpha = 0.15f),
                    contentColor = EmergencyRed
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .border(1.dp, EmergencyRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("LOGOUT OF SAFEALERT", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Confirm Logout", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = { Text("Are you sure you want to log out of your SafeAlert session?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                        onLogoutSuccess()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Text("LOGOUT")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = SurfaceCardElevated
        )
    }
}
