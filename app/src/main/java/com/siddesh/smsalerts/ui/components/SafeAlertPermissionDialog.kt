package com.siddesh.smsalerts.ui.components

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.siddesh.smsalerts.ui.theme.EmergencyRed
import com.siddesh.smsalerts.ui.theme.SurfaceCard
import com.siddesh.smsalerts.ui.theme.TextPrimary
import com.siddesh.smsalerts.ui.theme.TextSecondary
import com.siddesh.smsalerts.utils.AppPermissionFeature
import com.siddesh.smsalerts.utils.PermissionManager

/**
 * Centralized Permission Dialog implementing the required 10-step permission lifecycle.
 *
 * 1. Checks whether permission is granted.
 * 2. Displays clear rationale popup with "Allow Permission" and "Not Now".
 * 3. Launches native Android permission request.
 * 4. Explains limitation upon denial.
 * 5. Provides "Open Settings" option if permanently denied.
 * 6. Automatically detects granted state upon returning from Settings via Lifecycle ON_RESUME.
 * 7. Calls [onPermissionGranted] as soon as permission is confirmed.
 */
@Composable
fun SafeAlertPermissionDialog(
    feature: AppPermissionFeature,
    onDismiss: () -> Unit,
    onPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasAttempted by remember { mutableStateOf(false) }
    var wasDeniedOnce by remember { mutableStateOf(false) }

    // Recheck on resume (e.g. after returning from Android Settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (PermissionManager.isFeatureGranted(context, feature)) {
                    onPermissionGranted()
                    onDismiss()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permsToRequest = remember(feature) {
        PermissionManager.getPermissionsForFeature(feature)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasAttempted = true
        val allGranted = permsToRequest.all { results[it] == true } || PermissionManager.isFeatureGranted(context, feature)
        if (allGranted) {
            onPermissionGranted()
            onDismiss()
        } else {
            wasDeniedOnce = true
        }
    }

    val isPermanentlyDenied = remember(hasAttempted, wasDeniedOnce) {
        activity != null && PermissionManager.isPermanentlyDenied(activity, permsToRequest, hasAttempted)
    }

    val icon: ImageVector = when (feature) {
        AppPermissionFeature.LOCATION, AppPermissionFeature.BACKGROUND_LOCATION -> Icons.Default.LocationOn
        AppPermissionFeature.SMS -> Icons.Default.Sms
        AppPermissionFeature.CONTACTS -> Icons.Default.Contacts
        AppPermissionFeature.NOTIFICATIONS -> Icons.Default.Notifications
        AppPermissionFeature.MICROPHONE -> Icons.Default.Mic
        AppPermissionFeature.BLUETOOTH -> Icons.Default.Bluetooth
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = EmergencyRed,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = feature.title,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isPermanentlyDenied) {
                        "${feature.limitation}\n\nPermission was permanently disabled. Please tap Open Settings and grant permission under Permissions."
                    } else if (wasDeniedOnce) {
                        "${feature.limitation}\n\n${feature.rationale}"
                    } else {
                        feature.rationale
                    },
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                if (feature == AppPermissionFeature.BACKGROUND_LOCATION) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tip: Choose \"Allow all the time\" in Android Settings.",
                        color = EmergencyRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isPermanentlyDenied || (feature == AppPermissionFeature.BACKGROUND_LOCATION && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)) {
                        PermissionManager.openAppSettings(context)
                    } else {
                        hasAttempted = true
                        launcher.launch(permsToRequest.toTypedArray())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (isPermanentlyDenied || (feature == AppPermissionFeature.BACKGROUND_LOCATION && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)) {
                        "Open Settings"
                    } else {
                        "Allow Permission"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Not Now",
                    color = TextSecondary
                )
            }
        },
        containerColor = SurfaceCard,
        shape = RoundedCornerShape(16.dp)
    )
}

