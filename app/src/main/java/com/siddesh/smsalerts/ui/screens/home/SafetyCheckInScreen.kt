package com.siddesh.smsalerts.ui.screens.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
<<<<<<< HEAD
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
=======
>>>>>>> origin/main
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
<<<<<<< HEAD
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
=======
import androidx.compose.material.icons.filled.Info
>>>>>>> origin/main
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
<<<<<<< HEAD
=======
import androidx.compose.material3.CircularProgressIndicator
>>>>>>> origin/main
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
<<<<<<< HEAD
import androidx.compose.runtime.mutableIntStateOf
=======
>>>>>>> origin/main
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siddesh.smsalerts.ui.theme.CardBorder
import com.siddesh.smsalerts.ui.theme.EmergencyRed
import com.siddesh.smsalerts.ui.theme.StatusSuccess
import com.siddesh.smsalerts.ui.theme.StatusWarning
import com.siddesh.smsalerts.ui.theme.SurfaceCard
import com.siddesh.smsalerts.ui.theme.SurfaceCardElevated
import com.siddesh.smsalerts.ui.theme.TextMuted
import com.siddesh.smsalerts.ui.theme.TextPrimary
import com.siddesh.smsalerts.ui.theme.TextSecondary
import com.siddesh.smsalerts.ui.viewmodel.HomeViewModel
<<<<<<< HEAD
=======
import com.siddesh.smsalerts.utils.DateUtils
>>>>>>> origin/main
import com.siddesh.smsalerts.utils.HapticUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyCheckInScreen(
    homeViewModel: HomeViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val checkInState by homeViewModel.safetyCheckInState.collectAsState()
<<<<<<< HEAD
    val operationError by homeViewModel.safetyCheckInManager.operationError.collectAsState()

    var showCustomDialog by remember { mutableStateOf(false) }
    var customMinutesInput by remember { mutableStateOf("45") }
    var selectedGraceMinutes by remember { mutableIntStateOf(2) }
    var showEscalationInfoDialog by remember { mutableStateOf(false) }
    var isStartingTimer by remember { mutableStateOf(false) }

    LaunchedEffect(checkInState.isActive) {
        if (checkInState.isActive) {
            isStartingTimer = false
        }
    }
=======

    var showCustomDialog by remember { mutableStateOf(false) }
    var customMinutesInput by remember { mutableStateOf("45") }
    var showEscalationInfoDialog by remember { mutableStateOf(false) }
>>>>>>> origin/main

    // POST_NOTIFICATIONS permission launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Safety Check-In",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showEscalationInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Escalation Policy",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
<<<<<<< HEAD
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (operationError.isNotBlank()) Text(operationError, color = EmergencyRed)
            if (checkInState.isEscalated) {
                // ESCALATED EMERGENCY STATE
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(EmergencyRed.copy(alpha = 0.15f))
                        .border(2.dp, EmergencyRed, RoundedCornerShape(20.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = EmergencyRed,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "EMERGENCY SOS DISPATCHED",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = EmergencyRed
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Safety timer expired without confirmation. Emergency incident has been created and broadcast to all active trusted contacts.",
                            fontSize = 13.sp,
                            color = TextPrimary,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceCardElevated)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Incident: ${checkInState.escalatedIncidentId.take(12)}... • Open incident for delivery status",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Resolve Emergency button
                        Button(
                            onClick = {
                                HapticUtils.vibrateTick(context)
                                homeViewModel.resolveEscalatedCheckIn()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusSuccess,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("I AM SAFE — RESOLVE EMERGENCY", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            } else if (checkInState.isActive) {
                // ACTIVE CHECK-IN STATE
                val isGrace = checkInState.isGracePeriodActive
                val cardBorderColor = if (isGrace) EmergencyRed else if (checkInState.isPaused) StatusWarning else StatusSuccess
                val cardBgColor = if (isGrace) EmergencyRed.copy(alpha = 0.12f) else if (checkInState.isPaused) StatusWarning.copy(alpha = 0.08f) else StatusSuccess.copy(alpha = 0.12f)

=======
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (checkInState.isActive) {
                // ACTIVE CHECK-IN STATE
>>>>>>> origin/main
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
<<<<<<< HEAD
                        .background(cardBgColor)
                        .border(1.5.dp, cardBorderColor.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
=======
                        .background(StatusSuccess.copy(alpha = 0.12f))
                        .border(1.5.dp, StatusSuccess.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
>>>>>>> origin/main
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
<<<<<<< HEAD
                                imageVector = if (isGrace) Icons.Default.Warning else if (checkInState.isPaused) Icons.Default.Pause else Icons.Default.Timer,
                                contentDescription = null,
                                tint = if (isGrace) EmergencyRed else if (checkInState.isPaused) StatusWarning else StatusSuccess,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isGrace) "GRACE PERIOD ACTIVE" else if (checkInState.isPaused) "CHECK-IN PAUSED" else "SAFETY CHECK-IN ACTIVE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (isGrace) EmergencyRed else if (checkInState.isPaused) StatusWarning else StatusSuccess
=======
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = StatusSuccess,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Safety Check-In Active",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = StatusSuccess
>>>>>>> origin/main
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Countdown Display
<<<<<<< HEAD
                        val displaySeconds = if (isGrace) checkInState.graceRemainingSeconds else checkInState.remainingSeconds
                        val minutes = displaySeconds / 60
                        val seconds = displaySeconds % 60
                        Text(
                            text = "%02d:%02d".format(minutes, seconds),
                            fontSize = 46.sp,
=======
                        val minutes = checkInState.remainingSeconds / 60
                        val seconds = checkInState.remainingSeconds % 60
                        Text(
                            text = "%02d:%02d".format(minutes, seconds),
                            fontSize = 44.sp,
>>>>>>> origin/main
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )

                        Text(
<<<<<<< HEAD
                            text = if (isGrace) "Emergency alert will dispatch when grace expires!" else if (checkInState.isPaused) "Timer is paused. Tap resume to continue." else "Time remaining to confirm your safety",
                            fontSize = 12.sp,
                            fontWeight = if (isGrace) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isGrace) EmergencyRed else TextSecondary,
                            textAlign = TextAlign.Center
                        )

                        // Clear Active Deadline & Grace Period Display
                        if (checkInState.targetTimeMillis > 0) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val deadlineText = com.siddesh.smsalerts.utils.DateUtils.formatTimeOnly(checkInState.targetTimeMillis)
                            val graceInfo = if (checkInState.gracePeriodMinutes > 0) "${checkInState.gracePeriodMinutes} min grace" else "Immediate (no grace)"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceCardElevated)
                                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Deadline: $deadlineText • Grace: $graceInfo",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Primary Action: Confirm Safety
=======
                            text = if (checkInState.isExpired) "Check-in time expired!" else "Time remaining to confirm safety",
                            fontSize = 12.sp,
                            color = if (checkInState.isExpired) EmergencyRed else TextSecondary
                        )

                        Spacer(modifier = Modifier.height(20.dp))

>>>>>>> origin/main
                        Button(
                            onClick = {
                                HapticUtils.vibrateTick(context)
                                homeViewModel.markSafeOrCancelCheckIn()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusSuccess,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("I'M SAFE — CONFIRM SAFETY", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
<<<<<<< HEAD

                        Spacer(modifier = Modifier.height(12.dp))

                        // Controls: Pause/Resume and Cancel
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!isGrace) {
                                OutlinedButton(
                                    onClick = {
                                        HapticUtils.vibrateTick(context)
                                        if (checkInState.isPaused) {
                                            homeViewModel.resumeSafetyCheckIn()
                                        } else {
                                            homeViewModel.pauseSafetyCheckIn()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (checkInState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (checkInState.isPaused) "Resume" else "Pause", fontSize = 13.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    HapticUtils.vibrateTick(context)
                                    homeViewModel.markSafeOrCancelCheckIn()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cancel", fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                // INACTIVE STATE: SELECT DURATION & GRACE PERIOD
=======
                    }
                }
            } else {
                // INACTIVE STATE: SELECT DURATION
>>>>>>> origin/main
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceCard)
                        .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(EmergencyRed.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = EmergencyRed,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Set a Safety Timer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )

                        Text(
<<<<<<< HEAD
                            text = "Traveling through unfamiliar areas or meeting someone new? If the timer expires without confirmation, SafeAlerts automatically begins emergency escalation.",
=======
                            text = "Going for a run, meeting someone new, or traveling through an unfamiliar area? SafeAlert will alert you to verify your safety when the time expires.",
>>>>>>> origin/main
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

<<<<<<< HEAD
                // Grace Period Selection Header
                Text(
                    text = "GRACE PERIOD BEFORE ESCALATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                val graceOptions = listOf(
                    Pair("Immediate", 0),
                    Pair("1 min", 1),
                    Pair("2 mins", 2),
                    Pair("5 mins", 5)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    graceOptions.forEach { (label, mins) ->
                        val isSelected = selectedGraceMinutes == mins
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) EmergencyRed.copy(alpha = 0.2f) else SurfaceCard)
                                .border(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) EmergencyRed else CardBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    HapticUtils.vibrateTick(context)
                                    selectedGraceMinutes = mins
                                }
                                .padding(horizontal = 4.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) EmergencyRed else TextPrimary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

=======
>>>>>>> origin/main
                Text(
                    text = "SELECT CHECK-IN DURATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                val presetDurations = listOf(
                    Pair("15 Minutes", 15),
                    Pair("30 Minutes", 30),
                    Pair("1 Hour", 60),
                    Pair("2 Hours", 120)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presetDurations.forEach { (label, mins) ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceCard)
                                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                                .clickable {
<<<<<<< HEAD
                                    if (isStartingTimer) return@clickable
                                    isStartingTimer = true
                                    HapticUtils.vibrateTick(context)
                                    homeViewModel.startSafetyCheckIn(mins, selectedGraceMinutes)
=======
                                    HapticUtils.vibrateTick(context)
                                    homeViewModel.startSafetyCheckIn(mins)
>>>>>>> origin/main
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Alarm,
                                        contentDescription = null,
                                        tint = EmergencyRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = label,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = TextPrimary
                                    )
                                }

                                Text(
                                    text = "START",
                                    color = EmergencyRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // Custom duration option
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceCard)
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                            .clickable { showCustomDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Custom Duration...",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = TextPrimary
                                )
                            }

                            Text(
                                text = "CUSTOM",
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Escalation Policy Information Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceCardElevated)
                    .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = EmergencyRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
<<<<<<< HEAD
                            text = "Authoritative Escalation Architecture",
=======
                            text = "Safety Escalation Protocol",
>>>>>>> origin/main
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
<<<<<<< HEAD
                        text = "1. When duration expires, an urgent high-priority alarm notification sounds.\n" +
                                "2. If grace period is enabled, you receive the full grace duration to confirm safety.\n" +
                                "3. If no confirmation occurs, the app creates an incident and attempts authorized internet and enabled BLE delivery. Automatic carrier SMS is off.\n" +
                                "4. Operates in background with AlarmManager exact alarms across process restarts.",
=======
                        text = "1. When time expires, a persistent urgent device notification is issued.\n" +
                                "2. You can confirm \"I'm Safe\" at any time directly from the notification or app.\n" +
                                "3. If unconfirmed, SafeAlert prepares emergency dispatch with your live location.\n" +
                                "4. SafeAlert will never send unauthorized SMS without explicit confirmation.",
>>>>>>> origin/main
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Custom Duration Input Dialog
    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = {
                Text(
                    text = "Set Custom Duration",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter duration in minutes (e.g. 45):",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customMinutesInput,
                        onValueChange = { customMinutesInput = it.filter { ch -> ch.isDigit() } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EmergencyRed,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mins = customMinutesInput.toIntOrNull() ?: 30
<<<<<<< HEAD
                        if (mins > 0 && !isStartingTimer) {
                            isStartingTimer = true
                            homeViewModel.startSafetyCheckIn(mins, selectedGraceMinutes)
=======
                        if (mins > 0) {
                            homeViewModel.startSafetyCheckIn(mins)
>>>>>>> origin/main
                        }
                        showCustomDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Text("START TIMER")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = SurfaceCardElevated
        )
    }

    // Escalation Policy Modal Dialog
    if (showEscalationInfoDialog) {
        AlertDialog(
            onDismissRequest = { showEscalationInfoDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = EmergencyRed,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
<<<<<<< HEAD
                    text = "Safety Check-In Protection",
=======
                    text = "How Safety Check-In Protects You",
>>>>>>> origin/main
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
<<<<<<< HEAD
                        text = "Safety Check-In is backed by the durable Emergency Orchestration Engine. If you cannot confirm safety before the timer and grace period elapse, your emergency contacts are dispatched with your precise GPS coordinates, offline mesh broadcast begins, and emergency outbox records delivery status.",
=======
                        text = "Safety Check-In acts as a digital safety buddy. If you don't check in before the countdown runs out, the app sounds an alert and offers 1-tap SOS dispatch to your trusted contacts with your GPS location.",
>>>>>>> origin/main
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showEscalationInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Text("UNDERSTOOD")
                }
            },
            containerColor = SurfaceCardElevated
        )
    }
}
