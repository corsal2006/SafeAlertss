package com.siddesh.smsalerts.ui.screens.trustedcircle

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siddesh.smsalerts.service.TrustedCircleManager
import com.siddesh.smsalerts.ui.theme.*

@Composable
fun CircleCodeDialog(manager: TrustedCircleManager, onClose: () -> Unit) {
    val code by manager.invitationCode.collectAsState()
    val busy by manager.busy.collectAsState()
    val status by manager.status.collectAsState()
    val invitations by manager.invitations.collectAsState()
    val userProfile by com.siddesh.smsalerts.data.repository.UserRepository.getInstance().userProfile.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var joinTab by rememberSaveable { mutableStateOf(false) }
    var inputCode by rememberSaveable { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val displayName = userProfile?.fullName?.ifBlank { userProfile?.name }?.takeIf { it.isNotBlank() } ?: "SafeAlerts User"
    val initials = displayName.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase()

    LaunchedEffect(Unit) { manager.manageCode("get") }
    LaunchedEffect(code.expiresAt) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    val valid = code.code.isNotBlank() && code.expiresAt > now
    val remainingSecs = if (valid) ((code.expiresAt - now) / 1000).coerceAtLeast(0) else 0
    val formattedRemaining = "%02d:%02d".format(remainingSecs / 60, remainingSecs % 60)

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GroupAdd,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Trusted Circle Invite",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Authenticated User Identity Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCard)
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF38BDF8).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!userProfile?.photoUrl.isNullOrBlank()) {
                                coil.compose.AsyncImage(
                                    model = userProfile?.photoUrl,
                                    contentDescription = "$displayName profile photo",
                                    modifier = Modifier.size(36.dp).clip(CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = initials.ifBlank { "SA" },
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "Signed-in account · location sharing is a separate choice",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Compact Tab Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceCard)
                        .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!joinTab) Color(0xFF38BDF8).copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { joinTab = false }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "My Code",
                            fontSize = 13.sp,
                            fontWeight = if (!joinTab) FontWeight.Bold else FontWeight.Medium,
                            color = if (!joinTab) Color(0xFF38BDF8) else TextMuted
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (joinTab) Color(0xFF38BDF8).copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { joinTab = true }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Enter Code",
                            fontSize = 13.sp,
                            fontWeight = if (joinTab) FontWeight.Bold else FontWeight.Medium,
                            color = if (joinTab) Color(0xFF38BDF8) else TextMuted
                        )
                    }
                }

                if (joinTab) {
                    Text(
                        text = "Enter the 8-character invitation code from someone you trust to request joining their circle.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { inputCode = it.replace(Regex("\\s"), "").uppercase(java.util.Locale.ROOT).take(8) },
                        label = { Text("8-Character Code", fontSize = 12.sp) },
                        placeholder = { Text("e.g. 7K2M9X4B", fontSize = 12.sp, color = TextMuted) },
                        leadingIcon = {
                            Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val codeMatchesPattern = Regex("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}").matches(inputCode)
                    Button(
                        onClick = { manager.joinCode(inputCode) },
                        enabled = !busy && codeMatchesPattern,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                    ) {
                        Text("Connect with Code", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    invitations.forEach { invitation ->
                        Text("Invitation from ${invitation.senderName}. Accepting does not share your location.", color = TextPrimary)
                        Button(onClick = { manager.acceptInvitation(invitation) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Accept invitation") }
                        OutlinedButton(onClick = { manager.rejectInvitation(invitation.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Reject invitation") }
                    }
                } else {
                    Text(
                        text = "Share this secure, single-use code. After they accept, you can each enable location sharing.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    if (valid) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF38BDF8).copy(alpha = 0.08f))
                                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = code.code.chunked(4).joinToString(" "),
                                    fontSize = 24.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp,
                                    color = Color(0xFF38BDF8)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Expires in $formattedRemaining",
                                    fontSize = 11.sp,
                                    color = StatusWarning,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { clipboard.setText(AnnotatedString(code.code)) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, null, Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy", fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    context.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "$displayName invited you to join their SafeAlerts Trusted Circle. Enter invitation code ${code.code} in the SafeAlerts app to connect securely. This code expires in 15 minutes."
                                                )
                                                putExtra(Intent.EXTRA_SUBJECT, "SafeAlerts Trusted Circle Invitation")
                                            },
                                            "Share invitation code"
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Icon(Icons.Default.Share, null, Modifier.size(15.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share", fontSize = 12.sp)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceCard)
                                .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (code.code.isBlank()) "No active code generated." else "Your code has expired.",
                                fontSize = 12.sp,
                                color = TextMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { manager.manageCode("generate") },
                            enabled = !busy,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                        ) {
                            Text(if (valid) "Regenerate" else "Generate Code", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        if (valid) {
                            OutlinedButton(
                                onClick = { manager.manageCode("revoke") },
                                enabled = !busy,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyRed),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text("Revoke", fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (busy) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFF38BDF8)
                    )
                }

                if (status.isNotBlank()) {
                    Text(
                        text = status,
                        fontSize = 11.sp,
                        color = if (status.contains("fail", ignoreCase = true) || status.contains("error", ignoreCase = true)) StatusError else TextSecondary,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Close", color = TextSecondary, fontSize = 13.sp)
            }
        },
        containerColor = SurfaceCardElevated,
        shape = RoundedCornerShape(18.dp)
    )
}

