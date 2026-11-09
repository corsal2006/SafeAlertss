package com.siddesh.smsalerts.ui.components

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.siddesh.smsalerts.data.preferences.AppUserPreferences
import com.siddesh.smsalerts.data.preferences.UserPreferencesDataStore
import com.siddesh.smsalerts.utils.PermissionManager
import kotlinx.coroutines.launch

/** Contextual SOS setup: no contacts, microphone or Bluetooth permission batch. */
@Composable
fun EmergencyPermissionDialog(onDismiss: () -> Unit, onPermissionsUpdated: () -> Unit,
    onNavigateToContacts: (() -> Unit)? = null, onContinue: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { UserPreferencesDataStore(context) }
    val prefs by store.preferencesFlow.collectAsState(AppUserPreferences())
    var revision by remember { mutableIntStateOf(0) }
    var attempted by remember { mutableStateOf(setOf<String>()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        revision++; onPermissionsUpdated()
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { revision++; onPermissionsUpdated() }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState())
                .imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Prepare emergency alert", style = MaterialTheme.typography.titleLarge)
                Text("Choose what to share. Denied permissions do not prevent saving an incident on this phone.")
                Row {
                    Text("Include my location in SOS and share updates with authorized recipients while the incident is active, including in the background with a visible notification", Modifier.weight(1f))
                    Switch(prefs.locationSharingEnabled, onCheckedChange = { enabled ->
                        scope.launch { store.updateLocationSharing(enabled); onPermissionsUpdated() }
                    })
                }
                val permissions = remember(revision, prefs.locationSharingEnabled, prefs.directSmsEnabled) {
                    PermissionManager.getPermissionDescriptors(context).filter {
                        (it.permission == Manifest.permission.ACCESS_FINE_LOCATION && prefs.locationSharingEnabled) ||
                        (it.permission == Manifest.permission.SEND_SMS && prefs.directSmsEnabled) ||
                        it.permission == Manifest.permission.POST_NOTIFICATIONS
                    }
                }
                permissions.forEach { item ->
                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                    Text(item.description, style = MaterialTheme.typography.bodySmall)
                    if (item.isGranted) Text("Granted")
                    else {
                        val activity = context as? Activity
                        val settings = item.permission in attempted && activity != null &&
                            !PermissionManager.shouldShowRationale(activity, item.permission)
                        OutlinedButton(onClick = {
                            if (settings) PermissionManager.openAppSettings(context)
                            else {
                                attempted = attempted + item.permission
                                launcher.launch(if (item.permission == Manifest.permission.ACCESS_FINE_LOCATION)
                                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                                    else arrayOf(item.permission))
                            }
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(if (settings) "Open Android settings" else "Allow ${item.title}")
                        }
                    }
                }
                Text("SMS uses your SIM and saved personal contacts. Carrier charges may apply. Submission does not confirm delivery.",
                    style = MaterialTheme.typography.bodySmall)
                onNavigateToContacts?.let { OutlinedButton(onClick = it, Modifier.fillMaxWidth()) { Text("Manage emergency contacts") } }
                Button(onClick = { (onContinue ?: onDismiss)() }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (onContinue == null) "Done" else "Continue with available channels")
                }
                TextButton(onClick = onDismiss, Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}
