package com.siddesh.smsalerts.ui.screens.sos

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.siddesh.smsalerts.data.location.LocationResult
import com.siddesh.smsalerts.data.location.LocationService
import com.siddesh.smsalerts.data.model.AlertTemplateProvider
import com.siddesh.smsalerts.data.model.EmergencyAlert
import com.siddesh.smsalerts.data.model.EmergencyContact
import com.siddesh.smsalerts.data.model.EmergencyType
import com.siddesh.smsalerts.data.repository.AlertsRepository
import com.siddesh.smsalerts.data.repository.AuthRepository
import com.siddesh.smsalerts.data.sms.SmsSender
import com.siddesh.smsalerts.ui.theme.CardBorder
import com.siddesh.smsalerts.ui.theme.EmergencyRed
import com.siddesh.smsalerts.ui.theme.EmergencyRedDark
import com.siddesh.smsalerts.ui.theme.StatusError
import com.siddesh.smsalerts.ui.theme.StatusSuccess
import com.siddesh.smsalerts.ui.theme.StatusWarning
import com.siddesh.smsalerts.ui.theme.SurfaceCard
import com.siddesh.smsalerts.ui.theme.SurfaceCardElevated
import com.siddesh.smsalerts.ui.theme.TextMuted
import com.siddesh.smsalerts.ui.theme.TextPrimary
import com.siddesh.smsalerts.ui.theme.TextSecondary
import com.siddesh.smsalerts.ui.viewmodel.ContactsViewModel
import com.siddesh.smsalerts.ui.viewmodel.HomeViewModel
import com.siddesh.smsalerts.utils.HapticUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class SendAlertOutcome {
    data class DirectSmsSuccess(val recipientCount: Int, val mapsUrl: String?) : SendAlertOutcome()
    data class ComposerOpened(val recipientCount: Int, val message: String) : SendAlertOutcome()
    data class Failed(val reason: String) : SendAlertOutcome()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyReviewScreen(
    emergencyType: EmergencyType,
    contactsViewModel: ContactsViewModel,
    homeViewModel: HomeViewModel,
    onBackClick: () -> Unit,
    onNavigateToContacts: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()

    val userProfile by homeViewModel.userProfile.collectAsState()
    val allContacts by contactsViewModel.contacts.collectAsState()

    // Location state
    val locationService = remember { LocationService(context) }
    var isAcquiringLocation by remember { mutableStateOf(false) }
    var locationMapsUrl by remember { mutableStateOf<String?>(null) }
    var locationLat by remember { mutableStateOf<Double?>(null) }
    var locationLng by remember { mutableStateOf<Double?>(null) }
    var locationStatusText by remember { mutableStateOf("Initializing location...") }
    var showLocationDeniedDialog by remember { mutableStateOf(false) }
    var showGpsDisabledDialog by remember { mutableStateOf(false) }

    // Selected contacts
    val selectedContactIds = remember { mutableStateListOf<String>() }

    // Initialize selected contacts with all active contacts
    LaunchedEffect(allContacts) {
        if (selectedContactIds.isEmpty()) {
            val active = allContacts.filter { it.enabled }.map { it.id }
            selectedContactIds.clear()
            selectedContactIds.addAll(active)
        }
    }

    // Location fetch logic
    fun fetchLocation() {
        if (!locationService.hasLocationPermission()) {
            locationStatusText = "Location permission denied"
            showLocationDeniedDialog = true
            return
        }

        if (!locationService.isGpsEnabled()) {
            locationStatusText = "GPS disabled"
            showGpsDisabledDialog = true
            return
        }

        isAcquiringLocation = true
        locationStatusText = "Getting your location..."

        coroutineScope.launch {
            when (val res = locationService.getCurrentLocation(timeoutMs = 7000L)) {
                is LocationResult.Success -> {
                    locationLat = res.latitude
                    locationLng = res.longitude
                    locationMapsUrl = res.mapsUrl
                    locationStatusText = "Location attached ✓"
                }
                is LocationResult.PermissionDenied -> {
                    locationStatusText = "Location permission denied"
                    showLocationDeniedDialog = true
                }
                is LocationResult.GpsDisabled -> {
                    locationStatusText = "GPS disabled"
                    showGpsDisabledDialog = true
                }
                is LocationResult.Unavailable -> {
                    locationStatusText = "Location unavailable: ${res.reason}"
                }
            }
            isAcquiringLocation = false
        }
    }

    // Permission launcher for location
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchLocation()
        } else {
            locationStatusText = "Location permission denied"
            showLocationDeniedDialog = true
        }
    }

    // Initial check for location
    LaunchedEffect(Unit) {
        if (locationService.hasLocationPermission()) {
            fetchLocation()
        } else {
            locationStatusText = "Location permission not granted"
        }
    }

    // Message state
    var customMessage by remember {
        mutableStateOf(
            AlertTemplateProvider.formatMessage(
                type = emergencyType,
                userName = userProfile?.name.orEmpty(),
                mapsUrl = locationMapsUrl
            )
        )
    }
    var isEditingMessage by remember { mutableStateOf(false) }

    // Update message when location is acquired if not manually edited
    LaunchedEffect(locationMapsUrl) {
        if (!isEditingMessage && !locationMapsUrl.isNullOrBlank()) {
            customMessage = AlertTemplateProvider.formatMessage(
                type = emergencyType,
                userName = userProfile?.name.orEmpty(),
                mapsUrl = locationMapsUrl
            )
        }
    }

    // Sending state
    var isSending by remember { mutableStateOf(false) }
    var sendOutcome by remember { mutableStateOf<SendAlertOutcome?>(null) }
    var showSendConfirmationDialog by remember { mutableStateOf(false) }

    // Accidental trigger hold button state
    var holdProgress by remember { mutableFloatStateOf(0f) }
    val animatedHoldProgress by animateFloatAsState(targetValue = holdProgress, label = "holdProgress")

    val smsSender = remember { SmsSender(context) }
    val alertsRepository = remember { AlertsRepository() }
    val authRepository = remember { AuthRepository() }

    // Dispatch execution function
    fun executeSendAlert() {
        val selected = allContacts.filter { selectedContactIds.contains(it.id) }
        if (selected.isEmpty()) {
            Toast.makeText(context, "Please select at least one contact", Toast.LENGTH_SHORT).show()
            return
        }

        isSending = true
        coroutineScope.launch {
            val userId = authRepository.currentUserId.orEmpty()
            val phoneNumbers = selected.map { it.phoneNumber }
            val recipientIds = selected.map { it.id }

            val hasSmsPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED

            if (hasSmsPerm) {
                // Try direct SMS
                val dispatchResult = smsSender.sendEmergencySms(selected, customMessage)
                if (dispatchResult.successfulCount > 0) {
                    // Log to Firestore
                    val triggerSrc = when (emergencyType) {
                        EmergencyType.MEDICAL -> "MEDICAL"
                        EmergencyType.ACCIDENT -> "ACCIDENT"
                        EmergencyType.FIRE -> "FIRE"
                        else -> "BUTTON"
                    }

                    val alert = EmergencyAlert(
                        emergencyType = emergencyType.title,
                        triggerSource = triggerSrc,
                        message = customMessage,
                        recipientIds = recipientIds,
                        recipientPhoneNumbers = phoneNumbers,
                        latitude = locationLat,
                        longitude = locationLng,
                        mapsUrl = locationMapsUrl,
                        locationAvailable = !locationMapsUrl.isNullOrBlank(),
                        status = "SMS Sent",
                        sendMethod = "Direct SMS",
                        createdAt = System.currentTimeMillis()
                    )
                    if (userId.isNotBlank()) {
                        alertsRepository.saveEmergencyAlert(userId, alert)
                    }
                    HapticUtils.vibrateEmergencyAlertSent(context)
                    sendOutcome = SendAlertOutcome.DirectSmsSuccess(
                        recipientCount = dispatchResult.successfulCount,
                        mapsUrl = locationMapsUrl
                    )
                } else {
                    // Fallback to SMS composer
                    val fallbackIntent = smsSender.createFallbackSmsIntent(phoneNumbers, customMessage)
                    val triggerSrc = when (emergencyType) {
                        EmergencyType.MEDICAL -> "MEDICAL"
                        EmergencyType.ACCIDENT -> "ACCIDENT"
                        EmergencyType.FIRE -> "FIRE"
                        else -> "BUTTON"
                    }

                    val alert = EmergencyAlert(
                        emergencyType = emergencyType.title,
                        triggerSource = triggerSrc,
                        message = customMessage,
                        recipientIds = recipientIds,
                        recipientPhoneNumbers = phoneNumbers,
                        latitude = locationLat,
                        longitude = locationLng,
                        mapsUrl = locationMapsUrl,
                        locationAvailable = !locationMapsUrl.isNullOrBlank(),
                        status = "Message Composer Opened",
                        sendMethod = "SMS Composer Fallback",
                        createdAt = System.currentTimeMillis(),
                        failureReason = dispatchResult.failureReason
                    )
                    if (userId.isNotBlank()) {
                        alertsRepository.saveEmergencyAlert(userId, alert)
                    }
                    context.startActivity(fallbackIntent)
                    sendOutcome = SendAlertOutcome.ComposerOpened(
                        recipientCount = selected.size,
                        message = "Android messaging composer opened with recipient details pre-filled."
                    )
                }
            } else {
                // Direct SMS permission not granted -> Use official composer fallback
                val fallbackIntent = smsSender.createFallbackSmsIntent(phoneNumbers, customMessage)
                val triggerSrc = when (emergencyType) {
                    EmergencyType.MEDICAL -> "MEDICAL"
                    EmergencyType.ACCIDENT -> "ACCIDENT"
                    EmergencyType.FIRE -> "FIRE"
                    else -> "BUTTON"
                }

                val alert = EmergencyAlert(
                    emergencyType = emergencyType.title,
                    triggerSource = triggerSrc,
                    message = customMessage,
                    recipientIds = recipientIds,
                    recipientPhoneNumbers = phoneNumbers,
                    latitude = locationLat,
                    longitude = locationLng,
                    mapsUrl = locationMapsUrl,
                    locationAvailable = !locationMapsUrl.isNullOrBlank(),
                    status = "Message Composer Opened",
                    sendMethod = "SMS Composer",
                    createdAt = System.currentTimeMillis(),
                    failureReason = "SEND_SMS permission not granted; fallback composer launched."
                )
                if (userId.isNotBlank()) {
                    alertsRepository.saveEmergencyAlert(userId, alert)
                }
                context.startActivity(fallbackIntent)
                sendOutcome = SendAlertOutcome.ComposerOpened(
                    recipientCount = selected.size,
                    message = "Opened SMS composer for carrier dispatch."
                )
            }
            isSending = false
        }
    }

    // SEND_SMS permission launcher
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        executeSendAlert()
    }

    fun initiateSend() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            executeSendAlert()
        } else {
            // Request permission or fallback
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Review Emergency Alert",
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
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // SECTION 1: EMERGENCY CATEGORY BANNER
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(EmergencyRed.copy(alpha = 0.12f))
                    .border(1.5.dp, EmergencyRed.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(EmergencyRed.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emergencyType.symbol, fontSize = 26.sp)
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = emergencyType.title.uppercase(),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = EmergencyRed,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = emergencyType.description,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SECTION 2: LIVE LOCATION STATUS
            Text(
                text = "GPS LOCATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceCard)
                    .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isAcquiringLocation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = EmergencyRed
                            )
                        } else if (!locationMapsUrl.isNullOrBlank()) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = StatusSuccess,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.LocationOff,
                                contentDescription = null,
                                tint = StatusWarning,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = locationStatusText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!locationMapsUrl.isNullOrBlank()) StatusSuccess else TextPrimary
                            )
                            if (locationLat != null && locationLng != null) {
                                Text(
                                    text = "Lat: %.4f, Lng: %.4f".format(locationLat, locationLng),
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { fetchLocation() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Refresh", fontSize = 11.sp)
                        }
                    }

                    if (!locationMapsUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceCardElevated)
                                .clickable {
                                    locationMapsUrl?.let { uriHandler.openUri(it) }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = EmergencyRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = locationMapsUrl.orEmpty(),
                                fontSize = 11.sp,
                                color = EmergencyRed,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SECTION 3: RECIPIENT CONTACTS
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "RECIPIENTS (${selectedContactIds.size} OF ${allContacts.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )

                Text(
                    text = "+ Add Contact",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmergencyRed,
                    modifier = Modifier.clickable { onNavigateToContacts() }
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            if (allContacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(StatusWarning.copy(alpha = 0.1f))
                        .border(1.dp, StatusWarning.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = StatusWarning,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No trusted contacts configured yet",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Add at least one contact so SafeAlert knows who to notify.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onNavigateToContacts,
                            colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("ADD EMERGENCY CONTACT")
                        }
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    allContacts.forEach { contact ->
                        val isChecked = selectedContactIds.contains(contact.id)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isChecked) SurfaceCard else SurfaceCardElevated.copy(alpha = 0.5f))
                                .border(1.dp, if (isChecked) EmergencyRed.copy(alpha = 0.5f) else CardBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    if (isChecked) {
                                        selectedContactIds.remove(contact.id)
                                    } else {
                                        selectedContactIds.add(contact.id)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) selectedContactIds.add(contact.id)
                                    else selectedContactIds.remove(contact.id)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = EmergencyRed,
                                    uncheckedColor = TextMuted
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contact.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${contact.relationship} • ${contact.phoneNumber}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SECTION 4: REVIEW & EDIT EMERGENCY MESSAGE
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "EMERGENCY SMS TEXT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
                Text(
                    text = if (isEditingMessage) "Done" else "Edit Message",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmergencyRed,
                    modifier = Modifier.clickable { isEditingMessage = !isEditingMessage }
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = customMessage,
                onValueChange = {
                    customMessage = it
                    isEditingMessage = true
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmergencyRed,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION 5: ACCIDENTAL TRIGGER CONFIRMATION & SEND ACTION
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCardElevated)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "CONFIRM & TRANSMIT ALERT",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hold button for 2 seconds or tap for confirmation dialog to prevent accidental triggers.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2-Second Hold Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(EmergencyRed)
                            .pointerInput(selectedContactIds.size, isSending) {
                                if (selectedContactIds.isNotEmpty() && !isSending) {
                                    detectTapGestures(
                                        onTap = {
                                            // Fallback tap: opens confirmation dialog
                                            showSendConfirmationDialog = true
                                        },
                                        onPress = {
                                            val job = coroutineScope.launch {
                                                val startTime = System.currentTimeMillis()
                                                val duration = 2000L
                                                HapticUtils.vibrateTick(context)
                                                while (isActive) {
                                                    val elapsed = System.currentTimeMillis() - startTime
                                                    val fraction = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                                                    holdProgress = fraction
                                                    if (fraction >= 1f) {
                                                        HapticUtils.vibrateEmergencyAlertSent(context)
                                                        initiateSend()
                                                        break
                                                    }
                                                    delay(40)
                                                }
                                            }
                                            tryAwaitRelease()
                                            job.cancel()
                                            holdProgress = 0f
                                        }
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Progress fill overlay
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedHoldProgress)
                                .height(58.dp)
                                .background(EmergencyRedDark)
                                .align(Alignment.CenterStart)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "TRANSMITTING ALERT...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (holdProgress > 0f) "HOLD TO SEND (${(animatedHoldProgress * 100).toInt()}%)" else "HOLD 2S OR TAP TO SEND ALERT",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Location Denied Dialog
    if (showLocationDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showLocationDeniedDialog = false },
            title = { Text("Location Permission Denied", fontWeight = FontWeight.Bold) },
            text = {
                Text("SafeAlert attaches a Google Maps link to your SMS so responders know where you are. Send alert without location?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                        showLocationDeniedDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Text("GRANT PERMISSION")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLocationDeniedDialog = false
                    locationStatusText = "Sending without location"
                }) {
                    Text("SEND WITHOUT LOCATION", color = TextSecondary)
                }
            },
            containerColor = SurfaceCardElevated
        )
    }

    // GPS Disabled Dialog
    if (showGpsDisabledDialog) {
        AlertDialog(
            onDismissRequest = { showGpsDisabledDialog = false },
            title = { Text("GPS Is Unavailable", fontWeight = FontWeight.Bold) },
            text = {
                Text("Device GPS is currently turned off or unable to acquire satellites. Would you like to proceed without coordinates?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGpsDisabledDialog = false
                        locationStatusText = "Sending without location"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Text("SEND WITHOUT LOCATION")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showGpsDisabledDialog = false
                    fetchLocation()
                }) {
                    Text("RETRY GPS", color = TextSecondary)
                }
            },
            containerColor = SurfaceCardElevated
        )
    }

    // Tap-Trigger Confirmation Dialog
    if (showSendConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showSendConfirmationDialog = false },
            title = { Text("Confirm Emergency Dispatch", fontWeight = FontWeight.Bold, color = TextPrimary) },
            text = {
                Column {
                    Text("Are you sure you want to broadcast this alert?")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("• Type: ${emergencyType.title}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("• Recipients: ${selectedContactIds.size} contact(s)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("• Location: ${if (!locationMapsUrl.isNullOrBlank()) "Attached ✓" else "None"}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSendConfirmationDialog = false
                        initiateSend()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Text("CONFIRM & SEND NOW")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSendConfirmationDialog = false }) {
                    Text("CANCEL", color = TextSecondary)
                }
            },
            containerColor = SurfaceCardElevated
        )
    }

    // Send Outcome Dialog
    sendOutcome?.let { outcome ->
        AlertDialog(
            onDismissRequest = { sendOutcome = null },
            icon = {
                Icon(
                    imageVector = when (outcome) {
                        is SendAlertOutcome.DirectSmsSuccess -> Icons.Default.CheckCircle
                        is SendAlertOutcome.ComposerOpened -> Icons.Default.OpenInNew
                        is SendAlertOutcome.Failed -> Icons.Default.Close
                    },
                    contentDescription = null,
                    tint = when (outcome) {
                        is SendAlertOutcome.DirectSmsSuccess -> StatusSuccess
                        is SendAlertOutcome.ComposerOpened -> StatusWarning
                        is SendAlertOutcome.Failed -> StatusError
                    },
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = when (outcome) {
                        is SendAlertOutcome.DirectSmsSuccess -> "Emergency SMS Sent"
                        is SendAlertOutcome.ComposerOpened -> "Message Composer Opened"
                        is SendAlertOutcome.Failed -> "Alert Transmission Failed"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = when (outcome) {
                            is SendAlertOutcome.DirectSmsSuccess ->
                                "Direct SMS successfully transmitted to ${outcome.recipientCount} recipient(s) with live Google Maps coordinate telemetry."
                            is SendAlertOutcome.ComposerOpened ->
                                outcome.message
                            is SendAlertOutcome.Failed ->
                                outcome.reason
                        },
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        sendOutcome = null
                        onNavigateToHistory()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("VIEW IN HISTORY")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    sendOutcome = null
                    onNavigateToHome()
                }) {
                    Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("HOME", color = TextSecondary)
                }
            },
            containerColor = SurfaceCardElevated
        )
    }
}
