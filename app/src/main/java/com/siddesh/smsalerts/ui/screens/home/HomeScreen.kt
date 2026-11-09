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
<<<<<<< HEAD
import androidx.compose.material.icons.automirrored.filled.Chat
=======
>>>>>>> origin/main
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
<<<<<<< HEAD
import androidx.compose.material.icons.filled.Mic
=======
>>>>>>> origin/main
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
<<<<<<< HEAD
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
=======
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
>>>>>>> origin/main
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
<<<<<<< HEAD
import androidx.compose.material3.CircularProgressIndicator
=======
>>>>>>> origin/main
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
<<<<<<< HEAD
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextAlign
=======
>>>>>>> origin/main
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
<<<<<<< HEAD
    onNavigateToTriggers: () -> Unit = onNavigateToSettings,
    onNavigateToWalkieTalkie: () -> Unit = {},
    onNavigateToWomensSafety: () -> Unit = {},
    onNavigateToTrustedCircle: () -> Unit = {},
    onNavigateToIncident: (String) -> Unit = {}
=======
    onNavigateToTriggers: () -> Unit = onNavigateToSettings
>>>>>>> origin/main
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
<<<<<<< HEAD
    val activeIncident by homeViewModel.activeIncident.collectAsState()

    val emergencyManager = remember { com.siddesh.smsalerts.service.EmergencyManager.getInstance(context) }
    val emergencyStage by emergencyManager.stage.collectAsState()

    LaunchedEffect(emergencyStage) {
        when (val s = emergencyStage) {
            is com.siddesh.smsalerts.ui.viewmodel.SosStage.TriggerDetected -> {
                onNavigateToCountdown(s.emergencyType, s.source)
            }
            is com.siddesh.smsalerts.ui.viewmodel.SosStage.Countdown -> {
                onNavigateToCountdown("PERSONAL", "AUTOMATIC")
            }
            else -> {}
        }
    }
=======

    val emergencyManager = remember { com.siddesh.smsalerts.service.EmergencyManager.getInstance(context) }
>>>>>>> origin/main

    // Voice Detector Lifecycle Integration
    val voiceDetector = remember {
        com.siddesh.smsalerts.data.voice.VoiceEmergencyDetector(
            context = context,
            onEmergencyPhraseDetected = { _ ->
                emergencyManager.triggerAutomaticEmergency("VOICE")
            }
        )
    }

<<<<<<< HEAD
    val voiceStatus by voiceDetector.status.collectAsState()

=======
>>>>>>> origin/main
    LaunchedEffect(userPreferences.shakeToAlertEnabled) {
        if (userPreferences.shakeToAlertEnabled) {
            com.siddesh.smsalerts.service.EmergencyProtectionService.startService(context)
        } else {
<<<<<<< HEAD
            if (!com.siddesh.smsalerts.service.EmergencyProtectionService.locationSessionActive) com.siddesh.smsalerts.service.EmergencyProtectionService.stopService(context)
=======
            com.siddesh.smsalerts.service.EmergencyProtectionService.stopService(context)
>>>>>>> origin/main
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
<<<<<<< HEAD
    var requiredFeatureForDialog by remember {
        mutableStateOf<com.siddesh.smsalerts.utils.AppPermissionFeature?>(null)
    }
    var showPermissionSetupDialog by remember {
        mutableStateOf(false)
    }
    var lastSosTapTime by remember { mutableStateOf(0L) }
    var isEmergencyCardExpanded by remember { mutableStateOf(false) }
    var showResolveConfirmDialog by remember { mutableStateOf(false) }
    var isResolvingIncident by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, userPreferences.voiceAlertEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                homeViewModel.refreshDashboard()
                if (userPreferences.voiceAlertEnabled && com.siddesh.smsalerts.utils.PermissionManager.hasRecordAudioPermission(context)) voiceDetector.startListening()
            } else if (event == Lifecycle.Event.ON_PAUSE) { voiceDetector.stopListening() }
=======
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
>>>>>>> origin/main
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
<<<<<<< HEAD
            onContinue = { showPermissionSetupDialog = false; onNavigateToCountdown("EMERGENCY", "BUTTON") },
=======
>>>>>>> origin/main
            onDismiss = {
                showPermissionSetupDialog = false
            },
            onPermissionsUpdated = {
                homeViewModel.refreshDashboard()
<<<<<<< HEAD
            }
        )
    }

    requiredFeatureForDialog?.let { feature ->
        com.siddesh.smsalerts.ui.components.SafeAlertPermissionDialog(
            feature = feature,
            onDismiss = { requiredFeatureForDialog = null },
            onPermissionGranted = {
                val f = feature
                requiredFeatureForDialog = null
                homeViewModel.refreshDashboard()
                if (f == com.siddesh.smsalerts.utils.AppPermissionFeature.LOCATION) {
                    if (!com.siddesh.smsalerts.utils.PermissionManager.hasSmsPermission(context) && userPreferences.directSmsEnabled) {
                        requiredFeatureForDialog = com.siddesh.smsalerts.utils.AppPermissionFeature.SMS
                    } else {
                        onNavigateToCountdown("EMERGENCY", "BUTTON")
                    }
                } else if (f == com.siddesh.smsalerts.utils.AppPermissionFeature.SMS) {
                    onNavigateToCountdown("EMERGENCY", "BUTTON")
=======
                if (PermissionManager.hasAllRequiredPermissions(context)) {
                    showPermissionSetupDialog = false
>>>>>>> origin/main
                }
            }
        )
    }

<<<<<<< HEAD
    if (showResolveConfirmDialog && activeIncident != null) {
        AlertDialog(
            onDismissRequest = { showResolveConfirmDialog = false },
            title = {
                Text("Confirm Safe Resolution", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                Text(
                    "Are you safe? Resolving this emergency will update your incident status to SAFE/RESOLVED and cancel ongoing dispatch notifications.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val incId = activeIncident?.incidentId
                        showResolveConfirmDialog = false
                        if (!incId.isNullOrBlank()) {
                            isResolvingIncident = true
                            homeViewModel.resolveActiveEmergency(incId)
                            isResolvingIncident = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess)
                ) {
                    Text("Yes, I'm Safe", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResolveConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceCardElevated
        )
    }

=======
>>>>>>> origin/main
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
<<<<<<< HEAD
            if (userPreferences.voiceAlertEnabled && voiceStatus.isNotBlank() && voiceStatus != "Voice off") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCard.copy(alpha = 0.8f))
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (voiceStatus.startsWith("Listening")) EmergencyRed else TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = voiceStatus,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

=======
>>>>>>> origin/main
            // Greeting & Safety Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
<<<<<<< HEAD
                Column(modifier = Modifier.weight(1f)) {
=======
                Column {
>>>>>>> origin/main
                    Text(
                        text = homeViewModel.getDynamicGreeting(),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
<<<<<<< HEAD
                        text = when {
                            !serviceReadiness.hasContacts -> "Action required • Configure contacts"
                            !serviceReadiness.hasLocationReady -> "Action required • Enable location"
                            else -> "System active • Ready for emergency"
                        },
                        fontSize = 13.sp,
                        color = if (serviceReadiness.isReady && serviceReadiness.hasLocationReady) StatusSuccess else StatusWarning,
=======
                        text = if (serviceReadiness.isReady) "System active • Ready for emergency" else "Action required • Configure contacts",
                        fontSize = 13.sp,
                        color = if (serviceReadiness.isReady) StatusSuccess else StatusWarning,
>>>>>>> origin/main
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
<<<<<<< HEAD
                                text = "Without internet, only compatible nearby BLE peers can receive queued alerts.",
=======
                                text = "Cellular SMS will transmit even without Internet connectivity.",
>>>>>>> origin/main
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

<<<<<<< HEAD
            Spacer(modifier = Modifier.height(14.dp))

            // ACTIVE EMERGENCY INCIDENT CARD (Only when an emergency is actually active/dispatching)
            activeIncident?.takeIf { it.state != "RESOLVED" && it.state != "CANCELLED" }?.let { incident ->
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    backgroundColor = EmergencyRed.copy(alpha = 0.16f),
                    borderColor = EmergencyRed
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Header row with pulsating indicator, title, category, timestamp and badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(EmergencyRed)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "EMERGENCY ACTIVE",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EmergencyRed,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Type: ${incident.emergencyType} • ${DateUtils.formatShortTime(incident.creationTimestamp)}",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }

                            // State Badge (pill)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(EmergencyRed.copy(alpha = 0.25f))
                                    .border(1.dp, EmergencyRed, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = incident.state.uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmergencyRed,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Short summary by default
                        val shortSummary = if (incident.messageText.isNotBlank()) {
                            if (incident.messageText.length > 110 && !isEmergencyCardExpanded) {
                                incident.messageText.take(110).trimEnd() + "…"
                            } else {
                                incident.messageText
                            }
                        } else {
                            "Emergency alerts dispatched. Live location tracking and recipient statuses active."
                        }

                        Text(
                            text = shortSummary,
                            fontSize = 12.sp,
                            color = TextPrimary,
                            lineHeight = 16.sp
                        )

                        // Expandable Details Section
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { isEmergencyCardExpanded = !isEmergencyCardExpanded }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isEmergencyCardExpanded) "Hide details ▲" else "View coordinates & status ▼",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF38BDF8)
                            )
                        }

                        AnimatedVisibility(visible = isEmergencyCardExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceDark.copy(alpha = 0.6f))
                                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (incident.latitude != null && incident.longitude != null &&
                                    com.siddesh.smsalerts.data.location.LocationQuality.coordinates(incident.latitude, incident.longitude)) {
                                    Text(
                                        text = "GPS: %.5f, %.5f (±%.0fm)".format(
                                            incident.latitude,
                                            incident.longitude,
                                            incident.accuracy ?: 0f
                                        ),
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    com.siddesh.smsalerts.ui.components.SafeAlertGoogleMap(
                                        modifier = Modifier.fillMaxWidth().height(180.dp),
                                        centerLatitude = incident.latitude,
                                        centerLongitude = incident.longitude,
                                        zoom = 15f,
                                        hasLocationPermission = isLocationReady,
                                        incidentLocation = Pair(incident.latitude, incident.longitude),
                                        incidentTitle = "Active SOS: ${incident.emergencyType}",
                                        isIncidentActive = true,
                                        showRecenterButton = false
                                    )
                                }
                                if (incident.transportStatuses.isNotEmpty()) {
                                    Text(
                                        text = "Channels: " + incident.transportStatuses.entries.joinToString(", ") { "${it.key}: ${it.value}" },
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                                if (incident.recipientStatuses.isNotEmpty()) {
                                    Text(
                                        text = "Recipients: " + incident.recipientStatuses.entries.joinToString(", ") { "…${it.key.takeLast(4)}: ${it.value}" },
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        // Responsive Action Buttons: VIEW INCIDENT & I'M SAFE (RESOLVE)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onNavigateToIncident(incident.incidentId) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "VIEW INCIDENT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    softWrap = true
                                )
                            }

                            OutlinedButton(
                                onClick = { showResolveConfirmDialog = true },
                                enabled = !isResolvingIncident,
                                modifier = Modifier
                                    .weight(1.2f)
                                    .heightIn(min = 44.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = StatusSuccess,
                                    containerColor = StatusSuccess.copy(alpha = 0.12f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, StatusSuccess),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                if (isResolvingIncident) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(15.dp),
                                        strokeWidth = 2.dp,
                                        color = StatusSuccess
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = StatusSuccess
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "I'M SAFE (RESOLVE)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusSuccess,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    softWrap = true
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
=======
            Spacer(modifier = Modifier.height(24.dp))
>>>>>>> origin/main

            // SECTION 2: MAIN VISIBLE "EMERGENCY ALERT" BUTTON
            SosButton(
                size = 205.dp,
                requireHold = requireHold,
                onSosTriggered = {
<<<<<<< HEAD
                    val now = System.currentTimeMillis()
                    if (now - lastSosTapTime > 2000L) {
                        lastSosTapTime = now
                        if (activeIncident != null) {
                            onNavigateToIncident(activeIncident!!.incidentId)
                        } else if (!com.siddesh.smsalerts.utils.PermissionManager.hasLocationPermission(context)) {
                            requiredFeatureForDialog = com.siddesh.smsalerts.utils.AppPermissionFeature.LOCATION
                        } else if (!com.siddesh.smsalerts.utils.PermissionManager.hasSmsPermission(context) && userPreferences.directSmsEnabled) {
                            requiredFeatureForDialog = com.siddesh.smsalerts.utils.AppPermissionFeature.SMS
                        } else {
                            onNavigateToCountdown("EMERGENCY", "BUTTON")
                        }
=======
                    if (PermissionManager.hasCoreEmergencyPermissions(context)) {
                        onNavigateToCountdown("EMERGENCY", "BUTTON")
                    } else {
                        showPermissionSetupDialog = true
>>>>>>> origin/main
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

<<<<<<< HEAD
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

=======
>>>>>>> origin/main
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

<<<<<<< HEAD
            Spacer(modifier = Modifier.height(24.dp))
=======
            Spacer(modifier = Modifier.height(88.dp))
>>>>>>> origin/main
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
