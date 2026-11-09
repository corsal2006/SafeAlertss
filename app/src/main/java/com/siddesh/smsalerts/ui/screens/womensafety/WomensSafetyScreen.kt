package com.siddesh.smsalerts.ui.screens.womensafety

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.siddesh.smsalerts.ui.components.MapPoint
import com.siddesh.smsalerts.data.location.GeocodedLocation
import com.siddesh.smsalerts.data.location.LocationResult
import com.siddesh.smsalerts.data.preferences.AppUserPreferences
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import com.siddesh.smsalerts.data.location.LocationService
import com.siddesh.smsalerts.data.location.OsmRoutingService
import com.siddesh.smsalerts.data.location.RouteResult
import com.siddesh.smsalerts.service.LiveLocationManager
import com.siddesh.smsalerts.service.WomensSafetyJourneyState
import com.siddesh.smsalerts.service.WomensSafetyManager
import com.siddesh.smsalerts.ui.components.SafeAlertGoogleMap
import com.siddesh.smsalerts.ui.theme.CardBorder
import com.siddesh.smsalerts.ui.theme.EmergencyRed
import com.siddesh.smsalerts.ui.theme.StatusSuccess
import com.siddesh.smsalerts.ui.theme.StatusWarning
import com.siddesh.smsalerts.ui.theme.SurfaceCard
import com.siddesh.smsalerts.ui.theme.SurfaceCardElevated
import com.siddesh.smsalerts.ui.theme.TextMuted
import com.siddesh.smsalerts.ui.theme.TextPrimary
import com.siddesh.smsalerts.ui.theme.TextSecondary
import com.siddesh.smsalerts.utils.DateUtils
import com.siddesh.smsalerts.utils.HapticUtils
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WomensSafetyScreen(
    onBackClick: () -> Unit,
    onNavigateToSos: () -> Unit = {}
) {
    val context = LocalContext.current
    val womensSafetyManager = remember { WomensSafetyManager(context) }
    DisposableEffect(womensSafetyManager) { onDispose { womensSafetyManager.closeObserver() } }
    val preferences by remember { UserPreferencesDataStore(context) }.preferencesFlow.collectAsState(AppUserPreferences())
    val journeyState by womensSafetyManager.journeyState.collectAsState()
    val operationError by womensSafetyManager.operationError.collectAsState()
    val tracker = remember { LiveLocationManager.getInstance(context) }
    val trackingActive by tracker.isSharing.collectAsState()
    val latestFix by tracker.latestLocationPoint.collectAsState()
    val trackingStatus by tracker.locationStatus.collectAsState()
    val locationRepo = remember { com.siddesh.smsalerts.data.location.LocationRepository.getInstance(context) }
    val lastValidFix by locationRepo.lastValidLocation.collectAsState()
    val isLocating by locationRepo.isLocating.collectAsState()

    var hasLocationPermission by remember {
        mutableStateOf(locationRepo.hasLocationPermission())
    }
    var userLocation by remember { mutableStateOf<MapPoint?>(null) }
    var fixDescription by remember { mutableStateOf("Location permission is needed to show your position.") }
    var locationRetry by remember { mutableIntStateOf(0) }
    var destinationLatLng by remember { mutableStateOf<MapPoint?>(null) }
    var destinationInput by rememberSaveable { mutableStateOf("") }
    var destinationSuggestions by remember { mutableStateOf<List<GeocodedLocation>>(emptyList()) }
    var isSearchingDestination by remember { mutableStateOf(false) }
    var calculatedRoute by remember { mutableStateOf<RouteResult?>(null) }
    var isCalculatingRoute by remember { mutableStateOf(false) }
    var routeStatus by remember { mutableStateOf("Tap the map to choose a destination. In-app routing is unavailable.") }

    var selectedDurationMinutes by rememberSaveable { mutableIntStateOf(30) }
    var isCustomDuration by rememberSaveable { mutableStateOf(false) }
    var selectedGracePeriodMinutes by rememberSaveable { mutableIntStateOf(2) }
    var shareLiveLocation by rememberSaveable { mutableStateOf(true) }
    var alertHelpline1091 by rememberSaveable { mutableStateOf(true) }

    var showCancelDialog by remember { mutableStateOf(false) }
    var showArrivedDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf<com.siddesh.smsalerts.utils.AppPermissionFeature?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val fine = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = fine || coarse
        locationRetry++
    }

    LaunchedEffect(latestFix, journeyState.isActive) {
        latestFix?.takeIf { journeyState.isActive }?.let {
            userLocation = MapPoint(it.latitude, it.longitude)
            fixDescription = "${if (it.isCached) "Cached" else "Recorded"} fix · ${DateUtils.formatFullDateTime(it.timestamp)} · accuracy ${it.accuracy ?: "unknown"} m"
        }
    }
    LaunchedEffect(journeyState.isActive, preferences.journeyLatitude, preferences.journeyLongitude) {
        if (journeyState.isActive) {
            val lat = preferences.journeyLatitude.toDoubleOrNull()
            val lon = preferences.journeyLongitude.toDoubleOrNull()
            if (lat != null && lon != null) destinationLatLng = MapPoint(lat, lon)
        }
    }

    // Refresh location fix
    LaunchedEffect(hasLocationPermission, locationRetry) {
        if (hasLocationPermission) {
            when (val res = locationRepo.refreshLocation()) {
                is LocationResult.Success -> {
                    userLocation = MapPoint(res.latitude, res.longitude)
                    fixDescription = "${res.description} · ${DateUtils.formatFullDateTime(res.timestamp)} · accuracy ${res.accuracy} m"
                }
                else -> fixDescription = res.explanation()
            }
        }
    }

    // Local coordinate parsing. Named destinations remain labels, without geocoding.
    LaunchedEffect(destinationInput) {
        val q = destinationInput.trim()
        if (q.length >= 3) {
            delay(500)
            isSearchingDestination = true
            destinationSuggestions = com.siddesh.smsalerts.data.location.GooglePlacesSearchService.searchDestinations(q)
            isSearchingDestination = false
        } else {
            destinationSuggestions = emptyList()
        }
    }

    // Route calculation when origin or destination changes
    LaunchedEffect(userLocation, destinationLatLng) {
        val u = userLocation
        val d = destinationLatLng
        if (u != null && d != null) {
            isCalculatingRoute = true
            routeStatus = "Checking route availability…"
            val result = OsmRoutingService.calculateRoute(
                u.latitude, u.longitude, d.latitude, d.longitude
            )
            calculatedRoute = result
            isCalculatingRoute = false
            routeStatus = if (result != null) {
                "Route: ${result.summary}"
            } else {
                "Routing service unavailable. Direct coordinates active."
            }
            if (result != null && !isCustomDuration) {
                val etaMinutes = ((result.durationSeconds + 59) / 60).toInt().coerceIn(5, 1440)
                selectedDurationMinutes = etaMinutes
            }
        } else {
            calculatedRoute = null
            if (destinationLatLng == null) {
                routeStatus = "Search for a destination or tap the map to calculate route and ETA."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = EmergencyRed,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Women's Safety Journey",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 18.sp
                        )
                    }
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
                    IconButton(onClick = {
                        val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${preferences.womensHelplineNumber}"))
                        context.startActivity(callIntent)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Open configured women's helpline dialer",
                            tint = EmergencyRed
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(fixDescription, color = TextSecondary, fontSize = 12.sp)
            if (journeyState.isActive && journeyState.shareLiveLocation) Text(trackingStatus, color = TextSecondary, fontSize = 12.sp)
            OutlinedButton(onClick = {
                hasLocationPermission = locationRepo.hasLocationPermission()
                if (!hasLocationPermission) locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                else if (!locationRepo.isGpsEnabled()) context.startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                else {
                    locationRetry++
                    if (journeyState.isActive && journeyState.shareLiveLocation) tracker.startLiveSharing("journey")
                }
            }) { Text("Retry / allow location") }
            // Operation error message
            if (!operationError.isNullOrBlank()) {
                val err = operationError!!
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(EmergencyRed.copy(alpha = 0.15f))
                        .border(1.dp, EmergencyRed, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = err, color = TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { womensSafetyManager.clearOperationError() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (journeyState.isActive) {
                // ACTIVE JOURNEY VIEW
                WomensSafetyActiveCard(
                    journeyState = journeyState,
                    trackingActive = trackingActive && journeyState.shareLiveLocation,
                    onArrivedClick = { showArrivedDialog = true },
                    onCancelClick = { showCancelDialog = true },
                    onExtendClick = {
                        womensSafetyManager.extendDuration(15)
                        HapticUtils.vibrateTick(context)
                    }
                )

                // Google Map for the active journey.
                SafeAlertGoogleMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    centerLatitude = userLocation?.latitude ?: lastValidFix?.latitude ?: 0.0,
                    centerLongitude = userLocation?.longitude ?: lastValidFix?.longitude ?: 0.0,
                    zoom = 14f,
                    hasLocationPermission = hasLocationPermission,
                    accuracyMeters = lastValidFix?.accuracy,
                    accuracyLabel = lastValidFix?.badgeLabel,
                    isLocating = isLocating,
                    onRefreshClick = { locationRetry++ },
                    originPoint = (userLocation ?: lastValidFix?.let { MapPoint(it.latitude, it.longitude) })?.let { Pair(it.latitude, it.longitude) },
                    destinationPoint = destinationLatLng?.let { Pair(it.latitude, it.longitude) },
                    destinationTitle = journeyState.destination.ifBlank { "Destination" },
                    routePolyline = calculatedRoute?.points ?: emptyList()
                )
            } else {
                // SETUP JOURNEY VIEW
                // Free map and coordinate selection.
                SafeAlertGoogleMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    centerLatitude = userLocation?.latitude ?: lastValidFix?.latitude ?: 0.0,
                    centerLongitude = userLocation?.longitude ?: lastValidFix?.longitude ?: 0.0,
                    zoom = 14f,
                    hasLocationPermission = hasLocationPermission,
                    accuracyMeters = lastValidFix?.accuracy,
                    accuracyLabel = lastValidFix?.badgeLabel,
                    isLocating = isLocating,
                    onRefreshClick = { locationRetry++ },
                    originPoint = (userLocation ?: lastValidFix?.let { MapPoint(it.latitude, it.longitude) })?.let { Pair(it.latitude, it.longitude) },
                    destinationPoint = destinationLatLng?.let { Pair(it.latitude, it.longitude) },
                    destinationTitle = destinationInput.ifBlank { "Selected Destination" },
                    routePolyline = calculatedRoute?.points ?: emptyList(),
                    onMapClick = { latLng ->
                        destinationLatLng = latLng
                        destinationInput = "Pinned: %.4f, %.4f".format(latLng.latitude, latLng.longitude)
                        destinationSuggestions = emptyList()
                        HapticUtils.vibrateTick(context)
                    }
                )

                // Route status banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceCardElevated)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isCalculatingRoute) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = EmergencyRed)
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.Navigation, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(text = routeStatus, color = TextSecondary, fontSize = 12.sp)
                    }
                }

                // Destination Search Input
                DestinationSearchCard(
                    destinationInput = destinationInput,
                    onDestinationChange = { destinationInput = it },
                    destinationSuggestions = destinationSuggestions,
                    isSearching = isSearchingDestination,
                    onSuggestionSelect = { place ->
                        destinationInput = place.displayName
                        destinationLatLng = MapPoint(place.latitude, place.longitude)
                        destinationSuggestions = emptyList()
                        HapticUtils.vibrateTick(context)
                    },
                    onQuickSelect = { name ->
                        destinationInput = name
                        destinationSuggestions = emptyList()
                    }
                )

                // Journey Duration Selector
                DurationSelectorCard(
                    selectedDuration = selectedDurationMinutes,
                    isCustom = isCustomDuration,
                    onDurationSelected = { mins, custom ->
                        selectedDurationMinutes = mins
                        isCustomDuration = custom
                        HapticUtils.vibrateTick(context)
                    },
                    onCustomStep = { delta ->
                        selectedDurationMinutes = (selectedDurationMinutes + delta).coerceIn(1, 1440)
                        HapticUtils.vibrateTick(context)
                    }
                )

                // Grace Period Selector
                GracePeriodSelectorCard(
                    selectedGrace = selectedGracePeriodMinutes,
                    onGraceSelected = { grace ->
                        selectedGracePeriodMinutes = grace
                        HapticUtils.vibrateTick(context)
                    }
                )

                // Toggles for Live Location & Helpline
                JourneyOptionsCard(
                    shareLocation = shareLiveLocation,
                    onShareLocationChange = { shareLiveLocation = it },
                    alertHelpline = alertHelpline1091,
                    onAlertHelplineChange = { alertHelpline1091 = it }
                )

                // Start Journey Button
                Button(
                    onClick = {
                        if (!com.siddesh.smsalerts.utils.PermissionManager.hasLocationPermission(context)) {
                            showPermissionDialog = com.siddesh.smsalerts.utils.AppPermissionFeature.LOCATION
                            return@Button
                        }
                        val destName = destinationInput.trim().ifBlank { "Designated Destination" }

                        womensSafetyManager.startJourney(
                            destination = destName,
                            durationMinutes = selectedDurationMinutes,
                            gracePeriodMinutes = selectedGracePeriodMinutes,
                            shareLocation = shareLiveLocation,
                            alertHelpline = alertHelpline1091,
                            latitude = destinationLatLng?.latitude,
                            longitude = destinationLatLng?.longitude
                        )
                        HapticUtils.vibrateEmergencyAlertSent(context)
                    },
                    enabled = destinationInput.isNotBlank() && selectedDurationMinutes in 1..1440,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Safety Journey",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Confirmation Dialogs
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel Journey?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to cancel the active journey? Emergency notifications will not be triggered.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        womensSafetyManager.markArrivedOrCancel()
                        showCancelDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) { Text("Confirm Cancel") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Keep Journey") }
            },
            containerColor = SurfaceCard
        )
    }

    if (showArrivedDialog) {
        AlertDialog(
            onDismissRequest = { showArrivedDialog = false },
            title = { Text("Confirm Safe Arrival?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Confirming arrival completes the journey safely and disables the countdown escalation.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        womensSafetyManager.markArrivedOrCancel()
                        showArrivedDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess)
                ) { Text("I've Arrived") }
            },
            dismissButton = {
                TextButton(onClick = { showArrivedDialog = false }) { Text("Dismiss") }
            },
            containerColor = SurfaceCard
        )
    }

    showPermissionDialog?.let { feature ->
        com.siddesh.smsalerts.ui.components.SafeAlertPermissionDialog(
            feature = feature,
            onDismiss = { showPermissionDialog = null },
            onPermissionGranted = {
                showPermissionDialog = null
                hasLocationPermission = true
                locationRetry++
            }
        )
    }
}

// -------------------------------------------------------------
// SUB-COMPOSABLES (Preventing compiler instruction limits)
// -------------------------------------------------------------

@Composable
fun WomensSafetyActiveCard(
    journeyState: WomensSafetyJourneyState,
    trackingActive: Boolean,
    onArrivedClick: () -> Unit,
    onCancelClick: () -> Unit,
    onExtendClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (journeyState.isGracePeriodActive) StatusWarning else EmergencyRed)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (journeyState.isGracePeriodActive) "GRACE PERIOD EXPIRING!" else "JOURNEY IN PROGRESS",
                        fontWeight = FontWeight.Bold,
                        color = if (journeyState.isGracePeriodActive) StatusWarning else EmergencyRed,
                        fontSize = 12.sp
                    )
                }
                if (trackingActive) {
                    Text(
                        text = "LIVE LOCATION ON",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusSuccess
                    )
                }
            }

            Text(
                text = journeyState.destination.ifBlank { "Journey in Progress" },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            // Timer display
            var now by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(journeyState.targetTimestamp) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
            val deadline = journeyState.targetTimestamp + if (journeyState.isGracePeriodActive) journeyState.gracePeriodMinutes * 60_000L else 0L
            val remainingSec = ((deadline - now) / 1000).coerceAtLeast(0)
            val hrs = remainingSec / 3600
            val mins = (remainingSec % 3600) / 60
            val secs = remainingSec % 60
            val formattedTime = if (hrs > 0) "%02d:%02d:%02d".format(hrs, mins, secs) else "%02d:%02d".format(mins, secs)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Remaining Time", color = TextMuted, fontSize = 12.sp)
                    Text(
                        text = formattedTime,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (journeyState.isGracePeriodActive) StatusWarning else TextPrimary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Target Arrival", color = TextMuted, fontSize = 12.sp)
                    Text(
                        text = DateUtils.formatTimeOnly(journeyState.targetTimestamp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onArrivedClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("I'm Safe", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onExtendClick,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCardElevated),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("+15m", color = TextPrimary, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onCancelClick,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel", color = EmergencyRed)
                }
            }
        }
    }
}

@Composable
fun DestinationSearchCard(
    destinationInput: String,
    onDestinationChange: (String) -> Unit,
    destinationSuggestions: List<GeocodedLocation>,
    isSearching: Boolean,
    onSuggestionSelect: (GeocodedLocation) -> Unit,
    onQuickSelect: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Destination",
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 14.sp
            )

            OutlinedTextField(
                value = destinationInput,
                onValueChange = onDestinationChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search Destination") },
                placeholder = { Text("e.g. Phoenix Marketcity, Kurla or Airport", color = TextMuted, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = EmergencyRed)
                },
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = EmergencyRed)
                    } else if (destinationInput.isNotBlank()) {
                        IconButton(onClick = { onDestinationChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmergencyRed,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )

            Text("Search for any destination, station, airport or landmark. Select a result to see the route, ETA, and pin on the map.", color = TextMuted, fontSize = 12.sp)
            // Coordinate suggestion parsed on this device.
            if (destinationSuggestions.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceCardElevated)
                        .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                ) {
                    destinationSuggestions.take(4).forEach { place ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSuggestionSelect(place) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = place.displayName,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Quick destinations row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Home", "Office", "Metro Station", "College Hostel", "Bus Terminal").forEach { preset ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCardElevated)
                            .clickable { onQuickSelect(preset) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(text = preset, color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DurationSelectorCard(
    selectedDuration: Int,
    isCustom: Boolean,
    onDurationSelected: (Int, Boolean) -> Unit,
    onCustomStep: (Int) -> Unit
) {
    val presets = listOf(15, 30, 45, 60)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Expected Travel Duration",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 14.sp
                )
                Text(
                    text = "$selectedDuration mins",
                    fontWeight = FontWeight.Bold,
                    color = EmergencyRed,
                    fontSize = 14.sp
                )
            }

            // Unified 5-button duration row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presets.forEach { mins ->
                    val isSelected = !isCustom && selectedDuration == mins
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) EmergencyRed else SurfaceCardElevated)
                            .border(1.dp, if (isSelected) EmergencyRed else CardBorder, RoundedCornerShape(8.dp))
                            .clickable { onDurationSelected(mins, false) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${mins}m",
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                // Custom option
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCustom) EmergencyRed else SurfaceCardElevated)
                        .border(1.dp, if (isCustom) EmergencyRed else CardBorder, RoundedCornerShape(8.dp))
                        .clickable { onDurationSelected(selectedDuration, true) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Custom",
                        fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCustom) Color.White else TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            // Compact custom stepper when active
            if (isCustom) {
                OutlinedTextField(
                    value = if (selectedDuration == 0) "" else selectedDuration.toString(),
                    onValueChange = { value -> if (value.length <= 4 && value.all(Char::isDigit)) onDurationSelected(value.toIntOrNull() ?: 0, true) },
                    label = { Text("Duration in minutes") },
                    supportingText = { Text("1–1,440 minutes") },
                    isError = selectedDuration !in 1..1440,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    singleLine = true
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceCardElevated)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Fine-tune duration:", color = TextSecondary, fontSize = 12.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { onCustomStep(-5) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "-5m", tint = TextPrimary)
                        }
                        Text(
                            text = "$selectedDuration min",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        IconButton(
                            onClick = { onCustomStep(5) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "+5m", tint = TextPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GracePeriodSelectorCard(
    selectedGrace: Int,
    onGraceSelected: (Int) -> Unit
) {
    val graceOptions = listOf(0, 1, 2, 5)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Grace Period Before Auto-Escalation",
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 14.sp
            )
            Text(
                text = "If duration expires, app waits this period before sending emergency SMS to your contacts.",
                color = TextMuted,
                fontSize = 11.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                graceOptions.forEach { mins ->
                    val isSelected = selectedGrace == mins
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) EmergencyRed else SurfaceCardElevated)
                            .border(1.dp, if (isSelected) EmergencyRed else CardBorder, RoundedCornerShape(8.dp))
                            .clickable { onGraceSelected(mins) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (mins == 0) "Immediate" else "${mins}m",
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun JourneyOptionsCard(
    shareLocation: Boolean,
    onShareLocationChange: (Boolean) -> Unit,
    alertHelpline: Boolean,
    onAlertHelplineChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceCard)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Share Live GPS Tracking", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Periodically updates coordinates for emergency contacts", color = TextMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = shareLocation,
                    onCheckedChange = onShareLocationChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = EmergencyRed)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Include configured women's helpline", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("181 availability varies by state. For immediate emergency assistance, use 112.", color = TextMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = alertHelpline,
                    onCheckedChange = onAlertHelplineChange,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = EmergencyRed)
                )
            }
        }
    }
}
