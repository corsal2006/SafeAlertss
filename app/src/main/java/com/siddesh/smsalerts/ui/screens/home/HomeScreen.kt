package com.siddesh.smsalerts.ui.screens.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.siddesh.smsalerts.ui.components.EmergencyPermissionDialog
import com.siddesh.smsalerts.utils.PermissionManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siddesh.smsalerts.data.model.EmergencyType
import com.siddesh.smsalerts.ui.components.GlassCard
import com.siddesh.smsalerts.ui.components.SafeAlertMainTopBar
import com.siddesh.smsalerts.ui.components.SosButton
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
import com.siddesh.smsalerts.ui.viewmodel.HomeViewModel
import com.siddesh.smsalerts.utils.DateUtils

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    onNavigateToEmergencyType: () -> Unit,
    onNavigateToEmergencyReview: (EmergencyType) -> Unit,
    onNavigateToCountdown: (emergencyType: String, triggerSource: String) -> Unit = { _, _ -> },
    onNavigateToContacts: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSafetyCheckIn: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToTriggers: () -> Unit = onNavigateToSettings,
    onNavigateToWalkieTalkie: () -> Unit = {},
    onNavigateToWomensSafety: () -> Unit = {},
    onNavigateToTrustedCircle: () -> Unit = {}
) {
    val context = LocalContext.current
    val userProfile by homeViewModel.userProfile.collectAsState()
    val userPreferences by homeViewModel.userPreferences.collectAsState()
    val contactsCount by homeViewModel.contactsCount.collectAsState()
    val latestAlert by homeViewModel.latestAlert.collectAsState()
    val networkStatus by homeViewModel.networkStatus.collectAsState()
    val isLocationReady by homeViewModel.isLocationReady.collectAsState()
    val requireHold by homeViewModel.requireHold.collectAsState()
    val serviceReadiness by homeViewModel.serviceReadiness.collectAsState()
    val safetyCheckIn by homeViewModel.safetyCheckInState.collectAsState()

    val emergencyManager = remember { com.siddesh.smsalerts.service.EmergencyManager.getInstance(context) }

    // Voice Detector Lifecycle Integration
    val voiceDetector = remember {
        com.siddesh.smsalerts.data.voice.VoiceEmergencyDetector(
            context = context,
            onEmergencyPhraseDetected = { _ ->
                emergencyManager.triggerAutomaticEmergency("VOICE")
            }
        )
    }

    LaunchedEffect(userPreferences.shakeToAlertEnabled) {
        if (userPreferences.shakeToAlertEnabled) {
            com.siddesh.smsalerts.service.EmergencyProtectionService.startService(context)
        } else {
            com.siddesh.smsalerts.service.EmergencyProtectionService.stopService(context)
        }
    }

    LaunchedEffect(userPreferences.voiceAlertEnabled) {
        if (userPreferences.voiceAlertEnabled) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                voiceDetector.startListening()
            }
        } else {
            voiceDetector.stopListening()
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            voiceDetector.stopListening()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var showPermissionSetupDialog by remember {
        mutableStateOf(!PermissionManager.hasAllRequiredPermissions(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                homeViewModel.refreshDashboard()
                if (PermissionManager.hasAllRequiredPermissions(context)) {
                    showPermissionSetupDialog = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        homeViewModel.refreshDashboard()
    }

    if (showPermissionSetupDialog) {
        EmergencyPermissionDialog(
            onDismiss = {
                showPermissionSetupDialog = false
            },
            onPermissionsUpdated = {
                homeViewModel.refreshDashboard()
                if (PermissionManager.hasAllRequiredPermissions(context)) {
                    showPermissionSetupDialog = false
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // SECTION 1: HEADER (Profile, Greeting, Current Safety Status)
        SafeAlertMainTopBar(
            userProfile = userProfile,
            onProfileClick = onNavigateToProfile,
            onNotificationsClick = onNavigateToAbout
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Greeting & Safety Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = homeViewModel.getDynamicGreeting(),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (serviceReadiness.isReady) "System active • Ready for emergency" else "Action required • Configure contacts",
                        fontSize = 13.sp,
                        color = if (serviceReadiness.isReady) StatusSuccess else StatusWarning,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceCard)
                        .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                        .clickable { homeViewModel.toggleRequireHold() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        tint = if (requireHold) EmergencyRed else StatusSuccess,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (requireHold) "2s Hold" else "Quick Tap",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Offline Network Banner (only visible when device has no internet)
            AnimatedVisibility(visible = !networkStatus.isConnected) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    backgroundColor = StatusWarning.copy(alpha = 0.12f),
                    borderColor = StatusWarning.copy(alpha = 0.4f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = StatusWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Offline Mode Active",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Cellular SMS will transmit even without Internet connectivity.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION 2: MAIN VISIBLE "EMERGENCY ALERT" BUTTON
            SosButton(
                size = 205.dp,
                requireHold = requireHold,
                onSosTriggered = {
                    if (PermissionManager.hasCoreEmergencyPermissions(context)) {
                        onNavigateToCountdown("EMERGENCY", "BUTTON")
                    } else {
                        showPermissionSetupDialog = true
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (requireHold) "Hold 2 seconds for instant emergency alert" else "Tap for instant emergency alert",
                fontSize = 12.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION 3: QUICK ACTIONS
            // 🚑 Medical, 🚗 Accident, 🔥 Fire, 🚨 Security, 📍 Share Location, 👥 Trusted Contacts, 🕐 Alert History, ✅ Safety Check-In
            Text(
                text = "QUICK ACTIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Row 1: 4 Emergency Categories
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionChip(
                    modifier = Modifier.weight(1f),
                    symbol = "🚑",
                    label = "Medical",
                    onClick = { onNavigateToEmergencyReview(EmergencyType.MEDICAL) }
                )
                QuickActionChip(
                    modifier = Modifier.weight(1f),
                    symbol = "🚗",
                    label = "Accident",
                    onClick = { onNavigateToEmergencyReview(EmergencyType.ACCIDENT) }
                )
                QuickActionChip(
                    modifier = Modifier.weight(1f),
                    symbol = "🔥",
                    label = "Fire",
                    onClick = { onNavigateToEmergencyReview(EmergencyType.FIRE) }
                )
                QuickActionChip(
                    modifier = Modifier.weight(1f),
                    symbol = "🚨",
                    label = "Security",
                    onClick = { onNavigateToEmergencyReview(EmergencyType.CRIME) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: 4 Utility Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionChip(
                    modifier = Modifier.weight(1f),
                    symbol = "📍",
                    label = "Location",
                    onClick = { onNavigateToEmergencyReview(EmergencyType.OTHER) }
                )
                QuickActionChip(
                    modifier = Modifier.weight(1f),
                    symbol = "👥",
                    label = "Contacts",
                    onClick = onNavigateToContacts
                )
                QuickActionChip(
                    modifier = Modifier.weight(1f),
                    symbol = "🕐",
                    label = "History",
                    onClick = onNavigateToHistory
                )
                QuickActionChip(
                    modifier = Modifier.weight(1f),
                    symbol = "✅",
                    label = "Check-In",
                    onClick = onNavigateToSafetyCheckIn
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 4: SAFETY CHECK-IN ACTIVE OR SHORTCUT CARD
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToSafetyCheckIn
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (safetyCheckIn.isActive) StatusSuccess.copy(alpha = 0.2f) else SurfaceCardElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = if (safetyCheckIn.isActive) StatusSuccess else Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = if (safetyCheckIn.isActive) "Safety Check-In Active" else "Safety Check-In Timer",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = if (safetyCheckIn.isActive) {
                                    val mins = safetyCheckIn.remainingSeconds / 60
                                    val secs = safetyCheckIn.remainingSeconds % 60
                                    "Remaining: %02d:%02d".format(mins, secs)
                                } else {
                                    "Set a timer for walks, meetings, or transit"
                                },
                                fontSize = 12.sp,
                                color = if (safetyCheckIn.isActive) StatusSuccess else TextSecondary
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SECTION 4B: WALKIE-TALKIE & OFFLINE MESH CARD
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToWalkieTalkie
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF38BDF8).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Offline Chat & SOS Mesh",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Encrypted Bluetooth text chat & store-and-forward relay",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SECTION 4C: WOMEN'S SAFETY JOURNEY CARD
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToWomensSafety
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEC4899).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFFEC4899),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Women's Safety Journey",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Protected arrival timer & 1091 helpline escalation",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SECTION 4D: TRUSTED CIRCLE CARD
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToTrustedCircle
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF38BDF8).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Trusted Circle & Live Map",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Mutual live GPS location sharing & staleness tracking",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SECTION 5: TRUSTED CONTACTS SUMMARY & LOCATION STATUS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Trusted Contacts Summary
                DashboardCard(
                    modifier = Modifier.weight(1f),
                    title = "Trusted Contacts",
                    subtitle = "$contactsCount registered",
                    icon = Icons.Default.People,
                    iconTint = EmergencyRed,
                    onClick = onNavigateToContacts
                )

                // Current Location Status
                DashboardCard(
                    modifier = Modifier.weight(1f),
                    title = "GPS Coordinates",
                    subtitle = if (isLocationReady) "Acquired & Ready ✓" else "Standby / Tap to check",
                    icon = Icons.Default.LocationOn,
                    iconTint = if (isLocationReady) StatusSuccess else Color(0xFF38BDF8),
                    onClick = {
                        homeViewModel.refreshLocation()
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SECTION 6: RECENT ALERTS SECTION
            Text(
                text = "RECENT ALERTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (latestAlert != null) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onNavigateToHistory
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(EmergencyRed.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = EmergencyRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = latestAlert!!.emergencyType,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${DateUtils.formatShortTime(latestAlert!!.createdAt)} • ${latestAlert!!.status}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCard)
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "No emergency alerts sent yet. Your history will be logged here.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

@Composable
private fun QuickActionChip(
    modifier: Modifier = Modifier,
    symbol: String,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = symbol, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DashboardCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }
    }
}
