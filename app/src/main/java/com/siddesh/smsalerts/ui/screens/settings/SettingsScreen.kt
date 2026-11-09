package com.siddesh.smsalerts.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siddesh.smsalerts.data.preferences.AppUserPreferences
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import com.siddesh.smsalerts.ui.components.GlassCard
import com.siddesh.smsalerts.ui.components.SafeAlertTopBar
import com.siddesh.smsalerts.ui.theme.BackgroundDark
import com.siddesh.smsalerts.ui.theme.CardBorder
import com.siddesh.smsalerts.ui.theme.EmergencyRed
import com.siddesh.smsalerts.ui.theme.StatusSuccess
import com.siddesh.smsalerts.ui.theme.StatusWarning
import com.siddesh.smsalerts.ui.theme.SurfaceCard
import com.siddesh.smsalerts.ui.theme.SurfaceCardElevated
import com.siddesh.smsalerts.ui.theme.SurfaceDark
import com.siddesh.smsalerts.ui.theme.TextMuted
import com.siddesh.smsalerts.ui.theme.TextPrimary
import com.siddesh.smsalerts.ui.theme.TextSecondary
import com.siddesh.smsalerts.utils.PermissionUtils
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToContacts: () -> Unit = {},
    onNavigateToEmergencyTriggers: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onUserManual: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesDataStore = remember { UserPreferencesDataStore(context) }
    val preferences by preferencesDataStore.preferencesFlow.collectAsState(initial = AppUserPreferences())

    var showEditMessageDialog by remember { mutableStateOf(false) }
    var tempMessage by remember { mutableStateOf(preferences.defaultEmergencyMessage) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val hasSmsPermission = PermissionUtils.hasSmsPermission(context)
    val hasLocationPermission = PermissionUtils.hasLocationPermission(context)

    Scaffold(
        topBar = {
            SafeAlertTopBar(
                title = "Settings",
                showBackButton = true,
                onBackClick = onBackClick
            )
        },
        containerColor = BackgroundDark,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 0. Emergency Protection (Background Shake Detection)
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = if (preferences.shakeToAlertEnabled) StatusSuccess else TextMuted,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Emergency Protection",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (preferences.shakeToAlertEnabled) {
                                        "Shake detection is active in the background."
                                    } else {
                                        "Background emergency detection is disabled."
                                    },
                                    color = if (preferences.shakeToAlertEnabled) StatusSuccess else TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Switch(
                            checked = preferences.shakeToAlertEnabled,
                            onCheckedChange = { isChecked ->
                                scope.launch {
                                    preferencesDataStore.updateShakeToAlertEnabled(isChecked)
                                    if (isChecked) {
                                        com.siddesh.smsalerts.service.EmergencyProtectionService.startService(context)
                                    } else {
                                        com.siddesh.smsalerts.service.EmergencyProtectionService.stopService(context)
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = EmergencyRed,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = SurfaceDark
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "SafeAlerts uses a foreground service while protection is enabled. Android may stop monitoring after a user-forced stop.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    // Development-only Test Shake Detection (Only in DEBUG builds)
                    val isDebugBuild = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                    if (isDebugBuild) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = CardBorder.copy(alpha = 0.5f), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        var testTriggering by remember { mutableStateOf(false) }
                        var testMessage by remember { mutableStateOf<String?>(null) }

                        OutlinedButton(
                            onClick = {
                                testTriggering = true
                                testMessage = null
                                scope.launch {
                                    val emergencyManager = com.siddesh.smsalerts.service.EmergencyManager.getInstance(context)
                                    emergencyManager.triggerAutomaticEmergency("SHAKE")
                                    testMessage = "Triggered test shake alert! Check Alert History."
                                    testTriggering = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF59E0B)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = if (testTriggering) "Simulating Pipeline..." else "🧪 Test Shake Detection (Debug Only)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (testMessage != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = testMessage.orEmpty(),
                                color = StatusSuccess,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // 1. Emergency Message Configuration
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Emergency Message",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = {
                                tempMessage = preferences.defaultEmergencyMessage
                                showEditMessageDialog = true
                            }
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Message", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "\"${preferences.defaultEmergencyMessage}\"",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "This message is broadcast to all active emergency contacts when triggered.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            // 2. Emergency Triggers Configuration (Shake, Voice HELP, Button)
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToEmergencyTriggers
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Vibration, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Emergency Triggers",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Shake 3x, Voice \"HELP\", Cooldown & Detection Settings",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                }
            }

            // 2b. Emergency Numbers & Helplines (112, 108, 101, 1091)
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToEmergencyTriggers
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Emergency Services & Numbers",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Configure and verify numbers for your region",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = CardBorder.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple("Call Police", preferences.policeNumber, Color(0xFF38BDF8)),
                            Triple("Call Medical", preferences.ambulanceNumber, Color(0xFF10B981)),
                            Triple("Call Fire", preferences.fireNumber, Color(0xFFF59E0B)),
                            Triple("Call Women", preferences.womensHelplineNumber, Color(0xFFEC4899))
                        ).forEach { (label, number, accent) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(accent.copy(alpha = 0.12f))
                                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                    .clickable(enabled = number.isNotBlank()) {
                                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                                        context.startActivity(dialIntent)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = label.split(" ")[0],
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = accent
                                    )
                                    Text(
                                        text = label.split(" ")[1],
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Trusted Contacts Shortcut
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToContacts
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.People, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Manage Trusted Contacts",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Add, edit, or toggle recipients for alerts",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                }
            }

            // 4. Location Sharing Preference
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Location Sharing in Alerts",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Includes your actual GPS fix in authorized emergency alerts",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Switch(
                            checked = preferences.locationSharingEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { preferencesDataStore.updateLocationSharing(enabled) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmergencyRed,
                                checkedTrackColor = EmergencyRed.copy(alpha = 0.3f),
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = SurfaceDark
                            )
                        )
                    }
                }
            }

            // 4. Trigger & Countdown Preferences
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "SOS Trigger Window",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Countdown duration before alert dispatches: ${preferences.countdownDurationSeconds}s",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(3, 5, 10).forEach { seconds ->
                            FilterChip(
                                selected = preferences.countdownDurationSeconds == seconds,
                                onClick = {
                                    scope.launch { preferencesDataStore.updateCountdownDuration(seconds) }
                                },
                                label = { Text("${seconds}s") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmergencyRed,
                                    selectedLabelColor = Color.White,
                                    containerColor = SurfaceDark,
                                    labelColor = TextPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = CardBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Direct SMS vs Composer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Direct SMS Dispatch",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Sends automatic emergency SMS alerts via cellular network to your trusted contacts using Android SmsManager.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Switch(
                            checked = preferences.directSmsEnabled,
                            enabled = true,
                            onCheckedChange = { enabled ->
                                scope.launch { preferencesDataStore.updateDirectSms(enabled) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmergencyRed,
                                checkedTrackColor = EmergencyRed.copy(alpha = 0.3f),
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = SurfaceDark
                            )
                        )
                    }
                }
            }

            // 5. Interface & Feedback Settings
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "App Theme & Feedback",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Theme selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("dark" to "Dark Mode", "light" to "Light Mode", "system" to "System").forEach { (mode, label) ->
                            FilterChip(
                                selected = preferences.themeMode == mode,
                                onClick = {
                                    scope.launch { preferencesDataStore.updateThemeMode(mode) }
                                },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmergencyRed,
                                    selectedLabelColor = Color.White,
                                    containerColor = SurfaceDark,
                                    labelColor = TextPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = CardBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Haptic Feedback
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Vibration, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Haptic Feedback",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Vibrate device during countdown & alert confirmations",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Switch(
                            checked = preferences.hapticFeedbackEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch { preferencesDataStore.updateHapticFeedback(enabled) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmergencyRed,
                                checkedTrackColor = EmergencyRed.copy(alpha = 0.3f),
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = SurfaceDark
                            )
                        )
                    }
                }
            }

            // 6. Permissions Telemetry
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "System Permissions Telemetry",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Manage in Settings",
                            color = EmergencyRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingsPermissionRow(
                        title = "Automatic carrier SMS (SEND_SMS)",
                        description = "Required for sending direct emergency alerts via cellular carrier",
                        isGranted = hasSmsPermission
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = CardBorder)
                    Spacer(modifier = Modifier.height(10.dp))

                    SettingsPermissionRow(
                        title = "Precise GPS Location (ACCESS_FINE_LOCATION)",
                        description = "Telemetry coordinates link for recipients",
                        isGranted = hasLocationPermission
                    )
                }
            }

            OutlinedButton(onClick = onUserManual, modifier = Modifier.fillMaxWidth()) { Text("User Manual") }
            // 7. Privacy & About Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showPrivacyDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Privacy", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onNavigateToAbout,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("About", fontSize = 13.sp)
                }
            }

            // 8. Sign Out Button
            Button(
                onClick = { showLogoutDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed.copy(alpha = 0.15f), contentColor = EmergencyRed),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EmergencyRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("SIGN OUT OF SafeAlerts", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(88.dp))
        }
    }

    // Edit Emergency Message Dialog
    if (showEditMessageDialog) {
        AlertDialog(
            onDismissRequest = { showEditMessageDialog = false },
            title = { Text("Customize Emergency Message", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column {
                    Text(
                        text = "Customize the message sent to your emergency contacts when you trigger an alert:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tempMessage,
                        onValueChange = { tempMessage = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
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
                        scope.launch { preferencesDataStore.updateEmergencyMessage(tempMessage.trim()) }
                        showEditMessageDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Text("SAVE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditMessageDialog = false }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = SurfaceCardElevated
        )
    }

    // Privacy Policy Dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy & Security Commitment", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Choose which safety features and permissions to enable. SafeAlerts does not include advertising SDKs.\n\n" +
                                "Emergency SMS: With SMS permission, alerts use your SIM to contact the emergency contacts you saved. Alerts may be triggered by SOS or by a shake, voice phrase or expired safety timer you enabled. Messages can include your emergency profile and location when enabled. Carrier charges may apply. SafeAlerts does not read your SMS inbox or call history.\n\n" +
                                "Live location: An active SOS, journey or Trusted Circle sharing session can keep collecting and sharing location while the app is in the background, with a visible service notification. Updates target 15 seconds; Android may delay them. Cloud location is available to accepted members with an active sharing grant. Stop the session or turn off sharing to end updates.\n\n" +
                                "Account and contacts: Firebase stores your account, private profile and saved emergency contacts. Selected recipients receive the emergency information you share. Public profile information is limited to your name and photo. Group messages are available to group members; keep invite codes and offline group keys private.\n\n" +
                                "Microphone: Voice recognition listens on Home or during a speech test after you enable it and grant permission. On-device recognition is preferred; when unavailable, your phone's speech provider may send audio to its servers. SafeAlerts does not save raw microphone recordings.\n\n" +
                                "Maps and Bluetooth: Map images are requested from OpenStreetMap, which receives your network address and requested map area. Nearby sharing uses Bluetooth with your permission and a private group key.\n\n" +
                                "Your controls: Revoke permissions in Android settings, disable triggers, stop sharing, remove saved contacts, or confirm deletion of your resolved/cancelled incidents in History. Copies already delivered by SMS or to another device cannot be recalled.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPrivacyDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Text("CLOSE")
                }
            },
            containerColor = SurfaceCardElevated
        )
    }

    // Sign Out Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign Out", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Text(
                    text = "Are you sure you want to sign out? You will need to sign back in to transmit authenticated emergency broadcasts.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogoutClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Text("SIGN OUT")
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

@Composable
private fun SettingsPermissionRow(
    title: String,
    description: String,
    isGranted: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isGranted) StatusSuccess else EmergencyRed,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
        Text(
            text = if (isGranted) "READY" else "DISABLED",
            color = if (isGranted) StatusSuccess else EmergencyRed,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
