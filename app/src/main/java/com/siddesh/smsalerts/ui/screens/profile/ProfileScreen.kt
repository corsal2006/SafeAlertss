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
<<<<<<< HEAD
import androidx.compose.runtime.saveable.rememberSaveable
import com.siddesh.smsalerts.utils.ProfileDates
=======
>>>>>>> origin/main
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
<<<<<<< HEAD
    onNavigateToContacts: () -> Unit = {},
    onContinue: () -> Unit = {},
=======
>>>>>>> origin/main
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
<<<<<<< HEAD
    val enabledContactsCount by viewModel.enabledContactsCount.collectAsState()
    val phoneState by viewModel.phoneVerification.state.collectAsState()
    var verificationCode by remember { mutableStateOf("") }
    var phoneClock by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(phoneState.resendAt) {
        while (true) { phoneClock = android.os.SystemClock.elapsedRealtime(); kotlinx.coroutines.delay(1000) }
    }
=======
>>>>>>> origin/main
    val alertsCount by viewModel.alertsCount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val updateMessage by viewModel.updateMessage.collectAsState()

<<<<<<< HEAD
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var editName by rememberSaveable { mutableStateOf("") }
    var editPhone by rememberSaveable { mutableStateOf("") }
    var editDob by rememberSaveable { mutableStateOf("") }
    var editAddress by rememberSaveable { mutableStateOf("") }
    var loadedUid by rememberSaveable { mutableStateOf("") }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userProfile) {
        userProfile?.takeIf { it.isLoaded }?.let {
            if (!isEditing || loadedUid != it.uid) {
                editName = it.fullName.ifBlank { it.name }
                editPhone = it.phoneNumber
                editDob = it.dateOfBirth.orEmpty()
                editAddress = it.homeAddress
                loadedUid = it.uid
                if (!it.isComplete) isEditing = true
            }
=======
    var isEditing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userProfile) {
        userProfile?.let {
            editName = it.name
            editPhone = it.phoneNumber
>>>>>>> origin/main
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(updateMessage) {
        updateMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
<<<<<<< HEAD
=======
            isEditing = false
>>>>>>> origin/main
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
<<<<<<< HEAD
                .imePadding()
=======
>>>>>>> origin/main
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
<<<<<<< HEAD
            if (userProfile?.isComplete == false) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Complete your safety profile", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("Add your name and birth date. Age is calculated automatically. Address and phone are optional. Birth date stays private. Emergency functions remain available.", color = TextSecondary)
                        TextButton(onClick = onContinue) { Text("Complete later — open Home") }
                    }
                }
            }
=======
>>>>>>> origin/main
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
<<<<<<< HEAD
                        text = userProfile?.name?.ifBlank { "SafeAlerts User" } ?: "SafeAlerts User",
=======
                        text = userProfile?.name?.ifBlank { "SafeAlert User" } ?: "SafeAlert User",
>>>>>>> origin/main
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
<<<<<<< HEAD
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
=======
                Column(modifier = Modifier.fillMaxWidth()) {
>>>>>>> origin/main
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
<<<<<<< HEAD
                            enabled = !isLoading,
                            onClick = {
                                if (isEditing) {
                                    viewModel.updateProfile(editName, editPhone, editDob.takeIf { it.isNotBlank() }, editAddress) { isEditing = false }
=======
                            onClick = {
                                if (isEditing) {
                                    viewModel.updateProfile(editName, editPhone)
>>>>>>> origin/main
                                } else {
                                    isEditing = true
                                }
                            }
                        ) {
                            Text(
<<<<<<< HEAD
                                text = if (isLoading) "SAVING…" else if (isEditing) "SAVE" else "EDIT",
=======
                                text = if (isEditing) "SAVE" else "EDIT",
>>>>>>> origin/main
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
<<<<<<< HEAD

                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = {
                        val initial = java.util.Calendar.getInstance()
                        val parts = editDob.split("-")
                        if (parts.size == 3 && ProfileDates.age(editDob) != null) initial.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                        android.app.DatePickerDialog(context, { _, year, month, day ->
                            editDob = String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                        }, initial.get(java.util.Calendar.YEAR), initial.get(java.util.Calendar.MONTH), initial.get(java.util.Calendar.DAY_OF_MONTH)).apply {
                            datePicker.maxDate = System.currentTimeMillis()
                        }.show()
                    }, enabled = isEditing && !isLoading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (editDob.isBlank()) "Choose date of birth" else "Date of birth: $editDob")
                    }
                    Text("Age: ${ProfileDates.age(editDob)?.toString() ?: "Not available"}", color = TextSecondary)
                    val normalizedPhone = editPhone.replace(Regex("[\\s()-]"), "")
                    val phoneVerified = phoneState.verifiedPhone == normalizedPhone && normalizedPhone.isNotBlank()
                    Text(if (phoneVerified) "Phone verified" else "Phone not verified", color = if (phoneVerified) StatusSuccess else TextMuted)
                    if (!phoneVerified) Text("SMS OTP verification is not enabled in this configuration. Saved numbers remain unverified.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text(if (phoneState.emailVerified) "Email verified by Firebase" else "Email not verified", color = if (phoneState.emailVerified) StatusSuccess else TextMuted)
                    val resendSeconds = ((phoneState.resendAt - phoneClock).coerceAtLeast(0) + 999) / 1000
                    if (!phoneState.emailVerified) OutlinedButton(onClick = { viewModel.phoneVerification.sendEmailVerification() },
                        enabled = !phoneState.busy && resendSeconds == 0L && phoneState.email.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (resendSeconds > 0) "Request again in ${resendSeconds}s" else "Verify email (free)")
                    }
                    TextButton(onClick = { viewModel.phoneVerification.reloadVerification() }, enabled = !phoneState.busy) { Text("Refresh verification") }
                    if (phoneState.message.isNotBlank()) Text(phoneState.message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    OutlinedTextField(value = editAddress, onValueChange = { editAddress = it.take(300) },
                        label = { Text("Home address (optional, private)") }, enabled = isEditing && !isLoading, modifier = Modifier.fillMaxWidth())
                    Text("$enabledContactsCount of $contactsCount emergency contacts enabled", color = TextSecondary)
                    TextButton(onClick = onNavigateToContacts, modifier = Modifier.heightIn(min = 48.dp)) { Text("Manage emergency contacts") }
                    TextButton(onClick = onNavigateToSettings, modifier = Modifier.heightIn(min = 48.dp)) { Text("Emergency numbers and sharing preferences") }
                    Text("Permissions are requested when you use location, Bluetooth, microphone or notifications. Review access in Android App Settings.", color = TextMuted)
=======
>>>>>>> origin/main
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
<<<<<<< HEAD
                Text("LOGOUT OF SafeAlerts", fontWeight = FontWeight.Bold, fontSize = 14.sp)
=======
                Text("LOGOUT OF SAFEALERT", fontWeight = FontWeight.Bold, fontSize = 14.sp)
>>>>>>> origin/main
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Confirm Logout", fontWeight = FontWeight.Bold, color = TextPrimary) },
<<<<<<< HEAD
            text = { Text("Are you sure you want to log out of your SafeAlerts session?") },
=======
            text = { Text("Are you sure you want to log out of your SafeAlert session?") },
>>>>>>> origin/main
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
