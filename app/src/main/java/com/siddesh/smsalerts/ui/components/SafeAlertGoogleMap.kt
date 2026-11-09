package com.siddesh.smsalerts.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.siddesh.smsalerts.data.location.LocationQuality
import com.siddesh.smsalerts.data.model.TrustedCircleMember
import com.siddesh.smsalerts.ui.theme.CardBorder
import com.siddesh.smsalerts.ui.theme.EmergencyRed
import com.siddesh.smsalerts.ui.theme.StatusSuccess
import com.siddesh.smsalerts.ui.theme.StatusWarning
import com.siddesh.smsalerts.ui.theme.SurfaceDark
import com.siddesh.smsalerts.ui.theme.TextPrimary
import com.siddesh.smsalerts.ui.theme.TextSecondary
import com.siddesh.smsalerts.utils.DateUtils
import kotlinx.coroutines.launch

/**
 * Production Google Maps component for SafeAlerts.
 * Uses official Google Maps SDK for Android with native street-map tiles,
 * genuine GPS markers, circle members, active SOS incident locations,
 * non-disruptive camera tracking, accuracy radius circle, and clean error/permission state handling.
 */
@Composable
fun SafeAlertGoogleMap(
    modifier: Modifier = Modifier,
    centerLatitude: Double = 0.0,
    centerLongitude: Double = 0.0,
    zoom: Float = 14f,
    hasLocationPermission: Boolean = false,
    accuracyMeters: Float? = null,
    accuracyLabel: String? = null,
    isLocating: Boolean = false,
    originPoint: Pair<Double, Double>? = null,
    originTitle: String = "My location",
    destinationPoint: Pair<Double, Double>? = null,
    destinationTitle: String = "Destination",
    routePolyline: List<Pair<Double, Double>> = emptyList(),
    circleMembers: List<TrustedCircleMember> = emptyList(),
    incidentLocation: Pair<Double, Double>? = null,
    incidentTitle: String = "Incident",
    isIncidentActive: Boolean = false,
    onMapClick: ((MapPoint) -> Unit)? = null,
    onMemberClick: ((TrustedCircleMember) -> Unit)? = null,
    onRefreshClick: (() -> Unit)? = null,
    showRecenterButton: Boolean = true
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Verify Google Play Services availability
    val playServicesCode = remember {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
    }
    val isPlayServicesAvailable = playServicesCode == ConnectionResult.SUCCESS

    if (!isPlayServicesAvailable) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = StatusWarning, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Google Play Services Unavailable",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Google Maps requires Google Play Services (code: $playServicesCode).",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
        return
    }

    // Determine if valid coordinate exists
    val hasCenter = LocationQuality.coordinates(centerLatitude, centerLongitude)
    val hasOrigin = originPoint != null && LocationQuality.coordinates(originPoint.first, originPoint.second)
    val hasIncident = incidentLocation != null && LocationQuality.coordinates(incidentLocation.first, incidentLocation.second)
    val hasCircleMember = circleMembers.any { it.lastLatitude != null && it.lastLongitude != null && LocationQuality.coordinates(it.lastLatitude, it.lastLongitude) }
    val hasValidCoordinates = hasCenter || hasOrigin || hasIncident || hasCircleMember

    if (!hasValidCoordinates) {
        // Honest GPS acquisition/standby banner without falling back to Bengaluru or (0,0)
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isLocating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = Color(0xFF38BDF8)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = if (hasLocationPermission) Color(0xFF38BDF8) else StatusWarning,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (isLocating) "Acquiring GPS Fix..." else "Acquiring Accurate GPS Position",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        !hasLocationPermission -> "Location permission is required to pinpoint your exact position on Google Maps."
                        isLocating -> "Requesting satellite fix via FusedLocationProviderClient..."
                        else -> "Waiting for GPS satellite fix. Move outdoors or near a window and retry."
                    },
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                if (onRefreshClick != null && !isLocating) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onRefreshClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Refresh GPS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    // Determine target center coordinate from actual data
    val targetCenter = when {
        hasCenter -> LatLng(centerLatitude, centerLongitude)
        hasIncident -> LatLng(incidentLocation!!.first, incidentLocation.second)
        hasOrigin -> LatLng(originPoint!!.first, originPoint.second)
        hasCircleMember -> {
            val m = circleMembers.first { it.lastLatitude != null && it.lastLongitude != null && LocationQuality.coordinates(it.lastLatitude, it.lastLongitude) }
            LatLng(m.lastLatitude!!, m.lastLongitude!!)
        }
        else -> LatLng(0.0, 0.0)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(targetCenter, zoom)
    }

    // Keep track of whether the user has manually panned/zoomed the camera
    var userInteracted by remember { mutableStateOf(false) }

    // When new coordinates arrive from live tracking, update the camera ONLY if user is not actively exploring
    LaunchedEffect(targetCenter) {
        if (!userInteracted && LocationQuality.coordinates(targetCenter.latitude, targetCenter.longitude)) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(targetCenter, zoom),
                durationMs = 800
            )
        }
    }

    // Detect user gestures
    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving && cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
            userInteracted = true
        }
    }

    val uiSettings = remember {
        MapUiSettings(
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
            compassEnabled = true,
            rotationGesturesEnabled = true,
            scrollGesturesEnabled = true,
            tiltGesturesEnabled = true,
            zoomGesturesEnabled = true
        )
    }

    // Guard myLocationEnabled with actual system runtime permission check to prevent SecurityException
    val hasSystemLocationPerm = remember(context) {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    val mapProperties = remember(hasLocationPermission, hasSystemLocationPerm) {
        MapProperties(
            isMyLocationEnabled = hasLocationPermission && hasSystemLocationPerm,
            mapType = MapType.NORMAL
        )
    }

    Box(modifier = modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, CardBorder, RoundedCornerShape(12.dp))) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = uiSettings,
            onMapClick = { latLng ->
                if (onMapClick != null && LocationQuality.coordinates(latLng.latitude, latLng.longitude)) {
                    onMapClick(MapPoint(latLng.latitude, latLng.longitude))
                }
            }
        ) {
            // Accuracy circle around current target center if reported
            if (accuracyMeters != null && accuracyMeters > 0 && accuracyMeters.isFinite()) {
                Circle(
                    center = targetCenter,
                    radius = accuracyMeters.toDouble(),
                    fillColor = Color(0x2238BDF8),
                    strokeColor = Color(0x8838BDF8),
                    strokeWidth = 2f
                )
            }

            // Origin marker (only drawn if myLocationEnabled is false or if originTitle is customized to avoid duplicate blue dots)
            originPoint?.let { (lat, lon) ->
                if (LocationQuality.coordinates(lat, lon) && (!mapProperties.isMyLocationEnabled || originTitle != "My location")) {
                    Marker(
                        state = rememberMarkerState(key = "origin_${lat}_$lon", position = LatLng(lat, lon)),
                        title = originTitle,
                        snippet = "Lat: %.5f, Lon: %.5f".format(lat, lon),
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                    )
                }
            }

            // Destination marker (e.g. journey destination)
            destinationPoint?.let { (lat, lon) ->
                if (LocationQuality.coordinates(lat, lon)) {
                    Marker(
                        state = rememberMarkerState(key = "dest_${lat}_$lon", position = LatLng(lat, lon)),
                        title = destinationTitle,
                        snippet = "Lat: %.5f, Lon: %.5f".format(lat, lon),
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    )
                }
            }

            // Active SOS incident marker
            incidentLocation?.let { (lat, lon) ->
                if (LocationQuality.coordinates(lat, lon)) {
                    Marker(
                        state = rememberMarkerState(key = "incident_${lat}_$lon", position = LatLng(lat, lon)),
                        title = incidentTitle,
                        snippet = if (isIncidentActive) "CRITICAL: Active SOS Location" else "SOS Incident Location",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    )
                }
            }

            // Trusted Circle members' locations
            circleMembers.filter { it.isSharingLocationWithMe }.forEach { member ->
                val lat = member.lastLatitude
                val lon = member.lastLongitude
                if (lat != null && lon != null && LocationQuality.coordinates(lat, lon)) {
                    val isStale = member.isLocationStale
                    val isEmergency = member.activeIncidentId != null
                    val timeStr = if (member.lastLocationTimestamp > 0) DateUtils.formatFullDateTime(member.lastLocationTimestamp) else "Unknown time"
                    Marker(
                        state = rememberMarkerState(key = "member_${member.id}_${lat}_$lon", position = LatLng(lat, lon)),
                        title = member.name,
                        snippet = "${if (isStale) "Stale fix" else "Recent fix"} · $timeStr",
                        icon = BitmapDescriptorFactory.defaultMarker(
                            if (isEmergency) BitmapDescriptorFactory.HUE_RED
                            else if (isStale) BitmapDescriptorFactory.HUE_ORANGE
                            else BitmapDescriptorFactory.HUE_GREEN
                        ),
                        onClick = {
                            onMemberClick?.invoke(member)
                            false
                        }
                    )
                }
            }

            // Route polyline (if supplied)
            if (routePolyline.isNotEmpty()) {
                val polyPoints = remember(routePolyline) {
                    routePolyline.filter { LocationQuality.coordinates(it.first, it.second) }
                        .map { LatLng(it.first, it.second) }
                }
                if (polyPoints.size >= 2) {
                    Polyline(
                        points = polyPoints,
                        color = Color(0xFF2563EB),
                        width = 10f
                    )
                }
            }
        }

        // Top bar overlay: Google Maps indicator badge with accuracy info
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SurfaceDark.copy(alpha = 0.88f))
                .border(1.dp, CardBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isIncidentActive) EmergencyRed else StatusSuccess)
            )
            Spacer(modifier = Modifier.width(6.dp))
            val headerText = when {
                isIncidentActive -> "GOOGLE MAPS · ACTIVE SOS"
                accuracyLabel != null -> "GOOGLE MAPS · $accuracyLabel"
                accuracyMeters != null && accuracyMeters > 0 -> "GOOGLE MAPS · ±${kotlin.math.round(accuracyMeters).toInt()}m"
                else -> "GOOGLE MAPS"
            }
            Text(
                text = headerText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isIncidentActive) EmergencyRed else TextPrimary,
                letterSpacing = 0.5.sp
            )
        }

        // Recenter Button: appears when user panned or when requested
        if (showRecenterButton) {
            FloatingActionButton(
                onClick = {
                    userInteracted = false
                    coroutineScope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(targetCenter, zoom),
                            durationMs = 600
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(40.dp),
                shape = CircleShape,
                containerColor = SurfaceDark.copy(alpha = 0.9f),
                contentColor = if (userInteracted) Color(0xFF38BDF8) else TextSecondary
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Recenter",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
