package com.siddesh.smsalerts.ui.screens.womensafety

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siddesh.smsalerts.service.EmergencyManager
import com.siddesh.smsalerts.service.WomensSafetyManager
import com.siddesh.smsalerts.ui.theme.CardBorder
import com.siddesh.smsalerts.ui.theme.EmergencyRed
import com.siddesh.smsalerts.ui.theme.StatusSuccess
import com.siddesh.smsalerts.ui.theme.StatusWarning
import com.siddesh.smsalerts.ui.theme.SurfaceCard
import com.siddesh.smsalerts.ui.theme.SurfaceCardElevated
import com.siddesh.smsalerts.ui.theme.TextMuted
import com.siddesh.smsalerts.ui.theme.TextPrimary
import com.siddesh.smsalerts.ui.theme.TextSecondary
import com.siddesh.smsalerts.utils.HapticUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WomensSafetyScreen(
    onBackClick: () -> Unit,
    onNavigateToSos: () -> Unit = {}
) {
    val context = LocalContext.current
    val womensSafetyManager = remember { WomensSafetyManager(context) }
    val journeyState by womensSafetyManager.journeyState.collectAsState()

    var destinationInput by remember { mutableStateOf("") }
    var selectedDurationMinutes by remember { mutableIntStateOf(30) }
    var selectedGraceMinutes by remember { mutableIntStateOf(2) }
    var shareLiveLocation by remember { mutableStateOf(true) }
    var alertHelpline1091 by remember { mutableStateOf(true) }
    var showEscalationInfo by remember { mutableStateOf(false) }

    // Live ticking loop
    LaunchedEffect(journeyState.isActive) {
        while (isActive && journeyState.isActive) {
            womensSafetyManager.updateRemainingTime()
            delay(1000L)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Women's Safety Journey",
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
                    IconButton(onClick = { showEscalationInfo = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Protection Details",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (journeyState.isActive) {
                // ACTIVE JOURNEY STATE
                val isGrace = journeyState.isGracePeriodActive
                val borderColor = if (isGrace) EmergencyRed else StatusSuccess
                val bgColor = if (isGrace) EmergencyRed.copy(alpha = 0.12f) else StatusSuccess.copy(alpha = 0.12f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(bgColor)
                        .border(1.5.dp, borderColor.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isGrace) Icons.Default.Warning else Icons.Default.DirectionsWalk,
                                contentDescription = null,
                                tint = if (isGrace) EmergencyRed else StatusSuccess,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isGrace) "JOURNEY OVERDUE — GRACE ACTIVE" else "JOURNEY IN PROGRESS",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (isGrace) EmergencyRed else StatusSuccess
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Destination: ${journeyState.destination.ifBlank { "Planned Destination" }}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Countdown Timer
                        val displaySeconds = if (isGrace) journeyState.graceRemainingSeconds else journeyState.remainingSeconds
                        val minutes = displaySeconds / 60
                        val seconds = displaySeconds % 60
                        Text(
                            text = "%02d:%02d".format(minutes, seconds),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = if (isGrace) "Emergency alerts dispatch when grace period expires!" else "Time remaining to confirm safe arrival",
                            fontSize = 12.sp,
                            fontWeight = if (isGrace) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isGrace) EmergencyRed else TextSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Arrived Button
                        Button(
                            onClick = {
                                HapticUtils.vibrateTick(context)
                                womensSafetyManager.markArrivedOrCancel()
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
                            Text("I'VE ARRIVED SAFELY", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Extension & Cancel Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    HapticUtils.vibrateTick(context)
                                    womensSafetyManager.extendDuration(10)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text("+10m", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    HapticUtils.vibrateTick(context)
                                    womensSafetyManager.extendDuration(15)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text("+15m", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    HapticUtils.vibrateTick(context)
                                    womensSafetyManager.markArrivedOrCancel()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text("Cancel", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Immediate SOS Trigger Button
                        Button(
                            onClick = {
                                HapticUtils.vibrateTick(context)
                                EmergencyManager.getInstance(context).triggerEmergency(
                                    triggerSource = "WOMENS_SAFETY_MANUAL",
                                    emergencyType = "WOMEN'S SAFETY IMMEDIATE SOS",
                                    customMessage = "Immediate SOS triggered during Women's Safety Journey to ${journeyState.destination}!",
                                    bypassCountdown = false
                                )
                                onNavigateToSos()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("TRIGGER IMMEDIATE SOS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                // INACTIVE STATE: PLAN JOURNEY
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
                                .background(Color(0xFFEC4899).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFFEC4899),
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Protected Journey Guard",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )

                        Text(
                            text = "Traveling alone, returning late, or heading to an unfamiliar destination? Set your expected route arrival. If you do not confirm arrival before the deadline, emergency escalation begins automatically.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Destination Input
                Text(
                    text = "WHERE ARE YOU HEADING?",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = destinationInput,
                    onValueChange = { destinationInput = it },
                    placeholder = { Text("e.g. Home, Hostel, City Metro Station, Office...", fontSize = 13.sp, color = TextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFEC4899))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFEC4899),
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Duration Selector
                Text(
                    text = "ESTIMATED JOURNEY TIME",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                val durations = listOf(
                    Pair("15 min", 15),
                    Pair("30 min", 30),
                    Pair("45 min", 45),
                    Pair("60 min", 60)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    durations.forEach { (label, mins) ->
                        val isSelected = selectedDurationMinutes == mins
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFFEC4899).copy(alpha = 0.15f) else SurfaceCard)
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFFEC4899) else CardBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    HapticUtils.vibrateTick(context)
                                    selectedDurationMinutes = mins
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFFEC4899) else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Grace Period Selector
                Text(
                    text = "EXTRA GRACE PERIOD BEFORE SOS ESCALATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                val graceOptions = listOf(
                    Pair("Immediate (0m)", 0),
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
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) EmergencyRed.copy(alpha = 0.15f) else SurfaceCard)
                                .border(
                                    1.dp,
                                    if (isSelected) EmergencyRed else CardBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    HapticUtils.vibrateTick(context)
                                    selectedGraceMinutes = mins
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) EmergencyRed else TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Safety Options Toggles
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceCardElevated)
                        .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Live GPS Location Tracking", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                Text("Record updates at 15s cadence and include in emergency dispatch", fontSize = 11.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = shareLiveLocation,
                                onCheckedChange = { shareLiveLocation = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFEC4899), checkedTrackColor = Color(0xFFEC4899).copy(alpha = 0.3f))
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Women's Helpline (1091) Alert", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                Text("Include regional women's safety hotline in escalation workflow", fontSize = 11.sp, color = TextSecondary)
                            }
                            Switch(
                                checked = alertHelpline1091,
                                onCheckedChange = { alertHelpline1091 = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFEC4899), checkedTrackColor = Color(0xFFEC4899).copy(alpha = 0.3f))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Start Protected Journey Button
                Button(
                    onClick = {
                        HapticUtils.vibrateTick(context)
                        val dest = destinationInput.trim().ifBlank { "Unspecified Destination" }
                        womensSafetyManager.startJourney(
                            destination = dest,
                            durationMinutes = selectedDurationMinutes,
                            gracePeriodMinutes = selectedGraceMinutes,
                            shareLocation = shareLiveLocation,
                            alertHelpline = alertHelpline1091
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "START PROTECTED JOURNEY",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Escalation info card
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
                            tint = Color(0xFFEC4899),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Women's Safety Escalation Protocol",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "1. Your estimated arrival deadline is persisted across app restarts.\n" +
                                "2. If arrival is not confirmed at deadline, an urgent notification and grace period sound.\n" +
                                "3. If still unconfirmed, EmergencyManager dispatches emergency SMS with live GPS link to your enabled contacts and initiates Women's Helpline 1091 escalation.\n" +
                                "4. Offline BLE mesh broadcasts encrypted distress packets to nearby SafeAlerts devices.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showEscalationInfo) {
        AlertDialog(
            onDismissRequest = { showEscalationInfo = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFFEC4899),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "How Women's Safety Journey Protects You",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This feature provides an automated safety net for travel. By setting your destination and estimated time, the system acts as an independent watchdog that alerts your circle if you don't check in upon arrival.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showEscalationInfo = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899))
                ) {
                    Text("UNDERSTOOD")
                }
            },
            containerColor = SurfaceCardElevated
        )
    }
}
