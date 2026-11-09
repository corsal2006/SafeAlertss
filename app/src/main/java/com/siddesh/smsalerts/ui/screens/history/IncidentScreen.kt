package com.siddesh.smsalerts.ui.screens.history

import android.Manifest
import android.content.Intent
import android.net.Uri
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.siddesh.smsalerts.data.location.*
import com.siddesh.smsalerts.data.preferences.*
import com.siddesh.smsalerts.service.*
import com.siddesh.smsalerts.ui.components.*
import com.siddesh.smsalerts.utils.DateUtils
import com.siddesh.smsalerts.utils.PermissionManager
import kotlinx.coroutines.*

@Composable
fun IncidentScreen(id: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsStore = remember { UserPreferencesDataStore(context) }
    val prefs by prefsStore.preferencesFlow.collectAsState(AppUserPreferences())
    val tracker = remember { LiveLocationManager.getInstance(context) }
    val trackingStatus by tracker.locationStatus.collectAsState()
    val cloudStatus by tracker.cloudStatus.collectAsState()
    var uid by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.uid) }
    var incident by remember(id, uid) { mutableStateOf<Map<String, Any>?>(null) }
    var location by remember(id, uid) { mutableStateOf<Map<String, Any>?>(null) }
    var historicalLocation by remember(id, uid) { mutableStateOf<Map<String, Any>?>(null) }
    var local by remember(id, uid) { mutableStateOf<com.siddesh.smsalerts.data.model.EmergencyIncident?>(null) }
    var error by remember(id, uid) { mutableStateOf("") }
    var retrying by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    DisposableEffect(Unit) {
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { uid = it.currentUser?.uid }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }
    LaunchedEffect(id, uid) {
        while (isActive) {
            now = System.currentTimeMillis()
            local = withContext(Dispatchers.IO) {
                try { EmergencyOutbox(context).getIncident(id)?.takeIf {
                    it.userId == uid || (uid == null && it.userId in setOf("", "local_user"))
                } } catch (e: java.io.IOException) { error = "Saved incident could not be read. Check device storage."; null }
            }
            delay(2000)
        }
    }
    DisposableEffect(id, uid) {
        val listener = uid?.let { FirebaseFirestore.getInstance().document("incidents/$id").addSnapshotListener { doc, e ->
            incident = if (e == null) doc?.data else null
            error = when {
                e != null -> "Cloud incident unavailable. Reconnect or ask the sender to check sharing access."
                doc?.exists() != true -> "Cloud incident has not been confirmed yet."
                else -> ""
            }
        } }
        onDispose { listener?.remove() }
    }
    val owner = incident?.get("ownerUid") as? String ?: local?.userId
    DisposableEffect(owner, id, uid) {
        val listener = owner?.takeIf { it.isNotBlank() && uid != null }?.let {
            FirebaseFirestore.getInstance().collection("users/$it/alerts/$id/locations")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(1)
                .addSnapshotListener { snapshot, failure -> historicalLocation = if (failure == null) snapshot?.documents?.firstOrNull()?.data else null }
        }
        onDispose { listener?.remove(); historicalLocation = null }
    }
    DisposableEffect(owner, id, uid) {
        val listener = owner?.takeIf { it.isNotBlank() && uid != null }?.let {
            FirebaseFirestore.getInstance().document("users/$it/live_location/current").addSnapshotListener { doc, e ->
                val data = if (e == null) doc?.data else null
                location = data?.takeIf { it["incidentId"] == id || (it["sessionIds"] as? List<*>)?.contains(id) == true }
            }
        }
        onDispose { listener?.remove(); location = null }
    }
    fun retryLocation() {
        if (retrying || owner != uid || uid == null) return
        retrying = true
        scope.launch {
            try {
                prefsStore.updateLocationSharing(true)
                val result = LocationRepository.getInstance(context).refreshLocation()
                if (result is LocationResult.Success) {
                    tracker.recordFix(uid!!, id, result)
                    tracker.startLiveSharing(id)
                } else error = result.explanation()
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error = "Location could not be saved (${e.javaClass.simpleName}). Retry." }
            finally { retrying = false }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (PermissionManager.hasLocationPermission(context)) retryLocation()
        else error = "Location permission denied. Use Android settings to allow it, then retry."
    }
    Scaffold(topBar = { SafeAlertTopBar("Emergency incident", true, onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(local?.senderName?.takeIf { it.isNotBlank() } ?: incident?.get("senderName") as? String ?: "Emergency incident")
            if (error.isNotBlank()) Text(error, style = MaterialTheme.typography.bodySmall)
            Text("Category: ${local?.emergencyType ?: incident?.get("category") ?: "Unavailable"}")
            val state = local?.state ?: incident?.get("status")
            Text("Status: ${state ?: "Unavailable"}", color = if (state == "ACTIVE") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            local?.let { saved ->
                Text("Incident time: ${DateUtils.formatFullDateTime(saved.creationTimestamp)}")
                saved.transportStatuses.filterKeys { !it.contains(":") }.forEach { (channel, status) -> Text("$channel: $status") }
                saved.recipientStatuses.forEach { (phone, status) -> Text("SMS to …${phone.takeLast(4)}: $status") }
                if (saved.state !in setOf("RESOLVED", "CANCELLED")) {
                    Button(onClick = { EmergencyManager.getInstance(context).resolveEmergency(id) }, Modifier.fillMaxWidth()) { Text("I'M SAFE — RESOLVE INCIDENT") }
                    Text(trackingStatus); Text(cloudStatus, style = MaterialTheme.typography.bodySmall)
                    Text("Share real location for this SOS. Accepted circle members still need your separate sharing grant.",
                        style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {
                        if (PermissionManager.hasLocationPermission(context)) retryLocation()
                        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }, enabled = !retrying, modifier = Modifier.fillMaxWidth()) {
                        Text(if (retrying) "Getting location…" else if (prefs.locationSharingEnabled) "Retry location" else "Enable SOS location and retry")
                    }
                    if (prefs.locationSharingEnabled) OutlinedButton(onClick = { scope.launch {
                        prefsStore.updateLocationSharing(false); tracker.stopLiveSharing(id)
                    } }, Modifier.fillMaxWidth()) { Text("Stop SOS location sharing") }
                    TextButton(onClick = { PermissionManager.openLocationSettings(context) }) { Text("Device location settings") }
                    TextButton(onClick = { PermissionManager.openAppSettings(context) }) { Text("App permission settings") }
                }
            }
            val remoteFix = listOfNotNull(location, historicalLocation).maxByOrNull { (it["timestamp"] as? Number)?.toLong() ?: 0 }
                ?.takeIf { ((it["timestamp"] as? Number)?.toLong() ?: 0) >= (local?.locationTimestamp ?: 0) }
            val lat = (remoteFix?.get("latitude") as? Number)?.toDouble() ?: local?.latitude
            val lon = (remoteFix?.get("longitude") as? Number)?.toDouble() ?: local?.longitude
            val time = (remoteFix?.get("timestamp") as? Number)?.toLong() ?: local?.locationTimestamp ?: 0
            val cached = remoteFix?.get("cached") as? Boolean ?: local?.locationCached ?: false
            Text(if (time > 0) "${if (cached) "Cached" else if (LocationQuality.fresh(time, now)) "Recent" else "Stale"} fix: ${DateUtils.formatFullDateTime(time)}"
                else "No location available. No coordinates have been invented.")
            if (lat != null && lon != null && LocationQuality.coordinates(lat, lon)) {
                SafeAlertGoogleMap(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    centerLatitude = lat,
                    centerLongitude = lon,
                    zoom = 15f,
                    hasLocationPermission = PermissionManager.hasLocationPermission(context),
                    incidentLocation = Pair(lat, lon),
                    incidentTitle = local?.senderName ?: incident?.get("senderName") as? String ?: "Incident",
                    isIncidentActive = state == "ACTIVE"
                )
                OutlinedButton(onClick = { try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LocationResult.generateMapsUrl(lat, lon))))
                } catch (e: Exception) { error = "No map or browser app available." } }, Modifier.fillMaxWidth()) { Text("Open map link") }
            }
            listOf("Call user" to (local?.senderPhone ?: incident?.get("senderPhone") as? String).orEmpty(),
                "Call emergency services" to (local?.policeNumber?.ifBlank { prefs.policeNumber } ?: incident?.get("policeNumber") as? String).orEmpty(),
                "Call helpline" to (local?.helplineNumber ?: incident?.get("helplineNumber") as? String).orEmpty()).forEach { (label, phone) ->
                OutlinedButton(onClick = { try { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))) }
                    catch (e: Exception) { error = "No phone dialer available." } }, enabled = phone.matches(Regex("[+0-9]{3,16}")), modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
        }
    }
}
