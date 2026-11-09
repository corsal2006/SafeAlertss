package com.siddesh.smsalerts.ui.screens.sos

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import com.siddesh.smsalerts.utils.DateUtils
import com.siddesh.smsalerts.ui.theme.StatusInfo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
<<<<<<< HEAD
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
=======
>>>>>>> origin/main
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siddesh.smsalerts.data.model.EmergencyAlert
import com.siddesh.smsalerts.data.sms.SmsDispatchResult
import com.siddesh.smsalerts.ui.components.GlassCard
import com.siddesh.smsalerts.ui.theme.BackgroundDark
import com.siddesh.smsalerts.ui.theme.CardBorder
import com.siddesh.smsalerts.ui.theme.EmergencyRed
import com.siddesh.smsalerts.ui.theme.StatusError
import com.siddesh.smsalerts.ui.theme.StatusSuccess
import com.siddesh.smsalerts.ui.theme.SurfaceCard
import com.siddesh.smsalerts.ui.theme.SurfaceCardElevated
import com.siddesh.smsalerts.ui.theme.TextMuted
import com.siddesh.smsalerts.ui.theme.TextPrimary
import com.siddesh.smsalerts.ui.theme.TextSecondary
import com.siddesh.smsalerts.ui.viewmodel.SosStage
import com.siddesh.smsalerts.ui.viewmodel.SosViewModel

@Composable
fun SosCountdownScreen(
    emergencyType: String,
    customMessage: String,
    triggerSource: String = "BUTTON",
    sosViewModel: SosViewModel,
<<<<<<< HEAD
    onNavigateBack: () -> Unit,
    onIncident: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val stage by sosViewModel.stage.collectAsState()
    var attachedId by rememberSaveable { mutableStateOf<String?>(null) }
    var countdownTotal by rememberSaveable { mutableStateOf(3) }
    var wasActive by rememberSaveable { mutableStateOf(false) }
    var leaving by androidx.compose.runtime.remember { mutableStateOf(false) }
    fun leaveOnce() { if (!leaving) { leaving = true; onNavigateBack() } }
    LaunchedEffect(stage) {
        (stage as? SosStage.Countdown)?.secondsLeft?.let { countdownTotal = maxOf(countdownTotal, it) }
        if (stage is SosStage.Countdown || stage is SosStage.TriggerDetected || stage is SosStage.AcquiringLocation || stage is SosStage.TransmittingSms || stage is SosStage.SavingCloudAudit || stage is SosStage.Dispatching || stage is SosStage.EmergencyActive) {
            wasActive = true
        }
    }

    BackHandler {
        if (stage is SosStage.Countdown || stage is SosStage.TriggerDetected) {
            sosViewModel.cancelEmergency()
        } else leaveOnce()
    }

    LaunchedEffect(Unit) {
        if (attachedId != null) sosViewModel.resumeIncident(attachedId!!)
        else {
            sosViewModel.startCountdown(emergencyType, customMessage, triggerSource)
            attachedId = sosViewModel.incidentId
        }
=======
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val stage by sosViewModel.stage.collectAsState()

    BackHandler {
        if (stage is SosStage.Countdown) {
            sosViewModel.cancelEmergency()
        }
        onNavigateBack()
    }

    LaunchedEffect(Unit) {
        sosViewModel.startCountdown(emergencyType, customMessage, triggerSource)
>>>>>>> origin/main
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark),
        contentAlignment = Alignment.Center
    ) {
        when (val currentStage = stage) {
            is SosStage.Countdown -> {
                CountdownView(
                    secondsLeft = currentStage.secondsLeft,
<<<<<<< HEAD
                    totalSeconds = countdownTotal,
=======
>>>>>>> origin/main
                    emergencyType = emergencyType,
                    customMessage = customMessage,
                    triggerSource = triggerSource,
                    onConfirm = {
                        sosViewModel.dispatchNow(emergencyType, customMessage, triggerSource)
                    },
                    onCancel = {
                        sosViewModel.cancelEmergency()
<<<<<<< HEAD
=======
                        onNavigateBack()
>>>>>>> origin/main
                    }
                )
            }
            is SosStage.AcquiringLocation,
            is SosStage.TransmittingSms,
<<<<<<< HEAD
            is SosStage.SavingCloudAudit,
            is SosStage.Dispatching,
            is SosStage.TriggerDetected -> {
                DispatchingProgressView(stage = currentStage)
            }
            is SosStage.EmergencyActive -> {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Emergency active", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("Open the saved incident for actual channel outcomes and to resolve it when safe.", color = TextSecondary)
                    Button(onClick = { sosViewModel.incidentId?.let(onIncident) }, modifier = Modifier.fillMaxWidth()) { Text("Open incident") }
                    OutlinedButton(onClick = { leaveOnce() }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
                }
            }
=======
            is SosStage.SavingCloudAudit -> {
                DispatchingProgressView(stage = currentStage)
            }
>>>>>>> origin/main
            is SosStage.Completed -> {
                DispatchResultView(
                    alert = currentStage.alert,
                    smsResult = currentStage.smsResult,
<<<<<<< HEAD
                    onDone = { leaveOnce() }
=======
                    onDone = onNavigateBack
>>>>>>> origin/main
                )
            }
            is SosStage.Error -> {
                DispatchErrorView(
                    errorStage = currentStage,
                    emergencyType = emergencyType,
                    customMessage = customMessage,
                    onRetry = {
                        sosViewModel.startCountdown(emergencyType, customMessage)
                    },
                    onOpenFallbackSms = { intent ->
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Safely handled
                        }
                    },
<<<<<<< HEAD
                    onBack = { leaveOnce() }
                )
            }
            is SosStage.Cancelled,
            is SosStage.Resolved -> {
                if (wasActive) {
                    LaunchedEffect(Unit) {
                        leaveOnce()
                    }
                } else {
                    CircularProgressIndicator(color = EmergencyRed)
                }
            }
            is SosStage.Idle -> {
                if (attachedId != null) {
                    LaunchedEffect(Unit) {
                        leaveOnce()
                    }
                } else {
                    // Initial transition before startCountdown attaches; fallback to back navigation if stuck
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(3000)
                        if (stage is SosStage.Idle) {
                            leaveOnce()
                        }
                    }
                    CircularProgressIndicator(color = EmergencyRed)
=======
                    onBack = onNavigateBack
                )
            }
            is SosStage.Cancelled -> {
                LaunchedEffect(Unit) {
                    onNavigateBack()
>>>>>>> origin/main
                }
            }
        }
    }
}

@Composable
private fun CountdownView(
    secondsLeft: Int,
<<<<<<< HEAD
    totalSeconds: Int,
=======
>>>>>>> origin/main
    emergencyType: String,
    customMessage: String,
    triggerSource: String = "BUTTON",
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "countdown_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(
                text = "EMERGENCY ALERT CONFIRMATION",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = EmergencyRed,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Sending SOS Broadcast",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))

            val triggerLabel = when (triggerSource.uppercase()) {
                "VOICE" -> "Triggered by Voice Command 🎙️"
                "SHAKE" -> "Triggered by Device Shake 📳"
                "MEDICAL" -> "Shortcut: Medical 🚑"
                "ACCIDENT" -> "Shortcut: Accident 🚗"
                "FIRE" -> "Shortcut: Fire 🔥"
                else -> "Emergency Button 🔘"
            }

            Text(
                text = "$triggerLabel • Type: $emergencyType",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        val trackBorderColor = CardBorder
        Box(
            modifier = Modifier
                .size(170.dp)
                .scale(pulseScale),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = trackBorderColor,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )

<<<<<<< HEAD
                val sweep = (secondsLeft.toFloat() / totalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f) * 360f
=======
                val sweep = (secondsLeft / 5f) * 360f
>>>>>>> origin/main
                drawArc(
                    color = EmergencyRed,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$secondsLeft",
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Black,
                    color = EmergencyRed
                )
                Text(
                    text = "SECONDS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Pre-broadcast Confirmation Summary Card (Phase 9)
        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Alert Telemetry Preview",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = DateUtils.formatTimestamp(System.currentTimeMillis()),
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val previewMessage = if (customMessage.isNotBlank()) customMessage else "Emergency alert: I may need assistance. Please contact me as soon as possible."
                Text(
                    text = "\"$previewMessage\"",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = StatusInfo,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
<<<<<<< HEAD
                        text = "A location link is included when enabled and a fix is available",
=======
                        text = "Real-time GPS link will be appended to SMS",
>>>>>>> origin/main
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Button(
                onClick = onConfirm,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CONFIRM & DISPATCH NOW",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, CardBorder)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CANCEL EMERGENCY",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun DispatchingProgressView(stage: SosStage) {
    val stageTitle = when (stage) {
        is SosStage.AcquiringLocation -> "1/3 Acquiring GPS Location"
<<<<<<< HEAD
        is SosStage.TransmittingSms -> "2/3 Transmitting Emergency SMS"
        is SosStage.SavingCloudAudit -> "3/3 Storing Emergency Record"
        is SosStage.Dispatching -> "Broadcasting Multi-Transport SOS"
        is SosStage.EmergencyActive -> "Emergency Active (Relay & Live Location)"
        is SosStage.TriggerDetected -> "Emergency Trigger Detected"
=======
        is SosStage.TransmittingSms -> "2/3 Transmitting Native SMS"
        is SosStage.SavingCloudAudit -> "3/3 Storing Emergency Record"
>>>>>>> origin/main
        else -> "Processing..."
    }

    val stageDescription = when (stage) {
        is SosStage.AcquiringLocation -> "Connecting to Android Fused Location Provider for accurate GPS coordinates..."
<<<<<<< HEAD
        is SosStage.TransmittingSms -> "Transmitting direct emergency SMS alerts via cellular network to your trusted contacts..."
        is SosStage.SavingCloudAudit -> "Creating permanent encrypted audit record in Firebase Firestore..."
        is SosStage.Dispatching -> "Attempting enabled BLE transfers and authorized cloud sync..."
        is SosStage.EmergencyActive -> "Broadcasting live periodic coordinates and BLE store-and-forward packets..."
        is SosStage.TriggerDetected -> "Verifying trigger parameters and countdown..."
=======
        is SosStage.TransmittingSms -> "Executing cellular SMS Manager dispatch to configured emergency contacts..."
        is SosStage.SavingCloudAudit -> "Creating permanent encrypted audit record in Firebase Firestore..."
>>>>>>> origin/main
        else -> "Finalizing..."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(72.dp),
            color = EmergencyRed,
            strokeWidth = 5.dp
        )

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = stageTitle,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stageDescription,
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun DispatchResultView(
    alert: EmergencyAlert,
    smsResult: SmsDispatchResult,
    onDone: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(StatusSuccess.copy(alpha = 0.15f))
                .border(2.dp, StatusSuccess, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = StatusSuccess,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Emergency Alert Dispatched!",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Transmitted to ${smsResult.successfulCount} of ${smsResult.totalCount} emergency contacts via native SMS.",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = SurfaceCard
        ) {
            Column {
                Text(
                    text = "RECIPIENT DELIVERY STATUS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                smsResult.contactStatuses.forEach { contact ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Column {
                            Text(
                                text = contact.contactName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = contact.phoneNumber,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (contact.isSuccess) StatusSuccess.copy(alpha = 0.15f)
                                    else StatusError.copy(alpha = 0.15f)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = contact.status,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (contact.isSuccess) StatusSuccess else StatusError
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (alert.mapsUrl != null && !alert.mapsUrl.contains("Unavailable", ignoreCase = true)) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = SurfaceCard
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = EmergencyRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GPS Coordinates Attached",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Lat: ${alert.latitude ?: 0.0}, Long: ${alert.longitude ?: 0.0}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(alert.mapsUrl))
                            context.startActivity(mapIntent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open in Google Maps", fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
        ) {
            Text(
                text = "RETURN TO HOME",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color.White
            )
        }
    }
}

@Composable
private fun DispatchErrorView(
    errorStage: SosStage.Error,
    emergencyType: String,
    customMessage: String,
    onRetry: () -> Unit,
    onOpenFallbackSms: (Intent) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(StatusError.copy(alpha = 0.15f))
                .border(2.dp, StatusError, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = StatusError,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Alert Dispatch Issue",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = errorStage.reason,
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        if (errorStage.fallbackIntent != null) {
            val isLocationIntent = errorStage.fallbackIntent.action == android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
            val isSettingsIntent = errorStage.fallbackIntent.action == android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            val buttonText = when {
                isLocationIntent -> "ENABLE LOCATION IN SETTINGS"
                isSettingsIntent -> "OPEN APP SETTINGS"
<<<<<<< HEAD
                else -> "OPEN SMS APP (CARRIER CHARGES MAY APPLY)"
=======
                else -> "OPEN SYSTEM SMS APP"
>>>>>>> origin/main
            }
            val buttonIcon = when {
                isLocationIntent -> Icons.Default.MyLocation
                isSettingsIntent -> Icons.Default.Refresh
                else -> Icons.AutoMirrored.Filled.Message
            }

            Button(
                onClick = { onOpenFallbackSms(errorStage.fallbackIntent) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
            ) {
                Icon(buttonIcon, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = buttonText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (errorStage.canRetry) {
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("RETRY SOS ALERT")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        TextButton(onClick = onBack) {
            Text("Back to Dashboard", color = TextSecondary)
        }
    }
}
