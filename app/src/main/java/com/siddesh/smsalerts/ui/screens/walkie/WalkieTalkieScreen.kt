package com.siddesh.smsalerts.ui.screens.walkie

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkieTalkieScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val walkieManager = remember { WalkieTalkieManager.getInstance(context) }
    val discoveredPeers by walkieManager.discoveredPeers.collectAsState()
    val selectedPeer by walkieManager.selectedPeer.collectAsState()
    val messages by walkieManager.messages.collectAsState()
    val isAdvertising by walkieManager.isAdvertising.collectAsState()
    val isScanning by walkieManager.isScanning.collectAsState()

    var textInput by remember { mutableStateOf("") }
    var hasBtPermission by remember {
        mutableStateOf(PermissionManager.hasBluetoothPermissions(context))
    }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Nearby Offline Chat",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (!hasBtPermission) {
                                "Bluetooth permission required"
                            } else if (!walkieManager.isBluetoothEnabled()) {
                                "Bluetooth is disabled on device"
                            } else if (discoveredPeers.isEmpty()) {
                                "BLE Mesh Active • Scanning nearby SafeAlerts devices..."
                            } else {
                                "Connected: ${discoveredPeers.size} nearby authenticated peer(s)"
                            },
                            color = if (discoveredPeers.isNotEmpty()) StatusSuccess else if (!hasBtPermission) StatusError else TextSecondary,
                            fontSize = 12.sp
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
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Permission Banner if missing
            if (!hasBtPermission) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
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
                    color = TextMuted,
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

            Spacer(modifier = Modifier.height(8.dp))

            // CONVERSATION THREAD
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceCard.copy(alpha = 0.6f))
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                if (messages.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF38BDF8).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Offline Messages Yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Send encrypted text messages to nearby SafeAlerts devices directly over Bluetooth without internet or cellular connectivity.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 20.dp)
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
                                            text = msg.text,
                                            color = Color.White,
                                            fontSize = 14.sp
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
                                                color = if (isMe) Color.White.copy(alpha = 0.7f) else TextMuted
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

                                if (isMe && msg.deliveryStatus == "FAILED") {
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

            Spacer(modifier = Modifier.height(10.dp))

            // BOTTOM MESSAGE INPUT BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = {
                        Text(
                            text = if (selectedPeer != null) "Message ${selectedPeer!!.deviceName}..." else "Broadcast to all nearby devices...",
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (textInput.isNotBlank()) {
                            HapticUtils.vibrateTick(context)
                            walkieManager.sendTextMessage(textInput.trim())
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
                            walkieManager.sendTextMessage(textInput.trim())
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
                        tint = if (textInput.isNotBlank()) Color.White else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
