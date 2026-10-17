package com.siddesh.smsalerts.ui.components

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.siddesh.smsalerts.utils.PermissionManager

/**
 * Proactive First App Setup Permission Dialog.
 * Clearly explains why Location, SMS, Contacts, Notifications, and Audio permissions
 * are required before an emergency occurs, and launches the standard Android permission sequence.
 */
@Composable
fun EmergencyPermissionDialog(
    onDismiss: () -> Unit,
    onPermissionsUpdated: () -> Unit
) {
    val context = LocalContext.current
    var hasAttemptedRequest by remember { mutableStateOf(false) }

    // Launcher for all missing required permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasAttemptedRequest = true
        onPermissionsUpdated()
        if (PermissionManager.hasAllRequiredPermissions(context)) {
            onDismiss()
        }
    }

    val permissionItems = PermissionManager.getPermissionDescriptors(context)
    val hasAllGranted = permissionItems.all { it.isGranted }
    val missingCount = permissionItems.count { !it.isGranted }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCardElevated,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
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
        },
        title = {
            Text(
                text = "Emergency Readiness Setup",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "To protect you instantly during an emergency, Safe Alerts requests permissions now so your alerts can be sent without runtime delays or interruptions.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                permissionItems.forEach { item ->
                    PermissionRow(item = item)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (hasAttemptedRequest && !hasAllGranted) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(StatusWarning.copy(alpha = 0.12f))
                            .border(1.dp, StatusWarning.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Some permissions remain ungranted. If previously denied with 'Don't ask again', please enable them in Android Settings for full emergency functionality.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!hasAllGranted) {
                Button(
                    onClick = {
                        val missing = PermissionManager.getMissingPermissions(context)
                        if (missing.isNotEmpty()) {
                            permissionLauncher.launch(missing.toTypedArray())
                        } else {
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (hasAttemptedRequest) "RETRY PERMISSIONS ($missingCount)" else "GRANT PERMISSIONS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("ALL SET ✓", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            Row {
                if (hasAttemptedRequest && !hasAllGranted) {
                    OutlinedButton(
                        onClick = { PermissionManager.openAppSettings(context) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Settings", fontSize = 12.sp)
                    }
                }

                TextButton(onClick = onDismiss) {
                    Text("CONTINUE", color = TextSecondary, fontSize = 13.sp)
                }
            }
        }
    )
}

@Composable
private fun PermissionRow(item: PermissionManager.AppPermissionInfo) {
    val icon: ImageVector = when (item.permission) {
        PermissionManager.PERMISSION_FINE_LOCATION,
        PermissionManager.PERMISSION_COARSE_LOCATION -> Icons.Default.LocationOn
        PermissionManager.PERMISSION_SEND_SMS -> Icons.Default.Send
        PermissionManager.PERMISSION_READ_CONTACTS -> Icons.Default.Contacts
        PermissionManager.PERMISSION_RECORD_AUDIO -> Icons.Default.Mic
        else -> Icons.Default.Notifications
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceCard)
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (item.isGranted) StatusSuccess.copy(alpha = 0.15f)
                        else EmergencyRed.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isGranted) Icons.Default.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (item.isGranted) StatusSuccess else EmergencyRed,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = TextPrimary
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (item.isGranted) StatusSuccess.copy(alpha = 0.15f)
                                else if (item.isEssential) EmergencyRed.copy(alpha = 0.15f)
                                else Color.White.copy(alpha = 0.08f)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (item.isGranted) "GRANTED" else if (item.isEssential) "REQUIRED" else "OPTIONAL",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.isGranted) StatusSuccess else if (item.isEssential) EmergencyRed else TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = item.description,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
