package com.siddesh.smsalerts.ui.screens.walkie

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siddesh.smsalerts.mesh.BleMeshManager
import com.siddesh.smsalerts.mesh.MeshPacket
import com.siddesh.smsalerts.service.EmergencyManager
import com.siddesh.smsalerts.ui.components.GlassCard
import com.siddesh.smsalerts.ui.theme.BackgroundDark
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
import com.siddesh.smsalerts.utils.PermissionManager
import com.siddesh.smsalerts.walkie.WalkieTalkieManager
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkieTalkieScreen(
    onBackClick: () -> Unit
) {
    var showCloudGroups by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    if (showCloudGroups) {
        androidx.activity.compose.BackHandler { showCloudGroups = false }
        GroupChatScreen { showCloudGroups = false }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val walkieManager = remember { WalkieTalkieManager.getInstance(context) }
    val discoveredPeers by walkieManager.discoveredPeers.collectAsState()
    val mesh = remember { BleMeshManager.getInstance(context) }
    val receivedSos by mesh.receivedSos.collectAsState()
    val meshStatus by mesh.status.collectAsState()
    val transferStatus by mesh.transferStatus.collectAsState()
    var showGroup by remember { mutableStateOf(false) }
    var groupCode by remember { mutableStateOf("") }
    var groupError by remember { mutableStateOf("") }
    val selectedPeer by walkieManager.selectedPeer.collectAsState()
    val messages by walkieManager.messages.collectAsState()
    val chatError by walkieManager.error.collectAsState()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }
    val isAdvertising by walkieManager.isAdvertising.collectAsState()
    val isScanning by walkieManager.isScanning.collectAsState()

    var selectedModeIndex by remember { mutableIntStateOf(0) } // 0 = Offline SOS, 1 = Offline Chat
    var textInput by remember { mutableStateOf("") }
    var hasBtPermission by remember {
        mutableStateOf(PermissionManager.hasBluetoothPermissions(context))
    }

    var isSosBroadcasting by remember { mutableStateOf(false) }
    var lastSosPacketId by remember { mutableStateOf<String?>(null) }
    var lastSosTimestamp by remember { mutableStateOf(0L) }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasBtPermission = results.values.all { it }
        if (hasBtPermission) {
            walkieManager.startMesh()
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(hasBtPermission) {
        if (hasBtPermission) {
            walkieManager.startMesh()
        }
    }

    if (showGroup) {
        AlertDialog(onDismissRequest = { showGroup = false }, title = { Text("Offline-only pairing") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("For an account-based group with expiring invitations and cloud history, use Groups & cloud history. This fallback pairs nearby phones while offline using a shared secret; it creates no cloud membership. The key has no automatic expiry. Everyone holding it can read group packets. Share it in person and compare fingerprints. Names are self-reported.")
                com.siddesh.smsalerts.ui.components.FingerprintCard(com.siddesh.smsalerts.mesh.MeshSecurity.identityFingerprint(), "Your full fingerprint")
                OutlinedTextField(value = groupCode, onValueChange = { groupCode = it.filterNot(Char::isWhitespace) },
                    label = { Text("Offline pairing key") }, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    minLines = 2, maxLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(groupCode)) },
                    enabled = groupCode.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Copy full private code") }
                TextButton(onClick = {
                    try { mesh.stopMesh(); groupCode = com.siddesh.smsalerts.mesh.MeshSecurity.createGroup(context); walkieManager.refreshSession() }
                    catch (_: Exception) { groupError = "Device security storage unavailable; the group could not be created." }
                }) { Text("Create a new group") }
                Text(groupError, color = EmergencyRed)
            }
        }, confirmButton = { TextButton(onClick = {
            try { mesh.stopMesh(); com.siddesh.smsalerts.mesh.MeshSecurity.configureGroup(context, groupCode); walkieManager.refreshSession(); mesh.startMesh(); showGroup = false }
            catch (_: Exception) { groupError = "Invalid code. Copy the complete code from the other phone." }
        }) { Text("Join group") } }, dismissButton = { TextButton(onClick = { showGroup = false }) { Text("Close") } })
    }
    confirmDeleteId?.let { id ->
        AlertDialog(onDismissRequest = { confirmDeleteId = null }, title = { Text("Request deletion for both?") },
            text = { Text("A signed request is queued to the original recipient. Deletion is confirmed only after their compatible app acknowledges it. Saved copies and screenshots cannot be recalled.") },
            confirmButton = { TextButton(onClick = {
                try { walkieManager.requestDeleteForBoth(id) } catch (_: Exception) { groupError = "Deletion could not be saved." }
                confirmDeleteId = null
            }) { Text("Request deletion") } },
            dismissButton = { TextButton(onClick = { confirmDeleteId = null }) { Text("Cancel") } })
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Nearby Offline",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        // Truthful BLE Status
                        Text(
                            text = when {
                                !hasBtPermission -> "Bluetooth Permission Required"
                                !walkieManager.isBluetoothEnabled() -> "Bluetooth Disabled on Device"
                                isScanning && discoveredPeers.isEmpty() -> "Scanning for SafeAlerts Peers..."
                                isScanning -> "${discoveredPeers.count { it.isAuthenticated }} group-authenticated peer(s)"
                                else -> "Mesh Idle • Tap Scan to discover"
                            },
                            color = when {
                                !hasBtPermission || !walkieManager.isBluetoothEnabled() -> StatusError
                                discoveredPeers.isNotEmpty() -> StatusSuccess
                                else -> TextSecondary
                            },
                            fontSize = 11.sp
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
                        HapticUtils.vibrateTick(context)
                        if (isScanning || isAdvertising) {
                            walkieManager.stopMesh()
                        } else {
                            walkieManager.startMesh()
                        }
                    }) {
                        Icon(
                            imageVector = if (isScanning || isAdvertising) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isScanning || isAdvertising) "Stop Mesh" else "Start Mesh",
                            tint = if (isScanning || isAdvertising) EmergencyRed else StatusSuccess
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(meshStatus, color = TextSecondary, fontSize = 11.sp)
            if (chatError.isNotBlank()) Text(chatError, color = EmergencyRed, style = MaterialTheme.typography.bodySmall)
            if (groupError.isNotBlank()) Text(groupError, color = EmergencyRed, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { showCloudGroups = true }) { Text("Groups & cloud history") }
            TextButton(onClick = { groupCode = com.siddesh.smsalerts.mesh.MeshSecurity.groupCode(context).orEmpty(); showGroup = true }) { Text("Offline-only pairing setup") }
            // Permission Banner if missing
            if (!hasBtPermission) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    backgroundColor = StatusWarning.copy(alpha = 0.12f),
                    borderColor = StatusWarning.copy(alpha = 0.4f)
                ) {
                    Column {
                        Text(
                            text = "Bluetooth Permissions Required",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "SafeAlerts requires Bluetooth scan and connect permissions to exchange encrypted offline messages without cell service or internet.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { btPermissionLauncher.launch(PermissionManager.getBluetoothRequiredPermissions().toTypedArray()) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Grant Bluetooth Access", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Segmented Control: Offline SOS vs Offline Chat
            TabRow(
                selectedTabIndex = selectedModeIndex,
                containerColor = SurfaceCard,
                contentColor = TextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedModeIndex]),
                        color = if (selectedModeIndex == 0) EmergencyRed else Color(0xFF38BDF8)
                    )
                }
            ) {
                Tab(
                    selected = selectedModeIndex == 0,
                    onClick = {
                        HapticUtils.vibrateTick(context)
                        selectedModeIndex = 0
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = if (selectedModeIndex == 0) EmergencyRed else TextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Offline SOS",
                                fontWeight = FontWeight.Bold,
                                color = if (selectedModeIndex == 0) EmergencyRed else TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                )
                Tab(
                    selected = selectedModeIndex == 1,
                    onClick = {
                        HapticUtils.vibrateTick(context)
                        selectedModeIndex = 1
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = if (selectedModeIndex == 1) Color(0xFF38BDF8) else TextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Offline Chat",
                                fontWeight = FontWeight.Bold,
                                color = if (selectedModeIndex == 1) Color(0xFF38BDF8) else TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedModeIndex == 0) {
                // MODE A: OFFLINE SOS BROADCAST
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(EmergencyRed.copy(alpha = 0.12f))
                            .border(1.5.dp, EmergencyRed.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(18.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(EmergencyRed.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "OFFLINE EMERGENCY SOS BEACON",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                color = EmergencyRed
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Broadcasts a prioritized, signed emergency packet directly over Bluetooth Low Energy. Compatible SafeAlerts devices in your private group can receive and relay it without cellular service or internet.",
                                fontSize = 12.sp,
                                color = TextPrimary,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    HapticUtils.vibrateTick(context)
                                    if (!com.siddesh.smsalerts.mesh.MeshSecurity.hasGroup(context)) {
                                        groupError = "Set up or join the same private group on both phones before broadcasting."
                                        showGroup = true
                                        return@Button
                                    }
                                    if (!hasBtPermission) {
                                        btPermissionLauncher.launch(PermissionManager.getBluetoothRequiredPermissions().toTypedArray())
                                        return@Button
                                    }
                                    if (!walkieManager.isBluetoothEnabled()) {
                                        groupError = "Turn on Bluetooth, then retry. No packet has been transmitted."
                                        return@Button
                                    }
                                    walkieManager.startMesh()
                                    isSosBroadcasting = true
                                    val incidentId = EmergencyManager.getInstance(context).triggerEmergency(
                                        triggerSource = "OFFLINE_SOS", emergencyType = "OFFLINE SOS", bypassCountdown = false)
                                    lastSosPacketId = incidentId
                                    lastSosTimestamp = System.currentTimeMillis()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("BROADCAST OFFLINE SOS PACKET", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    // Status Panel for Active Broadcast
                    if (isSosBroadcasting || lastSosPacketId != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceCardElevated)
                                .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Broadcast Status:", fontSize = 12.sp, color = TextSecondary)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(StatusSuccess.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = transferStatus[lastSosPacketId] ?: "Preparing incident; no peer receipt confirmed",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (discoveredPeers.isNotEmpty()) StatusSuccess else StatusWarning
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Incident ID:", fontSize = 12.sp, color = TextSecondary)
                                    Text(lastSosPacketId?.take(16) ?: "--", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Timestamp:", fontSize = 12.sp, color = TextSecondary)
                                    Text(if (lastSosTimestamp > 0) DateUtils.formatTimeOnly(lastSosTimestamp) else "--", fontSize = 12.sp, color = TextPrimary)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Hop Limit / Protocol:", fontSize = 12.sp, color = TextSecondary)
                                    Text("Up to 5 hops • Compatible group only", fontSize = 12.sp, color = TextPrimary)
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedButton(
                                    onClick = {
                                        HapticUtils.vibrateTick(context)
                                        lastSosPacketId?.let { id -> scope.launch {
                                            try {
                                                mesh.queue.removePacket(id)
                                                EmergencyManager.getInstance(context).resolveEmergency(id)
                                                isSosBroadcasting = false
                                                lastSosPacketId = null
                                            } catch (e: Exception) {
                                                groupError = "Broadcast could not be stopped in storage. Retry; peers may still have copies."
                                            }
                                        } }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyRed),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("STOP BROADCAST", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    receivedSos.forEach { packet ->
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Received SOS · ${packet.senderName.ifBlank { "Group member" }}", color = EmergencyRed, fontWeight = FontWeight.Bold)
                                Text(packet.messageText, color = TextPrimary, fontSize = 13.sp)
                                Text("${DateUtils.formatFullDateTime(packet.timestamp)} · ${packet.hopCount} relay hops", color = TextSecondary, fontSize = 11.sp)
                                com.siddesh.smsalerts.ui.components.FingerprintCard(com.siddesh.smsalerts.mesh.MeshSecurity.sha256Hex(packet.publicKey), "Sender fingerprint")
                                if (packet.latitude != null && packet.longitude != null) TextButton(onClick = {
                                    try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:${packet.latitude},${packet.longitude}?q=${packet.latitude},${packet.longitude}"))) }
                                    catch (_: Exception) { android.widget.Toast.makeText(context, "No map application available", android.widget.Toast.LENGTH_SHORT).show() }
                                }) { Text("OPEN LAST REPORTED LOCATION") }
                            }
                        }
                    }

                    // Verified SafeAlerts Peer List
                    Text(
                        text = "NEARBY SafeAlerts PEERS (${discoveredPeers.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        modifier = Modifier.align(Alignment.Start),
                        letterSpacing = 0.8.sp
                    )

                    if (discoveredPeers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceCard)
                                .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No SafeAlerts peer devices detected within Bluetooth range.\nRange varies. Both devices need compatible SafeAlerts, Bluetooth, and the same private group.",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            discoveredPeers.forEach { peer ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(SurfaceCardElevated)
                                        .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(peer.deviceName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                            if (peer.isAuthenticated) com.siddesh.smsalerts.ui.components.FingerprintCard(peer.verificationFingerprint)
                                            TextButton(onClick = { mesh.connectPeer(peer) }) { Text(if (peer.isConnected) "Connected" else "Connect / retry") }
                                            Text("Signal: ${peer.signalStrengthDescription} (${peer.rssi} dBm)", fontSize = 11.sp, color = TextSecondary)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(StatusSuccess.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(if (peer.isAuthenticated) "GROUP VERIFIED" else "UNVERIFIED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = StatusSuccess)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // MODE B: OFFLINE TEXT CHAT
                // PEER SELECTOR BAR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECIPIENT CHANNEL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.8.sp
                    )

                    if (isScanning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BluetoothSearching,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Scanning",
                                fontSize = 10.sp,
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedPeer == null,
                            onClick = { walkieManager.selectPeer(null) },
                            label = { Text("📢 Mesh Broadcast") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmergencyRed.copy(alpha = 0.2f),
                                selectedLabelColor = EmergencyRed,
                                containerColor = SurfaceCard,
                                labelColor = TextSecondary
                            )
                        )
                    }
                    items(discoveredPeers) { peer ->
                        FilterChip(
                            selected = selectedPeer?.deviceId == peer.deviceId,
                            onClick = { walkieManager.selectPeer(peer) },
                            label = { Text("📱 ${peer.deviceName} (${peer.signalStrengthDescription})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF38BDF8).copy(alpha = 0.2f),
                                selectedLabelColor = Color(0xFF38BDF8),
                                containerColor = SurfaceCard,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                selectedPeer?.let { peer ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isBlocked = walkieManager.isPeerBlocked(peer.deviceId)
                        Text(
                            text = "Target: ${peer.deviceName} • ${if (isBlocked) "BLOCKED" else "READY"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isBlocked) StatusError else Color(0xFF38BDF8)
                        )
                        TextButton(
                            onClick = {
                                if (isBlocked) {
                                    walkieManager.unblockPeer(peer.deviceId)
                                } else {
                                    walkieManager.blockPeer(peer.deviceId)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isBlocked) "Unblock Peer" else "Block Peer",
                                fontSize = 11.sp,
                                color = if (isBlocked) StatusSuccess else StatusError
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // CONVERSATION THREAD
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceCard.copy(alpha = 0.6f))
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                        .padding(10.dp)
                ) {
                    if (messages.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF38BDF8).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No Offline Messages Yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Send encrypted text messages to nearby SafeAlerts devices directly over Bluetooth without internet or cell service.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                val isMe = msg.isOutgoing
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(
                                                RoundedCornerShape(
                                                    topStart = 14.dp,
                                                    topEnd = 14.dp,
                                                    bottomStart = if (isMe) 14.dp else 2.dp,
                                                    bottomEnd = if (isMe) 2.dp else 14.dp
                                                )
                                            )
                                            .background(
                                                if (isMe) EmergencyRed.copy(alpha = 0.85f)
                                                else SurfaceCardElevated
                                            )
                                            .border(
                                                1.dp,
                                                if (isMe) EmergencyRed else CardBorder,
                                                RoundedCornerShape(
                                                    topStart = 14.dp,
                                                    topEnd = 14.dp,
                                                    bottomStart = if (isMe) 14.dp else 2.dp,
                                                    bottomEnd = if (isMe) 2.dp else 14.dp
                                                )
                                            )
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Column {
                                            if (!isMe) {
                                                Text(
                                                    text = msg.senderName.ifBlank { "Nearby Peer" },
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF38BDF8)
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                            }

                                            Text(
                                                text = if (msg.deletedForBoth) "Message deleted" else msg.text,
                                                color = Color.White,
                                                fontSize = 13.sp
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.End,
                                                modifier = Modifier.align(Alignment.End)
                                            ) {
                                                Text(
                                                    text = DateUtils.formatTimeOnly(msg.timestamp),
                                                    fontSize = 9.sp,
                                                    color = if (isMe) Color.White.copy(alpha = 0.7f) else TextSecondary
                                                )

                                                if (isMe) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    val statusIcon = when (msg.deliveryStatus) {
                                                        "DELIVERED" -> Icons.Default.DoneAll
                                                        "RELAYED" -> Icons.Default.Check
                                                        else -> Icons.Default.Check
                                                    }
                                                    val statusTint = when (msg.deliveryStatus) {
                                                        "DELIVERED" -> StatusSuccess
                                                        "RELAYED" -> Color(0xFF38BDF8)
                                                        else -> Color.White.copy(alpha = 0.7f)
                                                    }
                                                    Icon(
                                                        imageVector = statusIcon,
                                                        contentDescription = msg.deliveryStatus,
                                                        tint = statusTint,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text(
                                                        text = msg.deliveryStatus,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = statusTint
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    TextButton(onClick = {
                                        try { walkieManager.deleteLocally(msg.id) } catch (_: Exception) { groupError = "Local deletion could not be saved." }
                                    }) { Text("Delete locally", fontSize = 10.sp) }
                                    if (isMe && !msg.deletedForBoth && msg.recipientId != MeshPacket.BROADCAST_RECIPIENT)
                                        TextButton(onClick = { confirmDeleteId = msg.id }) { Text("Delete for both", fontSize = 10.sp) }
                                    if (isMe && msg.deliveryStatus.startsWith("FAILED")) {
                                        TextButton(
                                            onClick = {
                                                HapticUtils.vibrateTick(context)
                                                walkieManager.resendMessage(msg.id)
                                            },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("Retry", fontSize = 10.sp, color = EmergencyRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // BOTTOM MESSAGE INPUT BAR (Fixed cleanly above keyboard / navigation bars)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { if (it.toByteArray().size <= 1000) textInput = it },
                        placeholder = {
                            Text(
                                text = if (selectedPeer != null) "Message ${selectedPeer!!.deviceName}..." else "Broadcast to all nearby devices...",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (textInput.isNotBlank()) {
                                HapticUtils.vibrateTick(context)
                                try { walkieManager.sendTextMessage(textInput.trim()) } catch (e: IllegalArgumentException) { android.widget.Toast.makeText(context, e.message, android.widget.Toast.LENGTH_SHORT).show() }
                                textInput = ""
                            }
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = SurfaceCard,
                            unfocusedContainerColor = SurfaceCard
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                HapticUtils.vibrateTick(context)
                                try { walkieManager.sendTextMessage(textInput.trim()) } catch (e: IllegalArgumentException) { android.widget.Toast.makeText(context, e.message, android.widget.Toast.LENGTH_SHORT).show() }
                                textInput = ""
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (textInput.isNotBlank()) EmergencyRed else SurfaceCard)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (textInput.isNotBlank()) Color.White else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
