package com.siddesh.smsalerts.ui.screens.trustedcircle

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShareLocation
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siddesh.smsalerts.data.location.LocationResult
import com.siddesh.smsalerts.data.location.LocationService
import com.siddesh.smsalerts.service.TrustedCircleManager
import com.siddesh.smsalerts.ui.theme.CardBorder
import com.siddesh.smsalerts.ui.theme.EmergencyRed
import com.siddesh.smsalerts.ui.theme.StatusError
import com.siddesh.smsalerts.ui.theme.StatusSuccess
import com.siddesh.smsalerts.ui.theme.StatusWarning
import com.siddesh.smsalerts.ui.theme.SurfaceCard
import com.siddesh.smsalerts.ui.theme.SurfaceCardElevated
import com.siddesh.smsalerts.ui.theme.TextMuted
import com.siddesh.smsalerts.ui.theme.TextPrimary
import com.siddesh.smsalerts.ui.theme.TextSecondary
import com.siddesh.smsalerts.utils.DateUtils
import com.siddesh.smsalerts.utils.HapticUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustedCircleScreen(
    onBackClick: () -> Unit,
    onIncidentClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val circleManager = remember { TrustedCircleManager.getInstance(context) }
    val members by circleManager.members.collectAsState()
    val circleStatus by circleManager.status.collectAsState()
    val cloudLocationStatus by com.siddesh.smsalerts.service.LiveLocationManager.getInstance(context).cloudStatus.collectAsState()
    val invitations by circleManager.invitations.collectAsState()
    val tracker = remember { com.siddesh.smsalerts.service.LiveLocationManager.getInstance(context) }
    val trackingStatus by tracker.locationStatus.collectAsState()
    var pendingShare by remember { mutableStateOf<String?>(null) }
    var permissionError by remember { mutableStateOf("") }
    var showPermissionDialog by remember { mutableStateOf<com.siddesh.smsalerts.utils.AppPermissionFeature?>(null) }

    var showAddDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Trusted Circle",
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
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Circle Privacy",
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
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .navigationBarsPadding()
        ) {
            Text(circleStatus, color = TextSecondary, modifier = Modifier.padding(12.dp))
            Text(cloudLocationStatus, color = TextMuted, modifier = Modifier.padding(horizontal = 12.dp))
            Text(trackingStatus, color = TextSecondary, modifier = Modifier.padding(horizontal = 12.dp))
            if (permissionError.isNotBlank()) {
                Text(permissionError, color = StatusWarning, modifier = Modifier.padding(12.dp))
                TextButton(onClick = { com.siddesh.smsalerts.utils.PermissionManager.openAppSettings(context) }) { Text("Location permission settings") }
            }
            if (members.any { it.amISharingLocationWithThem }) TextButton(onClick = {
                if (!LocationService(context).isGpsEnabled()) com.siddesh.smsalerts.utils.PermissionManager.openLocationSettings(context)
                else tracker.startLiveSharing("circle")
            }) { Text("Retry sharing / location settings") }
            // PENDING INVITATIONS BANNER (Mutual Account Invites)
            if (invitations.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF38BDF8).copy(alpha = 0.12f))
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.People, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PENDING CIRCLE INVITATIONS (${invitations.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                        }

                        invitations.forEach { inv ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(inv.senderName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                    Text(inv.senderPhone.ifBlank { "SafeAlerts User" }, fontSize = 11.sp, color = TextSecondary)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = {
                                            HapticUtils.vibrateTick(context)
                                            circleManager.acceptInvitation(inv)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Accept", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            HapticUtils.vibrateTick(context)
                                            circleManager.rejectInvitation(inv.id)
                                        },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Decline", fontSize = 11.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            // Header Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MEMBERS (${members.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.8.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (members.any { it.amISharingLocationWithThem }) {
                        TextButton(
                            onClick = {
                                HapticUtils.vibrateTick(context)
                                circleManager.stopAllSharing()
                            }
                        ) {
                            Text("Stop All Sharing", fontSize = 11.sp, color = EmergencyRed)
                        }
                    }

                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Member", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { CircleMap(members, onIncidentClick) }

                if (members.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceCard)
                                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF38BDF8).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.People,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No Trusted Circle Members Yet",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Add family and close friends to share live mutual GPS locations, receive journey notifications, and verify safety status.",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(members, key = { it.id }) { member ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceCardElevated)
                                .border(
                                    1.dp,
                                    if (member.isBlocked) StatusError.copy(alpha = 0.5f) else CardBorder,
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(14.dp)
                        ) {
                            Column {
                                if (member.activeIncidentId != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(EmergencyRed.copy(alpha = 0.2f))
                                            .border(1.dp, EmergencyRed, RoundedCornerShape(10.dp))
                                            .padding(10.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Warning, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "ACTIVE EMERGENCY DETECTED",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = EmergencyRed
                                                )
                                            }
                                            Text(
                                                text = "${member.name} triggered an SOS! Respond immediately or contact local emergency services.",
                                                fontSize = 11.sp,
                                                color = TextPrimary
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (member.phoneNumber.isNotBlank()) {
                                                    Button(
                                                        onClick = {
                                                            HapticUtils.vibrateTick(context)
                                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${member.phoneNumber.trim()}"))
                                                            context.startActivity(intent)
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.weight(1f).height(34.dp)
                                                    ) {
                                                        Text("Call ${member.name.split(" ").firstOrNull().orEmpty()}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                Button(
                                                    onClick = {
                                                        HapticUtils.vibrateTick(context)
                                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
                                                        context.startActivity(intent)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.weight(1f).height(34.dp)
                                                ) {
                                                    Text("Call 112", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        HapticUtils.vibrateTick(context)
                                                        member.activeIncidentId?.let { id -> onIncidentClick(id) }
                                                    },
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.weight(1f).height(34.dp)
                                                ) {
                                                    Text("Details", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(if (member.activeIncidentId != null) EmergencyRed.copy(alpha = 0.25f) else if (member.isBlocked) StatusError.copy(alpha = 0.15f) else Color(0xFF38BDF8).copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!member.photoUrl.isNullOrBlank()) coil.compose.AsyncImage(
                                                model = member.photoUrl, contentDescription = "${member.name} profile photo",
                                                modifier = Modifier.size(38.dp).clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            ) else Text(member.name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase(),
                                                color = if (member.activeIncidentId != null) EmergencyRed else Color(0xFF38BDF8), fontWeight = FontWeight.Bold)

                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = member.name + if (member.isBlocked) " (BLOCKED)" else if (member.activeIncidentId != null) " (SOS ACTIVE)" else "",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = if (member.activeIncidentId != null) EmergencyRed else if (member.isBlocked) StatusError else TextPrimary
                                            )
                                            Text(
                                                text = "${member.relationship} • ${member.phoneNumber}",
                                                fontSize = 12.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            HapticUtils.vibrateTick(context)
                                            circleManager.removeMember(member.id)
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = TextMuted, modifier = Modifier.size(18.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Location Status
                                if (member.lastLatitude != null && member.lastLongitude != null) {
                                    val isStale = member.isLocationStale
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isStale) StatusWarning.copy(alpha = 0.1f) else StatusSuccess.copy(alpha = 0.1f))
                                            .border(
                                                1.dp,
                                                if (isStale) StatusWarning.copy(alpha = 0.4f) else StatusSuccess.copy(alpha = 0.4f),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .padding(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.LocationOn,
                                                        contentDescription = null,
                                                        tint = if (isStale) StatusWarning else StatusSuccess,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (isStale) "STALE FIX" else "RECENT FIX",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isStale) StatusWarning else StatusSuccess
                                                    )
                                                }
                                                Text(
                                                    text = "%.5f, %.5f (±%dm)".format(member.lastLatitude, member.lastLongitude, member.lastAccuracyMeters?.toInt() ?: 0),
                                                    fontSize = 11.sp,
                                                    color = TextPrimary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "Updated: ${DateUtils.formatTimeOnly(member.lastLocationTimestamp)}",
                                                    fontSize = 10.sp,
                                                    color = TextSecondary
                                                )
                                            }

                                            OutlinedButton(
                                                onClick = {
                                                    HapticUtils.vibrateTick(context)
                                                    val geoUri = Uri.parse("geo:${member.lastLatitude},${member.lastLongitude}?q=${member.lastLatitude},${member.lastLongitude}(${member.name})")
                                                    val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                                                    context.startActivity(mapIntent)
                                                },
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("View Map", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                // Mutual Sharing Control
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Share My Live Location",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = if (member.amISharingLocationWithThem) "Sharing active (1h limit)" else "Not sharing with ${member.name}",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }

                                    Switch(
                                        checked = member.amISharingLocationWithThem,
                                        onCheckedChange = { enable ->
                                            HapticUtils.vibrateTick(context)
                                            if (enable && !com.siddesh.smsalerts.utils.PermissionManager.hasLocationPermission(context)) {
                                                pendingShare = member.id
                                                showPermissionDialog = com.siddesh.smsalerts.utils.AppPermissionFeature.LOCATION
                                            } else circleManager.toggleShareMyLiveLocation(member.id, enable)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFF38BDF8),
                                            checkedTrackColor = Color(0xFF38BDF8).copy(alpha = 0.3f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        CircleCodeDialog(circleManager) { showAddDialog = false }
    }

    showPermissionDialog?.let { feature ->
        com.siddesh.smsalerts.ui.components.SafeAlertPermissionDialog(
            feature = feature,
            onDismiss = { showPermissionDialog = null },
            onPermissionGranted = {
                val member = pendingShare
                showPermissionDialog = null
                if (feature == com.siddesh.smsalerts.utils.AppPermissionFeature.LOCATION && member != null) {
                    circleManager.toggleShareMyLiveLocation(member, true)
                }
            }
        )
    }

    // Privacy & Mutual Consent Info Dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Trusted Circle Privacy & Mutual Consent",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Trusted Circle enforces strict bilateral consent. Adding someone does not automatically share your coordinates. You can toggle outgoing sharing per person, revoke sharing at any time, or remove members without deleting your account.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                ) {
                    Text("GOT IT")
                }
            },
            containerColor = SurfaceCardElevated
        )
    }
}


@Composable
private fun CircleMap(
    members: List<com.siddesh.smsalerts.data.model.TrustedCircleMember>,
    onIncident: (String) -> Unit
) {
    val context = LocalContext.current
    val locationRepo = remember { com.siddesh.smsalerts.data.location.LocationRepository.getInstance(context) }
    val hasLocationPermission = remember { locationRepo.hasLocationPermission() }
    val lastValidFix by locationRepo.lastValidLocation.collectAsState()
    val isLocating by locationRepo.isLocating.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && lastValidFix == null) {
            locationRepo.refreshLocation()
        }
    }

    val points = members.filter { it.isSharingLocationWithMe && it.lastLatitude != null && it.lastLongitude != null }
    val centerLat = points.firstOrNull()?.lastLatitude ?: lastValidFix?.latitude ?: 0.0
    val centerLng = points.firstOrNull()?.lastLongitude ?: lastValidFix?.longitude ?: 0.0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCardElevated)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ShareLocation,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CIRCLE LOCATIONS · GOOGLE MAPS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        letterSpacing = 0.5.sp
                    )
                }

                if (points.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(StatusSuccess.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                        text = "${points.count { !it.isLocationStale }} RECENT FIXES",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = StatusSuccess
                        )
                    }
                }
            }

            com.siddesh.smsalerts.ui.components.SafeAlertGoogleMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                centerLatitude = centerLat,
                centerLongitude = centerLng,
                zoom = 13f,
                hasLocationPermission = hasLocationPermission,
                accuracyMeters = lastValidFix?.accuracy,
                accuracyLabel = lastValidFix?.badgeLabel,
                isLocating = isLocating,
                onRefreshClick = {
                    coroutineScope.launch { locationRepo.refreshLocation() }
                },
                originPoint = lastValidFix?.let { Pair(it.latitude, it.longitude) },
                circleMembers = members,
                onMemberClick = { member ->
                    member.activeIncidentId?.let { id -> onIncident(id) }
                }
            )

            if (points.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceCard)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pins appear only for available fixes. Each member must choose to share their location.",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
